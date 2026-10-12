package com.cgengine.render_engine;
import com.cgengine.math.Vector2f;
import com.cgengine.math.Vector3f;
import com.cgengine.model.Model;
import com.cgengine.model.Polygon;
import com.cgengine.render_engine.Rasterization.FrameBuffer;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class TextureSamplerTest {
 @Test void samplesKnown2x2TextureAndClampsUv(){Texture2D texture=new Texture2D(2,2,new int[]{0xFFFF0000,0xFF00FF00,0xFF0000FF,0xFFFFFFFF});assertEquals(0xFF0000FF,TextureSampler.sample(texture,0,0));assertEquals(0xFF00FF00,TextureSampler.sample(texture,1,1));assertEquals(0xFFFF0000,TextureSampler.sample(texture,-10,10));assertEquals(0xFFFFFFFF,TextureSampler.sample(texture,10,-10));}
 @Test void rejectsInvalidImageData(){assertThrows(IllegalArgumentException.class,()->new Texture2D(0,1,new int[0]));}
 @Test void textureIsUsedBeforeLightingAndMissingUvFallsBackToBaseColor(){Model model=new Model();model.vertices.addAll(List.of(new Vector3f(-.4f,-.3f,0),new Vector3f(.4f,-.3f,0),new Vector3f(0,.4f,0)));model.textureVertices.addAll(List.of(new Vector2f(0,0),new Vector2f(0,0),new Vector2f(0,0)));Polygon polygon=new Polygon();polygon.setVertexIndices(new ArrayList<>(List.of(0,1,2)));polygon.setTextureVertexIndices(new ArrayList<>(List.of(0,1,2)));polygon.setBaseColor(ColorRgb.RED);model.polygons.add(polygon);model.computeNormals();model.setTexture(new Texture2D(2,2,new int[]{0xFFFF0000,0xFF00FF00,0xFF0000FF,0xFFFFFFFF}));FrameBuffer frame=new FrameBuffer(80,80);Camera camera=new Camera(new Vector2f(0,(float)Math.PI/2),5,new Vector3f(0,0,0),1,1,.1f,10);RenderEngine.renderFrame(frame,camera,List.of(model));int textured=0xFF0000FF;assertEquals(textured,frame.colorAt(40,40));polygon.setTextureVertexIndices(new ArrayList<>());RenderEngine.renderFrame(frame,camera,List.of(model));assertEquals(0xFFFF0000,frame.colorAt(40,40));}
 @Test void missingNormalsUseAmbientOnly() {
  Model model = com.cgengine.obj_io.reader.ObjReader.read("v -1 -1 0\nv 1 -1 0\nv 0 1 0\nf 1 2 3");
  model.polygons.getFirst().setNormalIndices(new ArrayList<>());
  model.normals.clear();
  FrameBuffer frame = new FrameBuffer(64,64);
  Camera camera = new Camera(new Vector2f((float)Math.PI,(float)Math.PI/2),5,new Vector3f(0,0,0),1,1,.1f,10);
  RenderEngine.renderFrame(frame,camera,List.of(model));
  assertEquals(0xFF262626,frame.colorAt(32,32));
 }
}
