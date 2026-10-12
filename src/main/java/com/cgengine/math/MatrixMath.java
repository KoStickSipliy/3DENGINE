package com.cgengine.math;

public final class MatrixMath {
    private MatrixMath() { }
    public static Matrix4f multiply(Matrix4f a, Matrix4f b) { float[] o=new float[16]; for(int r=0;r<4;r++) for(int c=0;c<4;c++) for(int i=0;i<4;i++) o[r*4+c]+=a.get(r,i)*b.get(i,c); return new Matrix4f(o); }
    public static Vector4f multiply(Matrix4f m, Vector4f v) { float[] x={v.x(),v.y(),v.z(),v.w()},o=new float[4]; for(int r=0;r<4;r++) for(int c=0;c<4;c++) o[r]+=m.get(r,c)*x[c]; return new Vector4f(o[0],o[1],o[2],o[3]); }
    public static Vector3f perspectiveDivide(Vector4f v) { if (!Float.isFinite(v.w())||v.w()==0) throw new IllegalArgumentException("Perspective divide requires finite non-zero w"); return new Vector3f(v.x()/v.w(),v.y()/v.w(),v.z()/v.w()); }
}
