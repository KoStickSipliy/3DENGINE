package com.cgengine.render_engine;

import com.cgengine.math.*;
import com.cgengine.math.affine.*;
import com.cgengine.model.*;
import com.cgengine.obj_io.reader.ObjReader;
import com.cgengine.obj_io.writer.ObjWriter;
import com.cgengine.VertexDelete.Eraser;
import com.cgengine.render_engine.Rasterization.FrameBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FlatShadingTest {
    @TempDir Path output;
    private static Model cube(String diagonal) throws Exception {
        return ObjReader.read(Files.readString(Path.of("tests/fixtures/baseline/cube-diagonal-"+diagonal+".obj")));
    }
    private static Camera camera() {
        return new Camera(new Vector2f((float)(Math.PI+.4),(float)(-Math.PI/2+.3)),8,
                new Vector3f(0,0,0),1,1,.1f,40);
    }
    private static FrameBuffer render(Model m) {
        FrameBuffer f=new FrameBuffer(240,240); RenderEngine.renderFrame(f,camera(),List.of(m)); return f;
    }
    private static int at(Model m,int face,FrameBuffer f) {
        double x=0,y=0,z=0;
        for(int i:m.polygons.get(face).getVertexIndices()) {var v=m.vertices.get(i); x+=v.x()/3.0;y+=v.y()/3.0;z+=v.z()/3.0;}
        var p=MatrixMath.multiply(MatrixMath.multiply(camera().getProjectionMatrix(),camera().getViewMatrix()),
                new Vector4f((float)x,(float)y,(float)z,1));
        return f.colorAt((int)(p.x()/p.w()*240+120),(int)(-p.y()/p.w()*240+120));
    }
    private static int white(double cosine) {
        int c=(int)Math.round(255*(.15+.85*Math.max(0,cosine))); return 0xFF000000|(c<<16)|(c<<8)|c;
    }
    @Test void twoHalvesOfOnePlaneHaveNoLightingSeam() throws Exception {
        Model m=cube("ac");FrameBuffer f=render(m);
        assertEquals(at(m,2,f),at(m,3,f),"Two halves of the +Z plane must have the same brightness");
    }
    @Test void bothHalvesAndBothDiagonalsMatchKnownPlaneLambert() throws Exception {
        // Visible outward planes: +Z, +X, +Y. Oracles from camera angles, no production normal/light helper.
        Map<Integer,Double> planes=Map.of(2,Math.cos(.4)*Math.cos(.3),6,Math.sin(.4)*Math.cos(.3),8,Math.sin(.3));
        for(String diagonal:List.of("ac","bd")) {
            Model m=cube(diagonal); FrameBuffer f=render(m);
            for(var plane:planes.entrySet()) for(int face:List.of(plane.getKey(),plane.getKey()+1))
                assertEquals(white(plane.getValue()),at(m,face,f),diagonal+" face "+face);
        }
    }
    @Test void changingDiagonalPreservesWholeInteriorImage() throws Exception {
        FrameBuffer ac=render(cube("ac")),bd=render(cube("bd")); int checked=0;
        for(int y=2;y<238;y++) for(int x=2;x<238;x++) {
            int c=ac.colorAt(x,y); if(c==0)continue;
            boolean interior=true;
            for(int dy=-2;dy<=2;dy++) for(int dx=-2;dx<=2;dx++)
                interior &= ac.colorAt(x+dx,y+dy)==c && bd.colorAt(x+dx,y+dy)==bd.colorAt(x,y);
            if(interior){assertEquals(c,bd.colorAt(x,y));checked++;}
        }
        assertTrue(checked>500);
    }
    private static void assertGeometricNormals(Model m) {
        ModelValidator.validate(m);
        for(var p:m.polygons) {
            var ids=p.getVertexIndices(); var a=m.vertices.get(ids.get(0));var b=m.vertices.get(ids.get(1));var c=m.vertices.get(ids.get(2));
            double ex=b.x()-a.x(),ey=b.y()-a.y(),ez=b.z()-a.z(),fx=c.x()-a.x(),fy=c.y()-a.y(),fz=c.z()-a.z();
            double nx=ey*fz-ez*fy,ny=ez*fx-ex*fz,nz=ex*fy-ey*fx,l=Math.sqrt(nx*nx+ny*ny+nz*nz);
            for(int id:p.getNormalIndices()) {var n=m.normals.get(id);
                assertEquals(nx/l,n.x(),1e-5);assertEquals(ny/l,n.y(),1e-5);assertEquals(nz/l,n.z(),1e-5);
                assertEquals(0,n.x()*ex+n.y()*ey+n.z()*ez,1e-4);assertEquals(0,n.x()*fx+n.y()*fy+n.z()*fz,1e-4);
            }
        }
    }
    @Test void rotatedNonuniformScaleHasPerpendicularNormalsAndResetRestoresFrame() throws Exception {
        Model m=cube("ac"); List<Vector3f> original=new ArrayList<>(m.vertices); int[] before=render(m).colors().clone();
        m.applyTransformation(new Transformation(Rotator.degrees(31,Rotator.Axis.Y),new Scaling(1.2f,.7f,1.4f)));
        assertGeometricNormals(m); assertFalse(Arrays.equals(before,render(m).colors()));
        m.resetTransformation(); assertEquals(original,m.vertices); assertGeometricNormals(m);
        assertArrayEquals(before,render(m).colors());
    }
    @Test void deletingVertexOrFaceKeepsRemainingNormalsAndRoundTripValid() throws Exception {
        for(boolean vertex:List.of(true,false)) {
            Model m=cube("ac");
            if(vertex)m=Eraser.vertexDelete(m,List.of(6),false,false,false,false); else m.polygons.remove(2);
            assertEquals(vertex?7:8,m.vertices.size()); assertEquals(vertex?7:11,m.polygons.size());
            assertGeometricNormals(m); FrameBuffer before=render(m);
            Path file=output.resolve(vertex?"vertex.obj":"face.obj");new ObjWriter().write(m,file.toString());
            Model reloaded=ObjReader.read(Files.readString(file));assertEquals(m.vertices,reloaded.vertices);
            assertGeometricNormals(reloaded);assertArrayEquals(before.colors(),render(reloaded).colors());
        }
    }
    @Test void missingOrAnyZeroCornerNormalStaysAmbientWithoutRegeneration() {
        for(int mode=0;mode<3;mode++) {
            Model m=ObjReader.read("v -1 -1 0\nv 1 -1 0\nv 0 1 0\nf 1 2 3");
            if(mode==0){m.normals.clear();m.polygons.getFirst().getNormalIndices().clear();}
            else {m.normals=new ArrayList<>(List.of(new Vector3f(0,0,1),new Vector3f(0,0,0),new Vector3f(0,0,1)));
                m.polygons.getFirst().setNormalIndices(new ArrayList<>(List.of(0,1,2)));}
            if(mode==2)m.normals.set(1,new Vector3f(0,0,1)); // Nonzero supplied normals still use geometry.
            var normals=new ArrayList<>(m.normals);var indices=new ArrayList<>(m.polygons.getFirst().getNormalIndices());
            FrameBuffer f=render(m);assertEquals(mode==2?white(Math.cos(.4)*Math.cos(.3)):0xFF262626,f.colorAt(120,120));
            assertEquals(normals,m.normals);assertEquals(indices,m.polygons.getFirst().getNormalIndices());
        }
    }
    @Test void suppliedNonzeroNormalsDoNotOverrideGeometryAndInvalidIndicesAreRejected() {
        Model m=ObjReader.read("v -1 -1 0\nv 1 -1 0\nv 0 1 0\nf 1 2 3");
        m.normals.replaceAll(n->new Vector3f(1,0,0));
        assertEquals(white(Math.cos(.4)*Math.cos(.3)),render(m).colorAt(120,120));
        m.polygons.getFirst().getNormalIndices().set(0,99);assertThrows(ModelValidationException.class,()->ModelValidator.validate(m));
    }
    @Test void inwardTetrahedronKeepsWindingAndHasAmbientVisibleFaces() throws Exception {
        Model inward=ObjReader.read(Files.readString(Path.of("Test_Models/tetrahedron.obj")));
        Model outward=ObjReader.read(Files.readString(Path.of("tests/fixtures/baseline/tetrahedron-outward.obj")));
        assertEquals(inward.vertices,outward.vertices);
        var original=new ArrayList<>(inward.polygons.stream().map(p->List.copyOf(p.getVertexIndices())).toList());
        FrameBuffer a=render(inward),b=render(outward);int checked=0;
        for(int i=0;i<a.colors().length;i++)if(a.colors()[i]!=0){assertEquals(0xFF262626,a.colors()[i]);if((b.colors()[i]&255)>100)checked++;}
        assertTrue(checked>100);assertEquals(original,inward.polygons.stream().map(p->List.copyOf(p.getVertexIndices())).toList());
    }
    @Test void degenerateTriangleHasFiniteZeroNormalsAndWritesNoPixels() {
        Model m=ObjReader.read("v -1 0 0\nv 0 0 0\nv 1 0 0\nf 1 2 3");
        for(var n:m.normals)assertEquals(new Vector3f(0,0,0),n);
        assertTrue(Arrays.stream(render(m).colors()).allMatch(c->c==0));
    }
}
