package com.cgengine;

import com.cgengine.math.Vector2f;
import com.cgengine.model.*;
import com.cgengine.obj_io.reader.ObjReader;
import com.cgengine.render_engine.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Combined edits and selection changes with the production timer running. */
class Stage11AcceptanceTest {
    @TempDir Path directory;

    @Test void coloredTransformedEditedModelsSurviveSelectionClippingResizeAndRoundTrip() throws Exception {
        var holder = new UiRegressionTest.View[1];
        try {
            Stage10UiTest.pulse(() -> {
                holder[0] = new UiRegressionTest.View();
                holder[0].load(Path.of("Test_Models/cube.obj"));
                holder[0].load(Path.of("Test_Models/icosahedron.obj"));
                holder[0].controller.addCamera(null);
                holder[0].models().getSelectionModel().select(0);
                holder[0].menu("Frame Selected");
                holder[0].button("All");
                ((ComboBox<String>)holder[0].control("colorPicker")).setValue("Red");
                holder[0].button("Apply");
                holder[0].text("inputForModelRotation", "25 40 15");
                holder[0].text("inputForModelScaling", "2 3 1");
                holder[0].button("Apply transform");
            });
            var v = holder[0];
            Model[] first = new Model[1];
            Stage10UiTest.pulse(() -> {
                first[0] = v.selected();
                v.text("polygonIndices", "0"); v.button("Delete selected polygons");
                assertEquals(11, v.selected().polygons.size());
                v.text("VerticesToRemove", "0"); v.button("OK");
                ModelValidator.validate(v.selected());
                first[0] = v.selected();
                assertTrue(first[0].polygons.stream().allMatch(p -> p.getBaseColor() == ColorRgb.RED));
                v.button("Reset"); v.menu("Frame Selected");
                v.models().getSelectionModel().select(1);
                assertEquals("", ((TextField)v.control("polygonIndices")).getText());
                assertTrue(v.selected().polygons.stream().allMatch(p -> p.getBaseColor() == ColorRgb.WHITE));
                ((ListView<Camera>)v.control("listViewCameras")).getSelectionModel().select(0);
                v.models().getSelectionModel().select(0); v.menu("Frame Selected");
            });
            FxTestSupport.run(() -> {
                assertSame(first[0], v.selected()); assertNull(v.controller.error);
                assertTrue(Arrays.stream(v.controller.currentFrameBuffer().colors()).anyMatch(c -> c != 0));
            });
            Path saved = directory.resolve("combined.obj");
            Stage10UiTest.pulse(() -> { v.controller.file = saved.toFile(); v.menu("Save Model"); });
            FxTestSupport.run(() -> {
                Model back = ObjReader.read(Files.readString(saved));
                Stage10UiTest.sameGeometry(first[0], back);
                assertTrue(back.polygons.stream().allMatch(p -> p.getBaseColor() == ColorRgb.WHITE));
            });
            Stage10UiTest.pulse(() -> {
                var c = v.controller.currentCamera();
                c.setRotation(new Vector2f((float)Math.PI, (float)-Math.PI/2));
                c.setNavigationDistance(.05f); // Camera intersects the edited surface.
                Canvas canvas = v.control("canvas"); canvas.setWidth(640); canvas.setHeight(360);
            });
            FxTestSupport.run(() -> {
                assertEquals(640, v.controller.currentFrameBuffer().width());
                assertNull(v.controller.renderError(v.selected()));
                v.controller.currentCamera().setNavigationDistance(100_000);
            });
            Stage10UiTest.pulse(() -> {});
            FxTestSupport.run(() -> assertTrue(v.controller.currentCamera().getFarPlane() > 100_000));
            Stage10UiTest.pulse(() -> {v.menu("Frame Selected");v.controller.removeModel(null);v.controller.removeModel(null);});
            FxTestSupport.run(() -> {
                assertTrue(v.models().getItems().isEmpty());
                assertTrue(Arrays.stream(v.controller.currentFrameBuffer().colors()).allMatch(c -> c == 0));
                CameraMotionUiTest.key(v, KeyCode.W, true, false, false);
                v.stage.setOnHidden(e -> v.controller.dispose()); v.stage.close();
                float distance = v.controller.currentCamera().getDistance();
                CameraMotionUiTest.advance(v, .5); assertEquals(distance, v.controller.currentCamera().getDistance());
            });
        } finally { FxTestSupport.run(() -> {if(holder[0] != null)holder[0].close();}); }
    }
}
