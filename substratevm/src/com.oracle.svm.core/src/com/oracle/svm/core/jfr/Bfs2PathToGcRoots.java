package com.oracle.svm.core.jfr;

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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;

public class Bfs2PathToGcRoots {
    private static final RootVisitor rootVisitor = new RootVisitor();
    private static final HeapObjectRefVisitor heapObjectRefVisitor = new HeapObjectRefVisitor();
    private static final MarkHighBitsVisitor markHighBitsVisitor = new MarkHighBitsVisitor();
    private static final MarkHighBitsRefVisitor markHighBitsRefVisitor = new MarkHighBitsRefVisitor();

    void findPathToGcRoots(IdentityHashMap<Object, Boolean> targets, PathToGcRootsStore pathStore) {
        // todo deal with potential issue of getting a new high index in between VM operations?

        Log log = Log.log();
        log.string("Bfs2PathToGcRoots.findPathToGcRoots").newline();
        final int objectAlignment = ConfigurationValues.getObjectLayout().getAlignment();
        final BitMap bitMap = new BitMap(objectAlignment);
        final HighBitMap highBits = new HighBitMap(bitMap);
        new MarkHighBitsOperation(highBits).enqueue();
        final List<Integer> highBitIndexes = highBits.getHighBitIndexes();
        log.string("High bit indexes:");
        for (int i = 0; i < highBitIndexes.size(); i++) {
            log.string(" ").unsigned(highBitIndexes.get(i));
        }
        log.newline();

        final int classCount = Heap.getHeap().getLoadedClasses().size();
        final int queueCapacity = classCount * (1 << 10);
        final EdgeQueue queue = new EdgeQueue(queueCapacity);
        log.string("Queue capacity: ").unsigned(queueCapacity).newline();
        final LowBitMap lowBits = new LowBitMap(highBitIndexes, bitMap);
        new FindGcRootsToObjectsOperation(this, targets, pathStore, queue, lowBits).enqueue();
    }

    private void findPaths(IdentityHashMap<Object, Boolean> targets, PathToGcRootsStore pathStore, EdgeQueue queue, LowBitMap lowBits, FrontierLevels frontiers) {
        final Log log = Log.log();

        rootVisitor.initialize(queue);
        heapObjectRefVisitor.initialize(queue);

        Heap.getHeap().walkImageHeapObjects(rootVisitor);
        log.string("Root set edges: ").unsigned(queue.size()).newline();
        // queue.show(-1, log);

        frontiers.next = queue.tail(); // Initial frontier is where roots finished
        while (!isComplete(frontiers, queue, log)) {
            final EdgeQueue.Edge current = queue.pop();
            final Object to = current.to;
            if (to != null && lowBits.mark(Word.objectToUntrackedPointer(to).rawValue())) {
                if (Boolean.TRUE.equals(targets.get(to))) {
                    log.string("A leak target found: ").string(to.getClass().getName()).string("@").zhex(System.identityHashCode(to)).newline();
                    storePathToGcRoot(current, pathStore);
                }

                heapObjectRefVisitor.setParent(current);
                final boolean keepWalking = InteriorObjRefWalker.walkObject(to, heapObjectRefVisitor);
                if (!keepWalking) {
                    log.string("Stopped walking, is queue full? ").bool(queue.isFull()).newline();
                    return;
                }
            }
        }
    }

    private void storePathToGcRoot(EdgeQueue.Edge leak, PathToGcRootsStore pathStore) {
        int position = 0;
        final int path = pathStore.addPathElement(position++, leak.location, leak.to);
        EdgeQueue.Edge current = leak;
        while ((current = current.parent) != null) {
            // log.string("Store path to: ").string(current.getClass().getName()).string("@").zhex(System.identityHashCode(current)).newline();
            pathStore.addPathElement(position++, current.location, current.to, path);
        }
    }

    private static boolean isComplete(FrontierLevels frontiers, EdgeQueue queue, Log log) {
        if (queue.head() < frontiers.next) {
            return false;
        }
        if (queue.head() > frontiers.next) {
            return true;
        }
        if (queue.isEmpty()) {
            return true;
        }
        stepFrontier(frontiers, queue, log);
        return false;
    }

