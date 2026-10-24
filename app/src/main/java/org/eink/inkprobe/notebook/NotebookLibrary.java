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
    private int attemptedSelection, bookId;
    private NotebookBook book;
    private IOException problem;
    private Pending pending;

    private static final class Pending {
        final int kind, selected;
        final List<NotebookCatalog.Entry> entries;
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
    public List<NotebookCatalog.Entry> entries() { return state == null ? Collections.emptyList() : state.entries; }
    public int notebookId() { return book == null ? state == null ? 1 : state.selectedId : bookId; }
    public String notebookName() {
        return state == null ? "My Notebook" : state.entries.get(notebookId() - 1).name;
    }

    /** Metadata-only barrier: an open book's error remains available without hiding the catalog. */
    public void loadCatalog() throws IOException {
        if (state != null) return;
        execute(this::loadCatalogInitial);
    }

    public void load() throws IOException {
        execute(() -> {
            if (state == null) loadCatalogInitial();
            requireWritable();
            if (book != null) { book.load(); return; }
            pending = new Pending(OPEN, state.entries, state.selectedId);
            finishPending();
        });
    }

    public void openNotebook(int id) throws IOException {
        execute(() -> {
            requireCatalog(); requireWritable(); requireId(id);
            if (book != null && bookId == id) { book.retry(); return; }
            pending = new Pending(OPEN, state.entries, id);
            finishPending();
        });
    }

    public void createNotebook(String name) throws IOException {
        execute(() -> {
            requireCatalog(); requireWritable();
            if (state.entries.size() >= NotebookCatalog.MAX_NOTEBOOKS) throw new IOException("Notebook library is full");
            int id = state.entries.size() + 1;
            List<NotebookCatalog.Entry> entries = new ArrayList<>(state.entries);
            entries.add(new NotebookCatalog.Entry(id, name));
            pending = new Pending(CREATE, entries, id);
            finishPending();
        });
    }

    public void renameNotebook(int id, String name) throws IOException {
        execute(() -> {
            requireCatalog(); requireWritable(); requireId(id);
            List<NotebookCatalog.Entry> entries = new ArrayList<>(state.entries);
            entries.set(id - 1, new NotebookCatalog.Entry(id, name));
            pending = new Pending(RENAME, entries, state.selectedId);
            finishPending();
        });
    }

    public void initializeSize(int width, int height) throws IOException { edit(() -> book.initializeSize(width, height)); }
    public void append(NotebookPage.Stroke stroke) throws IOException { edit(() -> book.append(stroke)); }
    public void setTemplate(NotebookPage.Template template) throws IOException { edit(() -> book.setTemplate(template)); }
    public void selectPage(int number) throws IOException { edit(() -> book.selectPage(number)); }
    public void addPage() throws IOException { edit(() -> book.addPage()); }
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
            problem = null;
        });
    }

    private void loadCatalogInitial() throws IOException {
        state = durable = catalog.load();
        attemptedEntries = null; attemptedSelection = 0;
        problem = null;
    }

    private void finishPending() throws IOException {
        Pending next = pending;
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
        attemptedEntries = next.entries; attemptedSelection = next.selected;
        NotebookCatalog.State saved;
        if (sameTarget(durable, next.entries, next.selected)) saved = durable;
        else saved = catalog.save(next.entries, next.selected);
        catalog.sync();
        state = durable = saved;
        attemptedEntries = null; attemptedSelection = 0;
        if (next.kind != RENAME) { book = next.book; bookId = next.selected; }
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
                // The only adoptable orphan is our durable, unsized blank first page. Extra
                // page/index files or any writing indicate an unrelated notebook, not a retry.
                for (String file : contents) {
                    if (!file.equals("page.nbk") && !file.equals("page.nbk.bak") && !file.equals("page.nbk.tmp")) {
                        throw new IOException("An unexpected unlisted notebook exists; files were preserved.");
                    }
                }
                requirePageFiles(next.selected);
                NotebookPage candidate = new NotebookStore(target).load();
                if (candidate.isInitialized() || !candidate.strokes.isEmpty()
                        || candidate.template != NotebookPage.Template.BLANK) {
                    throw new IOException("An unexpected unlisted notebook exists; files were preserved.");
                }
            }
            next.ownsDirectory = true;
        }
        NotebookStore bootstrap = new NotebookStore(target);
        bootstrap.load(); bootstrap.initializeEmpty(); bootstrap.sync();
    }

    private NotebookBook bookFor(int id) {
        NotebookBook result = books.get(id);
        if (result == null) { result = new NotebookBook(bookDirectory(id)); books.put(id, result); }
        return result;
    }

    private File bookDirectory(int id) {
        return id == 1 ? directory : new File(new File(directory, "notebooks"), Integer.toString(id));
    }

    private void requirePageFiles(int id) throws IOException {
        File target = bookDirectory(id);
        if (!new File(target, "page.nbk").exists() && !new File(target, "page.nbk.bak").exists()
                && !new File(target, "page.nbk.tmp").exists()) {
            throw new IOException("Saved notebook " + id + " is missing; no blank replacement was created.");
        }
    }

    private boolean matchesAttempt(NotebookCatalog.State disk) {
        return attemptedEntries != null && sameTarget(disk, attemptedEntries, attemptedSelection);
    }

    private static boolean sameTarget(NotebookCatalog.State state, List<NotebookCatalog.Entry> entries, int selected) {
        return state != null && state.selectedId == selected && state.entries.equals(entries);
    }

    private void requireCatalog() throws IOException { if (state == null) throw new IOException("Notebook library has not loaded"); }
    private void requireId(int id) throws IOException { if (id < 1 || id > state.entries.size()) throw new IOException("Notebook is out of range"); }
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
