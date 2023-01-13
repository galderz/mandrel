package com.oracle.svm.core.jfr;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

final class JfrOldObjectSamplePriorityQueue
{
    private final JfrOldObjectSample[] items;
    public int count;
    private long total;

    @Platforms(Platform.HOSTED_ONLY.class)
    JfrOldObjectSamplePriorityQueue(int size)
    {
        this.items = new JfrOldObjectSample[size];
    }

    /**
     * Inserts the specified Sample into this queue.
     *
     * This method does not check if the queue has enough capacity.
     * It's up to the caller decide how to deal with a full queue.
     */
    @Uninterruptible(reason = "Accesses allocation sampler.")
    void push(Pointer pointer, Class<?> clazz, long span, long allocationTime, long threadId, long stackTraceId, long usedAtLastGC)
    {
        final JfrOldObjectSample valueOnStack = StackValue.get(JfrOldObjectSample.class);
        valueOnStack.setOldObject(pointer);
        valueOnStack.setOldObjectClass(clazz);
        valueOnStack.setSpan(span);
        valueOnStack.setAllocationTime(allocationTime);
        valueOnStack.setThreadId(threadId);
        valueOnStack.setStackTraceId(stackTraceId);
        valueOnStack.setUsedAtGC(usedAtLastGC);
        items[count] = copyToHeap(valueOnStack);

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

        final JfrOldObjectSample head = items[0];
        swap(0, count - 1);
        count--;
        items[count] = WordFactory.nullPointer();
        moveDown(0);
        total -= head.getSpan();
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    boolean isFull()
    {
        return count == items.length;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    long peekSpan()
    {
        return count == 0 ? -1 : items[0].getSpan();
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void moveDown(int i)
    {
        do
        {
            int j = -1;
            int r = right(i);
            if (r < count && items[r].getSpan() < items[i].getSpan())
            {
                int l = left(i);
                if (items[l].getSpan() < items[r].getSpan())
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
                if (l < count && items[l].getSpan() < items[i].getSpan())
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
        while (i > 0 && items[i].getSpan() < items[parent].getSpan())
        {
            swap(i, parent);
            i = parent;
            parent = parent(i);
        }
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private void swap(int i, int j)
    {
        final JfrOldObjectSample tmp = items[i];
        items[i] = items[j];
        items[j] = tmp;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private static int parent(int i)
    {
        return (i - 1) / 2;
    }

    JfrOldObjectSample[] getArray()
    {
        return items;
    }

    // todo copied from AbstractUninterruptibleHashtable
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private JfrOldObjectSample copyToHeap(JfrOldObjectSample valueOnStack) {
        final UnsignedWord sizeToAlloc = SizeOf.unsigned(JfrOldObjectSample.class);
        JfrOldObjectSample pointerOnHeap = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(sizeToAlloc);
        if (pointerOnHeap.isNonNull()) {
            UnmanagedMemoryUtil.copy((Pointer) valueOnStack, (Pointer) pointerOnHeap, sizeToAlloc);
            return pointerOnHeap;
        }
        return WordFactory.nullPointer();
    }
}
