package com.oracle.svm.core.jfr;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.events.OldObjectSampleEvent;
import com.oracle.svm.core.util.BoundedPriorityQueue;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class JfrOldObjectSampler {
    private static final int SAMPLER_SIZE = 256;

    // private final Lock lock = new ReentrantLock();
    // Not allowed to be access from allocation snippet, it triggers stackoverflow - todo find a more lightweight version
    // private final BoundedPriorityQueue<JfrOldObjectSample> samples = new BoundedPriorityQueue<>(SAMPLER_SIZE, JfrOldObjectSample.Comparator.INSTANCE);
    private final JfrOldObjectSamplePriorityQueue samples;
    private final JfrEdgeStore edgeStore;
    private long totalAllocated;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrOldObjectSampler() {
        samples = new JfrOldObjectSamplePriorityQueue(SAMPLER_SIZE);
        edgeStore = new JfrEdgeStore();
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    public void sample(Object object, long allocated) {
        // Not allowed
        // Logger.log(LogTag.JFR, LogLevel.TRACE, "SLOW ALLOCATION!!");

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

        totalAllocated += allocated;

        if (samples.isFull()) {
            if (samples.peekSpan() > allocated) {
                // Sample will not fit, return early
                return;
            }
            // Offered element has a higher priority,
            // vacate from the lowest priority one and insert the element.
            samples.poll();
        }

        // todo add thread id
        // todo add thread
        // todo add stacktrace id
        // todo add heap used at last gc

        // todo calling JfrTicks.elapsedTicks() throws error that time related code cannot be inlined
        //      should we set it to a dummy value and fix it up (somehow?) when actually emitting the event?
        samples.push(object, allocated, 0);
    }

    void emit(long cutoff, boolean emitAll, boolean skipBFS) {
        // todo do we need exclusive access on object sampler instance?
        //      (For operations that require exclusive access (non-safepoint))

        if (cutoff <= 0) {
            // No reference chains
            final long timestamp = JfrTicks.elapsedTicks();
            writeEvents(timestamp, emitAll);
            return;
        }

        // todo: with reference chains
    }

    private void writeEvents(long timestamp, boolean emitAll) {
        // todo add last sweep to sampler and handle !emitAll
        final long lastSweep = Long.MAX_VALUE;

        // First pass to associate a live sample with its immediate edge,
        // in preparation for writing checkpoint information.
        final JfrOldObjectSamplePriorityQueue.SampleList sampleList = samples.asList();
        int current = sampleList.firstIndex();
        int count = 0;
        while (current >= 0) {
            if (isAliveAndOlderThan(lastSweep, sampleList.allocationTimeAt(current))) {
                linkSampleWithEdge(sampleList.objectAt(current));
                count++;
            }
            current = sampleList.prevIndex(current);
        }


        if (count > 0) {
            // Second pass that serializes checkpoints and potential chains.
            // These need to be serialized before writing the events,
            // to ensure that constants are available for resolution
            // at the time old object sample events appear in the stream.
            // todo

            // A final pass to write the events
            current = sampleList.firstIndex();
            while (current >= 0) {
                final long allocationTime = sampleList.allocationTimeAt(current);
                if (isAliveAndOlderThan(lastSweep, allocationTime)) {
                    final long objectId = edgeStore.getObjectId(sampleList.objectAt(current));
                    OldObjectSampleEvent.emit(timestamp, allocationTime, objectId);
                }
                current = sampleList.prevIndex(current);
            }
        }
    }

    private void linkSampleWithEdge(Object object) {
        // todo check if object already associated with an edge, during heap traversal?
        edgeStore.put(object);
    }

    private boolean isAliveAndOlderThan(long lastSweep, long allocationTime) {
        // todo add not dead check
        return allocationTime < lastSweep;
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
