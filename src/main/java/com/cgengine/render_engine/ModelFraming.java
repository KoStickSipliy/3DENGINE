package com.cgengine.render_engine;

import com.cgengine.math.Vector2f;
import com.cgengine.math.Vector3f;
import com.cgengine.math.VectorMath;
import com.cgengine.model.Model;

/** Calculates a camera that frames a model without changing its geometry. */
public final class ModelFraming {
    private static final float EPSILON = 0.001f;
    private static final float FIT_MARGIN = 1.15f;

    private ModelFraming() { }

    public record Frame(Vector3f target, float distance, float nearPlane, float farPlane) {
        public Frame {
            if (target == null || !VectorMath.isFinite(target) || !Float.isFinite(distance)
                    || !Float.isFinite(nearPlane) || !Float.isFinite(farPlane)
                    || distance <= 0 || nearPlane <= 0 || farPlane <= distance) {
                throw new IllegalArgumentException("Invalid model frame");
            }
        }

        public Camera createCamera(Vector2f rotation, float fov, float aspectRatio) {
            return new Camera(rotation, distance, target, fov, aspectRatio, nearPlane, farPlane);
        }
    }

    public static Frame calculate(Model model, float fov, float aspectRatio) {
        if (model == null || model.vertices == null || model.vertices.isEmpty()) {
            throw new IllegalArgumentException("Selected model has no vertices to frame.");
        }
        if (!Float.isFinite(fov) || fov <= 0 || !Float.isFinite(aspectRatio) || aspectRatio <= 0) {
            throw new IllegalArgumentException("Invalid viewport or field of view.");
        }

        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (Vector3f vertex : model.vertices) {
            if (vertex == null || !VectorMath.isFinite(vertex)) {
                throw new IllegalArgumentException("Selected model contains an invalid vertex.");
            }
            minX = Math.min(minX, vertex.x()); maxX = Math.max(maxX, vertex.x());
            minY = Math.min(minY, vertex.y()); maxY = Math.max(maxY, vertex.y());
            minZ = Math.min(minZ, vertex.z()); maxZ = Math.max(maxZ, vertex.z());
        }

        Vector3f target = new Vector3f(minX + (maxX - minX) / 2f,
                minY + (maxY - minY) / 2f, minZ + (maxZ - minZ) / 2f);
        float radius = Math.max(EPSILON, (float) (Math.sqrt(
                square(maxX - minX) + square(maxY - minY) + square(maxZ - minZ)) / 2d));
        float verticalHalfFov = fov / 2f;
        float horizontalHalfFov = (float) Math.atan(Math.tan(verticalHalfFov) * aspectRatio);
        float limitingHalfFov = Math.min(verticalHalfFov, horizontalHalfFov);
        if (!Float.isFinite(limitingHalfFov) || limitingHalfFov <= 0 || limitingHalfFov >= Math.PI / 2) {
            throw new IllegalArgumentException("Field of view cannot frame a model.");
        }
        float distance = Math.max(EPSILON * 10f, radius / (float) Math.sin(limitingHalfFov) * FIT_MARGIN);
        float nearPlane = Math.max(EPSILON, distance / 1_000f);
        float farPlane = distance + radius * 2f + Math.max(EPSILON, radius * 0.1f);
        return new Frame(target, distance, nearPlane, farPlane);
    }

    private static double square(float value) { return (double) value * value; }
}
