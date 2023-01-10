package com.oracle.svm.core.genscavenge;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.Pointer;

final class JfrOldObjectSampleEvents {
    static void sampleOldObject(Pointer address, long size) {
         if (hasJfrSupport()) {
             jfrSupport().sampleOldObject(address, size);
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
