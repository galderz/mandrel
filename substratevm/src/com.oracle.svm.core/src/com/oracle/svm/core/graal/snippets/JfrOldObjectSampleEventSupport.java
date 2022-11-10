package com.oracle.svm.core.graal.snippets;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.SubstrateJVM;
import org.graalvm.nativeimage.ImageSingletons;

final class JfrOldObjectSampleEventSupport {
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void sampleOldObject(Object result, long size) {
        // todo adding SubstrateJVM.get().isEnabled(JfrEvent.OldObjectSample) complains not being able to inline, workaround?
        if (SubstrateJVM.isRecording()) {
            SubstrateJVM.getJfrOldObjectSampler().sample(result, size);
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
