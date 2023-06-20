package com.oracle.svm.test.jfr.oldobject;

import com.oracle.svm.core.jfr.oldobject.OldObject;
import com.oracle.svm.core.jfr.oldobject.OldObjectList;
import com.oracle.svm.core.jfr.oldobject.OldObjectPriorityQueue;
import org.junit.Assert;
import org.junit.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class TestOldObjectList {
//    @Test
//    private static void testIterateAndRemoveAll()
//    {
//        final int size = 4;
//        final OldObjectList list = new OldObjectList();
//        final OldObjectPriorityQueue queue = new OldObjectPriorityQueue(size);
//
//        for (int i = 0; i < size; i++)
//        {
//            list.prepend(queue.push(new WeakReference<>(String.valueOf(i)), 10 + i, i, 0, 0, 0, 0));
//        }
//
//        List<String> objects = new ArrayList<>();
//        int removed = iterateAndRemove(x -> true, objects, sampler);
//
//        assert 4 == removed;
//        assert null == sampler.list.head();
//        assert objects.equals(List.of("0", "1", "2", "3")) : objects;
//    }

    @Test
    public void testPushAndIterateMany()
    {
        final int size = 256;
        final OldObjectList list = new OldObjectList();
        final OldObjectPriorityQueue queue = new OldObjectPriorityQueue(size);

        // Attempt to push a large number of entries
        for (int i = 0; i < 1_000_000; i++)
        {
            if (queue.isFull())
            {
                evict(list, queue);
            }

            list.prepend(queue.push(new WeakReference<>(new Object()), i, 0, 0, 0, 0, 0));
        }

        // Attempt to iterate over the contents of the queue
        List<OldObject> oldObjects = iterate(list);
        Assert.assertEquals(256, oldObjects.size());
    }

    /**
     * Pop youngest because that's the one with the lowest span.
     */
    @Test
    public void testIterateSizePlusOnePopYoungest()
    {
        final int size = 8;
        final OldObjectList list = new OldObjectList();
        final OldObjectPriorityQueue queue = new OldObjectPriorityQueue(size);

        for (int i = 0; i < size + 1; i++)
        {
            if (queue.isFull())
            {
                evict(list, queue);
            }

            list.prepend(queue.push(new WeakReference<>(String.valueOf(i)), i == (size - 1) ? 100 : 200, i, 0, 0, 0, 0));
        }

        List<OldObject> oldObjects = iterate(list);
        Assert.assertEquals(List.of(0L, 1L, 2L, 3L, 4L, 5L, 6L, 8L), oldObjects.stream().map(OldObject::getAllocationTime).toList());
        Assert.assertEquals(List.of("0", "1", "2", "3", "4", "5", "6", "8"), oldObjects.stream().map(obj -> obj.getReference().get()).toList());
    }

    /**
     * Pop middle because that is the one with the lowest span.
     */
    @Test
    public void testIterateSizePlusOnePopMiddle()
    {
        final int size = 8;
        final OldObjectList list = new OldObjectList();
        final OldObjectPriorityQueue queue = new OldObjectPriorityQueue(size);

        for (int i = 0; i < size + 1; i++)
        {
            if (queue.isFull())
            {
                evict(list, queue);
            }

            list.prepend(queue.push(new WeakReference<>(String.valueOf(i)), i == (size / 2) ? 100 : 200, i, 0, 0, 0, 0));
        }

        List<OldObject> oldObjects = iterate(list);
        Assert.assertEquals(List.of(0L, 1L, 2L, 3L, 5L, 6L, 7L, 8L), oldObjects.stream().map(OldObject::getAllocationTime).toList());
        Assert.assertEquals(List.of("0", "1", "2", "3", "5", "6", "7", "8"), oldObjects.stream().map(obj -> obj.getReference().get()).toList());
    }

    /**
     * Pop oldest because that's the one with the lowest span.
     */
    @Test
    public void testIterateSizePlusOnePopOldest() {
        final int size = 8;
        final OldObjectList list = new OldObjectList();
        final OldObjectPriorityQueue queue = new OldObjectPriorityQueue(size);

        for (int i = 0; i < size + 1; i++)
        {
            if (queue.isFull())
            {
                evict(list, queue);
            }

            list.prepend(queue.push(new WeakReference<>(String.valueOf(i)), i * 100, i, 0, 0, 0, 0));
        }

        List<OldObject> oldObjects = iterate(list);
        Assert.assertEquals(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L), oldObjects.stream().map(OldObject::getAllocationTime).toList());
        Assert.assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8"), oldObjects.stream().map(obj -> obj.getReference().get()).toList());
    }

    @Test
    public void testIterateSize()
    {
        final int size = 8;
        final OldObjectList list = new OldObjectList();
        final OldObjectPriorityQueue queue = new OldObjectPriorityQueue(size);

        for (int i = 0; i < size; i++)
        {
            list.prepend(queue.push(new WeakReference<>(String.valueOf(i)), i * 100, i, 0, 0, 0, 0));
        }

        List<OldObject> oldObjects = iterate(list);
        Assert.assertEquals(List.of(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L), oldObjects.stream().map(OldObject::getAllocationTime).toList());
        Assert.assertEquals(List.of("0", "1", "2", "3", "4", "5", "6", "7"), oldObjects.stream().map(obj -> obj.getReference().get()).toList());
    }

    @Test
    public void testIterateSizeMinusOne()
    {
        final int size = 8;
        final OldObjectList list = new OldObjectList();
        final OldObjectPriorityQueue queue = new OldObjectPriorityQueue(size);

        for (int i = 0; i < size - 1; i++)
        {
            list.prepend(queue.push(new WeakReference<>(String.valueOf(i)), i * 100, i, 0, 0, 0, 0));
        }

        List<OldObject> oldObjects = iterate(list);
        Assert.assertEquals(List.of(0L, 1L, 2L, 3L, 4L, 5L, 6L), oldObjects.stream().map(OldObject::getAllocationTime).toList());
        Assert.assertEquals(List.of("0", "1", "2", "3", "4", "5", "6"), oldObjects.stream().map(obj -> obj.getReference().get()).toList());
    }

    @Test
    public void testIterate()
    {
        final OldObjectList list = new OldObjectList();
        final OldObjectPriorityQueue queue = new OldObjectPriorityQueue(10);
        list.prepend(queue.push(new WeakReference<>("200"), 200, 1, 10, 1_000, 10_000, -1));
        list.prepend(queue.push(new WeakReference<>("400"), 400, 2, 20, 2_000, 20_000, -2));
        list.prepend(queue.push(new WeakReference<>("300"), 300, 3, 30, 3_000, 30_000, -3));
        list.prepend(queue.push(new WeakReference<>("500"), 500, 4, 40, 4_000, 40_000, -4));
        list.prepend(queue.push(new WeakReference<>("100"), 100, 5, 50, 5_000, 50_000, -5));

        List<OldObject> oldObjects = iterate(list);

        Assert.assertEquals(List.of(1L, 2L, 3L, 4L, 5L), oldObjects.stream().map(OldObject::getAllocationTime).toList());
        Assert.assertEquals(List.of("200", "400", "300", "500", "100"), oldObjects.stream().map(obj -> obj.getReference().get()).toList());
    }

    private static List<OldObject> iterate(OldObjectList list) {
        List<OldObject> oldObjects = new ArrayList<>();
        OldObject current = list.head();
        while (current != null)
        {
            oldObjects.add(current);
            current = list.next(current);
        }
        return oldObjects;
    }

    private static void evict(OldObjectList list, OldObjectPriorityQueue queue) {
        final OldObject head = queue.poll();
        list.remove(head);
        head.clear();
    }

//    private static int iterateAndRemove(Predicate<String> shouldRemove, List<OldObject> objects, OldObjectList list)
//    {
//        OldObject current = list.head();
//        int removed = 0;
//        while (current != null)
//        {
//            OldObject next = list.next(current);
//            final String value = (String) current.getReference().get();
//            if (shouldRemove.test(value))
//            {
//                objects.add(current);
//                sampler.remove(current);
//                removed++;
//            }
//
//            current = next;
//        }
//
//        return removed;
//    }
}
