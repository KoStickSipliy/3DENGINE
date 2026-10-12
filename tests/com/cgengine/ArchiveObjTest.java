package com.cgengine;

import com.cgengine.math.Vector2f;
import com.cgengine.math.Vector3f;
import com.cgengine.model.Model;
import com.cgengine.model.ModelValidator;
import com.cgengine.obj_io.reader.ObjReader;
import com.cgengine.obj_io.writer.ObjWriter;
import com.cgengine.render_engine.Camera;
import com.cgengine.render_engine.RenderEngine;
import com.cgengine.render_engine.Rasterization.FrameBuffer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Local archive regression; archived sample assets are intentionally not versioned. */
class ArchiveObjTest {
    @TempDir Path directory;

    @Test void caracalCubeLoadsRendersAndRoundTrips() throws Exception {
        exercise(Path.of("3DModels_DELETE/CaracalCube/caracal_cube.obj"), 8, 12);
    }

    @Test void riggedHandLoadsRendersAndRoundTrips() throws Exception {
        exercise(Path.of("3DModels_DELETE/eo53jaydu328-Rigged_Hand/Rigged_Hand/Rigged_Hand_obj/Rigged Hand.obj"),
                1086, 2072);
    }

    private void exercise(Path path, int vertices, int triangles) throws Exception {
        Assumptions.assumeTrue(Files.exists(path), "Optional local OBJ archive is absent");
        Model model = ObjReader.read(Files.readString(path));
        ModelValidator.validate(model);
        assertEquals(vertices, model.vertices.size());
        assertEquals(triangles, model.polygons.size());
        assertTrue(model.polygons.stream().allMatch(p -> p.getVertexIndices().size() == 3));

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (Vector3f vertex : model.vertices) {
            minX = Math.min(minX, vertex.x()); maxX = Math.max(maxX, vertex.x());
            minY = Math.min(minY, vertex.y()); maxY = Math.max(maxY, vertex.y());
            minZ = Math.min(minZ, vertex.z()); maxZ = Math.max(maxZ, vertex.z());
        }
        Vector3f center = new Vector3f((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2);
        float distance = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ)) * 3 + .1f;
        Camera camera = new Camera(new Vector2f(0, (float) Math.PI / 2), distance, center, 1, 1, .01f, distance * 10);
        FrameBuffer frame = new FrameBuffer(256, 256);
        RenderEngine.renderFrame(frame, camera, List.of(model));
        assertTrue(frameHasPixels(frame), "Imported model must be visible to the CPU renderer");

        FxTestSupport.run(() -> {
            try (UiRegressionTest.View view = new UiRegressionTest.View()) {
                view.load(path);
                assertEquals(triangles, view.selected().polygons.size());
                view.controller.renderFrame();
                assertTrue(frameHasPixels(view.controller.currentFrameBuffer()),
                        "A first OBJ loaded through GuiController must be visible on the Canvas framebuffer");
                Camera framed = view.controller.currentCamera();
                assertTrue(framed.getNearPlane() < framed.getDistance());
                assertTrue(framed.getDistance() < framed.getFarPlane(),
                        "The AABB center must be strictly in front of the far plane");
                assertNull(view.controller.error);
            }
        });

        Path output = directory.resolve(path.getFileName());
        new ObjWriter().write(model, output.toString());
        Model reloaded = ObjReader.read(Files.readString(output));
        ModelValidator.validate(reloaded);
        assertEquals(vertices, reloaded.vertices.size());
        assertEquals(triangles, reloaded.polygons.size());
        assertEquals(model.vertices, reloaded.vertices);
        assertEquals(model.textureVertices, reloaded.textureVertices);
        assertEquals(model.polygons, reloaded.polygons);
    }

    private static boolean frameHasPixels(FrameBuffer frame) {
        for (int color : frame.colors()) if (color != 0) return true;
        return false;
    }
}
