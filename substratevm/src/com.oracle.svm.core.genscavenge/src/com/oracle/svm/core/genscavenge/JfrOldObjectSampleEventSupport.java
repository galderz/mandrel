package com.oracle.svm.core.genscavenge;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.SubstrateJVM;
import org.graalvm.nativeimage.ImageSingletons;

import java.lang.ref.WeakReference;

final class JfrOldObjectSampleEventSupport {
    @Uninterruptible(reason = "Accesses allocation sampler.")
    public void sample(WeakReference<Object> result, long allocatedSize, int arrayLength) {
        if (SubstrateJVM.isRecording()) {
            SubstrateJVM.getJfrOldObjectSampler().sample(result, allocatedSize, arrayLength);
        }
    }
}

@AutomaticallyRegisteredFeature
class JfrOldObjectSampleEventFeature implements InternalFeature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        System.out.println("JfrOldObjectSampleEventFeature.beforeAnalysis :: enter");
        if (HasJfrSupport.get()) {
            System.out.println("JfrOldObjectSampleEventFeature.beforeAnalysis :: has jfr support");
            ImageSingletons.add(JfrOldObjectSampleEventSupport.class, new JfrOldObjectSampleEventSupport());
        }
    }

//    @Override
//    public void beforeAnalysis(BeforeAnalysisAccess access) {
//        System.out.println("JfrOldObjectSampleEventFeature.beforeAnalysis :: enter");
//        if (HasJfrSupport.get()) {
//            System.out.println("JfrOldObjectSampleEventFeature.beforeAnalysis :: has jfr support");
//            ImageSingletons.add(JfrOldObjectSampleEventSupport.class, new JfrOldObjectSampleEventSupport());
//        }
//    }
}
