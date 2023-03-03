package com.oracle.svm.core.jfr;

import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.hub.DynamicHub;
import org.graalvm.nativebridge.In;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// todo rename class name to something more clear
public final class JfrOldObjectUtils {

    @UnknownObjectField(types = {byte[].class}) private byte[] fieldsMapBytes;

    @UnknownObjectField(types = {Object[][][].class}) private Object[][][] fieldsMap;

    private static final int LOCATION_SLOT = 0;
    private static final int NAME_SLOT = 1;
    private static final int MODIFIERS_SLOT = 2;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrOldObjectUtils() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void initialize(int size) {
        fieldsMap = new Object[size][][];
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setFieldsMapBytes(byte[] fieldsMapBytes) {
        this.fieldsMapBytes = fieldsMapBytes;
        System.out.println("Field map bytes set. Length: " + this.fieldsMapBytes.length);
    }

    public void buildFieldsMap() {
        // TODO is this the right place to do this?
        addFieldsMap(fieldsMapBytes);
        System.out.println("Field map types set. Length: " + this.fieldsMap.length);
    }

    public Object[][] getOldObjectFields(Class<?> clazz) {
        final DynamicHub hub = DynamicHub.fromClass(clazz);
        return this.fieldsMap[hub.getTypeID()];
    }

    public Object[] getOldObjectField(DynamicHub hub, int fieldOffset) {
        final int typeId = hub.getTypeID();
        final Object[] obj = this.fieldsMap[typeId];
        final Object[][] fields = (Object[][]) obj;
        if (fields == null) {
            return null;
            // throw new IllegalStateException("Class name must be present in field map: " + hub.getName() + " offset: " + fieldOffset);
        }

        for (int i = 0; i < fields.length; i++) {
            final Object[] fieldElements = fields[i];
            final int location = (int) fieldElements[LOCATION_SLOT];
            if (location == fieldOffset) {
                return fieldElements;
            }
        }

        throw new IllegalStateException("Field should have been found");
    }

    static int getFieldLocation(Object[] field) {
        return (int) field[LOCATION_SLOT];
    }

    static String getFieldName(Object[] field) {
        return (String) field[NAME_SLOT];
    }

    static Integer getModifiers(Object[] field) {
        return (Integer) field[MODIFIERS_SLOT];
    }

    // todo derived from HeapDumpWriterImpl
    private void addFieldsMap(byte[] data) {
        int offset = 0;
        while (offset < data.length) {
            List<Object[]> fields;
            String className = readString(data, offset);
            offset += className.length() + 1;
            int typeId = readInt(data, offset);
            offset += 4;

            if (data[offset] == 0 && data[offset + 1] == 0) {
                /* No fields. */
                fields = Collections.emptyList();
                offset += 2;
            } else {
                fields = new ArrayList<>();
                offset = readFields(false, data, offset, fields);
                offset++;
                offset = readFields(true, data, offset, fields);
                offset++;
            }

            // Object[][] fieldsArray = new Object[fields.size()][];

//            for (int i = 0; i < fields.size(); i++) {
//                final Object[] fieldElements = fields.get(i);
//                fieldsMap[typeId][i] = fieldElements;
//            }

            Object[][] fieldsArray = new Object[fields.size()][];
            fieldsMap[typeId] = fields.toArray(fieldsArray);
        }
    }

    // todo copied from HeapDumpWriterImpl
    private static String readString(byte[] data, int start) {
        int len = readStringLength(data, start);

        return new String(data, start, len, StandardCharsets.UTF_8);
    }

    /**
     * Returns size of the null-terminated string.
     *
     * @param data byte[] array that is the source of string.
     * @param start the initial offset to <code>data</code> array.
     * @return the number of characters (bytes) in a null-terminated character sequence, without
     *         including the null-terminating character.
     */
    // todo copied from HeapDumpWriterImpl
    private static int readStringLength(byte[] data, int start) {
        int offset = start;

        while (data[offset] != 0) {
            offset++;
        }
        return offset - start;
    }

    // todo derived from HeapDumpWriterImpl
    private int readFields(boolean isStatic, byte[] data, int dataOffset, List<Object[]> fields) {
        int offset = dataOffset;
        while (data[offset] != 0) {
            /* Read field. */
            // int stringStart = offset;
            // int stringLength = readStringLength(data, offset);
            final String fieldName = readString(data, offset);
            offset += fieldName.length() + 1;
            int modifiers = readInt(data, offset);
            offset += 4;
            offset++; // ignore java signature
            // char javaSig = (char) data[offset++];
            offset++; // ignore storage signature
            // char storageSig = (char) data[offset++];
            int location = readInt(data, offset);
            offset += 4;
            Object[] fieldDef = new Object[MODIFIERS_SLOT + 1];
            fieldDef[LOCATION_SLOT] = location;
            fieldDef[NAME_SLOT] = fieldName;
            fieldDef[MODIFIERS_SLOT] = modifiers;
            fields.add(fieldDef);
        }
        return offset;
    }

    private static int readInt(final byte[] data, final int st) {
        int start = st;
        int ch1 = data[start++] & 0xFF;
        int ch2 = data[start++] & 0xFF;
        int ch3 = data[start++] & 0xFF;
        int ch4 = data[start++] & 0xFF;
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

//    private static final class Field {
//        private final int location;
//        private String name;
//        private int modifiers;
//
//        private Field(int location, String name, int modifiers) {
//            this.location = location;
//            this.name = name;
//            this.modifiers = modifiers;
//        }
//    }

    public static final class OldObjectField {
        public final String name;
        public final int modifiers;

        public OldObjectField(String name, int modifiers) {
            this.name = name;
            this.modifiers = modifiers;
        }
    }
}
