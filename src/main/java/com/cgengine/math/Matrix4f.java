package com.cgengine.math;

import java.util.Arrays;

/** Row-major storage; all operations use column vectors and M × v. */
public final class Matrix4f {
    private final float[][] values = new float[4][4];
    public Matrix4f(final float... entries) { if (entries.length != 16) throw new IllegalArgumentException("Matrix4f requires 16 entries"); for (int row=0; row<4; row++) System.arraycopy(entries, row*4, values[row], 0, 4); }
    public static Matrix4f identity() { return new Matrix4f(1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1); }
    public float get(int row, int column) { return values[row][column]; }
    public Matrix4f with(int row, int column, float value) { float[] copy = new float[16]; for (int r=0;r<4;r++) System.arraycopy(values[r],0,copy,r*4,4); copy[row*4+column]=value; return new Matrix4f(copy); }
    @Override public boolean equals(Object other) { return other instanceof Matrix4f matrix && Arrays.deepEquals(values, matrix.values); }
    @Override public int hashCode() { return Arrays.deepHashCode(values); }
}
