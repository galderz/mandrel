package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.events.OldObjectSampleEvent;

final class OldObjectEventEmitter {

    // Making callees not uninterruptible to deal with WeakReference.get()
    @Uninterruptible(reason = "Prevent JFR recording and epoch change.", calleeMustBe = false)
    static void emitUnchained(OldObjectArray samples, long lastSweep) {
        final long timestamp = JfrTicks.elapsedTicks();

        for (int i = 0; i < samples.getCapacity(); i++) {
            final OldObject sample = samples.getSample(i);
            if (sample.reference != null) {
                final Object obj = sample.reference.get();
                final long allocationTime = sample.allocationTime;
                if (isAliveAndOlderThan(lastSweep, obj, allocationTime)) {
                    final long objectId = SubstrateJVM.getJfrOldObjectRepository().serializeOldObject(obj);
                    final long threadId = sample.threadId;
                    final long stackTraceId = sample.stackTraceId;
                    final long heapUsedAtLastGC = sample.heapUsedAtLastGC;
                    final int arrayLength = sample.arrayLength;
                    OldObjectSampleEvent.emit(timestamp, objectId, allocationTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength);
                }
            }
        }
    }

    private static boolean isAliveAndOlderThan(long lastSweep, Object obj, long allocationTime) {
        return obj != null && allocationTime < lastSweep;
    }
}
