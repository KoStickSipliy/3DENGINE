package com.cgengine.obj_io;

import com.cgengine.math.*;
import com.cgengine.model.Model;
import com.cgengine.obj_io.reader.*;
import com.cgengine.obj_io.writer.ObjWriter;
import com.cgengine.render_engine.*;
import com.cgengine.render_engine.Rasterization.FrameBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ObjStage10Test {
    @TempDir Path directory;
    static final String HEAD="v -1 -1 0\nv 1 -1 0\nv 0 1 0\n";
    static String textured(String w) { return HEAD+"vt 0 0"+w+"\nvt 1 0"+w+"\nvt .5 1"+w+"\nf 1/1 2/2 3/3\n"; }

    @Test void thirdTextureComponentIsValidatedButDoesNotChangeUvSamplingOrRoundTrip() throws Exception {
        Model two=ObjReader.read(textured(""));
        for(String w:List.of(" 0"," .75"," -2")) {
            Model three=ObjReader.read(textured(w));
            assertEquals(two.textureVertices,three.textureVertices); assertEquals(two.polygons,three.polygons);
            var texture=new Texture2D(2,2,new int[]{0xFFFF0000,0xFF00FF00,0xFF0000FF,0xFFFFFFFF});
            two.setTexture(texture); three.setTexture(texture);
            Camera camera=new Camera(new Vector2f((float)Math.PI,(float)-Math.PI/2),5,new Vector3f(0,0,0),1,1,.1f,20);
            var a=new FrameBuffer(100,100); var b=new FrameBuffer(100,100);
            RenderEngine.renderFrame(a,camera,List.of(two)); RenderEngine.renderFrame(b,camera,List.of(three));
            assertArrayEquals(a.colors(),b.colors());
            assertTrue(Arrays.stream(b.colors()).anyMatch(c->c!=0));
            Path saved=directory.resolve("uv.obj"); new ObjWriter().write(three,saved.toString());
            assertTrue(Files.readAllLines(saved).stream().filter(s->s.startsWith("vt ")).allMatch(s->s.split(" ").length==3));
            Model back=ObjReader.read(Files.readString(saved)); assertEquals(three.textureVertices,back.textureVertices); assertEquals(three.polygons,back.polygons);
        }
    }
    @Test void invalidThirdComponentAndExtraComponentsHaveLineDiagnostics() {
        for(String w:List.of(" bad"," NaN"," Infinity"," -Infinity"," 0 1")) {
            var e=assertThrows(ObjReaderException.class,()->ObjReader.read(textured(w)));
            assertTrue(e.getMessage().contains("line: 4"),e::getMessage);
        }
    }
    @Test void femaleBodyMinimalFaceRetainsDiagnosticRejection() throws Exception {
        String input=Files.readString(Path.of("tests/fixtures/stage10/female-body-face-51112.obj"));
        var error=assertThrows(ObjReaderException.class,()->ObjReader.read(input));
        assertTrue(error.getMessage().contains("line: 10"),error::getMessage);
        assertTrue(error.getMessage().contains("cannot be triangulated"));
    }
    @Test void femaleFaceIsWarpedAndCrossesInDominantProjectionAwayFromRoundoff() throws Exception {
        double[][] v=Files.readAllLines(Path.of("tests/fixtures/stage10/female-body-face-51112.obj")).stream().filter(s->s.startsWith("v "))
                .map(s->Arrays.stream(s.substring(2).split("\\s+")).mapToDouble(Double::parseDouble).toArray()).toArray(double[][]::new);
        assertEquals(4,Arrays.stream(v).map(Arrays::toString).distinct().count());
        double[] a=sub(v[1],v[0]),b=sub(v[2],v[0]);double[] n={a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};
        double[] d=sub(v[3],v[0]);double distance=Math.abs((n[0]*d[0]+n[1]*d[1]+n[2]*d[2])/Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]));
        assertEquals(.06193124,distance,1e-7); // Significant warping: approximately 10% of this quad's diagonal.
        double[] newell=new double[3];
        for(int i=0;i<4;i++){var p=v[i];var q=v[(i+1)%4];newell[0]+=(p[1]-q[1])*(p[2]+q[2]);newell[1]+=(p[2]-q[2])*(p[0]+q[0]);newell[2]+=(p[0]-q[0])*(p[1]+q[1]);}
        assertTrue(Math.abs(newell[1])>Math.abs(newell[0])&&Math.abs(newell[1])>Math.abs(newell[2]));
        // Dominant Y normal drops Y: in XZ edges (1,2) and (3,0) cross strictly.
        assertTrue(cross(v,1,2,3,2)*cross(v,1,2,0,2)<0);
        assertTrue(cross(v,3,0,1,2)*cross(v,3,0,2,2)<0);
        for(int i=0;i<4;i++)assertTrue(cross(v,i,(i+1)%4,(i+2)%4,1)>0); // XY is convex: changing projection changes interpretation.
    }
    static double[] sub(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    static double cross(double[][] v,int a,int b,int c,int second){return (v[b][0]-v[a][0])*(v[c][second]-v[a][second])-(v[b][second]-v[a][second])*(v[c][0]-v[a][0]);}
    @Test void planarConcavePolygonPreservesAreaWindingAndUvAndInvalidShapesFail() {
        double[][] points={{0,0},{3,0},{3,3},{1,1},{0,3}};
        for(boolean reversed:List.of(false,true)) {
            StringBuilder input=new StringBuilder();
            for(var p:points)input.append("v ").append(p[0]).append(' ').append(p[1]).append(" 0\n");
            for(var p:points)input.append("vt ").append(p[0]).append(' ').append(p[1]).append('\n');
            input.append("f"); for(int i=0;i<5;i++) { int id=reversed?5-i:i+1;input.append(' ').append(id).append('/').append(id); }
            Model model=ObjReader.read(input.toString()); assertEquals(3,model.polygons.size());
            double total=0;
            for(var face:model.polygons) {
                var ids=face.getVertexIndices(); assertEquals(ids,face.getTextureVertexIndices());
                var a=model.vertices.get(ids.get(0));var b=model.vertices.get(ids.get(1));var c=model.vertices.get(ids.get(2));
                double area=((b.x()-a.x())*(c.y()-a.y())-(b.y()-a.y())*(c.x()-a.x()))/2.;
                assertTrue(reversed?area<0:area>0); total+=Math.abs(area);
                // Points sampled throughout each triangle must lie inside the original outline.
                for(int u=1;u<10;u++)for(int v=1;u+v<10;v++) {
                    double x=(u*a.x()+v*b.x()+(10-u-v)*c.x())/10.,y=(u*a.y()+v*b.y()+(10-u-v)*c.y())/10.;
                    assertTrue(inside(points,x,y),"Triangle leaves polygon");
                }
            }
            assertEquals(6,total,1e-6); // Independent shoelace area of the given outline.
        }
        for(String shape:List.of("v 0 0 0\nv 2 2 0\nv 0 2 0\nv 2 0 0\nf 1 2 3 4", "v 0 0 0\nv 1 0 0\nv 2 0 0\nv 3 0 0\nf 1 2 3 4", "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 0 0\nf 1 2 3 4"))assertThrows(ObjReaderException.class,()->ObjReader.read(shape));
    }
    static boolean inside(double[][] polygon,double x,double y) {
        boolean result=false;
        for(int i=0,j=polygon.length-1;i<polygon.length;j=i++) {
            var a=polygon[i];var b=polygon[j];
            if((a[1]>y)!=(b[1]>y)&&x<(b[0]-a[0])*(y-a[1])/(b[1]-a[1])+a[0])result=!result;
        }
        return result;
    }
}
