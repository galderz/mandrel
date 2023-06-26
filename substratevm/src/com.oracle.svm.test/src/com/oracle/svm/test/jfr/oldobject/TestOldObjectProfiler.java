package com.oracle.svm.test.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.oldobject.OldObjectEffects;
import com.oracle.svm.core.jfr.oldobject.OldObjectProfiler;
import org.junit.Assert;
import org.junit.Test;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class TestOldObjectProfiler {
    @Test
    public void testDoNotEmitEventsNewerThanLastSweep() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size);
        final OldObjectProfiler profiler = new OldObjectProfiler(size, effects);
        for (int i = 0; i < size; i++) {
            profiler.sample(new WeakReference<>(String.valueOf(i)), i * 100, -1);
        }

        profiler.emit(0, 10);
        Assert.assertEquals(0, effects.sizeSamples());
    }

    @Test
    public void testSampleAfterEmit() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size) {
            @Override
            @Uninterruptible(reason = "Accesses allocation profiler.")
            public boolean isAlive(WeakReference<?> ref) {
                if (ticks <= 29) {
                    return true;
                }
                final Object obj = ref.get();
                return obj != null && ((int) ref.get()) >= 11;
            }
        };
        final OldObjectProfiler profiler = new OldObjectProfiler(size, effects);

        for (int i = 0; i < size; i++) {
            profiler.sample(new WeakReference<>(i), i * 100, -1);
        }

        profiler.emit(0, Long.MAX_VALUE);
        assertStreamEquals(IntStream.rangeClosed(0, 7).boxed(), effects.objects());

        for (int i = 1; i <= size / 2; i++) {
            profiler.sample(new WeakReference<>(10 + i), i * 1000, -1);
        }

        // Clear accumulated samples and see what gets emitted now.
        effects.clearSamples();
        profiler.emit(0, Long.MAX_VALUE);
        assertStreamEquals(IntStream.rangeClosed(11, 14).boxed(), effects.objects());
    }

    @Test
    public void testScavengeMiddle() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size) {
            @Override
            @Uninterruptible(reason = "Accesses allocation profiler.")
            public boolean isAlive(WeakReference<?> ref) {
                return !"4".equals(ref.get());
            }
        };
        final OldObjectProfiler profiler = new OldObjectProfiler(size, effects);

        for (int i = 0; i < size; i++) {
            profiler.sample(new WeakReference<>(String.valueOf(i)), i * 100, -1);
        }

        profiler.emit(0, Long.MAX_VALUE);
        assertLongStreamEquals(LongStream.concat(LongStream.rangeClosed(20L, 23L), LongStream.rangeClosed(25L, 27L)), effects.allocationTimes());
    }

    @Test
    public void testScavengeYoungest() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size) {
            @Override
            @Uninterruptible(reason = "Accesses allocation profiler.")
            public boolean isAlive(WeakReference<?> ref) {
                return !"7".equals(ref.get());
            }
        };
        final OldObjectProfiler profiler = new OldObjectProfiler(size, effects);

        for (int i = 0; i < size; i++) {
            profiler.sample(new WeakReference<>(String.valueOf(i)), i * 100, -1);
        }

        profiler.emit(0, Long.MAX_VALUE);
        assertLongStreamEquals(LongStream.rangeClosed(20L, 26L), effects.allocationTimes());
    }

    @Test
    public void testScavengeOldest() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size) {
            @Override
            @Uninterruptible(reason = "Accesses allocation profiler.")
            public boolean isAlive(WeakReference<?> ref) {
                return !"0".equals(ref.get());
            }
        };
        final OldObjectProfiler profiler = new OldObjectProfiler(size, effects);

        for (int i = 0; i < size; i++) {
            profiler.sample(new WeakReference<>(String.valueOf(i)), i * 100, -1);
        }

        profiler.emit(0, Long.MAX_VALUE);
        assertLongStreamEquals(LongStream.rangeClosed(21L, 27L), effects.allocationTimes());
    }

    @Test
    public void testScavengeAll() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size) {
            @Override
            @Uninterruptible(reason = "Accesses allocation profiler.")
            public boolean isAlive(WeakReference<?> ref) {
                return false;
            }
        };
        final OldObjectProfiler profiler = new OldObjectProfiler(size, effects);

        for (int i = 0; i < size; i++) {
            profiler.sample(new WeakReference<>(String.valueOf(i)), i * 100, -1);
        }

        profiler.emit(0, Long.MAX_VALUE);
        Assert.assertEquals(0, effects.sizeSamples());
    }

    @Test
    public void testSampleManyEmitQueueSize() {
        final int size = 256;
        final TestEffects effects = new TestEffects(20L, size);
        final OldObjectProfiler profiler = new OldObjectProfiler(size, effects);

        for (int i = 0; i < 1_000_000; i++) {
            profiler.sample(new WeakReference<>(String.valueOf(i)), i, -1);
        }

        profiler.emit(0, Long.MAX_VALUE);
        Assert.assertEquals(256, effects.sizeSamples());
    }

    @Test
    public void testSampleOverflowEvictYoungest() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size);
        final OldObjectProfiler profiler = new OldObjectProfiler(size, effects);

        for (int i = 0; i < size + 1; i++) {
            // Evict youngest because that's the one with the lowest span.
            final int allocatedSize = i == (size - 1) ? 100 : 200;
            profiler.sample(new WeakReference<>(String.valueOf(i)), allocatedSize, -1);
        }

        profiler.emit(0, Long.MAX_VALUE);
        assertLongStreamEquals(LongStream.concat(LongStream.rangeClosed(20L, 26L), LongStream.rangeClosed(28L, 28L)), effects.allocationTimes());
    }

    @Test
    public void testSampleOverflowEvictMiddle() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size);
        final OldObjectProfiler profiler = new OldObjectProfiler(size, effects);

        for (int i = 0; i < size + 1; i++) {
            // Evict middle because that's the one with the lowest span.
            final int allocatedSize = i == (size / 2) ? 100 : 200;
            profiler.sample(new WeakReference<>(String.valueOf(i)), allocatedSize, -1);
        }

        profiler.emit(0, Long.MAX_VALUE);
        assertLongStreamEquals(LongStream.concat(LongStream.rangeClosed(20L, 23L), LongStream.rangeClosed(25L, 28L)), effects.allocationTimes());
    }

    @Test
    public void testSampleOverflowEvictOldest() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size);
        final OldObjectProfiler profiler = new OldObjectProfiler(size, effects);

        for (int i = 0; i < size + 1; i++) {
            // Evict oldest because that's the one with the lowest span.
            profiler.sample(new WeakReference<>(String.valueOf(i)), i * 100, -1);
        }

        profiler.emit(0, Long.MAX_VALUE);
        assertLongStreamEquals(LongStream.rangeClosed(21L, 28L), effects.allocationTimes());
    }

    @Test
    public void testSampleFullEmit() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size);
        final OldObjectProfiler profiler = new OldObjectProfiler(size, effects);

        for (int i = 0; i < size; i++) {
            profiler.sample(new WeakReference<>(String.valueOf(i)), i * 100, -1);
        }

        profiler.emit(0, Long.MAX_VALUE);
        assertLongStreamEquals(LongStream.rangeClosed(20L, 27L), effects.allocationTimes());
    }

    @Test
    public void testSampleNotFullEmit() {
        final int size = 8;
        final TestEffects effects = new TestEffects(20L, size);
        final OldObjectProfiler profiler = new OldObjectProfiler(size, effects);

        for (int i = 0; i < size - 1; i++) {
            profiler.sample(new WeakReference<>(String.valueOf(i)), i * 100, -1);
        }

        profiler.emit(0, Long.MAX_VALUE);
        assertLongStreamEquals(LongStream.rangeClosed(20L, 26L), effects.allocationTimes());
    }

    @Test
    public void testSingleSample() {
        final int size = 3;
        final TestEffects effects = new TestEffects(20L, 3);
        final OldObjectProfiler profiler = new OldObjectProfiler(size, effects);
        profiler.sample(new WeakReference<>("a-sample"), 10, -1);
        profiler.emit(0, Long.MAX_VALUE);
        final TestSample testSample = effects.peekLastSample();
        Assert.assertNotNull(testSample);
        Assert.assertEquals("a-sample", testSample.obj);
        Assert.assertEquals(21, testSample.timestamp);
        Assert.assertEquals(20, testSample.allocationTime);
        Assert.assertEquals(1, testSample.threadId);
        Assert.assertEquals(40, testSample.stackTraceId);
        Assert.assertEquals(50, testSample.heapUsedAtLastGC);
        Assert.assertEquals(-1, testSample.arrayLength);
        Assert.assertEquals(1, effects.sizeSamples());
    }

    static void assertLongStreamEquals(LongStream expected, Stream<?> actual) {
        assertStreamEquals(expected.boxed(), actual);
    }

    static void assertStreamEquals(Stream<?> expected, Stream<?> actual) {
        Assert.assertEquals(expected.toList(), actual.toList());
    }

    private static class TestEffects implements OldObjectEffects {
        private final TestSample[] testSamples;
        private int head = 0;
        private int tail = 0;
        long ticks;

        TestEffects(long initialTicks, int size) {
            this.ticks = initialTicks;
            this.testSamples = new TestSample[size];
            for (int i = 0; i < size; i++) {
                this.testSamples[i] = new TestSample();
            }
        }

        @Override
        @Uninterruptible(reason = "Accesses allocation profiler.")
        public long elapsedTicks() {
            return ticks++;
        }

        @Override
        @Uninterruptible(reason = "Accesses allocation profiler.")
        public boolean isAlive(WeakReference<?> ref) {
            return true;
        }

        @Override
        @Uninterruptible(reason = "Accesses allocation profiler.")
        public void emit(Object aliveObject, long timestamp, long allocationTime, long threadId, long stackTraceId, long heapUsedAtLastGC, int arrayLength) {
            testSamples[tail++].set(aliveObject, timestamp, allocationTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength);
        }

        @Override
        @Uninterruptible(reason = "Accesses allocation profiler.")
        public long getStackTraceId() {
            return 40;
        }

        @Override
        @Uninterruptible(reason = "Accesses allocation profiler.")
        public long getHeapUsedAtLastGC() {
            return 50;
        }

        TestSample peekLastSample() {
            return testSamples[head];
        }

        int sizeSamples() {
            return tail - head;
        }

        Stream<Long> allocationTimes() {
            return Arrays.stream(testSamples).filter(s -> s.allocationTime != 0).map(TestSample::getAllocationTime);
        }

        Stream<Object> objects() {
            return Arrays.stream(testSamples).filter(s -> s.allocationTime != 0).map(TestSample::getObject);
        }

        public void clearSamples() {
            head = 0;
            tail = 0;
            for (TestSample testSample : testSamples) {
                testSample.set(null, 0, 0, 0, 0, 0, 0);
            }
        }
    }

    private static final class TestSample {
        Object obj;
        long timestamp;
        long allocationTime;
        long threadId;
        long stackTraceId;
        long heapUsedAtLastGC;
        int arrayLength;

        @Uninterruptible(reason = "Accesses allocation profiler.")
        void set(Object obj, long timestamp, long allocationTime, long threadId, long stackTraceId, long heapUsedAtLastGC, int arrayLength) {
            this.obj = obj;
            this.timestamp = timestamp;
            this.allocationTime = allocationTime;
            this.threadId = threadId;
            this.stackTraceId = stackTraceId;
            this.heapUsedAtLastGC = heapUsedAtLastGC;
            this.arrayLength = arrayLength;
        }

        long getAllocationTime() {
            return allocationTime;
        }

        Object getObject() {
            return obj;
        }
    }
}
