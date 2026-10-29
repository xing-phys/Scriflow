package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Worker-owned notebook catalog and cached books. The legacy notebook always keeps its path. */
public final class NotebookLibrary {
    private interface Operation { void run() throws IOException; }
    private static final int OPEN = 1, CREATE = 2, RENAME = 3;
    private final File directory;
    private final NotebookCatalog catalog;
    private final Map<Integer, NotebookBook> books = new HashMap<>();
    private NotebookCatalog.State state, durable;
    private List<NotebookCatalog.Entry> attemptedEntries;
    private List<NotebookCatalog.Folder> attemptedFolders;
    private int attemptedSelection, bookId;
    private NotebookBook book;
    private IOException problem;
    private Pending pending;

    private static final class Pending {
        final int kind, selected;
        final List<NotebookCatalog.Entry> entries;
        List<NotebookCatalog.Folder> folders;
        NotebookPaperSize paperSize;
        NotebookBook book;
        boolean ownsDirectory;
        Pending(int kind, List<NotebookCatalog.Entry> entries, int selected) {
            this.kind = kind; this.entries = new ArrayList<>(entries); this.selected = selected;
        }
    }

    public NotebookLibrary(File directory) {
        if (directory == null) throw new IllegalArgumentException("Missing notebook library directory");
        this.directory = directory;
        catalog = new NotebookCatalog(directory);
    }

    public NotebookPage current() { return book == null ? null : book.current(); }
    public IOException error() { return problem; }
    public boolean canUndo() { return book != null && book.canUndo(); }
    public boolean canRedo() { return book != null && book.canRedo(); }
    public int pageNumber() { return book == null ? 1 : book.pageNumber(); }
    public int pageCount() { return book == null ? 1 : book.pageCount(); }
    public boolean canUndoPageChange() { return book != null && book.canUndoPageChange(); }
    public boolean canRedoPageChange() { return book != null && book.canRedoPageChange(); }
    public NotebookScrollStore.State scrollPosition() { return book == null ? null : book.scrollPosition(); }
    public List<NotebookCatalog.Entry> entries() { return state == null ? Collections.emptyList() : state.entries; }
    public List<NotebookCatalog.Folder> folders() { return state == null ? Collections.emptyList() : state.folders; }
    public int availableNotebookId() { return state == null ? 0 : state.availableId(); }
    public int notebookId() { return book == null ? state == null ? 1 : state.selectedId : bookId; }
    public String notebookName() {
        return state == null ? "My Notebook" : state.entries.get(notebookId() - 1).name;
    }

    public File currentDirectory() { return bookDirectory(notebookId()); }
    public File sourcePdf() { return book == null ? null : book.sourcePdf(); }
    public List<NotebookPageCatalog.Entry> navigationMetadata() throws IOException {
        if (book == null) throw new IOException("No notebook is open");
        return book.navigationMetadata();
    }
    public List<NotebookOutline.Entry> outline() throws IOException {
        if (book == null) throw new IOException("No notebook is open");
        return book.outline();
    }
    public void setOutline(List<NotebookOutline.Entry> entries) throws IOException {
        if (book == null) throw new IOException("No notebook is open");
        book.setOutline(entries);
    }

    /** Metadata-only barrier: an open book's error remains available without hiding the catalog. */
    public void loadCatalog() throws IOException {
        if (state != null && !new File(directory, "restore.pending").exists()) return;
        if (book != null) book.retry();
        execute(() -> {
            loadCatalogInitial(); book = null; bookId = 0;
        });
    }

    public void load() throws IOException {
        execute(() -> {
            if (state == null) loadCatalogInitial();
            requireWritable();
            if (book != null) { book.load(); return; }
            requireActive(state.selectedId);
            pending = new Pending(OPEN, state.entries, state.selectedId);
            finishPending();
        });
    }

    public void openNotebook(int id) throws IOException {
        execute(() -> {
            requireCatalog(); requireWritable(); requireId(id);
            requireActive(id);
            if (book != null && bookId == id) { book.retry(); return; }
            pending = new Pending(OPEN, state.entries, id);
            finishPending();
        });
    }

    public void createNotebook(String name) throws IOException {
        createNotebook(name, null);
    }

    /** New application notebooks choose physical paper before becoming visible in the catalog. */
    public void createNotebook(String name, NotebookPaperSize paperSize) throws IOException {
        createNotebook(name, paperSize, 0);
    }

