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

/** Real-file scroll-anchor checks, including independently encoded version-1 fixtures. */
public final class NotebookScrollStoreTest {
    private interface Action { void run() throws Exception; }
    private static int assertions;
    private static final List<File> directories = new ArrayList<>();
    public static void main(String[] args) throws Exception {
        try {
            defaultAndExactRoundTrip();
            invalidSavesPreserveFiles();
            interruptedSnapshotPublication();
            corruptionAndTruncationFailClosed();
            invalidChecksummedValuesFailClosed();
            failedPublicationAndRetry();
            System.out.println("NotebookScrollStore: " + assertions + " assertions passed (host JVM, real files).");
        } finally { for (File directory : directories) deleteTree(directory); }
    }
    private static void defaultAndExactRoundTrip() throws Exception {
        File directory = directory(); NotebookScrollStore store = new NotebookScrollStore(directory);
        state(store.load(7), 7, 0); store.sync();
        equal(0, directory.list().length, "old notebook default writes nothing");
        state(store.save(7, 0.125), 7, 0.125);
        state(new NotebookScrollStore(directory).load(1), 7, 0.125);
        byte[] original = bytes(directory, "scroll.nbs"); store.save(7, 0.125);
        check(Arrays.equals(original, bytes(directory, "scroll.nbs")), "idempotent save preserves bytes");
        check(!new File(directory, "scroll.nbs.bak").exists(), "idempotent save does not rotate backup");
        state(store.save(1000, Math.nextDown(1.0)), 1000, Math.nextDown(1.0));
        state(new NotebookScrollStore(directory).load(1), 1000, Math.nextDown(1.0));
        check(Arrays.equals(original, bytes(directory, "scroll.nbs.bak")), "prior position retained in backup");
        NotebookScrollStore.State positive = new NotebookScrollStore.State(2, 0);
        NotebookScrollStore.State negative = new NotebookScrollStore.State(2, -0.0);
        check(positive.equals(negative) && positive.hashCode() == negative.hashCode(), "negative zero has one canonical representation");
    }
    private static void invalidSavesPreserveFiles() throws Exception {
        File directory = directory(); NotebookScrollStore store = new NotebookScrollStore(directory);
        store.load(1); store.save(2, 0.25); store.save(3, 0.5);
        Files.write(new File(directory, "scroll.nbs.tmp").toPath(), new byte[]{42});
        byte[] primary = bytes(directory, "scroll.nbs"), backup = bytes(directory, "scroll.nbs.bak"), temp = bytes(directory, "scroll.nbs.tmp");
        for (double value : new double[]{-1, 1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            expectIo(() -> store.save(3, value));
            check(Arrays.equals(primary, bytes(directory, "scroll.nbs")), "invalid fraction preserves primary");
            check(Arrays.equals(backup, bytes(directory, "scroll.nbs.bak")), "invalid fraction preserves backup");
            check(Arrays.equals(temp, bytes(directory, "scroll.nbs.tmp")), "invalid fraction preserves temp");
        }
        expectIo(() -> store.save(0, 0)); expectIo(() -> store.save(1001, 0));
        state(store.current(), 3, 0.5);
    }
    private static void interruptedSnapshotPublication() throws Exception {
        File directory = directory(); byte[] old = fixture(2, 0.25), next = fixture(4, 0.75);
        Files.write(new File(directory, "scroll.nbs").toPath(), old);
        Files.write(new File(directory, "scroll.nbs.tmp").toPath(), next);
        state(new NotebookScrollStore(directory).load(1), 2, 0.25);
        check(new File(directory, "scroll.nbs").renameTo(new File(directory, "scroll.nbs.bak")), "simulate interrupted rename");
        NotebookScrollStore recovered = new NotebookScrollStore(directory); state(recovered.load(1), 2, 0.25);
        recovered.save(4, 0.75); state(new NotebookScrollStore(directory).load(1), 4, 0.75);
        check(Arrays.equals(old, bytes(directory, "scroll.nbs.bak")), "recovery retains old backup");
        File first = directory(); Files.write(new File(first, "scroll.nbs.tmp").toPath(), next);
        NotebookScrollStore temporary = new NotebookScrollStore(first); state(temporary.load(1), 4, 0.75);
        temporary.save(4, 0.75); check(new File(first, "scroll.nbs").isFile(), "complete initial temp becomes primary on save");
    }
    private static void corruptionAndTruncationFailClosed() throws Exception {
        File directory = directory(); byte[] good = fixture(7, 0.125);
        Files.write(new File(directory, "scroll.nbs.bak").toPath(), fixture(2, 0.5));
        for (int i = 0; i < good.length; i++) {
            byte[] corrupt = good.clone(); corrupt[i] ^= 1;
            Files.write(new File(directory, "scroll.nbs").toPath(), corrupt);
            NotebookScrollStore broken = new NotebookScrollStore(directory); expectIo(() -> broken.load(1));
            expectIo(() -> broken.save(1, 0));
            check(Arrays.equals(corrupt, bytes(directory, "scroll.nbs")), "corrupt primary preserved at " + i);
        }
        for (int cut = 0; cut < good.length; cut++) {
            byte[] partial = Arrays.copyOf(good, cut); Files.write(new File(directory, "scroll.nbs").toPath(), partial);
            expectIo(() -> new NotebookScrollStore(directory).load(1));
            check(Arrays.equals(partial, bytes(directory, "scroll.nbs")), "truncated primary not replaced at " + cut);
        }
        Files.write(new File(directory, "scroll.nbs").toPath(), Arrays.copyOf(good, good.length + 1));
        expectIo(() -> new NotebookScrollStore(directory).load(1));
    }
    private static void invalidChecksummedValuesFailClosed() throws Exception {
        File directory = directory();
        for (double value : new double[]{-1, 1, Double.NaN, Double.POSITIVE_INFINITY}) {
            byte[] invalid = fixture(2, value); Files.write(new File(directory, "scroll.nbs").toPath(), invalid);
            expectIo(() -> new NotebookScrollStore(directory).load(1));
            check(Arrays.equals(invalid, bytes(directory, "scroll.nbs")), "invalid checksummed fraction preserved");
        }
        Files.write(new File(directory, "scroll.nbs").toPath(), fixture(0, 0));
        expectIo(() -> new NotebookScrollStore(directory).load(1));
    }
    private static void failedPublicationAndRetry() throws Exception {
        File directory = directory(); NotebookScrollStore store = new NotebookScrollStore(directory);
        store.load(1); store.save(2, 0.25); byte[] before = bytes(directory, "scroll.nbs");
        check(new File(directory, "scroll.nbs.tmp").mkdir(), "create failed-write obstruction");
        expectIo(() -> store.save(3, 0.5)); state(store.current(), 2, 0.25);
        check(Arrays.equals(before, bytes(directory, "scroll.nbs")), "failed write preserves previous position");
        check(new File(directory, "scroll.nbs.tmp").delete(), "remove obstruction");
        expectIo(() -> store.save(3, 0.5)); store.load(1); state(store.save(3, 0.5), 3, 0.5);
    }
    private static byte[] fixture(int page, double fraction) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream(); DataOutputStream content = new DataOutputStream(payload);
        content.writeInt(page); content.writeDouble(fraction); byte[] raw = payload.toByteArray(); CRC32 crc = new CRC32(); crc.update(raw);
        ByteArrayOutputStream output = new ByteArrayOutputStream(); DataOutputStream file = new DataOutputStream(output);
        file.writeInt(0x4e425331); file.writeInt(1); file.writeInt(12); file.writeInt(~12); file.write(raw); file.writeInt((int) crc.getValue());
        return output.toByteArray();
    }
    private static File directory() throws IOException { File directory = Files.createTempDirectory("notebook-scroll-").toFile(); directories.add(directory); return directory; }
    private static byte[] bytes(File root, String file) throws IOException { return Files.readAllBytes(new File(root, file).toPath()); }
    private static void state(NotebookScrollStore.State state, int page, double fraction) {
        equal(page, state.page, "scroll page"); check(state.fraction == fraction, "scroll fraction exact");
    }
    private static void equal(int expected, int actual, String message) { check(expected == actual, message); }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void expectIo(Action action) throws Exception { try { action.run(); } catch (IOException expected) { assertions++; return; } throw new AssertionError("Expected IOException"); }
    private static void deleteTree(File file) { File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child); file.delete(); }
}
