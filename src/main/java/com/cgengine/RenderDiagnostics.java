package com.cgengine;

import java.util.*;

/** Opt-in stage-1 measurements. Disabled unless -Dcgengine.diagnostics=true. */
final class RenderDiagnostics {
    static final boolean ENABLED = Boolean.getBoolean("cgengine.diagnostics");
    static final String MODE = System.getProperty("cgengine.diagnostics.mode", "normal");
    private static final Map<String, List<Double>> samples = new TreeMap<>();
    private static long previousFrame;
    private static long previousReport = System.nanoTime();

    static synchronized void measure(String name, long nanos) {
        if (!ENABLED) return;
        samples.computeIfAbsent(name, key -> new ArrayList<>()).add(nanos / 1e6);
        if (nanos >= 500_000_000L) event("slow " + name + " ms=" + nanos / 1e6);
    }
    static synchronized void frameStarted(long now) {
        if (previousFrame != 0) measure("frame-start-gap", now - previousFrame);
        previousFrame = now;
        if (now - previousReport > 2_000_000_000L) { flush(); previousReport = now; }
    }
    static synchronized void flush() {
        for (var entry : samples.entrySet()) {
            var values = entry.getValue();
            if (values.isEmpty()) continue;
            values.sort(Double::compare);
            event(String.format(Locale.ROOT, "aggregate %s n=%d meanMs=%.3f p95Ms=%.3f maxMs=%.3f",
                    entry.getKey(), values.size(), values.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                    values.get((int)Math.ceil(values.size() * .95) - 1), values.getLast()));
            values.clear();
        }
    }
    static synchronized void event(String message) {
        System.out.println(java.time.Instant.now() + " pid=" + ProcessHandle.current().pid()
                + " mode=" + MODE + " " + message);
    }
}
