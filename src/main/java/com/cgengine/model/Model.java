package com.cgengine.model;
import com.cgengine.math.Vector2f;
import com.cgengine.math.Vector3f;
import com.cgengine.math.VectorMath;
import java.util.ArrayList;
import java.util.Objects;
import com.cgengine.render_engine.Texture2D;
import com.cgengine.math.affine.Transformation;
public class Model implements Cloneable {
 public ArrayList<Vector3f> vertices=new ArrayList<>(); public ArrayList<Vector2f> textureVertices=new ArrayList<>(); public ArrayList<Vector3f> normals=new ArrayList<>(); public ArrayList<Polygon> polygons=new ArrayList<>(); private Texture2D texture; private ArrayList<Vector3f> transformBaseVertices;
 public Texture2D getTexture(){return texture;} public void setTexture(Texture2D texture){this.texture=texture;} public void removeTexture(){texture=null;}
 public void applyTransformation(Transformation transformation) {
  Objects.requireNonNull(transformation);
  Model candidate = clone();
  candidate.vertices = new ArrayList<>(vertices.size());
  for (Vector3f vertex : vertices) candidate.vertices.add(transformation.transform(vertex));
  ModelValidator.validate(candidate);
  candidate.computeNormals();
  ModelValidator.validate(candidate);
  if (transformBaseVertices == null) transformBaseVertices = new ArrayList<>(vertices);
  vertices = candidate.vertices;
  normals = candidate.normals;
  for (int i = 0; i < polygons.size(); i++)
   polygons.get(i).setNormalIndices(new ArrayList<>(candidate.polygons.get(i).getNormalIndices()));
 }
 public void resetTransformation(){if(transformBaseVertices!=null){vertices=new ArrayList<>(transformBaseVertices);computeNormals();transformBaseVertices=null;}}
 public void triangulate(){for(Polygon p:polygons)if(p.getVertexIndices().size()!=3)throw new ModelValidationException("Only triangular faces are supported");}
 /** One geometric normal per triangle, shared by its three OBJ corners. */
 public void computeNormals() {
  triangulate();
  normals = new ArrayList<>(polygons.size());
  for (Polygon p : polygons) {
   var indices = p.getVertexIndices();
   Vector3f normal = p.geometricNormal(vertices.get(indices.get(0)),vertices.get(indices.get(1)),vertices.get(indices.get(2)));
   int normalIndex = normals.size();
   normals.add(normal);
   p.setNormalIndices(new ArrayList<>(java.util.List.of(normalIndex,normalIndex,normalIndex)));
  }
 }
 public void validate(){ModelValidator.validate(this);} @Override public Model clone(){Model r=new Model();r.vertices=new ArrayList<>(vertices);r.textureVertices=new ArrayList<>(textureVertices);r.normals=new ArrayList<>(normals);r.texture=texture;r.transformBaseVertices=transformBaseVertices==null?null:new ArrayList<>(transformBaseVertices);for(Polygon p:polygons)r.polygons.add(p.clone());return r;} @Override public boolean equals(Object v){return v instanceof Model m&&Objects.equals(vertices,m.vertices)&&Objects.equals(textureVertices,m.textureVertices)&&Objects.equals(normals,m.normals)&&Objects.equals(polygons,m.polygons);}@Override public int hashCode(){return Objects.hash(vertices,textureVertices,normals,polygons);}
}
