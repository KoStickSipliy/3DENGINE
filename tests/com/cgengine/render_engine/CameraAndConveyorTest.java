package com.cgengine.render_engine;
import com.cgengine.math.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CameraAndConveyorTest {
 private static final float E=.0001f;
 @Test void projectionMatchesReferenceAndAspect(){Matrix4f m=GraphicConveyor.perspective(1,2,.01f,100);float f=(float)(1/Math.tan(.5));assertEquals(f/2,m.get(0,0),E);assertEquals(f,m.get(1,1),E);assertEquals(1,m.get(3,2),E);assertEquals(0,m.get(3,3),E);}
 @Test void cameraUsesInitialDistanceAndRotationSetter(){Camera c=new Camera(new Vector2f(0,(float)Math.PI/2),7,new Vector3f(1,2,3),1,1,.1f,10);assertEquals(10,c.getPosition().z(),E);c.setRotation(new Vector2f((float)Math.PI/2,(float)Math.PI/2));assertEquals(8,c.getPosition().x(),E);}
 @Test void lookAtAndInvalidInputsAreControlled(){Matrix4f view=GraphicConveyor.lookAt(new Vector3f(0,0,10),new Vector3f(0,0,0));assertEquals(10,view.get(2,3),E);assertThrows(IllegalArgumentException.class,()->GraphicConveyor.lookAt(new Vector3f(0,0,0),new Vector3f(0,0,0)));assertThrows(IllegalArgumentException.class,()->GraphicConveyor.multiplyMatrix4ByVector3(new Matrix4f(1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,0),new Vector3f(1,2,3)));}
}
