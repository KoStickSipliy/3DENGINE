package com.cgengine.render_engine;

import com.cgengine.math.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CameraScreenBasisTest {
    private static void vector(double x,double y,double z,Vector3f actual) {
        assertEquals(x,actual.x(),1e-4);assertEquals(y,actual.y(),1e-4);assertEquals(z,actual.z(),1e-4);
    }
    @Test void axesMatchIndependentAnglesAtFourYawsAndTwoPitches() {
        for(int quarter=0;quarter<4;quarter++)for(double pitch:List.of(-Math.PI/2+.3,-Math.PI/4)) {
            double yaw=quarter*Math.PI/2;
            Camera c=new Camera(new Vector2f((float)yaw,(float)pitch),10,new Vector3f(2,3,4),1,2,.1f,50);
            Vector3f r=c.getScreenRight(),u=c.getScreenUp();
            vector(Math.cos(yaw),0,-Math.sin(yaw),r);
            vector(Math.sin(yaw)*Math.cos(pitch),-Math.sin(pitch),Math.cos(yaw)*Math.cos(pitch),u);
            assertEquals(1,VectorMath.length(r),1e-5);assertEquals(1,VectorMath.length(u),1e-5);assertEquals(0,VectorMath.dot(r,u),1e-5);
            var view=c.getViewMatrix();
            Vector4f screenR=MatrixMath.multiply(view,new Vector4f(r.x(),r.y(),r.z(),0));
            Vector4f screenU=MatrixMath.multiply(view,new Vector4f(u.x(),u.y(),u.z(),0));
            vector(1,0,0,new Vector3f(screenR.x(),screenR.y(),screenR.z()));
            vector(0,1,0,new Vector3f(screenU.x(),screenU.y(),screenU.z()));
        }
    }
    @Test void projectionOfFixedTargetMovesLeftOnRightPanAndDownOnUpPan() {
        for(int quarter=0;quarter<4;quarter++)for(double pitch:List.of(-Math.PI/2+.3,-Math.PI/4)) {
            var rotation=new Vector2f((float)(quarter*Math.PI/2),(float)pitch);var target=new Vector3f(2,3,4);
            Camera c=new Camera(rotation,10,target,1,2,.1f,50);
            c.moveTarget(VectorMath.multiply(c.getScreenRight(),.5f));
            Vector2f pixel=project(c,target);
            assertEquals(500-1000/Math.tan(.5)/2*.5/10,pixel.x(),.001);assertEquals(250,pixel.y(),.001);
            c.setTarget(target);c.moveTarget(VectorMath.multiply(c.getScreenUp(),.5f));pixel=project(c,target);
            assertEquals(500,pixel.x(),.001);assertEquals(250+500/Math.tan(.5)*.5/10,pixel.y(),.001);
            assertEquals(10,c.getDistance());assertEquals(rotation,c.getRotation());
        }
    }
    private static Vector2f project(Camera camera,Vector3f point) {
        var clip=MatrixMath.multiply(MatrixMath.multiply(camera.getProjectionMatrix(),camera.getViewMatrix()),new Vector4f(point.x(),point.y(),point.z(),1));
        return new Vector2f(1000*clip.x()/clip.w()+500,-500*clip.y()/clip.w()+250);
    }
}
