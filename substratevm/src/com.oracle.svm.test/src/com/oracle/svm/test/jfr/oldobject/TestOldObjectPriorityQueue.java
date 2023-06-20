package com.oracle.svm.test.jfr.oldobject;

import com.oracle.svm.core.jfr.oldobject.OldObjectPriorityQueue;
import org.junit.Assert;
import org.junit.Test;

import java.lang.ref.WeakReference;

public class TestOldObjectPriorityQueue {
    @Test
    public void testFull() {
        OldObjectPriorityQueue queue = new OldObjectPriorityQueue(3);
        queue.push(new WeakReference<>(new Object()), 300, 1, 0, 0, 0, -1);
        assert !queue.isFull();
        queue.push(new WeakReference<>(new Object()), 200, 2, 0, 0, 0, -1);
        assert !queue.isFull();
        queue.push(new WeakReference<>(new Object()), 100,3, 0, 0, 0, -1);
        assert queue.isFull();
    }

    @Test
    public void testPushPeekPoll() {
        OldObjectPriorityQueue queue = new OldObjectPriorityQueue(10);
        queue.push(new WeakReference<>("200"), 200, 1, 10, 1_000, 10_000, -1);
        queue.push(new WeakReference<>("400"), 400, 2, 20, 2_000, 20_000, -2);
        queue.push(new WeakReference<>("300"), 300, 3, 30, 3_000, 30_000, -3);
        queue.push(new WeakReference<>("500"), 500, 4, 40, 4_000, 40_000, -4);
        queue.push(new WeakReference<>("100"), 100, 5, 50, 5_000, 50_000, -5);

        Assert.assertEquals(100, queue.peek().getSpan());
        Assert.assertEquals(5, queue.peek().getAllocationTime());
        Assert.assertEquals(50, queue.peek().getThreadId());
        Assert.assertEquals(5_000, queue.peek().getStackTraceId());
        Assert.assertEquals(50_000, queue.peek().getHeapUsedAtLastGC());
        Assert.assertEquals(-5, queue.peek().getArrayLength());
        queue.poll();
        Assert.assertEquals(200, queue.peek().getSpan());
        Assert.assertEquals(1, queue.peek().getAllocationTime());
        Assert.assertEquals(10, queue.peek().getThreadId());
        Assert.assertEquals(1_000, queue.peek().getStackTraceId());
        Assert.assertEquals(10_000, queue.peek().getHeapUsedAtLastGC());
        Assert.assertEquals(-1, queue.peek().getArrayLength());
        Assert.assertEquals("200", queue.peek().getReference().get());
        queue.poll();
        Assert.assertEquals(300, queue.peek().getSpan());
        Assert.assertEquals(3, queue.peek().getAllocationTime());
        Assert.assertEquals(30, queue.peek().getThreadId());
        Assert.assertEquals(3_000, queue.peek().getStackTraceId());
        Assert.assertEquals(30_000, queue.peek().getHeapUsedAtLastGC());
        Assert.assertEquals(-3, queue.peek().getArrayLength());
        Assert.assertEquals("300", queue.peek().getReference().get());
        queue.poll();
        Assert.assertEquals(400, queue.peek().getSpan());
        Assert.assertEquals(2, queue.peek().getAllocationTime());
        Assert.assertEquals(20, queue.peek().getThreadId());
        Assert.assertEquals(2_000, queue.peek().getStackTraceId());
        Assert.assertEquals(20_000, queue.peek().getHeapUsedAtLastGC());
        Assert.assertEquals(-2, queue.peek().getArrayLength());
        Assert.assertEquals("400", queue.peek().getReference().get());
        queue.poll();
        Assert.assertEquals(500, queue.peek().getSpan());
        Assert.assertEquals(4, queue.peek().getAllocationTime());
        Assert.assertEquals(40, queue.peek().getThreadId());
        Assert.assertEquals(4_000, queue.peek().getStackTraceId());
        Assert.assertEquals(40_000, queue.peek().getHeapUsedAtLastGC());
        Assert.assertEquals(-4, queue.peek().getArrayLength());
        Assert.assertEquals("500", queue.peek().getReference().get());
        queue.poll();
        Assert.assertEquals(0, queue.peek().getSpan());
        Assert.assertEquals(0, queue.peek().getAllocationTime());
        Assert.assertEquals(0, queue.peek().getThreadId());
        Assert.assertEquals(0, queue.peek().getStackTraceId());
        Assert.assertEquals(0, queue.peek().getHeapUsedAtLastGC());
        Assert.assertEquals(0, queue.peek().getArrayLength());
        Assert.assertNull(queue.peek().getReference());
    }
}
