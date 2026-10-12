package com.cgengine;

import com.cgengine.model.Model;
import com.cgengine.model.ModelValidator;
import com.cgengine.obj_io.reader.ObjReader;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.stage.Screen;
import javafx.stage.Stage;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Read-only baseline inventory; run against the preserved or freshly built JAR. */
public final class BaselineProbe {
    private static int root(int[] parents, int i) {
        while (parents[i] != i) { parents[i] = parents[parents[i]]; i = parents[i]; }
        return i;
    }
    public static void main(String[] args) throws Exception {
        System.out.println("javafx.runtime.version=" + System.getProperty("javafx.runtime.version"));
        System.out.println("path\tstatus\tvertices\ttriangles\tcomponentSizes\tsignedVolume\tdetail");
        List<Path> paths = new ArrayList<>();
        for (String directory : List.of("Test_Models", "tests/fixtures/baseline")) {
            try (var stream = Files.walk(Path.of(directory))) {
                stream.filter(p -> p.toString().endsWith(".obj")).sorted().forEach(paths::add);
            }
        }
        for (String name : List.of("WrapJaw", "WrapSkull", "WrapLowerTeeth", "WrapUpperTeeth",
                "WrapBody", "WrapHand", "WrapHead", "WrapHeadWithMouthSocket", "WrapFemaleBody"))
            paths.add(Path.of("3DModels_DELETE/Faceform/" + name + ".obj"));
        paths.add(Path.of("3DModels_DELETE/Faceform/AlexWithTexture/AlexNeutralWrapped.obj"));
        paths.add(Path.of("3DModels_DELETE/eo53jaydu328-Rigged_Hand/Rigged_Hand/Rigged_Hand_obj/Rigged Hand.obj"));
        for (Path path : paths) {
            if (!Files.exists(path)) { System.out.println(path + "\tMISSING\t-\t-\t-\t-\tarchive absent"); continue; }
            try {
                Model m = ObjReader.read(Files.readString(path));
                ModelValidator.validate(m);
                int[] parents = new int[m.vertices.size()];
                for (int i = 0; i < parents.length; i++) parents[i] = i;
                double volume = 0;
                for (var face : m.polygons) {
                    var ids = face.getVertexIndices();
                    for (int id : ids) parents[root(parents, id)] = root(parents, ids.get(0));
                    var a = m.vertices.get(ids.get(0)); var b = m.vertices.get(ids.get(1)); var c = m.vertices.get(ids.get(2));
                    volume += ((double)a.x() * (b.y()*c.z()-b.z()*c.y())
                            + (double)a.y() * (b.z()*c.x()-b.x()*c.z())
                            + (double)a.z() * (b.x()*c.y()-b.y()*c.x())) / 6;
                }
                Map<Integer, Integer> sizes = new TreeMap<>();
                for (int i = 0; i < parents.length; i++) sizes.merge(root(parents, i), 1, Integer::sum);
                System.out.println(path + "\tIMPORTED\t" + m.vertices.size() + "\t" + m.polygons.size()
                        + "\t" + sizes.values().stream().sorted().toList() + "\t" + volume + "\tvalidated");
            } catch (RuntimeException e) {
                System.out.println(path + "\tREJECTED\t-\t-\t-\t-\t" + e.getClass().getSimpleName()
                        + ": " + e.getMessage().replace('\n', ' ').replace('\t', ' '));
            }
        }
        if (args.length > 0 && args[0].equals("--cpu-only")) return;
        Platform.startup(() -> Platform.setImplicitExit(false));
        CompletableFuture<Void> done = new CompletableFuture<>();
        Platform.runLater(() -> {
            Stage stage = new Stage();
            try {
                new Simple3DViewer().start(stage);
                stage.getScene().getRoot().applyCss(); stage.getScene().getRoot().layout();
                // Sample on the next event turn after native window sizing/layout.
                Platform.runLater(() -> {
                    try {
                        Canvas canvas = (Canvas)stage.getScene().lookup("#canvas");
                        System.out.println("javafx.runtime.version=" + System.getProperty("javafx.runtime.version"));
                        System.out.println("stage=" + stage.getWidth() + "x" + stage.getHeight());
                        System.out.println("scene=" + stage.getScene().getWidth() + "x" + stage.getScene().getHeight());
                        System.out.println("canvas=" + canvas.getWidth() + "x" + canvas.getHeight());
                        System.out.println("window.outputScale=" + stage.getOutputScaleX() + "x" + stage.getOutputScaleY());
                        for (Screen screen : Screen.getScreens())
                            System.out.println("screen=" + screen.getBounds() + "; dpi=" + screen.getDpi()
                                    + "; outputScale=" + screen.getOutputScaleX() + "x" + screen.getOutputScaleY());
                        done.complete(null);
                    } catch (Throwable e) { done.completeExceptionally(e); }
                    finally { stage.close(); Platform.exit(); }
                });
            } catch (Throwable e) { stage.close(); Platform.exit(); done.completeExceptionally(e); }
        });
        done.get(60, TimeUnit.SECONDS);
    }
}
