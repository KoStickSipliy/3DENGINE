package com.cgengine.render_engine.Rasterization;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TriangleRasterizerTest {
 private static final int RED=0xFFFF0000,BLUE=0xFF0000FF;
 @Test void fillsGeneralFlatAndBorderTriangles(){FrameBuffer b=new FrameBuffer(8,8);TriangleRasterizer.rasterize(b,new RasterVertex(1,1,.5f),new RasterVertex(6,1,.5f),new RasterVertex(2,6,.5f),RED);assertEquals(RED,b.colorAt(2,2));assertEquals(0,b.colorAt(0,0));TriangleRasterizer.rasterize(b,new RasterVertex(-2,0,.4f),new RasterVertex(2,0,.4f),new RasterVertex(0,2,.4f),BLUE);assertEquals(BLUE,b.colorAt(0,0));}
 @Test void skipsDegenerateAndKeepsFirstFragmentAtEqualDepth(){FrameBuffer b=new FrameBuffer(6,6);RasterVertex a=new RasterVertex(1,1,.5f),c=new RasterVertex(5,1,.5f),d=new RasterVertex(1,5,.5f);TriangleRasterizer.rasterize(b,a,c,d,RED);TriangleRasterizer.rasterize(b,a,c,d,BLUE);assertEquals(RED,b.colorAt(2,2));TriangleRasterizer.rasterize(b,new RasterVertex(1,1,.1f),new RasterVertex(2,2,.1f),new RasterVertex(3,3,.1f),BLUE);assertEquals(0,b.colorAt(5,5));}
 @Test void nearestDepthWins(){FrameBuffer b=new FrameBuffer(6,6);RasterVertex a=new RasterVertex(1,1,.8f),c=new RasterVertex(5,1,.8f),d=new RasterVertex(1,5,.8f);TriangleRasterizer.rasterize(b,a,c,d,RED);TriangleRasterizer.rasterize(b,new RasterVertex(1,1,.2f),new RasterVertex(5,1,.2f),new RasterVertex(1,5,.2f),BLUE);assertEquals(BLUE,b.colorAt(2,2));assertEquals(.2f,b.depthAt(2,2),.00001f);}
}
