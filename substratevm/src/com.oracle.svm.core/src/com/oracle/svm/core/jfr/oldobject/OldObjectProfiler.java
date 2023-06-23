package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;

import java.lang.ref.WeakReference;

public final class OldObjectProfiler {
    private final OldObjectSampler sampler;
    private final OldObjectEventEmitter eventEmitter;

    public OldObjectProfiler(int queueSize, OldObjectEffects effects) {
        final OldObjectList list = new OldObjectList();
        this.sampler = new OldObjectSampler(queueSize, list, effects);
        this.eventEmitter = new OldObjectEventEmitter(list, effects);
    }

    @Uninterruptible(reason = "Accesses allocation profiler.")
    public void sample(WeakReference<Object> ref, long allocatedSize, int arrayLength) {
        sampler.sample(ref, allocatedSize, arrayLength);
    }

    @Uninterruptible(reason = "Accesses allocation profiler.")
    public void emit(long cutoff, long lastSweep) {
        eventEmitter.emit(cutoff, lastSweep);
    }
}
