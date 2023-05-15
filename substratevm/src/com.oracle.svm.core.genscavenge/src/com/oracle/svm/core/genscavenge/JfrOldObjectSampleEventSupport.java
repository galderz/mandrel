package com.oracle.svm.core.genscavenge;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.SubstrateJVM;
import org.graalvm.nativeimage.ImageSingletons;

import java.lang.ref.WeakReference;

final class JfrOldObjectSampleEventSupport {
    @Uninterruptible(reason = "Accesses allocation sampler.")
    public void sample(WeakReference<Object> result, long allocatedSize, int arrayLength) {
        if (JfrEvent.OldObjectSample.shouldEmit()) {
            SubstrateJVM.getJfrOldObjectSampler().sample(result, allocatedSize, arrayLength);
        }
    }
}

@AutomaticallyRegisteredFeature
class JfrOldObjectSampleEventFeature implements InternalFeature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (HasJfrSupport.get()) {
            ImageSingletons.add(JfrOldObjectSampleEventSupport.class, new JfrOldObjectSampleEventSupport());
        }
    }
}
