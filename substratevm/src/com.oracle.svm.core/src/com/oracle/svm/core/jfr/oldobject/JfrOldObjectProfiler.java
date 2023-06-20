package com.oracle.svm.core.jfr.oldobject;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

public class JfrOldObjectProfiler {
    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrOldObjectProfiler() {
    }
}
