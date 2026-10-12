package com.cgengine;

import com.cgengine.math.Vector3f;
import com.cgengine.model.Model;
import com.cgengine.model.ModelValidator;
import com.cgengine.obj_io.reader.ObjReader;
import com.cgengine.render_engine.Camera;
import com.cgengine.render_engine.ColorRgb;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.canvas.Canvas;
import javafx.stage.Stage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("unchecked")
class UiRegressionTest {
    @TempDir Path directory;
    static final String OBJ = "v -1 -1 0\nv 1 -1 0\nv 0 1 0\nv 1 1 0\n"
            + "vt 0 0\nvt 1 0\nvt 0 1\nvt 1 1\nf 1/1 2/2 3/3\nf 2/2 4/4 3/3\n";

    static class Controller extends GuiController {
        File file;
        String error, info;
        @Override File chooseModelFile(boolean save) { return file; }
        @Override File chooseTextureFile() { return file; }
        @Override void showError(String title, String content) { error = title + ": " + content; }
        @Override void showInfo(String title, String content) { info = title + ": " + content; }
    }
    static class View implements AutoCloseable {
        final Controller controller;
        final Stage stage = new Stage();
        final Parent root;
        final java.util.Map<String, Object> namespace;
        View() throws Exception { this(new Controller()); }
        View(Controller controller) throws Exception {
            this.controller = controller;
            FXMLLoader loader = new FXMLLoader(Simple3DViewer.class.getResource("fxml/gui.fxml"));
            loader.setControllerFactory(type -> controller);
            root = loader.load();
            namespace = loader.getNamespace();
            stage.setScene(new Scene(root)); stage.show(); root.applyCss(); root.layout();
        }
        @SuppressWarnings("unchecked") <T> T control(String id) { return (T) namespace.get(id); }
        void text(String id, String value) { ((TextField) control(id)).setText(value); }
        void button(String label) {
            Accordion accordion = (Accordion) root.lookup(".accordion");
            Button button = null;
            for (TitledPane pane : accordion.getPanes()) {
                button = pane.getContent().lookupAll(".button").stream()
                        .filter(n -> ((Button)n).getText().equals(label)).map(n -> (Button)n).findFirst().orElse(null);
                if (button != null) { accordion.setExpandedPane(pane); root.applyCss(); root.layout(); break; }
            }
            if (button == null) throw new AssertionError("Missing FXML button: " + label);
            if (button.getOnAction() != null) button.fire(); else button.getOnMouseClicked().handle(null);
        }
        void menu(String label) {
            MenuBar bar = (MenuBar) root.lookup(".menu-bar");
            bar.getMenus().stream().flatMap(m -> m.getItems().stream()).filter(i -> i.getText().equals(label))
                    .findFirst().orElseThrow().fire();
        }
        ListView<Model> models() { return control("listViewModels"); }
        Model selected() { return models().getSelectionModel().getSelectedItem(); }
        void load(Path file) { controller.file = file.toFile(); menu("Load Model"); assertNull(controller.error); }
        @Override public void close() { controller.dispose(); stage.close(); }
    }

    @Test void removingNormalsThroughFxmlKeepsAmbientAcrossFrames() throws Exception {
        Path input = directory.resolve("remove-normals.obj"); Files.writeString(input, OBJ);
        FxTestSupport.run(() -> {
            try (View v = new View()) {
                v.load(input);
                v.text("VerticesToRemove", "0");
                ((CheckBox)v.control("checkboxRemoveNormals")).setSelected(true);
                v.button("OK");
                Model model = v.selected();
                assertNull(v.controller.error); assertEquals(1, model.polygons.size());
                for (int i=0; i<3; i++) v.controller.renderFrame();
                assertTrue(model.normals.isEmpty());
                assertTrue(model.polygons.getFirst().getNormalIndices().isEmpty());
                var frame = new com.cgengine.render_engine.Rasterization.FrameBuffer(100,100);
                Camera camera = new Camera(new com.cgengine.math.Vector2f((float)Math.PI,(float)(-Math.PI/2)),
                        5,new Vector3f(0,0,0),1,1,.1f,20);
                com.cgengine.render_engine.RenderEngine.renderFrame(frame,camera,List.of(model));
                assertTrue(java.util.Arrays.stream(frame.colors()).anyMatch(c -> c == 0xFF262626));
                assertTrue(java.util.Arrays.stream(frame.colors()).allMatch(c -> c == 0 || c == 0xFF262626));
            }
        });
    }

