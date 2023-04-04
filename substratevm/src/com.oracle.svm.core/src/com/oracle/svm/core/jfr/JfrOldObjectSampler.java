package com.oracle.svm.core.jfr;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jfr.events.OldObjectSampleEvent;
import com.oracle.svm.core.locks.SpinLock;
import com.oracle.svm.core.thread.JavaThreads;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static com.oracle.svm.core.jfr.JfrOldObjectSampleArray.clearSample;
import static com.oracle.svm.core.jfr.JfrOldObjectSampleArray.getAllocationTime;
import static com.oracle.svm.core.jfr.JfrOldObjectSampleArray.getArrayLength;
import static com.oracle.svm.core.jfr.JfrOldObjectSampleArray.getHeapUsedAtLastGC;
import static com.oracle.svm.core.jfr.JfrOldObjectSampleArray.getPrevious;
import static com.oracle.svm.core.jfr.JfrOldObjectSampleArray.getReference;
import static com.oracle.svm.core.jfr.JfrOldObjectSampleArray.getSpan;
import static com.oracle.svm.core.jfr.JfrOldObjectSampleArray.getStackTraceId;
import static com.oracle.svm.core.jfr.JfrOldObjectSampleArray.getThreadId;
import static com.oracle.svm.core.jfr.JfrOldObjectSampleArray.setSample;
import static com.oracle.svm.core.jfr.JfrOldObjectSampleArray.setSpan;

public final class JfrOldObjectSampler {
    private static final int SAMPLER_SIZE = 256;

