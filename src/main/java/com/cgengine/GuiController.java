package com.cgengine;

import com.cgengine.math.affine.*;
import com.cgengine.math.Vector3f;
import com.cgengine.math.Vector2f;
import com.cgengine.math.VectorMath;
import com.cgengine.render_engine.RenderEngine;
import com.cgengine.render_engine.Rasterization.FrameBuffer;
import javafx.fxml.FXML;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.io.File;

import com.cgengine.VertexDelete.Eraser;
import com.cgengine.model.Model;
import com.cgengine.obj_io.reader.ObjReader;
import com.cgengine.obj_io.reader.ObjReaderException;
import com.cgengine.obj_io.writer.ObjWriter;
import com.cgengine.render_engine.Camera;
import com.cgengine.render_engine.ModelFraming;
import com.cgengine.render_engine.Texture2D;
import com.cgengine.render_engine.CameraMotion;

public class GuiController {

    final private float TRANSLATION = 2F;

    @FXML
    AnchorPane anchorPane;

    @FXML
    private Canvas canvas;


    final private float ASPECT_RATIO = 16.0F / 9.0F;
    private static final float INITIAL_DISTANCE = 100F;
    private static final float INITIAL_FAR_PLANE = 1_000F;
    private static final float MIN_CAMERA_DISTANCE = 0.01F;


    private Camera camera = new Camera(
            new Vector2f((float) (Math.PI), (float) (-Math.PI / 2)),
            INITIAL_DISTANCE,
            new Vector3f(0, 0, 0),
            1.0F, ASPECT_RATIO, 0.01F, INITIAL_FAR_PLANE);

    private javafx.animation.AnimationTimer timeline;
    private RenderState renderedState;
    private long renderedFrames;
    private final CameraMotion cameraMotion = new CameraMotion();
    private final java.util.EnumSet<KeyCode> heldCameraKeys = java.util.EnumSet.noneOf(KeyCode.class);
    private long lastMotionNanos;
    private Scene keyboardScene;
    private boolean controlDown, shiftDown;
    private FrameBuffer frameBuffer;
    private List<Model> diagnosticCachedModels;

    private final java.util.Map<Model, String[]> transformInputs = new java.util.IdentityHashMap<>();
    private final java.util.Map<Model, String> renderErrors = new java.util.IdentityHashMap<>();

    @FXML
    private ListView<Model> listViewModels;
    private List<Model> models = new ArrayList<>();
    private Model currentModel;

    @FXML
    private Label cameraStatusLabel;
    @FXML private MenuItem moveForwardMenuItem;
    @FXML private MenuItem moveBackwardMenuItem;
    @FXML private MenuItem moveLeftMenuItem;
    @FXML private MenuItem moveRightMenuItem;
    @FXML private MenuItem rotateLeftMenuItem;
    @FXML private MenuItem rotateRightMenuItem;
    @FXML private MenuItem rotateUpMenuItem;
    @FXML private MenuItem rotateDownMenuItem;
    @FXML private MenuItem moveUpMenuItem;
    @FXML private MenuItem moveDownMenuItem;

    @FXML
    private ListView<Camera> listViewCameras;
    private List<Camera> cameras = new ArrayList<>();
    private Camera currentCamera;

