package com.oracle.svm.core.util;

import java.util.AbstractQueue;
import java.util.Comparator;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * Bounded {@link PriorityQueue}.
 * 
 * @param <E>
 */
public class BoundedPriorityQueue<E> extends AbstractQueue<E> {
    private final int maxSize;
    private final Comparator<? super E> comparator;
    private final Queue<E> queue;

    public BoundedPriorityQueue(int maxSize, Comparator<? super E> comparator) {
        this.maxSize = maxSize;
        this.comparator = comparator;
        this.queue = new PriorityQueue<>(16, this.comparator);
    }

    @Override
    public Iterator<E> iterator() {
        return queue.iterator();
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public boolean offer(E e) {
        if (queue.size() >= maxSize) {
            final E head = queue.peek();
            if (comparator.compare(e, head) < 1) {
                return false;
            }
            // Offered element has a higher priority,
            // vacate from the lowest priority one and insert the element.
            poll();
        }
        return queue.offer(e);
    }

    @Override
    public E poll() {
        return queue.poll();
    }

    @Override
    public E peek() {
        return queue.peek();
    }
}
