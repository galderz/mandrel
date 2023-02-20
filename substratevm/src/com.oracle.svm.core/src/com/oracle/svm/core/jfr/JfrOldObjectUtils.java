package com.oracle.svm.core.jfr;

import com.oracle.svm.core.heap.UnknownObjectField;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// todo rename class name to something more clear
public final class JfrOldObjectUtils {

    @UnknownObjectField(types = {byte[].class}) private byte[] fieldsMapBytes;

    private Map<String, List<Field>> fieldsMap;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrOldObjectUtils() {
        this.fieldsMap = new HashMap<>();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setFieldsMapBytes(byte[] fieldsMapBytes) {
        this.fieldsMapBytes = fieldsMapBytes;
        System.out.println("Field map bytes set. Length: " + this.fieldsMapBytes.length);

        // TODO is this the right place to do this?
        addFieldsMap(fieldsMapBytes, this.fieldsMap);
        System.out.println("Field map types set. Length: " + this.fieldsMap.size());
    }

    public OldObjectField getOldObjectField(String className, int fieldOffset) {
        final List<Field> fields = this.fieldsMap.get(className);
        if (fields == null) {
            throw new IllegalStateException("Class name must be present in field map");
        }

        for (int i = 0; i < fields.size(); i++) {
            final Field field = fields.get(i);
            if (field.location == fieldOffset) {
                return new OldObjectField(field.name, field.modifiers);
            }
        }

        throw new IllegalStateException("Field should have been found");
    }

    // todo derived from HeapDumpWriterImpl
    private void addFieldsMap(byte[] data, Map<String, List<Field>> fieldsMap) {
        int offset = 0;
        while (offset < data.length) {
            List<Field> fields;
            String className = readString(data, offset);
            offset += className.length() + 1;

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
            fieldsMap.put(className, fields);
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
    private int readFields(boolean isStatic, byte[] data, int dataOffset, List<Field> fields) {
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
            Field fieldDef = new Field(location, fieldName, modifiers);
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

    private static final class Field {
        private final int location;
        private String name;
        private int modifiers;

        private Field(int location, String name, int modifiers) {
            this.location = location;
            this.name = name;
            this.modifiers = modifiers;
        }
    }

    public static final class OldObjectField {
        public final String name;
        public final int modifiers;

        public OldObjectField(String name, int modifiers) {
            this.name = name;
            this.modifiers = modifiers;
        }
    }
}
