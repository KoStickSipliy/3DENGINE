package com.cgengine;

import com.cgengine.math.Vector2f;
import com.cgengine.math.Vector3f;
import com.cgengine.obj_io.reader.ObjReader;
import com.cgengine.render_engine.Camera;
import com.cgengine.render_engine.RenderEngine;
import com.cgengine.render_engine.PolygonFiller;
import com.cgengine.render_engine.Rasterization.FrameBuffer;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.canvas.Canvas;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Executed in a separate JVM with ONLY the distribution jar and this probe. */
public final class PackagedSmoke {
    public static void main(String[] args) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> {
            try {
                Stage stage = new Stage();
                new Simple3DViewer().start(stage);
                if (!stage.isShowing() || !"CGEngine".equals(stage.getTitle()))
                    throw new AssertionError("Application window did not open");
                var model = ObjReader.read(args.length == 0
                        ? "v -1 -1 0\nv 1 -1 0\nv 0 1 0\nf 1 2 3\n"
                        : Files.readString(Path.of(args[0])));
                float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
                float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
                for (Vector3f vertex : model.vertices) {
                    minX = Math.min(minX, vertex.x()); maxX = Math.max(maxX, vertex.x());
                    minY = Math.min(minY, vertex.y()); maxY = Math.max(maxY, vertex.y());
                    minZ = Math.min(minZ, vertex.z()); maxZ = Math.max(maxZ, vertex.z());
                }
                Vector3f center = new Vector3f((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
                float distance = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) * 3 + .1f;
                var camera = new Camera(new Vector2f(0, (float) Math.PI / 2), distance,
                        center, 1, 1, .01f, distance * 10);
                FrameBuffer frame = new FrameBuffer(64, 64);
                RenderEngine.renderFrame(frame, camera, List.of(model));
                int visiblePixel = -1;
                for (int i = 0; i < frame.colors().length; i++) {
                    if (frame.colors()[i] != 0) { visiblePixel = i; break; }
                }
                if (visiblePixel < 0) throw new AssertionError("Smoke OBJ did not render");
                Canvas canvas = (Canvas) stage.getScene().lookup("#canvas");
                PolygonFiller.publish(canvas.getGraphicsContext2D(), frame);
                int x = visiblePixel % frame.width(), y = visiblePixel / frame.width();
                if (canvas.snapshot(null, null).getPixelReader().getArgb(x, y) != frame.colorAt(x, y))
                    throw new AssertionError("Canvas upload failed");
                stage.close();
            } catch (Throwable error) { failure.set(error); }
            finally { finished.countDown(); }
        });
        if (!finished.await(30, TimeUnit.SECONDS)) throw new AssertionError("JavaFX startup timed out");
        Platform.exit();
        if (failure.get() != null) throw new AssertionError("Packaged smoke failed", failure.get());
        System.out.println("PACKAGED SMOKE PASSED: " + (args.length == 0 ? "triangle" : args[0])
                + "; FXML, Stage, OBJ, CPU pixels, Canvas");
    }
}
