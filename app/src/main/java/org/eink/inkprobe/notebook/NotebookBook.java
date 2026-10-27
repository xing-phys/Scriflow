package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Worker-owned pages. Display order maps to stable storage; ink files never move. */
public final class NotebookBook {
    public enum PageAction { MOVE, DUPLICATE, INSERT_BEFORE, INSERT_AFTER, DELETE }
    private static final int PAGE_HISTORY_LIMIT = 50;
    private interface Operation { void run() throws IOException; }
    private final File directory;
    private final NotebookIndex index;
    private List<NotebookOutline.Entry> outline = java.util.Collections.emptyList();
    private NotebookScrollStore scroll;
    private final Map<Integer, NotebookDocument> documents = new HashMap<>();
    private Map<Integer, NotebookPageCatalog.Entry> metadataCache;
    private NotebookIndex.State durableIndex;
    private int attemptedCount, attemptedSelection;
    private int selected = 1, count = 1;
    private NotebookDocument document;
    private IOException problem;
    private boolean loadAttempted, loaded;
    private Navigation navigation;
    private PageChange pageChange;
    private final Deque<NotebookIndex.State> pageUndo = new ArrayDeque<>();
    private final Deque<NotebookIndex.State> pageRedo = new ArrayDeque<>();
    private NotebookScrollStore.State position, durablePosition, attemptedPosition, targetPosition;

    private static final class Navigation {
        final int target, count;
        final boolean adding;
        final NotebookPage source;
        NotebookDocument document;
        boolean ownsNewDirectory;
        final NotebookIndex.State before;
        Navigation(int target, int count, boolean adding, NotebookPage source, NotebookIndex.State before) {
            this.target = target; this.count = count; this.adding = adding; this.source = source;
            this.before = before;
        }
    }

