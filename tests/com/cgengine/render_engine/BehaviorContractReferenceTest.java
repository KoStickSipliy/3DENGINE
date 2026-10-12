package com.cgengine.render_engine;

import com.cgengine.math.*;
import com.cgengine.model.Model;
import com.cgengine.obj_io.reader.ObjReader;
import com.cgengine.obj_io.writer.ObjWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Stage-2 reference oracles; does NOT assert that production light/motion was fixed. */
class BehaviorContractReferenceTest {
    @TempDir Path output;
    private static final double EPS = 1e-4;

    @Test void proposedNegativeZLightMatchesIndependentLambertAndArgbConstants() {
        Light proposed = new Light(new Vector3f(0, 0, -1), .15f);
        assertEquals(1, proposed.brightness(new Vector3f(0, 0, -1)), 1e-5);
        assertEquals(1, proposed.brightness(new Vector3f(0, 0, -7)), 1e-5);
        for (Vector3f n : Arrays.asList(new Vector3f(0,0,1), new Vector3f(1,0,0),
                new Vector3f(0,0,0), null, new Vector3f(Float.NaN,0,0)))
            assertEquals(.15, proposed.brightness(n), 1e-5);
        assertEquals(.575, proposed.brightness(new Vector3f((float)(Math.sqrt(3)/2),0,-.5f)), 1e-5);
        assertEquals(0x80A06030, proposed.shade(0x80A06030, new Vector3f(0,0,-1)));
        assertEquals(0x80180E07, proposed.shade(0x80A06030, new Vector3f(0,0,1)));
        assertEquals(0xFF939393, proposed.shade(0xFFFFFFFF, new Vector3f((float)(Math.sqrt(3)/2),0,-.5f)));
    }

    @Test void preservedLookAtTransformsOutwardWorldNormalTowardProposedLight() {
        Matrix4f view = GraphicConveyor.lookAt(new Vector3f(0,0,10), new Vector3f(0,0,0));
        Vector4f point = MatrixMath.multiply(view, new Vector4f(1,0,0,1));
        assertEquals(-1, point.x(), EPS); assertEquals(0, point.y(), EPS); assertEquals(10, point.z(), EPS);
        Vector4f normal = MatrixMath.multiply(view, new Vector4f(0,0,1,0));
        assertEquals(0, normal.x(), EPS); assertEquals(0, normal.y(), EPS); assertEquals(-1, normal.z(), EPS);
        Light proposed = new Light(new Vector3f(0,0,-1), .15f);
        assertEquals(1, proposed.brightness(new Vector3f(normal.x(),normal.y(),normal.z())), 1e-5);
        assertEquals(.15, proposed.brightness(new Vector3f(0,0,1)), 1e-5);
    }

    @Test void explicitCameraBasesAndScreenPanSignPreserveComp003() {
        double c = Math.sqrt(.5);
        double[][] cases = {
                {Math.PI,-Math.PI/2, -1,0,0, 0,1,0, 0,0,-1},
                {0,Math.PI/2, 1,0,0, 0,-1,0, 0,0,-1},
                {Math.PI/2,Math.PI/2, 0,0,-1, 0,-1,0, -1,0,0},
                {0,Math.PI/4, 1,0,0, 0,-c,c, 0,-c,-c}
        };
        for (double[] reference : cases) {
            Vector3f target = new Vector3f(1,2,3);
            Camera camera = new Camera(new Vector2f((float)reference[0],(float)reference[1]),10,target,1,2,.01f,100);
            Matrix4f view = camera.getViewMatrix();
            for (int row=0; row<3; row++) for (int col=0; col<3; col++)
                assertEquals(reference[2+row*3+col],view.get(row,col),EPS);
            Vector3f eye = camera.getPosition();
            camera.setTarget(new Vector3f((float)(target.x()+reference[2]*.5),
                    (float)(target.y()+reference[3]*.5),(float)(target.z()+reference[4]*.5)));
            assertEquals(eye.x()+reference[2]*.5,camera.getPosition().x(),EPS);
            assertEquals(eye.y()+reference[3]*.5,camera.getPosition().y(),EPS);
            assertEquals(eye.z()+reference[4]*.5,camera.getPosition().z(),EPS);
            Vector4f moved = MatrixMath.multiply(camera.getViewMatrix(),new Vector4f(target.x(),target.y(),target.z(),1));
            Vector3f ndc = GraphicConveyor.multiplyMatrix4ByVector3(camera.getProjectionMatrix(),new Vector3f(moved.x(),moved.y(),moved.z()));
            Vector2f screen = GraphicConveyor.vertexToVec2(ndc,1000,500);
            // Explicit projection/mapping formula, independent of production screen-right helper.
            assertEquals(500-1000*(1/Math.tan(.5))/2*.5/10,screen.x(),.001);
            assertEquals(250,screen.y(),.001);
            assertEquals(10,camera.getDistance(),EPS);
        }
    }

