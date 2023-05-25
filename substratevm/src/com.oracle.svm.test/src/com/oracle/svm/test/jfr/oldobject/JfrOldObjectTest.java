package com.oracle.svm.test.jfr.oldobject;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.test.jfr.JfrRecordingTest;
import jdk.jfr.Recording;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public abstract class JfrOldObjectTest extends JfrRecordingTest {
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

    Recording startRecording() throws Throwable {
        final String[] events = {JfrEvent.OldObjectSample.getName()};
        Map<String, String> settings = new HashMap<>();
        settings.put("old-objects-stack-trace", "true");
        return startRecording(events, getDefaultConfiguration(), settings);
    }

    static void blackhole(Object obj) {
        if (obj.hashCode() == System.nanoTime()) {
            System.out.println(obj);
        }
    }

    Collection<RecordedEvent> filterEventsByType(Class<?> type, List<RecordedEvent> events) {
        return filterEventsByTypeName(type.getName(), events);
    }

    Collection<RecordedEvent> filterEventsByTypeName(String typeName, List<RecordedEvent> events) {
        final List<RecordedEvent> filteredEvents = events.stream().filter(e -> typeName.equals(e.<RecordedObject>getValue("object").getClass("type").getName())).toList();
        Assert.assertFalse(filteredEvents.isEmpty());
        return filteredEvents;
    }

    void assertOldObjectEvent(RecordedEvent event) {
        assertOldObjectEvent(Integer.MIN_VALUE, event);
    }

    void assertOldObjectEvent(int expectedArrayLength, RecordedEvent event) {
        final List<ValueDescriptor> fields = event.getFields();
        Assert.assertEquals(10, fields.size());

        Assert.assertEquals(0, event.getDuration().toMillis());
        Assert.assertTrue(event.getLong("lastKnownHeapUsage") > 0);
        Assert.assertTrue(event.getLong("objectAge") > 0);
        Assert.assertNull(event.getValue("root"));
        Assert.assertEquals(expectedArrayLength, event.getInt("arrayElements"));

        final List<RecordedFrame> frames = event.getStackTrace().getFrames();
        Assert.assertTrue(frames.size() > 0);
        Assert.assertTrue(frames.stream().anyMatch(e -> name.getMethodName().equals(e.getMethod().getName())));

        final long allocationTime = event.getLong("allocationTime");
        Assert.assertTrue(allocationTime > 0);

        final long startTime = event.getLong("startTime");
        Assert.assertTrue(startTime > 0);
        Assert.assertTrue(String.format("Allocation time (%d) should be earlier or same time as event start time (%d)", allocationTime, startTime), allocationTime <= startTime);

    }
}
