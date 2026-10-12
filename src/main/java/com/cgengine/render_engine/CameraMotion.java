package com.cgengine.render_engine;

import com.cgengine.math.*;
import java.util.ArrayDeque;

/** Time-based orbit navigation with an exactly integrated finite 120 ms input average. */
public final class CameraMotion {
    public static final double SMOOTHING_SECONDS = .120;
    public static final double MAX_DT = .050;
    public record Input(double right, double up, double dolly, double yaw, double pitch) {
        public static final Input NONE = new Input(0,0,0,0,0);
        public Input {
            if (!Double.isFinite(right) || !Double.isFinite(up) || !Double.isFinite(dolly)
                    || !Double.isFinite(yaw) || !Double.isFinite(pitch)) throw new IllegalArgumentException("Invalid motion input");
            double length = Math.max(1, Math.hypot(right, up)); right /= length; up /= length;
        }
        double[] axes() { return new double[]{right,up,dolly,yaw,pitch}; }
    }
    private record Change(double time, double[] delta) { }
    private final ArrayDeque<Change> changes = new ArrayDeque<>();
    private final double[] settled = new double[5];
    private Input input = Input.NONE;
    private double time;

    public void setInput(Input next) {
        if (next.equals(input)) return; // OS repeats never add motion.
        double[] delta = next.axes(), previous = input.axes();
        for (int i=0;i<5;i++) delta[i] -= previous[i];
        changes.addLast(new Change(time,delta)); input = next;
    }
    public void reset() {
        changes.clear(); java.util.Arrays.fill(settled,0); input=Input.NONE; time=0;
    }
    private static double area(double t) {
        return t<=0 ? 0 : t<SMOOTHING_SECONDS ? t*t/(2*SMOOTHING_SECONDS) : t-SMOOTHING_SECONDS/2;
    }
    public boolean update(Camera camera, double elapsedSeconds) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds<0) throw new IllegalArgumentException("Invalid elapsed time");
        double dt=Math.min(elapsedSeconds,MAX_DT), end=time+dt;
        double[] integrated=new double[5];
        for(int i=0;i<5;i++) integrated[i]=settled[i]*dt;
        for(Change change:changes) {
            double weight=area(end-change.time)-area(time-change.time);
            for(int i=0;i<5;i++) integrated[i]+=change.delta[i]*weight;
        }
        time=end;
        while(!changes.isEmpty() && time-changes.getFirst().time>=SMOOTHING_SECONDS) {
            double[] delta=changes.removeFirst().delta;
            for(int i=0;i<5;i++) settled[i]+=delta[i];
        }
        boolean moving=false; for(double value:integrated) moving |= Math.abs(value)>1e-12;
        if(!moving)return false;
        double oldDistance=camera.getDistance();
        float newDistance=(float)Math.max(.010,Math.min(1e8,oldDistance*Math.exp(.70*integrated[2])));
        double panDistance=Math.sqrt(oldDistance*newDistance), tangent=Math.tan(camera.getFov()/2);
        if(integrated[0]!=0 || integrated[1]!=0) {
            Vector3f right=VectorMath.multiply(camera.getScreenRight(),(float)(.35*panDistance*camera.getAspectRatio()*tangent*integrated[0]));
            Vector3f up=VectorMath.multiply(camera.getScreenUp(),(float)(.35*panDistance*tangent*integrated[1]));
            camera.moveTarget(VectorMath.add(right,up));
        }
        if(newDistance!=camera.getDistance()) camera.setNavigationDistance(newDistance);
        if(integrated[3]!=0 || integrated[4]!=0)
            camera.moveRotation(new Vector2f((float)(.80*integrated[3]),(float)(.80*integrated[4])));
        return true;
    }
}