    @Test void realFxmlSupportsLoadSelectionColorTransformDeletionAndSave() throws Exception {
        Path input = directory.resolve("\u0442\u0440\u0435\u0443\u0433\u043e\u043b\u044c\u043d\u0438\u043a\u0438.obj"); Files.writeString(input, OBJ);
        Path output = directory.resolve("\u0440\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442.obj");
        FxTestSupport.run(() -> {
            try (View v = new View()) {
                v.load(input); Model a = v.selected(); List<Vector3f> original = new ArrayList<>(a.vertices);
                v.load(input); Model b = v.selected();
                v.text("inputForModelMoving", "1 2 3"); v.text("inputForModelRotation", "0 0 90");
                v.text("inputForModelScaling", "2 3 4"); v.button("Apply transform");
                List<Vector3f> movedB = new ArrayList<>(b.vertices);
                assertNotEquals(original, movedB);
                v.models().getSelectionModel().select(0);
                assertEquals("0.0 0.0 0.0", ((TextField)v.control("inputForModelMoving")).getText());
                v.text("inputForModelMoving", "3 0 0"); v.button("Apply transform"); v.button("Reset");
                assertEquals(original, a.vertices); assertEquals(movedB, b.vertices);
                v.text("polygonIndices", "0, 0"); ((ComboBox<String>)v.control("colorPicker")).setValue("Red");
                v.button("Apply"); assertEquals(ColorRgb.RED, a.polygons.get(0).getBaseColor());
                assertEquals(ColorRgb.WHITE, a.polygons.get(1).getBaseColor());
                v.button("All"); assertEquals("0, 1", ((TextField)v.control("polygonIndices")).getText());
                v.controller.resetPolygonColor(null);
                assertTrue(a.polygons.stream().allMatch(p -> p.getBaseColor() == ColorRgb.WHITE));
                v.text("polygonIndices", "0"); v.button("Delete selected polygons");
                assertEquals(1, a.polygons.size()); assertEquals(original, a.vertices); ModelValidator.validate(a);
                v.controller.file = output.toFile(); v.menu("Save Model"); assertNotNull(v.controller.info);
                assertEquals(1, ObjReader.read(Files.readString(output)).polygons.size());
                v.text("VerticesToRemove", "0"); v.button("OK");
                assertEquals(3, v.selected().vertices.size()); assertEquals(1, v.selected().polygons.size());
                v.controller.removeModel(null); assertSame(b, v.selected());
                v.controller.removeModel(null); assertTrue(v.models().getItems().isEmpty());
                v.controller.renderFrame(); assertNull(v.controller.error);
            }
        });
    }

