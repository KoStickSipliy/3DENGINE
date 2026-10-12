package com.cgengine.obj_io;

import com.cgengine.model.Model;
import com.cgengine.obj_io.reader.ObjReader;
import com.cgengine.obj_io.reader.ObjReaderException;
import com.cgengine.obj_io.writer.ObjWriteException;
import com.cgengine.obj_io.writer.ObjWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ObjIoTest {
    private static final String HEAD = "v 0 0 0\nv 1 0 0\nv 0 1 0\n";

    @Test void parsesAllSupportedFaceFormsAndRecalculatesNormals() {
        for (String face : new String[]{"f 1 2 3", "f 1/1 2/2 3/3", "f 1//1 2//2 3//3", "f 1/1/1 2/2/2 3/3/3"}) {
            String texture = face.contains("/") && !face.contains("//") ? "vt 0 0\nvt 1 0\nvt 0 1\n" : "";
            String normals = face.contains("//") || face.contains("/1/") ? "vn 0 0 1\nvn 0 0 1\nvn 0 0 1\n" : "";
            Model model = ObjReader.read(HEAD + texture + normals + face);
            assertEquals(1, model.polygons.size());
            assertEquals(3, model.polygons.getFirst().getNormalIndices().size());
            assertEquals(1, model.normals.size());
        }
    }

    @Test void roundTripPreservesGeometryAndTextureIndices() throws Exception {
        Model initial = ObjReader.read(HEAD + "vt 0 0\nvt 1 0\nvt 0 1\nf 1/1 2/2 3/3\n");
        Path file = Files.createTempFile("cgengine-", ".obj");
        try {
            new ObjWriter().write(initial, file.toString());
            Model reloaded = ObjReader.read(Files.readString(file));
            assertEquals(initial.vertices, reloaded.vertices);
            assertEquals(initial.textureVertices, reloaded.textureVertices);
            assertEquals(initial.polygons, reloaded.polygons);
        } finally { Files.deleteIfExists(file); }
    }

    @Test void reportsLineForInvalidInput() {
        assertLine("", 0);
        assertLine(HEAD + "f 1 2 99", 4);
        assertLine(HEAD + "f 1 2", 4);
        assertLine(HEAD + "f 1 2 3 1", 4);
        assertLine("v nope 0 0", 1);
        assertLine(HEAD + "f -1 2 3", 4);
    }

    @Test void metadataAndConcavePolygonAreTriangulatedWithAlignedUv() throws Exception {
        String input = "mtllib ignored.mtl\no Named object\ng Part\ns 1\n"
                + "v 0 0 0\nv 3 0 0\nv 3 3 0\nv 1 1 0\nv 0 3 0\n"
                + "vt 0 0\nvt 3 0\nvt 3 3\nvt 1 1\nvt 0 3\n"
                + "vn 0 0 1\nusemtl Material\nf 1/1/1 2/2/1 3/3/1 4/4/1 5/5/1\n";
        Model model = ObjReader.read(input);
        assertEquals(3, model.polygons.size());
        for (var face : model.polygons) {
            assertEquals(3, face.getVertexIndices().size());
            assertEquals(face.getVertexIndices(), face.getTextureVertexIndices());
            assertEquals(3, face.getNormalIndices().size());
            for (int index : face.getNormalIndices()) {
                var normal = model.normals.get(index);
                assertEquals(0, normal.x(), 1e-5); assertEquals(0, normal.y(), 1e-5);
                assertEquals(1, normal.z(), 1e-5);
            }
        }
        Path file = Files.createTempFile("cgengine-polygon-", ".obj");
        try {
            new ObjWriter().write(model, file.toString());
            assertEquals(model.polygons.size(), ObjReader.read(Files.readString(file)).polygons.size());
        } finally { Files.deleteIfExists(file); }
    }

    @Test void writeFailureIsControlled() {
        Model model = ObjReader.read(HEAD + "f 1 2 3");
        assertThrows(ObjWriteException.class, () -> new ObjWriter().write(model, "\u0000invalid"));
    }

    private static void assertLine(String input, int line) {
        ObjReaderException error = assertThrows(ObjReaderException.class, () -> ObjReader.read(input));
        assertTrue(error.getMessage().contains("line: " + line), error::getMessage);
    }
}
