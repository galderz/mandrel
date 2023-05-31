package com.oracle.svm.test.jfr.oldobject;

import com.oracle.svm.core.jfr.JfrEvent;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
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

    static class NodeFull {
        NodeFull left;
        NodeFull right;
        Object value;
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

    @Test
    public void testNoStackTrace() throws Throwable {
        Recording recording = startRecording(new String[]{JfrEvent.OldObjectSample.getName()});

        NodeNoStack node = new NodeNoStack();
        leak = node;
        for (int i = 0; i < 1_000_000; i++) {
            node.value = new NodeNoStack();
            node.left = new NodeNoStack();
            node.right = new NodeNoStack();
            node = node.right;
        }

        blackhole(leak);
        stopRecording(recording, events -> filterEventsByType(NodeNoStack.class, events).forEach(this::assertNoStackTrace));
    }

    static class NodeNoStack {
        NodeNoStack left;
        NodeNoStack right;
        Object value;
    }

    private void assertNoStackTrace(RecordedEvent event) {
        Assert.assertNull(event.getStackTrace());
    }
}
