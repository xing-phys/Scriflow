package org.eink.inkprobe.notebook;

import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Archive IO stays off both the UI thread and the notebook's handwriting writer. */
final class NotebookWebsiteTasks {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> new Thread(r, "website-storage"));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    interface Result<T> { void complete(T value, IOException error); }
    private NotebookWebsiteTasks() { }

    static <T> void run(Callable<T> task, Result<T> result) {
        IO.execute(() -> {
            T value = null;
            IOException error = null;
            try { value = task.call(); }
            catch (Exception failure) {
                error = failure instanceof IOException ? (IOException) failure : new IOException("Could not access Saved websites.", failure);
            }
            final T completed = value;
            final IOException failed = error;
            MAIN.post(() -> result.complete(completed, failed));
        });
    }
}
