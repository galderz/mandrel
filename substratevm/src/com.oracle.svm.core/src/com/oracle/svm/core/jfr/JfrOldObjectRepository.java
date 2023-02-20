package com.oracle.svm.core.jfr;

import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class JfrOldObjectRepository implements JfrConstantPool {
    // TODO key on weak refs rather than obj to avoid leaks?
    public final Map<Object, OldObjectInfo> oldObjects = new HashMap<>();
    public final Map<Object, OldObjectField> oldObjectFields = new HashMap<>();
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


        JfrTypeRepository typeRepo = SubstrateJVM.getTypeRepository();

        long referenceCount = 0;

        writer.writeCompressedLong(JfrType.OldObject.getId());
        writer.writeCompressedLong(oldObjects.size());
        for (Map.Entry<Object, OldObjectInfo> entry : oldObjects.entrySet()) {
            final OldObjectInfo info = entry.getValue();
            final Object object = entry.getKey();
            writer.writeCompressedLong(info.id); // id
            writer.writeCompressedLong(Word.objectToUntrackedPointer(object).rawValue()); // address
            writer.writeCompressedLong(typeRepo.getClassId(object.getClass())); // class
            writer.writeCompressedLong(0); // todo description

            long referenceId;
            if (info.parent == null) {
                referenceId = 0;
            } else {
                referenceId = info.id;
                referenceCount++;
            }
            writer.writeCompressedLong(referenceId);
        }

        if (!oldObjectFields.isEmpty()) {
            writer.writeCompressedLong(JfrType.OldObjectField.getId());
            writer.writeCompressedLong(oldObjectFields.size());
            for (OldObjectField entry : oldObjectFields.values()) {
                writer.writeCompressedLong(entry.id);
                writer.writeString(entry.fieldName);
                writer.writeCompressedInt(entry.fieldModifiers);
            }
        }

        if (referenceCount > 0) {
            writer.writeCompressedLong(JfrType.Reference.getId());
            writer.writeCompressedLong(referenceCount);
            for (Map.Entry<Object, OldObjectInfo> entry : oldObjects.entrySet()) {
                final OldObjectInfo info = entry.getValue();
                if (info.parent != null) {
                    final Object object = entry.getKey();
                    writer.writeCompressedLong(info.id); // id
                    writer.writeCompressedLong(0); // todo array info id
                    writer.writeCompressedLong(oldObjectFields.get(object).id); // field info id
                    writer.writeCompressedLong(oldObjects.get(info.parent).id); // (parent) old object sample id
                    writer.writeCompressedLong(0); // todo handle skip edges
                }
            }
        }

        return NON_EMPTY;
    }

    void addOldObject(Object object) {
        oldObjects.putIfAbsent(object, new OldObjectInfo(idCounter++, -1, null));
    }

    void addOldObjectsInPathToGcRoot(PathToGcRoots.PathElement[] pathToGcRoot) {
        final Object gcRoot = pathToGcRoot[pathToGcRoot.length - 1].getObject();
        final long gcRootId = idCounter++;
        oldObjects.putIfAbsent(gcRoot, new OldObjectInfo(gcRootId, gcRootId, null));

        for (int i = 0; i < pathToGcRoot.length - 1; i++) {
            final PathToGcRoots.PathElement currentPathElem = pathToGcRoot[i];
            Object current = currentPathElem.getObject();
            Object parent = pathToGcRoot[i + 1].getObject();

            if (currentPathElem instanceof PathToGcRoots.HeapElement) {
                final PathToGcRoots.HeapElement currentHeapElem = (PathToGcRoots.HeapElement) currentPathElem;
                oldObjectFields.putIfAbsent(current, new OldObjectField(idCounter++, currentHeapElem.fieldName, currentHeapElem.fieldModifiers));
            }

            oldObjects.putIfAbsent(current, new OldObjectInfo(idCounter++, gcRootId, parent));
        }
    }

    long getOldObjectId(Object object) {
        final OldObjectInfo info = oldObjects.get(object);
        return Objects.isNull(info) ? 0 : info.id;
    }

    void clear() {
        oldObjects.clear();
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
        private final Object parent;

        private OldObjectInfo(long id, long gcRootId, Object parent) {
            this.id = id;
            this.gcRootId = gcRootId;
            this.parent = parent;
        }
    }

    private static final class OldObjectField {
        private final long id;
        private final String fieldName;
        private final int fieldModifiers;

        private OldObjectField(long id, String fieldName, int fieldModifiers) {
            this.id = id;
            this.fieldName = fieldName;
            this.fieldModifiers = fieldModifiers;
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
