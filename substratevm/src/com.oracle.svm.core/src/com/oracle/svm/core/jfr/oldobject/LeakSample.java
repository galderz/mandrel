package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;

import java.lang.ref.WeakReference;

final class LeakSample {
    static final LeakSample EMPTY = new LeakSample();

    WeakReference<?> reference;
    long span;
    long allocationTime;
    long threadId;
    long stackTraceId;
    long heapUsedAtLastGC;
    int arrayLength;
    LeakSample previous;

    @Uninterruptible(reason = "Accesses allocation sampler.")
    void set(WeakReference<?> ref, long allocatedSize, long allocatedTime, long threadId, long stackTraceId, long heapUsedAtLastGC, int arrayLength) {
        this.reference = ref;
        this.span = allocatedSize;
        this.allocationTime = allocatedTime;
        this.threadId = threadId;
        this.stackTraceId = stackTraceId;
        this.heapUsedAtLastGC = heapUsedAtLastGC;
        this.arrayLength = arrayLength;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    void clear() {
        this.reference = null;
        this.span = 0L;
        this.allocationTime = 0L;
        this.threadId = 0L;
        this.stackTraceId = 0L;
        this.heapUsedAtLastGC = 0L;
        this.arrayLength = 0;
        this.previous = null;
    }
}