    public void createNotebook(String name, NotebookPaperSize paperSize, int folderId) throws IOException {
        execute(() -> {
            requireCatalog(); requireWritable();
            int id = state.availableId();
            if (id == 0) throw new IOException("Notebook library is full. Permanently delete a notebook from Trash to free space.");
            if (folderId != 0) folderIndex(folderId);
            List<NotebookCatalog.Entry> entries = new ArrayList<>(state.entries);
            NotebookCatalog.Entry entry = new NotebookCatalog.Entry(id, name, folderId, NotebookCatalog.ACTIVE);
            if (id <= entries.size()) entries.set(id - 1, entry); else entries.add(entry);
            books.remove(id);
            pending = new Pending(CREATE, entries, id);
            pending.paperSize = paperSize;
            finishPending();
        });
    }

    /** Restores as a new notebook; errors are local to the transfer, never an ink save error. */
    public void importNotebook(NotebookBackup.Restored restored) throws IOException {
        requireCatalog(); requireWritable();
        if (book != null) book.retry();
        NotebookRestore.publish(directory, restored);
        book = null; bookId = 0;
        loadCatalogInitial();
    }

    public void renameNotebook(int id, String name) throws IOException {
        execute(() -> {
            requireCatalog(); requireWritable(); requireId(id);
            requireActive(id);
            List<NotebookCatalog.Entry> entries = new ArrayList<>(state.entries);
            entries.set(id - 1, entries.get(id - 1).renamed(name));
            pending = new Pending(RENAME, entries, state.selectedId);
            finishPending();
        });
    }

    public void createFolder(String name) throws IOException {
        execute(() -> {
            requireCatalog(); requireWritable();
            if (state.folders.size() >= NotebookCatalog.MAX_NOTEBOOKS) throw new IOException("The bookshelf supports up to 200 folders");
            String checkedName = checkedFolderName(name, 0);
            int id = 1;
            List<NotebookCatalog.Folder> folders = new ArrayList<>(state.folders);
            for (NotebookCatalog.Folder folder : folders) id = Math.max(id, folder.id + 1);
            folders.add(new NotebookCatalog.Folder(id, checkedName));
            saveMetadata(state.entries, folders);
        });
    }

    public void renameFolder(int id, String name) throws IOException {
        execute(() -> {
            requireCatalog(); requireWritable();
            List<NotebookCatalog.Folder> folders = new ArrayList<>(state.folders);
            int index = folderIndex(id);
            folders.set(index, new NotebookCatalog.Folder(id, checkedFolderName(name, id))); saveMetadata(state.entries, folders);
        });
    }

    /** Removing a folder keeps its notebooks and restores them to the bookshelf root. */
    public void deleteFolder(int id) throws IOException {
        execute(() -> {
            requireCatalog(); requireWritable();
            List<NotebookCatalog.Folder> folders = new ArrayList<>(state.folders);
            folders.remove(folderIndex(id));
            List<NotebookCatalog.Entry> entries = new ArrayList<>(state.entries);
            for (int i = 0; i < entries.size(); i++) if (entries.get(i).folderId == id)
                entries.set(i, entries.get(i).moved(0));
            saveMetadata(entries, folders);
        });
    }

    public void moveNotebook(int id, int folder) throws IOException {
        execute(() -> {
            requireCatalog(); requireWritable(); requireId(id); requireActive(id);
            if (folder != 0) folderIndex(folder);
            List<NotebookCatalog.Entry> entries = new ArrayList<>(state.entries);
            entries.set(id - 1, entries.get(id - 1).moved(folder)); saveMetadata(entries, state.folders);
        });
    }

    public void trashNotebook(int id) throws IOException { setTrash(id, true); }
    public void restoreNotebook(int id) throws IOException { setTrash(id, false); }

    private void setTrash(int id, boolean trash) throws IOException {
        execute(() -> {
            requireCatalog(); requireWritable(); requireId(id);
            NotebookCatalog.Entry entry = state.entries.get(id - 1);
            if (entry.status != (trash ? NotebookCatalog.ACTIVE : NotebookCatalog.TRASH))
                throw new IOException("This notebook is not available for that action");
            List<NotebookCatalog.Entry> entries = new ArrayList<>(state.entries);
            entries.set(id - 1, entry.withStatus(trash ? NotebookCatalog.TRASH : NotebookCatalog.ACTIVE));
            saveMetadata(entries, state.folders);
        });
    }

