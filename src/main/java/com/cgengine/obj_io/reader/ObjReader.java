package com.cgengine.obj_io.reader;

import com.cgengine.math.Vector2f;
import com.cgengine.math.Vector3f;
import com.cgengine.model.Model;
import com.cgengine.model.ModelValidationException;
import com.cgengine.model.ModelValidator;
import com.cgengine.model.Polygon;

import java.util.ArrayList;
import java.util.List;

/** Parser for OBJ geometry; polygonal faces become triangles before reaching Model. */
public final class ObjReader {
    private ObjReader() { }

    public static Model read(final String fileContent) {
        if (fileContent == null) throw error("OBJ content is null", 0);
        Model model = new Model();
        String[] lines = fileContent.split("\\R", -1);
        boolean hasData = false;
        for (int lineNumber = 1; lineNumber <= lines.length; lineNumber++) {
            String line = withoutComment(lines[lineNumber - 1]).trim();
            if (line.isEmpty()) continue;
            hasData = true;
            String[] fields = line.split("\\s+");
            try {
                switch (fields[0]) {
                    case "v" -> model.vertices.add(vector3(fields, lineNumber, "vertex"));
                    case "vt" -> model.textureVertices.add(vector2(fields, lineNumber));
                    case "vn" -> model.normals.add(vector3(fields, lineNumber, "normal"));
                    case "f" -> model.polygons.addAll(face(fields, model, lineNumber));
                    // These OBJ statements describe names/material assignments/smoothing.
                    // CGEngine has one UI-assigned texture per model and recomputes normals.
                    case "o", "g", "s", "mtllib", "usemtl" -> { }
                    default -> throw error("Unsupported OBJ directive '" + fields[0] + "'", lineNumber);
                }
            } catch (ObjReaderException exception) { throw exception;
            } catch (NumberFormatException exception) { throw error("Invalid numeric value", lineNumber);
            } catch (ModelValidationException exception) { throw error(exception.getMessage(), lineNumber); }
        }
        if (!hasData) throw error("OBJ file is empty", 0);
        if (model.vertices.isEmpty()) throw error("OBJ file contains no vertices", 0);
        try {
            model.computeNormals();
            ModelValidator.validate(model);
            return model;
        } catch (ModelValidationException exception) { throw error(exception.getMessage(), 0); }
    }

    private static List<Polygon> face(String[] fields, Model model, int line) {
        if (fields.length < 4) throw error("Face must contain at least three vertices", line);
        List<Integer> vertices = new ArrayList<>(fields.length - 1), textures = new ArrayList<>(fields.length - 1), normals = new ArrayList<>(fields.length - 1);
        FaceForm form = null;
        for (int i = 1; i < fields.length; i++) {
            String[] index = fields[i].split("/", -1);
            FaceForm cornerForm = FaceForm.of(index, line);
            if (form == null) form = cornerForm;
            if (form != cornerForm) throw error("All face corners must use the same index form", line);
            vertices.add(index(index[0], model.vertices.size(), "vertex", line));
            if (cornerForm.hasTexture) textures.add(index(index[1], model.textureVertices.size(), "texture", line));
            if (cornerForm.hasNormal) normals.add(index(index[2], model.normals.size(), "normal", line));
        }
        return FaceTriangulator.triangulate(model, vertices, textures, normals, line);
    }

    private static int index(String token, int count, String kind, int line) {
        int oneBased;
        try { oneBased = Integer.parseInt(token); }
        catch (NumberFormatException exception) { throw error("Invalid " + kind + " index", line); }
        if (oneBased <= 0) throw error("Relative or zero " + kind + " indices are not supported", line);
        if (oneBased > count) throw error(kind + " index " + oneBased + " is out of range", line);
        return oneBased - 1;
    }
    private static Vector3f vector3(String[] fields, int line, String kind) {
        if (fields.length != 4) throw error("Expected three components for " + kind, line);
        return new Vector3f(Float.parseFloat(fields[1]), Float.parseFloat(fields[2]), Float.parseFloat(fields[3]));
    }
    private static Vector2f vector2(String[] fields, int line) {
        if (fields.length != 3 && fields.length != 4) throw error("Expected two or three components for texture coordinate", line);
        // OBJ's optional w is accepted but not used by the two-dimensional sampler.
        if (fields.length == 4 && !Float.isFinite(Float.parseFloat(fields[3])))
            throw error("Texture coordinate w must be finite", line);
        return new Vector2f(Float.parseFloat(fields[1]), Float.parseFloat(fields[2]));
    }
    private static String withoutComment(String line) { int comment = line.indexOf('#'); return comment < 0 ? line : line.substring(0, comment); }
    private static ObjReaderException error(String message, int line) { return new ObjReaderException(message, line); }

    private enum FaceForm {
        V(false, false), V_T(true, false), V_N(false, true), V_T_N(true, true);
        final boolean hasTexture, hasNormal;
        FaceForm(boolean hasTexture, boolean hasNormal) { this.hasTexture = hasTexture; this.hasNormal = hasNormal; }
        static FaceForm of(String[] parts, int line) {
            if (parts.length == 1 && !parts[0].isEmpty()) return V;
            if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) return V_T;
            if (parts.length == 3 && !parts[0].isEmpty() && parts[1].isEmpty() && !parts[2].isEmpty()) return V_N;
            if (parts.length == 3 && !parts[0].isEmpty() && !parts[1].isEmpty() && !parts[2].isEmpty()) return V_T_N;
            throw error("Unsupported face index form", line);
        }
    }
}
