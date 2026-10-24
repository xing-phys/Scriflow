package org.eink.inkprobe.notebook;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/** Host checks for the page index using real files and independent version-1 fixtures. */
public final class NotebookIndexTest {
    private static int assertions;
    private static final List<File> directories = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        try {
            absentIndexPreservesLegacyPage();
            addSelectAndReload();
            interruptedPublicationRecoversConservatively();
            corruptionFailsClosedAtEveryByte();
            truncatedPrimaryNeverBecomesAnEmptyBook();
            checksummedInvalidValuesFailClosed();
            rejectedChangesPreserveAllFiles();
            failedPublicationAndSyncStopWrites();
            maximumPagesRemainReadable();
            System.out.println("NotebookIndex: " + assertions + " assertions passed (host JVM, real files).");
        } finally {
            for (File directory : directories) deleteTree(directory);
        }
    }

    private static void absentIndexPreservesLegacyPage() throws Exception {
        File directory = directory();
        byte[] legacy = {0, 1, 2, 3, 4};
        Files.write(new File(directory, "page.nbk").toPath(), legacy);
        NotebookIndex index = new NotebookIndex(directory);
        state(index.load(), 1, 1);
        index.sync();
        equal(1, directory.list().length, "load and sync do not create an index for a legacy page");
        check(Arrays.equals(legacy, bytes(directory, "page.nbk")), "legacy bytes untouched on load");
        index.save(1, 1);
        equal(28, bytes(directory, "book.nbi").length, "initial snapshot published");
        check(Arrays.equals(legacy, bytes(directory, "page.nbk")), "legacy bytes untouched on index save");
        state(new NotebookIndex(directory).load(), 1, 1);
        File missing = new File(directory, "not-created");
        new NotebookIndex(missing).load();
        check(!missing.exists(), "new empty notebook load does not create a directory");
    }

    private static void addSelectAndReload() throws Exception {
        File directory = directory();
        NotebookIndex index = new NotebookIndex(directory);
        NotebookIndex.State initial = index.load();
        state(index.save(2, 2), 2, 2);
        state(initial, 1, 1);
        check(index.current().equals(new NotebookIndex(directory).load()), "state has value equality");
        equal(index.current().hashCode(), new NotebookIndex(directory).load().hashCode(), "state hash matches");
        state(index.save(3, 3), 3, 3);
        state(new NotebookIndex(directory).load(), 3, 3);
        state(readFixture(bytes(directory, "book.nbi.bak")), 2, 2);
        state(index.save(3, 1), 3, 1);
        state(new NotebookIndex(directory).load(), 3, 1);
        byte[] before = bytes(directory, "book.nbi");
        byte[] backup = bytes(directory, "book.nbi.bak");
        state(index.save(3, 1), 3, 1);
        index.sync();
        check(Arrays.equals(before, bytes(directory, "book.nbi")), "idempotent save does not rewrite index");
        check(Arrays.equals(backup, bytes(directory, "book.nbi.bak")), "idempotent save preserves prior backup");
    }

    private static void interruptedPublicationRecoversConservatively() throws Exception {
        File directory = directory();
        byte[] old = fixture(3, 1), next = fixture(4, 4);
        Files.write(new File(directory, "book.nbi").toPath(), old);
        Files.write(new File(directory, "book.nbi.tmp").toPath(), next);
        state(new NotebookIndex(directory).load(), 3, 1);
        check(new File(directory, "book.nbi").renameTo(new File(directory, "book.nbi.bak")), "simulate first rename");
        NotebookIndex recovered = new NotebookIndex(directory);
        state(recovered.load(), 3, 1);
        recovered.sync();
        check(!new File(directory, "book.nbi").exists(), "load and sync leave backup-only state unchanged");
        state(recovered.save(4, 4), 4, 4);
        state(new NotebookIndex(directory).load(), 4, 4);
        check(Arrays.equals(old, bytes(directory, "book.nbi.bak")), "publish after recovery retains prior backup");

        File firstPublish = directory();
        Files.write(new File(firstPublish, "book.nbi.tmp").toPath(), fixture(2, 2));
        NotebookIndex temporary = new NotebookIndex(firstPublish);
        state(temporary.load(), 2, 2);
        temporary.save(2, 2);
        state(new NotebookIndex(firstPublish).load(), 2, 2);
        check(!new File(firstPublish, "book.nbi.tmp").exists(), "temporary-only recovery publishes primary");

        File badBackup = directory();
        Files.write(new File(badBackup, "book.nbi.bak").toPath(), new byte[] {1});
        Files.write(new File(badBackup, "book.nbi.tmp").toPath(), next);
        expectIo(() -> new NotebookIndex(badBackup).load());
        check(Arrays.equals(next, bytes(badBackup, "book.nbi.tmp")), "corrupt backup does not silently promote an uncommitted temp");
    }

    private static void corruptionFailsClosedAtEveryByte() throws Exception {
        File directory = directory();
        byte[] good = fixture(3, 2);
        Files.write(new File(directory, "book.nbi.bak").toPath(), fixture(2, 1));
        for (int i = 0; i < good.length; i++) {
            byte[] bad = good.clone();
            bad[i] ^= 1;
            Files.write(new File(directory, "book.nbi").toPath(), bad);
            NotebookIndex index = new NotebookIndex(directory);
            expectIo(() -> index.load());
            expectIo(() -> index.save(1, 1));
            expectIo(() -> index.sync());
            check(Arrays.equals(bad, bytes(directory, "book.nbi")), "corrupt index retained at byte " + i);
        }
        byte[] trailing = Arrays.copyOf(good, good.length + 1);
        Files.write(new File(directory, "book.nbi").toPath(), trailing);
        expectIo(() -> new NotebookIndex(directory).load());
    }

    private static void truncatedPrimaryNeverBecomesAnEmptyBook() throws Exception {
        File directory = directory();
        byte[] good = fixture(5, 3);
        Files.write(new File(directory, "book.nbi.bak").toPath(), fixture(4, 1));
        for (int length = 0; length < good.length; length++) {
            byte[] partial = Arrays.copyOf(good, length);
            Files.write(new File(directory, "book.nbi").toPath(), partial);
            expectIo(() -> new NotebookIndex(directory).load());
            check(Arrays.equals(partial, bytes(directory, "book.nbi")), "truncated primary preserved at " + length);
        }
        File partialTemp = directory();
        Files.write(new File(partialTemp, "book.nbi.tmp").toPath(), new byte[] {1, 2});
        expectIo(() -> new NotebookIndex(partialTemp).load());
    }

    private static void checksummedInvalidValuesFailClosed() throws Exception {
        File directory = directory();
        int[][] invalid = {{0, 1}, {-1, 1}, {1001, 1}, {3, 0}, {3, 4}, {3, -1}};
        for (int[] values : invalid) {
            byte[] bytes = fixture(values[0], values[1]);
            Files.write(new File(directory, "book.nbi").toPath(), bytes);
            expectIo(() -> new NotebookIndex(directory).load());
            check(Arrays.equals(bytes, bytes(directory, "book.nbi")), "checksummed invalid index retained");
        }
    }

    private static void rejectedChangesPreserveAllFiles() throws Exception {
        File directory = directory();
        NotebookIndex index = new NotebookIndex(directory);
        index.load();
        index.save(2, 2);
        index.save(3, 3);
        Files.write(new File(directory, "book.nbi.tmp").toPath(), new byte[] {42});
        byte[] primary = bytes(directory, "book.nbi"), backup = bytes(directory, "book.nbi.bak"), temp = bytes(directory, "book.nbi.tmp");
        int[][] invalid = {{1, 1}, {2, 2}, {5, 3}, {4, 5}, {3, 0}, {1001, 1}, {-1, 1}};
        for (int[] values : invalid) {
            expectIo(() -> index.save(values[0], values[1]));
            check(Arrays.equals(primary, bytes(directory, "book.nbi")), "invalid save preserves primary");
            check(Arrays.equals(backup, bytes(directory, "book.nbi.bak")), "invalid save preserves backup");
            check(Arrays.equals(temp, bytes(directory, "book.nbi.tmp")), "invalid save preserves temporary");
            state(index.current(), 3, 3);
        }
        state(index.save(4, 4), 4, 4);
    }

    private static void failedPublicationAndSyncStopWrites() throws Exception {
        File directory = directory();
        NotebookIndex index = new NotebookIndex(directory);
        index.load();
        index.save(2, 2);
        byte[] committed = bytes(directory, "book.nbi");
        File obstruction = new File(directory, "book.nbi.tmp");
        check(obstruction.mkdir(), "create temporary obstruction");
        expectIo(() -> index.save(3, 3));
        state(index.current(), 2, 2);
        check(Arrays.equals(committed, bytes(directory, "book.nbi")), "failed snapshot leaves committed index");
        check(obstruction.delete(), "remove temporary obstruction");
        expectIo(() -> index.save(3, 3));
        index.load();
        state(index.save(3, 3), 3, 3);

        File parent = directory();
        File blockedDirectory = new File(parent, "not-directory");
        Files.write(blockedDirectory.toPath(), new byte[] {1});
        NotebookIndex blocked = new NotebookIndex(blockedDirectory);
        blocked.load();
        expectIo(() -> blocked.save(2, 2));
        expectIo(() -> blocked.save(2, 2));
        check(Arrays.equals(new byte[] {1}, Files.readAllBytes(blockedDirectory.toPath())), "failed mkdir retains obstructing file");

        check(new File(directory, "book.nbi").delete(), "remove primary to simulate sync failure");
        check(new File(directory, "book.nbi").mkdir(), "replace primary with directory");
        expectIo(() -> index.sync());
        expectIo(() -> index.save(4, 4));
    }

    private static void maximumPagesRemainReadable() throws Exception {
        File directory = directory();
        Files.write(new File(directory, "book.nbi").toPath(), fixture(1000, 1000));
        NotebookIndex index = new NotebookIndex(directory);
        state(index.load(), 1000, 1000);
        state(index.save(1000, 1), 1000, 1);
        expectIo(() -> index.save(1001, 1001));
        state(new NotebookIndex(directory).load(), 1000, 1);
    }

    private static byte[] fixture(int count, int selected) throws IOException {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream payload = new DataOutputStream(payloadBytes);
        payload.writeInt(count);
        payload.writeInt(selected);
        byte[] raw = payloadBytes.toByteArray();
        CRC32 crc = new CRC32();
        crc.update(raw);
        ByteArrayOutputStream fileBytes = new ByteArrayOutputStream();
        DataOutputStream file = new DataOutputStream(fileBytes);
        file.writeInt(0x4e424931);
        file.writeInt(1);
        file.writeInt(8);
        file.writeInt(~8);
        file.write(raw);
        file.writeInt((int) crc.getValue());
        return fileBytes.toByteArray();
    }

    private static NotebookIndex.State readFixture(byte[] fixture) throws Exception {
        File directory = directory();
        Files.write(new File(directory, "book.nbi").toPath(), fixture);
        return new NotebookIndex(directory).load();
    }

    private static File directory() throws IOException {
        File directory = Files.createTempDirectory("notebook-index-").toFile();
        directories.add(directory);
        return directory;
    }

    private static byte[] bytes(File directory, String file) throws IOException {
        return Files.readAllBytes(new File(directory, file).toPath());
    }

    private static void state(NotebookIndex.State state, int count, int selected) {
        equal(count, state.pageCount, "page count");
        equal(selected, state.selectedPage, "selected page");
    }

    private static void equal(int expected, int actual, String message) {
        check(expected == actual, message + " (expected " + expected + ", got " + actual + ")");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private interface IoRunnable { void run() throws Exception; }

    private static void expectIo(IoRunnable action) throws Exception {
        try { action.run(); }
        catch (IOException expected) { assertions++; return; }
        throw new AssertionError("Expected IOException");
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        file.delete();
    }
}
