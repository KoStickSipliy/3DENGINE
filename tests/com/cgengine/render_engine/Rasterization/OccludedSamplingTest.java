package com.cgengine.render_engine.Rasterization;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OccludedSamplingTest {
    @Test void occludedEqualAndNonFiniteDepthNeverInvokeShader() {
        FrameBuffer buffer = new FrameBuffer(8, 8);
        TriangleRasterizer.rasterize(buffer, new RasterVertex(0,0,.2f),
                new RasterVertex(8,0,.2f), new RasterVertex(0,8,.2f), 0xffff0000);
        for (float depth : new float[]{.8f, .2f, Float.NaN, Float.POSITIVE_INFINITY}) {
            TriangleRasterizer.rasterize(buffer, new RasterVertex(0,0,depth),
                    new RasterVertex(8,0,depth), new RasterVertex(0,8,depth),
                    (a,b,c) -> { fail("Invisible fragment must not sample texture"); return 0; });
        }
        assertEquals(0xffff0000, buffer.colorAt(1,1));
    }
}
