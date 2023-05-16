package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

final class OldObjectPriorityQueue {
    private final OldObjectArray samples;
    private int count;
    private long total;

    @Platforms(Platform.HOSTED_ONLY.class)
    OldObjectPriorityQueue(OldObjectArray samples) {
        this.samples = samples;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    boolean isFull() {
        return count == samples.getCapacity();
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    int getCount() {
        return count;
    }

    /**
     * Inserts the specified Sample into this queue.
     * <p>
     * This method does not check if the queue has enough capacity.
     * It's up to the caller decide how to deal with a full queue.
     */
    @Uninterruptible(reason = "Accesses allocation sampler.")
    void push(OldObject sample) {
        count++;
        moveUp(count - 1);
        total += sample.span;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    OldObject peek() {
        return count == 0 ? OldObject.EMPTY : samples.getSample(0);
    }

    /**
     * Removes and return the head of the queue.
     * The head of the queue is the sample with the smallest span.
     */
    @Uninterruptible(reason = "Accesses allocation sampler.")
    OldObject poll() {
        if (count == 0) {
            return OldObject.EMPTY;
        }

        final OldObject head = peek();
        samples.swap(0, count - 1);
        count--;
        moveDown(0);
        total -= head.span;
        return head;
    }

    /**
     * Removes a sample from the queue.
     * It moves the sample all the way to the top to become the head,
     * then it polls it to remove it.
     */
    @Uninterruptible(reason = "Accesses allocation sampler.")
    void remove(OldObject sample) {
        final long span = sample.span;
        sample.span = 0L;
        moveUp(samples.getIndexOf(sample));
        sample.span = span;
        poll();
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void moveUp(int i) {
        int parent = parent(i);
        while (i > 0 && getSpanAt(i) < getSpanAt(parent)) {
            samples.swap(i, parent);
            i = parent;
            parent = parent(i);
        }
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private long getSpanAt(int index) {
        return samples.getSample(index).span;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private static int parent(int i) {
        return (i - 1) / 2;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void moveDown(int i) {
        do {
            int j = -1;
            int r = right(i);
            if (r < count && getSpanAt(r) < getSpanAt(i)) {
                int l = left(i);
                if (getSpanAt(l) < getSpanAt(r)) {
                    j = l;
                } else {
                    j = r;
                }
            } else {
                int l = left(i);
                if (l < count && getSpanAt(l) < getSpanAt(i)) {
                    j = l;
                }
            }

            if (j >= 0) {
                samples.swap(i, j);
            }
            i = j;
        } while (i >= 0);
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private static int left(int i) {
        return 2 * i + 1;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private static int right(int i) {
        return 2 * i + 2;
    }
}
