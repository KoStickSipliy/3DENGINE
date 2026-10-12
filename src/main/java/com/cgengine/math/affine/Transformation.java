package com.cgengine.math.affine;

import com.cgengine.math.Matrix4f;
import com.cgengine.math.MatrixMath;
import java.util.ArrayList;
import java.util.List;

public class Transformation implements AffineTransformation, DataList<AffineTransformation> {
    private final List<AffineTransformation> transformations=new ArrayList<>();
    private boolean calculated;
    private Matrix4f matrix;
    public Transformation(AffineTransformation... values) { for(AffineTransformation value:values) transformations.add(value); }
    public Transformation() { }
    @Override public Matrix4f getMatrix() { if(!calculated) { matrix=Matrix4f.identity(); for(AffineTransformation value:transformations) matrix=MatrixMath.multiply(matrix,value.getMatrix()); calculated=true; } return matrix; }
    @Override public void add(AffineTransformation value) { transformations.add(value); calculated=false; }
    @Override public void remove(int index) { transformations.remove(index); calculated=false; }
    @Override public void remove(AffineTransformation value) { transformations.remove(value); calculated=false; }
    @Override public void set(int index, AffineTransformation value) { transformations.set(index,value); calculated=false; }
    @Override public AffineTransformation get(int index) { return transformations.get(index); }
}
