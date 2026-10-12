package com.cgengine;

import com.cgengine.math.*;
import com.cgengine.model.*;
import com.cgengine.obj_io.reader.ObjReader;
import com.cgengine.obj_io.writer.ObjWriter;
import com.cgengine.render_engine.*;
import com.cgengine.render_engine.Rasterization.FrameBuffer;
import javafx.animation.PauseTransition;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.util.Duration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Local archive UI acceptance plus mandatory small geometry/atomic import regressions. */
@SuppressWarnings("unchecked")
class Stage10UiTest {
    @TempDir Path directory;

    @Test void asymmetricFixturesHaveOnlyTheirOriginalComponentsAndCoverage() throws Exception {
        for(String name:List.of("asymmetric-single","asymmetric-pair")) {
            Model model=ObjReader.read(Files.readString(Path.of("tests/fixtures/baseline/"+name+".obj")));
            int parts=name.endsWith("single")?1:2;
            assertEquals(Collections.nCopies(parts,4),components(model));
            var before=model.clone();
            Camera camera=ModelFraming.calculate(model,1,1).createCamera(new Vector2f((float)(Math.PI+.4),(float)(-Math.PI/2+.3)),1,1);
            var frame=new FrameBuffer(256,256); RenderEngine.renderFrame(frame,camera,List.of(model));
            originalCoverage(model,camera,frame);
            // An origin-centred wide view also includes where a reflected X copy would appear.
            Camera wide=new Camera(new Vector2f((float)(Math.PI+.4),(float)(-Math.PI/2+.3)),35,new Vector3f(0,0,0),1,1,.1f,100);
            var wideFrame=new FrameBuffer(256,256);RenderEngine.renderFrame(wideFrame,wide,List.of(model));originalCoverage(model,wide,wideFrame);
            assertEquals(before.vertices,model.vertices); assertEquals(before.polygons,model.polygons);
            Path saved=directory.resolve(name+".obj"); new ObjWriter().write(model,saved.toString());
            Model back=ObjReader.read(Files.readString(saved)); sameGeometry(model,back); assertEquals(components(model),components(back));
            var second=new FrameBuffer(256,256); RenderEngine.renderFrame(second,camera,List.of(back)); assertArrayEquals(frame.colors(),second.colors());
        }
    }

    @Test void invalidThirdCoordinatesAndFemaleFaceDoNotPartiallyAddModels() throws Exception {
        var holder=new UiRegressionTest.View[1];
        try {
            pulse(()->{holder[0]=new UiRegressionTest.View();holder[0].load(Path.of("Test_Models/cube.obj"));});
            var v=holder[0]; Model[] first=new Model[1]; int[][] frame=new int[1][];
            FxTestSupport.run(()->{first[0]=v.selected();frame[0]=v.controller.currentFrameBuffer().colors().clone();});
            List<String> inputs=new ArrayList<>(List.of("v 0 0 0\nvt 0 0 bad", "v 0 0 0\nvt 0 0 Infinity", "v 0 0 0\nvt 0 0 0 1", Files.readString(Path.of("tests/fixtures/stage10/female-body-face-51112.obj"))));
            Path female=Path.of("3DModels_DELETE/Faceform/WrapFemaleBody.obj");
            if(Files.exists(female))inputs.add(Files.readString(female));
            for(String input:inputs) {
                Path bad=directory.resolve("bad.obj"); Files.writeString(bad,input);
                pulse(()->{v.controller.error=null;v.controller.file=bad.toFile();v.menu("Load Model");assertNotNull(v.controller.error);if(inputs.size()>4 && input==inputs.get(4))assertTrue(v.controller.error.contains("line: 51112"),v.controller.error);});
                FxTestSupport.run(()->{assertSame(first[0],v.selected());assertEquals(1,v.models().getItems().size());assertArrayEquals(frame[0],v.controller.currentFrameBuffer().colors());});
            }
        } finally {FxTestSupport.run(()->{if(holder[0]!=null)holder[0].close();});}
    }

