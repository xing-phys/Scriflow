package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** Real-file cross-page move, linked history and interrupted two-page publication checks. */
public final class NotebookInkTransferTest {
    private static final List<File> roots = new ArrayList<>();
    private static int assertions;
    private interface Task { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        try {
            preservesIdentityAndStyle(); linkedHistoryAndLaterEdits(); samePageAndInvalidTargets();
            pendingWriteRetainsQueuedInk(); restartCompletesPartialWrite(); unexpectedPagesFailClosed();
            corruptJournalFailsClosed(); completedAndStagedJournalsAreInert(); reorderedStorageSlots();
            chainedMovesAndDeletedPartner(); interruptedUndoAndRecoveredTemporary();
            duplicateIdentitiesNeverPublishIntent(); noArgumentHistoryUsesPairedGates();
            noArgumentHistoryKeepsBookSaveBarrier();
            System.out.println("NotebookInkTransfer: " + assertions + " assertions passed (real files, linked history and crash recovery).");
        } finally { for (File root : roots) NotebookBackup.deleteTree(root); }
    }

    private static void preservesIdentityAndStyle() throws Exception {
        File root = root(); NotebookBook book = ready(root);
        NotebookPage source = book.page(1), destination = book.page(2);
        NotebookPage.Stroke selected = source.strokes.get(0), sourceOther = source.strokes.get(1), destinationOther = destination.strokes.get(0);
        book.selectPage(1);
        NotebookLasso.Selection moved = book.moveSelection(1, 2, selection(source, selected), 95, 95);
        check(book.pageNumber() == 1, "move does not change the selected-page index");
        check(book.page(1).strokes.equals(Collections.singletonList(sourceOther)), "source unrelated strokes remain in order");
        check(book.page(2).strokes.get(0) == destinationOther && book.page(2).strokes.size() == 2, "destination unrelated strokes remain intact");
        check(moved.pageId.equals(destination.id) && moved.strokes.size() == 1, "selection follows destination identity");
        NotebookPage.Stroke after = moved.strokes.get(0);
        check(after.id.equals(selected.id) && after.penType == selected.penType && after.color == selected.color
            && bits(after.width, selected.width) && bits(after.captureScale, selected.captureScale) && bits(after.captureWidth, selected.captureWidth), "move preserves stroke identity and captured style");
        check(after.points.get(0).x == 80 && after.points.get(0).y == 90 && after.points.get(1).x == 100 && after.points.get(1).y == 100, "placement clamps the complete group at destination edges");
        for (int i = 0; i < after.points.size(); i++) check(after.points.get(i).time == selected.points.get(i).time
            && bits(after.points.get(i).pressure, selected.points.get(i).pressure), "raw timestamp/pressure retained at point " + i);
        check(book.page(1).id.equals(source.id) && book.page(1).width == source.width && book.page(1).template == source.template
            && book.page(2).id.equals(destination.id) && book.page(2).template == destination.template, "page identities, dimensions and paper survive move");
        NotebookBook cold = new NotebookBook(root); cold.load();
        check(cold.page(1).strokes.size() == 1 && cold.page(2).strokes.size() == 2, "move persists across restart without loss or duplication");
        check(!NotebookInkTransfer.pending(root), "completed transfer has no active journal");
    }

