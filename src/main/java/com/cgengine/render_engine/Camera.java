package com.cgengine.render_engine;
import com.cgengine.math.*;
/** Orbit camera; rotation values are radians. */
public final class Camera {
 private Vector2f rotation; private float distance; private Vector3f target; private final float fov; private float nearPlane,farPlane; private final float clippingRadius; private float aspectRatio;
 public Camera(Vector2f rotation,float distance,Vector3f target,float fov,float aspectRatio,float nearPlane,float farPlane){if(rotation==null||target==null||!Float.isFinite(distance)||!Float.isFinite(fov)||!Float.isFinite(aspectRatio)||distance<0||fov<=0||aspectRatio<=0||nearPlane<=0||farPlane<=nearPlane)throw new IllegalArgumentException("Invalid camera parameters");this.rotation=rotation;this.distance=distance;this.target=target;this.fov=fov;this.aspectRatio=aspectRatio;this.nearPlane=nearPlane;this.farPlane=farPlane;this.clippingRadius=Math.max(.001f,(farPlane-distance)/2.1f);}
 public void setRotation(Vector2f value){if(value==null)throw new IllegalArgumentException("Rotation is null");rotation=value;} public void setTarget(Vector3f value){if(value==null||!VectorMath.isFinite(value))throw new IllegalArgumentException("Invalid target");target=value;} public void setAspectRatio(float value){if(!Float.isFinite(value)||value<=0)throw new IllegalArgumentException("Aspect ratio must be positive");aspectRatio=value;}
 public Vector2f getRotation(){return rotation;} public Vector3f getTarget(){return target;}
 public float getDistance(){return distance;} public float getFov(){return fov;} public float getAspectRatio(){return aspectRatio;} public float getNearPlane(){return nearPlane;} public float getFarPlane(){return farPlane;}
 /** Update zoom and clipping in place, preserving the selected camera and its orbit. */
 public void setNavigationDistance(float value) {
  if(!Float.isFinite(value) || value<.010f || value>1e8f) throw new IllegalArgumentException("Invalid navigation distance");
  distance=value;
  nearPlane=Math.max(.000001f,Math.min(distance*.001f,clippingRadius*.001f));
  farPlane=(float)Math.max(distance*2.0,distance+clippingRadius*2.1);
 }
 /** World-space screen axes taken from the same view basis used for rendering. */
 public Vector3f getScreenRight(){return viewAxis(0);}
 public Vector3f getScreenUp(){return viewAxis(1);}
 private Vector3f viewAxis(int row){Matrix4f view=getViewMatrix();return VectorMath.normalize(new Vector3f(view.get(row,0),view.get(row,1),view.get(row,2)));}
 public Vector3f getPosition(){return VectorMath.add(target,new Vector3f((float)(distance*Math.sin(rotation.x())*Math.sin(rotation.y())),(float)(distance*Math.cos(rotation.y())),(float)(distance*Math.cos(rotation.x())*Math.sin(rotation.y()))));}
 public void moveRotation(Vector2f delta){if(delta==null)throw new IllegalArgumentException("Rotation translation is null");rotation=VectorMath.add(rotation,delta);} public void moveDistance(float delta){if(!Float.isFinite(delta)||distance+delta<0)throw new IllegalArgumentException("Camera distance must remain non-negative");distance+=delta;} public void moveTarget(Vector3f delta){if(delta==null||!VectorMath.isFinite(delta))throw new IllegalArgumentException("Invalid target translation");target=VectorMath.add(target,delta);}
 public Matrix4f getViewMatrix(){float p=rotation.x(),t=rotation.y();return GraphicConveyor.lookAt(getPosition(),target,new Vector3f((float)(Math.sin(p+Math.PI)*Math.sin(-t-Math.PI/2)),(float)Math.cos(-t-Math.PI/2),(float)(Math.cos(p+Math.PI)*Math.sin(-t-Math.PI/2))));} public Matrix4f getProjectionMatrix(){return GraphicConveyor.perspective(fov,aspectRatio,nearPlane,farPlane);}
}
