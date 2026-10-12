package com.cgengine.math;

public final class VectorMath {
    private VectorMath() { }
    public static Vector2f add(Vector2f a, Vector2f b) { return new Vector2f(a.x()+b.x(), a.y()+b.y()); }
    public static Vector3f add(Vector3f a, Vector3f b) { return new Vector3f(a.x()+b.x(),a.y()+b.y(),a.z()+b.z()); }
    public static Vector3f subtract(Vector3f a, Vector3f b) { return new Vector3f(a.x()-b.x(),a.y()-b.y(),a.z()-b.z()); }
    public static Vector3f multiply(Vector3f a, float v) { return new Vector3f(a.x()*v,a.y()*v,a.z()*v); }
    public static float dot(Vector3f a, Vector3f b) { return a.x()*b.x()+a.y()*b.y()+a.z()*b.z(); }
    public static Vector3f cross(Vector3f a, Vector3f b) { return new Vector3f(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x()); }
    public static float length(Vector3f v) { return (float)Math.sqrt(dot(v,v)); }
    public static Vector3f normalize(Vector3f v) { float l=length(v); if (!Float.isFinite(l)||l==0) throw new IllegalArgumentException("Cannot normalize a zero or non-finite vector"); return multiply(v,1/l); }
    public static boolean isFinite(Vector3f v) { return Float.isFinite(v.x())&&Float.isFinite(v.y())&&Float.isFinite(v.z()); }
}
