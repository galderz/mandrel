package com.oracle.svm.test.jfr.oldobject;

import com.oracle.svm.core.jfr.oldobject.OldObject;
import com.oracle.svm.core.jfr.oldobject.OldObjectList;
import com.oracle.svm.core.jfr.oldobject.OldObjectPriorityQueue;
import org.junit.Assert;
import org.junit.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class TestOldObjectList {
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
}
