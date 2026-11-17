package org.eink.inkprobe.notebook;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Serial book metadata IO never shares the handwriting writer. */
final class NotebookEbookTasks {
    static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> new Thread(r, "ebook-storage"));
    static final Handler MAIN = new Handler(Looper.getMainLooper());
    interface Result<T> { void complete(T value, Exception error); }
    static <T> void run(Callable<T> work, Result<T> result) {
        IO.execute(() -> {
            T value = null; Exception error = null;
            try { value = work.call(); } catch (Exception failure) { error = failure; }
            final T completed = value; final Exception failed = error;
            MAIN.post(() -> result.complete(completed, failed));
        });
    }
    private NotebookEbookTasks() { }
}