    @Test void importedArchiveScenario() throws Exception {
        String supplied=System.getProperty("cgengine.stage10.model");
        Path input=Path.of(supplied==null?"3DModels_DELETE/Faceform/WrapJaw.obj":supplied);
        if(supplied==null)Assumptions.assumeTrue(Files.exists(input),"Optional local archive absent");
        else assertTrue(Files.exists(input),"Explicitly requested archive must exist");
        var holder=new UiRegressionTest.View[1];
        Model[] baseline=new Model[1];
        try {
            pulse(()->{
                holder[0]=new UiRegressionTest.View();var v=holder[0];
                Canvas canvas=v.control("canvas");canvas.setWidth(1280);canvas.setHeight(720);
                long start=System.nanoTime(); v.load(input); v.menu("Frame Selected");
                System.out.printf(Locale.ROOT,"Stage10 import %s handlerMs=%.3f%n",input,(System.nanoTime()-start)/1e6);
                baseline[0]=v.selected().clone();ModelValidator.validate(v.selected());
            });
            var v=holder[0];
            FxTestSupport.run(()->{
                assertPixels(v,false); assertNull(v.controller.error);
                if(input.getFileName().toString().equals("Rigged Hand.obj")) {
                    assertEquals(1086,v.selected().vertices.size());assertEquals(2072,v.selected().polygons.size());assertEquals(List.of(543,543),components(v.selected()));
                }
                System.out.printf("Stage10 geometry %s vertices=%d triangles=%d components=%s%n",input,baseline[0].vertices.size(),baseline[0].polygons.size(),components(baseline[0]));
                screenshot(v,input.getFileName()+"-loaded");
            });
            for(boolean shift:List.of(true,false)) for(KeyCode key:List.of(KeyCode.A,KeyCode.D,KeyCode.W,KeyCode.S)) {
                pulse(()->{Canvas canvas=v.control("canvas");canvas.setFocusTraversable(true);canvas.requestFocus();CameraMotionUiTest.key(v,key,true,false,shift);},200);
                pulse(()->CameraMotionUiTest.key(v,key,false,false,false),150);
                FxTestSupport.run(()->{assertPixels(v,false);sameGeometry(baseline[0],v.selected());assertNull(v.controller.error);});
            }
            int[][] white=new int[1][];
            FxTestSupport.run(()->white[0]=v.controller.currentFrameBuffer().colors().clone());
            pulse(()->{v.button("All");((ComboBox<String>)v.control("colorPicker")).setValue("Red");((Button)v.control("applyColorButton")).fire();});
            FxTestSupport.run(()->{assertTrue(v.selected().polygons.stream().allMatch(p->p.getBaseColor()==ColorRgb.RED));assertPixels(v,true);screenshot(v,input.getFileName()+"-red");});
            pulse(()->((Button)v.control("resetColorButton")).fire());
            FxTestSupport.run(()->{assertTrue(v.selected().polygons.stream().allMatch(p->p.getBaseColor()==ColorRgb.WHITE));assertArrayEquals(white[0],v.controller.currentFrameBuffer().colors());});
            Path output=directory.resolve(input.getFileName());
            pulse(()->{v.controller.file=output.toFile();v.menu("Save Model");assertNull(v.controller.error);assertNotNull(v.controller.info);});
            pulse(()->{v.controller.removeModel(null);v.load(output);});
            FxTestSupport.run(()->{
                sameGeometry(baseline[0],v.selected());assertEquals(components(baseline[0]),components(v.selected()));assertPixels(v,false);assertNull(v.controller.error);
                System.out.println("Stage10 PASS load/frame/Shift-WASD/WASD/All-Red/Reset/save/load "+input);
            });
        } finally {FxTestSupport.run(()->{if(holder[0]!=null)holder[0].close();RenderDiagnostics.flush();});}
    }

