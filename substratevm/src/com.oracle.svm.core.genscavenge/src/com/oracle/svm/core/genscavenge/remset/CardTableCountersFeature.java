package com.oracle.svm.core.genscavenge.remset;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

@AutomaticFeature
class CardTableCountersFeature implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return true; // TODO make this optional
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(CardTableCounters.class, new CardTableCounters());
    }

//    @Override
//    public void beforeAnalysis(BeforeAnalysisAccess access) {
//        RuntimeSupport runtime = RuntimeSupport.getRuntimeSupport();
//        runtime.addStartupHook(this::initializeCardTable);
//    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeSupport runtime = RuntimeSupport.getRuntimeSupport();
        final CardTableCounters counters = CardTableCounters.get();
        runtime.addStartupHook(counters.startupHook());
    }

//    private void initializeCardTable(boolean isFirstIsolate) {
//        Log log = Log.log();
//        log.string("Initialize card table");
//    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        int classCount = ImageSingletons.lookup(DynamicHubSupport.class).getMaxTypeId();
        CardTableCounters.get().initialize(classCount, access.getTypeNames());
    }
}
