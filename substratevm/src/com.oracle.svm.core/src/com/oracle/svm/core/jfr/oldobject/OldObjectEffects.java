package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;

import java.lang.ref.WeakReference;

public interface OldObjectEffects {
    @Uninterruptible(reason = "Accesses allocation profiler.")
    long elapsedTicks();

    @Uninterruptible(reason = "Accesses allocation profiler.")
    boolean isAlive(WeakReference<?> ref);

    @Uninterruptible(reason = "Accesses allocation profiler.")
    void emit(Object aliveObject, long timestamp, long allocationTime, long threadId, long stackTraceId, long heapUsedAtLastGC, int arrayLength);

    @Uninterruptible(reason = "Accesses allocation profiler.")
    default boolean isDead(WeakReference<?> ref) {
        return !isAlive(ref);
    }

    @Uninterruptible(reason = "Accesses allocation aprofiler.")
    long getStackTraceId();

    @Uninterruptible(reason = "Accesses allocation aprofiler.")
    long getHeapUsedAtLastGC();
}
