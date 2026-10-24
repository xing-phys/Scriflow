package org.eink.inkprobe.notebook;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** One process-wide writer and editing history survive Activity recreation. */
final class NotebookSession {
    interface Callback { void complete(NotebookPage page, IOException error); }
    private interface Operation { void run() throws IOException; }
    private static NotebookSession instance;
    static synchronized NotebookSession get(Context context) {
        if (instance == null) instance = new NotebookSession(context.getApplicationContext());
        return instance;
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final NotebookBook document;
    private final AtomicInteger pending = new AtomicInteger();
    private volatile NotebookPage page;
    private volatile IOException problem;
    private volatile boolean undoAvailable, redoAvailable;
    private volatile int pageNumber = 1, pageCount = 1;

    private NotebookSession(Context context) {
        document = new NotebookBook(new File(context.getFilesDir(), "notebook"));
    }

    NotebookPage current() { return page; }
    boolean isSaving() { return pending.get() != 0; }
    IOException error() { return problem; }
    boolean canUndo() { return undoAvailable; }
    boolean canRedo() { return redoAvailable; }
    int pageNumber() { return pageNumber; }
    int pageCount() { return pageCount; }

    void load(Callback callback) { submit(document::load, callback); }
    void initializeSize(int width, int height, Callback callback) {
        submit(() -> document.initializeSize(width, height), callback);
    }
    void append(NotebookPage.Stroke stroke, Callback callback) {
        submit(() -> document.append(stroke), callback);
    }
    void erase(List<NotebookPage.Point> path, float radius, Callback callback) {
        submit(() -> document.erase(path, radius), callback);
    }
    void undo(Callback callback) { submit(document::undo, callback); }
    void redo(Callback callback) { submit(document::redo, callback); }
    void setTemplate(NotebookPage.Template template, Callback callback) {
        submit(() -> document.setTemplate(template), callback);
    }
    void retry(Callback callback) { submit(document::retry, callback); }
    void selectPage(int number, Callback callback) {
        submit(() -> document.selectPage(number), callback);
    }
    void addPage(Callback callback) { submit(document::addPage, callback); }

    private void submit(Operation operation, Callback callback) {
        pending.incrementAndGet();
        worker.execute(() -> {
            IOException failure = null;
            try { operation.run(); }
            catch (IOException error) { failure = error; }
            catch (RuntimeException error) { failure = new IOException("Could not save the page", error); }
            page = document.current();
            undoAvailable = document.canUndo();
            redoAvailable = document.canRedo();
            pageNumber = document.pageNumber();
            pageCount = document.pageCount();
            problem = failure == null ? document.error() : failure;
            pending.decrementAndGet();
            final NotebookPage snapshot = page;
            final IOException error = problem;
            main.post(() -> callback.complete(snapshot, error));
        });
    }
}