    private static void linkedHistoryAndLaterEdits() throws Exception {
        NotebookBook book = ready(root()); NotebookPage source = book.page(1);
        NotebookPage.Stroke selected = source.strokes.get(0);
        book.moveSelection(1, 2, selection(source, selected), 50, 50);
        check(book.canUndoOnPage(1) && book.canUndoOnPage(2), "both pages expose linked undo");
        book.appendToPage(2, stroke(70, 70));
        check(!book.canUndoOnPage(1) && book.canUndoOnPage(2), "later destination edit blocks linked source undo");
        check(!book.undoOnPage(1) && book.page(1).strokes.size() == 1 && book.page(2).strokes.size() == 3, "blocked linked undo changes neither page");
        check(book.undoOnPage(2) && book.canUndoOnPage(1), "undoing later edit unblocks linked action");
        check(book.undoOnPage(1), "undo can start on source page");
        check(book.page(1).strokes.get(0).id.equals(selected.id) && book.page(2).strokes.size() == 1, "linked undo restores both sides together");
        check(book.canRedoOnPage(1) && book.canRedoOnPage(2) && book.redoOnPage(2), "redo can start on destination page");
        check(book.page(1).strokes.size() == 1 && book.page(2).strokes.size() == 2, "linked redo reapplies both sides");
        check(book.undoOnPage(2), "linked undo also starts on destination");
        book.appendToPage(1, stroke(70, 80));
        check(!book.canRedoOnPage(2) && !book.redoOnPage(2), "new partner edit invalidates linked redo without overwriting ink");
        check(book.page(1).strokes.size() == 3 && book.page(2).strokes.size() == 1, "invalidated redo preserves new source writing");
    }

    private static void samePageAndInvalidTargets() throws Exception {
        File root = root(); NotebookBook book = ready(root()); NotebookPage source = book.page(1);
        NotebookPage.Stroke selected = source.strokes.get(0);
        NotebookLasso.Selection moved = book.moveSelection(1, 1, selection(source, selected), 50, 50);
        check(moved.pageId.equals(source.id) && book.page(1).strokes.size() == 2, "same-page move keeps page and count");
        check(book.undoOnPage(1) && book.page(1).strokes.get(0).id.equals(selected.id), "same-page move retains ordinary undo");
        NotebookStore first = new NotebookStore(root); first.load(); first.initializeSize(100, 100); first.appendStroke(stroke(10, 10));
        File second = new File(root, "pages/2"); NotebookStore small = new NotebookStore(second); small.load(); small.initializeSize(10, 10);
        NotebookIndex index = new NotebookIndex(root); index.load(); index.initializeImported(2);
        NotebookBook different = new NotebookBook(root); different.load();
        byte[] firstBytes = bytes(root, "page.nbk"), secondBytes = bytes(second, "page.nbk");
        expect(() -> different.moveSelection(1, 2, NotebookLasso.all(different.page(1)), 5, 5));
        check(Arrays.equals(firstBytes, bytes(root, "page.nbk")) && Arrays.equals(secondBytes, bytes(second, "page.nbk")), "oversized selection never clips or changes either page");
        check(!NotebookInkTransfer.pending(root), "invalid move publishes no journal");
    }

    private static void pendingWriteRetainsQueuedInk() throws Exception {
        File root = root(); NotebookBook book = ready(root); NotebookPage source = book.page(1);
        NotebookPage.Stroke selected = source.strokes.get(0), late = stroke(55, 65);
        File obstruction = new File(root, "page.nbk.tmp"); check(obstruction.mkdir(), "block source snapshot publication");
        expect(() -> book.moveSelection(1, 2, selection(source, selected), 50, 50));
        check(NotebookInkTransfer.pending(root), "failed second write keeps complete active journal");
        check(book.page(1).strokes.size() == 1 && book.page(2).strokes.size() == 2, "accepted in-memory transfer is coherent on both pages");
        check(new NotebookStore(root).load().strokes.size() == 2 && new NotebookStore(new File(root, "pages/2")).load().strokes.size() == 2, "receiving page is saved before outgoing ink is removed");
        expect(() -> book.appendToPage(1, late));
        check(book.page(1).strokes.size() == 2, "queued pen-UP is retained in memory while transfer is pending");
        check(obstruction.delete(), "unblock source publication"); book.retry();
        check(!NotebookInkTransfer.pending(root) && book.error() == null, "retry completes journal and clears save error");
        check(book.page(1).strokes.contains(late) && book.page(2).strokes.size() == 2, "retry preserves later queued ink and exact moved group");
        check(book.undoOnPage(1) && book.undoOnPage(2), "ordinary queued edit and linked move both remain undoable after retry");
        check(book.page(1).strokes.size() == 2 && book.page(2).strokes.size() == 1, "undo after failed transfer restores both original pages");
    }

