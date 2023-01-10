package com.oracle.svm.test.jfr.oldobject;

import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import org.junit.Assert;

import java.util.List;

public class OldObjectAsserts {
    static void assertEvent(String expectedTypeName, RecordedEvent event) {
        Assert.assertEquals(0, event.getDuration().toMillis());
        Assert.assertNull(event.getStackTrace()); // todo assert stack traces

        final RecordedObject object = event.getValue("object");
        Assert.assertNotNull(object);
        final String objectTypeName = object.getClass("type").getName();
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
        Assert.assertEquals(Integer.MIN_VALUE, event.getInt("arrayElements"));
    }
}
