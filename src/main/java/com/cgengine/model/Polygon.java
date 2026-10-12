package com.cgengine.model;

import java.util.ArrayList;
import java.util.Objects;
import com.cgengine.render_engine.ColorRgb;
import com.cgengine.math.Vector3f;
import com.cgengine.math.VectorMath;

public class Polygon implements Cloneable {

    private ArrayList<Integer> vertexIndices;
    private ArrayList<Integer> textureVertexIndices;
    private ArrayList<Integer> normalIndices;
    private ColorRgb baseColor = ColorRgb.WHITE;
    private Vector3f normalA, normalB, normalC, geometricNormal;

    /** Immutable vertex references invalidate the cache on geometry or winding changes. */
    public Vector3f geometricNormal(Vector3f a, Vector3f b, Vector3f c) {
        if (a != normalA || b != normalB || c != normalC || geometricNormal == null) {
            Vector3f cross = VectorMath.cross(VectorMath.subtract(b, a), VectorMath.subtract(c, a));
            float length = VectorMath.length(cross);
            geometricNormal = length == 0 || !Float.isFinite(length)
                    ? new Vector3f(0, 0, 0) : VectorMath.normalize(cross);
            normalA = a; normalB = b; normalC = c;
        }
        return geometricNormal;
    }

    public Polygon() {
        vertexIndices = new ArrayList<>();
        textureVertexIndices = new ArrayList<>();
        normalIndices = new ArrayList<>();
    }

    // Метод clone
    @Override
    public Polygon clone() {
        try {
            Polygon clonedPolygon = (Polygon) super.clone();

            // Глубокое копирование списков
            clonedPolygon.vertexIndices = new ArrayList<>(this.vertexIndices);
            clonedPolygon.textureVertexIndices = new ArrayList<>(this.textureVertexIndices);
            clonedPolygon.normalIndices = new ArrayList<>(this.normalIndices);
            clonedPolygon.baseColor = this.baseColor;

            return clonedPolygon;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(); // Этот код никогда не должен выполняться
        }
    }

    public void setVertexIndices(ArrayList<Integer> vertexIndices) {
        this.vertexIndices = vertexIndices;
    }

    public void setTextureVertexIndices(ArrayList<Integer> textureVertexIndices) {
        this.textureVertexIndices = textureVertexIndices;
    }

    public void setNormalIndices(ArrayList<Integer> normalIndices) {
        this.normalIndices = normalIndices;
    }

    public ArrayList<Integer> getVertexIndices() {
        return vertexIndices;
    }

    public ArrayList<Integer> getTextureVertexIndices() {
        return textureVertexIndices;
    }

    public ArrayList<Integer> getNormalIndices() {
        return normalIndices;
    }
    public ColorRgb getBaseColor() { return baseColor; }
    public void setBaseColor(ColorRgb baseColor) { this.baseColor = Objects.requireNonNull(baseColor); }

    //метод триангуляции одного полигона
    public ArrayList<Polygon> triangulate() {
        int vertexNum = vertexIndices.size();
        ArrayList<Polygon> triangles = new ArrayList<>();

        if (vertexNum == 3) {
            triangles.add(this);
            return triangles;
        }
        throw new ModelValidationException("Only triangular faces are supported");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Polygon polygon)) return false;
        return Objects.equals(vertexIndices, polygon.vertexIndices) && Objects.equals(textureVertexIndices, polygon.textureVertexIndices) && Objects.equals(normalIndices, polygon.normalIndices) && Objects.equals(baseColor, polygon.baseColor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vertexIndices, textureVertexIndices, normalIndices, baseColor);
    }
}