    private static void restartCompletesPartialWrite() throws Exception {
        File root = root(); NotebookBook book = ready(root); NotebookPage source = book.page(1), destination = book.page(2);
        NotebookPage sourceAfter = source.withStrokes(Collections.singletonList(source.strokes.get(1)));
        List<NotebookPage.Stroke> receiving = new ArrayList<>(destination.strokes); receiving.add(source.strokes.get(0));
        NotebookPage destinationAfter = destination.withStrokes(receiving);
        NotebookInkTransfer.prepare(root, 1, 2, source, sourceAfter, destination, destinationAfter);
        NotebookStore target = new NotebookStore(new File(root, "pages/2")); target.load(); target.replaceStrokes(receiving);
        NotebookBook cold = new NotebookBook(root); cold.load();
        check(NotebookDocument.samePage(cold.page(1), sourceAfter) && NotebookDocument.samePage(cold.page(2), destinationAfter), "restart finishes destination-only publication exactly");
        check(!NotebookInkTransfer.pending(root) && !cold.canUndoOnPage(1) && !cold.canUndoOnPage(2), "restart retires journal and starts fresh process-local history");
        NotebookBook again = new NotebookBook(root); again.load();
        check(again.page(1).strokes.size() == 1 && again.page(2).strokes.size() == 2, "second reopen cannot replay the move twice");
    }

