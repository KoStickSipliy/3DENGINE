package com.cgengine;

import com.cgengine.VertexDelete.Eraser;
import com.cgengine.math.*;
import com.cgengine.math.affine.*;
import com.cgengine.model.*;
import com.cgengine.obj_io.reader.ObjReader;
import com.cgengine.obj_io.writer.ObjWriter;
import com.cgengine.render_engine.*;
import com.cgengine.render_engine.Rasterization.FrameBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EndToEndRegressionTest {
    @TempDir Path directory;
    @Test void emptyExportFailsBeforeReplacingAnExistingFile() throws Exception {
        Path file = directory.resolve("existing.obj");
        Files.writeString(file, UiRegressionTest.OBJ);
        assertThrows(com.cgengine.obj_io.writer.ObjWriteException.class,
                () -> new ObjWriter().write(new Model(), file.toString()));
        assertEquals(UiRegressionTest.OBJ, Files.readString(file));
    }
    @Test void everyObjFaceFormSurvivesRenderEditAndUnicodeRoundTrip() throws Exception {
        for (String face : List.of("f 1 2 3", "f 1/1 2/2 3/3", "f 1//1 2//1 3//1", "f 1/1/1 2/2/1 3/3/1")) {
            Model model = ObjReader.read("v -1 -1 0\nv 1 -1 0\nv 0 1 0\nv 10 10 10\n"
                    + "vt 0 0\nvt 1 0\nvt 0 1\nvn 0 0 1\n" + face);
            model.polygons.getFirst().setBaseColor(ColorRgb.RED);
            FrameBuffer frame = new FrameBuffer(64, 64);
            Camera camera = new Camera(new Vector2f(0, (float)Math.PI / 2), 5, new Vector3f(0,0,0), 1,1,.1f,100);
            RenderEngine.renderFrame(frame, camera, List.of(model));
            assertTrue(Float.isFinite(frame.depthAt(32,32)));
            int[] first = frame.colors().clone();
            RenderEngine.renderFrame(frame, camera, List.of(model)); assertArrayEquals(first, frame.colors());
            Model edited = Eraser.vertexDelete(model, List.of(3), true, false, false, false);
            assertEquals(4, model.vertices.size()); assertEquals(3, edited.vertices.size());
            edited.applyTransformation(new Transformation(Rotator.degrees(180, Rotator.Axis.Z), new Scaling(2,3,4)));
            Path file = directory.resolve("модель.obj"); new ObjWriter().write(edited, file.toString());
            Model reloaded = ObjReader.read(Files.readString(file));
            assertEquals(edited.vertices, reloaded.vertices); ModelValidator.validate(reloaded);
            assertEquals(ColorRgb.WHITE, reloaded.polygons.getFirst().getBaseColor());
        }
    }
    @Test void overflowIsAtomicAndUnselectedLooseVerticesSurviveDeletion() {
        Model model = ObjReader.read(UiRegressionTest.OBJ + "v 10 10 10\n");
        List<Vector3f> before = new ArrayList<>(model.vertices);
        assertThrows(IllegalArgumentException.class, () -> model.applyTransformation(new Transformation(new Scaling(Float.MAX_VALUE,1,1))));
        assertEquals(before, model.vertices); ModelValidator.validate(model);
        Model result = Eraser.vertexDelete(model, List.of(0), false, false, false, false);
        assertEquals(before.subList(1, before.size()), result.vertices);
        Model empty = Eraser.vertexDelete(model, List.of(0,1,2,3,4), false,false,false,false);
        assertTrue(empty.vertices.isEmpty()); assertTrue(empty.polygons.isEmpty()); ModelValidator.validate(empty);
    }
    @Test void pngJpegLimitsAndActualFormatAreChecked() throws Exception {
        var image = new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB);
        image.setRGB(0,0,0xFF0000); image.setRGB(1,0,0x00FF00);
        image.setRGB(0,1,0x0000FF); image.setRGB(1,1,0xFFFFFF);
        for (String extension : List.of("png", "jpg", "jpeg")) {
            Path file = directory.resolve("image." + extension);
            javax.imageio.ImageIO.write(image, extension.equals("png") ? "png" : "jpeg", file.toFile());
            Texture2D texture = Texture2D.load(file.toFile()); assertEquals(2, texture.width());
            if (extension.equals("png")) {
                assertEquals(0xFFFF0000, TextureSampler.sample(texture, 0,1));
                assertEquals(0xFF00FF00, TextureSampler.sample(texture, 1,1));
                assertEquals(0xFF0000FF, TextureSampler.sample(texture, 0,0));
                assertEquals(0xFFFFFFFF, TextureSampler.sample(texture, 1,0));
                assertEquals(TextureSampler.sample(texture,0,1),TextureSampler.sample(texture,-.2f,1.4f));
            }
        }
        Path disguised = directory.resolve("gif.png"); javax.imageio.ImageIO.write(image,"gif",disguised.toFile());
        assertThrows(java.io.IOException.class, () -> Texture2D.load(disguised.toFile()));
        assertDoesNotThrow(() -> new Texture2D(4096,4096,new int[4096*4096]));
        assertThrows(IllegalArgumentException.class, () -> new Texture2D(4097,1,new int[4097]));
    }
    @Test void invalidModelDoesNotStopOtherModelsOrSubsequentFrames() {
        Model good = ObjReader.read(UiRegressionTest.OBJ), bad = good.clone();
        bad.vertices.set(0, new Vector3f(Float.NaN,0,0));
        FrameBuffer frame = new FrameBuffer(64,64);
        Camera camera = new Camera(new Vector2f(0,(float)Math.PI/2),5,new Vector3f(0,0,0),1,1,.1f,100);
        RenderEngine.renderFrame(frame,camera,List.of(good)); int[] expected = frame.colors().clone();
        RenderEngine.renderFrame(frame,camera,List.of(bad,good)); assertArrayEquals(expected,frame.colors());
        RenderEngine.renderFrame(frame,camera,List.of(good,bad)); assertArrayEquals(expected,frame.colors());
    }

    @Test void renderListenerReportsAndClearsOnlyTheInvalidModel() {
        Model good = ObjReader.read(UiRegressionTest.OBJ), bad = good.clone();
        bad.vertices.set(0, new Vector3f(Float.NaN, 0, 0));
        java.util.Map<Model, String> statuses = new java.util.IdentityHashMap<>();
        Camera camera = new Camera(new Vector2f(0,(float)Math.PI/2),5,new Vector3f(0,0,0),1,1,.1f,100);
        FrameBuffer frame = new FrameBuffer(64,64);
        RenderEngine.ModelRenderListener listener = new RenderEngine.ModelRenderListener() {
            @Override public void onRendered(Model model) { statuses.remove(model); }
            @Override public void onSkipped(Model model, String reason) { statuses.put(model, reason); }
        };
        RenderEngine.renderFrame(frame, camera, List.of(bad, good), listener);
        assertEquals("Invalid vertex", statuses.get(bad)); assertFalse(statuses.containsKey(good));
        bad.vertices.set(0, good.vertices.getFirst());
        RenderEngine.renderFrame(frame, camera, List.of(bad, good), listener);
        assertTrue(statuses.isEmpty());
    }

    @Test void textureStaysOnTheSameSurfacePointWhenCameraMoves() {
        Model model = ObjReader.read("v -1 -1 0\nv 1 -1 0\nv -1 1 0\nvt 0 0\nvt 1 0\nvt 0 1\nf 1/1 2/2 3/3");
        int[] pixels = new int[16];
        for (int i=0;i<16;i++) pixels[i]=0xFF000000 | ((i+1)*15<<16) | ((16-i)*15<<8);
        Texture2D texture = new Texture2D(4,4,pixels); model.setTexture(texture);
        Vector3f surface = new Vector3f(-.4f,-.2f,0); // UV = (0.3, 0.4), away from texel borders.
        FrameBuffer frame = new FrameBuffer(512,512);
        for (float angle : new float[]{0,.5f,1f}) {
            Camera camera = new Camera(new Vector2f(angle,(float)Math.PI/2),3,new Vector3f(0,0,0),1,1,.1f,100);
            RenderEngine.renderFrame(frame,camera,List.of(model));
            Matrix4f view = camera.getViewMatrix();
            Vector3f projected = GraphicConveyor.multiplyMatrix4ByVector3(MatrixMath.multiply(camera.getProjectionMatrix(),view),surface);
            Vector2f screen = GraphicConveyor.vertexToVec2(projected,512,512);
            Vector4f normal = MatrixMath.multiply(view,new Vector4f(0,0,1,0));
            int expected = Light.CAMERA_DIRECTIONAL.shade(TextureSampler.sample(texture,.3f,.4f),new Vector3f(normal.x(),normal.y(),normal.z()));
            assertEquals(expected,frame.colorAt((int)screen.x(),(int)screen.y()),"Camera angle " + angle);
        }
    }

    @Test void repeatedRejectedImportsLeaveNoTemporaryFiles() throws Exception {
        String previous = System.getProperty("java.io.tmpdir");
        try {
            System.setProperty("java.io.tmpdir",directory.toString());
            for (int i=0;i<100;i++) assertThrows(com.cgengine.obj_io.reader.ObjReaderException.class,
                    () -> ObjReader.read("v 0 0 0\nf 1 2 99"));
            try (var files=Files.list(directory)) { assertEquals(0,files.count()); }
        } finally { System.setProperty("java.io.tmpdir",previous); }
    }
}
