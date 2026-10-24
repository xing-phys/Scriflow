package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Worker-owned ordered pages. Page 1 stays at its original path, without migration or rewriting. */
public final class NotebookBook {
    private interface Operation { void run() throws IOException; }
    private final File directory;
    private final NotebookIndex index;
    private final NotebookScrollStore scroll;
    private final Map<Integer, NotebookDocument> documents = new HashMap<>();
    private NotebookIndex.State durableIndex;
    private int attemptedCount, attemptedSelection;
    private int selected = 1, count = 1;
    private NotebookDocument document;
    private IOException problem;
    private boolean loadAttempted, loaded;
    private Navigation navigation;
    private NotebookScrollStore.State position, durablePosition, attemptedPosition, targetPosition;

    private static final class Navigation {
        final int target, count;
        final boolean adding;
        final NotebookPage source;
        NotebookDocument document;
        boolean ownsNewDirectory;
        Navigation(int target, int count, boolean adding, NotebookPage source) {
            this.target = target; this.count = count; this.adding = adding; this.source = source;
        }
    }

    public NotebookBook(File directory) {
        if (directory == null) throw new IllegalArgumentException("Missing notebook directory");
        this.directory = directory;
        index = new NotebookIndex(directory);
        scroll = new NotebookScrollStore(directory);
    }

    public NotebookPage current() { return document == null ? null : document.current(); }
    public IOException error() { return problem; }
    public boolean canUndo() { return document != null && document.canUndo(); }
    public boolean canRedo() { return document != null && document.canRedo(); }
    public int pageNumber() { return selected; }
    public int pageCount() { return count; }
    public NotebookScrollStore.State scrollPosition() { return position; }

    /** Fetch a page for a settled viewport without changing the selected page or page index. */
    public NotebookPage page(int number) throws IOException {
        final NotebookPage[] result = {null};
        execute(() -> result[0] = targetDocument(number, true).current());
        return result[0];
    }

    /** No I/O: use after completed edits to publish already loaded viewport pages. */
    public NotebookPage cachedPage(int number) throws IOException {
        requireLoaded(); requirePageNumber(number);
        NotebookDocument target = documents.get(number);
        if (target == null || target.current() == null) throw new IOException("Page has not been loaded");
        return target.current();
    }

    public void appendToPage(int number, NotebookPage.Stroke stroke) throws IOException {
        execute(() -> targetDocument(number, false).append(stroke));
    }
    public void appendStrokesToPage(int number, List<NotebookPage.Stroke> strokes) throws IOException {
        execute(() -> targetDocument(number, false).appendAll(strokes));
    }
    public boolean eraseOnPage(int number, List<NotebookPage.Point> path, float radius) throws IOException {
        return eraseOnPagePaths(number, java.util.Collections.singletonList(path), radius);
    }
    public boolean eraseOnPagePaths(int number, List<List<NotebookPage.Point>> paths, float radius) throws IOException {
        final boolean[] result = {false};
        execute(() -> result[0] = targetDocument(number, false).erasePaths(paths, radius));
        return result[0];
    }
    public boolean undoOnPage(int number) throws IOException {
        final boolean[] result = {false}; execute(() -> result[0] = targetDocument(number, false).undo()); return result[0];
    }
    public boolean redoOnPage(int number) throws IOException {
        final boolean[] result = {false}; execute(() -> result[0] = targetDocument(number, false).redo()); return result[0];
    }
    public void setPageTemplate(int number, NotebookPage.Template template) throws IOException {
        execute(() -> targetDocument(number, false).setTemplate(template));
    }
    public boolean canUndoOnPage(int number) throws IOException {
        final boolean[] result = {false}; execute(() -> result[0] = targetDocument(number, false).canUndo()); return result[0];
    }
    public boolean canRedoOnPage(int number) throws IOException {
        final boolean[] result = {false}; execute(() -> result[0] = targetDocument(number, false).canRedo()); return result[0];
    }

    /** Save once on a completed finger gesture; this never publishes the page-selection index. */
    public void saveScrollPosition(int number, double fraction) throws IOException {
        execute(() -> {
            requireLoaded(); requireWritable(); requirePageNumber(number);
            if (Double.isNaN(fraction) || Double.isInfinite(fraction) || fraction < 0 || fraction >= 1) {
                throw new IOException("Invalid notebook scroll position");
            }
            retryDocuments(true);
            targetPosition = new NotebookScrollStore.State(number, fraction);
            retryScroll();
        });
    }

    /** A load barrier never replaces an open page or its process-local editing history. */
    public void load() throws IOException {
        execute(() -> {
            if (loadAttempted) { requireWritable(); return; }
            loadAttempted = true;
            loadInitial();
        });
    }

