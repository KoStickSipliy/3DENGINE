package com.cgengine.render_engine;
import com.cgengine.math.*;
import com.cgengine.model.*;
import javafx.scene.canvas.GraphicsContext;
import java.util.List;
import com.cgengine.render_engine.Rasterization.FrameBuffer;
/** Wireframe renderer using the same homogeneous clipping path as filled triangles. */
public final class RenderEngine {
 public interface ModelRenderListener { void onRendered(Model model); void onSkipped(Model model, String reason); }
 private RenderEngine(){} public static void render(GraphicsContext c,Camera camera,Model model,int w,int h){ModelValidator.validate(model);Matrix4f mvp=MatrixMath.multiply(camera.getProjectionMatrix(),camera.getViewMatrix());for(Polygon p:model.polygons){List<ClipVertex> polygon=Clipper.clipTriangle(clip(mvp,model.vertices.get(p.getVertexIndices().get(0))),clip(mvp,model.vertices.get(p.getVertexIndices().get(1))),clip(mvp,model.vertices.get(p.getVertexIndices().get(2))));draw(c,polygon,w,h);}}
 public static void renderFrame(GraphicsContext context,Camera camera,List<Model> models,int width,int height){FrameBuffer buffer=new FrameBuffer(width,height);renderFrame(buffer,camera,models);PolygonFiller.publish(context,buffer);}
 /** Renders into caller-owned storage so a UI frame does not allocate a depth/color buffer. */
 public static void renderFrame(FrameBuffer buffer,Camera camera,List<Model> models){renderFrame(buffer,camera,models,null);}
 /** The listener receives model-level validation results without letting one bad model stop its scene peers. */
 public static void renderFrame(FrameBuffer buffer,Camera camera,List<Model> models,ModelRenderListener listener){buffer.clear(0);for(Model model:models){try{PolygonFiller.fill(buffer,camera,model);if(listener!=null)listener.onRendered(model);}catch(ModelValidationException invalidModel){if(listener!=null)listener.onSkipped(model,invalidModel.getMessage());}}}
 private static void draw(GraphicsContext c,List<ClipVertex> polygon,int w,int h){if(polygon.size()<2)return;try{for(int i=0;i<polygon.size();i++){Vector3f a=MatrixMath.perspectiveDivide(polygon.get(i).position()),b=MatrixMath.perspectiveDivide(polygon.get((i+1)%polygon.size()).position());Vector2f pa=GraphicConveyor.vertexToVec2(a,w,h),pb=GraphicConveyor.vertexToVec2(b,w,h);c.strokeLine(pa.x(),pa.y(),pb.x(),pb.y());}}catch(IllegalArgumentException ignored){}}
 private static ClipVertex clip(Matrix4f m,Vector3f v){return new ClipVertex(MatrixMath.multiply(m,new Vector4f(v.x(),v.y(),v.z(),1)));}
}
