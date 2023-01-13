package com.oracle.svm.core.genscavenge;

import com.oracle.svm.core.Uninterruptible;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

final class JfrOldObjectSampleEvents {
    @Uninterruptible(reason = "Accesses allocation sampler.")
    static void sampleOldObject(Object obj, UnsignedWord size) {
         if (hasJfrSupport()) {
             jfrSupport().sampleOldObject(obj, size);
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