    private static final class PageChange {
        final NotebookIndex.State before, after;
        final NotebookPage created;
        final int newSlot;
        final int historyStep;
        boolean ownsDirectory;
        PageChange(NotebookIndex.State before, NotebookIndex.State after,
                   NotebookPage created, int newSlot, int historyStep) {
            this.before = before; this.after = after; this.created = created;
            this.newSlot = newSlot; this.historyStep = historyStep;
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
    public boolean canUndoPageChange() { return !pageUndo.isEmpty(); }
    public boolean canRedoPageChange() { return !pageRedo.isEmpty(); }
    public File sourcePdf() { File source = new File(directory, "source.pdf"); return source.isFile() ? source : null; }
    public List<NotebookOutline.Entry> outline() { return outline; }

    /** One compact cache read on cold open; no scan of every page's handwriting. */
    public List<NotebookPageCatalog.Entry> navigationMetadata() throws IOException {
        requireLoaded();
        if (metadataCache == null) metadataCache = new HashMap<>(NotebookPageCatalog.load(directory));
        List<NotebookPageCatalog.Entry> result = new ArrayList<>(count);
        Map<Integer, NotebookPageCatalog.Entry> active = new java.util.LinkedHashMap<>();
        boolean changed = false;
        for (int number = 1; number <= count; number++) {
            int slot = storageSlot(number);
            try {
                NotebookPageCatalog.Entry entry = metadataCache.get(slot);
                NotebookDocument cached = documents.get(slot);
                if (cached != null && cached.current() != null) {
                    NotebookPageCatalog.Entry fresh = NotebookPageCatalog.Entry.of(cached.current());
                    if (entry == null || !entry.id.equals(fresh.id) || entry.width != fresh.width
                            || entry.height != fresh.height || entry.pdfPage != fresh.pdfPage) {
                        entry = fresh; changed = true;
                    }
                }
                if (entry == null) {
                    requirePageFiles(number);
                    entry = new NotebookStore(pageDirectory(number)).metadata(); changed = true;
                }
                if ((count > 1 || durableIndex.managed()) && entry.width == 0) throw new IOException("Saved page has no dimensions");
                metadataCache.put(slot, entry); active.put(slot, entry); result.add(entry);
            } catch (IOException failure) {
                // A damaged offscreen page must not prevent opening or editing healthy pages.
                result.add(null);
            }
        }
        if (changed) NotebookPageCatalog.save(directory, active);
        return java.util.Collections.unmodifiableList(result);
    }

    /** UUID destinations remain valid when pages are reordered and can reappear after undo. */
    public int pageNumber(java.util.UUID id) throws IOException {
        requireLoaded();
        if (id == null) return 0;
        for (int number = 1; number <= count; number++) if (previewPage(number).id.equals(id)) return number;
        return 0;
    }

    public void setOutline(List<NotebookOutline.Entry> entries) throws IOException {
        requireLoaded(); requireWritable();
        // An outline write has its own error boundary: a failed heading edit must not stop ink.
        outline = NotebookOutline.save(directory, entries);
    }

    /** Fetch a page for a settled viewport without changing the selected page or page index. */
    public NotebookPage page(int number) throws IOException {
        final NotebookPage[] result = {null};
        execute(() -> result[0] = targetDocument(number, true).current());
        return result[0];
    }

    /** No I/O: use after completed edits to publish already loaded viewport pages. */
    public NotebookPage cachedPage(int number) throws IOException {
        requireLoaded(); requirePageNumber(number);
        NotebookDocument target = documents.get(storageSlot(number));
        if (target == null || target.current() == null) throw new IOException("Page has not been loaded");
        return target.current();
    }

    /**
     * Read-only overview source. Accepted in-memory ink takes precedence over disk, including
     * completed gestures whose save failed. Other pages are read without opening a Document,
     * changing selection/history, or retaining all notebook pages in the editing cache.
     * A thumbnail failure is local to its caller and must not pause the writable notebook.
     */
    public NotebookPage previewPage(int number) throws IOException {
        requireLoaded(); requirePageNumber(number);
        NotebookDocument cached = documents.get(storageSlot(number));
        if (cached != null && cached.current() != null) {
            requirePdfSource(cached.current()); return cached.current();
        }
        requirePageFiles(number);
        NotebookPage result = new NotebookStore(pageDirectory(number)).load();
        if ((count > 1 || durableIndex.managed()) && !result.isInitialized()) throw new IOException("Saved page has no dimensions");
        requirePdfSource(result);
        return result;
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
    public NotebookLasso.Selection editSelectionOnPage(int number, NotebookLasso.Selection selection,
            NotebookLasso.Action action, float dx, float dy) throws IOException {
        final NotebookLasso.Selection[] result = {null};
        execute(() -> result[0] = targetDocument(number, false).editSelection(selection, action, dx, dy));
        return result[0];
    }
    public NotebookLasso.Selection pasteOnPage(int number, NotebookLasso.Clipboard clipboard,
            float x, float y) throws IOException {
        final NotebookLasso.Selection[] result = {null};
        execute(() -> result[0] = targetDocument(number, false).paste(clipboard, x, y));
        return result[0];
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
            navigation = new Navigation(pageNumber, count, false, current(), durableIndex);
            finishNavigation();
        });
    }

    /** Insert after the selected page, preserving paper geometry and stable page identities. */
    public void addPage() throws IOException {
        execute(() -> {
            requireWritable(); requireLoaded();
            if (count >= NotebookIndex.MAX_PAGES) throw new IOException("Notebook has reached its page limit");
            if (!current().isInitialized()) throw new IOException("The writing surface is not ready yet");
            if (selected < count || durableIndex.managed() || new File(slotDirectory(count + 1), "page.created").isFile()) {
                // A first management edit can leave an unindexed staged slot after process
                // death. Skip it without adopting its contents or blocking the legacy Add tool.
                managePages(PageAction.INSERT_AFTER, selected, selected); return;
            }
            navigation = new Navigation(count + 1, count + 1, true, current(), durableIndex);
            finishNavigation();
        });
    }

