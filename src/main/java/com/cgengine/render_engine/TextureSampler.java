package com.cgengine.render_engine;
/** Nearest-neighbour sampler using D-03 clamp addressing and OBJ's bottom-left V axis. */
public final class TextureSampler { private TextureSampler(){} public static int sample(Texture2D texture,float u,float v){if(texture==null||!Float.isFinite(u)||!Float.isFinite(v))throw new IllegalArgumentException("Texture and UV must be finite");u=Math.max(0,Math.min(1,u));v=Math.max(0,Math.min(1,v));return texture.pixel(Math.round(u*(texture.width()-1)),Math.round((1-v)*(texture.height()-1)));} }
