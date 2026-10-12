package com.cgengine;

import com.cgengine.math.Vector3f;
import com.cgengine.model.Model;
import com.cgengine.render_engine.Camera;
import com.cgengine.render_engine.ColorRgb;
import javafx.animation.PauseTransition;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.util.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/** Stage 9: real FXML controls, active AnimationTimer, model and uploaded Canvas pixels. */
@SuppressWarnings("unchecked")
class ColorPaintingUiTest {
    @TempDir Path directory;
    @Test void cube() throws Exception { simpleModel("cube"); }
    @Test void tetrahedron() throws Exception { simpleModel("tetrahedron"); }
    @Test void octahedron() throws Exception { simpleModel("octahedron"); }
    @Test void icosahedron() throws Exception { simpleModel("icosahedron"); }

    private void simpleModel(String name) throws Exception {
        var holder = new UiRegressionTest.View[1];
        try {
            pulse(() -> { holder[0] = new UiRegressionTest.View(); holder[0].load(Path.of("Test_Models/" + name + ".obj")); holder[0].menu("Frame Selected"); holder[0].menu("Right"); holder[0].menu("Up"); });
            var v = holder[0];
            Model[] original = new Model[1];
            FxTestSupport.run(() -> original[0] = v.selected().clone());
            pulse(() -> paint(v, "0, 2", "Red"));
            FxTestSupport.run(() -> { colors(v.selected(), i -> i == 0 || i == 2 ? ColorRgb.RED : ColorRgb.WHITE); pixels(v, name + "-red", null); });
            // Turn the selected triangles toward the camera, including faces hidden initially.
            for (int index : new int[]{0, 2}) {
                pulse(() -> faceCamera(v, index));
                FxTestSupport.run(() -> assertTrue(pixels(v, name + "-face-" + index, null).contains(index), "Selected face must actually be sampled"));
            }
            int[][] once = new int[1][];
            FxTestSupport.run(() -> once[0] = v.controller.currentFrameBuffer().colors().clone());
            pulse(() -> paint(v, "0, 0, 2", "Red"));
            FxTestSupport.run(() -> { colors(v.selected(), i -> i == 0 || i == 2 ? ColorRgb.RED : ColorRgb.WHITE); assertArrayEquals(once[0], v.controller.currentFrameBuffer().colors()); });
            for (String invalid : List.of("-1", "\u22121", Integer.toString(original[0].polygons.size()), "1, bad", "0, " + original[0].polygons.size(), "")) {
                pulse(() -> { v.controller.error = null; paint(v, invalid, "Blue"); assertNotNull(v.controller.error, invalid); });
                FxTestSupport.run(() -> { colors(v.selected(), i -> i == 0 || i == 2 ? ColorRgb.RED : ColorRgb.WHITE); assertArrayEquals(once[0], v.controller.currentFrameBuffer().colors(), "Invalid Apply must be atomic"); });
            }
            pulse(() -> {
                v.controller.error = null; v.button("All");
                assertEquals(IntStream.range(0, original[0].polygons.size()).mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining(", ")), ((TextField)v.control("polygonIndices")).getText());
                pick(v, "Green"); v.button("Apply");
            });
            FxTestSupport.run(() -> { colors(v.selected(), i -> ColorRgb.GREEN); pixels(v, name + "-green", null); });
            Model[] first = new Model[1]; Camera[] camera = new Camera[1];
            pulse(() -> {
                first[0] = v.selected(); camera[0] = v.controller.currentCamera();
                // Exercise the FXML camera-add mouse handler, then the list selection listener.
                ((Button)((ListView<?>)v.control("listViewCameras")).getParent().lookupAll(".button").stream()
                        .filter(n -> ((Button)n).getText().equals("+")).findFirst().orElseThrow()).getOnMouseClicked().handle(null);
                assertNotSame(camera[0], v.controller.currentCamera());
            });
            pulse(() -> ((ListView<Camera>)v.control("listViewCameras")).getSelectionModel().select(camera[0]));
            FxTestSupport.run(() -> { assertSame(camera[0], v.controller.currentCamera()); colors(first[0], i -> ColorRgb.GREEN); pixels(v, name + "-camera-return", null); });
            pulse(() -> v.load(Path.of("Test_Models/" + name + ".obj")));
            FxTestSupport.run(() -> { assertNotSame(first[0], v.selected()); colors(v.selected(), i -> ColorRgb.WHITE); colors(first[0], i -> ColorRgb.GREEN); });
            pulse(() -> { v.models().getSelectionModel().select(first[0]); v.text("polygonIndices", "0, 2"); ((Button)v.control("resetColorButton")).fire(); });
            FxTestSupport.run(() -> { colors(first[0], i -> i == 0 || i == 2 ? ColorRgb.WHITE : ColorRgb.GREEN); });
            // Remove the overlapping second copy before checking visible pixels of the first.
            pulse(() -> { v.models().getSelectionModel().select(1); v.controller.removeModel(null); v.menu("Frame Selected"); v.menu("Right"); v.menu("Up"); });
            FxTestSupport.run(() -> pixels(v, name + "-reset-selected", null));
            for (String invalid : List.of("-1", Integer.toString(first[0].polygons.size()), "1, bad", "")) {
                pulse(() -> { v.controller.error = null; v.text("polygonIndices", invalid); ((Button)v.control("resetColorButton")).fire(); assertNotNull(v.controller.error); });
                FxTestSupport.run(() -> colors(first[0], i -> i == 0 || i == 2 ? ColorRgb.WHITE : ColorRgb.GREEN));
            }
            pulse(() -> { v.controller.error = null; v.button("All"); ((Button)v.control("resetColorButton")).fire(); });
            FxTestSupport.run(() -> { colors(first[0], i -> ColorRgb.WHITE); pixels(v, name + "-reset-all", null); });
            Path saved = directory.resolve(name + "-white.obj"), colored = directory.resolve(name + "-colored.obj");
            pulse(() -> { v.controller.file = saved.toFile(); v.menu("Save Model"); assertNull(v.controller.error); assertNotNull(v.controller.info); });
            pulse(() -> v.load(saved));
            FxTestSupport.run(() -> { geometry(original[0], v.selected()); colors(v.selected(), i -> ColorRgb.WHITE); });
            // Also save while colored; otherwise White on reload would not prove D-02 reset.
            pulse(() -> { paint(v, "0, 2", "Red"); v.controller.file = colored.toFile(); v.menu("Save Model"); assertNull(v.controller.error); });
            pulse(() -> v.load(colored));
            FxTestSupport.run(() -> { geometry(original[0], v.selected()); colors(v.selected(), i -> ColorRgb.WHITE); System.out.println("Stage9 " + name + " COLOR-01..07 PASS"); });
        } finally { FxTestSupport.run(() -> { if (holder[0] != null) holder[0].close(); }); }
    }

    @Test void textureAndAbsentNormals() throws Exception {
        Path plain = directory.resolve("plain.obj"), uv = directory.resolve("uv.obj"), png = directory.resolve("cyan.png");
        // Vertex zero is unused: the real Remove normals UI can delete it without changing faces.
        String vertices = "v 0 0 5\nv -1 -1 0\nv 1 -1 0\nv 0 1 0\n";
        Files.writeString(plain, vertices + "f 2 3 4\n");
        Files.writeString(uv, vertices + "vt 0 0\nvt 1 0\nvt .5 1\nf 2/1 3/2 4/3\n");
        var image = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y=0; y<2; y++) for (int x=0; x<2; x++) image.setRGB(x,y,0x00FFFF);
        javax.imageio.ImageIO.write(image,"png",png.toFile());
        var holder = new UiRegressionTest.View[1];
        try {
            pulse(() -> { holder[0] = new UiRegressionTest.View(); holder[0].load(plain); });
            var v = holder[0];
            pulse(() -> { paint(v,"0","Red"); faceCamera(v,0); });
            FxTestSupport.run(() -> {
                assertTrue(v.selected().textureVertices.isEmpty()); assertNull(v.selected().getTexture()); pixels(v,"no-uv-red",null);
                assertTrue(Arrays.stream(v.controller.currentFrameBuffer().colors()).anyMatch(p -> ((p >>> 16) & 255) > 100), "With generated normals the front receives diffuse");
            });
            String[] palette = {"Red", "Green", "Blue", "Yellow", "White", "Black"};
            ColorRgb[] rgb = {ColorRgb.RED, ColorRgb.GREEN, ColorRgb.BLUE, ColorRgb.YELLOW, ColorRgb.WHITE, ColorRgb.BLACK};
            FxTestSupport.run(() -> assertEquals(List.of(palette), ((ComboBox<String>)v.control("colorPicker")).getItems()));
            for (int i=0;i<palette.length;i++) {
                final int color=i;
                pulse(() -> paint(v,"0",palette[color]));
                FxTestSupport.run(() -> { colors(v.selected(), p -> rgb[color]); pixels(v,"palette-"+palette[color],null); });
            }
            pulse(() -> paint(v,"0","Red"));
            pulse(() -> { v.text("VerticesToRemove","0"); ((CheckBox)v.control("checkboxRemoveNormals")).setSelected(true); v.button("OK"); });
            FxTestSupport.run(() -> { assertNull(v.controller.error); assertTrue(v.selected().normals.isEmpty()); colors(v.selected(), i -> ColorRgb.RED); pixels(v,"no-normals-ambient",null); });
            pulse(() -> { v.controller.removeModel(null); v.load(uv); paint(v,"0","Red"); faceCamera(v,0); v.controller.file=png.toFile(); v.button("Texture"); });
            FxTestSupport.run(() -> { assertNull(v.controller.error); assertNotNull(v.selected().getTexture()); colors(v.selected(), i -> ColorRgb.RED); pixels(v,"texture-cyan",0xFF00FFFF); });
            pulse(() -> paint(v,"0","Green"));
            FxTestSupport.run(() -> { colors(v.selected(), i -> ColorRgb.GREEN); pixels(v,"texture-over-green",0xFF00FFFF); });
            pulse(() -> v.button("Clear texture"));
            FxTestSupport.run(() -> { assertNull(v.selected().getTexture()); assertEquals(3,v.selected().textureVertices.size()); colors(v.selected(), i -> ColorRgb.GREEN); pixels(v,"clear-texture-green",null); System.out.println("Stage9 COLOR-08..09 PASS"); });
        } finally { FxTestSupport.run(() -> { if(holder[0]!=null)holder[0].close(); }); }
    }

    private static void pulse(FxTestSupport.ThrowingRunnable action) throws Exception {
        var completed = new CompletableFuture<Void>();
        FxTestSupport.run(() -> { action.run(); var pause = new PauseTransition(Duration.millis(100)); pause.setOnFinished(e -> completed.complete(null)); pause.play(); });
        completed.get(10,TimeUnit.SECONDS); // No manual renderFrame; the production timer must publish.
    }
    private static void pick(UiRegressionTest.View v, String color) { ((ComboBox<String>)v.control("colorPicker")).getSelectionModel().select(color); }
    private static void paint(UiRegressionTest.View v, String indices, String color) { v.text("polygonIndices",indices); pick(v,color); ((Button)v.control("applyColorButton")).fire(); }
    private static void colors(Model model, java.util.function.IntFunction<ColorRgb> expected) { for(int i=0;i<model.polygons.size();i++) assertEquals(expected.apply(i),model.polygons.get(i).getBaseColor(),"baseColor " + i); }
    private static void geometry(Model a, Model b) {
        assertEquals(a.vertices,b.vertices); assertEquals(a.textureVertices,b.textureVertices);
        assertEquals(a.normals,b.normals); assertEquals(a.polygons.size(),b.polygons.size());
        for(int i=0;i<a.polygons.size();i++) {
            assertEquals(a.polygons.get(i).getVertexIndices(),b.polygons.get(i).getVertexIndices());
            assertEquals(a.polygons.get(i).getTextureVertexIndices(),b.polygons.get(i).getTextureVertexIndices());
            assertEquals(a.polygons.get(i).getNormalIndices(),b.polygons.get(i).getNormalIndices());
        }
    }
    private static void faceCamera(UiRegressionTest.View v, int index) {
        Model m=v.selected(); var ids=m.polygons.get(index).getVertexIndices();
        double[] n=unit(cross(sub(vec(m.vertices.get(ids.get(1))),vec(m.vertices.get(ids.get(0)))),sub(vec(m.vertices.get(ids.get(2))),vec(m.vertices.get(ids.get(0))))));
        double[] center=new double[3]; for(var p:m.vertices) { center[0]+=p.x()/m.vertices.size(); center[1]+=p.y()/m.vertices.size(); center[2]+=p.z()/m.vertices.size(); }
        double[] face=new double[3]; for(int id:ids) { var p=m.vertices.get(id); face[0]+=p.x()/3.; face[1]+=p.y()/3.; face[2]+=p.z()/3.; }
        if(m.polygons.size()>1 && dot(n,sub(face,center))<0) for(int i=0;i<3;i++)n[i]=-n[i]; // Original tetrahedron has inward winding; open triangle faces its own normal.
        Camera c=v.controller.currentCamera();
        c.setRotation(new com.cgengine.math.Vector2f((float)Math.atan2(-n[0],-n[2]),(float)-Math.acos(Math.max(-1,Math.min(1,n[1])))));
    }

    /** Independent ray/triangle visibility + world-space Lambert; sample away from edges. */
    private static Set<Integer> pixels(UiRegressionTest.View v, String label, Integer textureColor) throws Exception {
        Model m=v.selected(); Camera c=v.controller.currentCamera(); var frame=v.controller.currentFrameBuffer();
        Canvas canvas=v.control("canvas"); var snapshot=canvas.snapshot(null,null); var reader=snapshot.getPixelReader();
        double[] eye=vec(c.getPosition()), forward=unit(sub(vec(c.getTarget()),eye)), light=scale(forward,-1);
        var view=c.getViewMatrix(); double[] right={view.get(0,0),view.get(0,1),view.get(0,2)}, up={view.get(1,0),view.get(1,1),view.get(1,2)};
        Set<Integer> seen=new HashSet<>(); int checked=0;
        for(int y=6;y<frame.height();y+=11) for(int x=6;x<frame.width();x+=11) {
            double sx=((x+.5)/frame.width()-.5)*Math.tan(c.getFov()/2)*c.getAspectRatio(), sy=(.5-(y+.5)/frame.height())*Math.tan(c.getFov()/2);
            double[] ray=add(forward,add(scale(right,sx),scale(up,sy)));
            int nearest=-1; double depth=Double.POSITIVE_INFINITY, interior=0; double[] normal=null;
            for(int i=0;i<m.polygons.size();i++) {
                var ids=m.polygons.get(i).getVertexIndices(); double[] a=vec(m.vertices.get(ids.get(0))), e1=sub(vec(m.vertices.get(ids.get(1))),a), e2=sub(vec(m.vertices.get(ids.get(2))),a);
                double[] h=cross(ray,e2); double det=dot(e1,h); if(Math.abs(det)<1e-10)continue;
                double[] s=sub(eye,a); double u=dot(s,h)/det; double[] q=cross(s,e1); double b=dot(ray,q)/det,t=dot(e2,q)/det;
                if(u<0||b<0||u+b>1||t<c.getNearPlane()||t>c.getFarPlane()||t>=depth)continue;
                depth=t; nearest=i; interior=Math.min(u,Math.min(b,1-u-b)); normal=unit(cross(e1,e2));
            }
            if(nearest<0||interior<.14)continue;
            var polygon=m.polygons.get(nearest); double brightness=polygon.getNormalIndices().isEmpty()?.15:.15+.85*Math.max(0,dot(normal,light));
            int source=textureColor==null?polygon.getBaseColor().argb():textureColor, expected=0xFF000000;
            for(int shift:new int[]{0,8,16})expected|=((int)Math.round(((source>>>shift)&255)*brightness))<<shift;
            int actual=frame.colorAt(x,y);
            assertEquals(255,actual>>>24,label+" alpha");
            for(int shift:new int[]{0,8,16})assertEquals((expected>>>shift)&255,(actual>>>shift)&255,1,label+" polygon "+nearest+" at "+x+","+y);
            assertEquals(actual,reader.getArgb(x,y),label+" Canvas upload");
            seen.add(nearest); checked++;
        }
        assertTrue(checked>=10,label+" requires visible interior samples, got "+checked);
        String output=System.getProperty("cgengine.stage9.artifacts");
        if(output!=null) {
            Path path=Path.of(output); Files.createDirectories(path);
            var png=new java.awt.image.BufferedImage((int)snapshot.getWidth(),(int)snapshot.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<png.getHeight();y++)for(int x=0;x<png.getWidth();x++)png.setRGB(x,y,reader.getArgb(x,y));
            javax.imageio.ImageIO.write(png,"png",path.resolve(label+".png").toFile());
        }
        System.out.println("Stage9 "+label+" checked="+checked+" visibleFaces="+seen);
        return seen;
    }
    private static double[] vec(Vector3f v){return new double[]{v.x(),v.y(),v.z()};}
    private static double[] sub(double[] a,double[] b){return new double[]{a[0]-b[0],a[1]-b[1],a[2]-b[2]};}
    private static double[] add(double[] a,double[] b){return new double[]{a[0]+b[0],a[1]+b[1],a[2]+b[2]};}
    private static double[] scale(double[] a,double s){return new double[]{a[0]*s,a[1]*s,a[2]*s};}
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
    private static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static double[] unit(double[] a){return scale(a,1/Math.sqrt(dot(a,a)));}
}
