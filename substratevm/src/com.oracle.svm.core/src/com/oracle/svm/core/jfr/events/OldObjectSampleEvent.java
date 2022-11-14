package com.oracle.svm.core.jfr.events;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.SubstrateJVM;
import org.graalvm.nativeimage.StackValue;

public class OldObjectSampleEvent {
    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static void emit(long timestamp, long objectId, long allocationTime, long threadId, long stackTraceId) {
        SubstrateJVM svm = SubstrateJVM.get();
        if (SubstrateJVM.isRecording() && svm.isEnabled(JfrEvent.OldObjectSample)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.OldObjectSample);
            JfrNativeEventWriter.putLong(data, timestamp); // start time
            JfrNativeEventWriter.putLong(data, 0); // duration
            JfrNativeEventWriter.putLong(data, threadId); // thread id
            JfrNativeEventWriter.putLong(data, stackTraceId); // stack trace id
            JfrNativeEventWriter.putLong(data, allocationTime); // allocation time
            JfrNativeEventWriter.putLong(data,timestamp - allocationTime); // object age
            JfrNativeEventWriter.putLong(data, 0); // todo last known heap usage (cache ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() when gc completes?)
            JfrNativeEventWriter.putLong(data, objectId);
            JfrNativeEventWriter.putInt(data, Integer.MIN_VALUE); // todo arrays
            JfrNativeEventWriter.putLong(data,0); // todo gc roots
            JfrNativeEventWriter.endSmallEvent(data);
        }
    }
}
