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
import java.util.UUID;
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
    private final File directory;
    private boolean initialPaperChecked, initialPaperRequired;
    private final AtomicInteger pending = new AtomicInteger();
    private volatile NotebookPage page;
    private volatile IOException problem;
    private volatile boolean undoAvailable, redoAvailable;
    private volatile boolean pageUndoAvailable, pageRedoAvailable;
    private volatile int pageNumber = 1, pageCount = 1;
    private volatile int notebookId = 1;
    private volatile String notebookName = "My Notebook";
    private volatile List<NotebookCatalog.Entry> entries = Collections.emptyList();
    private volatile List<NotebookCatalog.Folder> folders = Collections.emptyList();
    private volatile int availableNotebookId;
    private List<Integer> windowIds = Collections.emptyList();
    private volatile Map<Integer, NotebookPage> window = Collections.emptyMap();
    private Map<Integer, NotebookPage> deliveredWindow = Collections.emptyMap();
    private volatile Map<Integer, Boolean> undoWindow = Collections.emptyMap(), redoWindow = Collections.emptyMap();
    private volatile NotebookScrollStore.State position;
    private volatile File pdfSource;
    private volatile List<NotebookOutline.Entry> outline = Collections.emptyList();
    private volatile Map<Integer, UUID> pageIds = Collections.emptyMap();
    private volatile Map<UUID, Integer> pageNumbers = Collections.emptyMap();
    private volatile Map<Integer, Integer> pdfPages = Collections.emptyMap();
    private volatile List<NotebookViewport.Size> pageSizes = Collections.emptyList();
    private int navigationBook, navigationCount;

    private NotebookSession(Context context) {
        directory = new File(context.getFilesDir(), "notebook");
        document = new NotebookLibrary(directory);
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
    List<NotebookCatalog.Folder> folders() { return folders; }
    int availableNotebookId() { return availableNotebookId; }
    Map<Integer, NotebookPage> window() { return deliveredWindow; }
    boolean canUndo(int id) { return Boolean.TRUE.equals(undoWindow.get(id)); }
    boolean canRedo(int id) { return Boolean.TRUE.equals(redoWindow.get(id)); }
    NotebookScrollStore.State scrollPosition() { return position; }
    File pdfSource() { return pdfSource; }
    List<NotebookViewport.Size> pageSizes() { return pageSizes; }
    List<NotebookOutline.Entry> outline() { return outline; }
    UUID pageId(int number) { return pageIds.get(number); }
    int pageNumber(UUID id) { Integer number = pageNumbers.get(id); return number == null ? 0 : number; }
    int pageForPdf(int sourcePage) { Integer number = pdfPages.get(sourcePage); return number == null ? 0 : number; }
    void setOutline(List<NotebookOutline.Entry> entries, Callback callback) {
        final List<NotebookOutline.Entry> copy = new ArrayList<>(entries);
        submit(() -> document.setOutline(copy), callback);
    }

    void load(Callback callback) { submit(() -> { prepareInitialPaper(); document.load(); }, callback); }
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
    void moveSelection(int source, int destination, NotebookLasso.Selection selection, float centerX, float centerY,
            LassoCallback callback) {
        final NotebookLasso.Selection[] selected = {null};
        submit(() -> selected[0] = document.moveSelection(source, destination, selection, centerX, centerY),
            (page, error) -> callback.complete(selected[0], error));
    }
    void paste(int id, float x, float y, LassoCallback callback) {
        final NotebookLasso.Clipboard source = clipboard;
        final NotebookLasso.Selection[] selected = {null};
        submit(() -> selected[0] = document.pasteOnPage(id, source, x, y),
            (page, error) -> callback.complete(selected[0], error));
    }
    void list(Callback callback) { submit(() -> { prepareInitialPaper(); document.loadCatalog(); }, callback); }
    void openNotebook(int id, Callback callback) {
        submit(() -> { document.openNotebook(id); windowIds = Collections.emptyList(); navigationBook = 0; }, callback);
    }
    void createNotebook(String name, Callback callback) {
        createNotebook(name, NotebookPaperSize.A5, callback);
    }
    void createNotebook(String name, NotebookPaperSize paperSize, Callback callback) {
        createNotebook(name, paperSize, 0, callback);
    }
    void createNotebook(String name, NotebookPaperSize paperSize, int folderId, Callback callback) {
        submit(() -> { document.createNotebook(name, paperSize, folderId); windowIds = Collections.emptyList(); navigationBook = 0; }, callback);
    }
    void renameNotebook(int id, String name, Callback callback) {
        submit(() -> document.renameNotebook(id, name), callback);
    }
    void createFolder(String name, Callback callback) { submit(() -> document.createFolder(name), callback); }
    void renameFolder(int id, String name, Callback callback) { submit(() -> document.renameFolder(id, name), callback); }
    void deleteFolder(int id, Callback callback) { submit(() -> document.deleteFolder(id), callback); }
    void moveNotebook(int id, int folder, Callback callback) { submit(() -> document.moveNotebook(id, folder), callback); }
    void trashNotebook(int id, Callback callback) {
        submit(() -> { document.trashNotebook(id); windowIds = Collections.emptyList(); navigationBook = 0; }, callback);
    }
    void restoreNotebook(int id, Callback callback) { submit(() -> document.restoreNotebook(id), callback); }
    void permanentlyDeleteNotebook(int id, Callback callback) {
        submit(() -> { document.permanentlyDeleteNotebook(id); windowIds = Collections.emptyList(); navigationBook = 0; }, callback);
    }
    void initializeSize(int width, int height, Callback callback) {
        submit(() -> { document.initializeSize(width, height); navigationBook = 0; }, callback);
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
    void retry(Callback callback) { submit(() -> { prepareInitialPaper(); document.retry(); navigationBook = 0; }, callback); }
    void selectPage(int number, Callback callback) {
        submit(() -> document.selectPage(number), callback);
    }
    void addPage(Callback callback) {
        submit(() -> { document.addPage(); windowIds = Collections.emptyList(); navigationBook = 0; }, callback);
    }
    void managePages(NotebookBook.PageAction action, int source, int destination, Callback callback) {
        submit(() -> { document.managePages(action, source, destination); windowIds = Collections.emptyList(); navigationBook = 0; }, callback);
    }
    void undoPageChange(Callback callback) {
        submit(() -> { document.undoPageChange(); windowIds = Collections.emptyList(); navigationBook = 0; }, callback);
    }
    void redoPageChange(Callback callback) {
        submit(() -> { document.redoPageChange(); windowIds = Collections.emptyList(); navigationBook = 0; }, callback);
    }
    void addPage(int sourcePage, Callback callback) {
        submit(() -> { document.selectPage(sourcePage); document.addPage(); windowIds = Collections.emptyList(); navigationBook = 0; }, callback);
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
    void insertFigure(int expectedBook, int number, String pageId, NotebookImages.Imported image, Callback callback) {
        // Reject invalid input before the edit transaction so a capacity/target error never pauses writing.
        worker.execute(() -> {
            try {
                if (document.error() != null) throw new IOException("Return to the note and retry saving before inserting another figure", document.error());
                if (document.notebookId() != expectedBook) throw new IOException("The notebook changed; insert the figure again");
                NotebookPage target = document.previewPage(number);
                if (!target.id.toString().equals(pageId)) throw new IOException("The page changed; insert the figure again");
                List<NotebookFigure> figures = new ArrayList<>(target.figures); figures.add(image.place(target));
                target.withFigures(figures);
                submit(() -> {
                    if (document.notebookId() != expectedBook || !document.previewPage(number).id.toString().equals(pageId))
                        throw new IOException("The page changed; insert the figure again");
                    document.setFigures(number, figures);
                }, callback);
            } catch (IOException | IllegalArgumentException error) {
                main.post(() -> callback.complete(null, new IOException(error.getMessage(), error)));
            }
        });
    }
    void previewFigurePlacement(NotebookPage page, PreviewCallback callback) {
        final File source = pdfSource;
        try { tasks.render.execute(() -> {
            Bitmap bitmap = null; IOException error = null;
            float scale = Math.min(1, 1024f / Math.max(page.width, page.height));
            try { bitmap = NotebookPageThumbnail.render(page, source, Math.max(1, Math.round(page.width * scale)),
                Math.max(1, Math.round(page.height * scale)), () -> false); }
            catch (IOException | RuntimeException failure) { error = new IOException("Cannot prepare figure placement", failure); }
            final Bitmap result = bitmap; final IOException failure = error;
            main.post(() -> callback.complete(result, failure));
        }); } catch (RejectedExecutionException error) {
            main.post(() -> callback.complete(null, new IOException("Preview queue is busy. Try again.", error)));
        }
    }
    void prepareSelection(Context context, NotebookPage source, TransferFileCallback callback) {
        tasks.exports.execute(() -> {
            File directory = null;
            try {
                directory = new File(context.getCacheDir(), "selection-source-" + UUID.randomUUID());
                if (!directory.mkdir()) throw new IOException("Cannot prepare selection export");
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(new File(directory, "page.nbk"))) {
                    NotebookStore.writeSnapshot(output, source); output.getFD().sync();
                }
                final File result = directory; main.post(() -> callback.complete(result, null));
            } catch (IOException error) {
                if (directory != null) { new File(directory, "page.nbk").delete(); directory.delete(); }
                main.post(() -> callback.complete(null, error));
            }
        });
    }
    interface TransferFileCallback { void complete(File file, IOException error); }

    void setFigures(int id, List<NotebookFigure> figures, Callback callback) {
        final List<NotebookFigure> copy = new ArrayList<>(figures);
        submit(() -> document.setFigures(id, copy), callback);
    }
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
            final File source = document.sourcePdf();
            final List<NotebookOutline.Entry> headings;
            try { headings = document.outline(); }
            catch (IOException error) { job.fail(error); return; }
            tasks.exports.execute(() -> job.prepare(number -> readExportPage(
                expectedNotebook, count, expectedRevision, number), source, headings));
        });
        return job;
    }

    interface TransferCallback { void complete(IOException error); }

    void writeBackup(File destination, java.util.function.BooleanSupplier cancelled, TransferCallback callback) {
        worker.execute(() -> {
            final long version = revision;
            final int id = document.notebookId(), count = document.pageCount();
            final String name = document.notebookName();
            final File sourcePdf = document.sourcePdf();
            final List<NotebookOutline.Entry> headings;
            try { headings = document.outline(); }
            catch (IOException error) { main.post(() -> callback.complete(error)); return; }
            tasks.exports.execute(() -> {
                IOException failure = null;
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(destination)) {
                    NotebookBackup.write(output, name, count,
                        number -> readExportPage(id, count, version, number), sourcePdf, headings, cancelled);
                    output.getFD().sync();
                } catch (IOException | RuntimeException error) { failure = new IOException("Could not back up notebook", error); }
                final IOException result = failure;
                main.post(() -> callback.complete(result));
            });
        });
    }

    void importBackup(NotebookBackup.Restored restored, TransferCallback callback) {
        submit(() -> {
            document.loadCatalog(); document.importNotebook(restored); document.load();
            windowIds = Collections.emptyList(); navigationBook = 0;
        }, (page, error) -> callback.complete(error));
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
                final File sourcePdf = document.sourcePdf();
                tasks.render.execute(() -> {
                    if (request.cancelled) return;
                    Bitmap bitmap = null; IOException failure = null;
                    try { bitmap = NotebookPageThumbnail.render(preview, sourcePdf, 320, 384, () -> request.cancelled); }
                    catch (IOException | RuntimeException error) { failure = new IOException("Could not preview this page", error); }
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

    interface CompanionCallback { void complete(Bitmap bitmap, int pages, IOException error); }
    PreviewRequest previewCompanion(int id, int number, CompanionCallback callback) {
        PreviewRequest request = new PreviewRequest();
        worker.execute(() -> {
            if (request.cancelled) return;
            try {
                final int count = document.companionCount(id);
                final NotebookPage page = document.companionPage(id, Math.max(1, Math.min(count, number)));
                final File source = document.notebookPdf(id);
                tasks.render.execute(() -> {
                    if (request.cancelled) return;
                    Bitmap bitmap = null; IOException error = null;
                    try {
                        float scale = 1024f / Math.max(page.width, page.height);
                        bitmap = NotebookPageThumbnail.render(page, source,
                            Math.max(1, Math.round(page.width * scale)), Math.max(1, Math.round(page.height * scale)), () -> request.cancelled);
                    }
                    catch (IOException | RuntimeException failure) { error = new IOException("Cannot preview this note", failure); }
                    final Bitmap result = bitmap; final IOException failure = error;
                    main.post(() -> {
                        if (request.cancelled) { if (result != null) result.recycle(); return; }
                        callback.complete(result, count, failure);
                    });
                });
            } catch (IOException | RejectedExecutionException error) {
                main.post(() -> { if (!request.cancelled) callback.complete(null, 1, new IOException("Cannot open this note", error)); });
            }
        });
        return request;
    }

    PreviewRequest previewNotebook(int id, PreviewCallback callback) {
        PreviewRequest request = new PreviewRequest();
        worker.execute(() -> {
            if (request.cancelled) return;
            try {
                NotebookPage preview = document.previewNotebook(id);
                File source = document.notebookPdf(id);
                tasks.render.execute(() -> {
                    if (request.cancelled) return;
                    Bitmap bitmap = null; IOException failure = null;
                    try { bitmap = NotebookPageThumbnail.render(preview, source, 240, 288, () -> request.cancelled); }
                    catch (IOException | RuntimeException error) { failure = new IOException("Could not preview notebook", error); }
                    deliverPreview(request, callback, bitmap, failure);
                });
            } catch (IOException | RejectedExecutionException error) {
                deliverPreview(request, callback, null, new IOException("Could not load notebook preview", error));
            }
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

    /** Publish small identity maps once per page-order change; UI navigation never reads files. */
    private void publishNavigation() {
        pdfSource = document.sourcePdf();
        if (document.current() == null) {
            outline = Collections.emptyList(); pageIds = Collections.emptyMap(); pageNumbers = Collections.emptyMap();
            pdfPages = Collections.emptyMap(); pageSizes = Collections.emptyList(); navigationBook = 0; return;
        }
        try { outline = document.current() == null ? Collections.emptyList() : document.outline(); }
        catch (IOException error) { problem = error; }
        if (document.current() == null || navigationBook == document.notebookId()
                && navigationCount == document.pageCount()) return;
        Map<Integer, UUID> ids = new LinkedHashMap<>();
        Map<UUID, Integer> numbers = new LinkedHashMap<>();
        Map<Integer, Integer> sources = new LinkedHashMap<>();
        List<NotebookViewport.Size> sizes = new ArrayList<>();
        boolean complete = true;
        List<NotebookPageCatalog.Entry> metadata;
        try { metadata = document.navigationMetadata(); }
        catch (IOException failure) { metadata = Collections.emptyList(); complete = false; }
        for (int number = 1; number <= document.pageCount(); number++) {
            try {
                if (metadata.size() != document.pageCount()) throw new IOException("Navigation metadata unavailable");
                NotebookPageCatalog.Entry item = metadata.get(number - 1);
                if (item == null) throw new IOException("Page navigation metadata unavailable");
                ids.put(number, item.id); numbers.put(item.id, number);
                sizes.add(new NotebookViewport.Size(Math.max(1, item.width), Math.max(1, item.height)));
                if (item.pdfPage > 0 && !sources.containsKey(item.pdfPage)) sources.put(item.pdfPage, number);
            } catch (IOException ignored) {
                complete = false;
                NotebookPage fallback = document.current();
                sizes.add(new NotebookViewport.Size(Math.max(1, fallback.width), Math.max(1, fallback.height)));
            }
        }
        pageIds = Collections.unmodifiableMap(ids); pageNumbers = Collections.unmodifiableMap(numbers);
        pdfPages = Collections.unmodifiableMap(sources);
        pageSizes = Collections.unmodifiableList(sizes);
        navigationBook = complete ? document.notebookId() : 0; navigationCount = document.pageCount();
    }

    /** Only a truly new library receives default paper. Missing legacy files are never replaced. */
    private void prepareInitialPaper() throws IOException {
        if (!initialPaperChecked) {
            String[] contents = directory.list();
            initialPaperRequired = !directory.exists() || contents != null && contents.length == 0;
            initialPaperChecked = true;
        }
        if (!initialPaperRequired) return;
        NotebookStore bootstrap = new NotebookStore(directory);
        bootstrap.load(); bootstrap.initializePage(NotebookPaperSize.A5, NotebookPage.Template.BLANK); bootstrap.sync();
        initialPaperRequired = false;
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
            folders = document.folders(); availableNotebookId = document.availableNotebookId();
            position = document.scrollPosition();
            publishWindow();
            problem = failure == null ? document.error() : failure;
            publishNavigation();
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
