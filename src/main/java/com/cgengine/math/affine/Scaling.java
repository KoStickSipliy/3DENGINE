package com.cgengine.math.affine;
import com.cgengine.math.Matrix4f;
public class Scaling implements AffineTransformation {
    private float x,y,z;
    public Scaling(float x,float y,float z) { set(x,y,z); } public Scaling(){this(1,1,1);}
    @Override public Matrix4f getMatrix(){return new Matrix4f(x,0,0,0,0,y,0,0,0,0,z,0,0,0,0,1);}
    public void setX(float value){set(value,y,z);} public void setY(float value){set(x,value,z);} public void setZ(float value){set(x,y,value);} public void set(float nx,float ny,float nz){if(!Float.isFinite(nx)||!Float.isFinite(ny)||!Float.isFinite(nz)||nx<=0||ny<=0||nz<=0)throw new IllegalArgumentException("Scale components must be finite and strictly greater than zero");x=nx;y=ny;z=nz;} public void setRelative(float dx,float dy,float dz){set(x*dx,y*dy,z*dz);}
}
