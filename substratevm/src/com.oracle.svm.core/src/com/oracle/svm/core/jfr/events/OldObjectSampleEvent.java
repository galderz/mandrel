package com.oracle.svm.core.jfr.events;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.SubstrateJVM;
import org.graalvm.nativeimage.StackValue;

public class OldObjectSampleEvent {
    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static void emit(long startTicks, long allocationTime, long objectId) {
        SubstrateJVM svm = SubstrateJVM.get();
        if (SubstrateJVM.isRecording() && svm.isEnabled(JfrEvent.OldObjectSample)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.OldObjectSample);
            JfrNativeEventWriter.putLong(data, startTicks);
            JfrNativeEventWriter.putLong(data, 0);
            JfrNativeEventWriter.putEventThread(data);
            JfrNativeEventWriter.putLong(data, 0); // todo stack trace enabled
            JfrNativeEventWriter.putLong(data, allocationTime);
            JfrNativeEventWriter.putLong(data,startTicks - allocationTime);
            JfrNativeEventWriter.putLong(data, 0); // todo last known heap usage (cache ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() when gc completes?)
            JfrNativeEventWriter.putLong(data, objectId);
            JfrNativeEventWriter.putInt(data, Integer.MIN_VALUE); // todo arrays
            JfrNativeEventWriter.putLong(data,0); // todo gc roots
        }
    }
}
