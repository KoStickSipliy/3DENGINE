package com.cgengine;

import com.cgengine.math.Vector2f;
import com.cgengine.math.Vector3f;
import com.cgengine.model.Model;
import com.cgengine.obj_io.writer.ObjWriter;
import com.cgengine.performance.RenderBenchmarkTest;
import com.cgengine.render_engine.Camera;
import com.cgengine.render_engine.RenderMetrics;
import com.cgengine.render_engine.Texture2D;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.ListView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Same controller/Canvas path as production, one frame per queued FX action. */
class FxRenderBenchmarkTest {
    @TempDir Path directory;
    @Test void texturedCanvasMeetsD01WithQueuedSelectionActions() throws Exception {
        Model source = RenderBenchmarkTest.scene();
        source.textureVertices.addAll(List.of(new Vector2f(0,0), new Vector2f(1,0), new Vector2f(0,1)));
        source.polygons.forEach(p -> p.setTextureVertexIndices(new ArrayList<>(List.of(0,1,2))));
        Path fixture = directory.resolve("benchmark.obj"); new ObjWriter().write(source, fixture.toString());
        CompletableFuture<Void> finished = new CompletableFuture<>();
        FxTestSupport.run(() -> {
            UiRegressionTest.View view = new UiRegressionTest.View();
            try {
                view.load(fixture); view.controller.dispose();
                view.selected().setTexture(new Texture2D(2,2,new int[]{0xFFFF0000,0xFF00FF00,0xFF0000FF,0xFFFFFFFF}));
                view.controller.addCamera(null);
                ListView<Camera> cameras = view.control("listViewCameras");
                for (Camera camera : cameras.getItems()) {
                    camera.moveDistance(5 - camera.getDistance()); camera.setRotation(new Vector2f(0,(float)Math.PI/2));
                }
                Canvas canvas = view.control("canvas"); canvas.setWidth(1280); canvas.setHeight(720);
                new Runner(view, cameras, finished).run();
            } catch (Throwable failure) { view.close(); finished.completeExceptionally(failure); }
        });
        finished.get(6, TimeUnit.MINUTES);
    }

    static final class Runner implements Runnable {
        final UiRegressionTest.View view;
        final ListView<Camera> cameras;
        final CompletableFuture<Void> finished;
        RenderMetrics metrics = new RenderMetrics(900);
        int run, frame = -150, actions;
        long heapBefore;
        Runner(UiRegressionTest.View view, ListView<Camera> cameras, CompletableFuture<Void> finished) {
            this.view=view; this.cameras=cameras; this.finished=finished;
        }
        @Override public void run() {
            try {
                if (run == 3 && frame == 0) { System.gc(); heapBefore = usedHeap(); }
                long started = System.nanoTime();
                view.controller.invalidateFrame(); // Benchmark full rendering, including unchanged scenes.
                view.controller.renderFrame();
                long duration = System.nanoTime() - started;
                if (run < 3 && frame >= 0) {
                    metrics.recordNanos(duration);
                    if (frame % 90 == 0) Platform.runLater(() -> {
                        cameras.getSelectionModel().select(1-cameras.getSelectionModel().getSelectedIndex());
                        actions++;
                    });
                }
                frame++;
                if (run < 3 && frame == 900) {
                    System.out.printf(java.util.Locale.ROOT,
                            "D-01 Canvas+texture run %d: mean=%.3fms p95=%.3fms max=%.3fms actions=%d%n",
                            run+1,metrics.meanMillis(),metrics.p95Millis(),metrics.maxMillis(),actions);
                    assertTrue(metrics.meanMillis() <= 33.33, "Canvas mean");
                    assertTrue(metrics.p95Millis() <= 50, "Canvas p95");
                    assertTrue(metrics.maxMillis() < 200, "Canvas max");
                    assertEquals(10,actions,"Queued camera selections");
                    run++; frame=-150; actions=0; metrics=new RenderMetrics(900);
                } else if (run == 3 && frame == 1800) {
                    System.gc(); long growth=usedHeap()-heapBefore;
                    System.out.printf("D-01 Canvas soak: heap growth=%d bytes; JDK=%s; OS=%s; CPU=%s%n",
                            growth,System.getProperty("java.version"),System.getProperty("os.name"),System.getenv("PROCESSOR_IDENTIFIER"));
                    assertTrue(growth <= 64L*1024*1024,"Canvas heap growth");
                    assertNull(view.controller.error);
                    view.close(); finished.complete(null); return;
                }
                Platform.runLater(this);
            } catch (Throwable failure) { view.close(); finished.completeExceptionally(failure); }
        }
        static long usedHeap() { return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory(); }
    }
}