    /** Durable purge intent precedes file removal, so an interrupted purge cannot be restored. */
    public void permanentlyDeleteNotebook(int id) throws IOException {
        execute(() -> {
            requireCatalog(); requireWritable(); requireId(id);
            NotebookCatalog.Entry entry = state.entries.get(id - 1);
            if (entry.status != NotebookCatalog.TRASH && entry.status != NotebookCatalog.PURGING)
                throw new IOException("Move this notebook to Trash before permanently deleting it");
            if (entry.status == NotebookCatalog.TRASH) {
                List<NotebookCatalog.Entry> entries = new ArrayList<>(state.entries);
                entries.set(id - 1, entry.withStatus(NotebookCatalog.PURGING)); saveMetadata(entries, state.folders);
            }
            finishPurges();
        });
    }

    /** A read-only first-page preview must never change the selected notebook. */
    public NotebookPage previewNotebook(int id) throws IOException {
        requireCatalog(); requireId(id); requireActive(id);
        File target = bookDirectory(id);
        NotebookIndex.State index = new NotebookIndex(target).load();
        int slot = index.slots.get(0);
        File first = slot == 1 ? target : new File(new File(target, "pages"), Integer.toString(slot));
        if (!new File(first, "page.nbk").isFile() && !new File(first, "page.nbk.bak").isFile()
                && !new File(first, "page.nbk.tmp").isFile()) throw new IOException("Notebook preview is unavailable");
        return new NotebookStore(first).load();
    }

    private NotebookBook companionBook(int id) throws IOException {
        requireCatalog(); requireId(id); requireActive(id);
        NotebookBook cached = books.get(id);
        if (cached == null) { cached = new NotebookBook(bookDirectory(id)); cached.load(); books.put(id, cached); }
        return cached;
    }
    public NotebookPage companionPage(int id, int number) throws IOException { return companionBook(id).previewPage(number); }
    public int companionCount(int id) throws IOException { return companionBook(id).pageCount(); }
    public List<NotebookPageCatalog.Entry> companionMetadata(int id) throws IOException { return companionBook(id).navigationMetadata(); }

    public File notebookPdf(int id) { return new File(bookDirectory(id), "source.pdf"); }

    private int folderIndex(int id) throws IOException {
        for (int i = 0; i < state.folders.size(); i++) if (state.folders.get(i).id == id) return i;
        throw new IOException("Folder is no longer available");
    }

    private String checkedFolderName(String name, int exceptId) throws IOException {
        String checked = NotebookCatalog.normalizedName(name);
        for (NotebookCatalog.Folder folder : state.folders)
            if (folder.id != exceptId && folder.name.toLowerCase(java.util.Locale.ROOT).equals(checked.toLowerCase(java.util.Locale.ROOT)))
                throw new IOException("A folder with this name already exists");
        return checked;
    }

    private void saveMetadata(List<NotebookCatalog.Entry> entries, List<NotebookCatalog.Folder> folders) throws IOException {
        int selected = state.selectedId;
        if (entries.get(selected - 1).status != NotebookCatalog.ACTIVE)
            for (NotebookCatalog.Entry entry : entries) if (entry.status == NotebookCatalog.ACTIVE) { selected = entry.id; break; }
        pending = new Pending(RENAME, entries, selected); pending.folders = new ArrayList<>(folders);
        finishPending();
    }

