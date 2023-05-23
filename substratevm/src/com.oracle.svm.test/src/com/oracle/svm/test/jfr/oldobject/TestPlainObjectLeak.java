package com.oracle.svm.test.jfr.oldobject;

import jdk.jfr.Recording;
import org.junit.Test;

public class TestPlainObjectLeak extends JfrOldObjectTest {
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
        stopRecording(recording, events -> filterEventsByType(NodeNotFull.class, events).forEach(this::assertOldObjectEvent));
    }

    @Test
    public void testSampleQueueFull() throws Throwable {
        Recording recording = startRecording();

        NodeFull node = new NodeFull();
        leak = node;
        for (int i = 0; i < 10_000_000; i++) {
            node.value = new NodeFull();
            node.left = new NodeFull();
            node.right = new NodeFull();
            node = node.right;
        }

        blackhole(leak);
        stopRecording(recording, events -> filterEventsByType(NodeFull.class, events).forEach(this::assertOldObjectEvent));
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