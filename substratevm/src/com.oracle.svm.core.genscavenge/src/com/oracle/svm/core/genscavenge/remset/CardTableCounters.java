package com.oracle.svm.core.genscavenge.remset;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

public class CardTableCounters {
    long[] typeWriteCounters;
    Word[] typeAddresses;

    @Platforms(Platform.HOSTED_ONLY.class)
    public CardTableCounters() {
        // TODO do I need these initializations?
        this.typeWriteCounters = new long[0];
        this.typeAddresses = new Word[0];
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void initialize(int classCount) {
        this.typeWriteCounters = new long[classCount];
        this.typeAddresses = new Word[classCount];
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

    void incrementTypeWrite(int typeID, Word typeAddress) {
        if (typeID >= 0) {
            // throw new RuntimeException(String.valueOf(System.identityHashCode(classWriteCounters)));
            typeWriteCounters[typeID] += 1;
            typeAddresses[typeID] = typeAddress;
        }
    }

    public void log(Log log) {
        int maxNameLen = 0;
        for (int i = 0; i < typeAddresses.length; i++) {
            final long counter = typeWriteCounters[i];
            if (counter > 0) {
                final String name = getTypeName(i);
                maxNameLen = Math.max(name.length(), maxNameLen);
            }
        }

        log.string("=== Card Table Class Write Counters ===").newline();
        for (int i = 0; i < typeWriteCounters.length; i++) {
            final long counter = typeWriteCounters[i];
            if (counter > 0) {
                final String typeName = getTypeName(i);
                log.string("  ").string(typeName, maxNameLen, Log.RIGHT_ALIGN).string(":");
                log.unsigned(counter, 10, Log.RIGHT_ALIGN);
                log.newline();
            }
        }
    }

    private String getTypeName(int index) {
        return ((DynamicHub) typeAddresses[index].toObject()).getName();
    }

    public void clearCounters() {
        for (int i = 0; i < typeWriteCounters.length; i++) {
            typeWriteCounters[i] = 0;
        }
    }
}
