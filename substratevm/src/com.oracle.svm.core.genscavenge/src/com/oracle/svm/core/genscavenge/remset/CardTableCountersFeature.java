package com.oracle.svm.core.genscavenge.remset;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.jdk.RuntimeSupport;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

@AutomaticFeature
@SuppressWarnings("unused")
class CardTableCountersFeature implements Feature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(CardTableCounters.class, new CardTableCounters());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeSupport runtime = RuntimeSupport.getRuntimeSupport();
        CardTableCounters counters = CardTableCounters.get();
        runtime.addStartupHook(counters.startupHook());
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        CardTableCounters counters = CardTableCounters.get();
        counters.setTypeCount(ImageSingletons.lookup(DynamicHubSupport.class).getMaxTypeId());
    }
}
