package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Real-file tests of page isolation, process-local history, and interrupted navigation. */
public final class NotebookBookTest {
    private interface Action { void run() throws Exception; }
    private static int assertions;
    private static final List<File> directories = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        try {
            legacyUpgradePreservesBytes();
            independentPagesAndHistory();
            serializedRapidOperations();
            failedIndexPublicationAndRetry();
            ambiguousIndexPublicationAndRetry();
            corruptTargetNeverChangesSelection();
            changedCachedTargetIsNotOverwritten();
            missingTargetNeverBecomesBlank();
            failedPageCreationAndRetry();
            failedCurrentEditRetainsQueuedInk();
            unexpectedIndexFailsClosed();
            orphanPageIsPreserved();
            interruptedBlankCreationIsAdopted();
            initialLoadRetry();
            invalidInitialTargetCanBeRepaired();
            System.out.println("NotebookBook: " + assertions + " assertions passed (host JVM, real files).");
        } finally { for (File directory : directories) deleteTree(directory); }
    }

    private static void legacyUpgradePreservesBytes() throws Exception {
        File root = directory();
        NotebookDocument legacy = new NotebookDocument(root);
        legacy.load(); legacy.initializeSize(1404, 1688);
        legacy.setTemplate(NotebookPage.Template.LINED); legacy.append(stroke(10));
        byte[] original = bytes(root, "page.nbk");
        NotebookBook book = new NotebookBook(root); book.load();
        equal(1, book.pageNumber(), "legacy selection"); equal(1, book.pageCount(), "legacy count");
        equal(1, book.current().strokes.size(), "legacy strokes restored");
        check(!new File(root, "book.nbi").exists(), "opening legacy page creates no index");
        book.addPage();
        equal(2, book.pageCount(), "added second page"); equal(2, book.pageNumber(), "new page selected");
        equal(0, book.current().strokes.size(), "new page has no copied strokes");
        equal(1404, book.current().width, "new page inherits logical width");
        equal(1688, book.current().height, "new page inherits logical height");
        check(book.current().template == NotebookPage.Template.LINED, "new page inherits paper");
        book.selectPage(1); book.selectPage(2); book.selectPage(1);
        check(Arrays.equals(original, bytes(root, "page.nbk")), "upgrade/navigation preserve legacy bytes exactly");
        NotebookBook reopened = reopen(root);
        equal(1, reopened.pageNumber(), "restart selects last page");
        equal(2, reopened.pageCount(), "restart restores count");
        equal(1, reopened.current().strokes.size(), "restart restores original writing");
    }

    private static void independentPagesAndHistory() throws Exception {
        File root = directory(); NotebookBook book = ready(root);
        book.append(stroke(10)); book.append(stroke(30)); book.undo();
        check(book.canRedo(), "first page has redo");
        book.addPage(); check(!book.canUndo() && !book.canRedo(), "new page has own empty history");
        book.setTemplate(NotebookPage.Template.LINED); book.append(stroke(70));
        book.erase(path(70), 1); equal(0, book.current().strokes.size(), "erase second page");
        book.selectPage(1); check(book.canRedo(), "first page redo retained");
        check(book.current().template == NotebookPage.Template.BLANK, "first paper unaffected");
        book.redo(); equal(2, book.current().strokes.size(), "redo belongs to first page");
        book.selectPage(2); check(book.current().template == NotebookPage.Template.LINED, "second paper retained");
        book.undo(); equal(1, book.current().strokes.size(), "undo restores second page erased stroke");
        firstX(70, book.current()); book.load(); check(book.canRedo(), "load barrier keeps history");
        NotebookBook reopened = reopen(root);
        equal(2, reopened.pageNumber(), "last selected page reopened"); firstX(70, reopened.current());
        check(!reopened.canUndo() && !reopened.canRedo(), "new process has no old history");
        reopened.selectPage(1); equal(2, reopened.current().strokes.size(), "other page persisted independently");
    }

    private static void serializedRapidOperations() throws Exception {
        File root = directory(); NotebookBook book = ready(root);
        for (int page = 1; page <= 12; page++) {
            equal(page, book.pageNumber(), "serial operation selected expected page");
            book.append(stroke(page));
            book.setTemplate(page % 2 == 0 ? NotebookPage.Template.LINED : NotebookPage.Template.BLANK);
            if (page < 12) book.addPage();
        }
        for (int page = 12; page >= 1; page--) {
            book.selectPage(page); firstX(page, book.current());
            check(book.current().template == (page % 2 == 0 ? NotebookPage.Template.LINED : NotebookPage.Template.BLANK),
                "serialized paper change stays on its page");
        }
        NotebookBook reopened = reopen(root); equal(12, reopened.pageCount(), "all serial additions persisted");
    }

    private static void failedIndexPublicationAndRetry() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.append(stroke(10));
        block(root, "book.nbi.tmp"); expectIo(book::addPage);
        equal(1, book.pageNumber(), "failed new index leaves old visible page");
        equal(1, book.pageCount(), "unpublished page excluded from visible count"); firstX(10, book.current());
        check(new File(root, "pages/2/page.nbk").isFile(), "new page durable before index publication");
        check(book.error() != null, "index error exposed");
        byte[] old = bytes(root, "page.nbk"); expectIo(() -> book.append(stroke(99)));
        check(Arrays.equals(old, bytes(root, "page.nbk")), "index failure prevents further writes");
        unblock(root, "book.nbi.tmp"); book.retry();
        equal(2, book.pageNumber(), "retry completes intended navigation"); equal(2, book.pageCount(), "retry publishes count once");
        equal(0, book.current().strokes.size(), "retry does not copy old strokes");
        check(book.error() == null, "retry clears index error");
        book.selectPage(1); firstX(10, book.current());
    }

    private static void ambiguousIndexPublicationAndRetry() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.addPage(); book.selectPage(1);
        block(root, "book.nbi.tmp"); expectIo(() -> book.selectPage(2)); unblock(root, "book.nbi.tmp");
        NotebookIndex external = new NotebookIndex(root); external.load(); external.save(2, 2);
        book.retry(); equal(2, book.pageNumber(), "ambiguously completed exact target accepted");
        equal(2, book.pageCount(), "ambiguous retry does not add duplicate page");
    }

    private static void corruptTargetNeverChangesSelection() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.append(stroke(10)); book.addPage();
        book.append(stroke(70)); book.selectPage(1);
        byte[] valid = bytes(root, "pages/2/page.nbk"); byte[] corrupt = valid.clone(); corrupt[0] ^= 1;
        Files.write(new File(root, "pages/2/page.nbk").toPath(), corrupt);
        expectIo(() -> book.selectPage(2)); equal(1, book.pageNumber(), "corrupt target leaves old selection");
        firstX(10, book.current()); expectIo(book::retry);
        check(Arrays.equals(corrupt, bytes(root, "pages/2/page.nbk")), "corrupt target bytes preserved");
        equal(1, reopen(root).pageNumber(), "failed navigation does not persist new selection");
        Files.write(new File(root, "pages/2/page.nbk").toPath(), valid); book.retry();
        equal(2, book.pageNumber(), "fixed target allows intended navigation"); firstX(70, book.current());
    }

    private static void changedCachedTargetIsNotOverwritten() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.append(stroke(10)); book.addPage();
        book.append(stroke(70)); book.selectPage(1);
        NotebookStore external = new NotebookStore(new File(root, "pages/2")); external.load(); external.appendStroke(stroke(90));
        byte[] unexpected = bytes(root, "pages/2/page.nbk");
        expectIo(() -> book.selectPage(2)); expectIo(book::retry);
        equal(1, book.pageNumber(), "changed cached target cannot become writable");
        check(Arrays.equals(unexpected, bytes(root, "pages/2/page.nbk")), "unexpected cached target preserved");
    }

    private static void missingTargetNeverBecomesBlank() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.addPage(); book.selectPage(1);
        deleteTree(new File(root, "pages/2"));
        expectIo(() -> book.selectPage(2)); expectIo(book::retry);
        equal(1, book.pageNumber(), "missing page retains previous selection");
        check(!new File(root, "pages/2").exists(), "missing indexed page never recreated blank");
    }

    private static void failedPageCreationAndRetry() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.setTemplate(NotebookPage.Template.LINED);
        File pages = new File(root, "pages"); Files.write(pages.toPath(), new byte[] {9});
        expectIo(book::addPage); equal(1, book.pageCount(), "failed page creation does not publish count");
        check(!new File(root, "book.nbi").exists(), "page must exist durably before index");
        check(pages.delete(), "remove simulated unavailable parent"); book.retry();
        equal(2, book.pageNumber(), "page creation retry completes navigation");
        check(book.current().template == NotebookPage.Template.LINED, "retry preserves intended template");
        equal(0, book.current().strokes.size(), "retry creates empty page");
    }

    private static void failedCurrentEditRetainsQueuedInk() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.append(stroke(10)); book.append(stroke(30));
        block(root, "page.nbk.tmp"); expectIo(book::undo); expectIo(() -> book.append(stroke(70)));
        equal(2, book.current().strokes.size(), "queued completed stroke retained after failed edit");
        expectIo(book::addPage); equal(1, book.pageCount(), "failed current save blocks navigation");
        unblock(root, "page.nbk.tmp"); book.retry();
        equal(2, reopen(root).current().strokes.size(), "retry persists queued completed ink");
        book.addPage(); book.selectPage(1); equal(2, book.current().strokes.size(), "navigation retains recovered page");
    }

    private static void unexpectedIndexFailsClosed() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.addPage(); book.addPage(); book.selectPage(1);
        block(root, "book.nbi.tmp"); expectIo(() -> book.selectPage(2)); unblock(root, "book.nbi.tmp");
        NotebookIndex external = new NotebookIndex(root); external.load(); external.save(3, 3);
        byte[] unexpected = bytes(root, "book.nbi"); expectIo(book::retry);
        equal(1, book.pageNumber(), "unexpected valid selection does not change current page");
        check(Arrays.equals(unexpected, bytes(root, "book.nbi")), "unexpected index not overwritten");
    }

    private static void orphanPageIsPreserved() throws Exception {
        File root = directory(); NotebookBook book = ready(root);
        NotebookDocument orphan = new NotebookDocument(new File(root, "pages/2"));
        orphan.load(); orphan.initializeSize(100, 100); orphan.append(stroke(70));
        byte[] original = bytes(root, "pages/2/page.nbk"); expectIo(book::addPage); expectIo(book::retry);
        equal(1, book.pageCount(), "unindexed page not silently adopted");
        check(Arrays.equals(original, bytes(root, "pages/2/page.nbk")), "unindexed page not overwritten");
        deleteTree(new File(root, "pages/2")); book.retry(); equal(2, book.pageCount(), "explicitly removed orphan unblocks retry");
    }

    private static void initialLoadRetry() throws Exception {
        File root = directory(); NotebookBook original = ready(root); original.addPage();
        byte[] valid = bytes(root, "book.nbi"); byte[] bad = valid.clone(); bad[0] ^= 1;
        Files.write(new File(root, "book.nbi").toPath(), bad);
        NotebookBook book = new NotebookBook(root); expectIo(book::load); check(book.current() == null, "bad index shows no invented page");
        expectIo(book::load); check(Arrays.equals(bad, bytes(root, "book.nbi")), "load barrier leaves corrupt index intact");
        Files.write(new File(root, "book.nbi").toPath(), valid); book.retry(); equal(2, book.pageNumber(), "initial retry restores selected page");
    }

    private static void interruptedBlankCreationIsAdopted() throws Exception {
        File root = directory(); NotebookBook book = ready(root); book.setTemplate(NotebookPage.Template.LINED);
        NotebookStore orphan = new NotebookStore(new File(root, "pages/2"));
        orphan.load(); orphan.initializePage(100, 100, NotebookPage.Template.LINED);
        byte[] original = bytes(root, "pages/2/page.nbk");
        // Simulate a process dying after new-page sync and before book.nbi publication.
        book = reopen(root); book.addPage(); equal(2, book.pageNumber(), "exact interrupted blank adopted");
        check(Arrays.equals(original, bytes(root, "pages/2/page.nbk")), "adoption preserves already synced page bytes");
        equal(0, book.current().strokes.size(), "adopted page remains empty");
        check(book.current().template == NotebookPage.Template.LINED, "adopted paper matches original intent");
        check(!new File(root, "pages/2/page.nbk.bak").exists(), "new lined page has no intermediate blank publication");
        File interrupted = directory(); NotebookBook stopped = ready(interrupted); stopped.setTemplate(NotebookPage.Template.LINED);
        File target = new File(interrupted, "pages/2"); check(target.mkdirs(), "prepare interrupted new page directory");
        Files.write(new File(target, "page.nbk.tmp").toPath(), original);
        stopped = reopen(interrupted); stopped.addPage();
        equal(2, stopped.pageNumber(), "complete first temporary snapshot adopted after restart");
        check(stopped.current().template == NotebookPage.Template.LINED, "temporary new-page recovery retains lined paper");
        check(Arrays.equals(original, bytes(interrupted, "pages/2/page.nbk")), "temporary new-page recovery publishes exact first snapshot");
        for (int variant = 0; variant < 3; variant++) {
            File other = directory(); NotebookBook blocked = ready(other);
            NotebookDocument unexpected = new NotebookDocument(new File(other, "pages/2"));
            unexpected.load(); unexpected.initializeSize(variant == 0 ? 90 : 100, 100);
            if (variant == 1) unexpected.setTemplate(NotebookPage.Template.LINED);
            if (variant == 2) {
                byte[] bad = bytes(other, "pages/2/page.nbk"); bad[0] ^= 1;
                Files.write(new File(other, "pages/2/page.nbk").toPath(), bad);
            }
            byte[] preserved = bytes(other, "pages/2/page.nbk"); expectIo(blocked::addPage);
            check(Arrays.equals(preserved, bytes(other, "pages/2/page.nbk")), "unexpected blank orphan preserved");
            equal(1, blocked.pageCount(), "unexpected blank orphan excluded from index");
        }
    }

    private static void invalidInitialTargetCanBeRepaired() throws Exception {
        File root = directory(); NotebookBook original = ready(root); original.addPage();
        byte[] valid = bytes(root, "pages/2/page.nbk");
        // A valid-format but unsized snapshot is not a usable indexed page.
        NotebookStore replacement = new NotebookStore(new File(root, "replacement"));
        replacement.load(); replacement.setTemplate(NotebookPage.Template.LINED);
        Files.write(new File(root, "pages/2/page.nbk").toPath(), bytes(root, "replacement/page.nbk"));
        NotebookBook book = new NotebookBook(root); expectIo(book::load);
        check(book.error() != null, "unsized indexed page rejected");
        Files.write(new File(root, "pages/2/page.nbk").toPath(), valid);
        book.retry(); equal(2, book.pageNumber(), "repaired initial page selected");
        equal(100, book.current().width, "initial retry rereads repaired page dimensions");
    }

    private static NotebookBook ready(File root) throws Exception {
        NotebookBook book = reopen(root); book.initializeSize(100, 100); return book;
    }
    private static NotebookBook reopen(File root) throws Exception {
        NotebookBook book = new NotebookBook(root); book.load(); return book;
    }
    private static NotebookPage.Stroke stroke(float x) {
        return new NotebookPage.Stroke(2, 4, 0xff000000, path(x));
    }
    private static List<NotebookPage.Point> path(float x) {
        return Collections.singletonList(new NotebookPage.Point(x, 10, 100, 1));
    }
    private static File directory() throws IOException {
        File result = Files.createTempDirectory("notebook-book-test-").toFile(); directories.add(result); return result;
    }
    private static byte[] bytes(File root, String name) throws IOException { return Files.readAllBytes(new File(root, name).toPath()); }
    private static void block(File root, String name) throws IOException {
        File file = new File(root, name); if (file.exists()) check(file.delete(), "remove old temporary file");
        check(file.mkdir(), "block temporary path");
        Files.write(new File(file, "keep").toPath(), new byte[] {1});
    }
    private static void unblock(File root, String name) { deleteTree(new File(root, name)); }
    private static void deleteTree(File file) {
        File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
    private static void firstX(float expected, NotebookPage page) {
        check(!page.strokes.isEmpty() && page.strokes.get(0).points.get(0).x == expected, "expected page's writing is visible");
    }
    private static void expectIo(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected IOException"); } catch (IOException expected) { assertions++; }
    }
    private static void equal(int expected, int actual, String message) { check(expected == actual, message + " (" + actual + ")"); }
    private static void check(boolean result, String message) {
        assertions++; if (!result) throw new AssertionError(message);
    }
}
