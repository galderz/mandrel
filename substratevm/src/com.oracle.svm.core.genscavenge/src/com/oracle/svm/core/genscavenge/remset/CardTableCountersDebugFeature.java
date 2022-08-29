package com.oracle.svm.core.genscavenge.remset;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.log.Log;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

@AutomaticFeature
public class CardTableCountersDebugFeature implements Feature {
    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        System.out.println("[CardTableCountersDebugFeature] after compilation: " + System.identityHashCode(ImageSingletons.lookup(CardTableCounters.class).classWriteCounters));
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess access) {
        System.out.println("[CardTableCountersDebugFeature] after heap layout " + System.identityHashCode(ImageSingletons.lookup(CardTableCounters.class).classWriteCounters));
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        System.out.println("[CardTableCountersDebugFeature] before image write " + System.identityHashCode(ImageSingletons.lookup(CardTableCounters.class).classWriteCounters));
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        System.out.println("[CardTableCountersDebugFeature] after image write " + System.identityHashCode(ImageSingletons.lookup(CardTableCounters.class).classWriteCounters));
    }
}
