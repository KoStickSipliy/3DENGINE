package com.cgengine.render_engine.Rasterization;
import java.util.Arrays;
/** Local color and depth storage for one complete render frame. */
public final class FrameBuffer {
 private final int width,height; private final int[] colors; private final float[] depths;
 public FrameBuffer(int width,int height){if(width<=0||height<=0)throw new IllegalArgumentException("FrameBuffer dimensions must be positive");this.width=width;this.height=height;colors=new int[width*height];depths=new float[width*height];clear(0);}
 public int width(){return width;} public int height(){return height;}
 /** Clears an existing frame; storage is deliberately retained between frames. */
 public void clear(int argb){Arrays.fill(colors,argb);Arrays.fill(depths,Float.POSITIVE_INFINITY);}
 public boolean writeIfCloser(int x,int y,float depth,int argb){if(x<0||x>=width||y<0||y>=height||!Float.isFinite(depth))return false;int i=y*width+x;if(depth<depths[i]){depths[i]=depth;colors[i]=argb;return true;}return false;}
 public int colorAt(int x,int y){return colors[y*width+x];} public float depthAt(int x,int y){return depths[y*width+x];}
 public boolean isCloser(int x,int y,float depth){return x>=0&&x<width&&y>=0&&y<height&&Float.isFinite(depth)&&depth<depths[y*width+x];}
 /** Package-neutral bulk access for one JavaFX upload, avoiding per-pixel allocations. */
 public int[] colors(){return colors;}
}