    private static void unexpectedPagesFailClosed() throws Exception {
        File root = root(); NotebookBook book = ready(root); NotebookPage source = book.page(1), destination = book.page(2);
        NotebookPage sourceAfter = source.withStrokes(Collections.singletonList(source.strokes.get(1)));
        List<NotebookPage.Stroke> receiving = new ArrayList<>(destination.strokes); receiving.add(source.strokes.get(0));
        NotebookInkTransfer.prepare(root, 1, 2, source, sourceAfter, destination, destination.withStrokes(receiving));
        NotebookStore external = new NotebookStore(root); external.load(); external.appendStroke(stroke(70, 20));
        byte[] sourceBytes = bytes(root, "page.nbk"), destinationBytes = bytes(new File(root, "pages/2"), "page.nbk");
        expect(() -> new NotebookBook(root).load());
        check(Arrays.equals(sourceBytes, bytes(root, "page.nbk")) && Arrays.equals(destinationBytes, bytes(new File(root, "pages/2"), "page.nbk")), "unexpected original prevents writing either participant");
        check(NotebookInkTransfer.pending(root), "unexpected original preserves recovery snapshots");
        Files.copy(new File(root, "ink-transfer.pending/source-before/page.nbk").toPath(), new File(root, "page.nbk").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        NotebookBook repaired = new NotebookBook(root); repaired.load();
        check(repaired.page(1).strokes.size() == 1 && repaired.page(2).strokes.size() == 2, "exact original repair allows recovery");
    }

    private static void corruptJournalFailsClosed() throws Exception {
        File root = root(); NotebookBook book = ready(root); NotebookPage source = book.page(1), destination = book.page(2);
        List<NotebookPage.Stroke> receiving = new ArrayList<>(destination.strokes); receiving.add(source.strokes.get(0));
        NotebookInkTransfer.prepare(root, 1, 2, source, source.withStrokes(Collections.singletonList(source.strokes.get(1))), destination, destination.withStrokes(receiving));
        File after = new File(root, "ink-transfer.pending/destination-after/page.nbk"); byte[] valid = Files.readAllBytes(after.toPath()); byte[] bad = valid.clone(); bad[bad.length - 1] ^= 1; Files.write(after.toPath(), bad);
        byte[] sourceBytes = bytes(root, "page.nbk"), destinationBytes = bytes(new File(root, "pages/2"), "page.nbk");
        expect(() -> new NotebookBook(root).load());
        check(Arrays.equals(sourceBytes, bytes(root, "page.nbk")) && Arrays.equals(destinationBytes, bytes(new File(root, "pages/2"), "page.nbk")), "corrupt journal fails before either original changes");
        Files.write(after.toPath(), valid); NotebookBook fixed = new NotebookBook(root); fixed.load();
        check(fixed.page(1).strokes.size() == 1 && fixed.page(2).strokes.size() == 2, "repaired journal completes intended move");
    }

    private static void completedAndStagedJournalsAreInert() throws Exception {
        File root = root(); NotebookBook book = ready(root); NotebookPage source = book.page(1), destination = book.page(2);
        NotebookPage sourceAfter = source.withStrokes(Collections.singletonList(source.strokes.get(1)));
        List<NotebookPage.Stroke> receiving = new ArrayList<>(destination.strokes); receiving.add(source.strokes.get(0));
        NotebookInkTransfer.prepare(root, 1, 2, source, sourceAfter, destination, destination.withStrokes(receiving));
        NotebookStore s = new NotebookStore(root); s.load(); s.replaceStrokes(sourceAfter.strokes);
        NotebookStore d = new NotebookStore(new File(root, "pages/2")); d.load(); d.replaceStrokes(receiving);
        File complete = new File(root, "ink-transfer.completed-" + UUID.randomUUID());
        check(new File(root, NotebookInkTransfer.PENDING).renameTo(complete), "simulate crash after atomic journal retirement");
        s.appendStroke(stroke(60, 15));
        File staged = new File(root, "ink-transfer.staging-" + UUID.randomUUID()); check(staged.mkdir(), "simulate incomplete pre-intent staging");
        Files.write(new File(staged, "incomplete").toPath(), new byte[]{1, 2});
        NotebookBook cold = new NotebookBook(root); cold.load();
        check(cold.page(1).strokes.size() == 2 && cold.page(2).strokes.size() == 2, "inert completed/staging journals never replay over later writing");
        check(!complete.exists() && !staged.exists(), "inert transaction copies are cleaned separately");
    }

    private static void reorderedStorageSlots() throws Exception {
        File root = root(); NotebookBook book = ready(root); NotebookPage source = book.page(1); UUID selected = source.strokes.get(0).id;
        book.managePages(NotebookBook.PageAction.MOVE, 1, 2);
        book.moveSelection(2, 1, selection(book.page(2), book.page(2).strokes.get(0)), 50, 50);
        check(book.page(2).strokes.size() == 1 && book.page(1).strokes.get(1).id.equals(selected), "move resolves display numbers to stable storage slots");
        book.managePages(NotebookBook.PageAction.MOVE, 1, 2);
        check(book.undoOnPage(1) && book.page(1).strokes.get(0).id.equals(selected) && book.page(2).strokes.size() == 1, "linked history follows stable slots through page reordering");
    }

    private static void chainedMovesAndDeletedPartner() throws Exception {
        File root = root(); NotebookBook book = ready(root); book.addPage();
        NotebookPage source = book.page(1); UUID id = source.strokes.get(0).id;
        NotebookLasso.Selection first = book.moveSelection(1, 2, selection(source, source.strokes.get(0)), 50, 50);
        book.moveSelection(2, 3, first, 50, 50);
        check(!book.canUndoOnPage(1) && !book.undoOnPage(1), "later cross-page move blocks its earlier partner history");
        check(book.undoOnPage(3) && book.undoOnPage(1), "chained moves unwind in reverse order from either receiving page");
        check(book.page(1).strokes.get(0).id.equals(id) && book.page(2).strokes.size() == 1 && book.page(3).strokes.isEmpty(), "unwinding chain restores exact ink ownership");
        check(!book.canRedoOnPage(3) && !book.redoOnPage(3), "later chained redo waits for its predecessor");
        check(book.redoOnPage(2) && book.redoOnPage(3), "linked chain replays in order");
        book.managePages(NotebookBook.PageAction.DELETE, 2, 0);
        check(!book.canUndoOnPage(2) && !book.undoOnPage(2), "deleted partner cannot be modified through an active page's linked history");
        book.undoPageChange();
        check(book.canUndoOnPage(3) && book.undoOnPage(3), "restoring deleted partner also restores safe linked undo");
    }

    private static void interruptedUndoAndRecoveredTemporary() throws Exception {
        File root = root(); NotebookBook book = ready(root); NotebookPage source = book.page(1);
        book.moveSelection(1, 2, selection(source, source.strokes.get(0)), 50, 50);
        File obstruction = new File(root, "pages/2/page.nbk.tmp"); check(obstruction.mkdir(), "block outgoing page during linked undo");
        expect(() -> book.undoOnPage(1));
        check(NotebookInkTransfer.pending(root), "interrupted undo retains its complete recovery intent");
        check(new NotebookStore(root).load().strokes.size() == 2 && new NotebookStore(new File(root, "pages/2")).load().strokes.size() == 2,
            "undo restores receiving page before removing outgoing ink");
        check(obstruction.delete(), "unblock linked undo"); book.retry();
        check(book.page(1).strokes.size() == 2 && book.page(2).strokes.size() == 1 && book.redoOnPage(2), "retry preserves linked redo after interrupted undo");

        File temporaryRoot = root(); NotebookBook original = ready(temporaryRoot);
        NotebookPage first = original.page(1), second = original.page(2);
        List<NotebookPage.Stroke> receiving = new ArrayList<>(second.strokes); receiving.add(first.strokes.get(0));
        NotebookPage firstAfter = first.withStrokes(Collections.singletonList(first.strokes.get(1)));
        NotebookInkTransfer.prepare(temporaryRoot, 1, 2, first, firstAfter, second, second.withStrokes(receiving));
        NotebookStore writer = new NotebookStore(temporaryRoot); writer.load(); writer.replaceStrokes(firstAfter.strokes);
        check(new File(temporaryRoot, "page.nbk.bak").delete(), "simulate absent source backup");
        check(new File(temporaryRoot, "page.nbk").renameTo(new File(temporaryRoot, "page.nbk.tmp")), "simulate recovered temporary-only after state");
        NotebookBook reopened = new NotebookBook(temporaryRoot); reopened.load();
        check(new File(temporaryRoot, "page.nbk").isFile() && !new File(temporaryRoot, "page.nbk.tmp").exists(), "journal recovery publishes a primary before dropping its final recovery copy");
        check(reopened.page(1).strokes.size() == 1 && reopened.page(2).strokes.size() == 2 && !NotebookInkTransfer.pending(temporaryRoot), "temporary-only recovery completes exactly once");
    }

    private static void duplicateIdentitiesNeverPublishIntent() throws Exception {
        File root = root(); NotebookBook book = ready(root); NotebookPage first = book.page(1), second = book.page(2);
        NotebookPage duplicate = NotebookPage.restored(first.id, second.width, second.height, second.paperSize,
            second.pdfPage, second.template, second.strokes, second.pointCount);
        try (FileOutputStream output = new FileOutputStream(new File(root, "pages/2/page.nbk"))) { NotebookStore.writeSnapshot(output, duplicate); }
        List<NotebookPage.Stroke> receiving = new ArrayList<>(duplicate.strokes); receiving.add(first.strokes.get(0));
        byte[] source = bytes(root, "page.nbk"), destination = bytes(new File(root, "pages/2"), "page.nbk");
        expect(() -> NotebookInkTransfer.prepare(root, 1, 2, first, first.withStrokes(Collections.singletonList(first.strokes.get(1))),
            duplicate, duplicate.withStrokes(receiving)));
        check(!NotebookInkTransfer.pending(root) && Arrays.equals(source, bytes(root, "page.nbk"))
            && Arrays.equals(destination, bytes(new File(root, "pages/2"), "page.nbk")), "ambiguous page identity never creates an unrecoverable intent or changes originals");
    }

    private static void noArgumentHistoryUsesPairedGates() throws Exception {
        NotebookBook book = ready(root()); NotebookPage source = book.page(1);
        book.moveSelection(1, 2, selection(source, source.strokes.get(0)), 50, 50); book.selectPage(1);
        book.appendToPage(2, stroke(65, 35));
        check(!book.canUndo() && !book.undo(), "no-argument undo refuses a linked move when its partner has later ink");
        check(book.page(1).strokes.size() == 1 && book.page(2).strokes.size() == 3, "blocked no-argument undo changes neither participant");
        book.undoOnPage(2);
        check(book.canUndo() && book.undo(), "no-argument undo applies both participants when unblocked");
        check(book.page(1).strokes.size() == 2 && book.page(2).strokes.size() == 1, "no-argument linked undo restores original ownership");
        check(book.canRedo() && book.redo() && book.page(1).strokes.size() == 1 && book.page(2).strokes.size() == 2,
            "no-argument redo reapplies both participants");
        book.undo(); book.appendToPage(1, stroke(65, 35)); book.selectPage(2);
        check(!book.canRedo() && !book.redo() && book.page(1).strokes.size() == 3 && book.page(2).strokes.size() == 1,
            "no-argument redo preserves new partner ink after linked redo is invalidated");
    }

    private static void noArgumentHistoryKeepsBookSaveBarrier() throws Exception {
        File root = root(); NotebookBook book = ready(root); File obstruction = new File(root, "book.nbi.tmp");
        check(obstruction.mkdir(), "block selected-page index publication");
        expect(() -> book.selectPage(1)); byte[] saved = bytes(new File(root, "pages/2"), "page.nbk");
        expect(book::undo);
        check(Arrays.equals(saved, bytes(new File(root, "pages/2"), "page.nbk")) && book.page(2).strokes.size() == 1,
            "no-argument history keeps the existing book-level save-error barrier");
        check(obstruction.delete(), "unblock selected-page index"); book.retry();
    }

    private static NotebookBook ready(File root) throws Exception {
        NotebookBook book = new NotebookBook(root); book.load(); book.initializeSize(100, 100);
        book.append(stroke(10, 20)); book.append(stroke(60, 70)); book.setTemplate(NotebookPage.Template.LINED);
        book.addPage(); book.setTemplate(NotebookPage.Template.BLANK); book.append(stroke(15, 15)); return book;
    }
    private static NotebookPage.Stroke stroke(float x, float y) {
        return new NotebookPage.Stroke(UUID.randomUUID(), 2, 4, 0xff000000,
            Arrays.asList(new NotebookPage.Point(x, y, 9007199254740993L, .25f), new NotebookPage.Point(x + 20, y + 10, 9007199254741021L, .875f)), 1.5f, 6f);
    }
    private static NotebookLasso.Selection selection(NotebookPage page, NotebookPage.Stroke stroke) { return new NotebookLasso.Selection(page.id, Collections.singletonList(stroke)); }
    private static boolean bits(float a, float b) { return Float.floatToIntBits(a) == Float.floatToIntBits(b); }
    private static File root() throws Exception { File root = Files.createTempDirectory("notebook-ink-move-").toFile(); roots.add(root); return root; }
    private static byte[] bytes(File root, String path) throws Exception { return Files.readAllBytes(new File(root, path).toPath()); }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static void expect(Task task) throws Exception { try { task.run(); } catch (IOException expected) { assertions++; return; } throw new AssertionError("Expected IOException"); }
}
