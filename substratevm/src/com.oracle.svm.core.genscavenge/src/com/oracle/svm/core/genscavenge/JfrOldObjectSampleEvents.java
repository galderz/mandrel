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

package com.oracle.svm.core.genscavenge;

import com.oracle.svm.core.jfr.JfrTicks;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;

import java.lang.ref.WeakReference;

public class JfrOldObjectSampleEvents {
    static void sample(Object obj, long allocatedSize, int arrayLength) {
        if (hasJfrSupport()) {
            // Instantiate weak reference at the last possible time before allocations are not allowed
            jfrSupport().sample(new WeakReference<>(obj), allocatedSize, arrayLength);
        }
    }

    static void updateLastSweep(UnsignedWord sizeBefore, UnsignedWord sizeAfter) {
        final long timestamp = JfrTicks.elapsedTicks();
        if (timestamp > 0 && hasJfrSupport()) {
            if (sizeAfter.belowThan(sizeBefore)) {
                jfrSupport().updateLastSweep(timestamp);
            }
        }
    }

    @Fold
    static boolean hasJfrSupport() {
        return ImageSingletons.contains(JfrOldObjectSampleEventSupport.class);
    }

    @Fold
    static JfrOldObjectSampleEventSupport jfrSupport() {
        return ImageSingletons.lookup(JfrOldObjectSampleEventSupport.class);
    }
}
