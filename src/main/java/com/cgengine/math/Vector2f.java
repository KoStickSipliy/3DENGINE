package com.cgengine.math;

/** Immutable two-dimensional engine vector. */
public record Vector2f(float x, float y) {
    /** Compatibility helper removed together with legacy vertex deletion in P03. */
    @SuppressWarnings("unchecked") public static <T> T cloneVector2(T value) { return value; }
}
