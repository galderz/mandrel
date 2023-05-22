package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jfr.JfrChunkWriter;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.locks.SpinLock;
import com.oracle.svm.core.thread.JavaThreads;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.lang.ref.WeakReference;

public final class JfrOldObjectSampler {
    private static final int SAMPLER_SIZE = 256;

    private final OldObjectArray samples;
    private final OldObjectPriorityQueue queue;
    private final OldObjectList list;
    private final SpinLock lock;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrOldObjectSampler() {
        this.samples = new OldObjectArray(SAMPLER_SIZE);
        this.queue = new OldObjectPriorityQueue(this.samples);
        this.list = new OldObjectList();
        this.lock = new SpinLock();
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
        OldObject current = list.head();
        while (current != null) {
            OldObject next = list.next(current);
            final WeakReference<?> ref = current.reference;
            if (ref.get() == null) {
                remove(current);
                numDead++;
            }

            current = next;
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
        list.remove(sample);
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
        final OldObject head = queue.poll();
        list.remove(head);
        head.clear();
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
            // todo see if segfaults for retrieving stacktrace id go away
            //      https://gist.github.com/galderz/51020f04735ace36610cab1dd8c27c2c
            // final long stackTraceId = SubstrateJVM.get().getStackTraceId(JfrEvent.OldObjectSample, 4);
            final long stackTraceId = 1;
            final long threadId = JavaThreads.getThreadId(thread);
            sample.set(ref, allocatedSize, allocatedTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength);
        }

        queue.push(sample);
        list.prepend(sample);
    }

    public void emit(long cutoff) {
        lock.lock();

        try {
            if (cutoff <= 0) {
                // No reference chains
                OldObjectEventEmitter.emitUnchained(list);
            }

            // todo support cutoff > 0 (path-to-gc-roots)
        } finally {
            lock.unlock();
        }
    }
}
