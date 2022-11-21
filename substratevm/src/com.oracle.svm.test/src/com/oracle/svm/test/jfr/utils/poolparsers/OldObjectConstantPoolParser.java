package com.oracle.svm.test.jfr.utils.poolparsers;

import com.oracle.svm.core.jfr.JfrType;
import com.oracle.svm.test.jfr.utils.RecordingInput;
import org.junit.Assert;

import java.io.IOException;

public class OldObjectConstantPoolParser extends ConstantPoolParser {
    @Override
    public void parse(RecordingInput input) throws IOException {
        final int numOldObjects = input.readInt();
        for (int i = 0; i < numOldObjects; i++) {
            addFoundId(input.readLong()); // Id.
            Assert.assertTrue("Address can't be 0", input.readLong() != 0); // Address
            addExpectedId(JfrType.Class, input.readLong()); // ClassId.
            input.readLong(); // todo Description
            input.readLong(); // todo Reference
        }
    }
}
