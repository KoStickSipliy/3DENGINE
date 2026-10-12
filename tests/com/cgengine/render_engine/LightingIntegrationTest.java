package com.cgengine.render_engine;

import com.cgengine.math.*;
import com.cgengine.model.Model;
import com.cgengine.obj_io.reader.ObjReader;
import com.cgengine.render_engine.Rasterization.FrameBuffer;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** CPU regressions for stage 4; no JavaFX window or production lighting oracle. */
class LightingIntegrationTest {
    private static final int SIZE = 160;
    private static Camera camera(float yaw) {
        return new Camera(new Vector2f(yaw, (float)(-Math.PI / 2 + .3)), 6,
                new Vector3f(0,0,0), 1, 1, .1f, 20);
    }
    private static Model load(String path) throws Exception {
        return ObjReader.read(Files.readString(Path.of(path)));
    }
    private static FrameBuffer render(Model model, Camera camera) {
        FrameBuffer frame = new FrameBuffer(SIZE, SIZE);
        RenderEngine.renderFrame(frame, camera, List.of(model));
        return frame;
    }
    private static int[] centroidPixel(Model model, int polygon, Camera camera) {
        var ids = model.polygons.get(polygon).getVertexIndices();
        float x=0,y=0,z=0;
        for (int id : ids) { var v=model.vertices.get(id); x+=v.x()/3; y+=v.y()/3; z+=v.z()/3; }
        Vector4f clip=MatrixMath.multiply(MatrixMath.multiply(camera.getProjectionMatrix(),camera.getViewMatrix()),new Vector4f(x,y,z,1));
        return new int[]{(int)(clip.x()/clip.w()*SIZE+SIZE/2), (int)(-clip.y()/clip.w()*SIZE+SIZE/2)};
    }
    @Test void outwardCubeHasDiffuseAtInteriorPointsBeforeAndAfterHalfOrbit() throws Exception {
        Model cube=load("tests/fixtures/baseline/cube-diagonal-ac.obj");
        for (int orbit=0; orbit<2; orbit++) {
            Camera camera=camera((float)(Math.PI+.4+orbit*Math.PI));
            FrameBuffer frame=render(cube,camera);
            // +Z front at first view, -Z front after 180 degrees; both triangles sampled away from edges.
            for (int polygon : orbit==0 ? new int[]{2,3} : new int[]{0,1}) {
                int[] pixel=centroidPixel(cube,polygon,camera);
                int color=frame.colorAt(pixel[0],pixel[1]);
                assertEquals(255,color>>>24);
                assertTrue((color&255)>100,"Visible cube interior must receive diffuse, not ambient 38: "+(color&255));
            }
            assertArrayEquals(frame.colors(),render(cube,camera).colors(),"Unchanged frame must be deterministic");
        }
    }
    @Test void simpleOutwardModelsHaveLitInteriorAndDeterministicChannels() throws Exception {
        for (String name : List.of("cube","octahedron","icosahedron")) {
            Model model=load("Test_Models/"+name+".obj");
            Camera camera=camera((float)(Math.PI+.4));
            FrameBuffer frame=render(model,camera);
            long lit=Arrays.stream(frame.colors()).filter(c->(c&255)>100).count();
            assertTrue(lit>100,name+" must have a diffuse region rather than an ambient silhouette");
            for (int c : frame.colors()) if(c!=0) {
                assertEquals(255,c>>>24); assertEquals(c&255,(c>>>8)&255); assertEquals(c&255,(c>>>16)&255);
            }
            assertArrayEquals(frame.colors(),render(model,camera).colors(),name);
        }
    }
    @Test void tiltedTextureUsesIndependentLambertAndPerspectiveUvOracle() {
        Model model=ObjReader.read("v -1 -1 0\nv 1 -1 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 0.5 1\nf 1/1 2/2 3/3");
        int[] texels={0x80A06030,0xC03090E0,0xFF204080,0xFF80C020};
        model.setTexture(new Texture2D(2,2,texels));
        Camera camera=camera((float)(Math.PI+.4));
        FrameBuffer frame=render(model,camera);
        double factor=.15+.85*Math.cos(.4)*Math.cos(.3); // World +Z normal, camera on +Z side.
        double[][] projected=new double[3][3];
        var mvp=MatrixMath.multiply(camera.getProjectionMatrix(),camera.getViewMatrix());
        for(int i=0;i<3;i++) {
            var v=model.vertices.get(i); var p=MatrixMath.multiply(mvp,new Vector4f(v.x(),v.y(),v.z(),1));
            projected[i]=new double[]{p.x()/p.w()*SIZE+SIZE/2,-p.y()/p.w()*SIZE+SIZE/2,1/p.w()};
        }
        Set<Integer> sampled=new HashSet<>(); int checked=0;
        for(int y=0;y<SIZE;y++) for(int x=0;x<SIZE;x++) {
            double[] a=projected[0],b=projected[1],c=projected[2];
            double det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1]);
            double w0=((b[1]-c[1])*(x+.5-c[0])+(c[0]-b[0])*(y+.5-c[1]))/det;
            double w1=((c[1]-a[1])*(x+.5-c[0])+(a[0]-c[0])*(y+.5-c[1]))/det;
            double w2=1-w0-w1;
            if(Math.min(w0,Math.min(w1,w2))<.12) continue;
            double reciprocal=w0*a[2]+w1*b[2]+w2*c[2];
            double u=(w1*b[2]+.5*w2*c[2])/reciprocal, v=w2*c[2]/reciprocal;
            if(Math.abs(u-.5)<.03 || Math.abs(v-.5)<.03) continue; // Avoid sampling boundaries.
            int index=(v>.5 ? 0 : 2)+(u>.5 ? 1 : 0), source=texels[index];
            int expected=0xFF000000|((int)Math.round(((source>>>16)&255)*factor)<<16)
                    |((int)Math.round(((source>>>8)&255)*factor)<<8)|(int)Math.round((source&255)*factor);
            assertEquals(expected,frame.colorAt(x,y),"Texture/light at "+x+","+y);
            sampled.add(index); checked++;
        }
        assertTrue(checked>100); assertEquals(Set.of(0,1,2,3),sampled);
        assertArrayEquals(frame.colors(),render(model,camera).colors());
    }
}
