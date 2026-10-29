package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * Worker-owned document and editing history. All calls must use one serialized worker.
 * Completed edits remain in memory after storage errors; only retry may resume disk writes.
 */
public final class NotebookDocument {
    private interface Operation { void run() throws IOException; }
    private final NotebookStore store;
    private NotebookEdits editor;
    private NotebookPage durablePage, attemptedPage;
    private NotebookPage transferTarget;
    private IOException problem;
    private boolean loadAttempted;

    public NotebookDocument(File directory) { store = new NotebookStore(directory); }

    public NotebookPage current() { return editor == null ? null : editor.current(); }
    public IOException error() { return problem; }
    public boolean canUndo() { return editor != null && editor.canUndo(); }
    public boolean canRedo() { return editor != null && editor.canRedo(); }
    Object undoLink() { return editor == null ? null : editor.undoLink(); }
    Object redoLink() { return editor == null ? null : editor.redoLink(); }
    NotebookPage historyTarget(boolean forward) { return editor == null ? null : editor.historyTarget(forward); }

    /** Validate both participants before publishing the durable two-page intent. */
    void validateLinked(NotebookPage before, NotebookPage after, Object link, int historyStep) throws IOException {
        requireEditor(); requireWritable();
        if (!samePage(current(), before) || !samePage(before.withStrokes(after.strokes).withFigures(after.figures), after))
            throw new IOException("The selected page changed; move the ink again");
        if (historyStep != 0 && ((historyStep < 0 ? undoLink() : redoLink()) != link
                || !samePage(historyTarget(historyStep > 0), after)))
            throw new IOException("Undo the other page's later edits before changing this move");
    }

    /** No I/O: the book has already journaled and validated both targets. */
    void stageLinked(NotebookPage after, Object link, int historyStep) {
        if (historyStep < 0) editor.undo();
        else if (historyStep > 0) editor.redo();
        else editor.replaceLinked(after, link);
        transferTarget = after;
        // Queued pen-UPs may still enter memory, but cannot write over a pending transfer.
        problem = new IOException("Finish saving the cross-page move before writing to this page");
    }

    /** Acknowledge the journal result without discarding history or later queued pen-UPs. */
    void completeLinked() throws IOException {
        if (transferTarget == null) return;
        NotebookPage disk = store.load();
        if (!samePage(disk, transferTarget)) throw new IOException("Saved cross-page move differs from its target; files were preserved");
        store.sync(); acknowledged(disk); transferTarget = null; problem = null;
    }

    /** Validates a cached page against disk without syncing, publishing or losing its history. */
    public void validateDisk() throws IOException {
        execute(() -> {
            requireEditor(); requireWritable();
            if (!samePage(store.load(), durablePage)) {
                throw new IOException("The saved page differs from the open page; files were preserved.");
            }
        });
    }

    /** Recreated Activities wait behind outstanding work; they never reload over unsaved edits. */
    public void load() throws IOException {
        execute(() -> {
            if (loadAttempted) { requireWritable(); return; }
            loadAttempted = true;
            loadInitial();
        });
    }

    public void initializeSize(int width, int height) throws IOException {
        execute(() -> {
            requireEditor();
            NotebookPage before = current();
            editor.setPageSize(width, height);
            if (before.width == current().width && before.height == current().height) return;
            requireWritable();
            attemptedPage = current();
            acknowledged(store.initializeSize(width, height));
        });
    }

    public void append(NotebookPage.Stroke stroke) throws IOException {
        appendAll(java.util.Collections.singletonList(stroke));
    }

    /** Retain the entire gesture before I/O; journal each piece against its exact durable prefix. */
    public void appendAll(List<NotebookPage.Stroke> strokes) throws IOException {
        execute(() -> {
            requireEditor();
            List<NotebookPage.Stroke> group = strokes == null ? null : new java.util.ArrayList<>(strokes);
            if (!editor.appendAll(group)) return;
            requireWritable();
            for (NotebookPage.Stroke stroke : group) {
                attemptedPage = durablePage.withStroke(stroke);
                acknowledged(store.appendStroke(stroke));
            }
        });
    }

    public void setTemplate(NotebookPage.Template template) throws IOException {
        execute(() -> {
            requireEditor();
            NotebookPage.Template before = current().template;
            editor.setTemplate(template);
            if (before == template) return;
            requireWritable();
            attemptedPage = current();
            acknowledged(store.setTemplate(template));
        });
    }

    public boolean erase(List<NotebookPage.Point> path, float radius) throws IOException {
        return erasePaths(java.util.Collections.singletonList(path), radius);
    }

    public boolean erasePaths(List<List<NotebookPage.Point>> paths, float radius) throws IOException {
        final boolean[] changed = {false};
        execute(() -> {
            requireEditor();
            changed[0] = editor.erasePaths(paths, radius);
            if (changed[0]) publishEdit();
        });
        return changed[0];
    }

