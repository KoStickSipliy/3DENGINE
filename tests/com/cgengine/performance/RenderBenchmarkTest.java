package com.cgengine.performance;

import com.cgengine.math.Vector2f;
import com.cgengine.math.Vector3f;
import com.cgengine.model.Model;
import com.cgengine.model.Polygon;
import com.cgengine.render_engine.Camera;
import com.cgengine.render_engine.RenderEngine;
import com.cgengine.render_engine.RenderMetrics;
import com.cgengine.render_engine.Rasterization.FrameBuffer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** D-01 CPU benchmark. Its generated triangles intentionally cover only small screen areas. */
public class RenderBenchmarkTest {
    private static final int WIDTH = 1280, HEIGHT = 720, TRIANGLES = 10_000;

    @Test void meetsD01FrameAndHeapLimits() {
        Model model = scene();
        Camera camera = new Camera(new Vector2f(0, (float) Math.PI / 2), 5, new Vector3f(0, 0, 0), 1, (float) WIDTH / HEIGHT, .1f, 10);
        FrameBuffer frameBuffer = new FrameBuffer(WIDTH, HEIGHT);
        int runs = Integer.getInteger("cgengine.benchmark.runs", 3);
        for (int run = 0; run < runs; run++) {
            for (int i = 0; i < 150; i++) RenderEngine.renderFrame(frameBuffer, camera, List.of(model));
            RenderMetrics metrics = new RenderMetrics(900);
            for (int frame = 0; frame < 900; frame++) {
                long started = System.nanoTime();
                RenderEngine.renderFrame(frameBuffer, camera, List.of(model));
                metrics.recordNanos(System.nanoTime() - started);
            }
            System.out.printf("D-01 run %d: mean=%.3fms p95=%.3fms max=%.3fms%n", run + 1, metrics.meanMillis(), metrics.p95Millis(), metrics.maxMillis());
            assertTrue(metrics.meanMillis() <= 33.33, "mean frame time");
            assertTrue(metrics.p95Millis() <= 50, "p95 frame time");
            assertTrue(metrics.maxMillis() < 200, "maximum frame time");
        }
        if (Boolean.parseBoolean(System.getProperty("cgengine.benchmark.soak", "true"))) {
            forceGc(); long heapBefore = usedHeap();
            for (int frame = 0; frame < 1_800; frame++) RenderEngine.renderFrame(frameBuffer, camera, List.of(model));
            forceGc(); long growth = usedHeap() - heapBefore;
            System.out.printf("D-01 environment: JDK=%s; OS=%s; processors=%d; maxHeap=%d MiB; heap growth=%d MiB%n", System.getProperty("java.version"), System.getProperty("os.name"), Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() / (1024 * 1024), growth / (1024 * 1024));
            assertTrue(growth <= 64L * 1024 * 1024, "heap growth must be at most 64 MiB");
        }
    }

    public static Model scene() {
        Model model = new Model();
        for (int index = 0; index < TRIANGLES; index++) {
            int column = index % 100, row = index / 100;
            float x = -1.8f + column * .036f, y = -1.0f + row * .020f;
            int base = model.vertices.size();
            model.vertices.add(new Vector3f(x, y, 0)); model.vertices.add(new Vector3f(x + .012f, y, 0)); model.vertices.add(new Vector3f(x, y + .009f, 0));
            Polygon polygon = new Polygon(); polygon.setVertexIndices(new ArrayList<>(List.of(base, base + 1, base + 2))); model.polygons.add(polygon);
        }
        model.computeNormals();
        return model;
    }
    private static long usedHeap() { Runtime runtime = Runtime.getRuntime(); return runtime.totalMemory() - runtime.freeMemory(); }
    private static void forceGc() { System.gc(); System.gc(); }
}
