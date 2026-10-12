package com.cgengine;

import com.cgengine.model.Model;
import com.cgengine.render_engine.Camera;
import java.util.List;

/** Exact content comparison also detects edits through Model's public lists. */
final class RenderState {
    private final int width, height;
    private final List<Model> originals, copies;
    private final List<Object> cameraValues;

    RenderState(int width, int height, Camera camera, List<Model> models) {
        this.width = width; this.height = height;
        cameraValues = cameraValues(camera);
        originals = List.copyOf(models);
        copies = models.stream().map(RenderState::copy).toList();
    }

    boolean matches(int width, int height, Camera camera, List<Model> models) {
        if (this.width != width || this.height != height || !cameraValues.equals(cameraValues(camera))
                || models.size() != originals.size()) return false;
        for (int i = 0; i < models.size(); i++) {
            Model model = models.get(i), copy = copies.get(i);
            if (model != originals.get(i) || model.getTexture() != copy.getTexture() || !model.equals(copy)) return false;
        }
        return true;
    }

    private static List<Object> cameraValues(Camera c) {
        return List.of(c.getRotation(), c.getTarget(), c.getDistance(), c.getFov(),
                c.getAspectRatio(), c.getNearPlane(), c.getFarPlane());
    }

    private static Model copy(Model source) {
        Model copy = new Model();
        copy.vertices = new java.util.ArrayList<>(source.vertices);
        copy.textureVertices = new java.util.ArrayList<>(source.textureVertices);
        copy.normals = new java.util.ArrayList<>(source.normals);
        for (var polygon : source.polygons) copy.polygons.add(polygon == null ? null : polygon.clone());
        copy.setTexture(source.getTexture());
        return copy;
    }
}
