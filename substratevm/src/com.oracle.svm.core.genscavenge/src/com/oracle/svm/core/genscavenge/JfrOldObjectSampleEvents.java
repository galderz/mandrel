package com.oracle.svm.core.genscavenge;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.SubstrateJVM;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;

import java.lang.ref.WeakReference;

final class JfrOldObjectSampleEvents {
    static void sample(Object result, long allocatedSize, int arrayLength) {
         if (hasJfrSupport() && SubstrateJVM.get().isEnabled(JfrEvent.OldObjectSample)) {
             // Instantiate weak reference at the last possible time before allocations are not allowed
             jfrSupport().sample(new WeakReference<>(result), allocatedSize, arrayLength);
         }
    }

    @Fold
    static boolean hasJfrSupport() {
        return ImageSingletons.contains(JfrOldObjectSampleEventSupport.class);
    }

    @Fold
    static JfrOldObjectSampleEventSupport jfrSupport() {
        return ImageSingletons.lookup(JfrOldObjectSampleEventSupport.class);
    }
}
