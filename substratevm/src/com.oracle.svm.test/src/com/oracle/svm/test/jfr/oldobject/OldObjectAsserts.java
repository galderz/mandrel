package com.oracle.svm.test.jfr.oldobject;

import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedObject;
import org.junit.Assert;
import org.junit.rules.TestName;

import java.util.List;
import java.util.function.Consumer;

final class OldObjectAsserts {
    static Consumer<RecordedEvent> assertOldObjectEvent(TestName stackMethodName, String expectedTypeName) {
        return assertOldObjectEvent(stackMethodName, expectedTypeName, Integer.MIN_VALUE);
    }

    static Consumer<RecordedEvent> assertOldObjectEvent(TestName stackMethodName, String expectedTypeName, int expectedArrayLength) {
        return event -> {
            Assert.assertEquals(0, event.getDuration().toMillis());

            final List<RecordedFrame> frames = event.getStackTrace().getFrames();
            Assert.assertTrue(frames.size() > 0);
            Assert.assertTrue(frames.stream().anyMatch(e -> stackMethodName.getMethodName().equals(e.getMethod().getName())));

            final RecordedObject object = event.getValue("object");
            Assert.assertNotNull(object);

            final String objectTypeName = object.getClass("type").getName();
            // Ignore events for types except the expected ones
            if (objectTypeName.startsWith("[Ljava.lang.Object;") || objectTypeName.startsWith("com.oracle.svm.test.jfr.oldobject")) {
                Assert.assertEquals(expectedTypeName, objectTypeName);
                final List<ValueDescriptor> fields = event.getFields();
                Assert.assertEquals(10, fields.size());

                final long allocationTime = event.getLong("allocationTime");
                Assert.assertTrue(allocationTime > 0);
                final long startTime = event.getLong("startTime");
                Assert.assertTrue(startTime > 0);
                Assert.assertTrue(String.format("Allocation time (%d) should be earlier or same time as event start time (%d)", allocationTime, startTime), allocationTime <= startTime);

                Assert.assertTrue(event.getLong("lastKnownHeapUsage") > 0);
                Assert.assertTrue(event.getLong("objectAge") > 0);
                Assert.assertNull(event.getValue("root"));
                Assert.assertEquals(expectedArrayLength, event.getInt("arrayElements"));
            }
        };
    }
}