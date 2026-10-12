package com.cgengine;

import com.cgengine.model.Model;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelSmokeTest {

    @Test
    void newModelStartsWithEmptyGeometry() {
        final Model model = new Model();

        assertTrue(model.vertices.isEmpty());
        assertTrue(model.textureVertices.isEmpty());
        assertTrue(model.normals.isEmpty());
        assertTrue(model.polygons.isEmpty());
    }
}
