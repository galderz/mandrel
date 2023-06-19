package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

/**
 * A singly linked list view of the queue.
 * An item's previous is the item that was added to the queue after the item itself.
 * The list is iterated in FIFO order, starting with the element added first
 * and following previous links to find elements added after.
 * The traversal can also be used to discover entries that need removing,
 * e.g. if references have been garbage collected.
 */
final class OldObjectList {
    // Points to the oldest entry added to the list.
    // This would be the first FIFO iterated element.
    // Only gets updated when an entry is removed.
    OldObject head;

    // Points to the youngest entry added to the list.
    // This would be last FIFO iterated element.
    // Prepending merely updates this pointer.
    OldObject tail;

    OldObjectList() {}

    @Uninterruptible(reason = "Accesses allocation sampler.")
    OldObject head() {
        return head;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    OldObject next(OldObject current) {
        return current.previous;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    void prepend(OldObject sample) {
        if (tail == null) {
            tail = sample;
            head = sample;
            return;
        }

        OldObject tmp = tail;
        tail = sample;
        tmp.previous = sample;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    void remove(OldObject sample) {
        if (head == sample && tail == sample) {
            // Removing last remaining item, null both pointers
            head = null;
            tail = null;
            return;
        }

        if (head == sample) {
            // If item is head, update head to be item's prev
            head = sample.previous;
            return;
        }

        // Else, find an element whose previous is item; iow, find item's next element.
        OldObject next = findNext(sample);

        assert next != null;

        // Then set that next's previous to item's previous
        next.previous = sample.previous;

        // If the element removed is tail, update it to item's next.
        if (tail == sample) {
            tail = next;
        }
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    private OldObject findNext(OldObject target) {
        OldObject current = head;
        while (current != null) {
            if (current.previous == target) {
                return current;
            }

            current = current.previous;
        }

        return null;
    }
}