    public void initializeSize(int width, int height) throws IOException {
        edit(() -> document.initializeSize(width, height));
    }

    public void append(NotebookPage.Stroke stroke) throws IOException {
        edit(() -> document.append(stroke));
    }

    public void setTemplate(NotebookPage.Template template) throws IOException {
        edit(() -> document.setTemplate(template));
    }

    public boolean erase(List<NotebookPage.Point> path, float radius) throws IOException {
        final boolean[] changed = {false};
        edit(() -> changed[0] = document.erase(path, radius));
        return changed[0];
    }

    public boolean undo() throws IOException {
        final boolean[] changed = {false};
        edit(() -> changed[0] = document.undo());
        return changed[0];
    }

    public boolean redo() throws IOException {
        final boolean[] changed = {false};
        edit(() -> changed[0] = document.redo());
        return changed[0];
    }

    /** Outgoing completed ink is synced before a validated target is selected durably. */
    public void selectPage(int pageNumber) throws IOException {
        execute(() -> {
            requireWritable(); requireLoaded();
            if (pageNumber < 1 || pageNumber > count) throw new IOException("Page is out of range");
            if (pageNumber == selected) return;
            navigation = new Navigation(pageNumber, count, false, current());
            finishNavigation();
        });
    }

    /** Appends a blank page with the current paper/coordinate size; existing pages never move. */
    public void addPage() throws IOException {
        execute(() -> {
            requireWritable(); requireLoaded();
            if (count >= NotebookIndex.MAX_PAGES) throw new IOException("Notebook has reached its page limit");
            if (!current().isInitialized()) throw new IOException("The writing surface is not ready yet");
            navigation = new Navigation(count + 1, count + 1, true, current());
            finishNavigation();
        });
    }

    /** Resume the exact pending save/navigation, never overwrite an unexpected index or page. */
    public void retry() throws IOException {
        execute(() -> {
            if (!loaded) {
                loadAttempted = true;
                loadInitial();
                return;
            }
            NotebookIndex.State disk = index.load();
            if (!disk.equals(durableIndex) && !matchesAttempt(disk)) {
                throw new IOException("Saved page order differs from the open notebook; files were preserved.");
            }
            index.sync();
            durableIndex = disk;
            attemptedCount = attemptedSelection = 0;
            retryDocuments();
            retryScroll();
            if (navigation != null) finishNavigation();
            problem = null;
        });
    }

    private void loadInitial() throws IOException {
        NotebookIndex.State state = index.load();
        durableIndex = state;
        selected = state.selectedPage;
        count = state.pageCount;
        // The initial selected page is known even when its data cannot be loaded; retry loads it
        // explicitly. Once loaded, navigation instead keeps the previously visible page on failure.
        // No edit can have been accepted until initial load completes, so discard a failed initial
        // candidate and validate repaired files afresh (including an invalid empty snapshot).
        documents.clear();
        document = documentFor(selected);
        if (count > 1) requirePageFiles(selected);
        if (document.error() == null) document.load(); else document.retry();
        if (count > 1 && !current().isInitialized()) throw new IOException("Saved page has no dimensions");
        position = durablePosition = scroll.load(selected);
        if (position.page > count) throw new IOException("Saved scroll position refers to a missing page");
        attemptedPosition = targetPosition = null;
        loaded = true;
        problem = null;
    }

    private void finishNavigation() throws IOException {
        Navigation next = navigation;
        retryDocuments();
        retryScroll();
        if (next.adding) prepareNewPage(next); else prepareExistingPage(next);
        attemptedCount = next.count;
        attemptedSelection = next.target;
        NotebookIndex.State saved = index.save(next.count, next.target);
        index.sync();
        durableIndex = saved;
        attemptedCount = attemptedSelection = 0;
        document = next.document;
        selected = next.target;
        count = next.count;
        navigation = null;
    }

    private void prepareExistingPage(Navigation next) throws IOException {
        requirePageFiles(next.target);
        if (next.document == null) next.document = documentFor(next.target);
        if (next.document.error() == null) next.document.load(); else next.document.retry();
        // Cached pages keep their undo history, but must still match their acknowledged disk
        // state before navigation makes them writable again.
        next.document.retry();
        if (!next.document.current().isInitialized()) throw new IOException("Saved page has no dimensions");
    }

