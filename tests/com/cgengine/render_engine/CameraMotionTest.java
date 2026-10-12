package com.cgengine.render_engine;

import com.cgengine.math.*;
import com.cgengine.obj_io.reader.ObjReader;
import com.cgengine.render_engine.Rasterization.FrameBuffer;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CameraMotionTest {
    private static Camera camera(float distance) {
        return new Camera(new Vector2f(0,(float)Math.PI/2),distance,new Vector3f(0,0,0),1,16f/9,.001f,Math.max(10,distance*2));
    }
    private static CameraMotion.Input input(double right,double up,double dolly,double yaw,double pitch) {
        return new CameraMotion.Input(right,up,dolly,yaw,pitch);
    }
    private static void advance(CameraMotion motion,Camera camera,int fps,int frames) {
        for(int i=0;i<frames;i++)motion.update(camera,1.0/fps);
    }
    @Test void oneSecondPanMatchesIndependentOracleAt30And60FpsAndAllScales() {
        for(int fps:List.of(30,60))for(float distance:List.of(.05f,5f,500f)) {
            Camera c=camera(distance);CameraMotion motion=new CameraMotion();motion.setInput(input(1,0,0,0,0));
            advance(motion,c,fps,fps);
            double expected=.35*distance*(16f/9)*Math.tan(.5)*.940;
            assertEquals(expected,c.getTarget().x(),Math.max(1e-7,expected*1e-5));
            assertEquals(-.329,-c.getTarget().x()/distance/(16f/9)/Math.tan(.5),1e-5);
            assertEquals(distance,c.getDistance());
        }
    }
    @Test void osRepeatsDoNotChangeMotionAndReleaseHasFiniteTail() {
        Camera a=camera(5),b=camera(5);CameraMotion ma=new CameraMotion(),mb=new CameraMotion();
        var d=input(1,0,0,0,0);ma.setInput(d);mb.setInput(d);
        for(int i=0;i<60;i++){for(int repeat=0;repeat<20;repeat++)ma.setInput(d);ma.update(a,1.0/60);mb.update(b,1.0/60);}
        assertEquals(a.getTarget(),b.getTarget());ma.setInput(CameraMotion.Input.NONE);
        advance(ma,a,100,12);float stopped=a.getTarget().x();advance(ma,a,60,60);assertEquals(stopped,a.getTarget().x());
        assertEquals(.35*5*(16f/9)*Math.tan(.5),stopped,1e-5);
    }
    @Test void resetImmediatelyStopsHeldInputAndTail() {
        Camera c=camera(5);CameraMotion m=new CameraMotion();m.setInput(input(1,0,0,0,0));advance(m,c,60,20);
        m.reset();var target=c.getTarget();advance(m,c,60,120);assertEquals(target,c.getTarget());
    }
    @Test void equalTimeDollyReversesAndMatchesExponentialOracle() {
        for(int fps:List.of(30,60)) {
            Camera c=camera(100);CameraMotion m=new CameraMotion();m.setInput(input(0,0,-1,0,0));advance(m,c,fps,fps);
            assertEquals(100*Math.exp(-.7*.940),c.getDistance(),.001);
            m.setInput(input(0,0,1,0,0));advance(m,c,fps,fps);
            m.setInput(CameraMotion.Input.NONE);advance(m,c,100,12);
            assertEquals(100,c.getDistance(),.001);
        }
    }
    @Test void diagonalPanIsNormalizedInViewportUnits() {
        Camera c=camera(5);CameraMotion m=new CameraMotion();m.setInput(input(1,1,0,0,0));advance(m,c,60,60);
        double horizontal=c.getTarget().x()/5/(16f/9)/Math.tan(.5),vertical=-c.getTarget().y()/5/Math.tan(.5);
        assertEquals(.329,Math.hypot(horizontal,vertical),1e-5);assertEquals(horizontal,vertical,1e-5);
    }
    @Test void oppositeAxesCancelAndPanDollyOrbitCanCoexist() {
        Camera c=camera(5);CameraMotion m=new CameraMotion();m.setInput(CameraMotion.Input.NONE);advance(m,c,60,60);
        assertEquals(new Vector3f(0,0,0),c.getTarget());assertEquals(5,c.getDistance());
        m.setInput(input(1,0,-1,0,0));advance(m,c,60,60);assertTrue(c.getTarget().x()>0);assertEquals(5*Math.exp(-.658),c.getDistance(),1e-4);
        m.reset();var target=c.getTarget();var initial=c.getRotation();m.setInput(input(0,0,0,1,1));advance(m,c,60,60);
        assertEquals(initial.x()+.80*.940,c.getRotation().x(),1e-5);assertEquals(initial.y()+.80*.940,c.getRotation().y(),1e-5);assertEquals(target,c.getTarget());
    }
    @Test void longPauseUsesOnlyFiftyMilliseconds() {
        Camera a=camera(5),b=camera(5);CameraMotion ma=new CameraMotion(),mb=new CameraMotion();
        ma.setInput(input(1,0,-1,0,0));mb.setInput(input(1,0,-1,0,0));ma.update(a,2);mb.update(b,.05);
        assertEquals(a.getTarget(),b.getTarget());assertEquals(a.getDistance(),b.getDistance());
        assertEquals(5*Math.exp(-.7*.05*.05/(2*.12)),a.getDistance(),1e-5);
    }
    @Test void zoomClippingStaysFiniteAcrossSmallAndLargeDistancesWithoutReplacingCamera() {
        Camera c=camera(5);
        for(float distance:List.of(.01f,.05f,5f,500f,1e8f)) {
            c.setNavigationDistance(distance);assertEquals(distance,c.getDistance());
            assertTrue(Float.isFinite(c.getNearPlane())&&Float.isFinite(c.getFarPlane()));
            assertTrue(c.getNearPlane()<distance&&distance<c.getFarPlane());assertNotEquals(c.getTarget(),c.getPosition());
        }
        c.setNavigationDistance(5);CameraMotion motion=new CameraMotion();motion.setInput(input(0,0,-1,0,0));advance(motion,c,60,1200);
        assertEquals(.01f,c.getDistance(),1e-6);
    }
    @Test void zoomOutPastOriginalFarStillRendersGeometry() {
        var model=ObjReader.read("v -1 -1 0\nv 1 -1 0\nv 0 1 0\nf 1 2 3");Camera c=camera(5);
        c.setNavigationDistance(40);FrameBuffer f=new FrameBuffer(256,256);RenderEngine.renderFrame(f,c,List.of(model));
        assertTrue(Arrays.stream(f.colors()).anyMatch(color->color!=0));
    }
    @Test void longSteadyInputAndRepeatedResetStayBoundedAndFinite() {
        Camera c=camera(5);CameraMotion m=new CameraMotion();
        for(int i=0;i<10000;i++){m.setInput(input(0,0,(i/60)%2==0?-1:1,0,0));m.update(c,1.0/60);}
        assertTrue(Float.isFinite(c.getDistance()));m.reset();float distance=c.getDistance();advance(m,c,60,60);assertEquals(distance,c.getDistance());
        assertThrows(IllegalArgumentException.class,()->m.update(c,Double.NaN));
    }
}
