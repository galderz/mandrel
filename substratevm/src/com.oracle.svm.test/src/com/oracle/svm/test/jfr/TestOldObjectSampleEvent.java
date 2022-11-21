package com.oracle.svm.test.jfr;

import com.oracle.svm.core.jfr.JfrEvent;
import org.junit.Test;

public class TestOldObjectSampleEvent extends JfrTest {
    static Object leak;

    @Override
    protected String[] getTestedEvents() {
        return new String[]{
                JfrEvent.OldObjectSample.getName(),
        };
    }

    @Test
    public void test() throws Exception {
        Node node = new Node();
        leak = node;
        for (int i = 0; i < 1_000_000; i++) {
            node.value = new Big();
            node.left = new Node();
            node.right = new Node();
            node = node.right;
        }

        blackhole(leak);
    }

    static void blackhole(Object obj) {
        if (obj.hashCode() == System.nanoTime()) {
            System.out.println(obj);
        }
    }

    static class Node {
        Node left;
        Node right;
        Object value;
    }

    static class Big {
        public long value1;
        public Object value2;
        float value3;
        int value4;
        double value5;
    }
}
