package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Real-file tests for optimistic editing, history lifetime, and exact-state error recovery. */
public final class NotebookDocumentTest {
    private static int assertions;
    private static final List<File> directories = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        try {
            editingAndHistoryLifetime();
            failedEditRetainsQueuedDrawing();
            ambiguousCompletedTargetIsNotDuplicated();
            interruptedTailMatchesAcknowledgedState();
            unexpectedDiskStatesFailClosed();
            initialLoadRequiresExplicitRetry();
            initializationFailureRetainsCompletedDrawing();
            validationErrorsUseIOException();
            cachedValidationReadsWithoutPublishing();
            groupedAppendIsOneDurableHistoryCommand();
            groupedAppendFailureRetainsEveryPiece();
            System.out.println("NotebookDocument: " + assertions + " assertions passed (host JVM, real files).");
        } finally {
            for (File directory : directories) deleteTree(directory);
        }
    }

    private static void groupedAppendIsOneDurableHistoryCommand() throws Exception {
        File directory = directory(); NotebookDocument document = ready(directory);
        document.append(stroke(1));
        document.appendAll(Arrays.asList(stroke(10), stroke(40)));
        equal(3, saved(directory).strokes.size(), "grouped pen pieces all saved");
        check(document.undo(), "one undo removes grouped pen pieces");
        equal(1, saved(directory).strokes.size(), "grouped undo preserves prior writing on disk");
        byte[] before = bytes(directory);
        document.appendAll(Collections.<NotebookPage.Stroke>emptyList());
        check(document.canRedo(), "empty append group preserves redo");
        check(Arrays.equals(before, bytes(directory)), "empty append group does not write disk");
        expectIo(() -> document.appendAll(Arrays.asList(stroke(70), null)));
        equal(1, document.current().strokes.size(), "invalid later piece does not accept earlier piece");
        check(document.canRedo(), "invalid group preserves redo history");
        check(Arrays.equals(before, bytes(directory)), "invalid group preserves disk bytes");
        document.retry();
        check(document.redo(), "redo remains available after validation retry");
        equal(3, saved(directory).strokes.size(), "one redo saves complete group");
    }

    private static void groupedAppendFailureRetainsEveryPiece() throws Exception {
        for (int variant = 0; variant < 3; variant++) {
            File directory = directory(); NotebookDocument document = ready(directory);
            document.append(stroke(1));
            // Deny precisely the second journal open. This leaves a real, valid first-piece
            // prefix on disk without adding a production-only fault injection API.
            denySecondJournalWrite(directory, () -> document.appendAll(Arrays.asList(stroke(10), stroke(40), stroke(70))));
            equal(4, document.current().strokes.size(), "entire failed pen group retained in memory");
            equal(2, saved(directory).strokes.size(), "first grouped piece acknowledged before later write fails");
            if (variant == 2) {
                NotebookStore external = new NotebookStore(directory); external.load(); external.appendStroke(stroke(70));
                byte[] unexpected = bytes(directory);
                expectIo(document::retry);
                check(Arrays.equals(unexpected, bytes(directory)), "unexpected same-length group prefix is preserved");
                equal(4, document.current().strokes.size(), "refused retry retains complete intended group");
                continue;
            }
            if (variant == 1) {
                // Simulate the second piece completing but its acknowledgement being lost.
                NotebookStore external = new NotebookStore(directory); external.load(); external.appendStroke(stroke(40));
            }
            expectIo(() -> document.appendAll(Arrays.asList(stroke(80), stroke(90))));
            equal(6, document.current().strokes.size(), "later queued pen group retained after earlier failure");
            document.retry();
            NotebookPage result = saved(directory);
            equal(6, result.strokes.size(), "retry saves all groups without duplicating durable pieces");
            int[] expected = {1, 10, 40, 70, 80, 90};
            for (int i = 0; i < expected.length; i++) firstX(expected[i], result, i);
            check(document.undo(), "undo later queued gesture after retry");
            equal(4, saved(directory).strokes.size(), "one undo removes entire later group");
            check(document.undo(), "undo original failed gesture after retry");
            equal(1, saved(directory).strokes.size(), "one undo removes original complete group including durable prefix");
            check(document.redo() && document.redo(), "redo each complete recovered gesture");
            equal(6, saved(directory).strokes.size(), "group history remains complete after recovery");
        }
    }

    private static void denySecondJournalWrite(File directory, Task task) throws Exception {
        final String journal = new File(directory, "page.nbk").getAbsolutePath();
        SecurityManager previous = System.getSecurityManager();
        System.setSecurityManager(new SecurityManager() {
            private int writes;
            @Override public void checkPermission(java.security.Permission permission) { }
            @Override public void checkWrite(String file) {
                if (journal.equals(file) && ++writes == 2) throw new SecurityException("Simulated second-piece write failure");
            }
        });
        try { expectIo(task); }
        finally { System.setSecurityManager(previous); }
    }

    private static void cachedValidationReadsWithoutPublishing() throws Exception {
        File directory = directory(); NotebookDocument document = ready(directory); document.append(stroke(10));
        document.append(stroke(40)); document.undo();
        byte[] primary = bytes(directory), backup = Files.readAllBytes(new File(directory, "page.nbk.bak").toPath());
        document.validateDisk();
        check(Arrays.equals(primary, bytes(directory)), "cached validation preserves journal bytes");
        check(Arrays.equals(backup, Files.readAllBytes(new File(directory, "page.nbk.bak").toPath())), "cached validation preserves backup");
        check(!new File(directory, "page.nbk.tmp").exists(), "cached validation creates no snapshot");
        check(document.canRedo(), "cached validation preserves editing history");
        NotebookStore external = new NotebookStore(directory); external.load(); external.appendStroke(stroke(70));
        byte[] changed = bytes(directory); expectIo(document::validateDisk);
        check(Arrays.equals(changed, bytes(directory)), "cached validation refuses unexpected disk without writing");
        equal(1, document.current().strokes.size(), "cached validation retains original open snapshot");
    }

    private static void editingAndHistoryLifetime() throws Exception {
        File directory = directory();
        NotebookDocument document = ready(directory);
        document.append(stroke(10));
        document.append(stroke(40));
        check(document.canUndo(), "new writing has undo history");
        check(document.erase(path(10), 1), "erase hits first stroke");
        check(document.current().strokes.get(0).points.get(0).x == 40, "erase keeps second stroke");
        equal(1, saved(directory).strokes.size(), "erase durably publishes deletion");
        check(document.undo(), "undo erase");
        equal(2, saved(directory).strokes.size(), "undo persisted");
        check(document.canRedo(), "undo creates redo");
        document.setTemplate(NotebookPage.Template.LINED);
        document.load();
        check(document.canRedo(), "load barrier retains redo after activity recreation");
        check(document.current().template == NotebookPage.Template.LINED, "paper selection retained");
        check(document.redo(), "redo erase survives load barrier");
        byte[] bytes = bytes(directory);
        check(!document.erase(path(90), 0), "no-hit erase is no-op");
        check(!document.redo(), "empty redo is no-op");
        check(Arrays.equals(bytes, bytes(directory)), "no-op editing leaves disk bytes unchanged");
        NotebookDocument reopened = new NotebookDocument(directory);
        reopened.load();
        equal(1, reopened.current().strokes.size(), "process restart restores deletion");
        check(!reopened.canUndo() && !reopened.canRedo(), "new process starts new history");
        document.retry();
        check(document.canUndo(), "successful retry preserves history");
    }

    private static void failedEditRetainsQueuedDrawing() throws Exception {
        File directory = directory();
        NotebookDocument document = twoStrokes(directory);
        byte[] committed = bytes(directory);
        blockTemporary(directory);
        expectIo(() -> document.erase(path(10), 1));
        equal(1, document.current().strokes.size(), "failed publication retains optimistic deletion");
        check(document.error() != null, "failed edit exposes storage problem");
        expectIo(() -> document.append(stroke(70)));
        equal(2, document.current().strokes.size(), "queued completed drawing remains in memory");
        expectIo(() -> document.setTemplate(NotebookPage.Template.LINED));
        check(document.current().template == NotebookPage.Template.LINED, "paper changes retained after error");
        expectIo(() -> document.load());
        equal(2, document.current().strokes.size(), "load barrier never replaces unsaved page");
        check(Arrays.equals(committed, bytes(directory)), "no later disk writes while failed");
        unblockTemporary(directory);
        document.retry();
        check(document.error() == null, "retry clears problem after saving all changes");
        NotebookPage page = saved(directory);
        equal(2, page.strokes.size(), "retry saves exact optimistic stroke count");
        firstX(40, page, 0);
        firstX(70, page, 1);
        check(page.template == NotebookPage.Template.LINED, "retry saves retained paper change");
        check(document.undo(), "history remains usable after retry");
        equal(1, saved(directory).strokes.size(), "undo after retry saves correct previous state");
        check(document.redo(), "redo remains usable after retry");
        equal(2, saved(directory).strokes.size(), "redo after retry persists");
    }

    private static void ambiguousCompletedTargetIsNotDuplicated() throws Exception {
        File directory = directory();
        NotebookDocument document = twoStrokes(directory);
        blockTemporary(directory);
        expectIo(() -> document.erase(path(10), 1));
        unblockTemporary(directory);
        // Simulate the write completing but its acknowledgement being lost.
        NotebookStore external = new NotebookStore(directory);
        external.load();
        external.replaceStrokes(Collections.singletonList(stroke(40)));
        expectIo(() -> document.append(stroke(70)));
        document.retry();
        NotebookPage page = saved(directory);
        equal(2, page.strokes.size(), "ambiguous target accepted without duplicate drawing");
        firstX(40, page, 0);
        firstX(70, page, 1);
        check(document.undo(), "ambiguous recovery retains undo history");
        equal(1, saved(directory).strokes.size(), "undo returns exact accepted deletion state");
    }

    private static void interruptedTailMatchesAcknowledgedState() throws Exception {
        File directory = directory();
        NotebookDocument document = twoStrokes(directory);
        blockTemporary(directory);
        expectIo(() -> document.erase(path(10), 1));
        unblockTemporary(directory);
        Files.write(new File(directory, "page.nbk").toPath(), new byte[] {0, 0, 0}, StandardOpenOption.APPEND);
        document.retry();
        equal(1, saved(directory).strokes.size(), "interrupted append tail may recover known exact predecessor");
        firstX(40, saved(directory), 0);
    }

    private static void unexpectedDiskStatesFailClosed() throws Exception {
        for (int variant = 0; variant < 5; variant++) {
            File directory = directory();
            NotebookDocument document = twoStrokes(directory);
            blockTemporary(directory);
            expectIo(() -> document.erase(path(10), 1));
            unblockTemporary(directory);
            NotebookStore external = new NotebookStore(directory);
            external.load();
            if (variant == 0) {
                // A valid prefix of the old page is neither its predecessor nor the attempted edit.
                external.replaceStrokes(Collections.singletonList(stroke(10)));
            } else if (variant == 1) {
                external.setTemplate(NotebookPage.Template.LINED);
            } else if (variant == 2) {
                NotebookPage.Stroke changed = new NotebookPage.Stroke(2, 5, 0xff000000, stroke(40).points);
                external.replaceStrokes(Collections.singletonList(changed));
            } else if (variant == 3) {
                NotebookPage.Stroke changed = new NotebookPage.Stroke(2, 4, 0xff000000,
                    Collections.singletonList(new NotebookPage.Point(40, 10, 123456, 1)));
                external.replaceStrokes(Collections.singletonList(changed));
            } else {
                byte[] corrupt = bytes(directory);
                corrupt[0] ^= 1;
                Files.write(new File(directory, "page.nbk").toPath(), corrupt);
            }
            byte[] unexpected = bytes(directory);
            expectIo(() -> document.retry());
            check(Arrays.equals(unexpected, bytes(directory)), "unexpected/corrupt state preserved " + variant);
            equal(1, document.current().strokes.size(), "unsaved deletion retained on refused retry " + variant);
            firstX(40, document.current(), 0);
            check(document.canUndo(), "history retained on refused retry " + variant);
        }
    }

    private static void initialLoadRequiresExplicitRetry() throws Exception {
        File directory = directory();
        File primary = new File(directory, "page.nbk");
        Files.write(primary.toPath(), new byte[] {1, 2, 3});
        NotebookDocument document = new NotebookDocument(directory);
        expectIo(() -> document.load());
        check(document.current() == null, "failed initial load does not create blank replacement");
        check(primary.delete(), "remove test corrupt file");
        expectIo(() -> document.load());
        check(document.current() == null, "load barrier does not silently retry initial failure");
        document.retry();
        check(document.current() != null && !document.current().isInitialized(), "explicit retry loads new page");
        document.initializeSize(100, 100);
        document.append(stroke(10));
        equal(1, saved(directory).strokes.size(), "writing possible after initial-load recovery");
    }

    private static void initializationFailureRetainsCompletedDrawing() throws Exception {
        File directory = directory();
        NotebookDocument document = new NotebookDocument(directory);
        document.load();
        blockTemporary(directory);
        expectIo(() -> document.initializeSize(100, 100));
        check(document.current().isInitialized(), "failed initialization retains logical page size");
        expectIo(() -> document.append(stroke(10)));
        expectIo(() -> document.setTemplate(NotebookPage.Template.LINED));
        unblockTemporary(directory);
        document.retry();
        NotebookPage page = saved(directory);
        equal(100, page.width, "retry publishes initialized width");
        equal(1, page.strokes.size(), "retry saves drawing queued behind failed initialization");
        check(page.template == NotebookPage.Template.LINED, "retry preserves queued template change");
    }

    private static void validationErrorsUseIOException() throws Exception {
        NotebookDocument document = ready(directory());
        expectIo(() -> document.append(null));
        check(document.error() != null && document.error().getCause() instanceof IllegalArgumentException,
            "runtime validation error is exposed as IOException");
        equal(0, document.current().strokes.size(), "invalid input leaves page unchanged");
        document.retry();
        document.append(stroke(10));
        equal(1, document.current().strokes.size(), "retry resumes writing after validation error");
    }

    private static NotebookDocument ready(File directory) throws IOException {
        NotebookDocument document = new NotebookDocument(directory);
        document.load();
        document.initializeSize(100, 100);
        return document;
    }

    private static NotebookDocument twoStrokes(File directory) throws IOException {
        NotebookDocument document = ready(directory);
        document.append(stroke(10));
        document.append(stroke(40));
        return document;
    }

    private static NotebookPage.Stroke stroke(int x) {
        return new NotebookPage.Stroke(2, 4, 0xff000000, Arrays.asList(
            new NotebookPage.Point(x, 10, 1000, 1), new NotebookPage.Point(x + 5, 20, 1005, 0.5f)));
    }

    private static List<NotebookPage.Point> path(int x) {
        return Collections.singletonList(new NotebookPage.Point(x, 10, 1000, 1));
    }

    private static NotebookPage saved(File directory) throws IOException { return new NotebookStore(directory).load(); }
    private static byte[] bytes(File directory) throws IOException { return Files.readAllBytes(new File(directory, "page.nbk").toPath()); }
    private static void firstX(int x, NotebookPage page, int index) {
        check(page.strokes.get(index).points.get(0).x == x, "expected stroke at x=" + x);
    }
    private static File directory() throws IOException {
        File directory = Files.createTempDirectory("notebook-document-test-").toFile();
        directories.add(directory);
        return directory;
    }
    private static void blockTemporary(File directory) throws IOException {
        File blocker = new File(directory, "page.nbk.tmp");
        check(blocker.mkdir(), "create blocked temporary directory");
        Files.write(new File(blocker, "occupied").toPath(), new byte[] {1});
    }
    private static void unblockTemporary(File directory) throws IOException { deleteTree(new File(directory, "page.nbk.tmp")); }
    private interface Task { void run() throws Exception; }
    private static void expectIo(Task task) throws Exception {
        assertions++;
        try { task.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Expected IOException");
    }
    private static void equal(int expected, int actual, String label) { check(expected == actual, label + ": " + actual); }
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
    private static void deleteTree(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        if (!file.delete() && file.exists()) throw new IOException("Cannot remove test file " + file);
    }
}
