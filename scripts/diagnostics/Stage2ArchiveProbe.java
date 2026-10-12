package com.cgengine;

import com.cgengine.math.*;
import com.cgengine.model.Model;
import com.cgengine.obj_io.reader.ObjReader;
import com.cgengine.obj_io.writer.ObjWriter;
import com.cgengine.render_engine.*;
import java.nio.file.*;
import java.util.*;

/** Local optional geometry/scale references, without UI or motion implementation. */
public final class Stage2ArchiveProbe {
    static int root(int[] parents,int v) { while(parents[v]!=v){parents[v]=parents[parents[v]];v=parents[v];}return v; }
    static List<Integer> sizes(Model m) {
        int[] parents=new int[m.vertices.size()];for(int i=0;i<parents.length;i++)parents[i]=i;
        for(var p:m.polygons)for(int v:p.getVertexIndices())parents[root(parents,v)]=root(parents,p.getVertexIndices().getFirst());
        Map<Integer,Integer> counts=new HashMap<>();for(int i=0;i<parents.length;i++)counts.merge(root(parents,i),1,Integer::sum);
        return counts.values().stream().sorted().toList();
    }
    static Vector2f project(Camera camera,Vector3f p) {
        Vector4f view=MatrixMath.multiply(camera.getViewMatrix(),new Vector4f(p.x(),p.y(),p.z(),1));
        Vector3f ndc=GraphicConveyor.multiplyMatrix4ByVector3(camera.getProjectionMatrix(),new Vector3f(view.x(),view.y(),view.z()));
        return GraphicConveyor.vertexToVec2(ndc,1210,681);
    }
    static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path output=Path.of(args[0]);Files.createDirectories(output);
        for(String path:List.of("3DModels_DELETE/eo53jaydu328-Rigged_Hand/Rigged_Hand/Rigged_Hand_obj/Rigged Hand.obj", "3DModels_DELETE/Faceform/WrapBody.obj")) {
            Path input=Path.of(path);
            if(!Files.exists(input)){System.out.println("MISSING " + path + "; not verified");continue;}
            Model before=ObjReader.read(Files.readString(input));
            List<Integer> components=sizes(before);
            if(path.contains("Rigged Hand")) {
                check(before.vertices.size()==1086 && before.polygons.size()==2072,"Hand counts");
                check(components.equals(List.of(543,543)),"Hand components");
            }
            Path saved=output.resolve(input.getFileName());new ObjWriter().write(before,saved.toString());
            Model after=ObjReader.read(Files.readString(saved));
            check(before.vertices.equals(after.vertices),"Coordinates changed");
            check(before.polygons.equals(after.polygons),"Topology/indices changed");
            check(components.equals(sizes(after)),"Components changed");
            float aspect=1210f/681;
            Camera camera=ModelFraming.calculate(before,1,aspect).createCamera(new Vector2f((float)Math.PI,(float)-Math.PI/2),1,aspect);
            Vector3f target=camera.getTarget();float distance=camera.getDistance();
            double rightSpeed=.35*distance*aspect*Math.tan(.5),upSpeed=.35*distance*Math.tan(.5);
            Vector2f initial=project(camera,target);
            camera.setTarget(new Vector3f(target.x()-(float)rightSpeed,target.y(),target.z()));
            Vector2f horizontal=project(camera,target);
            double fraction=(horizontal.x()-initial.x())/1210.0;
            check(Math.abs(fraction+.35)<1e-4,"Pan fraction/sign mismatch");
            camera.setTarget(new Vector3f(target.x(),target.y()+(float)upSpeed,target.z()));
            Vector2f vertical=project(camera,target);
            double upFraction=(vertical.y()-initial.y())/681.0;
            check(Math.abs(upFraction-.35)<1e-4,"Up fraction/sign mismatch");
            check(Math.abs(camera.getDistance()-distance)<1e-5,"Pan changed distance");
            check(before.vertices.equals(after.vertices),"Motion changed geometry");
            System.out.printf(Locale.ROOT,"PASS %s vertices=%d triangles=%d components=%s distance=%.9f rightUnitsPerSecond=%.9f upUnitsPerSecond=%.9f imageDxFraction=%.9f imageDyFraction=%.9f roundTrip=exact%n",
                    path,before.vertices.size(),before.polygons.size(),components,distance,rightSpeed,upSpeed,fraction,upFraction);
        }
    }
}
