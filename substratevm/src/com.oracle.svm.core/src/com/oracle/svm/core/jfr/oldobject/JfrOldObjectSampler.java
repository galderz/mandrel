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
    private OldObjectList list;
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
        this.list = new OldObjectList();
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
    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void evict() {
        final OldObject head = queue.poll();
        list.remove(head);
        head.clear();
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void store(WeakReference<?> ref, long allocatedSize, long allocatedTime, int arrayLength) {
        final OldObject sample = queuePush(ref, allocatedSize, allocatedTime, arrayLength);
        list.prepend(sample);
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private OldObject queuePush(WeakReference<?> ref, long allocatedSize, long allocatedTime, int arrayLength) {
        final Thread thread = Thread.currentThread();
        final long heapUsedAtLastGC = Heap.getHeap().getUsedAtLastGC();

        // Note: thread can be null during shutdown, don't remove thread null check
        if (thread == null) {
            return queue.push(ref, allocatedSize, allocatedTime, 0L, 0L, heapUsedAtLastGC, arrayLength);
        }

        final long stackTraceId = SubstrateJVM.get().getStackTraceId(JfrEvent.OldObjectSample, 0);
        final long threadId = JavaThreads.getThreadId(thread);
        return queue.push(ref, allocatedSize, allocatedTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength);
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    public void emit(long cutoff, boolean emitAll) {
        JavaSpinLockUtils.lockNoTransition(this, LOCK_OFFSET);

        try {
            if (cutoff <= 0) {
                // No reference chains
                OldObjectEventEmitter.emitUnchained(list, emitAll ? Long.MAX_VALUE : lastSweep);
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
