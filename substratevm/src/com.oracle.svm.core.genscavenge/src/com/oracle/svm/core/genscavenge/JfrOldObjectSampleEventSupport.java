package com.oracle.svm.core.genscavenge;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.SubstrateJVM;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.Pointer;

final class JfrOldObjectSampleEventSupport {
    @Uninterruptible(reason = "Accesses allocation sampler.")
    public void sampleOldObject(Pointer address, long size) {
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvent.OldObjectSample)) {
            SubstrateJVM.getJfrOldObjectSampler().sample(address, size);
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
