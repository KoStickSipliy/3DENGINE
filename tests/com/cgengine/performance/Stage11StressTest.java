package com.cgengine.performance;

import com.cgengine.math.*;
import com.cgengine.model.*;
import com.cgengine.render_engine.*;
import com.cgengine.render_engine.Rasterization.FrameBuffer;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Additional workloads, reported separately from the small-triangle D-01 scene. */
class Stage11StressTest {
    @Test void largeTrianglesOverdrawTextureAndMultipleModels() {
        for (String name : List.of("large", "overdraw", "texture-multiple")) {
            Model model = new Model();
            int count = name.equals("overdraw") ? 64 : 2;
            for(int i=0;i<count;i++) {
                int base=model.vertices.size(); float z=-i*.001f;
                model.vertices.addAll(List.of(new Vector3f(-2,-1.5f,z),new Vector3f(2,-1.5f,z),new Vector3f(0,1.5f,z)));
                Polygon p=new Polygon();p.setVertexIndices(new ArrayList<>(List.of(base,base+1,base+2)));
                p.setTextureVertexIndices(new ArrayList<>(List.of(0,1,2)));model.polygons.add(p);
            }
            model.textureVertices.addAll(List.of(new Vector2f(0,0),new Vector2f(1,0),new Vector2f(.5f,1)));model.computeNormals();
            if(name.equals("texture-multiple"))model.setTexture(new Texture2D(2,2,new int[]{0xFFFF0000,0xFF00FF00,0xFF0000FF,0xFFFFFFFF}));
            List<Model> models=name.equals("texture-multiple")?List.of(model,model.clone(),model.clone()):List.of(model);
            Camera camera=new Camera(new Vector2f(0,(float)Math.PI/2),3,new Vector3f(0,0,0),1,1280f/720,.1f,100);
            var frame=new FrameBuffer(1280,720);
            for(int i=0;i<30;i++)RenderEngine.renderFrame(frame,camera,models);
            var metrics=new RenderMetrics(60);
            for(int i=0;i<60;i++){long start=System.nanoTime();RenderEngine.renderFrame(frame,camera,models);metrics.recordNanos(System.nanoTime()-start);}
            assertTrue(Arrays.stream(frame.colors()).filter(c->c!=0).count()>100_000);
            System.out.printf(Locale.ROOT,"Stage11 stress %s triangles=%d models=%d mean=%.3fms p95=%.3fms max=%.3fms%n",name,count,models.size(),metrics.meanMillis(),metrics.p95Millis(),metrics.maxMillis());
        }
    }
}
