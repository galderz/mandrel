package com.oracle.svm.core.jfr;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.hub.InteriorObjRefWalker;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.JavaVMOperation;
import org.graalvm.compiler.word.Word;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public class BfsPathToGcRoots {
    private static final ImageHeapRootsRefVisitor bootImageHeapObjRefVisitor = new ImageHeapRootsRefVisitor();
    private static final ObjectRefHighBitsVisitor addressHighBitsObjRefVisitor = new ObjectRefHighBitsVisitor();
    private static final HeapObjRefVisitor heapObjRefVisitor = new HeapObjRefVisitor();
    private static final HeapObjectVisitor heapObjectVisitor = new HeapObjectVisitor();

    void findPathToGcRoots(Set<Object> targets, PathToGcRootsStore pathStore) {
        new FindGcRootsToObjectsOperation(this, targets, pathStore).enqueue();
    }

    private void findPaths(Set<Object> targets, PathToGcRootsStore pathStore) {
        final int classCount = Heap.getHeap().getLoadedClasses().size();
        final int queueCapacity = classCount * (1 << 10);
        final EdgeQueue queue = new EdgeQueue(queueCapacity);

//        final Set<Object> roots = findRoots(queue);
//        queue.clear();

        final int objectAlignment = ConfigurationValues.getObjectLayout().getAlignment();
        final BitMap bitMap = new BitMap(objectAlignment);
        final HighBitMap highBits = markHighBits(bitMap);
        final LowBitMap lowBits = new LowBitMap(highBits.getHighBitIndexes(), bitMap);
        findPathEdges(queue, lowBits);

        storePaths(targets, queue, pathStore);
        showEdges(queue);
    }

    private void showEdges(EdgeQueue queue) {
        final Log log = Log.log();
        log.string("BfsPathToGcRoots.showEdges").newline();
        for (int i = 0; i < queue.edges.length; i++) {
            final Object from = queue.getFrom(i);
            if (from != null) {
                final Object to = queue.getTo(i);
                log.string(show(from)).string("->").string(show(to)).newline();
            }
        }
    }

    private static String show(Object obj) {
        return obj == null
                ? "null"
                : obj.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(obj));
    }

    private void storePaths(Set<Object> targets, EdgeQueue queue, PathToGcRootsStore pathStore) {
        final Log log = Log.log();
        log.string("BfsPathToGcRoots.storePaths").newline();
        int leakIndex = 0;
        for (Object leak : targets) {
            storePath(leak, leakIndex++, queue, pathStore);
        }

        // for each root R, find the objects they point to at the next level creating a Path instance for those:
        // Path(R1, a), Path(R1, b), Path(R2, c)...etc
        // Then, take each of `to` in those paths, find objects to which they point:
        // Path(a, ...), Path(a, ...), Path()
        // or
        // for each leak target:
        // search through the queue until there's an edge entry whose "to" is pointing to the leak
        // extract the "from" and "link" it
    }

    private static void storePath(Object leak, int pathIndex, EdgeQueue queue, PathToGcRootsStore store) {
        final Log log = Log.log();
        log.string("BfsPathToGcRoots.storePath").newline();
        int elementIndex = 0;
        store.addPathElement(elementIndex++, WordFactory.zero(), leak, pathIndex);

        Object current = leak;
        Object from;
        int index;
        while ((index = queue.findTo(current)) >= 0 && (from = queue.getFrom(index)) != null) {
            UnsignedWord location = queue.getLocation(index);
            store.addPathElement(elementIndex++, location, from, pathIndex);
            current = from;
        }
        log.string("BfsPathToGcRoots.storePath with length ").unsigned(elementIndex).newline();
    }

    private void findPathEdges(EdgeQueue queue, LowBitMap lowBits) {
        final Log log = Log.log();
        log.string("BfsPathToGcRoots.findPathEdges").newline();
        // TODO: find path edges in stack
        heapObjectVisitor.initialize(queue, lowBits);
        Heap.getHeap().walkObjects(heapObjectVisitor);
        // Heap.getHeap().walkImageHeapObjects(heapObjectVisitor); todo already visited in walkObjets
        log.string("BfsPathToGcRoots.findPathEdges queue size ").unsigned(queue.size()).newline();
    }

    private HighBitMap markHighBits(BitMap bitMap) {
        final Log log = Log.log();
        log.string("BfsPathToGcRoots.markHighBits").newline();
        final HighBitMap highBits = new HighBitMap(bitMap);
        // todo mark high bits in stack
        markHighBitsInImageHeap(highBits);
        markHighBitsInHeap(highBits);
        log.string("BfsPathToGcRoots.markHighBits unique high bit indexes: ").string(highBits.getHighBitIndexes().toString()).newline();
        return highBits;
    }

    private static void markHighBitsInHeap(HighBitMap highBits) {
        final Log log = Log.log();
        log.string("BfsPathToGcRoots.markHighBitsInHeap").newline();
        Heap.getHeap().walkObjects(new ObjectHighBitsVisitor(highBits));
        log.string("BfsPathToGcRoots.markHighBitsInHeap unique high bit indexes: ").string(highBits.getHighBitIndexes().toString()).newline();
    }

    private static void markHighBitsInImageHeap(HighBitMap highBits) {
        final Log log = Log.log();
        log.string("BfsPathToGcRoots.markHighBitsInImageHeap").newline();
        Heap.getHeap().walkImageHeapObjects(new ObjectHighBitsVisitor(highBits));
        log.string("BfsPathToGcRoots.markHighBitsInImageHeap unique high bit indexes: ").string(highBits.getHighBitIndexes().toString()).newline();
    }

