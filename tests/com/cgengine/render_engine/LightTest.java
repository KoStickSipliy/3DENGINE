package com.cgengine.render_engine;
import com.cgengine.math.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LightTest {
 @Test void d04BrightnessMatchesReferenceNormals(){
  Light light=Light.CAMERA_DIRECTIONAL;
  assertEquals(1f,light.brightness(new Vector3f(0,0,-1)),.00001f);
  assertEquals(1f,light.brightness(new Vector3f(0,0,-7)),.00001f);
  assertEquals(.575f,light.brightness(new Vector3f((float)(Math.sqrt(3)/2),0,-.5f)),.00001f);
  assertEquals(.15f,light.brightness(new Vector3f(1,0,0)),.00001f);
  assertEquals(.15f,light.brightness(new Vector3f(0,0,1)),.00001f);
  assertEquals(.15f,light.brightness(new Vector3f(0,0,0)),.00001f);
  assertEquals(.15f,light.brightness(null),.00001f);
 }
 @Test void shadesBaseColorWithoutChangingAlpha(){
  assertEquals(0x80A06030,Light.CAMERA_DIRECTIONAL.shade(0x80A06030,new Vector3f(0,0,-1)));
  assertEquals(0x80180E07,Light.CAMERA_DIRECTIONAL.shade(0x80A06030,new Vector3f(0,0,1)));
 }
}
