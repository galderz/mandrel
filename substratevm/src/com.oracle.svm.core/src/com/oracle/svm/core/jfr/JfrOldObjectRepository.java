package com.oracle.svm.core.jfr;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.AbstractUninterruptibleHashtable;
import com.oracle.svm.core.jdk.UninterruptibleEntry;
import com.oracle.svm.core.jfr.utils.JfrVisited;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;

import java.util.Map;

final class JfrOldObjectRepository implements JfrConstantPool {
    private final JfrOldObjectTable oldObjects = new JfrOldObjectTable();
    private long idCounter;
//    private JfrBuffer oldObjectBuffer;
//    private int numberOfSerializedOldObjects; // todo is oldObjects.size() not enough?

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrOldObjectRepository() {
    }

    @Override
    public int write(JfrChunkWriter writer) {
//        writer.writeCompressedLong(JfrType.OldObject.getId());
//        writer.writeCompressedInt(numberOfSerializedOldObjects);
//        writer.write(oldObjectBuffer);

//        SignedWord start = beginEvent();
//        writeCompressedLong(CONSTANT_POOL_TYPE_ID);
//        writeCompressedLong(JfrTicks.elapsedTicks());
//        writeCompressedLong(0); // duration
//        writeCompressedLong(0); // deltaToNext
//        writeBoolean(true); // flush
//
//        SignedWord poolCountPos = getFileSupport().position(fd);
//        getFileSupport().writeInt(fd, 0); // We'll patch this later.
//        JfrConstantPool[] serializers = JfrSerializerSupport.get().getSerializers();
//        int poolCount = writeConstantPools(serializers) + writeConstantPools(repositories);
//        SignedWord currentPos = getFileSupport().position(fd);
//        getFileSupport().seek(fd, poolCountPos);
//        getFileSupport().writeInt(fd, makePaddedInt(poolCount));
//        getFileSupport().seek(fd, currentPos);
//        endEvent(start);


        writer.writeCompressedLong(JfrType.OldObject.getId());
        writer.writeCompressedLong(oldObjects.getSize());
        JfrTypeRepository typeRepo = SubstrateJVM.getTypeRepository();
        final JfrOldObject[] entries = oldObjects.getTable();
        for (int i = 0; i < entries.length; i++) {
            final JfrOldObject entry = entries[i];
            final Pointer pointer = entry.getOldObject();
            writer.writeCompressedLong(entry.getId()); // id
            writer.writeCompressedLong(pointer.rawValue()); // address
            writer.writeCompressedLong(typeRepo.getClassId(pointer.toObject().getClass())); // class
            writer.writeCompressedLong(0); // todo description
            writer.writeCompressedLong(0); // todo write reference
        }

        return NON_EMPTY;
    }

    void addOldObject(Pointer pointer) {
        final JfrOldObject entry = StackValue.get(JfrOldObject.class);
        entry.setOldObject(pointer);

        // todo check if object already added, e.g. added during heap traversal?
        oldObjects.getOrPut(entry);
    }

    long getOldObjectId(Pointer pointer) {
        final JfrOldObject entry = StackValue.get(JfrOldObject.class);
        entry.setOldObject(pointer);

        final JfrOldObject result = (JfrOldObject) oldObjects.get(entry);
        return result.isNonNull() ? result.getId() : 0;
    }

//    @Uninterruptible(reason = "Accesses a JFR buffer.")
//    void serializeOldObjects() {
//        JfrTypeRepository typeRepo = SubstrateJVM.getTypeRepository();
//        for (Map.Entry<Object, OldObjectInfo> entry : oldObjects.entrySet()) {
//            final Object object = entry.getKey();
//            final OldObjectInfo info = entry.getValue();
//            serializeOldObject(object, info, typeRepo);
//        }
//    }

//    @Uninterruptible(reason = "Accesses a JFR buffer.")
//    private void serializeOldObject(Object object, OldObjectInfo info, JfrTypeRepository typeRepo) {
//        JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
//        JfrNativeEventWriterDataAccess.initialize(data, oldObjectBuffer);
//        JfrNativeEventWriter.putLong(data, info.id); // id
//        JfrNativeEventWriter.putLong(data, Word.objectToUntrackedPointer(object).rawValue()); // address
//        JfrNativeEventWriter.putLong(data, typeRepo.getClassId(object.getClass())); // class
//        JfrNativeEventWriter.putLong(data, 0); // todo description
//        JfrNativeEventWriter.putLong(data, 0); // todo write reference
//        numberOfSerializedOldObjects++;
//        JfrNativeEventWriter.commit(data);
//
//        /*
//         * Maybe during writing, the thread buffer was replaced with a new (larger) one,
//         * so we need to update the repository pointer as well.
//         */
//        oldObjectBuffer = data.getJfrBuffer();
//    }

    private static final class OldObjectInfo {
        private final long id;
        private final long gcRootId;
        private final OldObjectInfo parent;

        private OldObjectInfo(long id, long gcRootId, OldObjectInfo parent) {
            this.id = id;
            this.gcRootId = gcRootId;
            this.parent = parent;
        }
    }

    @RawStructure
    public interface JfrOldObject extends JfrVisited {
        @RawField
        Pointer getOldObject();

        @RawField
        void setOldObject(Pointer pointer);

        // todo add gc root id
        // todo add parent
    }

    private static final class JfrOldObjectTable extends AbstractUninterruptibleHashtable {
        private long nextId;

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected JfrOldObject[] createTable(int length) {
            return new JfrOldObject[length];
        }

        @Override
        public JfrOldObject[] getTable() {
            return (JfrOldObject[]) super.getTable();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isEqual(UninterruptibleEntry a, UninterruptibleEntry b) {
            final JfrOldObject entry1 = (JfrOldObject) a;
            final JfrOldObject entry2 = (JfrOldObject) b;
            return entry1.getId() == entry2.getId();
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry valueOnStack) {
            JfrOldObject result = (JfrOldObject) copyToHeap(valueOnStack, SizeOf.unsigned(JfrOldObject.class));
            result.setId(++nextId);
            return result;
        }
    }
}