    private void finishPurges() throws IOException {
        for (NotebookCatalog.Entry entry : new ArrayList<>(state.entries)) if (entry.status == NotebookCatalog.PURGING) {
            books.remove(entry.id);
            File target = bookDirectory(entry.id);
            if (target.exists() && !target.equals(directory) && !target.toPath().toRealPath().toFile().equals(
                    new File(new File(directory.toPath().toRealPath().toFile(), "notebooks"), Integer.toString(entry.id))))
                throw new IOException("Unexpected notebook path; files preserved");
            if (target.equals(directory)) {
                // The first legacy notebook shares a root with the catalog and other books.
                for (String name : new String[]{"page.nbk", "page.nbk.bak", "page.nbk.tmp", "page.created",
                        "book.nbi", "book.nbi.bak", "book.nbi.tmp", "scroll.nbs", "scroll.nbs.bak", "scroll.nbs.tmp",
                        "outline.nbo", "outline.nbo.bak", "outline.nbo.tmp", "source.pdf", "navigation.cache",
                        "navigation.cache.tmp", "pages", "restore.owner"}) deleteFiles(new File(target, name));
                File[] children = target.listFiles();
                if (children == null) throw new IOException("Cannot inspect notebook transaction files for deletion");
                for (File child : children) {
                    String name = child.getName();
                    if (name.equals(NotebookInkTransfer.PENDING)) { deleteFiles(child); continue; }
                    String suffix = name.startsWith("ink-transfer.staging-") ? name.substring("ink-transfer.staging-".length())
                        : name.startsWith("ink-transfer.completed-") ? name.substring("ink-transfer.completed-".length()) : "";
                    try {
                        if (java.util.UUID.fromString(suffix).toString().equals(suffix)) deleteFiles(child);
                    } catch (IllegalArgumentException ignored) { /* Unrelated root files are preserved. */ }
                }
            } else deleteFiles(target);
            List<NotebookCatalog.Entry> entries = new ArrayList<>(state.entries);
            entries.set(entry.id - 1, new NotebookCatalog.Entry(entry.id, "Deleted notebook", 0, NotebookCatalog.PURGED));
            saveMetadata(entries, state.folders);
        }
    }

