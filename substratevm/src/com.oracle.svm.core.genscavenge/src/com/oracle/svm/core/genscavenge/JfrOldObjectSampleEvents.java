package com.oracle.svm.core.genscavenge;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;

import java.lang.ref.WeakReference;

public class JfrOldObjectSampleEvents {
    static void sample(Object result, long allocatedSize, int arrayLength) {
        if (hasJfrSupport()) {
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