    static void sameGeometry(Model a,Model b) {
        assertEquals(a.vertices,b.vertices);assertEquals(a.textureVertices,b.textureVertices);assertEquals(a.normals,b.normals);assertEquals(a.polygons.size(),b.polygons.size());
        for(int i=0;i<a.polygons.size();i++) {var p=a.polygons.get(i);var q=b.polygons.get(i);assertEquals(p.getVertexIndices(),q.getVertexIndices());assertEquals(p.getTextureVertexIndices(),q.getTextureVertexIndices());assertEquals(p.getNormalIndices(),q.getNormalIndices());}
    }
    static List<Integer> components(Model m) {
        int[] parents=new int[m.vertices.size()];for(int i=0;i<parents.length;i++)parents[i]=i;
        for(var p:m.polygons)for(int id:p.getVertexIndices())parents[find(parents,id)]=find(parents,p.getVertexIndices().getFirst());
        Map<Integer,Integer> sizes=new HashMap<>();for(int i=0;i<parents.length;i++)sizes.merge(find(parents,i),1,Integer::sum);
        return sizes.values().stream().sorted().toList();
    }
    static int find(int[] p,int i){while(p[i]!=i){p[i]=p[p[i]];i=p[i];}return i;}
    static void pulse(FxTestSupport.ThrowingRunnable action) throws Exception {pulse(action,120);}
    static void pulse(FxTestSupport.ThrowingRunnable action,int millis) throws Exception {
        var done=new CompletableFuture<Void>();
        FxTestSupport.run(()->{action.run();var pause=new PauseTransition(Duration.millis(millis));pause.setOnFinished(e->done.complete(null));pause.play();});
        done.get(15,TimeUnit.SECONDS);
    }
    static void assertPixels(UiRegressionTest.View v,boolean red) {
        int count=0;Set<Integer> levels=new HashSet<>();var frame=v.controller.currentFrameBuffer();Canvas canvas=v.control("canvas");var read=canvas.snapshot(null,null).getPixelReader();
        for(int y=3;y<frame.height();y+=7)for(int x=3;x<frame.width();x+=7){int c=frame.colorAt(x,y);if(c==0)continue;
            assertEquals(255,c>>>24);assertEquals(c,read.getArgb(x,y));int r=(c>>>16)&255;assertTrue(r>=38);levels.add(r);count++;
            if(red){assertEquals(0,c&0xFFFF);}else{assertEquals(r,c&255);assertEquals(r,(c>>>8)&255);}
        }
        assertTrue(count>20,"Must have visible interior shape");assertTrue(levels.size()>2,"Expected distinguishable face brightness");assertTrue(levels.stream().anyMatch(n->n>100),"External faces must have diffuse");
    }
    static void screenshot(UiRegressionTest.View v,String name)throws Exception {
        String output=System.getProperty("cgengine.stage10.artifacts");if(output==null)return;
        var shot=((Canvas)v.control("canvas")).snapshot(null,null);var image=new java.awt.image.BufferedImage((int)shot.getWidth(),(int)shot.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++)image.setRGB(x,y,shot.getPixelReader().getArgb(x,y));
        Path folder=Path.of(output);Files.createDirectories(folder);javax.imageio.ImageIO.write(image,"png",folder.resolve(name+".png").toFile());
    }
    static void originalCoverage(Model model,Camera camera,FrameBuffer frame) {
        var matrix=MatrixMath.multiply(camera.getProjectionMatrix(),camera.getViewMatrix());List<double[][]> triangles=new ArrayList<>();
        for(var p:model.polygons){double[][] t=new double[3][2];for(int i=0;i<3;i++){var v=model.vertices.get(p.getVertexIndices().get(i));var c=MatrixMath.multiply(matrix,new Vector4f(v.x(),v.y(),v.z(),1));t[i]=new double[]{c.x()/c.w()*frame.width()+frame.width()/2.,-c.y()/c.w()*frame.height()+frame.height()/2.};}triangles.add(t);}
        int checked=0;
        for(int y=0;y<frame.height();y++)for(int x=0;x<frame.width();x++) {
            boolean covered=false,edge=false;
            for(var t:triangles){var a=t[0];var b=t[1];var c=t[2];double det=(b[1]-c[1])*(a[0]-c[0])+(c[0]-b[0])*(a[1]-c[1]);if(Math.abs(det)<1e-8)continue;
                double u=((b[1]-c[1])*(x+.5-c[0])+(c[0]-b[0])*(y+.5-c[1]))/det,w=((c[1]-a[1])*(x+.5-c[0])+(a[0]-c[0])*(y+.5-c[1]))/det,z=1-u-w;
                double min=Math.min(u,Math.min(w,z));if(min>=0)covered=true;if(min>-.03&&min<.03)edge=true;
            }
            if(edge)continue;assertEquals(covered,frame.colorAt(x,y)!=0,"Only original projected triangles may be drawn at "+x+","+y);if(covered)checked++;
        }
        assertTrue(checked>100);
    }
}