    @Test void errorsAndCancelledPickersPreserveSceneAndRenderLoop() throws Exception {
        Path valid = directory.resolve("valid.obj"); Files.writeString(valid, OBJ);
        FxTestSupport.run(() -> {
            try (View v = new View()) {
                v.controller.resetPolygonColor(null); assertNotNull(v.controller.error); v.controller.error = null;
                v.load(valid); Model model = v.selected(); List<Vector3f> original = new ArrayList<>(model.vertices);
                for (String value : List.of("", "1 2", "1 2 3 4", "NaN 1 1", "Infinity 1 1", "0 1 1", "-1 1 1", "1,5 1 1")) {
                    v.controller.error = null; v.text("inputForModelScaling", value); v.button("Apply transform");
                    assertNotNull(v.controller.error, value); assertEquals(original, model.vertices); v.controller.renderFrame();
                }
                for (String value : List.of("", "0,", "-1", "0, 99", "word")) {
                    v.controller.error = null; v.text("polygonIndices", value); v.controller.resetPolygonColor(null);
                    assertNotNull(v.controller.error, value); assertEquals(2, model.polygons.size());
                }
                for (String bad : List.of("", "v nope 0 0", "v 0 0 0\nf 1 2 3", OBJ + "f 1 2")) {
                    Path file = directory.resolve("invalid.obj"); Files.writeString(file, bad);
                    v.controller.file = file.toFile(); v.controller.error = null; v.menu("Load Model");
                    assertNotNull(v.controller.error); assertEquals(1, v.models().getItems().size()); assertSame(model, v.selected());
                }
                v.controller.file = directory.resolve("missing/output.obj").toFile(); v.controller.error = null;
                v.menu("Save Model"); assertNotNull(v.controller.error);
                v.controller.file = null; v.controller.error = null; v.menu("Load Model"); v.menu("Save Model");
                assertNull(v.controller.error); assertEquals(1, v.models().getItems().size());
                for (int i = 0; i < 100; i++) v.controller.handleCameraForward(null);
                v.controller.renderFrame();
                v.controller.addCamera(null); v.controller.removeCamera(null);
                assertEquals(1, ((ListView<Camera>)v.control("listViewCameras")).getItems().size());
                v.controller.removeCamera(null); assertNotNull(v.controller.error); v.controller.renderFrame();
            }
        });
    }

    @Test void frameSelectedIsExplicitStableAndInitialCameraDoesNotClipOrigin() throws Exception {
        Path input = directory.resolve("frame.obj"); Files.writeString(input, OBJ);
        FxTestSupport.run(() -> {
            try (View v = new View()) {
                Camera initial = v.controller.currentCamera();
                assertTrue(initial.getNearPlane() < 100f);
                assertTrue(100f < initial.getFarPlane(), "Origin must be strictly before the default far plane");
                assertTrue(initial.getNearPlane() < 99f && 101f < initial.getFarPlane(),
                        "A small object around origin must not touch either default clipping plane");
                v.load(input);
                Camera firstFrame = v.controller.currentCamera();
                v.menu("Frame Selected");
                Camera repeatedFrame = v.controller.currentCamera();
                assertEquals(firstFrame.getTarget(), repeatedFrame.getTarget());
                assertEquals(firstFrame.getDistance(), repeatedFrame.getDistance());
                assertEquals(firstFrame.getNearPlane(), repeatedFrame.getNearPlane());
                assertEquals(firstFrame.getFarPlane(), repeatedFrame.getFarPlane());
                v.controller.renderFrame();
                assertTrue(java.util.Arrays.stream(v.controller.currentFrameBuffer().colors()).anyMatch(color -> color != 0));
            }
        });
    }

    @Test void cameraControlsAreObservableResettableAndIgnoreTextInput() throws Exception {
        FxTestSupport.run(() -> {
            try (View v = new View()) {
                Camera initial = v.controller.currentCamera();
                assertTrue(((Label) v.control("cameraStatusLabel")).getText().contains("distance 100.000"));
                assertNotNull(((MenuItem) v.control("moveForwardMenuItem")).getAccelerator(),
                        "Camera shortcut must be shown in the Controls menu");
                v.controller.handleCameraForward(null);
                assertEquals(90f, v.controller.currentCamera().getDistance());
                javafx.event.Event.fireEvent(v.stage.getScene(), key(KeyCode.W, false, false));
                assertEquals(90f, v.controller.currentCamera().getDistance(), "Pressed only sets held state");
                for (int i=0;i<60;i++) v.controller.updateCameraMotion(1.0/60);
                assertEquals(90*Math.exp(-.70*.940),v.controller.currentCamera().getDistance(),.001);
                javafx.event.Event.fireEvent(v.stage.getScene(),new KeyEvent(KeyEvent.KEY_RELEASED,"","",KeyCode.W,false,false,false,false));
                for (int i=0;i<12;i++) v.controller.updateCameraMotion(.01);
                float stopped=v.controller.currentCamera().getDistance();
                assertEquals(90*Math.exp(-.70),stopped,.001);
                TextField field = v.control("inputForModelMoving"); field.requestFocus();
                javafx.event.Event.fireEvent(field, key(KeyCode.W, false, false));
                for (int i=0;i<60;i++) v.controller.updateCameraMotion(1.0/60);
                assertEquals(stopped, v.controller.currentCamera().getDistance(), "Text input must keep navigation keys");
                v.menu("Reset Camera");
                Camera reset = v.controller.currentCamera();
                assertEquals(100f, reset.getDistance());
                assertEquals(initial.getTarget(), reset.getTarget());
                assertTrue(((Label) v.control("cameraStatusLabel")).getText().contains("near/far"));
            }
        });
    }