//    private static Set<Object> findRoots(EdgeQueue queue) {
//        final Log log = Log.log();
//        log.string("BfsPathToGcRoots.findRoots").newline();
//        // todo find roots in stack frames, thread locals and code cache
//        final ImageHeapRootsVisitor visitor = new ImageHeapRootsVisitor(queue);
//        Heap.getHeap().walkImageHeapObjects(visitor);
//        log.string("BfsPathToGcRoots.findRoots roots queue size ").unsigned(queue.size()).newline();
//        final Set<Object> roots = Collections.newSetFromMap(new IdentityHashMap<>());
//        for (int i = 0; i < queue.size(); i++) {
//            final Object to = queue.getTo(i);
//            roots.add(to);
//        }
//        return roots;
//    }

//    private void findPathFromRoot(Set<Object> roots, EdgeQueue queue, Set<Object> samples, List<Path> results, Set<Object> seen) {
//        assert VMOperation.isInProgressAtSafepoint();
//
//        int iteration = 0;
//        Log log = Log.log();
//        for (; /* break */;) {
//            log.string("Iteration ").unsigned(iteration++).newline();
////            log.string("Roots size ").unsigned(roots.size()).newline();
////            if (roots.size() < 10) {
////                log.string("Roots: ").string(roots.toString()).newline();
////            }
//
//
//            // Walk backwards one step.
//            // currentEdge.reset();
//            findPathToTarget(roots, queue);
//
//            // Roots have been processed, clear them for next iteration
//            roots.clear();
//
////            if (queue.isFull()) {
////                log.string("Queue is full").newline();
////                break; // todo fallback using DFS
////            }
//
////            log.string("Queue size ").unsigned(queue.size()).newline();
//
//            // Iterate to:
//            // Fill paths travelled so far
//            // If any targets have been found, add paths to them to results
//            // If a path does not contain a target, add it to the next iteration
//            for (int i = 0; i < queue.size(); i++) {
//                final Object from = queue.getFrom(i);
//                final Object to = queue.getTo(i);
//                final UnsignedWord location = queue.getLocation(i);
//
//                if (samples.contains(to)) {
//                    // TODO need path from all the way to the root, not just last link
//                    final Path path = new Path(from);
//                    path.to = new Path(to);
//                    path.location = location;
//                    results.add(path);
//                } else if (seen.add(to)){
//                    log.zhex(Word.objectToTrackedPointer(to).rawValue()).newline();
//                    roots.add(to);
//                } else {
//                    log.string("Already seen: ").zhex(Word.objectToTrackedPointer(to).rawValue()).newline();
//                }
//            }
//
//            if (roots.isEmpty()) {
//                break;
//            }
//
////            log.string("Clear queue").newline();
//            queue.clear();
//
//            // todo check for cycles
//        }
//
//        log.flush();
//    }

