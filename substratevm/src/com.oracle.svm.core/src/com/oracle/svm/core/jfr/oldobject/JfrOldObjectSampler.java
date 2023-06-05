package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.locks.SpinLock;
import com.oracle.svm.core.thread.JavaThreads;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.lang.ref.WeakReference;

public final class JfrOldObjectSampler {
    private static final int SAMPLER_SIZE = 256;

    private OldObjectArray samples;
    private OldObjectPriorityQueue queue;
    private SpinLock lock;
    private long lastSweep = Long.MAX_VALUE;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrOldObjectSampler() {
    }

    public void initSampler() {
        System.out.println("Init sampler");
        this.samples = new OldObjectArray(SAMPLER_SIZE);
        this.queue = new OldObjectPriorityQueue(this.samples);
        this.lock = new SpinLock();
        System.out.println("this.samples=" + this.samples);
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    public void sample(WeakReference<Object> ref, long allocatedSize, int arrayLength) {
        final boolean success = lock.tryLock();
        if (!success) {
            return;
        }

        try {
            if (queue.isFull()) {
                if (queue.peek().span > allocatedSize) {
                    // Sample will not fit, try to scavenge
                    int numDead = scavenge();
                    if (numDead == 0) {
                        // Sample will not fit and all objects still in use, return early
                        return;
                    }
                } else {
                    // Offered element has a higher priority,
                    // vacate from the lowest priority one and insert the element.
                    evict();
                }
            }

            store(ref, allocatedSize, JfrTicks.elapsedTicks(), arrayLength);
        } finally {
            lock.unlock();
        }
    }
    // Making callees not uninterruptible to deal with WeakReference.get()

    @Uninterruptible(reason = "Accesses allocation sampler.", calleeMustBe = false)
    private int scavenge() {
        int numDead = 0;
        for (int i = 0; i < samples.getCapacity(); i++) {
            final OldObject sample = samples.getSample(i);
            if (sample.reference != null && sample.reference.get() == null) {
                remove(sample);
                numDead++;
            }
        }
        return numDead;
    }

    /**
     * Remove a given sample from the sampler.
     */
    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void remove(OldObject sample) {
        final OldObject prev = sample.previous;
        if (prev != null) {
            queue.remove(prev);
            prev.span += sample.span;
            queue.push(prev);
        }
        queue.remove(sample);
        sample.clear();
    }

    /**
     * Evict the sample with the smallest span from the sampler.
     * This includes removing it from the head of the queue,
     * as well as adjusting the list view links
     * and clearing its data.
     */
    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void evict() {
        queue.poll().clear();
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void store(WeakReference<?> ref, long allocatedSize, long allocatedTime, int arrayLength) {
        final int index = queue.getCount();
        final OldObject sample = samples.getSample(index);

        final Thread thread = Thread.currentThread();
        final long heapUsedAtLastGC = Heap.getHeap().getUsedAtLastGC();

        // Note: thread can be null during shutdown, don't remove thread null check
        if (thread == null) {
            sample.set(ref, allocatedSize, allocatedTime, 0L, 0L, heapUsedAtLastGC, arrayLength);
        } else {
            final long stackTraceId = SubstrateJVM.get().getStackTraceId(JfrEvent.OldObjectSample, 0);
            final long threadId = JavaThreads.getThreadId(thread);
            sample.set(ref, allocatedSize, allocatedTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength);
        }

        queue.push(sample);
    }

    public void emit(long cutoff, boolean emitAll) {
        lock.lock();

        try {
            if (cutoff <= 0) {
                // No reference chains
                OldObjectEventEmitter.emitUnchained(samples, emitAll ? Long.MAX_VALUE : lastSweep);
            }

            // todo support cutoff > 0 (path-to-gc-roots)
        } finally {
            lock.unlock();
        }
    }

    public void setLastSweep(long lastSweep) {
        this.lastSweep = lastSweep;
    }
}
