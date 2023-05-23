package com.oracle.svm.test.jfr.oldobject;

import jdk.jfr.Recording;
import org.junit.Test;

public class TestArrayLeak extends JfrOldObjectTest {
    @Test
    public void testArrayLeak() throws Throwable {
        Recording recording = startRecording();

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
        stopRecording(recording, events -> filterEventsByTypeName("[Ljava.lang.Object;", events).forEach(e -> assertOldObjectEvent(100, e)));
    }
}
