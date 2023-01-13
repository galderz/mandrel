package com.oracle.svm.core.jfr;

import com.oracle.svm.core.c.struct.PinnedObjectField;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;

@RawStructure
interface JfrOldObjectSample extends PointerBase {
    @RawField
    Pointer getOldObject();

    @RawField
    void setOldObject(Pointer pointer);

    @PinnedObjectField
    @RawField
    Class<?> getOldObjectClass();

    @PinnedObjectField
    @RawField
    void setOldObjectClass(Class<?> clazz);

    @RawField
    long getSpan();

    @RawField
    void setSpan(long span);

    @RawField
    long getAllocationTime();

    @RawField
    void setAllocationTime(long allocationTime);

    @RawField
    long getThreadId();

    @RawField
    void setThreadId(long threadId);

    @RawField
    long getStackTraceId();

    @RawField
    void setStackTraceId(long stacktraceId);

    @RawField
    long getUsedAtGC();

    @RawField
    void setUsedAtGC(long usedAtGC);
}