    @FXML
    private void initialize() {
        canvas.setWidth(720 * ASPECT_RATIO);
        canvas.setHeight(720);

        anchorPane.prefHeightProperty().addListener((ov, oldValue, newValue) -> {
            canvas.setHeight(newValue.doubleValue());
            canvas.setWidth(newValue.doubleValue() * ASPECT_RATIO);
        });

        // One callback per pulse: missed intervals never enqueue catch-up frames.
        timeline = new javafx.animation.AnimationTimer() {
            @Override public void handle(long pulseNanos) {
            long now = System.nanoTime();
            if (lastMotionNanos != 0) updateCameraMotion((now - lastMotionNanos) / 1e9);
            lastMotionNanos = now;
            renderFrame();
            }
        };
        timeline.start();

        vertexRemoverButton.setOnAction(event -> handleVertexRemoval());

        updateTransformationFields();

        inputForModelMoving.setText("0.0 0.0 0.0");
        inputForModelRotation.setText("0.0 0.0 0.0");
        inputForModelScaling.setText("1.0 1.0 1.0");

        currentCamera = camera;
        cameras.add(camera);
        listViewCameras.setItems(FXCollections.observableArrayList(cameras));

        listViewCameras.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                clearCameraInput();
                currentCamera = newValue;
                camera = newValue;
                updateCameraStatus();
            }
        });

        listViewCameras.getSelectionModel().select(camera);
        updateCameraStatus();

        canvas.setOnScroll(this::handleCameraScroll);
        anchorPane.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) installCameraKeyboardControls(newScene);
        });

        listViewCameras.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Camera item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText("Camera " + (cameras.indexOf(item) + 1));
                }
            }
        });

        listViewModels.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Model item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String error = renderErrors.get(item);
                    setText("Model " + (modelIndex(item) + 1)
                            + (error == null ? "" : " — not rendered: " + error));
                }
            }
        });

        listViewModels.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            int index = newValue.intValue();
            currentModel = index < 0 ? null : listViewModels.getItems().get(index);
            polygonIndices.clear();
            updateTransformationFields();
        });
    }

    void renderFrame() {
        int width = Math.max(1, (int) canvas.getWidth());
        int height = Math.max(1, (int) canvas.getHeight());
        camera.setAspectRatio((float) width / height);
        if (renderedState != null && renderedState.matches(width, height, camera, models)) return;
        long diagnosticStart = RenderDiagnostics.ENABLED ? System.nanoTime() : 0;
        if (RenderDiagnostics.ENABLED) RenderDiagnostics.frameStarted(diagnosticStart);
        if (frameBuffer == null || frameBuffer.width() != width || frameBuffer.height() != height) {
            frameBuffer = new FrameBuffer(width, height);
            diagnosticCachedModels = null;
        }
        boolean cached = RenderDiagnostics.ENABLED && RenderDiagnostics.MODE.equals("cached");
        boolean recalculate = !cached || diagnosticCachedModels == null
                || diagnosticCachedModels.size() != models.size();
        if (!recalculate) for (int i = 0; i < models.size(); i++)
            if (diagnosticCachedModels.get(i) != models.get(i)) { recalculate = true; break; }
        long diagnosticCpuStart = RenderDiagnostics.ENABLED ? System.nanoTime() : 0;
        if (recalculate) RenderEngine.renderFrame(frameBuffer, camera, models, new RenderEngine.ModelRenderListener() {
            @Override public void onRendered(Model model) { updateRenderError(model, null); }
            @Override public void onSkipped(Model model, String reason) { updateRenderError(model, reason); }
        });
        if (cached && recalculate) diagnosticCachedModels = new ArrayList<>(models);
        if (RenderDiagnostics.ENABLED) RenderDiagnostics.measure(recalculate ? "cpu-render" : "cached-frame", System.nanoTime() - diagnosticCpuStart);
        long diagnosticUploadStart = RenderDiagnostics.ENABLED ? System.nanoTime() : 0;
        if (!(RenderDiagnostics.ENABLED && RenderDiagnostics.MODE.equals("no-upload")))
            com.cgengine.render_engine.PolygonFiller.publish(canvas.getGraphicsContext2D(), frameBuffer);
        if (RenderDiagnostics.ENABLED) {
            if (!RenderDiagnostics.MODE.equals("no-upload")) RenderDiagnostics.measure("canvas-upload", System.nanoTime() - diagnosticUploadStart);
            RenderDiagnostics.measure("frame-handler", System.nanoTime() - diagnosticStart);
        }
        renderedState = new RenderState(width, height, camera, models);
        renderedFrames++;
    }

    void dispose() { clearCameraInput(); timeline.stop(); }

    void updateCameraMotion(double elapsedSeconds) {
        camera.setAspectRatio(Math.max(1, (float)canvas.getWidth()) / Math.max(1, (float)canvas.getHeight()));
        if (cameraMotion.update(camera, elapsedSeconds)) updateCameraStatus();
    }

    private void clearCameraInput() {
        heldCameraKeys.clear(); cameraMotion.reset(); controlDown = false; shiftDown = false;
        lastMotionNanos = 0;
    }

    private void updateRenderError(Model model, String error) {
        String previous = error == null ? renderErrors.remove(model) : renderErrors.put(model, error);
        if (!java.util.Objects.equals(previous, error)) listViewModels.refresh();
    }

    private void installCameraKeyboardControls(Scene scene) {
        if (keyboardScene == scene) return;
        clearCameraInput(); keyboardScene = scene;
        scene.focusOwnerProperty().addListener((observable, oldFocus, newFocus) -> {
            if (newFocus instanceof TextInputControl) clearCameraInput();
            setCameraMenuItemsDisabled(newFocus instanceof TextInputControl);
        });
        scene.windowProperty().addListener((observable, oldWindow, window) -> {
            clearCameraInput();
            if (window != null) installCameraWindowControls(window);
        });
        if (scene.getWindow() != null) installCameraWindowControls(scene.getWindow());
        setCameraMenuItemsDisabled(scene.getFocusOwner() instanceof TextInputControl);
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> handleHeldCameraKey(scene, event, true));
        scene.addEventFilter(KeyEvent.KEY_RELEASED, event -> handleHeldCameraKey(scene, event, false));
    }

    private void installCameraWindowControls(javafx.stage.Window window) {
        window.focusedProperty().addListener((observable, oldValue, focused) -> { if (!focused) clearCameraInput(); });
        window.showingProperty().addListener((observable, oldValue, showing) -> { if (!showing) clearCameraInput(); });
        if (window instanceof javafx.stage.Stage stage)
            stage.iconifiedProperty().addListener((observable, oldValue, iconified) -> { if (iconified) clearCameraInput(); });
    }

    private void handleHeldCameraKey(Scene scene, KeyEvent event, boolean pressed) {
        if (scene.getFocusOwner() instanceof TextInputControl || event.getTarget() instanceof TextInputControl
                || event.isAltDown() || event.isMetaDown()) { clearCameraInput(); return; }
        KeyCode key = event.getCode();
        boolean navigation = key == KeyCode.W || key == KeyCode.S || key == KeyCode.A || key == KeyCode.D;
        if (!navigation && key != KeyCode.SHIFT && key != KeyCode.CONTROL) return;
        controlDown = key == KeyCode.CONTROL ? pressed : event.isControlDown();
        shiftDown = key == KeyCode.SHIFT ? pressed : event.isShiftDown();
        if (navigation) {
            if (pressed) heldCameraKeys.add(key); else heldCameraKeys.remove(key);
            event.consume();
        }
        double horizontal = (heldCameraKeys.contains(KeyCode.D) ? 1 : 0) - (heldCameraKeys.contains(KeyCode.A) ? 1 : 0);
        double vertical = (heldCameraKeys.contains(KeyCode.W) ? 1 : 0) - (heldCameraKeys.contains(KeyCode.S) ? 1 : 0);
        cameraMotion.setInput(new CameraMotion.Input(shiftDown ? 0 : horizontal, controlDown ? vertical : 0,
                !controlDown && !shiftDown ? -vertical : 0, shiftDown ? -horizontal : 0,
                !controlDown && shiftDown ? vertical : 0));
    }

    private void setCameraMenuItemsDisabled(boolean disabled) {
        for (MenuItem item : java.util.List.of(moveForwardMenuItem, moveBackwardMenuItem, moveLeftMenuItem,
                moveRightMenuItem, rotateLeftMenuItem, rotateRightMenuItem, rotateUpMenuItem, rotateDownMenuItem,
                moveUpMenuItem, moveDownMenuItem)) item.setDisable(disabled);
    }

    private float cameraDistanceStep() { return Math.max(0.05F, camera.getDistance() * 0.1F); }
    private void moveCameraForward() { camera.setNavigationDistance(Math.max(MIN_CAMERA_DISTANCE, camera.getDistance() - cameraDistanceStep())); updateCameraStatus(); }
    private void moveCameraBackward() { camera.setNavigationDistance(Math.min(1e8f, camera.getDistance() + cameraDistanceStep())); updateCameraStatus(); }
    private void rotateCameraLeft() { camera.moveRotation(new Vector2f(TRANSLATION / 100, 0)); updateCameraStatus(); }
    private void rotateCameraRight() { camera.moveRotation(new Vector2f(-TRANSLATION / 100, 0)); updateCameraStatus(); }
    private void rotateCameraUp() { camera.moveRotation(new Vector2f(0, TRANSLATION / 100)); updateCameraStatus(); }
    private void rotateCameraDown() { camera.moveRotation(new Vector2f(0, -TRANSLATION / 100)); updateCameraStatus(); }
    private void moveCameraUp() { camera.moveTarget(VectorMath.multiply(camera.getScreenUp(), TRANSLATION / 2)); updateCameraStatus(); }
    private void moveCameraDown() { camera.moveTarget(VectorMath.multiply(camera.getScreenUp(), -TRANSLATION / 2)); updateCameraStatus(); }
    private void moveCameraLeftByTarget() { camera.moveTarget(VectorMath.multiply(camera.getScreenRight(), -TRANSLATION / 2)); updateCameraStatus(); }
    private void moveCameraRightByTarget() { camera.moveTarget(VectorMath.multiply(camera.getScreenRight(), TRANSLATION / 2)); updateCameraStatus(); }
    private void handleCameraScroll(ScrollEvent event) { if (event.getDeltaY() > 0) moveCameraForward(); else if (event.getDeltaY() < 0) moveCameraBackward(); event.consume(); }

    private void updateCameraStatus() {
        if (cameraStatusLabel == null || camera == null) return;
        Vector3f target = camera.getTarget();
        cameraStatusLabel.setText(String.format(Locale.ROOT, "Target (%.3f, %.3f, %.3f) | distance %.3f | near/far %.4f / %.3f",
                target.x(), target.y(), target.z(), camera.getDistance(), camera.getNearPlane(), camera.getFarPlane()));
    }

    private void updateTransformationFields() {
        String[] values = transformInputs.get(currentModel);
        inputForModelMoving.setText(values == null ? "0.0 0.0 0.0" : values[0]);
        inputForModelRotation.setText(values == null ? "0.0 0.0 0.0" : values[1]);
        inputForModelScaling.setText(values == null ? "1.0 1.0 1.0" : values[2]);
    }

    File chooseModelFile(boolean save) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Model (*.obj)", "*.obj"));
        chooser.setTitle(save ? "Save Model" : "Load Model");
        return save ? chooser.showSaveDialog(canvas.getScene().getWindow())
                : chooser.showOpenDialog(canvas.getScene().getWindow());
    }

    @FXML
    private void onOpenModelMenuItemClick() {
        File file = chooseModelFile(false);
        if (file == null) return;
        try {
            Model model = ObjReader.read(Files.readString(file.toPath()));
            boolean firstModel = models.isEmpty();
            models.add(model);
            listViewModels.setItems(FXCollections.observableArrayList(models));
            listViewModels.getSelectionModel().select(models.size() - 1);
            if (firstModel) frameSelectedModel();
        } catch (IOException e) {
            showError("File Error", "Failed to read file: " + e.getMessage());
        } catch (ObjReaderException | IllegalArgumentException e) {
            showError("Model Error", e.getMessage());
        }
    }

    /** Frames the selected model with the selected camera, preserving all model data. */
    @FXML
    void onFrameSelectedMenuItemClick(ActionEvent event) {
        try {
            frameSelectedModel();
        } catch (IllegalArgumentException e) {
            showError("Frame selected", e.getMessage());
        }
    }

    long renderedFrames() { return renderedFrames; }
    void invalidateFrame() { renderedState = null; }

    void frameSelectedModel() {
        if (currentModel == null) throw new IllegalArgumentException("Select a model first.");
        clearCameraInput();
        float aspectRatio = Math.max(1, (float) canvas.getWidth()) / Math.max(1, (float) canvas.getHeight());
        ModelFraming.Frame frame = ModelFraming.calculate(currentModel, camera.getFov(), aspectRatio);
        Camera framedCamera = frame.createCamera(camera.getRotation(), camera.getFov(), aspectRatio);
        int cameraIndex = cameras.indexOf(currentCamera);
        if (cameraIndex < 0) throw new IllegalStateException("Selected camera is not available.");
        cameras.set(cameraIndex, framedCamera);
        camera = framedCamera;
        currentCamera = framedCamera;
        listViewCameras.setItems(FXCollections.observableArrayList(cameras));
        listViewCameras.getSelectionModel().select(framedCamera);
        updateCameraStatus();
    }

    @FXML
    void onResetCameraMenuItemClick(ActionEvent event) {
        clearCameraInput();
        int cameraIndex = cameras.indexOf(currentCamera);
        if (cameraIndex < 0) { showError("Reset camera", "Select a camera first."); return; }
        Camera reset = createInitialCamera();
        cameras.set(cameraIndex, reset);
        camera = reset;
        currentCamera = reset;
        listViewCameras.setItems(FXCollections.observableArrayList(cameras));
        listViewCameras.getSelectionModel().select(reset);
        updateCameraStatus();
    }

    @FXML
    private void onSaveModelMenuItemClick() {
        if (currentModel == null) { showError("Save Error", "No model loaded to save"); return; }
        File file = chooseModelFile(true);
        if (file == null) return;
        try {
            new ObjWriter().write(currentModel, file.getAbsolutePath());
            showInfo("Model saved", "OBJ was saved successfully.");
        } catch (com.cgengine.obj_io.writer.ObjWriteException | IllegalArgumentException e) {
            showError("Save Error", e.getMessage());
        }
    }

    @FXML
    private TextField inputForModelMoving;

    @FXML
    private TextField inputForModelRotation;

    @FXML
    private TextField inputForModelScaling;


    @FXML
    void applyTransformation(MouseEvent event) {
        if (currentModel == null) { showError("Input Error", "Select a model first."); return; }
        try {
            Vector3f translation = parseVector(inputForModelMoving.getText());
            Vector3f rotation = parseVector(inputForModelRotation.getText());
            Vector3f scale = parseVector(inputForModelScaling.getText());
            Transformation transformation = new Transformation(
                    new Translator(translation.x(), translation.y(), translation.z()),
                    Rotator.degrees(rotation.x(), Rotator.Axis.X),
                    Rotator.degrees(rotation.y(), Rotator.Axis.Y),
                    Rotator.degrees(rotation.z(), Rotator.Axis.Z),
                    new Scaling(scale.x(), scale.y(), scale.z()));
            currentModel.applyTransformation(transformation);
            transformInputs.put(currentModel, new String[]{inputForModelMoving.getText(),
                    inputForModelRotation.getText(), inputForModelScaling.getText()});
        } catch (IllegalArgumentException e) { showError("Transformation Error", e.getMessage()); }
    }

    static Vector3f parseVector(String text) {
        String[] parts = text.trim().split("\\s+");
        if (parts.length != 3) throw new IllegalArgumentException("Enter exactly three finite numbers: x y z (decimal point).");
        Vector3f value = new Vector3f(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]), Float.parseFloat(parts[2]));
        if (!com.cgengine.math.VectorMath.isFinite(value)) throw new IllegalArgumentException("All components must be finite.");
        return value;
    }

    @FXML
    void resetTransformation(MouseEvent event) {
        if (currentModel == null) { showError("Input Error", "Select a model first."); return; }
        currentModel.resetTransformation();
        transformInputs.remove(currentModel);
        updateTransformationFields();
    }

    @FXML
    public void handleCameraForward(ActionEvent actionEvent) {
        moveCameraForward();
    }

    @FXML
    public void handleCameraBackward(ActionEvent actionEvent) {
        moveCameraBackward();
    }

    @FXML
    public void handleCameraLeft(ActionEvent actionEvent) {
        rotateCameraLeft();
    }

    @FXML
    public void handleCameraRight(ActionEvent actionEvent) {
        rotateCameraRight();
    }

    @FXML
    public void handleCameraUp(ActionEvent actionEvent) {
        rotateCameraUp();
    }

    @FXML
    public void handleCameraDown(ActionEvent actionEvent) {
        rotateCameraDown();
    }

    @FXML
    public void moveCameraUp(ActionEvent actionEvent) {
        moveCameraUp();
    }

    @FXML
    public void moveCameraDown(ActionEvent actionEvent) {
        moveCameraDown();
    }

    @FXML
    public void moveCameraLeft(ActionEvent actionEvent) {
        moveCameraLeftByTarget();
    }

    @FXML
    public void moveCameraRight(ActionEvent actionEvent) {
        moveCameraRightByTarget();
    }

    @FXML
    private TextField VerticesToRemove;
    @FXML
    private Button vertexRemoverButton;
    @FXML
    private CheckBox checkboxNewFile;
    @FXML
    private CheckBox checkboxRemoveNormals;
    @FXML
    private CheckBox checkboxRemoveTextures;
    @FXML
    private CheckBox checkboxRemovePolygons;

    @FXML
    private ComboBox<String> colorPicker;
    @FXML
    private TextField polygonIndices;

    public void chooseColor(ActionEvent actionEvent) {
        if (currentModel == null) { showError("No model selected", "Select a model before applying a color."); return; }
        String selectedColor = colorPicker.getValue();
        if (selectedColor == null) {
            showError("No color selected", "Please select a color to apply.");
            return;
        }

        switch (selectedColor.toLowerCase(Locale.ROOT)) {
            case "red": applyPolygonColor(com.cgengine.render_engine.ColorRgb.RED); break;
            case "green": applyPolygonColor(com.cgengine.render_engine.ColorRgb.GREEN); break;
            case "blue": applyPolygonColor(com.cgengine.render_engine.ColorRgb.BLUE); break;
            case "yellow": applyPolygonColor(com.cgengine.render_engine.ColorRgb.YELLOW); break;
            case "white": applyPolygonColor(com.cgengine.render_engine.ColorRgb.WHITE); break;
            default: applyPolygonColor(com.cgengine.render_engine.ColorRgb.BLACK); break;
        }
    }

    public void resetPolygonColor(ActionEvent actionEvent) {
        applyPolygonColor(com.cgengine.render_engine.ColorRgb.WHITE);
    }

    @FXML
    private void chooseTexture(ActionEvent actionEvent) {
        if (currentModel == null) { showError("No model selected", "Select a model before applying a texture."); return; }
        boolean hasUv = currentModel.polygons.stream().anyMatch(p -> p.getTextureVertexIndices().size() == 3);
        if (!hasUv) { showError("Texture Error", "The selected model has no UV coordinates; its base color will be used."); return; }
        File file = chooseTextureFile();
        if (file == null) return;
        try { currentModel.setTexture(Texture2D.load(file)); }
        catch (IOException | IllegalArgumentException e) { showError("Texture Error", e.getMessage()); }
    }

    File chooseTextureFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Texture");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images (*.png, *.jpg, *.jpeg)", "*.png", "*.jpg", "*.jpeg"));
        return chooser.showOpenDialog(canvas.getScene().getWindow());
    }

    @FXML
    private void removeTexture(ActionEvent event) {
        if (currentModel == null) { showError("No model selected", "Select a model first."); return; }
        currentModel.removeTexture();
    }

    @FXML
    public void selectAllPolygons(ActionEvent event) {
        if (currentModel == null) { showError("No model selected", "Select a model first."); return; }
        polygonIndices.setText(java.util.stream.IntStream.range(0, currentModel.polygons.size())
                .mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining(", ")));
    }

    @FXML
    void removePolygons(ActionEvent event) {
        try {
            List<Integer> indices = parsePolygonIndices();
            indices.sort(java.util.Comparator.reverseOrder());
            for (int index : indices) currentModel.polygons.remove(index);
            polygonIndices.clear();
        } catch (IllegalArgumentException e) { showError("Invalid polygon selection", e.getMessage()); }
    }

    private void applyPolygonColor(com.cgengine.render_engine.ColorRgb color) {
        try {
            List<Integer> indices = parsePolygonIndices();
            for (int index : indices) currentModel.polygons.get(index).setBaseColor(color);
        } catch (IllegalArgumentException e) { showError("Invalid polygon selection", e.getMessage()); }
    }

    private List<Integer> parsePolygonIndices() {
        if (currentModel == null) throw new IllegalArgumentException("Select a model first.");
        if (polygonIndices.getText().isBlank()) throw new IllegalArgumentException("Enter zero-based polygon indices.");
        java.util.LinkedHashSet<Integer> result = new java.util.LinkedHashSet<>();
        for (String part : polygonIndices.getText().split(",", -1)) {
            int index;
            try { index = Integer.parseInt(part.trim()); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Indices must be comma-separated non-negative integers."); }
            if (index < 0 || index >= currentModel.polygons.size()) throw new IllegalArgumentException("Polygon index is out of range.");
            result.add(index);
        }
        return new ArrayList<>(result);
    }

    private void handleVertexRemoval() {
        if (currentModel == null) {
            showError("No model loaded", "Please load a model first before trying to remove vertices.");
            return;
        }

        try {
            List<Integer> verticesToRemove = parseVerticesToRemove(VerticesToRemove.getText());

            if (!validateVertexIndices(verticesToRemove)) {
                showError("Invalid vertices", "One or more vertex indices are out of range.");
                return;
            }

            boolean createNewModel = checkboxNewFile.isSelected();
            boolean removeNormals = checkboxRemoveNormals.isSelected();
            boolean removeTextures = checkboxRemoveTextures.isSelected();
            boolean removePolygons = checkboxRemovePolygons.isSelected();

            Model resultModel = Eraser.vertexDelete(
                    currentModel,
                    verticesToRemove,
                    createNewModel,
                    removeNormals,
                    removeTextures,
                    removePolygons);

            if (createNewModel) {

                models.add(resultModel);
                listViewModels.setItems(FXCollections.observableArrayList(models));
                listViewModels.getSelectionModel().select(models.size() - 1);
            } else {
                transformInputs.remove(currentModel);
                int index = modelIndex(currentModel);
                models.set(index, resultModel);
                currentModel = resultModel;
                listViewModels.setItems(FXCollections.observableArrayList(models));
                listViewModels.getSelectionModel().select(index);
            }

        } catch (NumberFormatException e) {
            showError("Invalid input", "Please enter valid vertex indices (comma-separated numbers).");
        } catch (IllegalArgumentException e) {
            showError("Invalid vertices", e.getMessage());
        }
    }

    private List<Integer> parseVerticesToRemove(String input) {
        if (input == null || input.isBlank()) throw new IllegalArgumentException("Enter at least one vertex index.");
        List<Integer> indices = new ArrayList<>();
        String[] parts = input.split(",", -1);
        for (String part : parts) {
            int value = Integer.parseInt(part.trim());
            if (indices.contains(value)) throw new IllegalArgumentException("Vertex indices must not contain duplicates.");
            indices.add(value);
        }
        return indices;
    }

    private boolean validateVertexIndices(List<Integer> indices) {
        int maxIndex = currentModel.vertices.size() - 1;
        for (Integer index : indices) {
            if (index < 0 || index > maxIndex) {
                return false;
            }
        }
        return true;
    }

    void showError(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    void showInfo(String title, String content) { Alert alert = new Alert(Alert.AlertType.INFORMATION); alert.setTitle(title); alert.setHeaderText(null); alert.setContentText(content); alert.showAndWait(); }

    @FXML
    void addCamera(MouseEvent event) {
        Camera newCamera = createInitialCamera();

        cameras.add(newCamera);
        listViewCameras.setItems(FXCollections.observableArrayList(cameras));
        listViewCameras.getSelectionModel().select(newCamera);
    }

    @FXML
    void removeCamera(MouseEvent event) {
        Camera selectedCamera = listViewCameras.getSelectionModel().getSelectedItem();

        if (selectedCamera == null) {
            showError("Error", "No camera selected");
            return;
        }

        if (cameras.size() <= 1) {
            showError("Error", "Cannot remove last camera");
            return;
        }

        cameras.remove(selectedCamera);
        listViewCameras.setItems(FXCollections.observableArrayList(cameras));

        if (selectedCamera == currentCamera) {
            listViewCameras.getSelectionModel().select(0);
        }
    }

    @FXML
    void addModel(MouseEvent event) { onOpenModelMenuItemClick(); }

    private int modelIndex(Model model) {
        for (int i = 0; i < models.size(); i++) if (models.get(i) == model) return i;
        return -1;
    }

    FrameBuffer currentFrameBuffer() { return frameBuffer; }
    Camera currentCamera() { return currentCamera; }
    String renderError(Model model) { return renderErrors.get(model); }

    private Camera createInitialCamera() {
        return new Camera(new Vector2f((float) Math.PI, (float) -Math.PI / 2), INITIAL_DISTANCE,
                new Vector3f(0, 0, 0), 1.0F, ASPECT_RATIO, 0.01F, INITIAL_FAR_PLANE);
    }

    @FXML
    void removeModel(MouseEvent event) {
        Model selectedModel = listViewModels.getSelectionModel().getSelectedItem();

        if (selectedModel == null) {
            showError("Error", "No model selected");
            return;
        }

        int removedIndex = modelIndex(selectedModel);
        models.remove(removedIndex);
        transformInputs.remove(selectedModel);
        listViewModels.setItems(FXCollections.observableArrayList(models));

        if (models.isEmpty()) currentModel = null;
        else listViewModels.getSelectionModel().select(Math.min(removedIndex, models.size() - 1));
        polygonIndices.clear();
        updateTransformationFields();
    }
}
