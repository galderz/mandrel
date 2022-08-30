package com.oracle.svm.core.genscavenge.remset;

import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

public class CardTableCounters {
    long[] typeWriteCounters;

    @Platforms(Platform.HOSTED_ONLY.class)
    public CardTableCounters() {
        this.typeWriteCounters = new long[0];
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void initialize(int classCount) {
        typeWriteCounters = new long[classCount];
    }

    @Fold
    public static CardTableCounters get() {
        return ImageSingletons.lookup(CardTableCounters.class);
    }

    public RuntimeSupport.Hook startupHook() {
        return isFirstIsolate -> {
            Log log = Log.log();
            log.string("Initialize card table counters").newline();
            log.string("Size of type write counters array is ").unsigned(typeWriteCounters.length).newline();
        };
    }

    void incrementClassWrite(int typeID) {
        if (typeID >= 0) {
            // throw new RuntimeException(String.valueOf(System.identityHashCode(classWriteCounters)));
            typeWriteCounters[typeID] += 1;
        }
    }

    public void log(Log log) {
        // System.out.printf("[%d] Called CardTableCounters.log()%n", System.identityHashCode(this));
        log.string("[");
        log.signed(System.identityHashCode(this));
        log.string("] ");
        log.string("Called CardTableCounters.log()");
        log.newline();

//        log.string("[");
//        log.signed(System.identityHashCode(this));
//        log.string("] ");
//        log.string("Query class write counter array ");
//        log.signed(System.identityHashCode(typeWriteCounters));
//        log.newline();

        long total = 0;
        int maxNameLen = 30;

//        for (Counter counter : counters) {
//            total += counter.getValue();
//            maxNameLen = Math.max(counter.name.length(), maxNameLen);
//        }

        log.string("=== ");
        log.string("Card Table Class Write Counters ");
        log.string(" ===");
        log.newline();
        for (int i = 0; i < typeWriteCounters.length; i++) {
            final long counter = typeWriteCounters[i];
            log.string("  ").unsigned(i, maxNameLen, Log.RIGHT_ALIGN).string(":");
            log.unsigned(counter, 10, Log.RIGHT_ALIGN);
            log.newline();
        }
    }
}
