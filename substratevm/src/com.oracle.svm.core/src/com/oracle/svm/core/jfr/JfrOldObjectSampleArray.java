package com.oracle.svm.core.jfr;

import com.oracle.svm.core.Uninterruptible;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.lang.ref.WeakReference;

public class JfrOldObjectSampleArray {
    private static final int REF_SLOT = 0;
    private static final int SPAN_SLOT = 1;
    private static final int ALLOCATION_TIME_SLOT = 2;
    private static final int THREAD_ID_SLOT = 3;
    private static final int STACKTRACE_ID_SLOT = 4;
    private static final int USED_AT_GC_SLOT = 5;
    private static final int ARRAY_LENGTH_SLOT = 6;
    private static final int PREVIOUS_SLOT = 7;

    static final Object[] EMPTY = new Object[PREVIOUS_SLOT + 1];

    private final Object[][] samples;

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrOldObjectSampleArray(int capacity) {
        this.samples = new Object[capacity][];
        for (int i = 0; i < this.samples.length; i++) {
            this.samples[i] = new Object[PREVIOUS_SLOT + 1];
        }
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    int getCapacity() {
        return samples.length;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    void swap(int i, int j) {
        final Object[] tmp = samples[i];
        samples[i] = samples[j];
        samples[j] = tmp;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    int getIndexOf(Object[] sample) {
        for (int i = 0; i < samples.length; i++) {
            if (sample == samples[i]) {
                return i;
            }
        }

        return -1;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    Object[] getSample(int index) {
        return samples[index];
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    static WeakReference<?> getReference(Object[] sample) {
        return (WeakReference<?>) sample[REF_SLOT];
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    static long getSpan(Object[] sample) {
        return (long) sample[SPAN_SLOT];
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    long getSpan(int index) {
        return (long) samples[index][SPAN_SLOT];
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    static void setSpan(long value, Object[] sample) {
        sample[SPAN_SLOT] = value;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    static long getAllocationTime(Object[] sample) {
        return (long) sample[ALLOCATION_TIME_SLOT];
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    static long getThreadId(Object[] sample) {
        return (long) sample[THREAD_ID_SLOT];
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    static long getStackTraceId(Object[] sample) {
        return (long) sample[STACKTRACE_ID_SLOT];
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    static long getUsedAtGC(Object[] sample) {
        return (long) sample[USED_AT_GC_SLOT];
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    static int getArrayLength(Object[] sample) {
        return (int) sample[ARRAY_LENGTH_SLOT];
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    static Object[] getPrevious(Object[] entry) {
        return (Object[]) entry[PREVIOUS_SLOT];
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    static void setPrevious(Object[] value, Object[] sample) {
        sample[PREVIOUS_SLOT] = value;
    }
//    WeakReference<?> getReference(int index)
//    {
//        return (WeakReference<?>) samples[index][REF_SLOT];
//    }
//
//    void setReference(WeakReference<?> value, int index)
//    {
//        samples[index][REF_SLOT] = value;

//    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    // todo make static
    void set(WeakReference<?> ref, long allocatedSize, long allocatedTime, long threadId, long stackTraceId, long usedAtGC, int arrayLength, Object[] sample) {
        sample[REF_SLOT] = ref;
        sample[SPAN_SLOT] = allocatedSize;
        sample[ALLOCATION_TIME_SLOT] = allocatedTime;
        sample[THREAD_ID_SLOT] = threadId;
        sample[STACKTRACE_ID_SLOT] = stackTraceId;
        sample[USED_AT_GC_SLOT] = usedAtGC;
        sample[ARRAY_LENGTH_SLOT] = arrayLength;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    // todo make static
    void clear(Object[] sample) {
        sample[REF_SLOT] = null;
        sample[SPAN_SLOT] = 0;
        sample[ALLOCATION_TIME_SLOT] = 0;
        sample[THREAD_ID_SLOT] = 0;
        sample[STACKTRACE_ID_SLOT] = 0;
        sample[USED_AT_GC_SLOT] = 0;
        sample[ARRAY_LENGTH_SLOT] = 0;
    }

//    void setSpan(long value, int index)
//    {
//        samples[index][SPAN_SLOT] = value;
//    }
//
//    long getAllocationTime(int index)
//    {
//        return (long) samples[index][ALLOCATION_TIME_SLOT];
//    }
//
//    void setAllocationTime(long value, int index)
//    {
//        samples[index][ALLOCATION_TIME_SLOT] = value;
//    }
//
//    long getThreadId(int index)
//    {
//        return (long) samples[index][THREAD_ID_SLOT];
//    }
//
//    void setThreadId(long value, int index)
//    {
//        samples[index][THREAD_ID_SLOT] = value;
//    }
//
//    long getStackTraceId(int index)
//    {
//        return (long) samples[index][STACKTRACE_ID_SLOT];
//    }
//
//    void setStackTraceId(long value, int index)
//    {
//        samples[index][STACKTRACE_ID_SLOT] = value;
//    }
//
//    long getUsedAtGC(int index)
//    {
//        return (long) samples[index][USED_AT_GC_SLOT];
//    }
//
//    void setUsedAtGC(long value, int index)
//    {
//        samples[index][USED_AT_GC_SLOT] = value;
//    }
//
//    int getArrayLength(int index)
//    {
//        return (int) samples[index][ARRAY_LENGTH_SLOT];
//    }
//
//    void setArrayLength(long value, int index)
//    {
//        samples[index][ARRAY_LENGTH_SLOT] = value;
//    }
//
//    Object[] getPrevious(int index)
//    {
//        return (Object[]) samples[index][PREVIOUS_SLOT];
//    }
//
//    void setPrevious(Object[] value, int index)
//    {
//        samples[index][PREVIOUS_SLOT] = value;
//    }
}
