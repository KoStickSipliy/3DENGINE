package com.cgengine;

import javafx.event.Event;
import javafx.scene.input.*;
import org.junit.jupiter.api.Test;
import com.cgengine.math.*;
import com.cgengine.render_engine.Camera;
import javafx.scene.control.TextField;
import javafx.scene.control.ListView;
import javafx.scene.canvas.Canvas;
import static org.junit.jupiter.api.Assertions.*;

class CameraMotionUiTest {
    static void advance(UiRegressionTest.View v,double seconds) {
        for(double left=seconds;left>1e-10;left-=.01)v.controller.updateCameraMotion(Math.min(.01,left));
    }
    static void key(UiRegressionTest.View v,KeyCode code,boolean pressed,boolean ctrl,boolean shift) {
        Event.fireEvent(v.stage.getScene(),new KeyEvent(pressed?KeyEvent.KEY_PRESSED:KeyEvent.KEY_RELEASED,"","",code,shift,ctrl,false,false));
    }
    @Test void repeatedPressDoesNotMoveCameraBeforeTimeAdvances() throws Exception {
        FxTestSupport.run(()->{try(var v=new UiRegressionTest.View()) {
            var camera=v.controller.currentCamera();float distance=camera.getDistance();
            for(int i=0;i<20;i++)key(v,KeyCode.W,true,false,false);
            assertEquals(distance,camera.getDistance(),"Pressed events only change held state; motion requires elapsed time");
            key(v,KeyCode.W,false,false,false);
        }});
    }
    @Test void repeatedRealKeysHaveSameTimeMotionAndReleaseStopsIn120ms() throws Exception {
        FxTestSupport.run(()->{try(var v=new UiRegressionTest.View()) {
            Camera c=v.controller.currentCamera();float start=c.getDistance();
            for(int i=0;i<100;i++){key(v,KeyCode.W,true,false,false);v.controller.updateCameraMotion(.01);}
            assertEquals(start*Math.exp(-.658),c.getDistance(),.001);
            key(v,KeyCode.W,false,false,false);advance(v,.12);float stopped=c.getDistance();
            assertEquals(start*Math.exp(-.7),stopped,.001);advance(v,1);assertEquals(stopped,c.getDistance());
        }});
    }
    @Test void oppositeHeldKeysCancelAndModifierChangeDoesNotStick() throws Exception {
        FxTestSupport.run(()->{try(var v=new UiRegressionTest.View()) {
            Camera c=v.controller.currentCamera();var target=c.getTarget();float distance=c.getDistance();
            key(v,KeyCode.W,true,false,false);key(v,KeyCode.S,true,false,false);
            key(v,KeyCode.A,true,false,false);key(v,KeyCode.D,true,false,false);advance(v,1);
            assertEquals(distance,c.getDistance());assertEquals(target,c.getTarget());
            for(var k:java.util.List.of(KeyCode.W,KeyCode.S,KeyCode.A,KeyCode.D))key(v,k,false,false,false);
            key(v,KeyCode.D,true,false,false);advance(v,.3);float yaw=c.getRotation().x();
            key(v,KeyCode.SHIFT,true,false,true);advance(v,.3);assertTrue(c.getRotation().x()<yaw);
            key(v,KeyCode.D,false,false,true);key(v,KeyCode.SHIFT,false,false,false);advance(v,.12);
            var rotation=c.getRotation();var stopped=c.getTarget();advance(v,1);assertEquals(rotation,c.getRotation());assertEquals(stopped,c.getTarget());
        }});
    }
    @Test void textFocusHideAndCameraSwitchImmediatelyClearHeldMotion() throws Exception {
        FxTestSupport.run(()->{try(var v=new UiRegressionTest.View()) {
            key(v,KeyCode.D,true,false,false);advance(v,.2);
            TextField field=v.control("inputForModelMoving");
            javafx.scene.control.Accordion accordion=(javafx.scene.control.Accordion)v.root.lookup(".accordion");
            for(var pane:accordion.getPanes()) if(pane.getContent().lookupAll(".text-field").contains(field)) accordion.setExpandedPane(pane);
            v.root.applyCss();v.root.layout();field.requestFocus();assertSame(field,v.stage.getScene().getFocusOwner());
            var target=v.controller.currentCamera().getTarget();advance(v,.5);assertEquals(target,v.controller.currentCamera().getTarget());
            Event.fireEvent(field,new KeyEvent(KeyEvent.KEY_PRESSED,"","",KeyCode.W,false,false,false,false));
            Event.fireEvent(field,new KeyEvent(KeyEvent.KEY_TYPED,"w","w",KeyCode.UNDEFINED,false,false,false,false));
            assertTrue(field.getText().contains("w"));float distance=v.controller.currentCamera().getDistance();advance(v,.5);assertEquals(distance,v.controller.currentCamera().getDistance());
            Canvas canvas=v.control("canvas");canvas.setFocusTraversable(true);canvas.requestFocus();
            key(v,KeyCode.W,true,false,false);advance(v,.2);v.stage.hide();distance=v.controller.currentCamera().getDistance();advance(v,.5);assertEquals(distance,v.controller.currentCamera().getDistance());
            v.stage.show();canvas.requestFocus();advance(v,.5);assertEquals(distance,v.controller.currentCamera().getDistance());
            key(v,KeyCode.D,true,false,false);advance(v,.2);v.controller.addCamera(null);
            Camera selected=v.controller.currentCamera();target=selected.getTarget();advance(v,.5);assertEquals(target,selected.getTarget());
            ListView<Camera> cameras=v.control("listViewCameras");cameras.getSelectionModel().select(0);target=v.controller.currentCamera().getTarget();
            advance(v,.5);assertEquals(target,v.controller.currentCamera().getTarget());
        }});
    }
    @Test void focusLossToAnotherWindowClearsMotionOnReturn() throws Exception {
        UiRegressionTest.View[] view=new UiRegressionTest.View[1];javafx.stage.Stage[] other=new javafx.stage.Stage[1];
        var focused=new java.util.concurrent.CompletableFuture<Void>();var lost=new java.util.concurrent.CompletableFuture<Void>();
        try {
            FxTestSupport.run(()->{
                var v=new UiRegressionTest.View();view[0]=v;
                v.stage.focusedProperty().addListener((o,a,b)->{if(b)focused.complete(null);else lost.complete(null);});
                if(v.stage.isFocused())focused.complete(null);v.stage.requestFocus();
            });
            focused.get(10,java.util.concurrent.TimeUnit.SECONDS);
            FxTestSupport.run(()->{
                var v=view[0];key(v,KeyCode.W,true,false,false);advance(v,.2);
                other[0]=new javafx.stage.Stage();other[0].setScene(new javafx.scene.Scene(new javafx.scene.control.Label("Focus test"),200,100));
                other[0].show();other[0].requestFocus();
            });
            lost.get(10,java.util.concurrent.TimeUnit.SECONDS);
            FxTestSupport.run(()->{var v=view[0];float distance=v.controller.currentCamera().getDistance();advance(v,.5);assertEquals(distance,v.controller.currentCamera().getDistance());
                other[0].close();v.stage.requestFocus();advance(v,.5);assertEquals(distance,v.controller.currentCamera().getDistance());});
        } finally {FxTestSupport.run(()->{if(other[0]!=null)other[0].close();if(view[0]!=null)view[0].close();});}
    }
    @Test void activeTimelineAppliesHeldMotionAndFiniteReleaseTail() throws Exception {
        class MotionObserver extends UiRegressionTest.Controller {
            boolean measuring;
            double heldTime;
            @Override void updateCameraMotion(double elapsed) {
                // Independent oracle honours the documented 50 ms pause clamp.
                if (measuring) heldTime += Math.min(elapsed, .050);
                super.updateCameraMotion(elapsed);
            }
        }
        MotionObserver observer = new MotionObserver();
        UiRegressionTest.View[] view=new UiRegressionTest.View[1];
        var ready=new java.util.concurrent.CompletableFuture<Void>();var completed=new java.util.concurrent.CompletableFuture<Void>();
        var scheduler=java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        try {
            FxTestSupport.run(()->{
                var v=new UiRegressionTest.View(observer);view[0]=v;
                v.stage.focusedProperty().addListener((o,a,b)->{if(b)ready.complete(null);});
                if(v.stage.isFocused())ready.complete(null);v.stage.requestFocus();
            });
            ready.get(10,java.util.concurrent.TimeUnit.SECONDS);
            FxTestSupport.run(()->{
                var v=view[0];Canvas canvas=v.control("canvas");canvas.setWidth(320);canvas.setHeight(180);
                v.load(java.nio.file.Path.of("Test_Models/cube.obj"));v.menu("Frame Selected");
                Camera c=v.controller.currentCamera();float initial=c.getDistance();var target=c.getTarget();
                observer.measuring=true;key(v,KeyCode.W,true,false,false);
                scheduler.schedule(()->javafx.application.Platform.runLater(()->{
                    try {
                        observer.measuring=false;double held=observer.heldTime;key(v,KeyCode.W,false,false,false);
                        assertTrue(held > .25, "The active timer must deliver motion updates");
                        scheduler.schedule(()->javafx.application.Platform.runLater(()->{
                            try {
                                assertEquals(initial*Math.exp(-.70*held),c.getDistance(),initial*.04,"Live timer must apply held W and its release tail");
                                assertSame(c,v.controller.currentCamera());assertEquals(target,c.getTarget());
                                float stopped=c.getDistance();advance(v,.3);assertEquals(stopped,c.getDistance());
                                v.controller.renderFrame();assertTrue(java.util.Arrays.stream(v.controller.currentFrameBuffer().colors()).anyMatch(color->color!=0));
                                completed.complete(null);
                            }catch(Throwable error){completed.completeExceptionally(error);}
                        }),250,java.util.concurrent.TimeUnit.MILLISECONDS);
                    }catch(Throwable error){completed.completeExceptionally(error);}
                }),1,java.util.concurrent.TimeUnit.SECONDS);
            });
            completed.get(15,java.util.concurrent.TimeUnit.SECONDS);
        }finally{scheduler.shutdownNow();FxTestSupport.run(()->{if(view[0]!=null)view[0].close();});}
    }
}
