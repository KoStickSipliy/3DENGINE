package com.cgengine.math.affine;
import com.cgengine.math.Matrix4f;
public class Rotator implements AffineTransformation {
    public enum Axis { X,Y,Z }
    private float angle; private Axis axis;
    public Rotator(float angle,Axis axis){this.angle=angle;this.axis=axis;} public Rotator(int degrees,Axis axis){this((float)Math.toRadians(degrees),axis);} public static Rotator degrees(float degrees,Axis axis){return new Rotator((float)Math.toRadians(degrees),axis);} public Rotator(Axis axis){this(0,axis);}
    @Override public Matrix4f getMatrix(){float c=(float)Math.cos(angle),s=(float)Math.sin(angle); return switch(axis){case X->new Matrix4f(1,0,0,0,0,c,-s,0,0,s,c,0,0,0,0,1);case Y->new Matrix4f(c,0,s,0,0,1,0,0,-s,0,c,0,0,0,0,1);case Z->new Matrix4f(c,-s,0,0,s,c,0,0,0,0,1,0,0,0,0,1);};}
    public void setAxis(Axis value){axis=value;} public void setAngle(float value){angle=value;} public void setAngle(int degrees){angle=(float)Math.toRadians(degrees);} public void setRelativeAngle(float value){angle+=value;} public void setRelativeAngle(int degrees){angle+=(float)Math.toRadians(degrees);}
}
