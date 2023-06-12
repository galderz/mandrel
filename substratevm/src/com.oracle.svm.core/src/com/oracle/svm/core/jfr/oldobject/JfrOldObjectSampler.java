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
import com.oracle.svm.core.thread.JavaSpinLockUtils;
import com.oracle.svm.core.thread.JavaThreads;
import jdk.internal.misc.Unsafe;
import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.lang.ref.WeakReference;

public final class JfrOldObjectSampler {
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long LOCK_OFFSET = U.objectFieldOffset(JfrOldObjectSampler.class, "lock");
    public static final int DEFAULT_SAMPLER_SIZE = 256;

    @SuppressWarnings("unused") private volatile int lock;
    private int queueSize;
    private OldObjectArray samples;
    private OldObjectPriorityQueue queue;
    private long lastSweep = Long.MAX_VALUE;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrOldObjectSampler() {
    }

    public void configure(int oldObjectQueueSize) {
        this.queueSize = oldObjectQueueSize;
    }

    public void initialize() {
        if (Logger.shouldLog(LogTag.JFR, LogLevel.DEBUG)) {
            Logger.log(LogTag.JFR, LogLevel.DEBUG, "Initialize old object sampler: old-object-queue-size=" + queueSize);
        }
        this.samples = new OldObjectArray(queueSize);
        this.queue = new OldObjectPriorityQueue(this.samples);
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    public void sample(WeakReference<Object> ref, long allocatedSize, int arrayLength) {
        final boolean success = JavaSpinLockUtils.tryLock(this, LOCK_OFFSET);
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
            JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
        }
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
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
        final int sampleIndex = samples.getIndexOf(sample);
        final int prevIndex = sampleIndex == 0 ? samples.getCapacity() - 1 : sampleIndex - 1;
        final OldObject prev = samples.getSample(prevIndex);
        if (prev.reference != null) {
            queue.remove(prev);
            prev.span += sample.span;
            queue.push(prev);
        }

        queue.remove(sample);
        sample.clear();
    }

    /**
     * Evict the sample with the smallest span from the sampler, by removing the head of the queue.
     */
    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void evict() {
        queue.poll().clear();
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void store(WeakReference<?> ref, long allocatedSize, long allocatedTime, int arrayLength) {
        final Thread thread = Thread.currentThread();
        final long heapUsedAtLastGC = Heap.getHeap().getUsedAtLastGC();

        // Note: thread can be null during shutdown, don't remove thread null check
        if (thread == null) {
            queue.push(ref, allocatedSize, allocatedTime, 0L, 0L, heapUsedAtLastGC, arrayLength);
        } else {
            final long stackTraceId = SubstrateJVM.get().getStackTraceId(JfrEvent.OldObjectSample, 0);
            final long threadId = JavaThreads.getThreadId(thread);
            queue.push(ref, allocatedSize, allocatedTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength);
        }
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    public void emit(long cutoff, boolean emitAll) {
        JavaSpinLockUtils.lockNoTransition(this, LOCK_OFFSET);

        try {
            if (cutoff <= 0) {
                // No reference chains
                OldObjectEventEmitter.emitUnchained(samples, emitAll ? Long.MAX_VALUE : lastSweep);
            }

            // todo support cutoff > 0 (path-to-gc-roots)
        } finally {
            JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
        }
    }

    public void setLastSweep(long lastSweep) {
        this.lastSweep = lastSweep;
    }
}
