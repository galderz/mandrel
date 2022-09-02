package com.oracle.svm.core.genscavenge.remset;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.log.Log;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

public class CardTableCounters {
    long[] oldGenTypeWriteCounters;
    long[] youngGenTypeWriteCounters;
    Word[] typeAddresses;

    @Platforms(Platform.HOSTED_ONLY.class)
    public CardTableCounters() {
        // TODO do I need these initializations?
        this.oldGenTypeWriteCounters = new long[0];
        this.youngGenTypeWriteCounters = new long[0];
        this.typeAddresses = new Word[0];
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void initialize(int classCount) {
        this.oldGenTypeWriteCounters = new long[classCount];
        this.youngGenTypeWriteCounters = new long[classCount];
        this.typeAddresses = new Word[classCount];
    }

    @Fold
    public static CardTableCounters get() {
        return ImageSingletons.lookup(CardTableCounters.class);
    }

    void incrementTypeWrite(int typeID, Word typeAddress, boolean isYoungSpace) {
        if (typeID >= 0) {
            if (isYoungSpace) {
                youngGenTypeWriteCounters[typeID] += 1;
            } else {
                oldGenTypeWriteCounters[typeID] += 1;
            }

            typeAddresses[typeID] = typeAddress;
        }
    }

    public void log(Log log) {
        int maxNameLen = 0;
        for (int i = 0; i < typeAddresses.length; i++) {
            if (oldGenTypeWriteCounters[i] > 0 || youngGenTypeWriteCounters[i] > 0) {
                final String name = getTypeName(i);
                maxNameLen = Math.max(name.length(), maxNameLen);
            }
        }

        log.string("=== Young Generation Card Table Class Write Counters ===").newline();
        for (int i = 0; i < youngGenTypeWriteCounters.length; i++) {
            final long counter = youngGenTypeWriteCounters[i];
            if (counter > 0) {
                final String typeName = getTypeName(i);
                log.string("  ").string(typeName, maxNameLen, Log.RIGHT_ALIGN).string(":");
                log.unsigned(counter, 10, Log.RIGHT_ALIGN);
                log.newline();
            }
        }
        log.string("=== Old Generation Card Table Class Write Counters ===").newline();
        for (int i = 0; i < oldGenTypeWriteCounters.length; i++) {
            final long counter = oldGenTypeWriteCounters[i];
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
        for (int i = 0; i < oldGenTypeWriteCounters.length; i++) {
            oldGenTypeWriteCounters[i] = 0;
        }
    }
}
