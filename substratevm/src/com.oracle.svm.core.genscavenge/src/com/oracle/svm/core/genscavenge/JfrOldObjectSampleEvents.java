package com.oracle.svm.core.genscavenge;

import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.SubstrateJVM;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;

import java.lang.ref.WeakReference;

final class JfrOldObjectSampleEvents {
    static void sampleOldObject(Object result, long size) {
         if (hasJfrSupport() && SubstrateJVM.get().isEnabled(JfrEvent.OldObjectSample)) {
             // Instantiate weak reference at the last possible time before allocations are not allowed
             jfrSupport().sampleOldObject(new WeakReference<>(result), size);
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
