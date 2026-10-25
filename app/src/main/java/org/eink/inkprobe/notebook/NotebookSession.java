package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** One process-wide writer and editing history survive Activity recreation. */
final class NotebookSession {
    interface Callback { void complete(NotebookPage page, IOException error); }
    interface PreviewCallback { void complete(Bitmap bitmap, IOException error); }
    static final class PreviewRequest {
        private volatile boolean cancelled;
        void cancel() { cancelled = true; }
    }
    private interface Operation { void run() throws IOException; }
    private static NotebookSession instance;
    static synchronized NotebookSession get(Context context) {
        if (instance == null) instance = new NotebookSession(context.getApplicationContext());
        return instance;
    }

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final NotebookLibrary document;
    private final AtomicInteger pending = new AtomicInteger();
    private volatile NotebookPage page;
    private volatile IOException problem;
    private volatile boolean undoAvailable, redoAvailable;
    private volatile int pageNumber = 1, pageCount = 1;
    private volatile int notebookId = 1;
    private volatile String notebookName = "My Notebook";
    private volatile List<NotebookCatalog.Entry> entries = Collections.emptyList();
    private List<Integer> windowIds = Collections.emptyList();
    private volatile Map<Integer, NotebookPage> window = Collections.emptyMap();
    private Map<Integer, NotebookPage> deliveredWindow = Collections.emptyMap();
    private volatile Map<Integer, Boolean> undoWindow = Collections.emptyMap(), redoWindow = Collections.emptyMap();
    private volatile NotebookScrollStore.State position;

    private NotebookSession(Context context) {
        document = new NotebookLibrary(new File(context.getFilesDir(), "notebook"));
    }

    NotebookPage current() { return page; }
    boolean isSaving() { return pending.get() != 0; }
    IOException error() { return problem; }
    boolean canUndo() { return undoAvailable; }
    boolean canRedo() { return redoAvailable; }
    int pageNumber() { return pageNumber; }
    int pageCount() { return pageCount; }
    int notebookId() { return notebookId; }
    String notebookName() { return notebookName; }
    List<NotebookCatalog.Entry> entries() { return entries; }
    Map<Integer, NotebookPage> window() { return deliveredWindow; }
    boolean canUndo(int id) { return Boolean.TRUE.equals(undoWindow.get(id)); }
    boolean canRedo(int id) { return Boolean.TRUE.equals(redoWindow.get(id)); }
    NotebookScrollStore.State scrollPosition() { return position; }

    void load(Callback callback) { submit(document::load, callback); }
    void list(Callback callback) { submit(document::loadCatalog, callback); }
    void openNotebook(int id, Callback callback) {
        submit(() -> { document.openNotebook(id); windowIds = Collections.emptyList(); }, callback);
    }
    void createNotebook(String name, Callback callback) {
        submit(() -> { document.createNotebook(name); windowIds = Collections.emptyList(); }, callback);
    }
    void renameNotebook(int id, String name, Callback callback) {
        submit(() -> document.renameNotebook(id, name), callback);
    }
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
    void addPage(int sourcePage, Callback callback) {
        submit(() -> { document.selectPage(sourcePage); document.addPage(); }, callback);
    }
    void window(List<Integer> ids, Callback callback) {
        final List<Integer> requested = new ArrayList<>(ids);
        submit(() -> {
            windowIds = requested;
            for (int id : requested) document.page(id);
        }, callback);
    }
    void appendViewport(Map<Integer, List<NotebookPage.Stroke>> strokes, Callback callback) {
        submit(() -> {
            IOException first = null;
            for (Map.Entry<Integer, List<NotebookPage.Stroke>> entry : strokes.entrySet()) {
                try { document.appendStrokesToPage(entry.getKey(), entry.getValue()); }
                catch (IOException error) { if (first == null) first = error; }
            }
            if (first != null) throw first;
        }, callback);
    }
    void eraseViewport(List<NotebookViewport.EraseGesture> gestures, Callback callback) {
        submit(() -> {
            IOException first = null;
            for (NotebookViewport.EraseGesture gesture : gestures) {
                try { document.eraseOnPagePaths(gesture.pageId, gesture.paths, gesture.radius); }
                catch (IOException error) { if (first == null) first = error; }
            }
            if (first != null) throw first;
        }, callback);
    }
    void undo(int id, Callback callback) { submit(() -> document.undoOnPage(id), callback); }
    void redo(int id, Callback callback) { submit(() -> document.redoOnPage(id), callback); }
    void setTemplate(int id, NotebookPage.Template template, Callback callback) {
        submit(() -> document.setPageTemplate(id, template), callback);
    }
    void saveScroll(int id, double fraction, Callback callback) {
        submit(() -> document.saveScrollPosition(id, fraction), callback);
    }

