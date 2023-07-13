/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.events.OldObjectSampleEvent;
import com.oracle.svm.core.thread.JavaSpinLockUtils;
import com.oracle.svm.core.thread.JavaThreads;
import jdk.internal.misc.Unsafe;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.lang.ref.WeakReference;

public final class JfrOldObjectProfiler {
    public static final int DEFAULT_SAMPLER_SIZE = 256;

    // Moving locking to OldObjectProfiler class cannot easily be done.
    // For starters Unsafe needs to be accessible there,
    // but there might be other issues.
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long LOCK_OFFSET = U.objectFieldOffset(JfrOldObjectProfiler.class, "lock");
    @SuppressWarnings("unused") private volatile int lock;

    private int queueSize;
    private long lastSweep = Long.MAX_VALUE;
    private OldObjectProfiler profiler;

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
        final OldObjectEffects effects = new DefaultEffects();
        this.profiler = new OldObjectProfiler(queueSize, effects);
    }

    @Uninterruptible(reason = "Accesses allocation profiler.")
    public void sample(WeakReference<Object> ref, long allocatedSize, int arrayLength) {
        final boolean success = JavaSpinLockUtils.tryLock(this, LOCK_OFFSET);
        if (!success) {
            return;
        }

        try {
            profiler.sample(ref, allocatedSize, arrayLength);
        } finally {
            JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
        }
    }

    @Uninterruptible(reason = "Accesses allocation profiler.")
    public void emit(long cutoff, boolean emitAll) {
        JavaSpinLockUtils.lockNoTransition(this, LOCK_OFFSET);

        try {
            profiler.emit(cutoff, emitAll ? Long.MAX_VALUE : lastSweep);
        } finally {
            JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
        }
    }

    public void setLastSweep(long lastSweep) {
        this.lastSweep = lastSweep;
    }

    private static final class DefaultEffects implements OldObjectEffects {
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
        public void emit(Object aliveObject, long timestamp, long objectSize, long allocationTime, long threadId, long stackTraceId, long heapUsedAtLastGC, int arrayLength) {
            final long objectId = SubstrateJVM.getJfrOldObjectRepository().serializeOldObject(aliveObject);
            OldObjectSampleEvent.emit(timestamp, objectId, objectSize, allocationTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength);
        }

        @Override
        @Uninterruptible(reason = "Accesses allocation profiler.")
        public long getStackTraceId() {
            return SubstrateJVM.get().getStackTraceId(JfrEvent.OldObjectSample, 0);
        }

        @Override
        @Uninterruptible(reason = "Accesses allocation profiler.")
        public long getThreadId(Thread thread) {
            return JavaThreads.getThreadId(thread);
        }

        @Override
        @Uninterruptible(reason = "Accesses allocation profiler.")
        public long getHeapUsedAtLastGC() {
            return Heap.getHeap().getUsedAtLastGC();
        }
    }
}
