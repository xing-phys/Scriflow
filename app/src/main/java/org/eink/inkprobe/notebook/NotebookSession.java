package org.eink.inkprobe.notebook;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** One process-wide writer survives Activity recreation and keeps unsaved work in memory. */
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
    private final NotebookStore store;
    private final AtomicInteger pending = new AtomicInteger();
    private volatile NotebookPage page;
    private volatile IOException problem;
    private boolean loaded;

    private NotebookSession(Context context) {
        store = new NotebookStore(new File(context.getFilesDir(), "notebook"));
    }

    NotebookPage current() { return page; }
    boolean isSaving() { return pending.get() != 0; }
    IOException error() { return problem; }

    void load(Callback callback) {
        // A recreated Activity waits behind outstanding writes before restoring its canvas.
        worker.execute(() -> {
            if (!loaded) {
                try { page = store.load(); loaded = true; problem = null; }
                catch (IOException error) { problem = error; }
            }
            deliver(callback);
        });
    }

    void initializeSize(int width, int height, Callback callback) {
        submit(() -> {
            requirePage();
            if (!page.isInitialized()) page = page.withSize(width, height);
            requireWritable();
            store.initializeSize(width, height);
        }, callback);
    }

    void append(NotebookPage.Stroke stroke, Callback callback) {
        submit(() -> {
            requirePage();
            // Retain every completed stroke even if an earlier queued write failed.
            page = page.withStroke(stroke);
            requireWritable();
            store.appendStroke(stroke);
        }, callback);
    }

    void setTemplate(NotebookPage.Template template, Callback callback) {
        submit(() -> {
            requirePage();
            page = page.withTemplate(template);
            requireWritable();
            store.setTemplate(template);
        }, callback);
    }

    void retry(Callback callback) {
        submit(() -> {
            NotebookPage intended = page;
            NotebookPage disk = store.load();
            if (intended == null) {
                page = disk; loaded = true; problem = null; return;
            }
            if (!disk.isInitialized() && intended.isInitialized())
                disk = store.initializeSize(intended.width, intended.height);
            if (disk.width != intended.width || disk.height != intended.height
                    || disk.strokes.size() > intended.strokes.size())
                throw new IOException("The saved page differs from the open page; files were preserved.");
            for (int i = 0; i < disk.strokes.size(); i++) {
                if (!sameStroke(disk.strokes.get(i), intended.strokes.get(i)))
                    throw new IOException("The saved page differs from the open page; files were preserved.");
            }
            // A previous failed sync may nevertheless have written a full record. Compare the
            // exact prefix before retrying, so a retry cannot duplicate that stroke.
            for (int i = disk.strokes.size(); i < intended.strokes.size(); i++)
                store.appendStroke(intended.strokes.get(i));
            store.setTemplate(intended.template);
            store.sync();
            page = store.current(); loaded = true; problem = null;
        }, callback);
    }

    private void submit(Operation operation, Callback callback) {
        pending.incrementAndGet();
        worker.execute(() -> {
            try { operation.run(); }
            catch (IOException error) { problem = error; }
            catch (RuntimeException error) { problem = new IOException("Could not save the page", error); }
            finally { pending.decrementAndGet(); }
            deliver(callback);
        });
    }

    private void requirePage() throws IOException {
        if (page == null) throw new IOException("The notebook has not loaded.");
    }
    private void requireWritable() throws IOException {
        if (problem != null) throw problem;
    }
    private void deliver(Callback callback) {
        final NotebookPage snapshot = page;
        final IOException error = problem;
        main.post(() -> callback.complete(snapshot, error));
    }
    private static boolean sameStroke(NotebookPage.Stroke a, NotebookPage.Stroke b) {
        if (a.penType != b.penType || a.color != b.color || !same(a.width, b.width)
                || a.points.size() != b.points.size()) return false;
        for (int i = 0; i < a.points.size(); i++) {
            NotebookPage.Point p = a.points.get(i), q = b.points.get(i);
            if (!same(p.x, q.x) || !same(p.y, q.y) || !same(p.pressure, q.pressure)
                    || p.time != q.time) return false;
        }
        return true;
    }
    private static boolean same(float a, float b) {
        return Float.floatToIntBits(a) == Float.floatToIntBits(b);
    }
}