    /** Read-only whole-book export; failures never enter the notebook save-error state. */
    NotebookPdfExport exportPdf(Context context, int expectedNotebook, String name, int count) {
        NotebookPdfExport job = new NotebookPdfExport(context, expectedNotebook, name, count);
        worker.execute(() -> job.prepare(number -> {
            // A restarted process may restore this screen before any editor has opened.
            // Load the persisted current book, then validate identity without switching it.
            if (number == 1) document.load();
            if (document.notebookId() != expectedNotebook || document.pageCount() != count)
                throw new IOException("The notebook has changed. Reopen it and export again.");
            if (document.error() != null) throw new IOException("Save the notebook before exporting", document.error());
            try { return document.previewPage(number); }
            catch (IOException error) {
                throw new IOException("Couldn’t read page " + number + ". " + error.getMessage(), error);
            }
        }));
        return job;
    }

    /** Read-only, cancellable work on the writer queue, without changing editor snapshots. */
    PreviewRequest previewPage(int expectedNotebook, int number, PreviewCallback callback) {
        PreviewRequest request = new PreviewRequest();
        worker.execute(() -> {
            if (request.cancelled) return;
            Bitmap bitmap = null;
            IOException failure = null;
            try {
                if (document.notebookId() != expectedNotebook)
                    throw new IOException("The notebook has changed");
                NotebookPage preview = document.previewPage(number);
                if (request.cancelled) return;
                bitmap = NotebookPageThumbnail.render(preview, 320, 384, () -> request.cancelled);
            } catch (IOException error) { failure = error; }
            catch (RuntimeException error) { failure = new IOException("Could not preview this page", error); }
            final Bitmap result = bitmap;
            final IOException error = failure;
            main.post(() -> {
                if (request.cancelled) {
                    // Never delivered or bound to a View, so this allocation is ours to release.
                    if (result != null) result.recycle();
                    return;
                }
                callback.complete(result, error);
            });
        });
        return request;
    }

    private void publishWindow() {
        Map<Integer, NotebookPage> pages = new LinkedHashMap<>();
        Map<Integer, Boolean> undo = new LinkedHashMap<>(), redo = new LinkedHashMap<>();
        for (int id : windowIds) {
            try {
                pages.put(id, document.cachedPage(id));
                undo.put(id, document.canUndoOnPage(id)); redo.put(id, document.canRedoOnPage(id));
            } catch (IOException ignored) { /* A failed window load is reported by its operation. */ }
        }
        window = Collections.unmodifiableMap(pages);
        undoWindow = Collections.unmodifiableMap(undo); redoWindow = Collections.unmodifiableMap(redo);
    }

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
            notebookId = document.notebookId();
            notebookName = document.notebookName();
            entries = document.entries();
            position = document.scrollPosition();
            publishWindow();
            problem = failure == null ? document.error() : failure;
            pending.decrementAndGet();
            final NotebookPage snapshot = page;
            final IOException error = problem;
            final Map<Integer, NotebookPage> savedWindow = window;
            main.post(() -> {
                deliveredWindow = savedWindow;
                callback.complete(snapshot, error);
            });
        });
    }
}
