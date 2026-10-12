package com.cgengine.render_engine;
import com.cgengine.math.*;
import com.cgengine.model.Model;
import com.cgengine.model.ModelValidator;
import com.cgengine.render_engine.Rasterization.*;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.image.PixelFormat;
import java.util.List;
/** Filled-model adapter; clipping always precedes perspective division. */
public final class PolygonFiller {
 private PolygonFiller(){} public static void fillPolygon(GraphicsContext c,Camera camera,Model m,int w,int h,Color color){FrameBuffer b=new FrameBuffer(w,h);fill(b,camera,m,color==null?null:toArgb(color));publish(c,b);}
 public static void publish(GraphicsContext c,FrameBuffer b){c.getPixelWriter().setPixels(0,0,b.width(),b.height(),PixelFormat.getIntArgbInstance(),b.colors(),0,b.width());}
 public static void fill(FrameBuffer b,Camera camera,Model m){fill(b,camera,m,null);} public static void fill(FrameBuffer b,Camera camera,Model m,Integer overrideColor){ModelValidator.validate(m);Matrix4f view=camera.getViewMatrix(),mvp=MatrixMath.multiply(camera.getProjectionMatrix(),view);for(var p:m.polygons){int base=overrideColor==null?p.getBaseColor().argb():overrideColor;float brightness=Light.CAMERA_DIRECTIONAL.brightness(faceNormal(view,m,p));boolean textured=m.getTexture()!=null&&p.getTextureVertexIndices().size()==3;List<ClipVertex> clipped=Clipper.clipTriangle(clip(mvp,m.vertices.get(p.getVertexIndices().get(0)),uv(m,p,0)),clip(mvp,m.vertices.get(p.getVertexIndices().get(1)),uv(m,p,1)),clip(mvp,m.vertices.get(p.getVertexIndices().get(2)),uv(m,p,2)));for(ClipVertex[] triangle:Clipper.triangles(clipped)){RasterVertex[] v=new RasterVertex[3];try{for(int i=0;i<3;i++){Vector4f clip=triangle[i].position();Vector3f n=MatrixMath.perspectiveDivide(clip);Vector2f s=GraphicConveyor.vertexToVec2(n,b.width(),b.height());float invW=1/clip.w();Vector2f uv=triangle[i].uv();v[i]=new RasterVertex(s.x(),s.y(),n.z(),uv==null?0:uv.x()*invW,uv==null?0:uv.y()*invW,invW);}if(textured)TriangleRasterizer.rasterize(b,v[0],v[1],v[2],(w0,w1,w2)->{float reciprocal=w0*v[0].inverseW()+w1*v[1].inverseW()+w2*v[2].inverseW();int sampled=TextureSampler.sample(m.getTexture(),(w0*v[0].uOverW()+w1*v[1].uOverW()+w2*v[2].uOverW())/reciprocal,(w0*v[0].vOverW()+w1*v[1].vOverW()+w2*v[2].vOverW())/reciprocal);return Light.CAMERA_DIRECTIONAL.shade(sampled,brightness);});else TriangleRasterizer.rasterize(b,v[0],v[1],v[2],Light.CAMERA_DIRECTIONAL.shade(base,brightness));}catch(IllegalArgumentException ignored){}}}}
 private static Vector2f uv(Model model,com.cgengine.model.Polygon polygon,int corner){return polygon.getTextureVertexIndices().size()==3?model.textureVertices.get(polygon.getTextureVertexIndices().get(corner)):null;}
 /** Flat Lambert uses cached geometry; explicit absent/zero corner normals keep ambient. */
 private static Vector3f faceNormal(Matrix4f view,Model model,com.cgengine.model.Polygon polygon) {
  if (polygon.getNormalIndices().isEmpty()) return new Vector3f(0,0,0);
  for (int index : polygon.getNormalIndices()) {
   Vector3f corner = model.normals.get(index);
   if (!VectorMath.isFinite(corner) || VectorMath.length(corner) == 0) return new Vector3f(0,0,0);
  }
  var indices = polygon.getVertexIndices();
  Vector3f normal = polygon.geometricNormal(model.vertices.get(indices.get(0)),
          model.vertices.get(indices.get(1)),model.vertices.get(indices.get(2)));
  Vector4f cameraNormal = MatrixMath.multiply(view, new Vector4f(normal.x(),normal.y(),normal.z(),0));
  return new Vector3f(cameraNormal.x(),cameraNormal.y(),cameraNormal.z());
 }
 private static ClipVertex clip(Matrix4f m,Vector3f v,Vector2f uv){return new ClipVertex(MatrixMath.multiply(m,new Vector4f(v.x(),v.y(),v.z(),1)),uv);} private static ClipVertex clip(Matrix4f m,Vector3f v){return clip(m,v,null);} private static int toArgb(Color c){Color v=c==null?Color.WHITE:c;return ((int)Math.round(v.getOpacity()*255)<<24)|((int)Math.round(v.getRed()*255)<<16)|((int)Math.round(v.getGreen()*255)<<8)|(int)Math.round(v.getBlue()*255);}
}
