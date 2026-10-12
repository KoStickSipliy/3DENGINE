package com.cgengine.render_engine;

import com.cgengine.render_engine.Rasterization.FrameBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RenderMetricsTest {
    @Test void reportsMeanP95AndMaximumInMilliseconds() {
        RenderMetrics metrics = new RenderMetrics(4);
        metrics.recordNanos(1_000_000); metrics.recordNanos(2_000_000);
        metrics.recordNanos(3_000_000); metrics.recordNanos(100_000_000);
        assertEquals(26.5, metrics.meanMillis(), 0.0001);
        assertEquals(100, metrics.p95Millis(), 0.0001);
        assertEquals(100, metrics.maxMillis(), 0.0001);
    }
    @Test void framebufferClearsWithoutReplacingItsStorage() {
        FrameBuffer buffer = new FrameBuffer(3, 2);
        int[] before = buffer.colors(); buffer.writeIfCloser(1, 1, .5f, 0xFFFF0000);
        buffer.clear(0);
        assertSame(before, buffer.colors());
        assertEquals(0, buffer.colorAt(1, 1));
        assertEquals(Float.POSITIVE_INFINITY, buffer.depthAt(1, 1));
    }
}
