package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

final class OldObjectArray {
    private final OldObject[] samples;

    // @Platforms(Platform.HOSTED_ONLY.class)
    OldObjectArray(int capacity) {
        this.samples = new OldObject[capacity];
        for (int i = 0; i < this.samples.length; i++) {
            this.samples[i] = new OldObject();
        }
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    int getCapacity() {
        return samples.length;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    void swap(int i, int j) {
        final OldObject tmp = samples[i];
        samples[i] = samples[j];
        samples[j] = tmp;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    int getIndexOf(OldObject sample) {
        for (int i = 0; i < samples.length; i++) {
            if (sample == samples[i]) {
                return i;
            }
        }

        return -1;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    OldObject getSample(int index) {
        return samples[index];
    }
}
