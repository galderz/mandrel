package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

final class LeakSamples {
    private final LeakSample[] samples;

    @Platforms(Platform.HOSTED_ONLY.class)
    LeakSamples(int capacity) {
        this.samples = new LeakSample[capacity];
        for (int i = 0; i < this.samples.length; i++) {
            this.samples[i] = new LeakSample();
        }
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    int getCapacity() {
        return samples.length;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    void swap(int i, int j) {
        final LeakSample tmp = samples[i];
        samples[i] = samples[j];
        samples[j] = tmp;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    int getIndexOf(LeakSample sample) {
        for (int i = 0; i < samples.length; i++) {
            if (sample == samples[i]) {
                return i;
            }
        }

        return -1;
    }

    @Uninterruptible(reason = "Accesses allocation sampler.")
    LeakSample getSample(int index) {
        return samples[index];
    }
}
