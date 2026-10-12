package com.cgengine.render_engine.Rasterization;
public record RasterVertex(float x,float y,float depth,float uOverW,float vOverW,float inverseW) { public RasterVertex(float x,float y,float depth){this(x,y,depth,0,0,0);} }