    final JfrOldObjectSampleArray samples;
    final JfrOldObjectSamplePriorityQueue queue;
    final JfrOldObjectSampleList list;
    private final SpinLock lock;
    private long totalAllocated;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrOldObjectSampler() {
        this.samples = new JfrOldObjectSampleArray(SAMPLER_SIZE);
        this.queue = new JfrOldObjectSamplePriorityQueue(this.samples);
        this.list = new JfrOldObjectSampleList();
        this.lock = new SpinLock();
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    public void sample(WeakReference<Object> ref, long allocatedSize, int arrayLength) {
        final boolean success = lock.tryLock();
        if (!success) {
            return;
        }

        try {
            totalAllocated += allocatedSize;

            if (queue.isFull()) {
                if (getSpan(queue.peek()) > allocatedSize) {
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

    @Uninterruptible(reason = "Accesses allocation sampler.", calleeMustBe = false)
    private int scavenge() {
        int numDead = 0;
        Object[] current = list.head();
        while (current != null) {
            Object[] next = list.next(current);
            final WeakReference<?> ref = getReference(current);
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
    private void remove(Object[] sample) {
        final Object[] prev = getPrevious(sample);
        if (prev != null) {
            queue.remove(prev);
            setSpan(getSpan(sample) + getSpan(prev), prev);
            queue.push(prev);
        }
        queue.remove(sample);
        list.remove(sample);
        clearSample(sample);
    }

    /**
     * Evict the sample with the smallest span from the sampler.
     * This includes removing it from the head of the queue,
     * as well as adjusting the list view links
     * and clearing its data.
     */
    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void evict() {
        final Object[] head = queue.poll();
        list.remove(head);
        clearSample(head);
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void store(WeakReference<?> ref, long allocatedSize, long allocatedTime, int arrayLength) {
        final int index = queue.getCount();
        final Object[] sample = samples.getSample(index);

        final Thread thread = Thread.currentThread();
        final long heapUsedAtLastGC = Heap.getHeap().getUsedAtLastGC();

        // Note: thread can be null during shutdown, don't remove thread null check
        if (thread == null) {
            setSample(ref, allocatedSize, allocatedTime, 0L, 0L, heapUsedAtLastGC, arrayLength, sample);
        } else {
            // todo see if segfaults for retrieving stacktrace id go away
            //      https://gist.github.com/galderz/51020f04735ace36610cab1dd8c27c2c
            // final long stackTraceId = SubstrateJVM.get().getStackTraceId(JfrEvent.OldObjectSample, 4);
            final long stackTraceId = 1;
            final long threadId = JavaThreads.getThreadId(thread);
            setSample(ref, allocatedSize, allocatedTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength, sample);
        }

        queue.push(sample);
        list.prepend(sample);
    }

    void emit(long cutoff, boolean emitAll, boolean skipBFS, JfrChunkWriter chunkWriter) {
        lock.lock();

        try {
            if (cutoff <= 0) {
                // No reference chains
                writeEvents(emitAll, chunkWriter);
                return;
            }

            writePathToGcRoots();
            writeEvents(emitAll, chunkWriter);
        } finally {
            lock.unlock();
        }
    }

    private void writeEvents(boolean emitAll, JfrChunkWriter chunkWriter) {
        System.out.println("JfrOldObjectSampler.writeEvents start");
        // todo add last sweep to sampler and handle !emitAll
        final long lastSweep = Long.MAX_VALUE;

        final JfrOldObjectRepository oldObjectRepo = SubstrateJVM.getOldObjectRepository();

        Object[] current;
        int count = 0;

        // First pass to associate a live sample with its immediate edge,
        // in preparation for writing checkpoint information.
        current = list.head();
        while (current != null) {
            final long allocationTime = getAllocationTime(current);
            final Object obj = getReference(current).get();
            if (isAliveAndOlderThan(obj, lastSweep, allocationTime)) {
                // System.out.printf("[%s] [JfrOldObjectSampler.writeEvents] add old object %s%n", Thread.currentThread().getName(), obj);
                oldObjectRepo.addOldObject(obj);
                count++;
            }

            current = list.next(current);
        }

        if (count > 0) {
            // Second pass that serializes checkpoints and potential chains.
            // These need to be serialized before writing the events,
            // to ensure that constants are available for resolution
            // at the time old object sample events appear in the stream.
            chunkWriter.writeSingleCheckpointEvent(oldObjectRepo);

            // A final pass to write the events
            current = list.head();
            final long timestamp = JfrTicks.elapsedTicks();
            while (current != null) {
                final long allocationTime = getAllocationTime(current);
                final Object obj = getReference(current).get();
                if (isAliveAndOlderThan(obj, lastSweep, allocationTime)) {
                    final long objectId = oldObjectRepo.getOldObjectId(obj);
                    // System.out.printf("[%s] [JfrOldObjectSampler.writeEvents] write object id %d for object %s%n", Thread.currentThread().getName(), objectId, obj);
                    final long threadId = getThreadId(current);
                    final long stackTraceId = getStackTraceId(current);
                    final long heapUsedAtLastGC = getHeapUsedAtLastGC(current);
                    final int arrayLength = getArrayLength(current);
                    OldObjectSampleEvent.emit(timestamp, objectId, allocationTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength);
                }

                current = list.next(current);
            }
        }

        oldObjectRepo.clear();

        System.out.printf("Emit completed for %d samples%n", count);
    }

    private void writePathToGcRoots() {
        System.out.println("JfrOldObjectSampler.writePathToGcRoots");
        // todo add last sweep to sampler
        final long lastSweep = Long.MAX_VALUE;

//        new LeakToGcRoots().findPaths();


//        final PathToGcRoots pathToGcRoots = new PathToGcRoots();

        // final Set<Object> oldObjects = Collections.newSetFromMap(new IdentityHashMap<>());
        final IdentityHashMap<Object, Boolean> oldObjects = new IdentityHashMap<>();

        Object[] current = list.head();
        while (current != null) {
            final long allocationTime = getAllocationTime(current);
            final Object obj = getReference(current).get();
            if (isAliveAndOlderThan(obj, lastSweep, allocationTime)) {
                oldObjects.put(obj, Boolean.TRUE);
//                final PathToGcRoots.PathElement[] pathToRoot = pathToGcRoots.findPathToRoot(obj);
//                if (pathToRoot.length > 0) {
//                    SubstrateJVM.getOldObjectRepository().addOldObjectsInPathToGcRoot(pathToRoot, oldObjectUtils);
//                }
            }
            current = list.next(current);
        }

        // System.out.println("JfrOldObjectSampler.computePathToGcRoots leak targets: " + leakTargets);
        final Bfs2PathToGcRoots bfs = new Bfs2PathToGcRoots();
        final PathToGcRootsStore pathStore = new PathToGcRootsStore(oldObjects.size()); // todo consider pre-allocating for max numb of tracked leaks and re-use that
        bfs.findPathToGcRoots(oldObjects, pathStore);
        SubstrateJVM.getOldObjectRepository().addOldObjects(oldObjects, pathStore);
        System.out.println("JfrOldObjectSampler.writePathToGcRoots end");
    }

    private boolean isAliveAndOlderThan(Object obj, long lastSweep, long allocationTime) {
        return obj != null && allocationTime < lastSweep;
    }
}
