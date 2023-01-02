package com.oracle.svm.test.jfr;

import com.oracle.svm.core.jfr.JfrEvent;
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
import java.util.stream.Collectors;

public class TestOldObjectSampleEvent extends JfrTest {
    static Object leak;

    @Override
    protected String[] getTestedEvents() {
        return new String[]{
                JfrEvent.OldObjectSample.getName(),
        };
    }

    @Test
    public void testPlainObjectLeak() {
        PlainObjectLeak.test();
    }

    @Override
    protected void checkEvent(RecordedEvent event) {
        super.checkEvent(event);
        System.out.println("Check event: " + event);

        Assert.assertEquals(0, event.getDuration().toMillis()); // Duration.
        Assert.assertNull(event.getStackTrace()); // todo assert stack traces

        final RecordedObject object = event.getValue("object");
        Assert.assertNotNull(object);

        final List<ValueDescriptor> fields = event.getFields();
        Assert.assertEquals(fields.stream().map(ValueDescriptor::getName).collect(Collectors.toList()).toString(), 10, fields.size());

        final long allocationTime = event.getLong("allocationTime");
        Assert.assertTrue(allocationTime > 0);
        final long startTime = event.getLong("startTime");
        Assert.assertTrue(startTime > 0);
        Assert.assertTrue(String.format("Allocation time (%d) should be earlier or same time as event start time (%d)", allocationTime, startTime), allocationTime <= startTime);

        Assert.assertTrue(event.getLong("lastKnownHeapUsage") > 0);
        Assert.assertTrue(event.getLong("objectAge") > 0);
        Assert.assertNull(event.getValue("root"));

        final String objectTypeName = object.getClass("type").getName();
        if (objectTypeName.contains("PlainObjectLeak")) {
            PlainObjectLeak.checkEvent(event);
        } else {
            throw new RuntimeException("Unknown event: " + event);
        }
    }

    private static void blackhole(Object obj) {
        if (obj.hashCode() == System.nanoTime()) {
            System.out.println(obj);
        }
    }

    static class PlainObjectLeak {
        static void test() {
            Node node = new Node();
            leak = node;
            for (int i = 0; i < 1_000_000; i++) {
                node.value = new Any();
                node.left = new Node();
                node.right = new Node();
                node = node.right;
            }

            blackhole(leak);
        }

        public static void checkEvent(RecordedEvent event) {
            Assert.assertEquals(Integer.MIN_VALUE, event.getInt("arrayElements"));
        }

        static class Node {
            Node left;
            Node right;
            Object value;
        }

        static class Any {
            public long value1;
            public Object value2;
            float value3;
            int value4;
            double value5;
        }
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
