package com.cgengine.render_engine;
/** Opaque session-only polygon colour. */
public record ColorRgb(int red,int green,int blue) {
 public static final ColorRgb RED=new ColorRgb(255,0,0),GREEN=new ColorRgb(0,255,0),BLUE=new ColorRgb(0,0,255),YELLOW=new ColorRgb(255,255,0),WHITE=new ColorRgb(255,255,255),BLACK=new ColorRgb(0,0,0);
 public ColorRgb{if(red<0||red>255||green<0||green>255||blue<0||blue>255)throw new IllegalArgumentException("RGB channels must be in [0,255]");} public int argb(){return 0xFF000000|(red<<16)|(green<<8)|blue;}
}
