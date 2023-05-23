package com.oracle.svm.test.jfr.oldobject;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.test.jfr.JfrRecordingTest;
import jdk.jfr.Recording;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.HashMap;
import java.util.Map;

import static com.oracle.svm.test.jfr.oldobject.OldObjectAsserts.assertOldObjectEvent;

public class TestPlainObjectLeak extends JfrRecordingTest {
    static Object leak;

    @Rule
    public TestName name = new TestName();

    @Before
    public void triggerGC() {
        // Trigger GC before tests to:
        // - Get a reading of last known heap usage for the first executed test.
        // - Force previous tests objects to be garbage collected and sampling scavenge to kick in.
        System.gc();
        System.gc();
    }

    @Test
    public void testSampleQueueNotFull() throws Throwable {
        Recording recording = startOldObjectRecording();

        NodeNotFull node = new NodeNotFull();
        leak = node;
        for (int i = 0; i < 1_000_000; i++) {
            node.value = new NodeNotFull();
            node.left = new NodeNotFull();
            node.right = new NodeNotFull();
            node = node.right;
        }

        blackhole(leak);
        stopRecording(recording, events -> events.forEach(assertOldObjectEvent(NodeNotFull.class.getName(), name.getMethodName())));
    }

    @Test
    public void testSampleQueueFull() throws Throwable {
        Recording recording = startOldObjectRecording();

        NodeFull node = new NodeFull();
        leak = node;
        for (int i = 0; i < 10_000_000; i++) {
            node.value = new NodeFull();
            node.left = new NodeFull();
            node.right = new NodeFull();
            node = node.right;
        }

        blackhole(leak);
        stopRecording(recording, events -> events.forEach(assertOldObjectEvent(NodeFull.class.getName(), name.getMethodName())));
    }

    private Recording startOldObjectRecording() throws Throwable {
        final String[] events = {JfrEvent.OldObjectSample.getName()};
        Map<String, String> settings = new HashMap<>();
        settings.put("old-objects-stack-trace", "true");
        return startRecording(events, getDefaultConfiguration(), settings);
    }

    private static void blackhole(Object obj) {
        if (obj.hashCode() == System.nanoTime()) {
            System.out.println(obj);
        }
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