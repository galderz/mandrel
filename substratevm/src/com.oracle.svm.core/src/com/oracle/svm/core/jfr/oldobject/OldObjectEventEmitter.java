/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr.oldobject;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.events.OldObjectSampleEvent;

final class OldObjectEventEmitter {

    // Making callees not uninterruptible to deal with WeakReference.get()
    @Uninterruptible(reason = "Prevent JFR recording and epoch change.", calleeMustBe = false)
    static void emitUnchained(OldObjectArray samples, long lastSweep) {
        final long timestamp = JfrTicks.elapsedTicks();

        for (int i = 0; i < samples.getCapacity(); i++) {
            final OldObject sample = samples.getSample(i);
            if (sample.reference != null) {
                final Object obj = sample.reference.get();
                final long allocationTime = sample.allocationTime;
                if (isAliveAndOlderThan(lastSweep, obj, allocationTime)) {
                    final long objectId = SubstrateJVM.getJfrOldObjectRepository().serializeOldObject(obj);
                    final long threadId = sample.threadId;
                    final long stackTraceId = sample.stackTraceId;
                    final long heapUsedAtLastGC = sample.heapUsedAtLastGC;
                    final int arrayLength = sample.arrayLength;
                    OldObjectSampleEvent.emit(timestamp, objectId, allocationTime, threadId, stackTraceId, heapUsedAtLastGC, arrayLength);
                }
            }
        }
    }

    private static boolean isAliveAndOlderThan(long lastSweep, Object obj, long allocationTime) {
        return obj != null && allocationTime < lastSweep;
    }
}
