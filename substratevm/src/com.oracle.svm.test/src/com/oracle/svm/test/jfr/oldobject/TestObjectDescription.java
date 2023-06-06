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

package com.oracle.svm.test.jfr.oldobject;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class TestObjectDescription extends JfrOldObjectTest {
    /**
     * Destroy thread groups to avoid leak building its parent's groups array. Keep destroy outside
     * the test so that java monitors created during the synchronized block access don't end up
     * polluting the recording.
     */
    @After
    public void destroyThreadGroups() {
        Node current = (Node) leak;
        while (current != null && current.value instanceof ThreadGroup) {
            ((ThreadGroup) current.value).destroy();
            current = current.right;
        }
    }

    @Test
    public void testThreadGroup() throws Throwable {
        Recording recording = startRecording();

        Node node = new Node();
        leak = node;
        for (int i = 0; i < 10_000; i++) {
            node.value = new MyThreadGroup(MyThreadGroup.NAME);
            node.left = new Node();
            node.right = new Node();
            node = node.right;
        }

        blackhole(leak);
        stopRecording(recording, events -> filterEventsByType(MyThreadGroup.class, events).forEach(e -> assertDescription("Thread Group: My Thread Group", e)));
    }

    private static void assertDescription(String expected, RecordedEvent event) {
        final String description = event.<RecordedObject> getValue("object").getValue("description");
        Assert.assertEquals(expected, description);
    }

    @Test
    public void testEllipsis() throws Throwable {
        final int objectDescriptionMaxSize = 100;
        final int prefixSize = "Thread Group: ".length();
        final String threadGroupName = "x".repeat(2 * objectDescriptionMaxSize);

        Recording recording = startRecording();

        Node node = new Node();
        leak = node;
        for (int i = 0; i < 10_000; i++) {
            node.value = new MyThreadGroup(threadGroupName);
            node.left = new Node();
            node.right = new Node();
            node = node.right;
        }

        blackhole(leak);
        stopRecording(recording, events -> filterEventsByType(MyThreadGroup.class, events).forEach(e -> assertDescriptionLimit("xxx...", objectDescriptionMaxSize + prefixSize, e)));
    }

    private static void assertDescriptionLimit(String expected, int expectedSize, RecordedEvent event) {
        final String description = event.<RecordedObject> getValue("object").getValue("description");
        Assert.assertEquals(expectedSize, description.length());
        Assert.assertTrue(description.contains(expected));
    }

    static class Node {
        Node left;
        Node right;
        Object value;
    }

    public static final class MyThreadGroup extends ThreadGroup {
        public static final String NAME = "My Thread Group";

        public MyThreadGroup(String name) {
            super(name);
        }
    }
}
