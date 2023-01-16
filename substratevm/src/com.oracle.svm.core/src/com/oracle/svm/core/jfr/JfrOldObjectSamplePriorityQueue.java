package com.oracle.svm.core.jfr;

import com.oracle.svm.core.Uninterruptible;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.lang.ref.WeakReference;

final class JfrOldObjectSamplePriorityQueue
{
    private static final int REF_INDEX = 0;
    private static final int SPAN_INDEX = 1;
    private static final int ALLOCATION_TIME_INDEX = 2;
    private static final int THREAD_ID_INDEX = 3;
    private static final int STACKTRACE_ID_INDEX = 4;
    private static final int USED_AT_GC_INDEX = 5;

    private final Object[][] items;
    public int count;
    private long total;

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrOldObjectSamplePriorityQueue(int size)
    {
        this.items = new Object[size][];
        for (int i = 0; i < this.items.length; i++)
        {
            this.items[i] = new Object[USED_AT_GC_INDEX + 1];
        }
    }

    /**
     * Inserts the specified Sample into this queue.
     *
     * This method does not check if the queue has enough capacity.
     * It's up to the caller decide how to deal with a full queue.
     */
    @Uninterruptible(reason = "Accesses allocation sampler.")
    void push(WeakReference<?> obj, long span, long allocationTime, long threadId, long stackTraceId, long usedAtLastGC)
    {
        set(obj, span, allocationTime, threadId, stackTraceId, usedAtLastGC, items[count]);
        count++;
        moveUp(count - 1);
        total += span;
    }

    /**
     * Removes the head of the queue.
     * The head of the queue is the sample with the smallest span.
     */
    @Uninterruptible(reason = "Accesses allocation sampler.")
    void poll()
    {
        if (count == 0)
        {
            return;
        }

        final Object[] head = items[0];
        swap(0, count - 1);
        count--;
        clearItem(items[count]);
        moveDown(0);
        total -= span(head);
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void clearItem(Object[] item)
    {
        set(null, 0, 0, 0, 0, 0, item);
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    boolean isFull()
    {
        return count == items.length;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    long peekSpan()
    {
        return count == 0 ? -1 : span(items[0]);
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void moveDown(int i)
    {
        do
        {
            int j = -1;
            int r = right(i);
            if (r < count && span(items[r]) < span(items[i]))
            {
                int l = left(i);
                if (span(items[l]) < span(items[r]))
                {
                    j = l;
                }
                else
                {
                    j = r;
                }
            }
            else
            {
                int l = left(i);
                if (l < count && span(items[l]) < span(items[i]))
                {
                    j = l;
                }
            }

            if (j >= 0)
            {
                swap(i, j);
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

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void moveUp(int i)
    {
        int parent = parent(i);
        while (i > 0 && span(items[i]) < span(items[parent]))
        {
            swap(i, parent);
            i = parent;
            parent = parent(i);
        }
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void swap(int i, int j)
    {
        final Object[] tmp = items[i];
        items[i] = items[j];
        items[j] = tmp;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private static int parent(int i)
    {
        return (i - 1) / 2;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private static void set(WeakReference<?> obj, long span, long allocationTime, long threadId, long stackTraceId, long usedAtLastGC, Object[] sample)
    {
        sample[REF_INDEX] = obj;
        sample[SPAN_INDEX] = span;
        sample[ALLOCATION_TIME_INDEX] = allocationTime;
        sample[THREAD_ID_INDEX] = threadId;
        sample[STACKTRACE_ID_INDEX] = stackTraceId;
        sample[USED_AT_GC_INDEX] = usedAtLastGC;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    Object[][] getArray() {
        return items;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    WeakReference<?> reference(Object[] sample) {
        return (WeakReference<?>) sample[REF_INDEX];
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    static long span(Object[] sample)
    {
        return longAt(SPAN_INDEX, sample);
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    long allocationTime(Object[] sample)
    {
        return longAt(ALLOCATION_TIME_INDEX, sample);
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    long threadId(Object[] sample) {
        return longAt(THREAD_ID_INDEX, sample);
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    long stackTraceId(Object[] sample) {
        return longAt(STACKTRACE_ID_INDEX, sample);
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    long usedAtLastGC(Object[] sample) {
        return longAt(USED_AT_GC_INDEX, sample);
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private static long longAt(int fieldIndex, Object[] sample) {
        return sample == null ? -1 : (long) sample[fieldIndex];
    }
}
