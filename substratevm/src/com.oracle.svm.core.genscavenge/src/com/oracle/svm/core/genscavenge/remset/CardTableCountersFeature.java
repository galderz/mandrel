package com.oracle.svm.core.genscavenge.remset;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.hub.DynamicHubSupport;
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

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        int classCount = ImageSingletons.lookup(DynamicHubSupport.class).getMaxTypeId();
        CardTableCounters.get().initialize(classCount);
    }
}
