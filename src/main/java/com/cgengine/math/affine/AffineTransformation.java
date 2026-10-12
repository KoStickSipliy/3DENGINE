package com.cgengine.math.affine;

import com.cgengine.math.Matrix4f;
import com.cgengine.math.MatrixMath;
import com.cgengine.math.Vector3f;
import com.cgengine.math.Vector4f;
import java.util.ArrayList;
import java.util.List;

public interface AffineTransformation {
    Matrix4f getMatrix();
    default Vector3f transform(Vector3f value) { Vector4f transformed=MatrixMath.multiply(getMatrix(),new Vector4f(value.x(),value.y(),value.z(),1)); return new Vector3f(transformed.x(),transformed.y(),transformed.z()); }
    default List<Vector3f> transform(List<Vector3f> values) { List<Vector3f> result=new ArrayList<>(values.size()); for(Vector3f value:values) result.add(transform(value)); return result; }
}
