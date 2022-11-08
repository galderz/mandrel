package com.oracle.svm.core.jfr;

import java.util.HashMap;
import java.util.Map;

final class JfrEdgeStore {
    private final Map<Object, StoredEdge> edges = new HashMap<>();
    private long edgeIdCounter;

    void put(Object object) {
        edges.put(object, new StoredEdge(edgeIdCounter++, -1, null));
    }

    long getObjectId(Object object) {
        return edges.get(object).id;
    }

    private static final class StoredEdge {
        final long id;
        final long gcRootId;
        final StoredEdge parent;
        
        StoredEdge(long id, long gcRootId, StoredEdge parent) {
            this.id = id;
            this.gcRootId = gcRootId;
            this.parent = parent;
        }
    }
}
