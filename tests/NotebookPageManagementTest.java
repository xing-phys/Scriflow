package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.CRC32;

/** Real-file ordering/migration, interruption, identity, history, scroll and backup checks. */
public final class NotebookPageManagementTest {
    private interface Task { void run() throws Exception; }
    private static int assertions;
    private static final List<File> directories = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        try {
            moveWithoutRewritingInk(); duplicateInsertDeleteAndUndo(); singleRemainingPage();
            failedPublicationAndRetry(); abandonedAndUnexpectedSlots(); interruptedIndexRenames();
            invalidOperations(); indexValidation(); scrollGenerations(); backupAndLibrary(); randomizedOrder();
            System.out.println("NotebookPageManagement: " + assertions + " assertions passed (host JVM, real files).");
        } finally { for (File directory : directories) NotebookBackup.deleteTree(directory); }
    }

    private static void moveWithoutRewritingInk() throws Exception {
        File root = directory(); NotebookBook book = ready(root, 3);
        List<UUID> original = ids(book);
        Map<Integer, byte[]> ink = new HashMap<>();
        for (int slot = 1; slot <= 3; slot++) ink.put(slot, bytes(slot(root, slot), "page.nbk"));
        book.saveScrollPosition(3, 0.625);
        byte[] oldScroll = bytes(root, "scroll.nbs");
        book.managePages(NotebookBook.PageAction.MOVE, 1, 3);
        equal(Arrays.asList(original.get(1), original.get(2), original.get(0)), ids(book), "move final position");
        check(book.pageNumber() == 3, "moved page selected");
        for (int slot = 1; slot <= 3; slot++) check(Arrays.equals(ink.get(slot), bytes(slot(root, slot), "page.nbk")), "move never rewrites ink");
        NotebookIndex.State state = new NotebookIndex(root).load();
        equal(Arrays.asList(2, 3, 1), state.slots, "stable storage slot order");
        check(state.managed() && state.generation == 1, "first management edit upgrades index");
        check(Arrays.equals(oldScroll, bytes(root, "scroll.nbs")), "old scroll bytes preserved at publication");
        position(book.scrollPosition(), 3, 0, "new order resets to moved page top");
        NotebookBook reopened = new NotebookBook(root); reopened.load();
        equal(ids(book), ids(reopened), "reordered book reopens"); position(reopened.scrollPosition(), 3, 0, "old anchor cannot point past reordered content");
        book.undoOnPage(3); check(book.page(3).strokes.isEmpty(), "ink history follows moved page");
        check(book.page(1).strokes.size() == 1, "ink undo leaves other pages intact"); book.redoOnPage(3);
        book.appendToPage(1, stroke(75));
        check(new NotebookStore(slot(root, 2)).load().strokes.size() == 2, "writing after reorder targets correct slot");
        book.undoPageChange(); equal(original, ids(book), "separate page undo restores order");
        check(book.page(2).strokes.size() == 2, "page undo preserves later ink edits");
        check(new NotebookIndex(root).load().generation == 2, "undo advances scroll generation");
        check(book.canRedoPageChange(), "page redo available");
        book.redoPageChange(); equal(Arrays.asList(original.get(1), original.get(2), original.get(0)), ids(book), "redo page move");
        book.selectPage(1); check(book.current().id.equals(original.get(1)), "navigation after reorder uses correct page");
        book.undoPageChange(); equal(original, ids(book), "page undo after navigation");
        book.addPage(); check(book.pageCount() == 4 && book.pageNumber() == 4, "toolbar append works after management");
        check(!book.canRedoPageChange(), "new page edit clears page redo");
    }

    private static void duplicateInsertDeleteAndUndo() throws Exception {
        File root = directory(); NotebookBook book = ready(root, 3); List<UUID> original = ids(book);
        NotebookPage source = book.page(2); book.setPageTemplate(2, NotebookPage.Template.LINED); source = book.page(2);
        book.managePages(NotebookBook.PageAction.DUPLICATE, 2, 0);
        NotebookPage copy = book.page(3);
        check(!copy.id.equals(source.id), "duplicate fresh page ID");
        check(copy.template == source.template && copy.width == source.width && copy.height == source.height, "duplicate paper and dimensions");
        check(copy.strokes.size() == source.strokes.size(), "duplicate stroke count");
        for (int i = 0; i < source.strokes.size(); i++) {
            NotebookPage.Stroke a = source.strokes.get(i), b = copy.strokes.get(i);
            check(!a.id.equals(b.id), "duplicate fresh stroke ID");
            check(a.penType == b.penType && a.width == b.width && a.color == b.color, "duplicate style");
            for (int p = 0; p < a.points.size(); p++) {
                NotebookPage.Point x = a.points.get(p), y = b.points.get(p);
                check(x.x == y.x && x.y == y.y && x.time == y.time && x.pressure == y.pressure, "duplicate exact sample");
            }
        }
        book.appendToPage(3, stroke(60)); check(book.page(2).strokes.size() == 1, "duplicate independent of source");
        book.managePages(NotebookBook.PageAction.INSERT_BEFORE, 2, 0);
        check(book.pageNumber() == 2 && book.page(2).strokes.isEmpty() && book.page(2).template == NotebookPage.Template.LINED, "insert before uses source paper");
        UUID blank = book.page(2).id;
        book.managePages(NotebookBook.PageAction.DELETE, 1, 0);
        check(book.page(1).id.equals(blank), "delete first page shifts display positions");
        check(!ids(book).contains(original.get(0)), "deleted page absent");
        book.undoPageChange(); check(book.page(1).id.equals(original.get(0)), "undo restores deleted page");
        book.redoPageChange(); check(!ids(book).contains(original.get(0)), "redo deletes the restored page");
        book.undoPageChange(); check(book.page(1).id.equals(original.get(0)), "second undo restores exact deleted page");
        book.undoPageChange(); check(book.page(2).id.equals(original.get(1)), "undo blank insertion");
        book.undoPageChange(); equal(original, ids(book), "undo duplicate");
        book.managePages(NotebookBook.PageAction.INSERT_AFTER, 3, 0);
        check(book.pageNumber() == 4 && book.page(4).strokes.isEmpty(), "insert after last page");
        NotebookBook reopened = new NotebookBook(root); reopened.load();
        check(!reopened.canUndoPageChange(), "page history does not survive process restart");
    }

    private static void singleRemainingPage() throws Exception {
        File root = directory(); NotebookBook book = ready(root, 2);
        UUID survivor = book.page(2).id; book.saveScrollPosition(2, 0.9);
        book.managePages(NotebookBook.PageAction.DELETE, 1, 0);
        check(book.pageCount() == 1 && book.current().id.equals(survivor), "remaining page need not use root slot");
        NotebookBook reopened = new NotebookBook(root); reopened.load();
        check(reopened.current().id.equals(survivor), "single non-root page reopens");
        position(reopened.scrollPosition(), 1, 0, "deleted anchor resets safely");
        byte[] before = bytes(root, "book.nbi"); expectIo(() -> reopened.managePages(NotebookBook.PageAction.DELETE, 1, 0));
        check(Arrays.equals(before, bytes(root, "book.nbi")), "cannot delete final page"); reopened.retry();
        // A missing managed singleton must never be silently turned into a blank page.
        File target = new File(slot(root, 2), "page.nbk"); check(target.delete(), "remove synthetic survivor snapshot");
        File backup = new File(slot(root, 2), "page.nbk.bak"); if (backup.exists()) check(backup.delete(), "remove synthetic survivor backup");
        expectIo(() -> new NotebookBook(root).load()); check(!target.exists(), "missing managed singleton preserved");
    }

    private static void failedPublicationAndRetry() throws Exception {
        for (boolean ambiguous : new boolean[]{false, true}) {
            File root = directory(); NotebookBook book = ready(root, 2); List<UUID> original = ids(book);
            byte[] committed = bytes(root, "book.nbi"); block(root, "book.nbi.tmp");
            expectIo(() -> book.managePages(NotebookBook.PageAction.DUPLICATE, 1, 0));
            equal(original, ids(book), "failed order publication keeps visible book");
            check(Arrays.equals(committed, bytes(root, "book.nbi")), "failed index publication retains disk order");
            NotebookPage prepared = new NotebookStore(slot(root, 3)).load();
            unblock(root, "book.nbi.tmp");
            if (ambiguous) {
                NotebookIndex external = new NotebookIndex(root); NotebookIndex.State before = external.load();
                external.saveOrder(before.reordered(Arrays.asList(1, 3, 2), 2, 4)); external.sync();
            }
            book.retry(); check(book.pageCount() == 3 && book.page(2).id.equals(prepared.id), "retry adopts exact prepared page once");
            check(!slot(root, 4).exists(), "retry never makes a second duplicate");
            book.undoPageChange(); equal(original, ids(book), "failed/retried command has one undo");
        }
        File root = directory(); NotebookBook book = ready(root, 3); List<UUID> original = ids(book);
        block(root, "book.nbi.tmp"); expectIo(() -> book.managePages(NotebookBook.PageAction.MOVE, 1, 3)); unblock(root, "book.nbi.tmp");
        NotebookIndex external = new NotebookIndex(root); NotebookIndex.State state = external.load();
        external.saveOrder(state.reordered(Arrays.asList(3, 2, 1), 1, 4));
        byte[] unexpected = bytes(root, "book.nbi"); expectIo(book::retry);
        check(Arrays.equals(unexpected, bytes(root, "book.nbi")), "retry refuses unrelated page order");
        equal(original, ids(book), "refused retry keeps in-memory page order");
    }

    private static void abandonedAndUnexpectedSlots() throws Exception {
        File root = directory(); NotebookBook book = ready(root, 2);
        block(root, "book.nbi.tmp"); expectIo(() -> book.managePages(NotebookBook.PageAction.INSERT_AFTER, 1, 0));
        UUID orphan = new NotebookStore(slot(root, 3)).load().id; unblock(root, "book.nbi.tmp");
        NotebookBook restarted = new NotebookBook(root); restarted.load();
        check(restarted.pageCount() == 2, "uncommitted insert absent after restart");
        restarted.addPage();
        equal(Arrays.asList(1, 2, 4), new NotebookIndex(root).load().slots, "toolbar append skips staged legacy orphan");
        restarted.undoPageChange();
        restarted.managePages(NotebookBook.PageAction.DUPLICATE, 1, 0);
        equal(Arrays.asList(1, 5, 2), new NotebookIndex(root).load().slots, "new operation skips orphan and removed slots");
        check(new NotebookStore(slot(root, 3)).load().id.equals(orphan), "orphan never overwritten or adopted");
        restarted.undoPageChange(); restarted.managePages(NotebookBook.PageAction.INSERT_AFTER, 1, 0);
        check(new NotebookIndex(root).load().slots.contains(6), "removed storage IDs never reused");
    }

    private static void interruptedIndexRenames() throws Exception {
        File root = directory(); NotebookBook book = ready(root, 2); List<UUID> original = ids(book);
        book.managePages(NotebookBook.PageAction.MOVE, 1, 2); List<UUID> moved = ids(book);
        byte[] published = bytes(root, "book.nbi");
        Files.write(new File(root, "book.nbi.tmp").toPath(), published);
        check(new File(root, "book.nbi").delete(), "simulate interruption before publishing prepared index");
        NotebookBook beforeRename = new NotebookBook(root); beforeRename.load(); equal(original, ids(beforeRename), "backup before order publication remains valid");
        Files.write(new File(root, "book.nbi").toPath(), published);
        NotebookBook afterRename = new NotebookBook(root); afterRename.load(); equal(moved, ids(afterRename), "published order remains valid after restart");
    }

    private static void invalidOperations() throws Exception {
        File root = directory(); NotebookBook book = ready(root, 3);
        byte[] before = bytes(root, "book.nbi");
        for (int[] pair : new int[][]{{0, 1}, {4, 1}, {1, 0}, {1, 4}}) {
            expectIo(() -> book.managePages(NotebookBook.PageAction.MOVE, pair[0], pair[1])); book.retry();
            check(Arrays.equals(before, bytes(root, "book.nbi")), "invalid move preserves index");
        }
        book.managePages(NotebookBook.PageAction.MOVE, 2, 2);
        check(Arrays.equals(before, bytes(root, "book.nbi")), "same-position move does not publish or migrate");
        File full = directory(); NotebookIndex index = new NotebookIndex(full); index.load();
        for (int i = 2; i <= NotebookIndex.MAX_PAGES; i++) index.save(i, 1);
        NotebookStore store = new NotebookStore(full); store.load(); store.initializeSize(100, 100);
        NotebookBook maximum = new NotebookBook(full); maximum.load(); byte[] fullIndex = bytes(full, "book.nbi");
        expectIo(() -> maximum.managePages(NotebookBook.PageAction.DUPLICATE, 1, 0));
        check(Arrays.equals(fullIndex, bytes(full, "book.nbi")), "page limit enforced before creating files");
    }

    private static void indexValidation() throws Exception {
        File root = directory(); NotebookIndex index = new NotebookIndex(root); index.load(); index.save(2, 1);
        NotebookIndex.State old = index.current(); index.saveOrder(old.reordered(Arrays.asList(2, 1), 2, 3));
        byte[] good = bytes(root, "book.nbi");
        for (int i = 0; i < good.length; i++) {
            byte[] corrupt = good.clone(); corrupt[i] ^= 1; Files.write(new File(root, "book.nbi").toPath(), corrupt);
            expectIo(() -> new NotebookIndex(root).load());
            check(Arrays.equals(corrupt, bytes(root, "book.nbi")), "corrupt v2 index retained");
        }
        for (int length = 0; length < good.length; length++) {
            Files.write(new File(root, "book.nbi").toPath(), Arrays.copyOf(good, length)); expectIo(() -> new NotebookIndex(root).load());
        }
        for (byte[] invalid : new byte[][]{fixture(1, 3, 1, 2, 2), fixture(1, 3, 1, 0, 2),
                fixture(1, 3, 1, 3, 1), fixture(1, 3, 0, 1, 2), fixture(1, 3, -1, 1, 2), fixture(3, 3, 1, 1, 2)}) {
            Files.write(new File(root, "book.nbi").toPath(), invalid); expectIo(() -> new NotebookIndex(root).load());
        }
        Files.write(new File(root, "book.nbi").toPath(), good);
        NotebookIndex restored = new NotebookIndex(root); NotebookIndex.State layout = restored.load();
        check(layout.equals(index.current()) && layout.hashCode() == index.current().hashCode(), "v2 state value equality");
        restored.save(2, 1); check(restored.current().slots.equals(Arrays.asList(2, 1)) && restored.current().generation == 1, "navigation keeps order generation");
    }

    private static void scrollGenerations() throws Exception {
        File root = directory(); NotebookScrollStore legacy = new NotebookScrollStore(root); legacy.load(1); legacy.save(3, 0.5);
        byte[] old = bytes(root, "scroll.nbs");
        NotebookScrollStore current = new NotebookScrollStore(root, 1); position(current.load(2), 2, 0, "legacy anchor defaults after reorder");
        check(Arrays.equals(old, bytes(root, "scroll.nbs")), "mismatched scroll read is nonmutating");
        current.save(2, 0.25); position(new NotebookScrollStore(root, 1).load(1), 2, 0.25, "tagged scroll roundtrip");
        position(new NotebookScrollStore(root, 2).load(1), 1, 0, "new order ignores old ordinal anchor");
        byte[] good = bytes(root, "scroll.nbs");
        for (int i = 0; i < good.length; i++) {
            byte[] corrupt = good.clone(); corrupt[i] ^= 1; Files.write(new File(root, "scroll.nbs").toPath(), corrupt);
            expectIo(() -> new NotebookScrollStore(root, 1).load(1));
        }
    }

    private static void backupAndLibrary() throws Exception {
        File root = directory(); NotebookLibrary library = new NotebookLibrary(root); library.load(); library.initializeSize(100, 100);
        library.append(stroke(10)); UUID first = library.current().id;
        library.addPage(); library.append(stroke(20)); UUID second = library.current().id;
        library.managePages(NotebookBook.PageAction.MOVE, 1, 2);
        library.managePages(NotebookBook.PageAction.DUPLICATE, 1, 0);
        library.managePages(NotebookBook.PageAction.DELETE, 3, 0);
        check(library.pageCount() == 2 && !library.previewPage(1).id.equals(first), "library forwards page management");
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        NotebookBackup.write(archive, "Managed notebook", library.pageCount(), library::previewPage, () -> false);
        File staging = new File(directory(), "restored");
        NotebookBackup.Restored restored = NotebookBackup.read(new ByteArrayInputStream(archive.toByteArray()), staging, () -> false);
        NotebookBook copy = new NotebookBook(restored.directory); copy.load();
        check(copy.pageCount() == 2 && copy.page(1).id.equals(second), "backup exports current display order");
        check(copy.page(2).id.equals(library.previewPage(2).id), "backup preserves duplicated identity");
        library.createNotebook("Other notebook"); library.initializeSize(100, 100);
        check(library.pageCount() == 1 && !library.canUndoPageChange(), "other notebook order/history isolated");
        library.openNotebook(1); library.undoPageChange();
        check(library.pageCount() == 3 && library.previewPage(3).id.equals(first), "page history retained across notebook switches");
    }

    private static void randomizedOrder() throws Exception {
        File root = directory(); NotebookBook book = ready(root, 3); List<UUID> expected = ids(book);
        Deque<List<UUID>> undo = new ArrayDeque<>(); Random random = new Random(7162);
        for (int step = 0; step < 120; step++) {
            List<UUID> before = new ArrayList<>(expected); int n = expected.size(), source = random.nextInt(n) + 1;
            int action = random.nextInt(5);
            if (action == 0 && !undo.isEmpty()) { book.undoPageChange(); expected = undo.removeLast(); }
            else if (action == 1 && n > 1) {
                int target = random.nextInt(n) + 1;
                book.managePages(NotebookBook.PageAction.MOVE, source, target);
                if (source == target) continue;
                UUID id = expected.remove(source - 1); expected.add(target - 1, id); undo.addLast(before);
            } else if (action == 2 && n > 1) {
                book.managePages(NotebookBook.PageAction.DELETE, source, 0); expected.remove(source - 1); undo.addLast(before);
            } else if (n < 20) {
                book.managePages(NotebookBook.PageAction.DUPLICATE, source, 0);
                UUID created = book.page(source + 1).id; check(!expected.contains(created), "random duplicate new identity");
                expected.add(source, created); undo.addLast(before);
            } else continue;
            if (undo.size() > 50) undo.removeFirst();
            equal(expected, ids(book), "randomized displayed order");
            NotebookBook reopened = new NotebookBook(root); reopened.load(); equal(expected, ids(reopened), "randomized durable order");
            check(reopened.scrollPosition().page <= reopened.pageCount(), "randomized valid scroll anchor");
        }
    }

    private static NotebookBook ready(File root, int count) throws Exception {
        NotebookBook book = new NotebookBook(root); book.load(); book.initializeSize(100, 100); book.append(stroke(10));
        for (int i = 2; i <= count; i++) { book.addPage(); book.append(stroke(i * 10)); }
        return book;
    }
    private static NotebookPage.Stroke stroke(float x) {
        return new NotebookPage.Stroke(2, 4, 0xff000000, Arrays.asList(
            new NotebookPage.Point(x, 10, 100, 0.2f), new NotebookPage.Point(x + 2, 20, 120, 0.8f)));
    }
    private static List<UUID> ids(NotebookBook book) throws Exception {
        List<UUID> ids = new ArrayList<>(); for (int i = 1; i <= book.pageCount(); i++) ids.add(book.previewPage(i).id); return ids;
    }
    private static File slot(File root, int slot) { return slot == 1 ? root : new File(root, "pages/" + slot); }
    private static File directory() throws IOException { File dir = Files.createTempDirectory("notebook-pages-").toFile(); directories.add(dir); return dir; }
    private static byte[] bytes(File directory, String name) throws IOException { return Files.readAllBytes(new File(directory, name).toPath()); }
    private static void block(File root, String name) throws IOException {
        File dir = new File(root, name); check(dir.mkdir(), "create write obstruction"); Files.write(new File(dir, "keep").toPath(), new byte[]{1});
    }
    private static void unblock(File root, String name) { NotebookBackup.deleteTree(new File(root, name)); }
    private static byte[] fixture(int selected, int next, long generation, int... slots) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream(); DataOutputStream data = new DataOutputStream(body);
        data.writeInt(slots.length); data.writeInt(selected); data.writeInt(next); data.writeLong(generation);
        for (int slot : slots) data.writeInt(slot); data.flush(); byte[] payload = body.toByteArray(); CRC32 crc = new CRC32(); crc.update(payload);
        ByteArrayOutputStream file = new ByteArrayOutputStream(); data = new DataOutputStream(file);
        data.writeInt(0x4e424931); data.writeInt(2); data.writeInt(payload.length); data.writeInt(~payload.length); data.write(payload); data.writeInt((int) crc.getValue());
        return file.toByteArray();
    }
    private static void expectIo(Task task) throws Exception { try { task.run(); } catch (IOException expected) { assertions++; return; } throw new AssertionError("Expected IOException"); }
    private static void position(NotebookScrollStore.State state, int page, double fraction, String message) { check(state.page == page && state.fraction == fraction, message); }
    private static void equal(Object expected, Object actual, String message) { check(expected.equals(actual), message + ": " + actual); }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
