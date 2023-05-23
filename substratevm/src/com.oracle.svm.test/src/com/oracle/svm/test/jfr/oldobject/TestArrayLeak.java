package com.oracle.svm.test.jfr.oldobject;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.test.jfr.JfrRecordingTest;
import jdk.jfr.Recording;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.HashMap;
import java.util.Map;

import static com.oracle.svm.test.jfr.oldobject.OldObjectAsserts.assertOldObjectEvent;

public class TestArrayLeak extends JfrRecordingTest {
    static Object leak;

    @Rule
    public TestName name = new TestName();

    @Before
    public void triggerUpdateOfLastKnownHeapUsage() {
        // Trigger GC before tests to get a reading of last known heap usage for the first executed test.
        System.gc();
        System.gc();
    }

    @After
    public void garbageCollectLeak() {
        // Force previous tests objects to be garbage collected.
        // The scavenge logic in the sampler should clear those from the queue.
        leak = null;
        System.gc();
        System.gc();
    }

    @Test
    public void testArrayLeak() throws Throwable {
        Recording recording = startOldObjectRecording();

        Object[] node = new Object[3];
        leak = node;
        for (int i = 0; i < 1_000_000; i++) {
            Object[] value = new Object[100];
            node[0] = value;
            Object[] left = new Object[3];
            node[1] = left;
            Object[] right = new Object[3];
            node[2] = right;
            node = right;
        }

        blackhole(leak);
        stopRecording(recording, events -> events.forEach(assertOldObjectEvent(name, "[Ljava.lang.Object;", 100)));
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
}