    private static void stepFrontier(FrontierLevels frontiers, EdgeQueue queue, Log log) {
        logCompletedFrontier(frontiers, queue, log);
        frontiers.current++;
        frontiers.prev = frontiers.next;
        frontiers.next = queue.tail();
    }

    private static void logCompletedFrontier(FrontierLevels frontiers, EdgeQueue queue, Log log) {
        long numberOfEdgesInFrontier = frontiers.next - frontiers.prev;
        log.string("BFS front: ").unsigned(frontiers.current).string(" edges: ").unsigned(numberOfEdgesInFrontier).newline();
        // queue.show(frontiers.current, log);
    }

    private static class HeapObjectRefVisitor implements ObjectReferenceVisitor {
        private EdgeQueue queue;
        private EdgeQueue.Edge parent;

        public void initialize(EdgeQueue queue) {
            this.queue = queue;
        }

        public void setParent(EdgeQueue.Edge parent) {
            this.parent = parent;
        }

        @Override
        public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
            if (objRef.isNull()) {
                return true;
            }

            Pointer referentPointer = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
            if (referentPointer.isNull()) {
                return true;
            }
//            UnsignedWord holderAddress = Word.objectToUntrackedPointer(holderObject);
//            UnsignedWord offset = refPointer.subtract(holderAddress);
            return queue.push(holderObject, WordFactory.zero(), referentPointer.toObject(), parent);
        }
    }

    private static class RootVisitor implements ObjectVisitor {
        private EdgeQueue queue;

        public void initialize(EdgeQueue queue) {
            this.queue = queue;
        }
        @Override
        public boolean visitObject(Object obj) {
            if (obj == null) {
                return true;
            }

            // StaticFieldsSupport.staticObjectFields is a root exposed as Object[]
            // todo is there a way to link the Object[] that comes from StaticFieldsSupport?
            // todo expand support for other global data
            if (obj instanceof Object[]) {
                queue.push(null, WordFactory.zero(), obj, null);
            }

            return true;
        }
    }

