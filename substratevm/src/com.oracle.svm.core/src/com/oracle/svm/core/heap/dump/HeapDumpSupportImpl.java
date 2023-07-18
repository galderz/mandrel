/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.core.heap.dump;

import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.NO_ALLOCATION;
import static com.oracle.svm.core.heap.RestrictHeapAccess.Access.UNRESTRICTED;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.headers.LibC;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.impl.HeapDumpSupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.os.RawFileOperationSupport.FileCreationMode;
import com.oracle.svm.core.os.RawFileOperationSupport.RawFileDescriptor;
import com.oracle.svm.core.thread.NativeVMOperation;
import com.oracle.svm.core.thread.NativeVMOperationData;
import com.oracle.svm.core.thread.VMOperation;

public class HeapDumpSupportImpl implements HeapDumpSupport {
    private final HeapDumpWriter writer;
    private final HeapDumpOperation heapDumpOperation;
    private final HeapDumpOnErrorOperation heapDumpOnErrorOperation;
    // private HeapDumpOnErrorVMOperationData heapDumpOnErrorOperationData;
    // private String heapOnErrorDumpPath;
    private CCharPointer heapOnErrorDumpPath;

    @Platforms(Platform.HOSTED_ONLY.class)
    public HeapDumpSupportImpl(HeapDumpMetadata metadata) {
        this.writer = new HeapDumpWriter(metadata);
        this.heapDumpOperation = new HeapDumpOperation();
        this.heapDumpOnErrorOperation = new HeapDumpOnErrorOperation();
    }

    @Override
    public void dumpHeap(String filename, boolean gcBefore) throws IOException {
        RawFileDescriptor fd = getFileSupport().create(filename, FileCreationMode.CREATE_OR_REPLACE, RawFileOperationSupport.FileAccessMode.READ_WRITE);
        if (!getFileSupport().isValid(fd)) {
            throw new IOException("Could not create the heap dump file: " + filename);
        }

        try {
            writeHeapTo(fd, gcBefore);
        } finally {
            getFileSupport().close(fd);
        }
    }

    @Override
    public void dumpHeapOnOutOfMemoryError() {
        int size = SizeOf.get(HeapDumpOnErrorVMOperationData.class);
        HeapDumpOnErrorVMOperationData data = StackValue.get(size);
        UnmanagedMemoryUtil.fill((Pointer) data, WordFactory.unsigned(size), (byte) 0);

        data.setHeapDumpPath(heapOnErrorDumpPath);
        heapDumpOnErrorOperation.enqueue(data);
    }

    @Override
    public void initHeapDumpOnOutOfMemoryErrorPath() {
        System.out.println("initHeapDumpOnOutOfMemoryErrorPath");
        String dumpFileName = "svm-heapdump-" + ProcessProperties.getProcessID() + ".hprof";
        String dumpPath = SubstrateOptions.getHeapDumpPath(dumpFileName);
        try (CTypeConversion.CCharPointerHolder cPath = CTypeConversion.toCString(dumpPath)) {
//            int size = SizeOf.get(HeapDumpOnErrorVMOperationData.class);
//            heapDumpOnErrorOperationData = StackValue.get(size);
//            UnmanagedMemoryUtil.fill((Pointer) heapDumpOnErrorOperationData, WordFactory.unsigned(size), (byte) 0);

            // TODO try creating a setting a raw file descriptor and see if that works
            heapOnErrorDumpPath = copyDumpPath(cPath.get());
            // heapDumpOnErrorOperationData.setHeapDumpPath(copy);
            // heapDumpOnErrorOperation.heapDumpPathPointerField = copy;
        }

//        verifyPath();
    }

    private static CCharPointer copyDumpPath(CCharPointer cPath) {
        final UnsignedWord length = SubstrateUtil.strlen(cPath);
        final CCharPointer copy = UnmanagedMemory.malloc(length);
        LibC.memcpy(copy, cPath, length);
        return copy;
    }

//    private void verifyPath() {
//        final CCharPointer cPath = heapDumpOnErrorOperationData.getHeapDumpPath();
//        String heapDumpOnErrorPath = CTypeConversion.toJavaString(cPath, SubstrateUtil.strlen(cPath), StandardCharsets.UTF_8);
//        System.out.println("Heap dump on OOME error path is: " + heapDumpOnErrorPath);
//    }

