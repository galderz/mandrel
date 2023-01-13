package com.oracle.svm.core.genscavenge;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.SubstrateJVM;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

final class JfrOldObjectSampleEventSupport {
    @Uninterruptible(reason = "Accesses allocation sampler.")
    public void sampleOldObject(Object obj, UnsignedWord size) {
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvent.OldObjectSample)) {
            SubstrateJVM.getJfrOldObjectSampler().sample(Word.objectToUntrackedPointer(obj), obj.getClass(), size.rawValue());
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
