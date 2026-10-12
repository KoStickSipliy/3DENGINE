package com.cgengine;

import com.cgengine.model.ModelValidator;
import com.cgengine.render_engine.*;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit local acceptance: never silently shortened by a system property. */
class Stage11MixedSessionTest {
    @TempDir Path directory;

    @Test void thirtyMinuteMixedSession() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("cgengine.stage11.mixed"), "Enable local 30-minute desktop acceptance explicitly");
        List<Path> inputs = new ArrayList<>();
        for(String name:List.of("cube","tetrahedron","octahedron","icosahedron"))inputs.add(Path.of("Test_Models/"+name+".obj"));
        for(String name:List.of("WrapJaw","WrapSkull","WrapLowerTeeth","WrapUpperTeeth","WrapBody"))inputs.add(Path.of("3DModels_DELETE/Faceform/"+name+".obj"));
        inputs.add(Path.of("3DModels_DELETE/Faceform/AlexWithTexture/AlexNeutralWrapped.obj"));
        Path hands=Path.of("3DModels_DELETE/Hands/Rigged Hand.obj");
        // Find the archive by filename; no dependency on the enclosing archive folder spelling.
        try(var paths=Files.walk(Path.of("3DModels_DELETE"))) {hands=paths.filter(p->p.getFileName().toString().equals("Rigged Hand.obj")).findFirst().orElseThrow();}
        inputs.add(hands);
        for(Path input:inputs)assertTrue(Files.exists(input),"Required mixed-session archive: "+input);
        var holder=new UiRegressionTest.View[1];
        var queued=new ArrayList<Double>();var uploaded=new ArrayList<Double>();
        var failures=new CopyOnWriteArrayList<Throwable>();
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{failures.add(error);error.printStackTrace();});
        long start=System.nanoTime();int cycle=-1,actions=0,cycleActions=0;long heapBefore=0;
        try {
            Stage10UiTest.pulse(()->{
                holder[0]=new UiRegressionTest.View();var v=holder[0];
                Canvas canvas=v.control("canvas");canvas.setWidth(1280);canvas.setHeight(720);
                v.controller.addCamera(null);
            });
            System.out.printf("Stage11 mixed START pid=%d durationSeconds=1800%n",ProcessHandle.current().pid());
            while(System.nanoTime()-start<TimeUnit.MINUTES.toNanos(30)) {
                var v=holder[0];int next=(int)TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-start)/60;
                if(next!=cycle) {
                    cycle=next;cycleActions=0;Path input=inputs.get(cycle%inputs.size());int minute=cycle;
                    Stage10UiTest.pulse(()->{
                        while(!v.models().getItems().isEmpty())v.controller.removeModel(null);
                        v.load(input);v.load(Path.of("Test_Models/cube.obj"));
                        v.models().getSelectionModel().select(0);v.menu("Frame Selected");
                        Canvas canvas=v.control("canvas");canvas.setFocusTraversable(true);canvas.requestFocus();
                        CameraMotionUiTest.key(v,KeyCode.D,true,false,true);
                    },1000);
                    System.gc();long heap=usedHeap();if(cycle==1)heapBefore=heap;
                    int threads=Thread.getAllStackTraces().size();
                    System.out.printf("Stage11 mixed minute=%d model=%s heapBytes=%d javaThreads=%d frames=%d%n",minute,input,heap,threads,v.controller.renderedFrames());
                    assertTrue(failures.isEmpty(),"Uncaught exceptions: "+failures);
                }
                Thread.sleep(500);
                int action=cycleActions++;actions++;long posted=System.nanoTime();var published=new CompletableFuture<Void>();
                FxTestSupport.run(()->{
                    queued.add((System.nanoTime()-posted)/1e6);
                    switch(action%12) {
                        case 0 -> {v.button("All");((ComboBox<String>)v.control("colorPicker")).setValue("Red");v.button("Apply");}
                        case 1 -> v.controller.resetPolygonColor(null);
                        case 2 -> {v.models().getSelectionModel().select(1);assertEquals("",((TextField)v.control("polygonIndices")).getText());}
                        case 3 -> {v.models().getSelectionModel().select(0);v.menu("Frame Selected");}
                        case 4 -> {var cameras=(ListView<Camera>)v.control("listViewCameras");cameras.getSelectionModel().select(1-cameras.getSelectionModel().getSelectedIndex());v.menu("Frame Selected");}
                        case 5 -> {v.text("inputForModelRotation","10 20 5");v.text("inputForModelScaling","1.2 1.5 .8");v.button("Apply transform");}
                        case 6 -> {v.button("Reset");v.menu("Frame Selected");}
                        case 7 -> {v.controller.file=null;v.menu("Load Model");v.menu("Save Model");}
                        case 8 -> {Canvas canvas=v.control("canvas");canvas.setWidth(1280);canvas.setHeight(720);}
                        case 9 -> {Canvas canvas=v.control("canvas");canvas.setWidth(960);canvas.setHeight(540);}
                        case 10 -> {v.controller.file=directory.resolve("roundtrip.obj").toFile();v.menu("Save Model");}
                        case 11 -> {var expected=v.selected().clone();v.load(directory.resolve("roundtrip.obj"));Stage10UiTest.sameGeometry(expected,v.selected());v.controller.removeModel(null);v.models().getSelectionModel().select(0);}
                    }
                    assertNull(v.controller.error);ModelValidator.validate(v.selected());
                    Canvas canvas=v.control("canvas");canvas.setFocusTraversable(true);canvas.requestFocus();
                    CameraMotionUiTest.key(v,KeyCode.D,true,false,true);
                    long before=v.controller.renderedFrames();
                    new AnimationTimer(){@Override public void handle(long now){if(v.controller.renderedFrames()>before){stop();uploaded.add((System.nanoTime()-posted)/1e6);published.complete(null);}}}.start();
                });
                published.get(10,TimeUnit.SECONDS);
            }
            System.gc();long heap=usedHeap();
            stats("UI queue",queued);stats("action-to-upload (includes handler)",uploaded);
            System.out.printf("Stage11 mixed COMPLETE elapsedSeconds=%.3f actions=%d heapGrowthBytes=%d javaThreads=%d uncaught=%d%n",(System.nanoTime()-start)/1e9,actions,heap-heapBefore,Thread.getAllStackTraces().size(),failures.size());
            queued.sort(Double::compare);assertTrue(queued.get((int)Math.ceil(queued.size()*.95)-1)<=100,"Mixed UI p95");assertTrue(queued.getLast()<500,"Mixed UI max");
            assertTrue(heap-heapBefore<=64L*1024*1024,"Mixed post-warm-up retained heap growth");assertTrue(failures.isEmpty());
        } finally {
            FxTestSupport.run(()->{if(holder[0]!=null)holder[0].close();RenderDiagnostics.flush();});
            Thread.setDefaultUncaughtExceptionHandler(previous);
        }
    }
    static long usedHeap(){return Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();}
    static void stats(String name,List<Double> data){data.sort(Double::compare);System.out.printf(Locale.ROOT,"Stage11 mixed %s n=%d p95=%.3fms max=%.3fms%n",name,data.size(),data.get((int)Math.ceil(data.size()*.95)-1),data.getLast());}
}

