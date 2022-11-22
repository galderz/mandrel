package com.oracle.svm.test.jfr;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrType;
import com.oracle.svm.test.jfr.utils.poolparsers.ConstantPoolParser;
import jdk.jfr.DataAmount;
import jdk.jfr.Timestamp;
import jdk.jfr.Unsigned;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeProxyCreation;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    public void testAllocateObject() {
        Node node = new Node();
        leak = node;
        for (int i = 0; i < 1_000_000; i++) {
            node.value = new Big();
            node.left = new Node();
            node.right = new Node();
            node = node.right;
        }

        blackhole(leak);
    }

    @Override
    protected void checkEvent(RecordedEvent event) {
        super.checkEvent(event);
        System.out.println("Check event: " + event);
        Assert.assertEquals(0, event.getDuration().toMillis()); // Duration.
        ConstantPoolParser.addExpectedId(JfrType.Thread, event.getThread().getId()); // ThreadId.
        Assert.assertNull(event.getStackTrace()); // todo why null? is stacktrace disabled by default?

        final List<ValueDescriptor> fields = event.getFields();
        Assert.assertEquals(fields.stream().map(ValueDescriptor::getName).collect(Collectors.toList()).toString(), 10, fields.size());
        final long allocationTime = event.getLong("allocationTime");
        Assert.assertTrue("Allocation time: " + allocationTime, allocationTime > 0);
        final long startTime = event.getLong("startTime");
        Assert.assertTrue("Start time: " + startTime, startTime > 0);

        // todo rest of fields

        Assert.assertTrue(String.format("Allocation time (%d) should be earlier or same time as event start time (%d)", allocationTime, startTime), allocationTime <= startTime);

        for (ValueDescriptor field : fields) {
            switch (field.getName()) {
                case "allocationTime":
                    break;
                case "startTime":
                    break;
                case "objectAge":
                    break;
                case "lastKnownHeapUsage":
                    break;
                case "object":
                    break;
                case "arrayElements":
                    break;
                case "root":
                    break;
                case "duration":
                case "eventThread":
                case "stackTrace":
                    // Common event field already checked
                    break;
                default:
                    Assert.fail("Unexpected field: " + field.getName());
            }
        }
    }

    //    @Override
//    protected void checkTestedEvents(Set<RecordedEvent> seenEvents) {
//        seenEvents.stream()
//                .filter(e -> e.getEventType().getName().equals(JfrEvent.OldObjectSample.getName()))
//                .forEach(TestOldObjectSampleEvent::checkEventContent);
//    }
//
//    private static void checkEventContent(RecordedEvent event) {
//        Assert.assertTrue(event.getStartTime().toEpochMilli() != 0);
//    }

    static void blackhole(Object obj) {
        if (obj.hashCode() == System.nanoTime()) {
            System.out.println(obj);
        }
    }

    static class Node {
        Node left;
        Node right;
        Object value;
    }

    static class Big {
        public long value1;
        public Object value2;
        float value3;
        int value4;
        double value5;
    }

    public static class TestFeature implements Feature {
        @Override
        public void beforeAnalysis(BeforeAnalysisAccess access) {
            RuntimeProxyCreation.register(Unsigned.class);
            RuntimeProxyCreation.register(Timestamp.class);
            RuntimeProxyCreation.register(DataAmount.class);
        }
    }
}
