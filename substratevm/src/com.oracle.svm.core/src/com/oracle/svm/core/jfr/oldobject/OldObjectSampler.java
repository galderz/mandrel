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
import com.oracle.svm.core.thread.JavaThreads;

import java.lang.ref.WeakReference;

final class OldObjectSampler {
    private final OldObjectArray samples;
    private final OldObjectPriorityQueue queue;
    private final OldObjectList list;
    private final OldObjectEffects effects;
    private long totalAllocated;

    OldObjectSampler(int queueSize, OldObjectList list, OldObjectEffects effects) {
        this.samples = new OldObjectArray(queueSize);
        this.queue = new OldObjectPriorityQueue(this.samples);
        this.list = list;
        this.effects = effects;
    }

    @Uninterruptible(reason = "Accesses allocation profiler.")
    void sample(WeakReference<Object> ref, long allocatedSize, int arrayLength) {
        totalAllocated += allocatedSize;
        long span = totalAllocated - queue.total();
        if (queue.isFull()) {
            if (queue.peek().span > span) {
                // Sample will not fit, try to scavenge
                // HotSpot code scavenges when it gets notified that a sample was GC'd,
                // but such mechanism doesn't exist in substratevm.
                // So, instead just attempt to scavenge when the queue is full.
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
            OldObject next = current.previous;
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
        final long threadId = JavaThreads.getThreadId(thread);
        return queue.push(ref, allocatedSize, allocatedTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength);
    }

}
