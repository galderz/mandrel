package com.oracle.svm.core.jfr.oldobject;

/**
 * A wrapper for an old object.
 * Wrapper needed to be able to detect internal interferences when heap walking.
 */
public final class OldObject {
    public Object object;

    public OldObject(Object object) {
        this.object = object;
    }
}