    /** A management command publishes just a new order and, for copies/inserts, one new slot. */
    public void managePages(PageAction action, int source, int destination) throws IOException {
        execute(() -> {
            requireLoaded(); requireWritable(); requirePageNumber(source);
            if (action == null) throw new IOException("Missing page action");
            if (action == PageAction.MOVE) {
                requirePageNumber(destination);
                if (source == destination) return;
            }
            if (action == PageAction.DELETE && count == 1) throw new IOException("Keep at least one page in the notebook");
            boolean creates = action == PageAction.DUPLICATE || action == PageAction.INSERT_BEFORE || action == PageAction.INSERT_AFTER;
            if (creates && count == NotebookIndex.MAX_PAGES) throw new IOException("Notebook has reached its page limit");
            retryDocuments(); retryScroll();
            NotebookPage original = targetDocument(source, true).current();
            if (!original.isInitialized()) throw new IOException("The writing surface is not ready yet");
            List<Integer> order = new ArrayList<>(durableIndex.slots);
            int target, slot = 0, nextSlot = durableIndex.nextSlot;
            NotebookPage created = null;
            if (action == PageAction.MOVE) {
                int moved = order.remove(source - 1); order.add(destination - 1, moved); target = destination;
            } else if (action == PageAction.DELETE) {
                order.remove(source - 1); target = Math.min(source, order.size());
            } else {
                slot = availableSlot(nextSlot); nextSlot = slot + 1;
                List<NotebookPage.Stroke> strokes = new ArrayList<>();
                if (action == PageAction.DUPLICATE) for (NotebookPage.Stroke stroke : original.strokes)
                    strokes.add(new NotebookPage.Stroke(stroke.penType, stroke.width, stroke.color, stroke.points,
                        stroke.captureScale, stroke.captureWidth));
                created = NotebookPage.empty().withSize(original.width, original.height)
                    .withPaperSize(original.paperSize).withTemplate(original.template).withStrokes(strokes);
                if (action == PageAction.DUPLICATE) created = created.withPdfPage(original.pdfPage);
                int after = action == PageAction.INSERT_AFTER && destination > 0 ? destination : source;
                target = action == PageAction.INSERT_BEFORE ? source : after + 1;
                if (target < 1 || target > count + 1) throw new IOException("Page insertion position is out of range");
                order.add(target - 1, slot);
            }
            pageChange = new PageChange(durableIndex, durableIndex.reordered(order, target, nextSlot), created, slot, 0);
            finishPageChange();
        });
    }

    /** Page-order undo is separate from per-page ink undo, and lasts for this process. */
    public void undoPageChange() throws IOException {
        changePageHistory(false);
    }

    public void redoPageChange() throws IOException {
        changePageHistory(true);
    }