//    private static class RootRefVisitor implements ObjectReferenceVisitor {
//        private Pointer parent;
//        private EdgeQueue queue;
//
//        public void initialize(Pointer parent, EdgeQueue queue) {
//            this.parent = parent;
//            this.queue = queue;
//        }
//
//        @Override
//        public boolean visitObjectReference(Pointer objRef, boolean compressed, Object holderObject) {
//            if (objRef.isNull()) {
//                return true;
//            }
//
//            // todo deal with interferences?
//            Pointer pointee = ReferenceAccess.singleton().readObjectAsUntrackedPointer(objRef, compressed);
//            UnsignedWord offset = objRef.subtract(parent);
//            queue.push(parent.toObject(), offset, pointee.toObject());
//            return true;
//        }
//    }

    private static final class MarkHighBitsOperation extends JavaVMOperation {
        final HighBitMap highBits;

        private MarkHighBitsOperation(HighBitMap highBits) {
            super(VMOperationInfos.get(MarkHighBitsOperation.class, "TBD", SystemEffect.SAFEPOINT));
            this.highBits = highBits;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while computing paths to GC roots.")
        protected void operate() {
            markHighBitsVisitor.initialize(highBits);
            markHighBitsRefVisitor.initialize(highBits);
            Heap.getHeap().walkObjects(markHighBitsVisitor);
        }
    }

    private static class MarkHighBitsVisitor implements ObjectVisitor {
        private HighBitMap highBits;

        void initialize(HighBitMap highBits) {
            this.highBits = highBits;
        }

        @Override
        public boolean visitObject(Object obj) {
            final long address = Word.objectToUntrackedPointer(obj).rawValue();
            highBits.mark(address);
            markHighBitsRefVisitor.initialize(highBits);
            return InteriorObjRefWalker.walkObject(obj, markHighBitsRefVisitor);
        }
    }

    private static class MarkHighBitsRefVisitor implements ObjectReferenceVisitor {
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

    private static final class FindGcRootsToObjectsOperation extends JavaVMOperation {
        private final Bfs2PathToGcRoots bfs;
        private final IdentityHashMap<Object, Boolean> targets;
        private final PathToGcRootsStore pathStore;
        private final EdgeQueue queue;
        private final LowBitMap lowBits;

        private final FrontierLevels frontiers;

        private FindGcRootsToObjectsOperation(Bfs2PathToGcRoots bfs, IdentityHashMap<Object, Boolean> targets, PathToGcRootsStore pathStore, EdgeQueue queue, LowBitMap lowBits) {
            super(VMOperationInfos.get(FindGcRootsToObjectsOperation.class, "TBD", SystemEffect.SAFEPOINT));
            this.bfs = bfs;
            this.targets = targets;
            this.pathStore = pathStore;
            this.queue = queue;
            this.lowBits = lowBits;
            this.frontiers = new FrontierLevels();
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while computing paths to GC roots.")
        protected void operate() {
            bfs.findPaths(targets, pathStore, queue, lowBits, frontiers);
        }
    }

    private static final class FrontierLevels {
        long current;
        long next;
        long prev;
    }

    /**
     * A queue for edge nodes that enables computing the path to the root from any node.
     * To avoid losing information in the path to the root,
     * the queue does not write over any previously queued edge nodes.
     * So this queue does not behave like a ring or circular queue.
     */
    private static final class EdgeQueue {
        private final Edge[] edges;
        private long tail = 0;
        private long head = 0;

        public EdgeQueue(int capacity)
        {
            this.edges = new Edge[capacity];
            for (int i = 0; i < this.edges.length; i++)
            {
                this.edges[i] = new Edge();
            }
        }

        public boolean push(Object from, UnsignedWord location, Object to, Edge parent)
        {
            if (tail - head < edges.length) {
                int pos = (int) (tail % edges.length);
                if (pos < head) {
                    return false; // wrapping around not supported
                }
                set(from, location, to, parent, pos);
                tail++;
                return true;
            }

            return false;
        }

        public Edge pop()
        {
            if (head < tail) {
                int pos = (int) (head % edges.length);
                Edge e = edges[pos];
                // set(null, 0, null, pos);
                head++;
                return e;
            }

            return null;
        }

        public int size() {
            return (int) (tail - head);
        }

        public long tail() {
            return tail;
        }

        public long head() {
            return head;
        }

        public boolean isEmpty() {
            return tail == head;
        }

        public boolean isFull() {
            return size() == edges.length;
        }

        public void show(long iteration, Log log)
        {
            long showTail = tail;
            long showHead = head;

            Edge current;
            while ((current = peek(showTail, showHead)) != null)
            {
                showHead++;
                log.signed(iteration).string(" ");
                show(current.from, log);
                log.string("->");
                show(current.to, log);
                log.newline();
            }
        }

        private Edge peek(long peekTail, long peekHead)
        {
            if (peekHead < peekTail) {
                int pos = (int) (peekHead % edges.length);
                Edge e = edges[pos];
                return e;
            }

            return null;
        }

        private static void show(Object obj, Log log) {
            if (obj == null) {
                log.string("null");
            } else {
                log.string(obj.getClass().getName()).string("@").zhex(System.identityHashCode(obj));
            }
        }

        private void set(Object from, UnsignedWord location, Object to, Edge parent, int index)
        {
            edges[index].from = from;
            edges[index].location = location;
            edges[index].to = to;
            edges[index].parent = parent;
        }

        static class Edge {
            Object from;
            UnsignedWord location;
            Object to;
            Edge parent;
        }
    }

//    private void showEdges(EdgeQueue queue, int iteration) {
//        final Log log = Log.log();
//        log.string("Bfs2PathToGcRoots.showEdges").newline();
//        log.string("Edge queue size ").unsigned(queue.size()).newline();
////        for (int i = 0; i < queue.edges.length; i++) {
////            final Object from = queue.getFrom(i);
////            final Object to = queue.getTo(i);
////            if (from == null && to == null)
////                continue;
////
////            final long fromAddress = Word.objectToUntrackedPointer(from).rawValue();
////            log.unsigned(iteration).string(" ");
////            show(from, log);
////            log.string("(").signed(fromAddress).string(")").string("->");
////            show(to, log);
////            log.newline();
////        }
//    }

//    private static void show(Object obj, Log log) {
//        if (obj == null) {
//            log.string("null");
//        } else {
//            log.string(obj.getClass().getName()).string("@").zhex(System.identityHashCode(obj));
//        }
//    }

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