    @Test void bothCubeDiagonalsHaveSameOutwardPlaneNormalsAndSurface() throws Exception {
        Model ac = load("tests/fixtures/baseline/cube-diagonal-ac.obj");
        Model bd = load("tests/fixtures/baseline/cube-diagonal-bd.obj");
        assertEquals(ac.vertices,bd.vertices);
        Map<String,Integer> expected = Map.of("-1,0,0",2,"1,0,0",2,"0,-1,0",2,"0,1,0",2,"0,0,-1",2,"0,0,1",2);
        for (Model m : List.of(ac,bd)) {
            Map<String,Integer> normals = new HashMap<>();
            for (var p : m.polygons) {
                var ids = p.getVertexIndices(); var a=m.vertices.get(ids.get(0)); var b=m.vertices.get(ids.get(1)); var d=m.vertices.get(ids.get(2));
                double x=(b.y()-a.y())*(d.z()-a.z())-(b.z()-a.z())*(d.y()-a.y());
                double y=(b.z()-a.z())*(d.x()-a.x())-(b.x()-a.x())*(d.z()-a.z());
                double z=(b.x()-a.x())*(d.y()-a.y())-(b.y()-a.y())*(d.x()-a.x());
                double length=Math.sqrt(x*x+y*y+z*z);
                assertEquals(4,length,EPS); // Each triangle has area 2, independently of diagonal.
                normals.merge(Math.round(x/length)+","+Math.round(y/length)+","+Math.round(z/length),1,Integer::sum);
                assertTrue(x*a.x()+y*a.y()+z*a.z()>0,"Outward winding about origin");
            }
            assertEquals(expected,normals);
        }
    }

    @Test void reversedTetrahedronAndObjRoundTripsKeepCoordinatesAndComponents() throws Exception {
        Model inward=load("Test_Models/tetrahedron.obj"), outward=load("tests/fixtures/baseline/tetrahedron-outward.obj");
        assertEquals(inward.vertices,outward.vertices);
        assertEquals(-8.0/3,volume(inward),EPS); assertEquals(8.0/3,volume(outward),EPS);
        for (String name : List.of("asymmetric-single","asymmetric-pair")) {
            Model before=load("tests/fixtures/baseline/"+name+".obj");
            int expected=name.endsWith("pair") ? 2 : 1;
            assertEquals(Collections.nCopies(expected,4),componentSizes(before));
            Path file=output.resolve(name+".obj"); new ObjWriter().write(before,file.toString());
            Model after=ObjReader.read(Files.readString(file));
            assertEquals(before.vertices,after.vertices); assertEquals(before.polygons,after.polygons);
            assertEquals(Collections.nCopies(expected,4),componentSizes(after));
        }
    }

    @Test void finiteSmoothingAndNormalizedSpeedHaveIndependentTimeOracles() {
        for (int fps : List.of(30,60)) {
            double area=0;
            for (int i=0;i<fps;i++) area+=stepArea((i+1.0)/fps)-stepArea((double)i/fps);
            assertEquals(.940,area,1e-12);
            assertEquals(.329,.35*area,1e-12);
            assertEquals(Math.exp(-.658),Math.exp(-.70*area),1e-12);
        }
        assertEquals(1,stepArea(1.12)-stepArea(.12),1e-12); // Release at one second, full tail.
        assertEquals(0,stepArea(2.12)-2*stepArea(1.12)+stepArea(.12),1e-12); // W then S then stop.
        for (double distance : List.of(.05,5.0,500.0)) {
            double f=1/Math.tan(.5), aspect=16.0/9;
            double velocity=.35*distance*aspect*Math.tan(.5);
            assertEquals(-.35,-f/aspect*velocity/distance,1e-12);
        }
    }
    private static double stepArea(double t) { return t<=0 ? 0 : t<.12 ? t*t/(2*.12) : t-.12/2; }
    private static Model load(String path) throws Exception { return ObjReader.read(Files.readString(Path.of(path))); }
    private static double volume(Model m) {
        double total=0;
        for(var p:m.polygons) {var i=p.getVertexIndices();var a=m.vertices.get(i.get(0));var b=m.vertices.get(i.get(1));var c=m.vertices.get(i.get(2));
            total+=(a.x()*(b.y()*c.z()-b.z()*c.y())+a.y()*(b.z()*c.x()-b.x()*c.z())+a.z()*(b.x()*c.y()-b.y()*c.x()))/6.0;}
        return total;
    }
    static List<Integer> componentSizes(Model m) {
        List<Set<Integer>> edges=new ArrayList<>();for(int i=0;i<m.vertices.size();i++)edges.add(new HashSet<>());
        for(var p:m.polygons)for(int a:p.getVertexIndices())for(int b:p.getVertexIndices())edges.get(a).add(b);
        Set<Integer> seen=new HashSet<>();List<Integer> sizes=new ArrayList<>();
        for(int i=0;i<edges.size();i++)if(seen.add(i)){int count=0;Deque<Integer> queue=new ArrayDeque<>();queue.add(i);
            while(!queue.isEmpty()){int v=queue.remove();count++;for(int n:edges.get(v))if(seen.add(n))queue.add(n);}sizes.add(count);}
        sizes.sort(Integer::compare);return sizes;
    }
}
