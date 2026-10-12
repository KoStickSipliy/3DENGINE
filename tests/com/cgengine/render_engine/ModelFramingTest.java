package com.cgengine.render_engine;

import com.cgengine.math.Vector3f;
import com.cgengine.model.Model;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelFramingTest {
    private static final float FOV = 1f;
    private static final float ASPECT = 16f / 9f;

    @Test void cubeFrameCentersModelAndKeepsCenterStrictlyInsideFrustum() {
        ModelFraming.Frame frame = ModelFraming.calculate(model(
                new Vector3f(-1, -1, -1), new Vector3f(1, 1, 1)), FOV, ASPECT);
        assertEquals(new Vector3f(0, 0, 0), frame.target());
        assertTrue(frame.nearPlane() < frame.distance());
        assertTrue(frame.distance() < frame.farPlane());
    }

    @Test void thinAndPointModelsReceiveFiniteNonDegenerateFrames() {
        for (Model model : new Model[]{
                model(new Vector3f(-5, .01f, 10), new Vector3f(5, .01f, 10)),
                model(new Vector3f(99, -22, .5f))}) {
            ModelFraming.Frame frame = ModelFraming.calculate(model, FOV, ASPECT);
            assertTrue(Float.isFinite(frame.distance()));
            assertTrue(frame.distance() > frame.nearPlane());
            assertTrue(frame.farPlane() > frame.distance());
        }
    }

    @Test void largeModelAwayFromOriginIsCenteredWithoutChangingItsVertices() {
        Model model = model(new Vector3f(1_000_000, -2_000_000, 3_000_000),
                new Vector3f(1_004_000, -1_994_000, 3_008_000));
        ModelFraming.Frame frame = ModelFraming.calculate(model, FOV, ASPECT);
        assertEquals(new Vector3f(1_002_000, -1_997_000, 3_004_000), frame.target());
        assertEquals(new Vector3f(1_000_000, -2_000_000, 3_000_000), model.vertices.getFirst());
    }

    @Test void emptyModelExplainsWhyItCannotBeFramed() {
        assertThrows(IllegalArgumentException.class, () -> ModelFraming.calculate(new Model(), FOV, ASPECT));
    }

    private static Model model(Vector3f... vertices) {
        Model model = new Model();
        model.vertices.addAll(java.util.List.of(vertices));
        return model;
    }
}
