package com.oracle.svm.core.jfr;

final class JfrOldObjectSample {
    private final Object object; // todo make weak reference? or manually clean these upon a gc event (like in hotspot)
    private final long allocationSize;
    private final long stackTraceId = 0;

    JfrOldObjectSample(Object object, long allocationSize) {
        this.object = object;
        this.allocationSize = allocationSize;
    }

    public Object getObject() {
        return object;
    }

    public long getAllocationSize() {
        return allocationSize;
    }

    public boolean hasStackTraceId() {
        return stackTraceId != 0;
    }

    static final class Comparator implements java.util.Comparator<JfrOldObjectSample> {
        static final Comparator INSTANCE = new Comparator();

        @Override
        public int compare(JfrOldObjectSample o1, JfrOldObjectSample o2) {
            return Long.compare(o1.allocationSize, o2.allocationSize);
        }
    }
}
