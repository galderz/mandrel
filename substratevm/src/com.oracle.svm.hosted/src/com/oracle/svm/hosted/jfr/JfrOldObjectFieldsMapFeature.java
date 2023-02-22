package com.oracle.svm.hosted.jfr;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.jfr.JfrOldObjectUtils;
import com.oracle.svm.core.meta.SharedField;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.ByteArrayReader;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import jdk.vm.ci.meta.ResolvedJavaField;
import org.graalvm.compiler.core.common.util.TypeConversion;
import org.graalvm.compiler.core.common.util.UnsafeArrayTypeWriter;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.io.UnsupportedEncodingException;
import java.util.Collection;

@AutomaticallyRegisteredFeature
public class JfrOldObjectFieldsMapFeature implements InternalFeature {

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(JfrOldObjectUtils.class, new JfrOldObjectUtils());
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        // todo trim down to only those that are instances
        int mapSize = ImageSingletons.lookup(DynamicHubSupport.class).getMaxTypeId();
        ImageSingletons.lookup(JfrOldObjectUtils.class).initialize(mapSize);
    }

    /**
     * Write out fields info and their offsets.
     */
    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public void afterCompilation(AfterCompilationAccess access) {
        FeatureImpl.AfterCompilationAccessImpl accessImpl = (FeatureImpl.AfterCompilationAccessImpl) access;
        // todo skip serialization if possible
        // todo transform it directly into an Object[][] with all the necessary information (iow skip a hashmap)
        byte[] fieldMap = dumpFieldsMap(accessImpl.getTypes());

        // System.out.println("String type id: " + JfrTraceId.getTraceId(((FeatureImpl.AfterCompilationAccessImpl) access).getTypes().iterator().next()));
        // System.out.println("A type id: " + accessImpl.getTypes().iterator().next());

        ImageSingletons.lookup(JfrOldObjectUtils.class).setFieldsMapBytes(fieldMap);
        access.registerAsImmutable(fieldMap);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static byte[] dumpFieldsMap(Collection<? extends SharedType> types) {
        UnsafeArrayTypeWriter writeBuffer = UnsafeArrayTypeWriter.create(ByteArrayReader.supportsUnalignedMemoryAccess());

        writeFieldsInfo(writeBuffer, types);
        int length = TypeConversion.asS4(writeBuffer.getBytesWritten());
        return writeBuffer.toArray(new byte[length]);
    }

    // todo derived from HeapDumpHostedUtils
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void writeFieldsInfo(UnsafeArrayTypeWriter writeBuffer, Collection<? extends SharedType> types) {
        for (SharedType type : types) {
            /* I am only interested in instance types. */
            if (type.isInstanceClass()) {
                /* Get the direct fields of the class. */
                final ResolvedJavaField[] fields = type.getInstanceFields(false);
                /* Get the static fields of the class. */
                final ResolvedJavaField[] sfields = type.getStaticFields();
                /* I am only interested in classes with some fields. */
                if (fields.length == 0 && sfields.length == 0) {
                    continue;
                }
                /* Write the class name */
                writeString(writeBuffer, type.toClassName());
                /* Write type id */
                writeInt(writeBuffer, type.getHub().getTypeID());

                /* Write each direct field and offset. */
                for (ResolvedJavaField resolvedJavaField : inHotSpotFieldOrder(fields)) {
                    if (resolvedJavaField instanceof SharedField) {
                        final SharedField field = (SharedField) resolvedJavaField;

                        writeField(field, writeBuffer);
                    }
                }
                writeBuffer.putU1(0);
                /* Write each static field and offset. */
                for (ResolvedJavaField resolvedJavaField : inHotSpotFieldOrder(sfields)) {
                    if (resolvedJavaField instanceof SharedField) {
                        final SharedField field = (SharedField) resolvedJavaField;
                        if (!field.isWritten()) {
                            /* I am only interested in fields that are not constants. */
                            continue;
                        }
                        if (!field.isAccessed()) {
                            /* I am only interested in fields that are used. */
                            continue;
                        }

                        writeField(field, writeBuffer);
                    }
                }
                writeBuffer.putU1(0);
            }
        }
    }

    // todo derived from HeapDumpHostedUtils
    @Platforms(Platform.HOSTED_ONLY.class)
    private static void writeField(final SharedField field, UnsafeArrayTypeWriter writeBuffer) {
        final int location = field.getLocation();
        /* I am only interested in fields that have locations. */
        if (location < 0) {
            return;
        }
        writeString(writeBuffer, field.getName());
        final int modifiers = field.getModifiers();
        writeInt(writeBuffer, modifiers);
        writeBuffer.putU1(field.getJavaKind().getTypeChar());
        writeBuffer.putU1(field.getStorageKind().getTypeChar());
        writeInt(writeBuffer, location);
    }

    private static void writeInt(UnsafeArrayTypeWriter writeBuffer, int num) {
        writeBuffer.putU1((num >>> 24) & 0xFF);
        writeBuffer.putU1((num >>> 16) & 0xFF);
        writeBuffer.putU1((num >>> 8) & 0xFF);
        writeBuffer.putU1((num >>> 0) & 0xFF);
    }

    /*
     * Write fields in the same order as in HotSpot heap dump. This is the reverse order of what SVM
     * hands out. See also GR-6758.
     */
    // todo copied from HeapDumpHostedUtils
    @Platforms(Platform.HOSTED_ONLY.class)
    private static ResolvedJavaField[] inHotSpotFieldOrder(ResolvedJavaField[] fields) {
        ResolvedJavaField[] reversed = new ResolvedJavaField[fields.length];

        for (int i = 0; i < fields.length; i++) {
            reversed[fields.length - 1 - i] = fields[i];
        }
        return reversed;
    }

    // todo copied from HeapDumpHostedUtils
    @Platforms(Platform.HOSTED_ONLY.class)
    private static void writeString(UnsafeArrayTypeWriter writeBuffer, String name) {
        try {
            byte[] buf = name.getBytes("UTF-8");
            for (byte b : buf) {
                writeBuffer.putU1(b);
            }
            writeBuffer.putU1(0);
        } catch (UnsupportedEncodingException ex) {
            VMError.shouldNotReachHere(ex);
        }
    }
}
