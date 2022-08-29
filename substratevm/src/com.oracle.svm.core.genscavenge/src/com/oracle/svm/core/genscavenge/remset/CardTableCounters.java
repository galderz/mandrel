package com.oracle.svm.core.genscavenge.remset;

import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.Arrays;

public class CardTableCounters {

    private long[] typeWriteCounters;
    private int typeCount;

//    @Platforms(Platform.HOSTED_ONLY.class)
//    public CardTableCounters() {
//    }

    @Fold
    public static CardTableCounters get() {
        return ImageSingletons.lookup(CardTableCounters.class);
    }

    public void setTypeCount(int typeCount) {
        this.typeCount = typeCount;
//        this.typeWriteCounters = new long[typeCount];
    }

    public void incrementTypeWrite(int typeID) {
        typeWriteCounters[0] += 1;

//        if (typeID >= 0) {
//            // typeWriteCounters[typeID] += 1;
//        }
    }

//    @Platforms(Platform.HOSTED_ONLY.class)
//    void initialize(int typeCount) {
//        // System.out.printf("[%d] Called CardTableCounters.initialize()%n", System.identityHashCode(this));
//        typeWriteCounters = new long[typeCount];
//        // System.out.printf("[%d] Set class write counters to array %d %n", System.identityHashCode(this), System.identityHashCode(classWriteCounters));
//        Arrays.fill(typeWriteCounters, 0);
//    }

    public RuntimeSupport.Hook startupHook() {
        return isFirstIsolate -> {
            Log log = Log.log();
            log.string("Initialize card table counters").newline();
            log.string("Number of types is ").unsigned(typeCount).newline();
            // typeWriteCounters = new long[typeCount];
        };
    }
}
