package com.oracle.svm.test.jfr.oldobject;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import org.junit.Assert;
import org.junit.Test;

public class TestObjectDescription extends JfrOldObjectTest {
    @Test
    public void testThreadGroupName() throws Throwable {
        Recording recording = startRecording();

        Node node = new Node();
        leak = node;
        for (int i = 0; i < 1_000_000; i++) {
            node.value = new MyThreadGroup();
            node.left = new Node();
            node.right = new Node();
            node = node.right;
        }

        blackhole(leak);
        stopRecording(recording, events -> filterEventsByType(MyThreadGroup.class, events).forEach(this::assertOldObjectDescription));

    }

    private void assertOldObjectDescription(RecordedEvent event) {
        final RecordedObject object = event.getValue("object");
        String description = object.getValue("description");
        Assert.assertEquals("Thread Group: My Thread Group", description);
    }

    static class Node {
        Node left;
        Node right;
        Object value;
    }

    public final static class MyThreadGroup extends ThreadGroup {
        public final static String NAME = "My Thread Group";

        public MyThreadGroup() {
            super(NAME);
        }
    }
}