    public void setFigures(List<NotebookFigure> figures) throws IOException {
        execute(() -> { requireEditor(); if (editor.setFigures(figures)) publishEdit(); });
    }

    public boolean undo() throws IOException {
        final boolean[] changed = {false};
        execute(() -> {
            requireEditor();
            changed[0] = editor.undo();
            if (changed[0]) publishEdit();
        });
        return changed[0];
    }

    public NotebookLasso.Selection editSelection(NotebookLasso.Selection selection,
            NotebookLasso.Action action, float dx, float dy) throws IOException {
        final NotebookLasso.Selection[] result = {null};
        execute(() -> {
            requireEditor();
            NotebookPage before = current();
            result[0] = editor.editSelection(selection, action, dx, dy);
            if (current() != before) publishEdit();
        });
        return result[0];
    }

    public NotebookLasso.Selection paste(NotebookLasso.Clipboard clipboard, float x, float y) throws IOException {
        final NotebookLasso.Selection[] result = {null};
        execute(() -> { requireEditor(); result[0] = editor.paste(clipboard, x, y); publishEdit(); });
        return result[0];
    }

    public boolean redo() throws IOException {
        final boolean[] changed = {false};
        execute(() -> {
            requireEditor();
            changed[0] = editor.redo();
            if (changed[0]) publishEdit();
        });
        return changed[0];
    }

    /**
     * Only two on-disk states are legitimate after a failed write: its acknowledged predecessor
     * and its exact attempted target. Matching a stroke prefix is unsafe after erasure or undo.
     */
    public void retry() throws IOException {
        execute(() -> {
            if (transferTarget != null) throw new IOException("The book must finish its cross-page move before retrying this page");
            if (editor == null) {
                loadAttempted = true;
                loadInitial();
                return;
            }
            NotebookPage intended = current();
            NotebookPage disk = store.load();
            if (!samePage(disk, durablePage) && !samePage(disk, attemptedPage)) {
                throw new IOException("The saved page differs from the open page; files were preserved.");
            }
            // Confirm an ambiguously completed write before attempting another state transition.
            attemptedPage = disk;
            store.sync();
            acknowledged(disk);
            if (!disk.isInitialized() && intended.isInitialized()) {
                attemptedPage = disk.withSize(intended.width, intended.height);
                acknowledged(store.initializeSize(intended.width, intended.height));
                disk = durablePage;
            }
            if (disk.width != intended.width || disk.height != intended.height) {
                throw new IOException("The saved page has different dimensions; files were preserved.");
            }
            if (!sameStrokes(disk.strokes, intended.strokes)) {
                attemptedPage = disk.withStrokes(intended.strokes);
                acknowledged(store.replaceStrokes(intended.strokes));
                disk = durablePage;
            }
            if (!disk.figures.equals(intended.figures)) {
                attemptedPage = disk.withFigures(intended.figures);
                acknowledged(store.replaceContent(attemptedPage));
                disk = durablePage;
            }
            if (disk.template != intended.template) {
                attemptedPage = disk.withTemplate(intended.template);
                acknowledged(store.setTemplate(intended.template));
                disk = durablePage;
            }
            attemptedPage = disk;
            store.sync();
            acknowledged(disk);
            problem = null;
        });
    }

    private void loadInitial() throws IOException {
        NotebookPage loaded = store.load();
        editor = new NotebookEdits(loaded);
        acknowledged(loaded);
        problem = null;
    }

    private void publishEdit() throws IOException {
        requireWritable();
        attemptedPage = current();
        acknowledged(store.replaceContent(current()));
    }

    private void acknowledged(NotebookPage page) {
        durablePage = page;
        attemptedPage = null;
    }

    private void requireEditor() throws IOException {
        if (editor == null) throw new IOException("The notebook has not loaded.");
    }

    private void requireWritable() throws IOException {
        if (problem != null) throw problem;
    }

    private void execute(Operation operation) throws IOException {
        try { operation.run(); }
        catch (IOException error) { problem = error; throw error; }
        catch (RuntimeException error) {
            problem = new IOException("Could not save the page", error);
            throw problem;
        }
    }

    static boolean samePage(NotebookPage a, NotebookPage b) {
        return a != null && b != null && a.width == b.width && a.height == b.height && a.pdfPage == b.pdfPage
            && java.util.Objects.equals(a.paperSize, b.paperSize)
            && a.figures.equals(b.figures) && a.id.equals(b.id) && a.template == b.template && sameStrokes(a.strokes, b.strokes);
    }

    private static boolean sameStrokes(List<NotebookPage.Stroke> a, List<NotebookPage.Stroke> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!sameStroke(a.get(i), b.get(i))) return false;
        return true;
    }

    private static boolean sameStroke(NotebookPage.Stroke a, NotebookPage.Stroke b) {
        if (!a.id.equals(b.id) || a.penType != b.penType || a.color != b.color || !same(a.width, b.width)
                || !same(a.captureScale, b.captureScale) || !same(a.captureWidth, b.captureWidth)
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
