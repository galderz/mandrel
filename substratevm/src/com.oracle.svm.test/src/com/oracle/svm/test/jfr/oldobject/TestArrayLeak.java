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

public class TestArrayLeak extends JfrTest {

    static Object leak;

    @Override
    protected String[] getTestedEvents() {
        return new String[]{
                JfrEvent.OldObjectSample.getName(),
        };
//        return new String[0];
    }

    @Override
    public void endRecording() {
        super.endRecording();

        leak = null;
        System.gc();
        System.gc();
    }

    @Test
    public void testArrayLeak() {
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
    }

    @Override
    protected void checkEvent(RecordedEvent event) {
        super.checkEvent(event);
        OldObjectAsserts.assertEvent("[Ljava.lang.Object;", 100, event);
    }

    private static void blackhole(Object obj) {
        if (obj.hashCode() == System.nanoTime()) {
            System.out.println(obj);
        }
    }

    private static final class Empty {}

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
