package org.eink.inkprobe.notebook;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Resource ownership: mutations serialize; expensive derived output never runs on the writer. */
final class NotebookTasks {
    final ExecutorService writer = Executors.newSingleThreadExecutor(r -> new Thread(r, "notebook-writer"));
    final ExecutorService render = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(8), r -> new Thread(r, "notebook-render"),
        new ThreadPoolExecutor.AbortPolicy());
    final ExecutorService exports = Executors.newSingleThreadExecutor(r -> new Thread(r, "notebook-export"));
    void shutdown() { writer.shutdownNow(); render.shutdownNow(); exports.shutdownNow(); }
}
