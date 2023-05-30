package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.events.OldObjectSampleEvent;

final class OldObjectEventEmitter {

    // Making callees not uninterruptible to deal with WeakReference.get()
    @Uninterruptible(reason = "Prevent JFR recording and epoch change.", calleeMustBe = false)
    static void emitUnchained(OldObjectList list, long lastSweep) {
        final long timestamp = JfrTicks.elapsedTicks();

        OldObject current = list.head();
        while (current != null) {
            final Object obj = current.reference.get();
            final long allocationTime = current.allocationTime;
            if (isAliveAndOlderThan(lastSweep, obj, allocationTime)) {
                final long objectId = SubstrateJVM.getJfrOldObjectRepository().serializeOldObject(obj);
                final long threadId = current.threadId;
                final long stackTraceId = current.stackTraceId;
                final long heapUsedAtLastGC = current.heapUsedAtLastGC;
                final int arrayLength = current.arrayLength;
                OldObjectSampleEvent.emit(timestamp, objectId, allocationTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength);
            }

            current = list.next(current);
        }
    }

    private static boolean isAliveAndOlderThan(long lastSweep, Object obj, long allocationTime) {
        return obj != null && allocationTime < lastSweep;
    }
}
