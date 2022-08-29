package com.oracle.svm.core.genscavenge.remset;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownObjectField;
import com.oracle.svm.core.log.Log;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.util.Arrays;

public class CardTableCounters {
    // @UnknownObjectField(types = {long[].class}) private long[] classWriteCounters;
    long[] classWriteCounters;

    @Platforms(Platform.HOSTED_ONLY.class)
    void initialize(int classCount) {
        System.out.printf("[%d] Called CardTableCounters.initialize()%n", System.identityHashCode(this));
        classWriteCounters = new long[classCount];
        System.out.printf("[%d] Set class write counters to array %d %n", System.identityHashCode(this), System.identityHashCode(classWriteCounters));
        Arrays.fill(classWriteCounters, 0);
    }

//    @Fold
//    public static CardTableCounters singleton() {
//        return ImageSingletons.lookup(CardTableCounters.class);
//    }

    void incrementClassWrite(int typeID) {
        if (typeID >= 0) {
            // throw new RuntimeException(String.valueOf(System.identityHashCode(classWriteCounters)));
            classWriteCounters[typeID] += 1;
        }
    }

    public void log(Log log) {
        // System.out.printf("[%d] Called CardTableCounters.log()%n", System.identityHashCode(this));
        log.string("[");
        log.signed(System.identityHashCode(this));
        log.string("] ");
        log.string("Called CardTableCounters.log()");
        log.newline();

        log.string("[");
        log.signed(System.identityHashCode(this));
        log.string("] ");
        log.string("Query class write counter array ");
        log.signed(System.identityHashCode(classWriteCounters));
        log.newline();

        long total = 0;
        int maxNameLen = 30;

//        for (Counter counter : counters) {
//            total += counter.getValue();
//            maxNameLen = Math.max(counter.name.length(), maxNameLen);
//        }

//        log.string("=== ");
//        log.string("Card Table Class Write Counters ");
//        log.unsigned(classWriteCounters == null ? 0 : classWriteCounters.length);
//        log.string(" ===");
//        log.newline();
//        for (int i = 0; i < classWriteCounters.length; i++) {
//            // final long classWriteCounter = classWriteCounters[i];
//            log.string("  ").unsigned(i, maxNameLen, Log.RIGHT_ALIGN).string(":");
//            // log.unsigned(classWriteCounter, 10, Log.RIGHT_ALIGN);
//            log.newline();
//        }
    }
}
