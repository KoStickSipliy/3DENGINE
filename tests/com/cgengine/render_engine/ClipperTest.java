package com.cgengine.render_engine;
import com.cgengine.math.Vector4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClipperTest {
 private static ClipVertex v(float x,float y,float z,float w){return new ClipVertex(new Vector4f(x,y,z,w));}
 @Test void acceptsVisibleAndRejectsInvisibleTriangles(){assertEquals(3,Clipper.clipTriangle(v(0,0,0,1),v(.5f,0,0,1),v(0,.5f,0,1)).size());assertTrue(Clipper.clipTriangle(v(3,0,0,1),v(4,0,0,1),v(3,1,0,1)).isEmpty());}
 @Test void clipsEachFrustumBoundaryAndProducesFiniteVertices(){for(int axis=0;axis<3;axis++)for(int sign:new int[]{-1,1}){float[] q={0,0,0};q[axis]=2*sign;var p=Clipper.clipTriangle(v(q[0],q[1],q[2],1),v(0,0,0,1),v(0,.5f,0,1));assertFalse(p.isEmpty());for(var x:p)assertTrue(Float.isFinite(x.position().w())&&x.position().w()>0);}}
 @Test void zeroWAndCrossingTrianglesAreSafe(){var p=Clipper.clipTriangle(v(0,0,0,0),v(.2f,0,0,1),v(0,.2f,0,1));assertFalse(p.isEmpty());assertTrue(Clipper.triangles(p).size()>=1);}
}
