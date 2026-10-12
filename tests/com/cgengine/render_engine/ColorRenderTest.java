package com.cgengine.render_engine;
import com.cgengine.math.*;
import com.cgengine.model.*;
import com.cgengine.render_engine.Rasterization.FrameBuffer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ColorRenderTest {
 private static Model triangle(float x,ColorRgb color){Model m=new Model();m.vertices.addAll(List.of(new Vector3f(x-.4f,-.3f,0),new Vector3f(x+.4f,-.3f,0),new Vector3f(x,.4f,0)));Polygon p=new Polygon();p.setVertexIndices(new ArrayList<>(List.of(0,1,2)));p.setBaseColor(color);m.polygons.add(p);m.computeNormals();return m;}
 @Test void polygonColorsAreIndependentAndFrameHasBoth(){Model a=triangle(-.7f,ColorRgb.RED),b=triangle(.7f,ColorRgb.BLUE);a.polygons.getFirst().setBaseColor(ColorRgb.YELLOW);assertEquals(ColorRgb.BLUE,b.polygons.getFirst().getBaseColor());FrameBuffer f=new FrameBuffer(100,100);Camera c=new Camera(new Vector2f(0,(float)Math.PI/2),5,new Vector3f(0,0,0),1,1,.1f,10);RenderEngine.renderFrame(f,c,List.of(a,b));int yellow=0xFFFFFF00;int blue=0xFF0000FF;boolean hasYellow=false,hasBlue=false;for(int y=0;y<100;y++)for(int x=0;x<100;x++){hasYellow|=f.colorAt(x,y)==yellow;hasBlue|=f.colorAt(x,y)==blue;}assertTrue(hasYellow&&hasBlue);}
 @Test void clonedPolygonRetainsOwnColor(){Model m=triangle(0,ColorRgb.RED);Model copy=m.clone();copy.polygons.getFirst().setBaseColor(ColorRgb.BLACK);assertEquals(ColorRgb.RED,m.polygons.getFirst().getBaseColor());}
}
