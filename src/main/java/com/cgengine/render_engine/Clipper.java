package com.cgengine.render_engine;
import java.util.*;
/** Homogeneous Sutherland-Hodgman frustum clipping before perspective division. */
public final class Clipper {
 private Clipper(){} private interface Plane{float distance(ClipVertex v);}
 private static final Plane[] PLANES={v->v.position().x()+v.position().w(),v->v.position().w()-v.position().x(),v->v.position().y()+v.position().w(),v->v.position().w()-v.position().y(),v->v.position().z()+v.position().w(),v->v.position().w()-v.position().z()};
 public static List<ClipVertex> clipTriangle(ClipVertex a,ClipVertex b,ClipVertex c){List<ClipVertex> polygon=List.of(a,b,c);for(Plane plane:PLANES){if(polygon.isEmpty())return polygon;List<ClipVertex> out=new ArrayList<>();ClipVertex previous=polygon.getLast();float pd=plane.distance(previous);for(ClipVertex current:polygon){float cd=plane.distance(current);boolean inside=cd>=0,wasInside=pd>=0;if(inside!=wasInside){float t=pd/(pd-cd);out.add(previous.interpolate(current,t));}if(inside)out.add(current);previous=current;pd=cd;}polygon=out;}return polygon;}
 public static List<ClipVertex[]> triangles(List<ClipVertex> polygon){if(polygon.size()<3)return List.of();List<ClipVertex[]> result=new ArrayList<>();for(int i=1;i+1<polygon.size();i++)result.add(new ClipVertex[]{polygon.getFirst(),polygon.get(i),polygon.get(i+1)});return result;}
}
