package com.oracle.svm.test.jfr.oldobject;

import jdk.jfr.Recording;
import org.junit.Assert;
import org.junit.Test;

public class TestPlainObjectLeak extends JfrOldObjectTest {
    private static final int DEFAULT_OLD_OBJECT_QUEUE_SIZE = 256;

    @Test
    public void testSampleQueueNotFull() throws Throwable {
        Recording recording = startRecording();

        NodeNotFull node = new NodeNotFull();
        leak = node;
        for (int i = 0; i < 1_000_000; i++) {
            node.value = new NodeNotFull();
            node.left = new NodeNotFull();
            node.right = new NodeNotFull();
            node = node.right;
        }

        blackhole(leak);
        stopRecording(recording, events -> {
            Assert.assertTrue(events.size() < DEFAULT_OLD_OBJECT_QUEUE_SIZE);
            filterEventsByType(NodeNotFull.class, events).forEach(this::assertOldObjectEvent);
        });
    }

    @Test
    public void testSampleQueueFull() throws Throwable {
        Recording recording = startRecording();

        NodeFull node = new NodeFull();
        leak = node;
        for (int i = 0; i < 40_000_000; i++) {
            node.value = new NodeFull();
            node.left = new NodeFull();
            node.right = new NodeFull();
            node = node.right;
        }

        blackhole(leak);
        stopRecording(recording, events -> {
            Assert.assertEquals(DEFAULT_OLD_OBJECT_QUEUE_SIZE, events.size());
            filterEventsByType(NodeFull.class, events).forEach(this::assertOldObjectEvent);
        });
    }

    static class NodeNotFull {
        NodeNotFull left;
        NodeNotFull right;
        Object value;
    }

    static class NodeFull {
        NodeFull left;
        NodeFull right;
        Object value;
    }
}