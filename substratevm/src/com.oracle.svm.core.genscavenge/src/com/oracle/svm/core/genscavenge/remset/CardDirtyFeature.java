package com.oracle.svm.core.genscavenge.remset;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.jfr.traceid.JfrTraceIdMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

@AutomaticFeature
public class CardDirtyFeature implements Feature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(CardDirtyMap.class, new CardDirtyMap());
    }
}
