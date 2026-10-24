package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Real-file checks for library upgrades, isolated books and interrupted catalog transitions. */
public final class NotebookLibraryTest {
    private interface Action { void run() throws Exception; }
    private static int assertions;
    private static final List<File> directories = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        try {
            legacyPagesAndSelectionRemainByteExact();
            independentBooksPagesAndHistory();
            metadataDoesNotOpenCorruptSelectedBook();
            failedCreateKeepsVisibleBookAndRetries();
            ambiguouslyCompletedCreateIsAcceptedExactly();
            failedRenameAndUnexpectedMetadata();
            changedCatalogBeforeNormalMutationIsPreserved();
            corruptOrMissingTargetNeverBecomesBlank();
            changedCachedBookIsNotOverwritten();
            queuedInkAfterPageErrorStaysOnItsBook();
            orphanCreationRecoveryIsConservative();
            failedCreationParentAndRetry();
            initialCatalogCorruptionCanBeRepaired();
            invalidNamesCannotChangeFiles();
            viewportEditsAndPositionsStayWithTheirNotebook();
            groupedViewportInkStaysWithItsNotebook();
            System.out.println("NotebookLibrary: " + assertions + " assertions passed (host JVM, real files).");
        } finally { for (File directory : directories) deleteTree(directory); }
    }

    private static void groupedViewportInkStaysWithItsNotebook() throws Exception {
        File root = directory(); NotebookLibrary library = ready(root); library.addPage();
        library.page(1); library.page(2);
        byte[] first = bytes(root, "page.nbk");
        check(new File(root, "page.nbk").delete(), "remove first notebook page journal for grouped failure");
        check(new File(root, "page.nbk").mkdir(), "block first notebook page group");
        expectIo(() -> library.appendStrokesToPage(1, Arrays.asList(stroke(10), stroke(30))));
        IOException failure = library.error();
        library.appendStrokesToPage(2, Arrays.asList(stroke(50), stroke(70)));
        check(library.error() == failure, "healthy grouped viewport save preserves library error");
        equal(2, library.cachedPage(1).strokes.size(), "library retains every failed grouped piece");
        equal(2, library.cachedPage(2).strokes.size(), "library accepts healthy later-page group");
        check(new File(root, "page.nbk").delete(), "remove library grouped failure obstruction");
        Files.write(new File(root, "page.nbk").toPath(), first);
        library.retry();
        library.createNotebook("Other grouped notes"); library.initializeSize(100, 120);
        library.appendStrokesToPage(1, Arrays.asList(stroke(80), stroke(90)));
        library.openNotebook(1); library.undoOnPage(1);
        equal(0, library.page(1).strokes.size(), "one first-notebook undo removes complete recovered group");
        equal(2, library.page(2).strokes.size(), "first-notebook undo leaves other page group intact");
        library.openNotebook(2);
        equal(2, library.page(1).strokes.size(), "other notebook grouped ink isolated");
        library.undoOnPage(1);
        equal(0, library.page(1).strokes.size(), "one other-notebook undo removes its entire group");
        library.redoOnPage(1);
        equal(2, library.page(1).strokes.size(), "one other-notebook redo restores its entire group");
    }

    private static void legacyPagesAndSelectionRemainByteExact() throws Exception {
        File root = directory();
        NotebookBook legacy = new NotebookBook(root);
        legacy.load(); legacy.initializeSize(1404, 1688); legacy.setTemplate(NotebookPage.Template.LINED);
        legacy.append(stroke(10)); legacy.addPage(); legacy.append(stroke(70));
        byte[] page1 = bytes(root, "page.nbk"), page2 = bytes(root, "pages/2/page.nbk"), index = bytes(root, "book.nbi");
        NotebookLibrary library = new NotebookLibrary(root);
        library.loadCatalog();
        equal(1, library.entries().size(), "legacy book imported by reference");
        check(library.current() == null, "catalog load does not load a writing page");
        equal("My Notebook", library.notebookName(), "legacy default name");
        check(!new File(root, "library.nbl").exists(), "metadata load writes nothing");
        library.load(); equal(2, library.pageNumber(), "legacy selected page retained"); firstX(70, library.current());
        library.createNotebook("Research");
        equal(2, library.notebookId(), "new notebook selected");
        check(!library.current().isInitialized() && library.current().strokes.isEmpty(), "new notebook starts unsized and empty");
        check(new File(root, "notebooks/2/page.nbk").isFile(), "new notebook page durable before catalog");
        library.renameNotebook(1, "Previous notes"); library.openNotebook(1);
        equal(2, library.pageNumber(), "opening legacy book keeps its page selection");
        equal("Previous notes", library.notebookName(), "legacy rename is metadata only");
        check(Arrays.equals(page1, bytes(root, "page.nbk")), "legacy first page bytes preserved");
        check(Arrays.equals(page2, bytes(root, "pages/2/page.nbk")), "legacy second page bytes preserved");
        check(Arrays.equals(index, bytes(root, "book.nbi")), "legacy page index bytes preserved");
    }

    private static void independentBooksPagesAndHistory() throws Exception {
        File root = directory(); NotebookLibrary library = ready(root);
        library.append(stroke(10)); library.append(stroke(20)); library.undo();
        check(library.canRedo(), "first book has redo");
        library.addPage(); library.append(stroke(30));
        library.createNotebook("Atomic physics");
        check(!library.canUndo() && !library.canRedo(), "new book has no inherited edit history");
        library.initializeSize(100, 120); library.setTemplate(NotebookPage.Template.LINED);
        library.append(stroke(60)); library.addPage(); library.append(stroke(70));
        library.renameNotebook(2, "  Quantum information  ");
        equal("Quantum information", library.notebookName(), "name normalized");
        library.openNotebook(1); equal(2, library.pageNumber(), "first book remembers selected page"); firstX(30, library.current());
        library.selectPage(1); check(library.canRedo(), "first book first page redo retained"); library.redo();
        equal(2, library.current().strokes.size(), "redo applies only to original book page");
        library.openNotebook(2); equal(2, library.pageNumber(), "second book remembers selected page"); firstX(70, library.current());
        library.undo(); check(library.current().strokes.isEmpty(), "undo second book page");
        library.selectPage(1); firstX(60, library.current());
        check(library.current().template == NotebookPage.Template.LINED, "second book paper isolated");
        library.renameNotebook(1, "Condensed matter"); equal(2, library.notebookId(), "renaming another book keeps selection");
        NotebookLibrary reopened = reopen(root);
        equal(2, reopened.notebookId(), "restart reopens selected book"); equal(1, reopened.pageNumber(), "restart reopens selected page");
        firstX(60, reopened.current()); check(!reopened.canUndo() && !reopened.canRedo(), "history remains process-local");
        reopened.openNotebook(1); equal("Condensed matter", reopened.notebookName(), "other name persisted");
        equal(2, reopened.current().strokes.size(), "other book edits persisted");
    }

    private static void metadataDoesNotOpenCorruptSelectedBook() throws Exception {
        File root = directory(); NotebookLibrary original = ready(root); original.createNotebook("Other");
        byte[] valid = bytes(root, "notebooks/2/page.nbk"), bad = valid.clone(); bad[0] ^= 1;
        Files.write(new File(root, "notebooks/2/page.nbk").toPath(), bad);
        NotebookLibrary library = new NotebookLibrary(root); library.loadCatalog();
        equal(2, library.entries().size(), "catalog readable despite corrupt selected page");
        check(library.current() == null && library.error() == null, "metadata load does not inspect selected page");
        expectIo(library::load); check(library.error() != null, "opening detects corruption");
        library.loadCatalog(); check(library.error() != null, "metadata barrier retains error");
        expectIo(() -> library.openNotebook(1));
        check(Arrays.equals(bad, bytes(root, "notebooks/2/page.nbk")), "corruption preserved");
        Files.write(new File(root, "notebooks/2/page.nbk").toPath(), valid);
        library.retry(); equal(2, library.notebookId(), "retry opens repaired selected book");
        check(library.error() == null && library.current() != null, "retry clears error after loading repaired book");
    }

    private static void failedCreateKeepsVisibleBookAndRetries() throws Exception {
        File root = directory(); NotebookLibrary library = ready(root); library.append(stroke(10));
        library.renameNotebook(1, "Named");
        block(root, "library.nbl.tmp"); expectIo(() -> library.createNotebook("Second"));
        equal(1, library.notebookId(), "failed create preserves visible book"); firstX(10, library.current());
        equal(1, library.entries().size(), "unpublished book hidden from catalog");
        check(new File(root, "notebooks/2/page.nbk").isFile(), "first page durable before failed catalog publication");
        byte[] before = bytes(root, "page.nbk"); expectIo(() -> library.append(stroke(90)));
        check(Arrays.equals(before, bytes(root, "page.nbk")), "catalog error pauses edits");
        unblock(root, "library.nbl.tmp"); library.retry();
        equal(2, library.notebookId(), "retry selects intended notebook"); equal(2, library.entries().size(), "retry adds exactly once");
        check(library.current().strokes.isEmpty(), "source ink not copied into new notebook");
        library.openNotebook(1); firstX(10, library.current());
    }

    private static void ambiguouslyCompletedCreateIsAcceptedExactly() throws Exception {
        File root = directory(); NotebookLibrary library = ready(root);
        library.renameNotebook(1, "Named");
        block(root, "library.nbl.tmp"); expectIo(() -> library.createNotebook("Second")); unblock(root, "library.nbl.tmp");
        NotebookCatalog external = new NotebookCatalog(root); NotebookCatalog.State state = external.load();
        List<NotebookCatalog.Entry> entries = new ArrayList<>(state.entries); entries.add(new NotebookCatalog.Entry(2, "Second"));
        external.save(entries, 2);
        library.retry(); equal(2, library.notebookId(), "exact ambiguous target accepted");
        equal(2, library.entries().size(), "ambiguous retry creates no duplicate");
        check(!new File(root, "notebooks/3").exists(), "no duplicate directory created");
    }

    private static void failedRenameAndUnexpectedMetadata() throws Exception {
        File root = directory(); NotebookLibrary library = ready(root); library.append(stroke(10));
        library.createNotebook("Second"); library.initializeSize(100, 120); library.append(stroke(70));
        block(root, "library.nbl.tmp"); expectIo(() -> library.renameNotebook(2, "Renamed"));
        equal("Second", library.notebookName(), "failed rename leaves acknowledged name visible"); firstX(70, library.current());
        unblock(root, "library.nbl.tmp"); library.retry(); equal("Renamed", library.notebookName(), "rename retried");
        block(root, "library.nbl.tmp"); expectIo(() -> library.openNotebook(1)); unblock(root, "library.nbl.tmp");
        NotebookCatalog external = new NotebookCatalog(root); NotebookCatalog.State state = external.load();
        List<NotebookCatalog.Entry> entries = new ArrayList<>(state.entries); entries.set(0, new NotebookCatalog.Entry(1, "Unexpected"));
        external.save(entries, 1);
        byte[] unexpected = bytes(root, "library.nbl"); expectIo(library::retry);
        equal(2, library.notebookId(), "unexpected catalog does not change visible book"); firstX(70, library.current());
        check(Arrays.equals(unexpected, bytes(root, "library.nbl")), "unexpected metadata never overwritten");
    }

    private static void corruptOrMissingTargetNeverBecomesBlank() throws Exception {
        File root = directory(); NotebookLibrary library = ready(root); library.append(stroke(10));
        library.createNotebook("Second"); library.initializeSize(100, 120); library.append(stroke(70)); library.openNotebook(1);
        byte[] valid = bytes(root, "notebooks/2/page.nbk"), corrupt = valid.clone(); corrupt[0] ^= 1;
        Files.write(new File(root, "notebooks/2/page.nbk").toPath(), corrupt);
        expectIo(() -> library.openNotebook(2)); equal(1, library.notebookId(), "corrupt target leaves old book visible");
        firstX(10, library.current()); expectIo(library::retry);
        check(Arrays.equals(corrupt, bytes(root, "notebooks/2/page.nbk")), "corrupt target retained");
        Files.write(new File(root, "notebooks/2/page.nbk").toPath(), valid); library.retry(); firstX(70, library.current());
        library.openNotebook(1); deleteTree(new File(root, "notebooks/2"));
        expectIo(() -> library.openNotebook(2)); expectIo(library::retry);
        check(!new File(root, "notebooks/2").exists(), "missing cached notebook does not become blank");
        equal(1, library.notebookId(), "missing target keeps selection");
        NotebookCatalog catalog = new NotebookCatalog(root); NotebookCatalog.State state = catalog.load(); catalog.save(state.entries, 2);
        NotebookLibrary initial = new NotebookLibrary(root); initial.loadCatalog(); expectIo(initial::load);
        check(!new File(root, "notebooks/2").exists(), "initial missing selected notebook not recreated");
    }

    private static void changedCatalogBeforeNormalMutationIsPreserved() throws Exception {
        File root = directory(); NotebookLibrary library = ready(root); library.renameNotebook(1, "Named");
        NotebookCatalog external = new NotebookCatalog(root); NotebookCatalog.State state = external.load();
        external.save(Collections.singletonList(new NotebookCatalog.Entry(1, "External")), state.selectedId);
        byte[] changed = bytes(root, "library.nbl");
        expectIo(() -> library.renameNotebook(1, "Overwrite")); expectIo(library::retry);
        equal("Named", library.notebookName(), "unexpected names do not replace visible metadata");
        check(Arrays.equals(changed, bytes(root, "library.nbl")), "normal mutation refuses changed catalog");
    }

    private static void changedCachedBookIsNotOverwritten() throws Exception {
        File root = directory(); NotebookLibrary library = ready(root); library.createNotebook("Second");
        library.initializeSize(100, 120); library.append(stroke(70)); library.openNotebook(1);
        NotebookStore external = new NotebookStore(new File(root, "notebooks/2")); external.load(); external.appendStroke(stroke(90));
        byte[] changed = bytes(root, "notebooks/2/page.nbk");
        expectIo(() -> library.openNotebook(2)); expectIo(library::retry);
        check(Arrays.equals(changed, bytes(root, "notebooks/2/page.nbk")), "changed cached target is not overwritten");
        equal(1, library.notebookId(), "changed target never selected");
    }

    private static void queuedInkAfterPageErrorStaysOnItsBook() throws Exception {
        File root = directory(); NotebookLibrary library = ready(root); library.createNotebook("Second");
        library.initializeSize(100, 120); library.openNotebook(1); library.append(stroke(10));
        block(root, "page.nbk.tmp"); expectIo(library::undo); expectIo(() -> library.append(stroke(30)));
        equal(1, library.current().strokes.size(), "completed queued stroke retained after page save failure"); firstX(30, library.current());
        expectIo(() -> library.openNotebook(2)); equal(1, library.notebookId(), "save error blocks book switching");
        unblock(root, "page.nbk.tmp"); library.retry(); firstX(30, library.current());
        library.openNotebook(2); check(library.current().strokes.isEmpty(), "queued source ink not routed into other book");
        library.openNotebook(1); firstX(30, library.current());
    }

    private static void orphanCreationRecoveryIsConservative() throws Exception {
        File root = directory(); NotebookLibrary library = ready(root);
        NotebookStore orphan = new NotebookStore(new File(root, "notebooks/2")); orphan.load(); orphan.initializeEmpty();
        byte[] original = bytes(root, "notebooks/2/page.nbk");
        library.createNotebook("Adopted"); equal(2, library.notebookId(), "interrupted durable empty first page adopted");
        check(Arrays.equals(original, bytes(root, "notebooks/2/page.nbk")), "adoption preserves synced first-page bytes");

        File writtenRoot = directory(); NotebookLibrary writtenLibrary = ready(writtenRoot);
        NotebookStore written = new NotebookStore(new File(writtenRoot, "notebooks/2")); written.load(); written.initializeSize(100, 120); written.appendStroke(stroke(50));
        byte[] writing = bytes(writtenRoot, "notebooks/2/page.nbk");
        expectIo(() -> writtenLibrary.createNotebook("Unsafe")); expectIo(writtenLibrary::retry);
        check(Arrays.equals(writing, bytes(writtenRoot, "notebooks/2/page.nbk")), "orphan writing preserved");
        check(!new File(writtenRoot, "library.nbl").exists(), "unexpected orphan never indexed");

        File extraRoot = directory(); NotebookLibrary extraLibrary = ready(extraRoot);
        NotebookStore empty = new NotebookStore(new File(extraRoot, "notebooks/2")); empty.load(); empty.initializeEmpty();
        Files.write(new File(extraRoot, "notebooks/2/book.nbi").toPath(), new byte[] {1});
        expectIo(() -> extraLibrary.createNotebook("Unsafe"));
        check(Arrays.equals(new byte[]{1}, bytes(extraRoot, "notebooks/2/book.nbi")), "unknown orphan index preserved");
        File corruptRoot = directory(); NotebookLibrary corruptLibrary = ready(corruptRoot);
        File target = new File(corruptRoot, "notebooks/2"); check(target.mkdirs(), "create corrupt orphan directory");
        Files.write(new File(target, "page.nbk.tmp").toPath(), new byte[]{1, 2, 3});
        expectIo(() -> corruptLibrary.createNotebook("Unsafe"));
        check(Arrays.equals(new byte[]{1, 2, 3}, bytes(corruptRoot, "notebooks/2/page.nbk.tmp")), "partial orphan snapshot preserved");
    }

    private static void failedCreationParentAndRetry() throws Exception {
        File root = directory(); NotebookLibrary library = ready(root);
        Files.write(new File(root, "notebooks").toPath(), new byte[] {42});
        expectIo(() -> library.createNotebook("Second")); equal(1, library.notebookId(), "creation path failure keeps old book");
        check(Arrays.equals(new byte[] {42}, bytes(root, "notebooks")), "obstructing path preserved");
        check(new File(root, "notebooks").delete(), "remove simulated unavailable parent");
        library.retry(); equal(2, library.notebookId(), "retry creates notebook after parent restored");
        check(library.current().strokes.isEmpty(), "new notebook empty after creation retry");
    }

    private static void initialCatalogCorruptionCanBeRepaired() throws Exception {
        File root = directory(); NotebookLibrary original = ready(root); original.renameNotebook(1, "Named");
        byte[] valid = bytes(root, "library.nbl"), corrupt = valid.clone(); corrupt[0] ^= 1;
        Files.write(new File(root, "library.nbl").toPath(), corrupt);
        NotebookLibrary library = new NotebookLibrary(root); expectIo(library::loadCatalog);
        check(library.entries().isEmpty() && library.current() == null, "failed initial catalog exposes no invented metadata");
        check(Arrays.equals(corrupt, bytes(root, "library.nbl")), "bad catalog preserved");
        Files.write(new File(root, "library.nbl").toPath(), valid); library.retry();
        equal("Named", library.notebookName(), "catalog retry loads repaired names");
        check(library.current() == null, "catalog retry remains metadata only"); library.load();
        check(library.current() != null, "book can open after catalog repair");
    }

    private static void invalidNamesCannotChangeFiles() throws Exception {
        File root = directory(); NotebookLibrary library = ready(root); library.renameNotebook(1, "Named");
        byte[] before = bytes(root, "library.nbl"); expectIo(() -> library.createNotebook("  "));
        check(!new File(root, "notebooks/2").exists(), "invalid name creates no notebook files");
        check(Arrays.equals(before, bytes(root, "library.nbl")), "invalid name leaves catalog unchanged");
        library.retry(); expectIo(() -> library.renameNotebook(1, "two\nlines"));
        check(Arrays.equals(before, bytes(root, "library.nbl")), "invalid rename leaves catalog unchanged");
        library.retry(); equal("Named", library.notebookName(), "retry retains acknowledged name");
    }

    private static void viewportEditsAndPositionsStayWithTheirNotebook() throws Exception {
        File root = directory(); NotebookLibrary library = ready(root); library.addPage();
        library.page(1); library.appendToPage(1, stroke(10));
        library.setPageTemplate(1, NotebookPage.Template.LINED); library.saveScrollPosition(1, 0.375);
        check(library.canUndoOnPage(1), "library exposes explicit-page history");
        library.eraseOnPagePaths(1, Collections.singletonList(Collections.singletonList(new NotebookPage.Point(10, 20, 100, 1))), 0);
        equal(0, library.cachedPage(1).strokes.size(), "library applies page-targeted erase");
        library.undoOnPage(1); equal(1, library.cachedPage(1).strokes.size(), "library applies targeted undo");
        check(library.canRedoOnPage(1), "targeted redo exposed");
        library.redoOnPage(1); equal(0, library.cachedPage(1).strokes.size(), "library applies targeted redo");
        library.undoOnPage(1);
        library.createNotebook("Other"); library.initializeSize(100, 120); library.addPage();
        library.page(1); library.appendToPage(1, stroke(70)); library.saveScrollPosition(2, 0.75);
        library.openNotebook(1);
        equal(1, library.scrollPosition().page, "first notebook anchor page retained");
        check(library.scrollPosition().fraction == 0.375, "first notebook fraction retained"); firstX(10, library.page(1));
        equal(2, library.pageNumber(), "targeted viewport operations keep legacy selected page independent");
        library.openNotebook(2); firstX(70, library.page(1));
        check(library.scrollPosition().page == 2 && library.scrollPosition().fraction == 0.75, "second notebook has its own anchor");
        NotebookLibrary reopened = reopen(root);
        check(reopened.scrollPosition().page == 2 && reopened.scrollPosition().fraction == 0.75, "restart restores selected notebook anchor");
    }

    private static NotebookLibrary ready(File root) throws Exception {
        NotebookLibrary library = new NotebookLibrary(root); library.loadCatalog(); library.load(); library.initializeSize(100, 120); return library;
    }
    private static NotebookLibrary reopen(File root) throws Exception {
        NotebookLibrary library = new NotebookLibrary(root); library.loadCatalog(); library.load(); return library;
    }
    private static NotebookPage.Stroke stroke(float x) {
        return new NotebookPage.Stroke(2, 4, 0xff000000, Collections.singletonList(new NotebookPage.Point(x, 20, 100, 1)));
    }
    private static void firstX(float expected, NotebookPage page) { check(page.strokes.get(0).points.get(0).x == expected, "correct book stroke"); }
    private static File directory() throws IOException {
        File directory = Files.createTempDirectory("notebook-library-").toFile(); directories.add(directory); return directory;
    }
    private static byte[] bytes(File root, String path) throws IOException { return Files.readAllBytes(new File(root, path).toPath()); }
    private static void block(File root, String path) { check(new File(root, path).mkdir(), "create filesystem obstruction"); }
    private static void unblock(File root, String path) { check(new File(root, path).delete(), "remove filesystem obstruction"); }
    private static void equal(int expected, int actual, String message) { check(expected == actual, message + " (expected " + expected + ", got " + actual + ")"); }
    private static void equal(String expected, String actual, String message) { check(expected.equals(actual), message); }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void expectIo(Action action) throws Exception {
        try { action.run(); } catch (IOException expected) { assertions++; return; } throw new AssertionError("Expected IOException");
    }
    private static void deleteTree(File file) { File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child); file.delete(); }
}
