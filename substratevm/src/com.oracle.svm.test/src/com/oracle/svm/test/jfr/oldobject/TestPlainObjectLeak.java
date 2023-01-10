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
        expectedTypeName = NodeA.class.getName();
        NodeA node = new NodeA();
        leak = node;
        for (int i = 0; i < 1_000_000; i++) {
            node.value = new NodeA();
            node.left = new NodeA();
            node.right = new NodeA();
            node = node.right;
        }

        blackhole(leak);
    }

    @Test
    public void testSampleQueueFull() {
        expectedTypeName = NodeB.class.getName();
        NodeB node = new NodeB();
        leak = node;
        for (int i = 0; i < 4_000_000; i++) {
            node.value = new NodeB();
            node.left = new NodeB();
            node.right = new NodeB();
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

    static class NodeA {
        NodeA left;
        NodeA right;
        Object value;
    }

    static class NodeB {
        NodeB left;
        NodeB right;
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
