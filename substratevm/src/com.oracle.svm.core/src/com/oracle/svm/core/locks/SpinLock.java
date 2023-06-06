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

package com.oracle.svm.core.locks;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.util.VMError;

/**
 * A non-reentrant spin lock. The main difference when compared with {@link VMMutex} is that it
 * provides a tryLock() method. This is useful for situations when trying to skip an action if a
 * lock cannot be obtained. A good example of this is the sampling logic in the old object sample
 * JFR event.
 */
public final class SpinLock {
    private static final int NOT_HELD = -1;

    private final UninterruptibleUtils.AtomicLong lock = new UninterruptibleUtils.AtomicLong(NOT_HELD);

    /**
     * Acquires the lock if it's not held by another thread. Otherwise, if the lock cannot be
     * acquired it returns right away. This lock is non-reentrant so if the lock is already held by
     * the current thread, the method returns false.
     *
     * @return true if the lock was acquired, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean tryLock() {
        final Thread thread = Thread.currentThread();
        // Note: thread can be null during shutdown, don't remove thread null check
        if (thread == null) {
            return false;
        }

        final long threadId = JavaThreads.getThreadId(thread);
        if (!lock.compareAndSet(NOT_HELD, threadId)) {
            if (threadId == lock.get()) {
                return false; // this lock is not reentrant
            }

            return false;
        }

        return true;
    }

    /**
     * Attempts to acquire a lock. If the lock cannot be acquired, it spins until it can be
     * obtained. This lock is non-reentrant so if the lock is already held by the current thread,
     * the method throws a {@link RuntimeException}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void lock() {
        final long threadId = JavaThreads.getThreadId(Thread.currentThread());
        while (!lock.compareAndSet(NOT_HELD, threadId)) {
            if (threadId == lock.get()) {
                throw VMError.shouldNotReachHere("This lock is not reentrant");
            }
        }
    }

    /**
     * Attempts to release this lock. If unlock() is called without having called lock(), a
     * {@link RuntimeException} will be raised.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void unlock() {
        final long threadId = JavaThreads.getThreadId(Thread.currentThread());
        if (!lock.compareAndSet(threadId, NOT_HELD)) {
            throw VMError.shouldNotReachHere("Unlock called without holding the lock");
        }
    }
}