//    private static void findPathToTarget(Set<Object> roots, EdgeQueue queue) {
//        // assert target != null && !edge.isFilled();
//        findPathInHeap(roots, queue);
////        findPathInImageHeap(target, edge);
////        findPathInStack(target, edge, currentThreadWalkStackPointer);
//    }

//    private static void findPathInHeap(Set<Object> roots, EdgeQueue queue) {
////        if (result.isFilled()) {
////            return;
////        }
//        heapObjectVisitor.initialize(roots, queue);
//        Heap.getHeap().walkObjects(heapObjectVisitor);
//    }

    private static class ObjectHighBitsVisitor implements ObjectVisitor {
        private final HighBitMap highBits;

        private ObjectHighBitsVisitor(HighBitMap highBits) {
            this.highBits = highBits;
        }

        @Override
        public boolean visitObject(Object obj) {
            final long address = Word.objectToUntrackedPointer(obj).rawValue();
            highBits.mark(address);
            addressHighBitsObjRefVisitor.initialize(highBits);
            return InteriorObjRefWalker.walkObject(obj, addressHighBitsObjRefVisitor);
        }
    }

    private static class ObjectRefHighBitsVisitor implements ObjectReferenceVisitor {
        private HighBitMap highBits;

        void initialize(HighBitMap highBits) {
            this.highBits = highBits;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
            if (objRef.isNull()) {
                return true;
            }
            long referentAddress = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed).rawValue();

            highBits.mark(referentAddress);
            return true;
        }
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
        private EdgeQueue queue;
        private LowBitMap lowBits;

        HeapObjectVisitor() {
        }

        public void initialize(EdgeQueue queue, LowBitMap lowBits) {
            this.queue = queue;
            this.lowBits = lowBits;
        }

        @Override
        public boolean visitObject(Object containerObject) {
            Pointer containerPointer = Word.objectToUntrackedPointer(containerObject);
            if (lowBits.mark(containerPointer.rawValue())) {
                heapObjRefVisitor.initialize(containerPointer, queue, lowBits);
                InteriorObjRefWalker.walkObject(containerObject, heapObjRefVisitor);
            }
            return true;
        }
    }

    private static class HeapObjRefVisitor implements ObjectReferenceVisitor {
        private Pointer containerPointer;
        private LowBitMap lowBits;
        private EdgeQueue queue;

        HeapObjRefVisitor() {
        }

        @NeverInline("Starting a stack walk in the caller frame")
        static boolean isInterfering(Object currentObject) {
            // return currentObject instanceof PathElement || currentObject instanceof FindPathToObjectOperation || currentObject instanceof TargetMatcher;
            return currentObject instanceof EdgeQueue || currentObject instanceof FindGcRootsToObjectsOperation || currentObject instanceof LowBitMap;
        }

        public void initialize(Pointer container, EdgeQueue queue, LowBitMap lowBits) {
            this.containerPointer = container;
            this.queue = queue;
            this.lowBits = lowBits;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
            if (objRef.isNull()) {
                return true;
            }
            Object containerObject = containerPointer.toObject();
            if (!isInterfering(containerObject)) {
                Pointer referentPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);

//                queue.push(containerObject, referentPointer, referentPointer.toObject());

//                lowBits.mark(referentPointer.rawValue());

                if (lowBits.mark(referentPointer.rawValue())) {
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

        int findTo(Object target) {
            for (int i = 0; i < edges.length; i++) {
                final Object to = getTo(i);
                if (target.equals(to)) {
                    return i;
                }
            }
            return -1;
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
        private final PathToGcRootsStore pathStore;

        private FindGcRootsToObjectsOperation(BfsPathToGcRoots bfs, Set<Object> targets, PathToGcRootsStore pathStore) {
            super(VMOperationInfos.get(FindGcRootsToObjectsOperation.class, "TBD", SystemEffect.SAFEPOINT));
            this.bfs = bfs;
            this.targets = targets;
            this.pathStore = pathStore;
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
            bfs.findPaths(targets, pathStore);
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

    //  0                   1                   2                   3                   4                   5                   6
    //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3
    // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    // |S|                       High Bits                           |                     Low Bits                              | N/A |
    // +-------------------------------------------------------------+-----------------------------------------------------------+-----+
    static class BitMap
    {
        final int objectAlignment;
        final int logAlignment;
        final int lowBitsCount;
        final long lowSize;
        final long lowMask;
        final int highBitsShift;

        BitMap(int objectAlignment)
        {
            this.objectAlignment = objectAlignment;
            this.logAlignment = log2(objectAlignment);
            this.lowBitsCount = 30;
            this.lowSize = 1L << lowBitsCount;
            this.lowMask = lowSize - 1;
            this.highBitsShift = lowBitsCount + logAlignment;
        }

        int getLowBits(long num)
        {
            return (int) ((num >> logAlignment) & lowMask);
        }

        int getHighBits(long number)
        {
            return (int) (Math.abs(number) >> highBitsShift);
        }

        static int log2(int num)
        {
            return 31 - Integer.numberOfLeadingZeros(num);
        }
    }

    static class LowBitMap
    {
        final NoAllocFixedIntToObjectMap<NoAllocFixedBitSet> lowBitSets;
        final BitMap bitMap;
//        long count;

        LowBitMap(List<Integer> highBitIndexes, BitMap bitMap)
        {
            this.lowBitSets = new NoAllocFixedIntToObjectMap<>(highBitIndexes.size());
            for (int i = 0; i < highBitIndexes.size(); i++) {
                final Integer highBitIndex = highBitIndexes.get(i);
                lowBitSets.put(highBitIndex, new NoAllocFixedBitSet(1 << bitMap.lowBitsCount));
            }
            this.bitMap = bitMap;
        }

        /**
         * Mark the low bits in bit set for the number.
         * Returns true if low bits were marked as it was not marked before.
         * Returns false if low bits were not marked because it was already marked.
         */
        boolean mark(long number) {
            final int highBits = this.bitMap.getHighBits(number);
            final NoAllocFixedBitSet bitSet = lowBitSets.get(highBits);
            final int lowBits = this.bitMap.getLowBits(number);
            final boolean isMarked = bitSet.get(lowBits);
            if (isMarked) {
                return false;
            }

//            count = count + highBits % 2;
////            count = count + lowBits % 2;
//            count = count + bitSet.words.length % 2;

            bitSet.set(lowBits);
            return true;
        }
    }

    static class HighBitMap
    {
        final NoAllocFixedBitSet zeroLedBits;
        final NoAllocFixedBitSet oneLedBits;
        final BitMap bitMap;

        HighBitMap(BitMap bitMap)
        {
            int highBitsCount = 64 - bitMap.lowBitsCount - bitMap.logAlignment - 1;
            this.zeroLedBits = new NoAllocFixedBitSet(1 << highBitsCount);
            this.oneLedBits = new NoAllocFixedBitSet(1 << highBitsCount);
            this.bitMap = bitMap;
        }

        void mark(long number) {
            final int signum = getSignum(number);
            final int highBits = bitMap.getHighBits(number);
            if (signum < 0)
            {
                oneLedBits.set(highBits);
            }
            else
            {
                zeroLedBits.set(highBits);
            }
        }

        List<Integer> getHighBitIndexes() {
            final List<Integer> indexes = new ArrayList<>();
            addMarkedBitIndexes(zeroLedBits, indexes);
            addMarkedBitIndexes(oneLedBits, indexes);
            return indexes;
        }

        private static int getSignum(long number)
        {
            return Long.signum(number);
        }

        private void addMarkedBitIndexes(NoAllocFixedBitSet bitSet, List<Integer> indexes) {
            for (int i = bitSet.nextSetBit(0); i != -1; i = bitSet.nextSetBit(i + 1)) {
                indexes.add(i);
            }
        }
    }

    /**
     * A fixed-size, allocation free, int to object map.
     */
    static class NoAllocFixedIntToObjectMap<V>
    {
        private static final int MIN_CAPACITY = 4;

        private final int[] keys;
        private final Object[] values;
        private int size;

        NoAllocFixedIntToObjectMap()
        {
            this(MIN_CAPACITY);
        }

        NoAllocFixedIntToObjectMap(int initialCapacity)
        {
            int capacity = findNextPositivePowerOfTwo(Math.max(initialCapacity, MIN_CAPACITY));
            keys = new int[capacity];
            values = new Object[capacity];
        }

        int size()
        {
            return size;
        }

        boolean isFull()
        {
            return keys.length == size;
        }

        @SuppressWarnings("unchecked")
        V get(int key)
        {
            final int mask = values.length - 1;
            int index = hash(key, mask);

            Object value = values[index];
            while (value != null)
            {
                if (keys[index] == key)
                    break;

                index = ++index & mask;
                value = values[index];
            }

            return (V) value;
        }

        boolean put(int key, V value)
        {
            if (isFull())
                return false;

            final int mask = values.length - 1;
            int index = hash(key, mask);

            Object prevValue = values[index];
            while (prevValue != null)
            {
                if (keys[index] == key)
                    break;

                index = ++index & mask;
                prevValue = values[index];
            }

            if (Objects.isNull(prevValue))
            {
                size++;
                keys[index] = key;
            }

            values[index] = value;
            return true;
        }

        // From https://stackoverflow.com/questions/664014/what-integer-hash-function-are-good-that-accepts-an-integer-hash-key
        static int hash(int value)
        {
            value = ((value >>> 16) ^ value) * 0x45d9f3b;
            value = ((value >>> 16) ^ value) * 0x45d9f3b;
            value = (value >>> 16) ^ value;
            return value;
        }

        private int hash(int value, int mask)
        {
            return hash(value) & mask;
        }

        private static int findNextPositivePowerOfTwo(final int value) {
            return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
        }
    }

    /**
     * A fixed-size, allocation free, bit set.
     */
    static final class NoAllocFixedBitSet
    {
        static final int ADDRESS_BITS_PER_WORD = 6;
        static final long WORD_MASK = 0xffffffffffffffffL;
        static final int BITS_PER_WORD = 1 << ADDRESS_BITS_PER_WORD;
        final long[] words;
        int wordsInUse = 0;

        NoAllocFixedBitSet(int numberOfBits)
        {
            words = new long[wordIndex(numberOfBits - 1) + 1];
        }

        /**
         * Sets the bit at the specified index to true.
         * It returns false if the bit set is not big enough to set the specified index,
         * otherwise returns true.
         */
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while operating on bit set.")
        boolean set(int bitIndex)
        {
            assert bitIndex >= 0 : "bit index can't be negative";

            int wordIndex = wordIndex(bitIndex);
            int wordsRequired = wordIndex + 1;
            if (wordsInUse < wordsRequired) {
                if (words.length < wordsRequired) {
                    return false; // not enough space
                }
                wordsInUse = wordsRequired;
            }

            words[wordIndex] |= (1L << bitIndex);
            return true;
        }

        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while operating on bit set.")
        int nextSetBit(int fromIndex)
        {
            assert fromIndex >= 0 : "from index can't be negative";

            int index = wordIndex(fromIndex);
            if (index >= wordsInUse)
            {
                return -1;
            }

            long word = words[index] & (WORD_MASK << fromIndex);

            while (true)
            {
                if (word != 0)
                {
                    return (index * BITS_PER_WORD) + Long.numberOfTrailingZeros(word);
                }
                if (++index == wordsInUse)
                {
                    return -1;
                }

                word = words[index];
            }
        }

        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while operating on bit set.")
        boolean get(int bitIndex)
        {
            assert bitIndex >= 0 : "bit index can't be negative";

            int wordIndex = wordIndex(bitIndex);
            return (wordIndex < wordsInUse)
                    && ((words[wordIndex] & (1L << bitIndex)) != 0);
        }

        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while operating on bit set.")
        private static int wordIndex(int bitIndex)
        {
            return bitIndex >> ADDRESS_BITS_PER_WORD;
        }
    }
}
