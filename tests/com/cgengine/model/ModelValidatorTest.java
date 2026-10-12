package com.cgengine.model;
import com.cgengine.math.Vector3f;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ModelValidatorTest {
 private static Model triangle(){Model m=new Model();m.vertices.add(new Vector3f(0,0,0));m.vertices.add(new Vector3f(1,0,0));m.vertices.add(new Vector3f(0,1,0));Polygon p=new Polygon();p.setVertexIndices(new ArrayList<>(java.util.List.of(0,1,2)));m.polygons.add(p);return m;}
 @Test void normalRecalculationCreatesAlignedIndices(){Model m=triangle();m.computeNormals();ModelValidator.validate(m);assertEquals(java.util.List.of(0,0,0),m.polygons.getFirst().getNormalIndices());assertEquals(new Vector3f(0,0,1),m.normals.getFirst());}
 @Test void invalidIndexAndNonTriangleAreRejected(){Model m=triangle();m.polygons.getFirst().setVertexIndices(new ArrayList<>(java.util.List.of(0,1,3)));assertThrows(ModelValidationException.class,()->ModelValidator.validate(m));m.polygons.getFirst().setVertexIndices(new ArrayList<>(java.util.List.of(0,1)));assertThrows(ModelValidationException.class,m::triangulate);}
 @Test void cloneDoesNotAliasPolygonLists(){Model m=triangle();Model c=m.clone();c.polygons.getFirst().getVertexIndices().set(0,2);assertEquals(0,m.polygons.getFirst().getVertexIndices().getFirst());}
}