    private void changePageHistory(boolean redo) throws IOException {
        execute(() -> {
            requireLoaded(); requireWritable();
            Deque<NotebookIndex.State> history = redo ? pageRedo : pageUndo;
            if (history.isEmpty()) return;
            NotebookIndex.State previous = history.peekLast();
            pageChange = new PageChange(durableIndex,
                durableIndex.reordered(previous.slots, previous.selectedPage, durableIndex.nextSlot), null, 0, redo ? 1 : -1);
            finishPageChange();
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
            if (pageChange != null) {
                if (!disk.equals(pageChange.before) && !disk.equals(pageChange.after))
                    throw new IOException("Saved page order differs from the pending change; files were preserved.");
                finishPageChange(); problem = null; return;
            }
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
        scroll = new NotebookScrollStore(directory, state.generation);
        // The initial selected page is known even when its data cannot be loaded; retry loads it
        // explicitly. Once loaded, navigation instead keeps the previously visible page on failure.
        // No edit can have been accepted until initial load completes, so discard a failed initial
        // candidate and validate repaired files afresh (including an invalid empty snapshot).
        documents.clear();
        metadataCache = null;
        document = documentFor(selected);
        if (count > 1 || state.managed()) requirePageFiles(selected);
        if (document.error() == null) document.load(); else document.retry();
        if ((count > 1 || state.managed()) && !current().isInitialized()) throw new IOException("Saved page has no dimensions");
        position = durablePosition = scroll.load(selected);
        if (position.page > count) throw new IOException("Saved scroll position refers to a missing page");
        attemptedPosition = targetPosition = null;
        outline = NotebookOutline.load(directory);
        requirePdfSource(current());
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
        if (next.adding) rememberPageChange(next.before);
    }

    private void finishPageChange() throws IOException {
        PageChange change = pageChange;
        NotebookIndex.State disk = index.load();
        if (!disk.equals(change.before) && !disk.equals(change.after))
            throw new IOException("Saved page order differs from the pending change; files were preserved.");
        retryDocuments(); retryScroll();
        if (change.created != null) prepareManagedPage(change);
        // Every referenced slot must exist before committing the new order, including undo.
        for (int slot : change.after.slots) requireSlotFiles(slot);
        int targetSlot = change.after.slot(change.after.selectedPage);
        NotebookDocument target = documentForSlot(targetSlot);
        if (target.error() == null) target.load(); else target.retry();
        target.validateDisk();
        if (!target.current().isInitialized()) throw new IOException("Saved page has no dimensions");
        requirePdfSource(target.current());
        NotebookIndex.State saved = index.saveOrder(change.after); index.sync();
        NotebookScrollStore nextScroll = new NotebookScrollStore(directory, saved.generation);
        NotebookScrollStore.State nextPosition = nextScroll.load(saved.selectedPage);
        durableIndex = saved; selected = saved.selectedPage; count = saved.pageCount; document = target;
        scroll = nextScroll; position = durablePosition = nextPosition;
        attemptedPosition = targetPosition = null;
        if (change.historyStep < 0) { pageUndo.removeLast(); pageRedo.addLast(change.before); }
        else if (change.historyStep > 0) { pageRedo.removeLast(); pushPageUndo(change.before); }
        else rememberPageChange(change.before);
        pageChange = null;
    }

    private void prepareManagedPage(PageChange change) throws IOException {
        File target = slotDirectory(change.newSlot);
        if (!change.ownsDirectory) {
            File parent = target.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create pages directory");
            if (!target.mkdir()) throw new IOException("An unexpected page directory exists; files were preserved.");
            change.ownsDirectory = true;
        }
        // This is only a hint to skip an orphan, never permission to adopt or overwrite it.
        try (FileOutputStream marker = new FileOutputStream(new File(target, "page.created"))) {
            marker.write(change.created.id.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            marker.getFD().sync();
        }
        File published = new File(target, "page.nbk");
        if (published.exists()) {
            if (!NotebookDocument.samePage(new NotebookStore(target).load(), change.created))
                throw new IOException("The prepared page changed; files were preserved.");
            return;
        }
        File temporary = new File(target, "page.nbk.tmp");
        try (FileOutputStream output = new FileOutputStream(temporary)) {
            NotebookStore.writeSnapshot(output, change.created); output.getFD().sync();
        }
        if (!temporary.renameTo(published)) throw new IOException("Cannot publish the new page");
    }

    private int availableSlot(int candidate) throws IOException {
        while (candidate < Integer.MAX_VALUE && slotDirectory(candidate).exists()) candidate++;
        if (candidate == Integer.MAX_VALUE) throw new IOException("Notebook page storage limit reached");
        return candidate;
    }

    private void rememberPageChange(NotebookIndex.State previous) {
        pageRedo.clear(); pushPageUndo(previous);
    }

    private void pushPageUndo(NotebookIndex.State previous) {
        pageUndo.addLast(previous);
        if (pageUndo.size() > PAGE_HISTORY_LIMIT) pageUndo.removeFirst();
    }

    private void prepareExistingPage(Navigation next) throws IOException {
        requirePageFiles(next.target);
        if (next.document == null) next.document = documentFor(next.target);
        if (next.document.error() == null) next.document.load(); else next.document.retry();
        // Cached pages keep their undo history, but must still match their acknowledged disk
        // state before navigation makes them writable again.
        next.document.retry();
        if (!next.document.current().isInitialized()) throw new IOException("Saved page has no dimensions");
        requirePdfSource(next.document.current());
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
                        || !java.util.Objects.equals(candidate.paperSize, next.source.paperSize)
                        || candidate.template != next.source.template || candidate.pdfPage != 0 || !candidate.strokes.isEmpty()) {
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
            bootstrap.initializePage(next.source.width, next.source.height, next.source.paperSize, next.source.template);
            bootstrap.sync();
            next.document = documentFor(next.target);
        }
        if (next.document.error() == null) next.document.load(); else next.document.retry();
        next.document.retry();
    }

    private NotebookDocument documentFor(int number) {
        return documentForSlot(storageSlot(number));
    }

    private NotebookDocument documentForSlot(int slot) {
        NotebookDocument result = documents.get(slot);
        if (result == null) {
            result = new NotebookDocument(slotDirectory(slot));
            documents.put(slot, result);
        }
        return result;
    }

    private NotebookDocument targetDocument(int number, boolean validate) throws IOException {
        requireLoaded(); requirePageNumber(number);
        NotebookDocument target = documents.get(storageSlot(number));
        if (target != null && target.current() != null) {
            // Already accepted ink must remain visible even when another part of the same
            // split stroke failed to save. Do not reload or discard it during repaint.
            if (validate && problem == null && target.error() == null) {
                if (count > 1 || durableIndex.managed()) requirePageFiles(number);
                target.validateDisk();
            }
            requirePdfSource(target.current());
            return target;
        }
        if (count > 1 || durableIndex.managed()) requirePageFiles(number);
        if (target == null) target = documentFor(number);
        target.load();
        if ((count > 1 || durableIndex.managed()) && !target.current().isInitialized()) throw new IOException("Saved page has no dimensions");
        requirePdfSource(target.current());
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
            if (count > 1 || durableIndex.managed()) requireSlotFiles(entry.getKey());
            entry.getValue().retry();
            requirePdfSource(entry.getValue().current());
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
        return slotDirectory(storageSlot(number));
    }

    private int storageSlot(int number) {
        // Only the legacy append path addresses the not-yet-indexed count+1 slot.
        return durableIndex != null && number <= durableIndex.pageCount ? durableIndex.slot(number) : number;
    }

    private File slotDirectory(int slot) {
        return slot == 1 ? directory : new File(new File(directory, "pages"), Integer.toString(slot));
    }

    private void requirePageFiles(int number) throws IOException {
        requireSlotFiles(storageSlot(number));
    }

    private void requireSlotFiles(int slot) throws IOException {
        File target = slotDirectory(slot);
        if (!new File(target, "page.nbk").exists() && !new File(target, "page.nbk.bak").exists()
                && !new File(target, "page.nbk.tmp").exists()) {
            throw new IOException("A saved page is missing; no blank replacement was created.");
        }
    }

    private void requirePdfSource(NotebookPage page) throws IOException {
        if (page == null || page.pdfPage == 0) return;
        File source = sourcePdf();
        if (source == null || source.length() == 0)
            throw new IOException("The original source PDF is missing or empty; files were preserved.");
    }

    private boolean matchesAttempt(NotebookIndex.State state) {
        return attemptedCount != 0 && state.pageCount == attemptedCount
            && state.selectedPage == attemptedSelection
            && (!durableIndex.managed() || state.generation == durableIndex.generation
                && state.nextSlot == durableIndex.nextSlot && state.slots.equals(durableIndex.slots));
    }

    private void edit(Operation operation) throws IOException {
        execute(() -> {
            requireLoaded();
            // Completed pen-up events already queued before a page save failure still belong to
            // this visible page. Document retains them in memory while refusing disk writes.
            if (problem != null && document.error() == null) throw problem;
            requirePdfSource(current());
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
