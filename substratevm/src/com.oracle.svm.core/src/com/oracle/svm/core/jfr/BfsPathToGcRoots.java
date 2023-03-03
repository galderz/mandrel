package com.oracle.svm.core.jfr;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.WeakIdentityHashMap;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.VMOperation;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class BfsPathToGcRoots {
    private static final ImageHeapRootsRefVisitor bootImageHeapObjRefVisitor = new ImageHeapRootsRefVisitor();
    private static final HeapObjRefVisitor heapObjRefVisitor = new HeapObjRefVisitor();
    private static final HeapObjectVisitor heapObjectVisitor = new HeapObjectVisitor();

    List<Path> findPathToGcRoots(Set<Object> targets) {
        List<Path> results = new ArrayList<>(targets.size());
        new FindGcRootsToObjectsOperation(this, targets, results).enqueue();
        System.out.println(results);
        return results;
    }

    private void findPaths(Set<Object> targets, List<Path> results) {
        final Set<Object> seen = Collections.newSetFromMap(new WeakIdentityHashMap<>()); // todo switch to identity hash map

        final int classCount = Heap.getHeap().getLoadedClasses().size();
        final int queueCapacity = classCount * 1024;
        final EdgeQueue queue = new EdgeQueue(queueCapacity);

        final Set<Object> roots = findRoots(queue);
        findPathFromRoot(roots, queue, targets, results, seen);

//        BatchPathEdges batch = new BatchPathEdges(classList.size());
//        for (int i = 0; i < classList.size(); i++) {
//            final Class<?> clazz = classList.get(i);
//            final Object[][] fields = oldObjectUtils.getOldObjectFields(clazz);
//            final Object[][] edges = batch.createPathEdges(fields.length);
//            for (int j = 0; j < fields.length; j++) {
//                BatchPathEdges.setFrom(clazz, edges[i]);
//                BatchPathEdges.setLocation(JfrOldObjectUtils.getFieldLocation(fields[i]), edges[i]);
//            }
//        }
    }

    private static Set<Object> findRoots(EdgeQueue queue) {
        final Log log = Log.log();
        log.string("BfsPathToGcRoots.findRoots").newline();
        final ImageHeapRootsVisitor visitor = new ImageHeapRootsVisitor(queue);
        Heap.getHeap().walkImageHeapObjects(visitor);
        log.string("BfsPathToGcRoots.findRoots roots queue size ").unsigned(queue.size()).newline();
        final Set<Object> roots = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < queue.size(); i++) {
            final Object to = queue.getTo(i);
            roots.add(to);
        }
        queue.clear();
        return roots;
    }

//    void findBfs(BatchPathEdges result) {
//        assert VMOperation.isInProgressAtSafepoint();
//        for (; /* break */ ; ) {
//            // Walk bfs one step at the time (first invocation is from roots)
//            findPathToTarget(new ObjectTargetMatcher(currentTargetObj), currentEdge, currentThreadWalkStackPointer);
//
//            PathElement currentElement = null;
//            if (currentEdge.isFilled()) {
//                currentElement = currentEdge.getFrom();
//                if (path.isEmpty()) {
//                    path.add(currentEdge.getTo());
//                }
//            }
//            if (currentElement == null) {
//                // No pointer to current object: The path ends here.
//                break;
//            }
//            currentTargetObj = currentElement.getObject();
//            if (currentTargetObj == null) {
//                // Current element is a root: Add element to path and stop.
//                path.add(currentElement);
//                break;
//            }
//            if (checkForCycles(currentTargetObj)) { // seen before
//                CyclicElement cyclic = new CyclicElement(currentTargetObj);
//                path.add(cyclic);
//                break;
//            }
//            path.add(currentElement);
//        }
//    }

//    private static void findPathToTarget(BatchPathEdges result) {
//        // assert target != null && !edge.isFilled();
//        findBfsInHeap(result);
//        // findPathInImageHeap(target, edge);
//        // findPathInStack(target, edge, currentThreadWalkStackPointer);
//    }

