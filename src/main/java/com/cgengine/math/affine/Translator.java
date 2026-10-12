package com.cgengine.math.affine;
import com.cgengine.math.Matrix4f;
public class Translator implements AffineTransformation {
    private float x,y,z;
    public Translator(float x,float y,float z) { set(x,y,z); }
    public Translator() { this(0,0,0); }
    @Override public Matrix4f getMatrix() { return new Matrix4f(1,0,0,x,0,1,0,y,0,0,1,z,0,0,0,1); }
    public void setX(float value){x=value;} public void setY(float value){y=value;} public void setZ(float value){z=value;} public void set(float nx,float ny,float nz){x=nx;y=ny;z=nz;} public void setRelative(float dx,float dy,float dz){set(x+dx,y+dy,z+dz);}
}
