package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrBuffer;
import com.oracle.svm.core.jfr.JfrBufferAccess;
import com.oracle.svm.core.jfr.JfrBufferType;
import com.oracle.svm.core.jfr.JfrChunkWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrRepository;
import com.oracle.svm.core.jfr.JfrType;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdEpoch;
import com.oracle.svm.core.locks.VMMutex;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.WordFactory;

public final class JfrOldObjectRepository implements JfrRepository {
    private static final int OBJECT_DESCRIPTION_MAX_SIZE = 100;

    private final VMMutex mutex;
    private final JfrOldObjectEpochData epochData0;
    private final JfrOldObjectEpochData epochData1;
    private long nextId;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrOldObjectRepository() {
        this.mutex = new VMMutex("jfrOldObjectRepository");
        this.epochData0 = new JfrOldObjectEpochData();
        this.epochData1 = new JfrOldObjectEpochData();
    }

    public void teardown() {
        epochData0.teardown();
        epochData1.teardown();
    }

    @Uninterruptible(reason = "Locking without transition and result is only valid until epoch changes.", callerMustBe = true)
    public long serializeOldObject(Object obj) {
        mutex.lockNoTransition();
        try {
            JfrOldObjectEpochData epochData = getEpochData(false);
            if (epochData.buffer.isNull()) {
                epochData.buffer = JfrBufferAccess.allocate(JfrBufferType.C_HEAP);
            }

            final Word pointer = Word.objectToUntrackedPointer(obj);
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initialize(data, epochData.buffer);
            final long objectId = nextId++;
            JfrNativeEventWriter.putLong(data, objectId);
            JfrNativeEventWriter.putLong(data, pointer.rawValue());
            JfrNativeEventWriter.putLong(data, SubstrateJVM.getTypeRepository().getClassId(obj.getClass()));
            writeDescription(obj, data);
            JfrNativeEventWriter.putLong(data, WordFactory.zero().rawValue()); // todo parent address (path-to-gc-roots)
            if (!JfrNativeEventWriter.commit(data)) {
                return -1;
            }

            epochData.unflushedEntries++;
            /* The buffer may have been replaced with a new one. */
            epochData.buffer = data.getJfrBuffer();
            return objectId;
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Locking without transition and result is only valid until epoch changes.", callerMustBe = true)
    private static void writeDescription(Object obj, JfrNativeEventWriterData data) {
        if (obj instanceof ThreadGroup) {
            String prefix = "Thread Group: ";
            String threadGroupName = ((ThreadGroup) obj).getName();
            JfrNativeEventWriter.putString(data, threadGroupName, OBJECT_DESCRIPTION_MAX_SIZE, prefix);
            return;
        }
        if (obj instanceof Thread) {
            String prefix = "Thread Name: ";
            String threadName = ((Thread) obj).getName();
            JfrNativeEventWriter.putString(data, threadName, OBJECT_DESCRIPTION_MAX_SIZE, prefix);
            return;
        }
        if (obj instanceof Class) {
            String prefix = "Class Name: ";
            String className = ((Class<?>) obj).getName();
            JfrNativeEventWriter.putString(data, className, OBJECT_DESCRIPTION_MAX_SIZE, prefix);
            return;
        }

        // Size description not implemented since that relies on runtime reflection.
        JfrNativeEventWriter.putLong(data, 0L);
    }

    @Override
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public int write(JfrChunkWriter writer, boolean flushpoint) {
        if (flushpoint) {
            // Not supported
            return EMPTY;
        }

        mutex.lockNoTransition();
        try {
            JfrOldObjectEpochData epochData = getEpochData(true);
            int count = epochData.unflushedEntries;
            if (count != 0) {
                writer.writeCompressedLong(JfrType.OldObject.getId());
                writer.writeCompressedInt(count);
                writer.write(epochData.buffer);
            }
            epochData.clear();
            return count == 0 ? EMPTY : NON_EMPTY;
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Result is only valid until epoch changes.", callerMustBe = true)
    private JfrOldObjectEpochData getEpochData(boolean previousEpoch) {
        boolean epoch = previousEpoch ? JfrTraceIdEpoch.getInstance().previousEpoch() : JfrTraceIdEpoch.getInstance().currentEpoch();
        return epoch ? epochData0 : epochData1;
    }

    private static class JfrOldObjectEpochData {
        private int unflushedEntries;
        private JfrBuffer buffer;

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrOldObjectEpochData() {
            this.unflushedEntries = 0;
        }

        @Uninterruptible(reason = "May write current epoch data.")
        void clear() {
            unflushedEntries = 0;
            JfrBufferAccess.reinitialize(buffer);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        void teardown() {
            unflushedEntries = 0;
            JfrBufferAccess.free(buffer);
            buffer = WordFactory.nullPointer();
        }
    }
}
