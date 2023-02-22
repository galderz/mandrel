package com.oracle.svm.core.jfr;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.thread.JavaVMOperation;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LeakToGcRoots {
    private static final HeapObjRefVisitor heapObjRefVisitor = new HeapObjRefVisitor();
    private static final HeapObjectVisitor heapObjectVisitor = new HeapObjectVisitor();

    Map<ObjectField, Object> edges = new IdentityHashMap<>();

    void findPaths() {
        System.out.println("[enter] LeakToGcRoots.findPaths start");
        final long start = System.nanoTime();
        new PopulateEdges(edges).enqueue();
        final long end = System.nanoTime();
        System.out.println("Edges size: " + edges.size());
        System.out.printf("[exit] LeakToGcRoots.findPaths after %d seconds%n", TimeUnit.NANOSECONDS.toSeconds(end - start));
    }

    private static final class PopulateEdges extends JavaVMOperation {
        private final Map<ObjectField, Object> edges;

        protected PopulateEdges(Map<ObjectField, Object> edges) {
            super(VMOperationInfos.get(PopulateEdges.class, "TBD", SystemEffect.SAFEPOINT));
            this.edges = edges;
        }

        @Override
        protected void operate() {
            heapObjectVisitor.initialize(edges);
            Heap.getHeap().walkObjects(heapObjectVisitor);
            // todo do I need to walk image heap?
            // todo do I need to walk the stack?
        }
    }

    private static final class HeapObjectVisitor implements ObjectVisitor {
        private Map<ObjectField, Object> edges;

        HeapObjectVisitor() {
        }

        void initialize(Map<ObjectField, Object> edges) {
            this.edges = edges;
        }

        @Override
        public boolean visitObject(Object containerObject) {
            Pointer containerPointer = Word.objectToUntrackedPointer(containerObject);
            heapObjRefVisitor.initialize(containerPointer, edges);
            return InteriorObjRefWalker.walkObject(containerObject, heapObjRefVisitor);
        }
    }

    private static class HeapObjRefVisitor implements ObjectReferenceVisitor {
        private Pointer containerPointer;
        private Map<ObjectField, Object> edges;

        HeapObjRefVisitor() {
        }

        @NeverInline("Starting a stack walk in the caller frame")
        static boolean isInterfering(Object currentObject) {
            return currentObject instanceof PopulateEdges;
        }

        public void initialize(Pointer container, Map<ObjectField, Object> edges) {
            this.containerPointer = container;
            this.edges = edges;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
            if (objRef.isNull()) {
                return true;
            }
            Object containerObject = containerPointer.toObject();
            if (!isInterfering(containerObject)) {
                Pointer referentPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
                UnsignedWord offset = objRef.subtract(containerPointer);
                edges.put(new ObjectField(containerObject, offset), referentPointer.toObject());
                // edges.computeIfAbsent(containerObject, x -> new ArrayList<>()).add(new FieldRef(offset, referentPointer.toObject()));
            }
            return true;
        }
    }

    private static final class ObjectField {
        private final Object object;
        private final UnsignedWord offset;

        private ObjectField(Object object, UnsignedWord offset) {
            this.object = object;
            this.offset = offset;
        }
    }

//    private static final class FieldRef {
//        /**
//         * Offset within container object.
//         */
//        private final UnsignedWord offset;
//
//        /**
//         * Referenced object from offset in container object.
//         */
//        private final Object ref;
//
//        private FieldRef(UnsignedWord offset, Object ref) {
//            this.offset = offset;
//            this.ref = ref;
//        }
//    }
}
