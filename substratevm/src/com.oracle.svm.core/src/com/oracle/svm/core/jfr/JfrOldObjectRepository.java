package com.oracle.svm.core.jfr;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.AbstractUninterruptibleHashtable;
import com.oracle.svm.core.jdk.UninterruptibleEntry;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.SignedWord;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

final class JfrOldObjectRepository implements JfrConstantPool {
    public final Map<Object, OldObjectInfo> oldObjects = new HashMap<>();
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
        writer.writeCompressedLong(oldObjects.size());
        JfrTypeRepository typeRepo = SubstrateJVM.getTypeRepository();
        for (Map.Entry<Object, OldObjectInfo> entry : oldObjects.entrySet()) {
            final Object object = entry.getKey();
            final OldObjectInfo info = entry.getValue();
            writer.writeCompressedLong(info.id); // id
            writer.writeCompressedLong(Word.objectToUntrackedPointer(object).rawValue()); // address
            writer.writeCompressedLong(typeRepo.getClassId(object.getClass())); // class
            writer.writeCompressedLong(0); // todo description
            writer.writeCompressedLong(0); // todo write reference
        }

        return NON_EMPTY;
    }

    void addOldObject(Object object) {
        // todo check if object already added, e.g. added during heap traversal?
        oldObjects.put(object, new OldObjectInfo(idCounter++, -1, null));
    }

    long getOldObjectId(Object object) {
        final OldObjectInfo info = oldObjects.get(object);
        return Objects.isNull(info) ? 0 : info.id;
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

//    private static final class OldObjecTable extends AbstractUninterruptibleHashtable {
//
//        @Override
//        protected UninterruptibleEntry[] createTable(int length) {
//            return new UninterruptibleEntry[0];  // TODO: Customise this generated block
//        }
//
//        @Override
//        protected boolean isEqual(UninterruptibleEntry a, UninterruptibleEntry b) {
//            return false;  // TODO: Customise this generated block
//        }
//
//        @Override
//        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry valueOnStack) {
//            return null;  // TODO: Customise this generated block
//        }
//    }
}