    private void prepareNewPage(Navigation next) throws IOException {
        if (!next.ownsNewDirectory) {
            File target = pageDirectory(next.target);
            String[] contents = target.list();
            if (target.exists() && (!target.isDirectory() || contents == null)) {
                throw new IOException("Cannot inspect an unindexed page; its files were preserved.");
            }
            if (contents != null && contents.length != 0) {
                // A crash may leave the blank new page durable just before its index publication.
                // Adopt only the exact empty page we would have created. Corrupt, nonempty, or
                // differently sized/patterned orphans are never overwritten or adopted.
                requirePageFiles(next.target);
                NotebookPage candidate = new NotebookStore(target).load();
                if (candidate.width != next.source.width || candidate.height != next.source.height
                        || candidate.template != next.source.template || !candidate.strokes.isEmpty()) {
                    throw new IOException("An unexpected unindexed page exists; its files were preserved.");
                }
            }
            next.ownsNewDirectory = true;
        }
        if (next.document == null) {
            // Publish dimensions and paper together before loading a Document. There is never a
            // sized BLANK intermediate when the user requested LINED paper, even after a crash.
            NotebookStore bootstrap = new NotebookStore(pageDirectory(next.target));
            bootstrap.load();
            bootstrap.initializePage(next.source.width, next.source.height, next.source.template);
            bootstrap.sync();
            next.document = documentFor(next.target);
        }
        if (next.document.error() == null) next.document.load(); else next.document.retry();
        next.document.retry();
    }

    private NotebookDocument documentFor(int number) {
        NotebookDocument result = documents.get(number);
        if (result == null) {
            result = new NotebookDocument(pageDirectory(number));
            documents.put(number, result);
        }
        return result;
    }

    private NotebookDocument targetDocument(int number, boolean validate) throws IOException {
        requireLoaded(); requirePageNumber(number);
        NotebookDocument target = documents.get(number);
        if (target != null && target.current() != null) {
            // Already accepted ink must remain visible even when another part of the same
            // split stroke failed to save. Do not reload or discard it during repaint.
            if (validate && problem == null && target.error() == null) {
                if (count > 1) requirePageFiles(number);
                target.validateDisk();
            }
            return target;
        }
        if (count > 1) requirePageFiles(number);
        if (target == null) target = documentFor(number);
        target.load();
        if (count > 1 && !target.current().isInitialized()) throw new IOException("Saved page has no dimensions");
        return target;
    }

    private void requirePageNumber(int number) throws IOException {
        if (number < 1 || number > count) throw new IOException("Page is out of range");
    }

    private void retryDocuments() throws IOException {
        retryDocuments(false);
    }

    private void retryDocuments(boolean dirtyOnly) throws IOException {
        // Deterministic order, including dirty pages that are no longer selected.
        for (Map.Entry<Integer, NotebookDocument> entry : new TreeMap<>(documents).entrySet()) {
            if (dirtyOnly && entry.getValue().error() == null) continue;
            if (count > 1) requirePageFiles(entry.getKey());
            entry.getValue().retry();
        }
    }

    private void retryScroll() throws IOException {
        NotebookScrollStore.State disk = scroll.load(durablePosition.page);
        if (disk.page > count || !disk.equals(durablePosition) && !disk.equals(attemptedPosition)) {
            throw new IOException("Saved scroll position differs from the open notebook; files were preserved.");
        }
        scroll.sync();
        durablePosition = disk;
        if (targetPosition != null) {
            attemptedPosition = targetPosition;
            NotebookScrollStore.State saved = scroll.save(targetPosition.page, targetPosition.fraction);
            scroll.sync();
            position = durablePosition = saved;
            targetPosition = null;
        } else position = disk;
        attemptedPosition = null;
    }

    private File pageDirectory(int number) {
        return number == 1 ? directory : new File(new File(directory, "pages"), Integer.toString(number));
    }

    private void requirePageFiles(int number) throws IOException {
        File target = pageDirectory(number);
        if (!new File(target, "page.nbk").exists() && !new File(target, "page.nbk.bak").exists()
                && !new File(target, "page.nbk.tmp").exists()) {
            throw new IOException("Saved page " + number + " is missing; no blank replacement was created.");
        }
    }

    private boolean matchesAttempt(NotebookIndex.State state) {
        return attemptedCount != 0 && state.pageCount == attemptedCount
            && state.selectedPage == attemptedSelection;
    }

    private void edit(Operation operation) throws IOException {
        execute(() -> {
            requireLoaded();
            // Completed pen-up events already queued before a page save failure still belong to
            // this visible page. Document retains them in memory while refusing disk writes.
            if (problem != null && document.error() == null) throw problem;
            operation.run();
        });
    }

    private void requireLoaded() throws IOException {
        if (!loaded || document == null) throw new IOException("The notebook has not loaded.");
    }

    private void requireWritable() throws IOException { if (problem != null) throw problem; }

    private void execute(Operation operation) throws IOException {
        try { operation.run(); }
        catch (IOException error) { problem = error; throw error; }
        catch (RuntimeException error) {
            problem = new IOException("Could not save the notebook", error);
            throw problem;
        }
    }
}
