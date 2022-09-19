package com.oracle.svm.core.jfr.events;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrRecorderThread;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.StackValue;

public class OldObjectSampleEvent {

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emit0(Object obj) {
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvent.OldObjectSample)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            // TODO can large be true with old object sample? e.g. stacktrace
            JfrNativeEventWriter.beginEvent(data, JfrEvent.OldObjectSample, false);
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks()); // allocation time
            JfrNativeEventWriter.putLong(data, 0); // object age
            JfrNativeEventWriter.putLong(data, 0); // last known heap usage

            // TODO should it be tracked by object ids instead?
            JfrNativeEventWriter.putLong(data, Word.objectToUntrackedPointer(obj).rawValue()); // OldObject.address
            JfrNativeEventWriter.putClass(data, obj.getClass()); // OldObject.type
            JfrNativeEventWriter.putString(data, ""); // OldObject.description
            JfrNativeEventWriter.putLong(data, 0); // OldObject.referrer

            //    <Field type="Reference" name="referrer" label="Referrer Object" description="Object referencing this object" />
            // number of elements or -1 if not array
            // gc root trace id
        }
    }

}
