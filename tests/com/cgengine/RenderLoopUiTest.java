package com.cgengine;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class RenderLoopUiTest {
    @Test void disposeStopsRenderingBeforeNextPulse() throws Exception {
        var view = new UiRegressionTest.View[1];
        var done = new java.util.concurrent.CompletableFuture<Void>();
        try {
            FxTestSupport.run(() -> {
                var v = new UiRegressionTest.View(); view[0] = v;
                v.load(Path.of("Test_Models/cube.obj")); v.controller.renderFrame();
                long frames = v.controller.renderedFrames();
                v.controller.dispose();
                v.controller.currentCamera().moveTarget(new com.cgengine.math.Vector3f(1,0,0));
                new javafx.animation.AnimationTimer() {
                    @Override public void handle(long now) {
                        stop();
                        try { assertEquals(frames,v.controller.renderedFrames()); done.complete(null); }
                        catch (Throwable error) { done.completeExceptionally(error); }
                    }
                }.start();
            });
            done.get(10,java.util.concurrent.TimeUnit.SECONDS);
        } finally { FxTestSupport.run(() -> {if(view[0]!=null)view[0].close();}); }
    }
    @Test void burstUsesLatestStateAndInvalidPeerDoesNotStopLoop() throws Exception {
        FxTestSupport.run(() -> { try (var v = new UiRegressionTest.View()) {
            v.load(Path.of("Test_Models/cube.obj"));
            javafx.scene.canvas.Canvas canvas = v.control("canvas"); canvas.setWidth(320); canvas.setHeight(180);
            v.controller.renderFrame(); long before = v.controller.renderedFrames();
            for (int i=0;i<100;i++) {
                CameraMotionUiTest.key(v,javafx.scene.input.KeyCode.D,true,false,false);
                v.controller.updateCameraMotion(.01);
            }
            CameraMotionUiTest.key(v,javafx.scene.input.KeyCode.D,false,false,false);
            CameraMotionUiTest.advance(v,.2);
            assertEquals(before,v.controller.renderedFrames(),"Events do not enqueue render jobs");
            v.controller.renderFrame(); assertEquals(before+1,v.controller.renderedFrames());
            var expected = new com.cgengine.render_engine.Rasterization.FrameBuffer(320,180);
            com.cgengine.render_engine.RenderEngine.renderFrame(expected,v.controller.currentCamera(),java.util.List.of(v.selected()));
            assertArrayEquals(expected.colors(),v.controller.currentFrameBuffer().colors());
            v.load(Path.of("Test_Models/cube.obj"));
            var bad=v.selected(); bad.polygons.add(null); v.controller.renderFrame();
            assertEquals("Face must contain exactly three vertices",v.controller.renderError(bad));
            assertTrue(java.util.Arrays.stream(v.controller.currentFrameBuffer().colors()).anyMatch(c->c!=0));
            bad.polygons.removeLast(); v.controller.renderFrame(); assertNull(v.controller.renderError(bad));
        }});
    }
    @Test void everyRenderInputAndPublicListEditInvalidatesFrame() throws Exception {
        FxTestSupport.run(() -> { try (var v = new UiRegressionTest.View()) {
            v.load(Path.of("Test_Models/cube.obj"));
            var m = v.selected();
            javafx.scene.canvas.Canvas canvas = v.control("canvas");
            canvas.setWidth(320); canvas.setHeight(180);
            v.controller.renderFrame();
            java.util.List<Runnable> changes = java.util.List.of(
                () -> v.controller.currentCamera().moveTarget(new com.cgengine.math.Vector3f(.1f,0,0)),
                () -> v.controller.currentCamera().setNavigationDistance(8),
                () -> m.polygons.getFirst().setBaseColor(com.cgengine.render_engine.ColorRgb.RED),
                () -> m.setTexture(new com.cgengine.render_engine.Texture2D(1,1,new int[]{0xff00ff00})),
                m::removeTexture,
                () -> m.vertices.set(0,new com.cgengine.math.Vector3f(-.8f,-1,-1)),
                () -> m.polygons.getFirst().getVertexIndices().set(0,
                        (m.polygons.getFirst().getVertexIndices().getFirst()+1)%m.vertices.size()),
                () -> m.normals.set(0,new com.cgengine.math.Vector3f(0,0,0)),
                () -> m.textureVertices.add(new com.cgengine.math.Vector2f(.3f,.4f)),
                () -> canvas.setWidth(400),
                () -> canvas.setHeight(240),
                () -> v.controller.addCamera(null),
                () -> v.controller.removeModel(null));
            for (Runnable change : changes) {
                long previous = v.controller.renderedFrames(); change.run(); v.controller.renderFrame();
                assertEquals(previous+1,v.controller.renderedFrames());
                v.controller.renderFrame(); assertEquals(previous+1,v.controller.renderedFrames());
            }
            assertEquals(400,v.controller.currentFrameBuffer().width());
            assertTrue(java.util.Arrays.stream(v.controller.currentFrameBuffer().colors()).allMatch(c -> c==0));
        }});
    }

    @Test void unchangedSceneRetainsFramePixels() throws Exception {
        FxTestSupport.run(() -> { try (var v = new UiRegressionTest.View()) {
            v.load(Path.of("Test_Models/cube.obj"));
            v.controller.renderFrame();
            var frame = v.controller.currentFrameBuffer();
            frame.colors()[0] = 0x12345678;
            v.controller.renderFrame();
            assertEquals(0x12345678, frame.colors()[0], "Unchanged scene must not rasterize again");
        }});
    }
}
