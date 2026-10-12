package com.cgengine.render_engine.Rasterization;
/** Deterministic pixel-centre barycentric triangle rasterizer. Equal depth keeps the first fragment. */
public final class TriangleRasterizer {
 @FunctionalInterface public interface FragmentShader {int color(float w0,float w1,float w2);}
 private TriangleRasterizer(){} public static void rasterize(FrameBuffer b,RasterVertex a,RasterVertex c,RasterVertex d,int argb){rasterize(b,a,c,d,(w0,w1,w2)->argb);} public static void rasterize(FrameBuffer b,RasterVertex a,RasterVertex c,RasterVertex d,FragmentShader shader){float area=edge(a,c,d.x(),d.y());if(!Float.isFinite(area)||area==0)return;int minX=Math.max(0,(int)Math.floor(Math.min(a.x(),Math.min(c.x(),d.x())))),maxX=Math.min(b.width()-1,(int)Math.ceil(Math.max(a.x(),Math.max(c.x(),d.x()))));int minY=Math.max(0,(int)Math.floor(Math.min(a.y(),Math.min(c.y(),d.y())))),maxY=Math.min(b.height()-1,(int)Math.ceil(Math.max(a.y(),Math.max(c.y(),d.y()))));for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++){float px=x+.5f,py=y+.5f,w0=edge(c,d,px,py)/area,w1=edge(d,a,px,py)/area,w2=edge(a,c,px,py)/area;if(w0>=0&&w1>=0&&w2>=0){float depth=w0*a.depth()+w1*c.depth()+w2*d.depth();if(b.isCloser(x,y,depth))b.writeIfCloser(x,y,depth,shader.color(w0,w1,w2));}}}
 private static float edge(RasterVertex a,RasterVertex b,float x,float y){return (x-a.x())*(b.y()-a.y())-(y-a.y())*(b.x()-a.x());}
}
