package com.cgengine.math;

/** Immutable three-dimensional engine vector. */
public record Vector3f(float x, float y, float z) {
    /** Compatibility helper removed together with legacy vertex deletion in P03. */
    @SuppressWarnings("unchecked") public static <T> T cloneVector3(T value) { return value; }
}
