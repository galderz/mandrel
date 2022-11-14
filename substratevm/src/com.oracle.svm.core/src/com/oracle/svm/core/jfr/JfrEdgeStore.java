package com.oracle.svm.core.jfr;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.HashMap;
import java.util.Map;

final class JfrEdgeStore {
    public final Map<Object, StoredEdge> edges;
    private long edgeIdCounter;

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrEdgeStore() {
        edges = new HashMap<>();
    }

    void put(Object object) {
        edges.put(object, new StoredEdge(edgeIdCounter++, -1, null));
    }

    long getObjectId(Object object) {
        final StoredEdge edge = edges.get(object);
        // System.out.printf("Edge for %s is %s%n", object, edge);
        return edge.id;
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