    public void writeHeapTo(RawFileDescriptor fd, boolean gcBefore) throws IOException {
        int size = SizeOf.get(HeapDumpVMOperationData.class);
        HeapDumpVMOperationData data = StackValue.get(size);
        UnmanagedMemoryUtil.fill((Pointer) data, WordFactory.unsigned(size), (byte) 0);

        data.setGCBefore(gcBefore);
        data.setRawFileDescriptor(fd);
        heapDumpOperation.enqueue(data);

        if (!data.getSuccess()) {
            throw new IOException("An error occurred while writing the heap dump.");
        }
    }

    @Fold
    static RawFileOperationSupport getFileSupport() {
        return RawFileOperationSupport.bigEndian();
    }

    @RawStructure
    private interface HeapDumpVMOperationData extends NativeVMOperationData {
        @RawField
        boolean getGCBefore();

        @RawField
        void setGCBefore(boolean value);

        @RawField
        RawFileDescriptor getRawFileDescriptor();

        @RawField
        void setRawFileDescriptor(RawFileDescriptor fd);

        @RawField
        boolean getSuccess();

        @RawField
        void setSuccess(boolean value);
    }

    @RawStructure
    private interface HeapDumpOnErrorVMOperationData extends NativeVMOperationData {
        @RawField
        CCharPointer getHeapDumpPath();

        @RawField
        void setHeapDumpPath(CCharPointer value);
    }

    private class HeapDumpOperation extends NativeVMOperation {
        @Platforms(Platform.HOSTED_ONLY.class)
        HeapDumpOperation() {
            super(VMOperationInfos.get(HeapDumpOperation.class, "Write heap dump", VMOperation.SystemEffect.SAFEPOINT));
        }

        @Override
        @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Heap dumping must not allocate.")
        protected void operate(NativeVMOperationData d) {
            HeapDumpVMOperationData data = (HeapDumpVMOperationData) d;
            if (data.getGCBefore()) {
                System.gc();
            }

            try {
                boolean success = writer.dumpHeap(data.getRawFileDescriptor());
                data.setSuccess(success);
            } catch (Throwable e) {
                reportError(e);
            }
        }

        @RestrictHeapAccess(access = UNRESTRICTED, reason = "Error reporting may allocate.")
        private void reportError(Throwable e) {
            Log.log().string("An exception occurred during heap dumping. The data in the heap dump file may be corrupt.").newline().string(e.getClass().getName()).string(": ")
                            .string(e.getMessage());
        }
    }

    private class HeapDumpOnErrorOperation extends NativeVMOperation {
        // CCharPointer heapDumpPathPointerField;

        @Platforms(Platform.HOSTED_ONLY.class)
        HeapDumpOnErrorOperation() {
            super(VMOperationInfos.get(HeapDumpOnErrorOperation.class, "Write heap dump on OutOfMemoryError", SystemEffect.SAFEPOINT));
        }

        @Override
        @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Heap dumping must not allocate.")
        protected void operate(NativeVMOperationData d) {
            HeapDumpOnErrorVMOperationData data = (HeapDumpOnErrorVMOperationData) d;

            final CCharPointer heapDumpPathPointer = data.getHeapDumpPath();
            // final CCharPointer heapDumpPathPointer = heapDumpPathPointerField;

            // final String heapDumpPath = CTypeConversion.toJavaString(heapDumpPathPointer, SubstrateUtil.strlen(heapDumpPathPointer), StandardCharsets.UTF_8);
            // final RawFileDescriptor fd = getFileSupport().open(heapDumpPath, RawFileOperationSupport.FileAccessMode.READ_WRITE);
            // final RawFileDescriptor fd = getFileSupport().open(heapDumpPathPointer, RawFileOperationSupport.FileAccessMode.READ_WRITE);
            final RawFileDescriptor fd = getFileSupport().create(heapDumpPathPointer, FileCreationMode.CREATE_OR_REPLACE, RawFileOperationSupport.FileAccessMode.READ_WRITE);
            if (!getFileSupport().isValid(fd)) {
                Log.log().string("Invalid file descriptor opening heap dump on OutOfMemoryError.").newline();
                Log.log().string(heapDumpPathPointer).newline();
                return;
            }

            try {
                final boolean success = writer.dumpHeap(fd);
                if (!success) {
                    reportFailure();
                }
            } catch (Throwable e) {
                reportError(e);
            } finally {
                getFileSupport().close(fd);
            }
        }

        @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Error reporting must not allocate.")
        private void reportFailure() {
            Log.log().string("An error occurred while writing the heap dump on OutOfMemoryError.").newline();

        }

        @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Error reporting must not allocate.")
        private void reportError(Throwable e) {
            Log.log().string("An exception occurred during heap dumping on OutOfMemoryError. The data in the heap dump file may be corrupt. ").string(e.getClass().getName());
        }
    }
}
