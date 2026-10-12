package com.cgengine.render_engine;

import java.util.Arrays;

/** Collects frame times outside the production render loop. */
public final class RenderMetrics {
    private final long[] samples;
    private int count;

    public RenderMetrics(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        samples = new long[capacity];
    }

    public void recordNanos(long elapsedNanos) {
        if (elapsedNanos < 0) throw new IllegalArgumentException("elapsedNanos must not be negative");
        if (count == samples.length) throw new IllegalStateException("metrics capacity exceeded");
        samples[count++] = elapsedNanos;
    }

    public int count() { return count; }
    public double meanMillis() { long total = 0; for (int i = 0; i < count; i++) total += samples[i]; return count == 0 ? 0 : total / 1_000_000.0 / count; }
    public double p95Millis() { return percentileMillis(0.95); }
    public double maxMillis() { return percentileMillis(1.0); }

    private double percentileMillis(double percentile) {
        if (count == 0) return 0;
        long[] sorted = Arrays.copyOf(samples, count);
        Arrays.sort(sorted);
        return sorted[(int) Math.ceil(percentile * count) - 1] / 1_000_000.0;
    }
}
