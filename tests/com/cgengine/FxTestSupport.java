package com.cgengine;

import javafx.application.Platform;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

final class FxTestSupport {
    static {
        Platform.startup(() -> Platform.setImplicitExit(false));
    }
    static void run(ThrowingRunnable action) throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> { action.run(); return null; });
        Platform.runLater(task);
        task.get(60, TimeUnit.SECONDS);
    }
    interface ThrowingRunnable { void run() throws Exception; }
}
