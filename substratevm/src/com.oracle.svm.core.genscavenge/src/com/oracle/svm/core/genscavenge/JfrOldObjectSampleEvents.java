package com.oracle.svm.core.genscavenge;

import com.oracle.svm.core.jfr.JfrTicks;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;

import java.lang.ref.WeakReference;

public class JfrOldObjectSampleEvents {
    static void sample(Object result, long allocatedSize, int arrayLength) {
        if (hasJfrSupport()) {
            // Instantiate weak reference at the last possible time before allocations are not allowed
            jfrSupport().sample(new WeakReference<>(result), allocatedSize, arrayLength);
        }
    }

    static void updateLastSweep(UnsignedWord sizeBefore, UnsignedWord sizeAfter) {
        final long timestamp = JfrTicks.elapsedTicks();
        if (timestamp > 0 && hasJfrSupport()) {
            if (sizeAfter.belowThan(sizeBefore)) {
                jfrSupport().updateLastSweep(timestamp);
            }
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
