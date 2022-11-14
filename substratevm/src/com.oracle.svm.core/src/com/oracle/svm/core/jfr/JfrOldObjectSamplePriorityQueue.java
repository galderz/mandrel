package com.oracle.svm.core.jfr;

import com.oracle.svm.core.Uninterruptible;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

final class JfrOldObjectSamplePriorityQueue
{
    private static final int OBJECT_INDEX = 0;
    private static final int SPAN_INDEX = 1;
    private static final int ALLOCATION_TIME_INDEX = 2;
    private static final int PREV_INDEX = 3;

    private final Object[][] items;
    private final SampleList list;
    public int count;
    private long total;

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrOldObjectSamplePriorityQueue(int size)
    {
        this.items = new Object[size][];
        for (int i = 0; i < this.items.length; i++)
        {
            this.items[i] = new Object[4];
        }
        list = new SampleList();
    }

    /**
     * Inserts the specified Sample into this queue.
     *
     * This method does not check if the queue has enough capacity.
     * It's up to the caller decide how to deal with a full queue.
     */
    @Uninterruptible(reason = "Accesses allocation sampler.")
    void push(Object obj, long span, long allocationTime)
    {
        set(obj, span, allocationTime, items[count]);
        list.prepend(items[count]);
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
        items[count] = null;
        moveDown(0);
        total -= span(head);
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
        // items[i].index = i;
        // items[j].index = j;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private static int parent(int i)
    {
        return (i - 1) / 2;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private static void set(Object obj, long span, long allocationTime, Object[] sample)
    {
        sample[OBJECT_INDEX] = obj;
        sample[SPAN_INDEX] = span;
        sample[ALLOCATION_TIME_INDEX] = allocationTime;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    static Long span(Object[] sample)
    {
        return (Long) sample[SPAN_INDEX];
    }

    SampleList asList()
    {
        return list;
    }

    final class SampleList
    {
        Object[] head;
        Object[] tail;

        @Platforms(Platform.HOSTED_ONLY.class)
        private SampleList() {
        }

        @Uninterruptible(reason = "Accesses allocation sampler.")
        private void prepend(Object[] sample)
        {
            if (head == null)
            {
                head = sample;
                tail = sample;
                return;
            }

            Object[] tmp = head;
            head = sample;
            tmp[PREV_INDEX] = sample;

        }

        int firstIndex()
        {
            // Iterate to locate index of tail.
            // Avoids the need the keep index in sample.
            for (int i = 0; i < items.length; i++)
            {
                if (tail == items[i]) {
                    System.out.println("First index: " + i);
                    return i;
                }
            }

            return -1;
        }

        int prevIndex(int index)
        {
            final Object[] entry = items[index];
            if (entry == null) {
                return -1;
            }

            final Object prev = entry[PREV_INDEX];
            // Iterate to locate index of prev.
            // Avoids the need the keep index in sample.
            for (int i = 0; i < items.length; i++)
            {
                if (prev == items[i])
                    return i;
            }

            return -1;
        }

        long allocationTimeAt(int index)
        {
            final Object[] entry = items[index];
            return entry == null ? -1 : (long) entry[ALLOCATION_TIME_INDEX];
        }

        Object objectAt(int index) {
            return items[index][OBJECT_INDEX];
        }
    }
}
