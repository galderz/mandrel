package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;

import java.lang.ref.WeakReference;

final class OldObjectSampler {
    private final OldObjectArray samples;
    private final OldObjectPriorityQueue queue;
    private final OldObjectList list;
    private final OldObjectEffects effects;

    OldObjectSampler(int queueSize, OldObjectList list, OldObjectEffects effects) {
        this.samples = new OldObjectArray(queueSize);
        this.queue = new OldObjectPriorityQueue(this.samples);
        this.list = list;
        this.effects = effects;
    }

    @Uninterruptible(reason = "Accesses allocation profiler.")
    void sample(WeakReference<Object> ref, long allocatedSize, int arrayLength) {
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

        store(ref, allocatedSize, effects.elapsedTicks(), arrayLength);
    }

    @Uninterruptible(reason = "Accesses allocation profiler.")
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
    @Uninterruptible(reason = "Accesses allocation profiler.")
    private void remove(OldObject sample) {
        final OldObject prev = sample.previous;
        if (prev != null) {
            // To keep samples evenly distributed over time,
            // the weight of a sample that is removed should be redistributed.
            // Here it gets redistributed to the sample that came just after in time.
            queue.remove(prev);
            prev.span += sample.span;
            queue.push(prev);
        }
        queue.remove(sample);
        list.remove(sample);
        sample.clear();
    }

    /**
     * Evict the sample with the smallest span from the sampler, by removing the head of the queue.
     */
    @Uninterruptible(reason = "Accesses allocation profiler.")
    private void evict() {
        final OldObject head = queue.poll();
        list.remove(head);
        head.clear();
    }

    @Uninterruptible(reason = "Accesses allocation profiler.")
    private void store(WeakReference<?> ref, long allocatedSize, long allocatedTime, int arrayLength) {
        final OldObject sample = push(ref, allocatedSize, allocatedTime, arrayLength);
        list.prepend(sample);
    }

    @Uninterruptible(reason = "Accesses allocation profiler.")
    private OldObject push(WeakReference<?> ref, long allocatedSize, long allocatedTime, int arrayLength) {
        final Thread thread = Thread.currentThread();
        final long heapUsedAtLastGC = effects.getHeapUsedAtLastGC();

        // Note: thread can be null during shutdown, don't remove thread null check
        if (thread == null) {
            return queue.push(ref, allocatedSize, allocatedTime, 0L, 0L, heapUsedAtLastGC, arrayLength);
        }

        final long stackTraceId = effects.getStackTraceId();
        final long threadId = effects.getThreadId(thread);
        return queue.push(ref, allocatedSize, allocatedTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength);
    }

}
