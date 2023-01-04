package com.oracle.svm.test.jfr.oldobject;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.test.jfr.JfrTest;
import jdk.jfr.DataAmount;
import jdk.jfr.MemoryAddress;
import jdk.jfr.Timestamp;
import jdk.jfr.Unsigned;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class TestPlainObjectLeak extends JfrTest {
    static Object leak;

    @Override
    protected String[] getTestedEvents() {
        return new String[]{
                JfrEvent.OldObjectSample.getName(),
        };
    }

    @Test
    public void testPlainObjectLeak() {
        Node node = new Node();
        leak = node;
        for (int i = 0; i < 4_000_000; i++) {
            node.value = new Node();
            node.left = new Node();
            node.right = new Node();
            node = node.right;
        }

        blackhole(leak);
    }

    @Override
    protected void checkEvent(RecordedEvent event) {
        super.checkEvent(event);
        OldObjectAsserts.assertEvent(event);
    }

    private static void blackhole(Object obj) {
        if (obj.hashCode() == System.nanoTime()) {
            System.out.println(obj);
        }
    }

    static class Node {
        Node left;
        Node right;
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
