package com.oracle.svm.core.jfr;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jfr.events.OldObjectSampleEvent;
import com.oracle.svm.core.thread.JavaThreads;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.lang.ref.WeakReference;

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

    // private final Lock lock = new ReentrantLock();

    final JfrOldObjectSampleArray samples;
    final JfrOldObjectSamplePriorityQueue queue;
    final JfrOldObjectSampleList list;
    private long totalAllocated;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrOldObjectSampler() {
        this.samples = new JfrOldObjectSampleArray(SAMPLER_SIZE);
        this.queue = new JfrOldObjectSamplePriorityQueue(this.samples);
        this.list = new JfrOldObjectSampleList();
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    public void sample(WeakReference<Object> ref, long allocatedSize, int arrayLength) {
        // Not allowed. tryLock() throwing:
        // Fatal error: org.graalvm.compiler.java.BytecodeParser$BytecodeParserError: org.graalvm.compiler.debug.GraalError:
        // Cannot use an assertion within the context of an intrinsic: AnalysisField<Thread.$assertionsDisabled accessed: 0 reads: false written: 0 folded: 0>
        // final boolean locked = lock.tryLock();
        // if (!locked) {
        //     Logger.log(LogTag.JFR_SYSTEM, LogLevel.TRACE, "Skipping old object sample due to lock contention");
        //     return;
        // }

        // todo: if tryLock not allowed, maybe a volatile boolean can be used?
        // todo: check if dead samples are present and clean those up?

        totalAllocated += allocatedSize;

        if (queue.isFull())
        {
            if (getSpan(queue.peek()) > allocatedSize)
            {
                return;
            }

            evict();
        }

        // todo calling JfrTicks.elapsedTicks() throws error that time related code cannot be inlined
        //      should we set it to a dummy value and fix it up (somehow?) when actually emitting the event?
        store(ref, allocatedSize, JfrTicks.elapsedTicks(), arrayLength);
    }

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
        // todo rename to heapUsedAtLastGC for consistency
        final long usedAtLastGC = Heap.getHeap().getUsedAtLastGC();

        // Note: thread can be null during shutdown, don't remove thread null check
        if (thread == null) {
            setSample(ref, allocatedSize, allocatedTime, 0L, 0L, usedAtLastGC, arrayLength, sample);
        } else {
            // todo see if segfaults for retrieving stacktrace id go away
            //      https://gist.github.com/galderz/51020f04735ace36610cab1dd8c27c2c
            // final long stackTraceId = SubstrateJVM.get().getStackTraceId(JfrEvent.OldObjectSample, 4);
            final long stackTraceId = 1;
            final long threadId = JavaThreads.getThreadId(thread);
            setSample(ref, allocatedSize, allocatedTime, threadId, stackTraceId, usedAtLastGC, arrayLength, sample);
        }

        queue.push(sample);
        list.prepend(sample);
    }

    void emit(long cutoff, boolean emitAll, boolean skipBFS, JfrChunkWriter chunkWriter) {
        // todo do we need exclusive access on object sampler instance?
        //      (For operations that require exclusive access (non-safepoint))

        if (cutoff <= 0) {
            // No reference chains
            writeEvents(emitAll, chunkWriter);
            return;
        }

        // todo: with reference chains
    }

    private void writeEvents(boolean emitAll, JfrChunkWriter chunkWriter) {
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
                System.out.printf("[%s] [JfrOldObjectSampler.writeEvents] add old object %s%n", Thread.currentThread().getName(), obj);
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
            // oldObjectRepo.write(chunkWriter);
            chunkWriter.writeSingleCheckpointEvent(oldObjectRepo);

            // A final pass to write the events
            current = list.head();
            final long timestamp = JfrTicks.elapsedTicks();
            while (current != null) {
                final long allocationTime = getAllocationTime(current);
                final Object obj = getReference(current).get();
                if (isAliveAndOlderThan(obj, lastSweep, allocationTime)) {
                    final long objectId = oldObjectRepo.getOldObjectId(obj);
                    System.out.printf("[%s] [JfrOldObjectSampler.writeEvents] write object id %d for object %s%n", Thread.currentThread().getName(), objectId, obj);
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

    private boolean isAliveAndOlderThan(Object obj, long lastSweep, long allocationTime) {
        return obj != null && allocationTime < lastSweep;
    }

    // Resolve stacktraces from their ids for checkpointing
    public void resolveStackTraces() {
        // todo check if last != last resolved ?
        // todo add is not dead check
//        samples.stream()
//                .filter(JfrOldObjectSample::hasStackTraceId)
//                .forEach(this::resolveStackTrace);
    }

    private void resolveStackTrace(JfrOldObjectSample sample) {
        // todo add blob cache support
        // todo add method to jstacktrace repository to resolve from stacktrace ids to stactraces
        // todo then serialize the stacktrace and cache it
    }
}
