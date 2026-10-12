package com.cgengine.render_engine;
import com.cgengine.math.Vector4f;
import com.cgengine.math.Vector2f;
/** Vertex in homogeneous clip space. */
public record ClipVertex(Vector4f position, Vector2f uv) {
 public ClipVertex(Vector4f position){this(position,null);} public ClipVertex interpolate(ClipVertex other,float t){Vector4f a=position,b=other.position();Vector2f sampled=uv==null?null:new Vector2f(uv.x()+(other.uv.x()-uv.x())*t,uv.y()+(other.uv.y()-uv.y())*t);return new ClipVertex(new Vector4f(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t,a.w()+(b.w()-a.w())*t),sampled);}
}
