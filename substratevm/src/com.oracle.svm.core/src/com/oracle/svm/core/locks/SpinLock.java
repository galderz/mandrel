package com.oracle.svm.core.locks;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.util.VMError;

/**
 * A non-reentrant spin lock.
 * The main difference when compared with {@link VMMutex} is that it provides a tryLock() method.
 * This is useful for situations when trying to skip an action if a lock cannot be obtained.
 * A good example of this is the sampling logic in the old object sample JFR event.
 */
public class SpinLock {
    private static final int NOT_HELD = -1;

    private final UninterruptibleUtils.AtomicLong lock = new UninterruptibleUtils.AtomicLong(NOT_HELD);

    /**
     * Acquires the lock if it's not held by another thread.
     * Otherwise, if the lock cannot be acquired it returns right away.
     * This lock is non-reentrant so if the lock is already held by the current thread,
     * the method throws a {@link RuntimeException}.
     *
     * @return true if the lock was acquired, false otherwise.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean tryLock() {
        final Thread thread = Thread.currentThread();
        // Note: thread can be null during shutdown, don't remove thread null check
        if (thread == null) {
            return false;
        }

        final long threadId = thread.getId();
        if (!lock.compareAndSet(NOT_HELD, threadId)) {
            if (threadId == lock.get()) {
                throw VMError.shouldNotReachHere("This lock is not reentrant");
            }

            return false;
        }

        return true;
    }

    /**
     * Attempts to acquire a lock.
     * If the lock cannot be acquired, it spins until it can be obtained.
     * This lock is non-reentrant so if the lock is already held by the current thread,
     * the method throws a {@link RuntimeException}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void lock() {
        final long threadId = Thread.currentThread().getId();
        while (!lock.compareAndSet(NOT_HELD, threadId)) {
            if (threadId == lock.get()) {
                throw VMError.shouldNotReachHere("This lock is not reentrant");
            }
        }
    }

    /**
     * Attempts to release this lock.
     * If unlock() is called without having called lock(),
     * a {@link RuntimeException} will be raised.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public void unlock() {
        final long threadId = Thread.currentThread().getId();
        if (!lock.compareAndSet(threadId, NOT_HELD)) {
            throw VMError.shouldNotReachHere("Unlock called without holding the lock");
        }
    }
}
