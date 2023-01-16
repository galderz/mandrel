package com.oracle.svm.test.jfr.oldobject;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.test.jfr.JfrTest;
import jdk.jfr.DataAmount;
import jdk.jfr.MemoryAddress;
import jdk.jfr.Timestamp;
import jdk.jfr.Unsigned;
import jdk.jfr.consumer.RecordedEvent;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation;
import org.junit.Test;

public class TestPlainObjectLeak extends JfrTest {
    static Object leak;
    private String expectedTypeName;

    @Override
    protected String[] getTestedEvents() {
        return new String[]{
                JfrEvent.OldObjectSample.getName(),
        };
    }

    @Override
    public void endRecording() {
        super.endRecording();

        leak = null;
        System.gc();
        System.gc();
    }

    @Test
    public void testSampleQueueNotFull() {
        expectedTypeName = NodeNotFull.class.getName();
        NodeNotFull node = new NodeNotFull();
        leak = node;
        for (int i = 0; i < 1_000_000; i++) {
            node.value = new NodeNotFull();
            node.left = new NodeNotFull();
            node.right = new NodeNotFull();
            node = node.right;
        }

        blackhole(leak);
    }

    @Test
    public void testSampleQueueFull() {
        expectedTypeName = NodeFull.class.getName();
        NodeFull node = new NodeFull();
        leak = node;
        for (int i = 0; i < 10_000_000; i++) {
            node.value = new NodeFull();
            node.left = new NodeFull();
            node.right = new NodeFull();
            node = node.right;
        }

        blackhole(leak);
    }

    @Override
    protected void checkEvent(RecordedEvent event) {
        super.checkEvent(event);
        OldObjectAsserts.assertEvent(expectedTypeName, event);
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

    public static class TestFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            RuntimeProxyCreation.register(DataAmount.class);
            RuntimeProxyCreation.register(MemoryAddress.class);
            RuntimeProxyCreation.register(Timestamp.class);
            RuntimeProxyCreation.register(Unsigned.class);
        }
    }
}