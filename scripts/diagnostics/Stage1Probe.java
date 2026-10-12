package com.cgengine;

import javafx.application.Platform;
import javafx.animation.AnimationTimer;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import com.cgengine.model.Model;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Diagnostic scenario, deliberately outside the mandatory JUnit/CI suite. */
public final class Stage1Probe {
    static final ScheduledExecutorService monitor = Executors.newScheduledThreadPool(2);
    static final AtomicLong pending = new AtomicLong();
    static final AtomicInteger dumpSeries = new AtomicInteger();
    static final CompletableFuture<Void> finished = new CompletableFuture<>();
    static final List<Runnable> steps = new ArrayList<>();
    static Stage stage;
    static Parent root;
    static Map<String,Object> namespace;
    static Controller controller;
    static Path model;
    static Path output;
    static int completed;
    static volatile boolean closing;
    static AnimationTimer pulseMonitor;

    static final class Controller extends GuiController {
        @Override File chooseModelFile(boolean save) {
            if (Boolean.getBoolean("cgengine.diagnostics.nativeDialogs")) return super.chooseModelFile(save);
            return save ? null : model.toFile();
        }
        @Override void showError(String title, String content) { throw new IllegalStateException(title + ": " + content); }
    }
    static String context() {
        if (controller == null) return "model=minimal canvas=none";
        Canvas canvas = (Canvas)namespace.get("canvas");
        var camera = controller.currentCamera();
        return "model=" + model + " models=" + ((ListView<?>)namespace.get("listViewModels")).getItems().size()
                + " camera=" + camera + " target=" + camera.getTarget() + " rotation=" + camera.getRotation()
                + " distance=" + camera.getDistance() + " canvas=" + canvas.getWidth() + "x" + canvas.getHeight();
    }
    static void action(String name, Runnable body) {
        long submitted = System.nanoTime();
        RenderDiagnostics.event("action-submitted " + name);
        Platform.runLater(() -> {
            long started = System.nanoTime();
            RenderDiagnostics.measure("action-queue", started - submitted);
            RenderDiagnostics.event("action-before " + name + " " + context());
            try { body.run(); }
            catch (Throwable e) { fail(e); return; }
            RenderDiagnostics.measure("action-handler", System.nanoTime() - started);
            RenderDiagnostics.event("action-after-handler " + name + " " + context());
            // A pulse marker is not proof of physical monitor presentation.
            AnimationTimer nextPulse = new AnimationTimer() {
                @Override public void handle(long now) {
                    stop();
                    RenderDiagnostics.measure("action-to-next-pulse", System.nanoTime() - submitted);
                    RenderDiagnostics.event("action-next-pulse " + name + " " + context());
                    completed++;
                    monitor.schedule(Stage1Probe::next, 350, TimeUnit.MILLISECONDS);
                }
            };
            nextPulse.start();
        });
    }
    static void next() {
        if (closing) return;
        if (steps.isEmpty()) { Platform.runLater(Stage1Probe::close); return; }
        steps.removeFirst().run();
    }
    static void step(String name, Runnable body) { steps.add(() -> action(name, body)); }
    static void button(String text) {
        Accordion accordion = (Accordion)root.lookup(".accordion");
        for (TitledPane pane : accordion.getPanes()) {
            for (Node node : pane.getContent().lookupAll(".button")) {
                Button button = (Button)node;
                if (button.getText().equals(text)) {
                    accordion.setExpandedPane(pane); root.applyCss(); root.layout();
                    if (button.getOnAction() != null) button.fire(); else button.getOnMouseClicked().handle(null);
                    return;
                }
            }
        }
        throw new IllegalArgumentException("Missing button " + text);
    }
    static void menu(String text) {
        MenuBar bar = (MenuBar)root.lookup(".menu-bar");
        bar.getMenus().stream().flatMap(m -> m.getItems().stream()).filter(i -> i.getText().equals(text)).findFirst().orElseThrow().fire();
    }
    static void key(KeyCode key, boolean shift) {
        Node target = stage.getScene().getFocusOwner();
        if (target == null) target = root;
        target.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, shift, false, false, false));
        target.fireEvent(new KeyEvent(KeyEvent.KEY_RELEASED, "", "", key, shift, false, false, false));
    }
    static void fail(Throwable e) {
        e.printStackTrace(); finished.completeExceptionally(e); close();
    }
    static void close() {
        if (closing) return;
        closing = true;
        RenderDiagnostics.event("scenario-complete completed=" + completed + " " + context());
        if (pulseMonitor != null) pulseMonitor.stop();
        if (controller != null) controller.dispose();
        if (stage != null) stage.close();
        RenderDiagnostics.flush(); finished.complete(null); Platform.exit();
    }
    static void monitor() {
        monitor.scheduleAtFixedRate(() -> {
            long now = System.nanoTime();
            if (closing || !pending.compareAndSet(0, now)) return;
            Platform.runLater(() -> {
                RenderDiagnostics.measure("ui-queue", System.nanoTime() - now);
                pending.compareAndSet(now, 0);
            });
        }, 0, 100, TimeUnit.MILLISECONDS);
        monitor.scheduleAtFixedRate(() -> {
            long start = pending.get();
            if (closing || start == 0 || System.nanoTime() - start < 1_000_000_000L || dumpSeries.get() >= 3) return;
            int series = dumpSeries.incrementAndGet();
            new Thread(() -> {
                for (int i = 1; i <= 3; i++) {
                    Path log = output.resolve("threads-" + series + "-" + i + ".txt");
                    try {
                        RenderDiagnostics.event("watchdog jcmd start " + log);
                        Process p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "jcmd.exe").toString(),
                                Long.toString(ProcessHandle.current().pid()), "Thread.print", "-l")
                                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
                        if (!p.waitFor(5, TimeUnit.SECONDS)) {
                            p.destroy(); Files.writeString(log, "\njcmd timeout after 5 seconds\n", StandardOpenOption.APPEND);
                        }
                        Thread.sleep(1000);
                    } catch (Exception e) { RenderDiagnostics.event("watchdog failure " + e); }
                }
            }, "diagnostic-dump-series-" + series).start();
        }, 1, 4, TimeUnit.SECONDS);
    }
    static void minimal() {
        TextField text = new TextField("Clipboard diagnostic text");
        Button chooser = new Button("Open native file chooser");
        chooser.setOnAction(e -> {
            RenderDiagnostics.event("native-dialog-open");
            new FileChooser().showOpenDialog(stage);
            RenderDiagnostics.event("native-dialog-return");
        });
        Label drag = new Label("Drag this diagnostic text into the field");
        drag.setOnDragDetected(e -> {
            RenderDiagnostics.event("native-drag-start");
            Dragboard board = drag.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent(); content.putString("Diagnostic drag text"); board.setContent(content); e.consume();
        });
        text.setOnDragOver(e -> { if (e.getDragboard().hasString()) e.acceptTransferModes(TransferMode.COPY); e.consume(); });
        text.setOnDragDropped(e -> { text.setText(e.getDragboard().getString()); e.setDropCompleted(true); RenderDiagnostics.event("native-drag-drop"); e.consume(); });
        drag.setOnDragDone(e -> RenderDiagnostics.event("native-drag-done " + e.getTransferMode()));
        root = new VBox(25, text, chooser, drag);
        stage.setScene(new Scene(root, 640, 240)); stage.setTitle("CGEngine stage1 minimal"); stage.show();
    }
    public static void main(String[] args) throws Exception {
        model = Path.of(args[0]); output = Path.of(args[1]); Files.createDirectories(output);
        boolean manual = Arrays.asList(args).contains("--manual");
        Thread.setDefaultUncaughtExceptionHandler((t,e) -> { e.printStackTrace(); finished.completeExceptionally(e); });
        Platform.startup(() -> {
            try {
                Platform.setImplicitExit(false); stage = new Stage();
                if (RenderDiagnostics.MODE.equals("minimal")) minimal();
                else {
                    controller = new Controller();
                    FXMLLoader loader = new FXMLLoader(Simple3DViewer.class.getResource("fxml/gui.fxml"));
                    loader.setControllerFactory(type -> controller); root = loader.load(); namespace = loader.getNamespace();
                    Scene scene = new Scene(root); stage.setMinWidth(1280); stage.setMinHeight(720);
                    AnchorPane pane = (AnchorPane)root; pane.prefWidthProperty().bind(scene.widthProperty()); pane.prefHeightProperty().bind(scene.heightProperty());
                    stage.setScene(scene); stage.setTitle("CGEngine stage1 " + RenderDiagnostics.MODE + " " + model.getFileName());
                    stage.show(); root.applyCss(); root.layout();
                    scene.addEventFilter(KeyEvent.ANY, e -> { if (e.getEventType() == KeyEvent.KEY_PRESSED) RenderDiagnostics.event("native-key " + e.getCode() + " focus=" + scene.getFocusOwner()); });
                    scene.addEventFilter(javafx.event.ActionEvent.ACTION, e -> RenderDiagnostics.event("native-action " + e.getTarget()));
                    stage.focusedProperty().addListener((o,old,value) -> RenderDiagnostics.event("window-focus=" + value));
                }
                stage.setOnCloseRequest(e -> { e.consume(); close(); });
                pulseMonitor = new AnimationTimer() {
                    long previous;
                    @Override public void handle(long now) { if (previous != 0) RenderDiagnostics.measure("pulse-gap", now - previous); previous = now; }
                }; pulseMonitor.start();
                RenderDiagnostics.event("scenario-start manual=" + manual + " " + context());
                monitor();
                if (!manual && controller != null) {
                    step("load", () -> menu("Load Model"));
                    if (Arrays.asList(args).contains("--multi")) step("load-second", () -> { model = Path.of("Test_Models/tetrahedron.obj"); menu("Load Model"); });
                    step("frame-selected", () -> controller.onFrameSelectedMenuItemClick(null));
                    step("focus-canvas", () -> { Canvas c = (Canvas)namespace.get("canvas"); c.setFocusTraversable(true); c.requestFocus(); });
                    for (KeyCode k : List.of(KeyCode.W, KeyCode.S, KeyCode.A, KeyCode.D)) step("key-" + k, () -> key(k,false));
                    for (KeyCode k : List.of(KeyCode.W, KeyCode.S, KeyCode.A, KeyCode.D)) step("shift-" + k, () -> key(k,true));
                    step("all", () -> button("All"));
                    step("red", () -> ((ComboBox<String>)namespace.get("colorPicker")).setValue("Red"));
                    step("apply", () -> button("Apply"));
                    step("reset-color", () -> controller.resetPolygonColor(null));
                    step("add-camera", () -> controller.addCamera(null));
                    step("switch-camera", () -> ((ListView<?>)namespace.get("listViewCameras")).getSelectionModel().select(0));
                    step("reset-camera", () -> controller.onResetCameraMenuItemClick(null));
                    step("focus-text", () -> ((TextField)namespace.get("polygonIndices")).requestFocus());
                    step("key-in-text", () -> key(KeyCode.W, false));
                    step("delete-model", () -> controller.removeModel(null));
                    monitor.schedule(Stage1Probe::next, 1, TimeUnit.SECONDS);
                }
            } catch (Throwable e) { fail(e); }
        });
        try { finished.get(manual ? 900 : 60, TimeUnit.SECONDS); }
        catch (Exception e) {
            RenderDiagnostics.event("scenario-incomplete completed=" + completed + " reason=" + e);
            // Bound only this diagnostic JVM even when FX cannot drain its queue.
            // System.exit runs JFR's shutdown hook and does not mask the failed run.
            System.exit(2);
        }
        finally { monitor.shutdownNow(); if (!closing) Platform.runLater(Stage1Probe::close); }
    }
}
