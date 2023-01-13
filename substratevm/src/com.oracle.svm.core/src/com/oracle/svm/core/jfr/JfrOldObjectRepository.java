package com.oracle.svm.core.jfr;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.struct.PinnedObjectField;
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

final class JfrOldObjectRepository implements JfrConstantPool {
    private final JfrOldObjectTable oldObjects = new JfrOldObjectTable();

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrOldObjectRepository() {
    }

    @Override
    public int write(JfrChunkWriter writer) {
        System.out.println("JfrOldObjectRepository::write - entry");
        if (oldObjects.getSize() == 0) {
            return EMPTY;
        }

        writer.writeCompressedLong(JfrType.OldObject.getId());
        writer.writeCompressedLong(oldObjects.getSize());
        System.out.printf("JfrOldObjectRepository::write - %d old objects to write%n", oldObjects.getSize());
        JfrTypeRepository typeRepo = SubstrateJVM.getTypeRepository();
        final JfrOldObject[] entries = oldObjects.getTable();
        for (int i = 0; i < entries.length; i++) {
            JfrOldObject entry = entries[i];
            while (entry.isNonNull()) {
                writer.writeCompressedLong(entry.getId()); // id
                writer.writeCompressedLong(entry.getOldObject().rawValue()); // address
                writer.writeCompressedLong(typeRepo.getClassId(entry.getOldObjectClass())); // class
                writer.writeCompressedLong(0); // todo description
                writer.writeCompressedLong(0); // todo write reference
                entry = entry.getNext();
            }
        }
        // todo clear the table?

        return NON_EMPTY;
    }

    void addOldObject(Pointer pointer, Class<?> clazz) {
        final JfrOldObject entry = StackValue.get(JfrOldObject.class);
        entry.setOldObject(pointer);
        entry.setOldObjectClass(clazz);
        entry.setHash(pointerHashCode(pointer));

        // todo check if object already added, e.g. added during heap traversal?
        oldObjects.getOrPut(entry);
    }

    private static int pointerHashCode(Pointer pointer) {
        final long rawPointerValue = pointer.rawValue();
        return (int) (rawPointerValue ^ (rawPointerValue >>> 32));
    }

    long getOldObjectId(Pointer pointer) {
        final JfrOldObject entry = StackValue.get(JfrOldObject.class);
        entry.setOldObject(pointer);
        entry.setHash(pointerHashCode(pointer));

        final JfrOldObject result = (JfrOldObject) oldObjects.get(entry);
        return result.isNonNull() ? result.getId() : 0;
    }

    @RawStructure
    public interface JfrOldObject extends JfrVisited {
        @RawField
        Pointer getOldObject();

        @RawField
        void setOldObject(Pointer pointer);

        @PinnedObjectField
        @RawField
        Class<?> getOldObjectClass();

        @PinnedObjectField
        @RawField
        void setOldObjectClass(Class<?> clazz);

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
            return entry1.getOldObject().rawValue() == entry2.getOldObject().rawValue();
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