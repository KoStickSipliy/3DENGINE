package com.cgengine.obj_io.writer;

import com.cgengine.math.Vector2f;
import com.cgengine.math.Vector3f;
import com.cgengine.model.Model;
import com.cgengine.model.ModelValidator;
import com.cgengine.model.Polygon;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/** Serializes the same triangular OBJ subset accepted by {@code ObjReader}. */
public final class ObjWriter {
    public void write(final Model model, final String filename) {
        if (filename == null || filename.isBlank()) throw new ObjWriteException("Output filename is empty", null);
        ModelValidator.validate(model);
        if (model.vertices.isEmpty()) throw new ObjWriteException("Cannot save OBJ without vertices", null);
        try (BufferedWriter writer = Files.newBufferedWriter(Path.of(filename), StandardCharsets.UTF_8)) {
            for (Vector3f vertex : model.vertices) writer.write("v " + vertex.x() + " " + vertex.y() + " " + vertex.z() + "\n");
            for (Vector2f texture : model.textureVertices) writer.write("vt " + texture.x() + " " + texture.y() + "\n");
            for (Vector3f normal : model.normals) writer.write("vn " + normal.x() + " " + normal.y() + " " + normal.z() + "\n");
            for (Polygon polygon : model.polygons) writer.write(face(polygon) + "\n");
        } catch (IOException | InvalidPathException exception) { throw new ObjWriteException("Unable to write OBJ to '" + filename + "'", exception); }
    }
    private static String face(Polygon polygon) {
        List<Integer> vertices = polygon.getVertexIndices(), textures = polygon.getTextureVertexIndices(), normals = polygon.getNormalIndices();
        boolean hasTextures = textures.size() == 3, hasNormals = normals.size() == 3;
        StringBuilder result = new StringBuilder("f");
        for (int i = 0; i < 3; i++) {
            result.append(' ').append(vertices.get(i) + 1);
            if (hasTextures) result.append('/').append(textures.get(i) + 1);
            else if (hasNormals) result.append('/');
            if (hasNormals) result.append('/').append(normals.get(i) + 1);
        }
        return result.toString();
    }
}
