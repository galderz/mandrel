package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;

import java.lang.ref.WeakReference;

interface OldObjectEffects {
    @Uninterruptible(reason = "Accesses allocation profiler.")
    long elapsedTicks();

    @Uninterruptible(reason = "Accesses allocation profiler.")
    boolean isAlive(WeakReference<?> ref);

    @Uninterruptible(reason = "Accesses allocation profiler.")
    void emit(Object aliveObject, long timestamp, long allocationTime, long threadId, long stackTraceId, long usedAtLastGC, int arrayLength);

    @Uninterruptible(reason = "Accesses allocation profiler.")
    default boolean isDead(WeakReference<?> ref) {
        return !isAlive(ref);
    }
}
