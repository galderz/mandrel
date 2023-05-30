package com.oracle.svm.test.jfr.oldobject;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class TestObjectDescription extends JfrOldObjectTest {
    /**
     * Destroy thread groups to avoid leak building its parent's groups array.
     * Keep destroy outside the test so that java monitors created during the synchronized block access
     * don't end up polluting the recording.
     */
    @After
    public void destroyThreadGroups() {
        Node current = (Node) leak;
        while (current != null && current.value instanceof ThreadGroup) {
            ((ThreadGroup) current.value).destroy();
            current = current.right;
        }
    }

    @Test
    public void testThreadGroup() throws Throwable {
        Recording recording = startRecording();

        Node node = new Node();
        leak = node;
        for (int i = 0; i < 10_000; i++) {
            node.value = new MyThreadGroup(MyThreadGroup.NAME);
            node.left = new Node();
            node.right = new Node();
            node = node.right;
        }

        blackhole(leak);
        stopRecording(recording, events -> filterEventsByType(MyThreadGroup.class, events).forEach(e -> assertDescription("Thread Group: My Thread Group", e)));
    }

    private void assertDescription(String expected, RecordedEvent event) {
        final String description = event.<RecordedObject>getValue("object").getValue("description");
        Assert.assertEquals(expected, description);
    }

    @Test
    public void testEllipsis() throws Throwable {
        final int objectDescriptionMaxSize = 100;
        final int prefixSize = "Thread Group: ".length();
        final String threadGroupName = "x".repeat(2 * objectDescriptionMaxSize);

        Recording recording = startRecording();

        Node node = new Node();
        leak = node;
        for (int i = 0; i < 10_000; i++) {
            node.value = new MyThreadGroup(threadGroupName);
            node.left = new Node();
            node.right = new Node();
            node = node.right;
        }

        blackhole(leak);
        stopRecording(recording, events -> filterEventsByType(MyThreadGroup.class, events).forEach(e -> assertDescriptionLimit("xxx...", objectDescriptionMaxSize + prefixSize, e)));
    }

    private void assertDescriptionLimit(String expected, int expectedSize, RecordedEvent event) {
        final String description = event.<RecordedObject>getValue("object").getValue("description");
        Assert.assertEquals(expectedSize, description.length());
        Assert.assertTrue(description.contains(expected));
    }

    static class Node {
        Node left;
        Node right;
        Object value;
    }

    public final static class MyThreadGroup extends ThreadGroup {
        public final static String NAME = "My Thread Group";

        public MyThreadGroup(String name) {
            super(name);
        }
    }
}
