package com.cgengine;

import com.cgengine.math.*;
import com.cgengine.render_engine.*;
import javafx.event.Event;
import javafx.scene.control.ListView;
import javafx.scene.input.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CameraPanUiTest {
    @TempDir Path output;
    private static void press(UiRegressionTest.View v,KeyCode key,boolean ctrl,boolean shift) {
        Camera c=v.controller.currentCamera();
        double speed=shift && !(ctrl && (key==KeyCode.W || key==KeyCode.S)) ? .8
                : .35*c.getDistance()*Math.tan(c.getFov()/2)*((key==KeyCode.A || key==KeyCode.D)?c.getAspectRatio():1);
        double duration=shift && !(ctrl && (key==KeyCode.W || key==KeyCode.S)) ? .02/speed
                : (key==KeyCode.W || key==KeyCode.S) && !ctrl ? .1 : 1/speed;
        Event.fireEvent(v.stage.getScene(),new KeyEvent(KeyEvent.KEY_PRESSED,"","",key,shift,ctrl,false,false));
        for(double left=duration;left>1e-10;left-=.01)v.controller.updateCameraMotion(Math.min(.01,left));
        Event.fireEvent(v.stage.getScene(),new KeyEvent(KeyEvent.KEY_RELEASED,"","",key,shift,ctrl,false,false));
        for(int i=0;i<12;i++)v.controller.updateCameraMotion(.01);
    }
    private static void vector(Vector3f expected,Vector3f actual) {
        assertEquals(expected.x(),actual.x(),1e-4); assertEquals(expected.y(),actual.y(),1e-4); assertEquals(expected.z(),actual.z(),1e-4);
    }
    @Test void realKeysPanAlongExpectedRightAtFourYawsAndTwoPitches() throws Exception {
        FxTestSupport.run(()->{try(var v=new UiRegressionTest.View()) {
            Camera c=v.controller.currentCamera();
            for(int quarter=0;quarter<4;quarter++)for(double pitch:List.of(-Math.PI/2+.3,-Math.PI/4)) {
                double yaw=quarter*Math.PI/2;
                c.setRotation(new Vector2f((float)yaw,(float)pitch));c.setTarget(new Vector3f(2,3,4));
                Vector3f target=c.getTarget(),eye=c.getPosition();float distance=c.getDistance();Vector2f rotation=c.getRotation();
                // Explicit yaw/pitch basis, independent of the production view/right helper.
                Vector3f right=new Vector3f((float)Math.cos(yaw),0,(float)-Math.sin(yaw));
                Vector3f up=new Vector3f((float)(Math.sin(yaw)*Math.cos(pitch)),(float)-Math.sin(pitch),(float)(Math.cos(yaw)*Math.cos(pitch)));
                Vector3f forward=new Vector3f((float)(-Math.sin(yaw)*Math.sin(pitch)),(float)-Math.cos(pitch),(float)(-Math.cos(yaw)*Math.sin(pitch)));
                press(v,KeyCode.D,false,false);
                vector(VectorMath.add(target,right),c.getTarget());vector(VectorMath.add(eye,right),c.getPosition());
                var delta=VectorMath.subtract(c.getTarget(),target);
                assertEquals(0,VectorMath.dot(delta,up),1e-4);assertEquals(0,VectorMath.dot(delta,forward),1e-4);
                assertEquals(distance,c.getDistance());assertEquals(rotation,c.getRotation());
                press(v,KeyCode.A,false,false);vector(target,c.getTarget());vector(eye,c.getPosition());
            }
        }});
    }
    @Test void controlVerticalPanUsesExpectedUpAndMenuSharesDirections() throws Exception {
        FxTestSupport.run(()->{try(var v=new UiRegressionTest.View()) {
            Camera c=v.controller.currentCamera();
            for(double pitch:List.of(-Math.PI/2+.3,-Math.PI/4)) {
                c.setRotation(new Vector2f((float)(Math.PI/2),(float)pitch)); c.setTarget(new Vector3f(2,3,4));
                Vector3f target=c.getTarget(),eye=c.getPosition();var rotation=c.getRotation();float distance=c.getDistance();
                Vector3f up=new Vector3f((float)Math.cos(pitch),(float)-Math.sin(pitch),0);
                press(v,KeyCode.W,true,true); // Ctrl takes priority over Shift for W/S.
                vector(VectorMath.add(target,up),c.getTarget());vector(VectorMath.add(eye,up),c.getPosition());
                assertEquals(rotation,c.getRotation());assertEquals(distance,c.getDistance());
                press(v,KeyCode.S,true,false);vector(target,c.getTarget());vector(eye,c.getPosition());
                v.controller.moveCameraRight(null);vector(VectorMath.add(target,new Vector3f(0,0,-1)),c.getTarget());
                v.controller.moveCameraLeft(null);vector(target,c.getTarget());
            }
        }});
    }
    @Test void frameSelectedAwayFromOriginKeepsTargetAndGeometryDuringDolly() throws Exception {
        Path file=output.resolve("offset.obj");Files.writeString(file,"v 19 29 40\nv 21 29 40\nv 20 31 40\nf 1 2 3");
        FxTestSupport.run(()->{try(var v=new UiRegressionTest.View()) {
            v.load(file);v.menu("Frame Selected");Camera c=v.controller.currentCamera();
            vector(new Vector3f(20,30,40),c.getTarget());var target=c.getTarget();var vertices=new ArrayList<>(v.selected().vertices);
            float distance=c.getDistance();press(v,KeyCode.W,false,false);
            assertTrue(c.getDistance()<distance);vector(target,c.getTarget());assertEquals(vertices,v.selected().vertices);
            assertTrue(c.getNearPlane()<c.getDistance()&&c.getDistance()<c.getFarPlane());
        }});
    }
    private static double imageCenterX(com.cgengine.render_engine.Rasterization.FrameBuffer frame) {
        double sum=0;int count=0;
        for(int y=0;y<frame.height();y++)for(int x=0;x<frame.width();x++)if(frame.colorAt(x,y)!=0){sum+=x;count++;}
        assertTrue(count>20);return sum/count;
    }
    @Test void asymmetricModelMovesLeftOnDAndCameraSelectionRotationResetStillWork() throws Exception {
        Path file=Path.of("tests/fixtures/baseline/asymmetric-single.obj");
        FxTestSupport.run(()->{try(var v=new UiRegressionTest.View()) {
            v.load(file);v.menu("Frame Selected");var vertices=new ArrayList<>(v.selected().vertices);
            Camera c=v.controller.currentCamera();c.setRotation(new Vector2f((float)(Math.PI+.4),(float)(-Math.PI/2+.3)));
            v.controller.renderFrame();double before=imageCenterX(v.controller.currentFrameBuffer());
            press(v,KeyCode.D,false,false);v.controller.renderFrame();
            assertTrue(imageCenterX(v.controller.currentFrameBuffer())<before,"D must move the object left in the rendered real window");
            assertEquals(vertices,v.selected().vertices);
            press(v,KeyCode.A,false,false);
            float yaw=c.getRotation().x();press(v,KeyCode.A,false,true);assertTrue(c.getRotation().x()>yaw);
            press(v,KeyCode.D,true,true);assertEquals(yaw,c.getRotation().x(),1e-5); // Shift wins for A/D.
            v.controller.addCamera(null);
            ListView<Camera> cameras=v.control("listViewCameras");cameras.getSelectionModel().select(c);
            assertSame(c,v.controller.currentCamera());v.menu("Reset Camera");
            assertSame(cameras.getSelectionModel().getSelectedItem(),v.controller.currentCamera());
            vector(new Vector3f(0,0,0),v.controller.currentCamera().getTarget());assertEquals(100,v.controller.currentCamera().getDistance());
            v.controller.renderFrame();assertNull(v.controller.error);
        }});
    }
}
