package com.cgengine.model;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SceneStateTest {
 @Test void removalKeepsSelectionValidIncludingEmptyScene(){SceneState scene=new SceneState();Model a=new Model(),b=new Model();scene.addModel(a);scene.addModel(b);assertFalse(scene.select(new Model()));assertTrue(scene.select(a));assertTrue(scene.removeModel(b));assertSame(a,scene.selectedModel());assertSame(a,scene.models().getFirst());assertTrue(scene.removeModel(a));assertNull(scene.selectedModel());assertTrue(scene.models().isEmpty());scene.addModel(b);assertSame(b,scene.selectedModel());}
}