//    private static void findPathInHeap(BatchPathEdges result) {
//        heapObjectVisitor.initialize(target, result);
//        Heap.getHeap().walkObjects(heapObjectVisitor);
//    }
//
//    private static class HeapObjectVisitor implements ObjectVisitor {
//        private BatchPathEdges result;
//
//        HeapObjectVisitor() {
//        }
//
//        void initialize(BatchPathEdges result) {
//            this.result = result;
//        }
//
//        @Override
//        public boolean visitObject(Object containerObject) {
//            Pointer containerPointer = Word.objectToUntrackedPointer(containerObject);
//            heapObjRefVisitor.initialize(containerPointer, result);
//            return InteriorObjRefWalker.walkObject(containerObject, heapObjRefVisitor);
//        }
//    }
//
//    private static class HeapObjRefVisitor implements ObjectReferenceVisitor {
//        private Pointer containerPointer;
//
//        HeapObjRefVisitor() {
//        }
//
//        @NeverInline("Starting a stack walk in the caller frame")
//        static boolean isInterfering(Object currentObject) {
//            return currentObject instanceof BfsFindPathToObjectOperation;
//        }
//
//        public void initialize(Pointer container, TargetMatcher targetMatcher, PathEdge edge) {
//            super.initialize(targetMatcher, edge);
//            containerPointer = container;
//        }
//
//        @Override
//        public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
//            if (objRef.isNull()) {
//                return true;
//            }
//            Object containerObject = containerPointer.toObject();
//            if (!isInterfering(containerObject)) {
//                Pointer referentPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
//                if (target.matches(referentPointer.toObject())) {
//                    UnsignedWord offset = objRef.subtract(containerPointer);
//                    result.fill(new HeapElement(containerObject, offset), new LeafElement(referentPointer.toObject()));
//                    return false;
//                }
//            }
//            return true;
//        }
//    }
//
//    private static final class BfsFindPathToObjectOperation extends JavaVMOperation {
//        private final BfsPathToGcRoots pathToGcRoots;
//        private final BatchPathEdges result;
//
//        BfsFindPathToObjectOperation(BatchPathEdges result) {
//            super(VMOperationInfos.get(BfsFindPathToObjectOperation.class, "TBD", SystemEffect.SAFEPOINT));
//            this.result = result;
//        }
//
//        @Override
//        @NeverInline("Starting a stack walk.")
//        protected void operate() {
//            pathToGcRoots.findBfs(result);
//        }
//    }

    private void findPathFromRoot(Set<Object> roots, EdgeQueue queue, Set<Object> samples, List<Path> results, Set<Object> seen) {
        assert VMOperation.isInProgressAtSafepoint();

        int iteration = 0;
        Log log = Log.log();
        for (; /* break */;) {
            log.string("Iteration ").unsigned(iteration++).newline();
//            log.string("Roots size ").unsigned(roots.size()).newline();
//            if (roots.size() < 10) {
//                log.string("Roots: ").string(roots.toString()).newline();
//            }


            // Walk backwards one step.
            // currentEdge.reset();
            findPathToTarget(roots, queue);

            // Roots have been processed, clear them for next iteration
            roots.clear();

//            if (queue.isFull()) {
//                log.string("Queue is full").newline();
//                break; // todo fallback using DFS
//            }

//            log.string("Queue size ").unsigned(queue.size()).newline();

            // Iterate to:
            // Fill paths travelled so far
            // If any targets have been found, add paths to them to results
            // If a path does not contain a target, add it to the next iteration
            for (int i = 0; i < queue.size(); i++) {
                final Object from = queue.getFrom(i);
                final Object to = queue.getTo(i);
                final UnsignedWord location = queue.getLocation(i);

                if (samples.contains(to)) {
                    // TODO need path from all the way to the root, not just last link
                    final Path path = new Path(from);
                    path.to = new Path(to);
                    path.location = location;
                    results.add(path);
                } else if (seen.add(to)){
                    log.zhex(Word.objectToTrackedPointer(to).rawValue()).newline();
                    roots.add(to);
                } else {
                    log.string("Already seen: ").zhex(Word.objectToTrackedPointer(to).rawValue()).newline();
                }
            }

            if (roots.isEmpty()) {
                break;
            }

//            log.string("Clear queue").newline();
            queue.clear();

            // todo check for cycles
        }

        log.flush();
    }

    private static void findPathToTarget(Set<Object> roots, EdgeQueue queue) {
        // assert target != null && !edge.isFilled();
        findPathInHeap(roots, queue);
//        findPathInImageHeap(target, edge);
//        findPathInStack(target, edge, currentThreadWalkStackPointer);
    }

    private static void findPathInHeap(Set<Object> roots, EdgeQueue queue) {
//        if (result.isFilled()) {
//            return;
//        }
        heapObjectVisitor.initialize(roots, queue);
        Heap.getHeap().walkObjects(heapObjectVisitor);
    }

    private static class ImageHeapRootsVisitor implements ObjectVisitor {
        private final EdgeQueue queue;

        public ImageHeapRootsVisitor(EdgeQueue queue) {
            this.queue = queue;
        }

        @Override
        public boolean visitObject(Object obj) {
            queue.push(obj);
            bootImageHeapObjRefVisitor.initialize(queue);
            return InteriorObjRefWalker.walkObject(obj, bootImageHeapObjRefVisitor);
        }
    }

    private static class ImageHeapRootsRefVisitor implements ObjectReferenceVisitor {
        private EdgeQueue queue;

        void initialize(EdgeQueue queue) {
            this.queue = queue;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
            if (objRef.isNull()) {
                return true;
            }
            Object referent = ReferenceAccess.singleton().readObjectAt(objRef, compressed);
            queue.push(referent);
            return true;
        }
    }

    private static class HeapObjectVisitor implements ObjectVisitor {
        private Set<Object> roots;
        private EdgeQueue queue;

        HeapObjectVisitor() {
        }

        public void initialize(Set<Object> roots, EdgeQueue queue) {
            this.roots = roots;
            this.queue = queue;
        }

        @Override
        public boolean visitObject(Object containerObject) {
            Pointer containerPointer = Word.objectToUntrackedPointer(containerObject);
            heapObjRefVisitor.initialize(containerPointer, roots, queue);
            return InteriorObjRefWalker.walkObject(containerObject, heapObjRefVisitor);
        }
    }

    private static class HeapObjRefVisitor implements ObjectReferenceVisitor {
        private Pointer containerPointer;
        private Set<Object> roots;
        private EdgeQueue queue;

        HeapObjRefVisitor() {
        }

        @NeverInline("Starting a stack walk in the caller frame")
        static boolean isInterfering(Object currentObject) {
            // return currentObject instanceof PathElement || currentObject instanceof FindPathToObjectOperation || currentObject instanceof TargetMatcher;
            return currentObject instanceof EdgeQueue || currentObject instanceof FindGcRootsToObjectsOperation;
        }

        public void initialize(Pointer container, Set<Object> roots, EdgeQueue queue) {
            this.containerPointer = container;
            this.roots = roots;
            this.queue = queue;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
            if (objRef.isNull()) {
                return true;
            }
            Object containerObject = containerPointer.toObject();
            if (!isInterfering(containerObject)) {
                Pointer referentPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
                if (roots.contains(containerObject)) {
                    UnsignedWord offset = objRef.subtract(containerPointer);
                    queue.push(containerObject, offset, referentPointer.toObject());
                }
            }
            return true;
        }
    }

    private static final class EdgeQueue {
        private static final int FROM_SLOT = 0;
        private static final int TO_SLOT = 1;

        private final Object[][] edges;
        private final UnsignedWord[] locations;

        private int count;

        EdgeQueue(int capacity)
        {
            this.edges = new Object[capacity][];
            for (int i = 0; i < this.edges.length; i++)
            {
                this.edges[i] = new Object[TO_SLOT + 1];
            }
            this.locations = new UnsignedWord[capacity];
        }

        boolean isFull() {
            return count == edges.length;
        }

        void push(Object from, UnsignedWord location, Object to)
        {
            set(from, location, to, count);
            count++;
        }

        void push(Object to)
        {
            set(to, count);
            count++;
        }

        int size() {
            return count;
        }

        Object getFrom(int index) {
            return edges[index][FROM_SLOT];
        }

        UnsignedWord getLocation(int index) {
            return locations[index];
        }

        Object getTo(int index) {
            return edges[index][TO_SLOT];
        }

        void clear() {
            count = 0;
        }

        private void set(Object from, UnsignedWord location, Object to, int index)
        {
            edges[index][FROM_SLOT] = from;
            edges[index][TO_SLOT] = to;
            locations[index] = location;
        }

        private void set(Object to, int index)
        {
            edges[index][TO_SLOT] = to;
        }
    }

    private static final class FindGcRootsToObjectsOperation extends JavaVMOperation {
        private final BfsPathToGcRoots bfs;
        private final Set<Object> targets;
        private final List<Path> results;

        private FindGcRootsToObjectsOperation(BfsPathToGcRoots bfs, Set<Object> targets, List<Path> results) {
            super(VMOperationInfos.get(FindGcRootsToObjectsOperation.class, "TBD", SystemEffect.SAFEPOINT));
            this.bfs = bfs;
            this.targets = targets;
            this.results = results;
        }

//        FindPathToObjectOperation(PathExhibitor exhibitor, Object object, PathEdge result) {
//            super(VMOperationInfos.get(FindPathToObjectOperation.class, "Find path to object", SystemEffect.SAFEPOINT));
//            this.exhibitor = exhibitor;
//            this.object = object;
//            this.results = result;
//        }

        @Override
        @NeverInline("Starting a stack walk.")
        protected void operate() {
//            Log log = Log.log();
//            log.string("[start] BFS GC root path search").newline();
            bfs.findPaths(targets, results);
//            log.string("[end] BFS GC root path search").newline();
//            log.flush();
        }
    }

//    private static final class BatchPathEdges {
//        private static final int FROM_SLOT = 0;
//        private static final int TO_SLOT = 1;
//        private static final int LOCATION_SLOT = 2;
//
//        final List<Object[][]> edges;
//
//        private BatchPathEdges(int size) {
//            this.edges = new ArrayList<>(size);
//        }
//
//        Object[][] createPathEdges(int numFields) {
//            final Object[][] fieldEdges = new Object[numFields][];
//            for (int i = 0; i < fieldEdges.length; i++) {
//                fieldEdges[i] = new Object[LOCATION_SLOT + 1];
//            }
//            this.edges.add(fieldEdges);
//            return fieldEdges;
//        }
//
//        static void setFrom(Object obj, Object[] edge) {
//            edge[FROM_SLOT] = obj;
//        }
//
//        static void setLocation(int location, Object[] edge) {
//            edge[LOCATION_SLOT] = location;
//        }
//    }

//    private static final PathElement {
//
//    }

    private static final class Path {
        private final Object from;
        private Path to;
        private UnsignedWord location;

        private Path(Object from) {
            this.from = from;
        }
    }
}
