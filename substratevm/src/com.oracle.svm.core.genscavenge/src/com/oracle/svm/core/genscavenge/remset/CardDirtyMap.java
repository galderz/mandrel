package com.oracle.svm.core.genscavenge.remset;

import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.hub.DynamicHub;

public final class CardDirtyMap {
//    public Map<Object, DynamicHub> lastDirtyType = new HashMap<>() {
//        @Override
//        public DynamicHub put(Object key, DynamicHub value) {
//            return super.put(key, value);
//        }
//    };

//    public Map<Object, DynamicHub> lastDirtyType = new ConcurrentIdentityHashMap<>();

//    public static final class CardDirtyHashMap extends HashMap<Object, DynamicHub> {
//        @AlwaysInline("GC performance")
//        @Override
//        public DynamicHub put(Object key, DynamicHub value) {
//            return super.put(key, value);    // TODO: Customise this generated block
//        }
//    }

    // TODO switch to a pre-allocated 2 dimensional array of 3 slots (chunk, hub, next)

    // private final ChunkHub root = ChunkHub.root();

    private final Object[][] dirtyMap = new Object[1024][];

    public CardDirtyMap() {
        for (int i = 0; i < dirtyMap.length; i++) {
            dirtyMap[i] = new Object[2];
        }
    }

    public void put(HeapChunk.Header<?> chunk, DynamicHub hub) {
        for (int i = 0; i < dirtyMap.length; i++) {
            final Object[] current = dirtyMap[i];
            if (getChunk(current) == null) {
                setChunk(chunk, current);
                setHub(hub, current);
                return;
            }
        }
    }

    private static Object getChunk(Object[] current) {
        return current[0];
    }

    private static void setChunk(HeapChunk.Header<?> chunk, Object[] current) {
        current[0] = chunk;
    }

    private static Object getHub(Object[] current) {
        return current[1];
    }

    private static void setHub(DynamicHub hub, Object[] current) {
        current[1] = hub;
    }

//    private static Object next(Object[] current) {
//        return current[2];
//    }

//    private void append(ChunkHub chunkHub) {
//        ChunkHub current = root;
//        while (current.next != null) {
//            current = current.next;
//        }
//
//        current.next = chunkHub;
//
////        if (root.next == null) {
////            root.next = current;
////            return;
////        }
////
////        append(current, root.next);
//    }

//    private static final class ChunkHub {
//        final HeapChunk.Header<?> chunk;
//        final DynamicHub hub;
//        ChunkHub next;
//
//        private ChunkHub(HeapChunk.Header<?> chunk, DynamicHub hub) {
//            this.chunk = chunk;
//            this.hub = hub;
//        }
//
//        static ChunkHub root() {
//            return new ChunkHub(null, null);
//        }
//    }
}
