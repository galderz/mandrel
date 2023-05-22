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
import com.oracle.svm.core.jfr.utils.JfrVisited;
import com.oracle.svm.core.locks.VMMutex;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

public final class JfrOldObjectRepository implements JfrRepository {
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
            JfrNativeEventWriter.putLong(data, 0L); // todo description
            JfrNativeEventWriter.putLong(data, WordFactory.zero().rawValue()); // todo path to gc roots
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

//    @Uninterruptible(reason = "Epoch must not change while in this method.")
//    private void serializeOldObject(JfrOldObjectTableEntry entry, JfrOldObjectEpochData epochData) {
//    }

//    @Uninterruptible(reason = "Locking without transition and result is only valid until epoch changes.", callerMustBe = true)
//    public JfrOldObjectTableEntry putOldObject0(Object obj, JfrOldObjectEpochData epochData) {
//        JfrOldObjectTableEntry entry = StackValue.get(JfrOldObjectTableEntry.class);
//        final Word pointer = Word.objectToUntrackedPointer(obj);
//        entry.setHash(UninterruptibleUtils.Long.hashCode(pointer.rawValue()));
//        entry.setRawOldObject(pointer);
//        entry.setParent(WordFactory.zero());
//        entry.setGcRootId(0);
//        entry.setSkipLength(0);
//        return (JfrOldObjectTableEntry) epochData.table.getOrPut(entry);
//    }

    @Override
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public int write(JfrChunkWriter writer, boolean flushpoint) {
        if (flushpoint) {
            // Not supported
            return EMPTY;
        }

        mutex.lockNoTransition();
        try {
            JfrOldObjectEpochData epochData = getEpochData(!flushpoint);
            int count = epochData.unflushedEntries;
            if (count != 0) {
                writer.writeCompressedLong(JfrType.OldObject.getId());
                writer.writeCompressedInt(count);
                writer.write(epochData.buffer);
            }
            epochData.clear(flushpoint);
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

    @Uninterruptible(reason = "Result is only valid until epoch changes.", callerMustBe = true)
    private JfrBuffer getCurrentBuffer() {
        JfrOldObjectEpochData epochData = getEpochData(false);
        if (epochData.buffer.isNull()) {
            epochData.buffer = JfrBufferAccess.allocate(JfrBufferType.C_HEAP);
        }
        return epochData.buffer;
    }

    @RawStructure
    public interface JfrOldObjectTableEntry extends JfrVisited {
        @RawField
        Pointer getRawOldObject();

        @RawField
        void setRawOldObject(Pointer pointer);

        @RawField
        Pointer getParent();

        @RawField
        void setParent(Pointer pointer);

        @RawField
        long getGcRootId();

        @RawField
        void setGcRootId(long id);

        @RawField
        int getSkipLength();

        @RawField
        void setSkipLength(int skipLength);

        @RawField
        long getClassId();

        @RawField
        void setClassId(long id);
    }

//    public static final class JfrOldObjectTable extends AbstractUninterruptibleHashtable {
//        private long nextId;
//
//        @Override
//        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
//        protected JfrOldObjectTableEntry[] createTable(int length) {
//            return new JfrOldObjectTableEntry[length];
//        }
//
//        @Override
//        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
//        protected boolean isEqual(UninterruptibleEntry a, UninterruptibleEntry b) {
//            JfrOldObjectTableEntry entry1 = (JfrOldObjectTableEntry) a;
//            JfrOldObjectTableEntry entry2 = (JfrOldObjectTableEntry) b;
//            return entry1.getRawOldObject() == entry2.getRawOldObject();
//        }
//
//        @Override
//        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
//        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry valueOnStack) {
//            JfrOldObjectTableEntry result = (JfrOldObjectTableEntry) copyToHeap(valueOnStack, SizeOf.unsigned(JfrOldObjectTableEntry.class));
//            if (result.isNonNull()) {
//                result.setId(++nextId);
//            }
//            return result;
//        }
//    }

    private static class JfrOldObjectEpochData {
        // private final JfrOldObjectTable table;
        private int unflushedEntries;
        private JfrBuffer buffer;

        @Platforms(Platform.HOSTED_ONLY.class)
        JfrOldObjectEpochData() {
            // this.table = new JfrOldObjectTable();
            this.unflushedEntries = 0;
        }

        @Uninterruptible(reason = "May write current epoch data.")
        void clear(boolean flushpoint) {
            // if (!flushpoint) {
            //    table.clear();
            // }
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
