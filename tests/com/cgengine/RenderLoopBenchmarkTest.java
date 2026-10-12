package com.cgengine;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Machine-specific UI latency acceptance; the production frame loop remains active. */
class RenderLoopBenchmarkTest {
    @Test void activeLoopLatencyAndThirtySecondIdle() throws Exception {
        var view = new UiRegressionTest.View[1];
        long[] idleFrames = new long[1];
        java.util.List<Double> latency = new java.util.ArrayList<>();
        java.util.List<Double> visible = new java.util.ArrayList<>();
        Path model = Path.of(System.getProperty("cgengine.stage8.model", "Test_Models/cube.obj"));
        try {
            FxTestSupport.run(() -> {
                view[0] = new UiRegressionTest.View();
                javafx.scene.canvas.Canvas c = view[0].control("canvas"); c.setWidth(1280); c.setHeight(720);
                view[0].load(model); view[0].controller.renderFrame();
                CameraMotionUiTest.key(view[0],javafx.scene.input.KeyCode.D,true,false,true);
            });
            // Keep the real frame loop running while the external thread posts input.
            for (int i=0;i<100;i++) {
                Thread.sleep(30);
                long posted = System.nanoTime();
                int action = i;
                var published = new java.util.concurrent.CompletableFuture<Void>();
                FxTestSupport.run(() -> {
                    latency.add((System.nanoTime()-posted)/1e6);
                    var v=view[0];
                    CameraMotionUiTest.key(v,javafx.scene.input.KeyCode.D,true,false,true);
                    if (action % 10 == 0) {
                        v.button("All");
                        javafx.scene.control.ComboBox<String> picker = v.control("colorPicker");
                        picker.setValue(action % 20 == 0 ? "Red" : "Green"); v.button("Apply");
                        assertNull(v.controller.error);
                    }
                    long previous = v.controller.renderedFrames();
                    new javafx.animation.AnimationTimer() {
                        @Override public void handle(long now) {
                            if (v.controller.renderedFrames() > previous) {
                                stop(); visible.add((System.nanoTime()-posted)/1e6); published.complete(null);
                            }
                        }
                    }.start();
                });
                published.get(10,java.util.concurrent.TimeUnit.SECONDS);
            }
            FxTestSupport.run(() -> {
                CameraMotionUiTest.key(view[0],javafx.scene.input.KeyCode.D,false,false,false);
                CameraMotionUiTest.advance(view[0],.2); view[0].controller.renderFrame();
                idleFrames[0] = view[0].controller.renderedFrames();
            });
            java.util.Collections.sort(latency);
            double p95=latency.get(94),max=latency.getLast();
            System.out.printf(java.util.Locale.ROOT,"Stage8 %s UI latency n=100 p95=%.3fms max=%.3fms frames=%d%n",model,p95,max,idleFrames[0]);
            java.util.Collections.sort(visible);
            System.out.printf(java.util.Locale.ROOT,"Stage8 %s action-to-Canvas-upload n=100 p95=%.3fms max=%.3fms%n",model,visible.get(94),visible.getLast());
            assertTrue(p95<=100,"UI p95"); assertTrue(max<500,"UI max");
            Thread.sleep(Long.getLong("cgengine.stage8.idleMillis",30000L));
            FxTestSupport.run(() -> assertEquals(idleFrames[0],view[0].controller.renderedFrames(),"Idle scene must not render"));
        } finally { FxTestSupport.run(() -> {if(view[0]!=null)view[0].close();}); }
    }
}
