package com.oracle.svm.core.jfr;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.struct.PinnedObjectField;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.jdk.AbstractUninterruptibleHashtable;
import com.oracle.svm.core.jdk.UninterruptibleEntry;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import java.lang.ref.WeakReference;

final class JfrOldObjectSamplePriorityQueue
{
    private static final int ADDRESSS_INDEX = 0;
    private static final int SPAN_INDEX = 1;
    private static final int ALLOCATION_TIME_INDEX = 2;
    private static final int THREAD_ID_INDEX = 3;
    private static final int STACKTRACE_ID_INDEX = 4;
    private static final int USED_AT_GC_INDEX = 5;
    private static final int PREVIOUS = 6;

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
            this.items[i] = new Object[7];
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
    void push(Pointer pointer, long span, long allocationTime, long threadId, long stackTraceId, long usedAtLastGC)
    {
        set(pointer, span, allocationTime, threadId, stackTraceId, usedAtLastGC, items[count]);
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
        list.remove(items[count]);
        clearItem(items[count]);
        moveDown(0);
        total -= span(head);
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void clearItem(Object[] item)
    {
        set(WordFactory.zero(), 0, 0, 0, 0, 0, item);
        item[PREVIOUS] = null;
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

    private static void set(Pointer pointer, long span, long allocationTime, long threadId, long stackTraceId, long usedAtLastGC, Object[] sample)
    {
        sample[ADDRESSS_INDEX] = pointer;
        sample[SPAN_INDEX] = span;
        sample[ALLOCATION_TIME_INDEX] = allocationTime;
        sample[THREAD_ID_INDEX] = threadId;
        sample[STACKTRACE_ID_INDEX] = stackTraceId;
        sample[USED_AT_GC_INDEX] = usedAtLastGC;
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

    @RawStructure
    private interface JfrOldObjectSample extends UninterruptibleEntry {
        @RawField
        Pointer getOldObject();

        @RawField
        void setOldObject(Pointer pointer);

        @RawField
        long getSpan();

        @RawField
        void setSpan(long span);

        @RawField
        long getAllocationTime();

        @RawField
        void setAllocationTime(long allocationTime);

        @RawField
        long getThreadId();

        @RawField
        void setThreadId(long threadId);

        @RawField
        long getStacktraceId();

        @RawField
        void setStacktraceId(long stacktraceId);

        @RawField
        long getUsedAtGC();

        @RawField
        void setUsedAtGC(long usedAtGC);
    }

    private static final class JfrOldObjectSampleTable extends AbstractUninterruptibleHashtable {
        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected JfrOldObjectSample[] createTable(int length) {
            return new JfrOldObjectSample[length];
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected boolean isEqual(UninterruptibleEntry a, UninterruptibleEntry b) {
            final JfrOldObjectSample entry1 = (JfrOldObjectSample) a;
            final JfrOldObjectSample entry2 = (JfrOldObjectSample) b;
            return entry1.getOldObject().equal(entry2.getOldObject());
        }

        @Override
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        protected UninterruptibleEntry copyToHeap(UninterruptibleEntry valueOnStack) {
            return copyToHeap(valueOnStack, SizeOf.unsigned(JfrOldObjectSample.class));
        }
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
            tmp[PREVIOUS] = sample;
        }

        @Uninterruptible(reason = "Accesses allocation sampler.")
        private void remove(Object[] item) {
            if (tail == item) {
                // If item is tail, update tail to be item's prev
                tail = (Object[]) item[PREVIOUS];
                return;
            }

            // Else, find an element whose previous is item; iow, find item's next element.
            // Note: Iterate to locate index of next.
            //       Avoids the need the keep index in sample.
            Object[] next = null;
            for (int i = 0; i < items.length; i++) {
                if (items[i][PREVIOUS] == item) {
                    next = items[i];
                    break;
                }
            }

            assert next != null;

            // Then set that next's previous to item's previous
            next[PREVIOUS] = item[PREVIOUS];

            // If the element removed is head, update it to item's next.
            if (head == item) {
                head = next;
            }
        }

        int firstIndex()
        {
            // Note: Iterate to locate index of tail.
            //       Avoids the need the keep index in sample.
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

            final Object prev = entry[PREVIOUS];
            // Note: Iterate to locate index of prev.
            //       Avoids the need the keep index in sample.
            for (int i = 0; i < items.length; i++)
            {
                if (prev == items[i])
                    return i;
            }

            return -1;
        }

        long allocationTimeAt(int index)
        {
            return longAt(index, ALLOCATION_TIME_INDEX);
        }

        Pointer addressAt(int index) {
            return (Pointer) items[index][ADDRESSS_INDEX];
        }

        long threadIdAt(int index) {
            return longAt(index, THREAD_ID_INDEX);
        }

        long stackTraceIdAt(int index) {
            return longAt(index, STACKTRACE_ID_INDEX);
        }


        long usedAtLastGCAt(int index) {
            return longAt(index, USED_AT_GC_INDEX);
        }

        private long longAt(int index, int fieldIndex) {
            final Object[] entry = items[index];
            return entry == null ? -1 : (long) entry[fieldIndex];
        }
    }
}
