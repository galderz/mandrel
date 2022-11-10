package com.oracle.svm.core.graal.snippets;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;

final class JfrOldObjectSampleEvents {
    static void sampleOldObject(Object result, long size) {
        // todo temporarily removed check
        // if (hasJfrSupport()) {
        //     jfrSupport().sampleOldObject(result, size);
        // }

        jfrSupport().sampleOldObject(result, size);
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
