package com.oracle.svm.core.jfr;

import com.oracle.svm.core.Uninterruptible;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import static com.oracle.svm.core.jfr.JfrOldObjectSampleArray.EMPTY;
import static com.oracle.svm.core.jfr.JfrOldObjectSampleArray.getSpan;
import static com.oracle.svm.core.jfr.JfrOldObjectSampleArray.setSpan;

final class JfrOldObjectSamplePriorityQueue {
    private final JfrOldObjectSampleArray samples;
    private int count;
    private long total;

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrOldObjectSamplePriorityQueue(JfrOldObjectSampleArray samples) {
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
    void push(Object[] sample) {
        count++;
        moveUp(count - 1);
        total += getSpan(sample);
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    Object[] peek() {
        return count == 0 ? EMPTY : samples.getSample(0);
    }

    /**
     * Removes the head of the queue.
     * The head of the queue is the sample with the smallest span.
     */
    @Uninterruptible(reason = "Accesses allocation sampler.")
    void poll() {
        if (count == 0) {
            return;
        }

        final Object[] head = peek();
        samples.swap(0, count - 1);
        count--;
        moveDown(0);
        total -= getSpan(head);
    }

    /**
     * Removes a sample from the queue.
     * It moves the sample all the way to the top to become the head,
     * then it polls it to remove it.
     */
    @Uninterruptible(reason = "Accesses allocation sampler.")
    void remove(Object[] sample) {
        final long span = getSpan(sample);
        setSpan(0L, sample);
        moveUp(samples.getIndexOf(sample));
        setSpan(span, sample);
        poll();
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void moveUp(int i) {
        int parent = parent(i);
        while (i > 0 && samples.getSpan(i) < samples.getSpan(parent)) {
            samples.swap(i, parent);
            i = parent;
            parent = parent(i);
        }
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
            if (r < count && samples.getSpan(r) < samples.getSpan(i)) {
                int l = left(i);
                if (samples.getSpan(l) < samples.getSpan(r)) {
                    j = l;
                } else {
                    j = r;
                }
            } else {
                int l = left(i);
                if (l < count && samples.getSpan(l) < samples.getSpan(i)) {
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