    private static void deleteFiles(File target) throws IOException {
        if (java.nio.file.Files.isSymbolicLink(target.toPath())) throw new IOException("Unexpected notebook path; files preserved");
        if (!target.exists()) return;
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children == null) throw new IOException("Cannot inspect notebook files for deletion");
            for (File child : children) {
                if (java.nio.file.Files.isSymbolicLink(child.toPath()))
                    throw new IOException("Unexpected notebook path; files preserved");
                deleteFiles(child);
            }
        }
        if (!target.delete()) throw new IOException("Cannot permanently delete notebook files. Retry to finish deleting.");
    }

    public void initializeSize(int width, int height) throws IOException { edit(() -> book.initializeSize(width, height)); }
    public void append(NotebookPage.Stroke stroke) throws IOException { edit(() -> book.append(stroke)); }
    public void setTemplate(NotebookPage.Template template) throws IOException { edit(() -> book.setTemplate(template)); }
    public void selectPage(int number) throws IOException { edit(() -> book.selectPage(number)); }
    public void addPage() throws IOException { edit(() -> book.addPage()); }
    public void managePages(NotebookBook.PageAction action, int source, int destination) throws IOException {
        edit(() -> book.managePages(action, source, destination));
    }
    public void undoPageChange() throws IOException { edit(() -> book.undoPageChange()); }
    public void redoPageChange() throws IOException { edit(() -> book.redoPageChange()); }
    public NotebookPage page(int number) throws IOException {
        final NotebookPage[] result = {null}; edit(() -> result[0] = book.page(number)); return result[0];
    }
    public NotebookPage cachedPage(int number) throws IOException {
        if (book == null) throw new IOException("No notebook is open");
        return book.cachedPage(number);
    }
    /** A failed overview thumbnail never becomes a library/editor save error. */
    public NotebookPage previewPage(int number) throws IOException {
        requireCatalog();
        if (book == null) throw new IOException("No notebook is open");
        return book.previewPage(number);
    }
    public void appendToPage(int number, NotebookPage.Stroke stroke) throws IOException { edit(() -> book.appendToPage(number, stroke)); }
    public void appendStrokesToPage(int number, List<NotebookPage.Stroke> strokes) throws IOException {
        edit(() -> book.appendStrokesToPage(number, strokes));
    }
    public void setFigures(int number, List<NotebookFigure> figures) throws IOException { edit(() -> book.setFigures(number, figures)); }
    public void editFigure(int source, int destination, java.util.UUID page, NotebookFigure expected,
            NotebookFigure replacement, boolean copy) throws IOException {
        edit(() -> book.editFigure(source, destination, page, expected, replacement, copy));
    }
    public void setPageTemplate(int number, NotebookPage.Template template) throws IOException { edit(() -> book.setPageTemplate(number, template)); }
    public void saveScrollPosition(int number, double fraction) throws IOException { edit(() -> book.saveScrollPosition(number, fraction)); }
    public boolean eraseOnPage(int number, List<NotebookPage.Point> path, float radius) throws IOException {
        final boolean[] result = {false}; edit(() -> result[0] = book.eraseOnPage(number, path, radius)); return result[0];
    }
    public boolean eraseOnPagePaths(int number, List<List<NotebookPage.Point>> paths, float radius) throws IOException {
        final boolean[] result = {false}; edit(() -> result[0] = book.eraseOnPagePaths(number, paths, radius)); return result[0];
    }
    public boolean undoOnPage(int number) throws IOException {
        final boolean[] result = {false}; edit(() -> result[0] = book.undoOnPage(number)); return result[0];
    }
    public NotebookLasso.Selection editSelectionOnPage(int number, NotebookLasso.Selection selection,
            NotebookLasso.Action action, float dx, float dy) throws IOException {
        final NotebookLasso.Selection[] result = {null};
        edit(() -> result[0] = book.editSelectionOnPage(number, selection, action, dx, dy));
        return result[0];
    }
    public NotebookLasso.Selection moveSelection(int source, int destination, NotebookLasso.Selection selection,
            float centerX, float centerY) throws IOException {
        final NotebookLasso.Selection[] result = {null};
        edit(() -> result[0] = book.moveSelection(source, destination, selection, centerX, centerY));
        return result[0];
    }
    public NotebookLasso.Selection pasteOnPage(int number, NotebookLasso.Clipboard clipboard,
            float x, float y) throws IOException {
        final NotebookLasso.Selection[] result = {null};
        edit(() -> result[0] = book.pasteOnPage(number, clipboard, x, y));
        return result[0];
    }
    public boolean redoOnPage(int number) throws IOException {
        final boolean[] result = {false}; edit(() -> result[0] = book.redoOnPage(number)); return result[0];
    }
    public boolean canUndoOnPage(int number) throws IOException {
        final boolean[] result = {false}; edit(() -> result[0] = book.canUndoOnPage(number)); return result[0];
    }
    public boolean canRedoOnPage(int number) throws IOException {
        final boolean[] result = {false}; edit(() -> result[0] = book.canRedoOnPage(number)); return result[0];
    }
    public boolean erase(List<NotebookPage.Point> path, float radius) throws IOException {
        final boolean[] result = {false}; edit(() -> result[0] = book.erase(path, radius)); return result[0];
    }
    public boolean undo() throws IOException {
        final boolean[] result = {false}; edit(() -> result[0] = book.undo()); return result[0];
    }
    public boolean redo() throws IOException {
        final boolean[] result = {false}; edit(() -> result[0] = book.redo()); return result[0];
    }

    /** Retries only the acknowledged catalog or its exact attempted publication, never a subset. */
    public void retry() throws IOException {
        execute(() -> {
            if (state == null) { loadCatalogInitial(); return; }
            NotebookCatalog.State disk = catalog.load();
            if (!disk.equals(durable) && !matchesAttempt(disk)) {
                throw new IOException("Saved notebook names or selection differ from the open library; files were preserved.");
            }
            catalog.sync();
            durable = disk;
            attemptedEntries = null; attemptedSelection = 0;
            if (book != null) book.retry();
            if (pending != null) finishPending();
            else state = disk;
            finishPurges();
            problem = null;
        });
    }

    private void loadCatalogInitial() throws IOException {
        NotebookRestore.recover(directory);
        state = durable = catalog.load();
        attemptedEntries = null; attemptedSelection = 0;
        problem = null;
        finishPurges();
    }

    private void finishPending() throws IOException {
        Pending next = pending;
        if (next.folders == null) next.folders = new ArrayList<>(state.folders);
        NotebookCatalog.State disk = catalog.load();
        if (!disk.equals(durable) && !matchesAttempt(disk)) {
            throw new IOException("Saved notebook names or selection differ from the open library; files were preserved.");
        }
        catalog.sync();
        durable = disk;
        if (book != null) book.retry();
        if (next.kind == CREATE) prepareNew(next);
        if (next.kind != RENAME) {
            if (next.selected > 1) requirePageFiles(next.selected);
            if (next.book == null) next.book = bookFor(next.selected);
            if (next.book.error() == null) next.book.load(); else next.book.retry();
            // Loading a cached book is a barrier; retry also checks it against its disk state.
            next.book.retry();
        }
        attemptedEntries = next.entries; attemptedSelection = next.selected; attemptedFolders = next.folders;
        NotebookCatalog.State saved;
        if (sameTarget(durable, next.entries, next.selected, next.folders)) saved = durable;
        else saved = catalog.save(next.entries, next.selected, next.folders);
        catalog.sync();
        state = durable = saved;
        attemptedEntries = null; attemptedSelection = 0;
        if (next.kind != RENAME) { book = next.book; bookId = next.selected; }
        else if (book != null && state.entries.get(bookId - 1).status != NotebookCatalog.ACTIVE) { book = null; bookId = 0; }
        pending = null;
    }

    private void prepareNew(Pending next) throws IOException {
        if (next.book != null) return;
        File target = bookDirectory(next.selected);
        if (!next.ownsDirectory) {
            String[] contents = target.list();
            if (target.exists() && (!target.isDirectory() || contents == null)) {
                throw new IOException("Cannot inspect an unlisted notebook; files were preserved.");
            }
            if (contents != null && contents.length != 0) {
                // The only adoptable orphan is our exact durable blank first page. Extra
                // page/index files or any writing indicate an unrelated notebook, not a retry.
                for (String file : contents) {
                    if (!file.equals("page.nbk") && !file.equals("page.nbk.bak") && !file.equals("page.nbk.tmp")) {
                        throw new IOException("An unexpected unlisted notebook exists; files were preserved.");
                    }
                }
                requirePageFiles(next.selected);
                NotebookPage candidate = new NotebookStore(target).load();
                if (candidate.isInitialized() != (next.paperSize != null)
                        || !java.util.Objects.equals(candidate.paperSize, next.paperSize)
                        || next.paperSize != null && (candidate.width != next.paperSize.logicalWidth()
                            || candidate.height != next.paperSize.logicalHeight())
                        || !candidate.strokes.isEmpty()
                        || candidate.template != NotebookPage.Template.BLANK) {
                    throw new IOException("An unexpected unlisted notebook exists; files were preserved.");
                }
            }
            next.ownsDirectory = true;
        }
        NotebookStore bootstrap = new NotebookStore(target);
        bootstrap.load();
        if (next.paperSize == null) bootstrap.initializeEmpty();
        else bootstrap.initializePage(next.paperSize, NotebookPage.Template.BLANK);
        bootstrap.sync();
    }

    private NotebookBook bookFor(int id) {
        NotebookBook result = books.get(id);
        if (result == null) { result = new NotebookBook(bookDirectory(id)); books.put(id, result); }
        return result;
    }

    private File bookDirectory(int id) {
        File nested = new File(new File(directory, "notebooks"), Integer.toString(id));
        // A reused legacy slot is created under notebooks/1; the library root stays metadata-only.
        return id == 1 && !nested.exists() && (state == null || state.entries.get(0).status != NotebookCatalog.PURGED)
            ? directory : nested;
    }

    private void requirePageFiles(int id) throws IOException {
        File target = bookDirectory(id);
        if (!new File(target, "page.nbk").exists() && !new File(target, "page.nbk.bak").exists()
                && !new File(target, "page.nbk.tmp").exists()) {
            throw new IOException("Saved notebook " + id + " is missing; no blank replacement was created.");
        }
    }

    private boolean matchesAttempt(NotebookCatalog.State disk) {
        return attemptedEntries != null && sameTarget(disk, attemptedEntries, attemptedSelection, attemptedFolders);
    }

    private static boolean sameTarget(NotebookCatalog.State state, List<NotebookCatalog.Entry> entries, int selected,
                                      List<NotebookCatalog.Folder> folders) {
        return state != null && state.selectedId == selected && state.entries.equals(entries) && state.folders.equals(folders);
    }

    private void requireCatalog() throws IOException { if (state == null) throw new IOException("Notebook library has not loaded"); }
    private void requireId(int id) throws IOException { if (id < 1 || id > state.entries.size()) throw new IOException("Notebook is out of range"); }
    private void requireActive(int id) throws IOException {
        if (state.entries.get(id - 1).status != NotebookCatalog.ACTIVE) throw new IOException("Choose a notebook from the bookshelf");
    }
    private void requireWritable() throws IOException { if (problem != null) throw problem; }

    private void edit(Operation operation) throws IOException {
        execute(() -> {
            if (book == null) throw new IOException("No notebook is open");
            // Previously queued completed UP events remain on their original book after a
            // failed page write. Catalog failures instead pause all subsequent changes.
            if (problem != null && book.error() == null) throw problem;
            operation.run();
        });
    }

    private void execute(Operation operation) throws IOException {
        try { operation.run(); }
        catch (IOException error) { problem = error; throw error; }
        catch (RuntimeException error) { problem = new IOException("Could not save the notebook library", error); throw problem; }
    }
}
