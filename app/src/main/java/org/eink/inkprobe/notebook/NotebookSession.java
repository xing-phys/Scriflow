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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/** One process-wide writer and editing history survive Activity recreation. */
final class NotebookSession {
    interface Callback { void complete(NotebookPage page, IOException error); }
    interface PreviewCallback { void complete(Bitmap bitmap, IOException error); }
    interface LassoCallback { void complete(NotebookLasso.Selection selection, IOException error); }
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

    private final NotebookTasks tasks = new NotebookTasks();
    private final ExecutorService worker = tasks.writer;
    // Writer-owned generation invalidates an export if its source changes between page reads.
    private long revision;
    private NotebookLasso.Clipboard clipboard;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final NotebookLibrary document;
    private final AtomicInteger pending = new AtomicInteger();
    private volatile NotebookPage page;
    private volatile IOException problem;
    private volatile boolean undoAvailable, redoAvailable;
    private volatile boolean pageUndoAvailable, pageRedoAvailable;
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
    boolean canUndoPageChange() { return pageUndoAvailable; }
    boolean canRedoPageChange() { return pageRedoAvailable; }
    int notebookId() { return notebookId; }
    String notebookName() { return notebookName; }
    List<NotebookCatalog.Entry> entries() { return entries; }
    Map<Integer, NotebookPage> window() { return deliveredWindow; }
    boolean canUndo(int id) { return Boolean.TRUE.equals(undoWindow.get(id)); }
    boolean canRedo(int id) { return Boolean.TRUE.equals(redoWindow.get(id)); }
    NotebookScrollStore.State scrollPosition() { return position; }

    void load(Callback callback) { submit(document::load, callback); }
    NotebookLasso.Clipboard clipboard() { return clipboard; }
    void copy(NotebookLasso.Selection selection) { clipboard = new NotebookLasso.Clipboard(selection); }

    void selectLasso(int id, List<NotebookPage.Point> path, LassoCallback callback) {
        final List<NotebookPage.Point> outline = new ArrayList<>(path);
        final NotebookLasso.Selection[] selected = {null};
        submit(() -> selected[0] = NotebookLasso.select(document.page(id), outline),
            (page, error) -> callback.complete(selected[0], error));
    }
    void editSelection(int id, NotebookLasso.Selection selection, NotebookLasso.Action action,
            float dx, float dy, LassoCallback callback) {
        final NotebookLasso.Selection[] selected = {null};
        submit(() -> selected[0] = document.editSelectionOnPage(id, selection, action, dx, dy),
            (page, error) -> callback.complete(selected[0], error));
    }
    void paste(int id, float x, float y, LassoCallback callback) {
        final NotebookLasso.Clipboard source = clipboard;
        final NotebookLasso.Selection[] selected = {null};
        submit(() -> selected[0] = document.pasteOnPage(id, source, x, y),
            (page, error) -> callback.complete(selected[0], error));
    }
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
    void managePages(NotebookBook.PageAction action, int source, int destination, Callback callback) {
        submit(() -> { document.managePages(action, source, destination); windowIds = Collections.emptyList(); }, callback);
    }
    void undoPageChange(Callback callback) {
        submit(() -> { document.undoPageChange(); windowIds = Collections.emptyList(); }, callback);
    }
    void redoPageChange(Callback callback) {
        submit(() -> { document.redoPageChange(); windowIds = Collections.emptyList(); }, callback);
    }
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
        worker.execute(() -> {
            final long expectedRevision = revision;
            tasks.exports.execute(() -> job.prepare(number -> readExportPage(
                expectedNotebook, count, expectedRevision, number)));
        });
        return job;
    }

    interface TransferCallback { void complete(IOException error); }

    void writeBackup(File destination, java.util.function.BooleanSupplier cancelled, TransferCallback callback) {
        worker.execute(() -> {
            final long version = revision;
            final int id = document.notebookId(), count = document.pageCount();
            final String name = document.notebookName();
            tasks.exports.execute(() -> {
                IOException failure = null;
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(destination)) {
                    NotebookBackup.write(output, name, count,
                        number -> readExportPage(id, count, version, number), cancelled);
                    output.getFD().sync();
                } catch (IOException | RuntimeException error) { failure = new IOException("Could not back up notebook", error); }
                final IOException result = failure;
                main.post(() -> callback.complete(result));
            });
        });
    }

    void importBackup(NotebookBackup.Restored restored, TransferCallback callback) {
        worker.execute(() -> {
            IOException failure = null;
            try { ++revision; document.importNotebook(restored); windowIds = Collections.emptyList(); }
            catch (IOException | RuntimeException error) { failure = new IOException("Could not restore notebook. " + error.getMessage(), error); }
            final IOException result = failure;
            main.post(() -> callback.complete(result));
        });
    }

    /** Immutable source handoff; neither native replay nor PDF compression holds the writer. */
    private NotebookPage readExportPage(int expectedNotebook, int count, long expectedRevision,
                                        int number) throws IOException {
        try {
            return worker.submit(() -> {
                if (number == 1) document.load();
                if (revision != expectedRevision || document.notebookId() != expectedNotebook
                        || document.pageCount() != count)
                    throw new IOException("The notebook changed. Export again when editing has finished.");
                if (document.error() != null) throw new IOException("Save the notebook before exporting", document.error());
                return document.previewPage(number);
            }).get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new IOException("Export interrupted", error);
        } catch (ExecutionException error) {
            if (error.getCause() instanceof IOException) throw (IOException) error.getCause();
            throw new IOException("Could not read export source", error.getCause());
        }
    }

    /** Load immutable samples on the writer, then replay on a bounded render queue. */
    PreviewRequest previewPage(int expectedNotebook, int number, PreviewCallback callback) {
        PreviewRequest request = new PreviewRequest();
        worker.execute(() -> {
            if (request.cancelled) return;
            try {
                if (document.notebookId() != expectedNotebook) throw new IOException("The notebook has changed");
                NotebookPage preview = document.previewPage(number);
                tasks.render.execute(() -> {
                    if (request.cancelled) return;
                    Bitmap bitmap = null; IOException failure = null;
                    try { bitmap = NotebookPageThumbnail.render(preview, 320, 384, () -> request.cancelled); }
                    catch (RuntimeException error) { failure = new IOException("Could not preview this page", error); }
                    deliverPreview(request, callback, bitmap, failure);
                });
            } catch (IOException | RejectedExecutionException error) {
                deliverPreview(request, callback, null, new IOException("Could not load preview. Try again.", error));
            }
        });
        return request;
    }

    private void deliverPreview(PreviewRequest request, PreviewCallback callback, Bitmap result, IOException error) {
        main.post(() -> {
            if (request.cancelled) { if (result != null) result.recycle(); return; }
            callback.complete(result, error);
        });
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
            try { ++revision; operation.run(); }
            catch (IOException error) { failure = error; }
            catch (RuntimeException error) { failure = new IOException("Could not save the page", error); }
            page = document.current();
            undoAvailable = document.canUndo();
            redoAvailable = document.canRedo();
            pageNumber = document.pageNumber();
            pageCount = document.pageCount();
            pageUndoAvailable = document.canUndoPageChange();
            pageRedoAvailable = document.canRedoPageChange();
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