    @Test void invalidModelGetsListStatusWithoutBreakingValidModelsOrShowingDialog() throws Exception {
        Path input = directory.resolve("valid.obj"); Files.writeString(input, OBJ);
        FxTestSupport.run(() -> {
            try (View v = new View()) {
                v.load(input); Model bad = v.selected();
                v.load(input); Model good = v.selected();
                Vector3f original = bad.vertices.getFirst();
                bad.vertices.set(0, new Vector3f(Float.NaN, 0, 0));
                v.controller.renderFrame();
                assertEquals("Invalid vertex", v.controller.renderError(bad));
                assertNull(v.controller.renderError(good));
                assertTrue(java.util.Arrays.stream(v.controller.currentFrameBuffer().colors()).anyMatch(color -> color != 0));
                assertNull(v.controller.error, "Render diagnostics must not open an alert every frame");
                bad.vertices.set(0, original);
                v.controller.renderFrame();
                assertNull(v.controller.renderError(bad), "Successful rendering clears the stale error");
            }
        });
    }

    private static KeyEvent key(KeyCode code, boolean control, boolean shift) {
        return new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, control, false, false);
    }

    @Test void textureFailuresAreAtomicAndSeparateRemovalKeepsUvAndColors() throws Exception {
        Path input = directory.resolve("model.obj"); Files.writeString(input, OBJ);
        java.awt.image.BufferedImage pixels = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        pixels.setRGB(0, 0, 0xFF0000); pixels.setRGB(1, 0, 0x00FF00);
        Path png = directory.resolve("texture.png"); javax.imageio.ImageIO.write(pixels, "png", png.toFile());
        Path oversized = directory.resolve("large.png");
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(4097, 1, 1), "png", oversized.toFile());
        Path corrupt = directory.resolve("bad.png"); Files.writeString(corrupt, "invalid image");
        FxTestSupport.run(() -> {
            try (View v = new View()) {
                v.load(input); Model model = v.selected();
                v.controller.file = png.toFile(); v.button("Texture"); assertNotNull(model.getTexture());
                var texture = model.getTexture();
                for (Path bad : List.of(oversized, corrupt, directory.resolve("missing.png"), directory.resolve("bad.gif"))) {
                    v.controller.file = bad.toFile(); v.controller.error = null; v.button("Texture");
                    assertNotNull(v.controller.error); assertSame(texture, model.getTexture()); v.controller.renderFrame();
                }
                v.text("polygonIndices", "0"); v.controller.resetPolygonColor(null); assertSame(texture, model.getTexture());
                v.button("Clear texture"); assertNull(model.getTexture()); assertEquals(4, model.textureVertices.size());
                assertEquals(ColorRgb.WHITE, model.polygons.getFirst().getBaseColor());
                model.polygons.forEach(p -> p.setTextureVertexIndices(new ArrayList<>()));
                v.controller.error = null; v.button("Texture"); assertNotNull(v.controller.error);
                v.controller.renderFrame();
            }
        });
    }
}
