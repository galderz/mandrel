package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.events.OldObjectSampleEvent;
import com.oracle.svm.core.thread.JavaSpinLockUtils;
import jdk.internal.misc.Unsafe;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.lang.ref.WeakReference;

public final class JfrOldObjectProfiler {
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long LOCK_OFFSET = U.objectFieldOffset(JfrOldObjectProfiler.class, "lock");
    public static final int DEFAULT_SAMPLER_SIZE = 256;

    @SuppressWarnings("unused") private volatile int lock;
    private int queueSize;
    private long lastSweep = Long.MAX_VALUE;
    private final OldObjectEffects effects = new DefaultEffects();
    private OldObjectSampler sampler;
    private OldObjectEventEmitter eventEmitter;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrOldObjectProfiler() {
    }

    public void configure(int oldObjectQueueSize) {
        this.queueSize = oldObjectQueueSize;
    }

    public void initialize() {
        if (Logger.shouldLog(LogTag.JFR, LogLevel.DEBUG)) {
            Logger.log(LogTag.JFR, LogLevel.DEBUG, "Initialize old object sampler: old-object-queue-size=" + queueSize);
        }
        final OldObjectList list = new OldObjectList();
        this.sampler = new OldObjectSampler(queueSize, list, effects);
        this.eventEmitter = new OldObjectEventEmitter(list, effects);
    }

    @Uninterruptible(reason = "Accesses allocation profiler.")
    public void sample(WeakReference<Object> ref, long allocatedSize, int arrayLength) {
        final boolean success = JavaSpinLockUtils.tryLock(this, LOCK_OFFSET);
        if (!success) {
            return;
        }

        try {
            sampler.sample(ref, allocatedSize, arrayLength);
        } finally {
            JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
        }
    }

    @Uninterruptible(reason = "Accesses allocation profiler.")
    public void emit(long cutoff, boolean emitAll) {
        JavaSpinLockUtils.lockNoTransition(this, LOCK_OFFSET);

        try {
            eventEmitter.emit(cutoff, emitAll ? Long.MAX_VALUE : lastSweep);
        } finally {
            JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
        }
    }

    public void setLastSweep(long lastSweep) {
        this.lastSweep = lastSweep;
    }

    private final static class DefaultEffects implements OldObjectEffects {
        @Override
        @Uninterruptible(reason = "Accesses allocation profiler.")
        public long elapsedTicks() {
            return JfrTicks.elapsedTicks();
        }

        @Override
        @Uninterruptible(reason = "Accesses allocation profiler.")
        public boolean isAlive(WeakReference<?> ref) {
            return ref.get() != null;
        }

        @Override
        @Uninterruptible(reason = "Accesses allocation profiler.")
        public void emit(Object aliveObject, long timestamp, long allocationTime, long threadId, long stackTraceId, long heapUsedAtLastGC, int arrayLength) {
            final long objectId = SubstrateJVM.getJfrOldObjectRepository().serializeOldObject(aliveObject);
            OldObjectSampleEvent.emit(timestamp, objectId, allocationTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength);
        }
    }
}
