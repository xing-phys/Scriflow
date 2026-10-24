package org.eink.inkprobe.notebook;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

/** Host checks against real files, independent of Android and its SDK stubs. */
public final class NotebookStoreTest {
    private static int assertions;
    private static final List<File> directories = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        try {
            emptyAndFixedSize();
            immutableModelAndExactReload();
            appendDoesNotRewriteEarlierPoints();
            everyInterruptedAppendBoundary();
            completeCorruptionFailsClosed();
            malformedButChecksummedDataFailsClosed();
            truncatedInitialSnapshotIsNotANewPage();
            templateAndBackupRecovery();
            failedSnapshotLeavesCommittedDocument();
            validationDoesNotDiscardCommittedData();
            System.out.println("NotebookStore: " + assertions + " assertions passed (host JVM, real files).");
        } finally {
            for (File directory : directories) deleteTree(directory);
        }
    }

    private static void emptyAndFixedSize() throws Exception {
        File directory = directory();
        NotebookStore store = new NotebookStore(directory);
        NotebookPage page = store.load();
        check(!page.isInitialized(), "new page has no viewport yet");
        equal(0, page.strokes.size(), "new page empty");
        check(page.template == NotebookPage.Template.BLANK, "default blank paper");
        equal(0, directory.list().length, "loading a new page writes nothing");
        page = store.initializeSize(1200, 1600);
        equal(1200, page.width, "first width saved");
        equal(1600, page.height, "first height saved");
        page = store.initializeSize(300, 400);
        equal(1200, page.width, "later viewport cannot change logical width");
        NotebookPage reloaded = new NotebookStore(directory).load();
        equal(1200, reloaded.width, "width survives process recreation");
        equal(1600, reloaded.height, "height survives process recreation");
    }

    private static void immutableModelAndExactReload() throws Exception {
        ArrayList<NotebookPage.Point> points = new ArrayList<>();
        points.add(new NotebookPage.Point(0, 0, 1000, 0));
        points.add(new NotebookPage.Point(1200, 1600, Long.MAX_VALUE, 3.25f));
        NotebookPage.Stroke stroke = new NotebookPage.Stroke(2, 4.25f, 0xff001122, points);
        points.clear();
        equal(2, stroke.points.size(), "stroke owns point list");
        expectUnsupported(() -> stroke.points.clear());
        File directory = directory();
        NotebookStore store = initialized(directory);
        NotebookPage oldPage = store.current();
        NotebookPage page = store.appendStroke(stroke);
        equal(0, oldPage.strokes.size(), "append leaves prior snapshot immutable");
        expectUnsupported(() -> page.strokes.clear());
        NotebookPage reloaded = new NotebookStore(directory).load();
        equal(1, reloaded.strokes.size(), "completed stroke durable on return");
        equal(2, reloaded.pointCount, "point count retained");
        sameStroke(stroke, reloaded.strokes.get(0));
    }

    private static void appendDoesNotRewriteEarlierPoints() throws Exception {
        File directory = directory();
        NotebookStore store = initialized(directory);
        store.appendStroke(stroke(10));
        byte[] before = bytes(directory, "page.nbk");
        store.appendStroke(stroke(20));
        byte[] after = bytes(directory, "page.nbk");
        check(after.length > before.length, "append grows journal");
        check(Arrays.equals(before, Arrays.copyOf(after, before.length)), "append preserves prior file bytes");
        equal(2, new NotebookStore(directory).load().strokes.size(), "both appends reload");
    }

    private static void everyInterruptedAppendBoundary() throws Exception {
        File source = directory();
        NotebookStore original = initialized(source);
        original.appendStroke(stroke(10));
        byte[] prefix = bytes(source, "page.nbk");
        original.appendStroke(stroke(20));
        byte[] full = bytes(source, "page.nbk");
        for (int cut = prefix.length + 1; cut < full.length; cut++) {
            File copy = directory();
            byte[] interrupted = Arrays.copyOf(full, cut);
            Files.write(new File(copy, "page.nbk").toPath(), interrupted);
            NotebookStore recovered = new NotebookStore(copy);
            NotebookPage page = recovered.load();
            equal(1, page.strokes.size(), "completed prefix survives cut " + cut);
            check(recovered.isRecoveredTail(), "interrupted tail reported " + cut);
            check(Arrays.equals(interrupted, bytes(copy, "page.nbk")), "load does not alter partial tail " + cut);
            recovered.appendStroke(stroke(30));
            NotebookStore reopened = new NotebookStore(copy);
            page = reopened.load();
            equal(2, page.strokes.size(), "append after interrupted tail visible " + cut);
            check(!reopened.isRecoveredTail(), "new append replaces torn tail cleanly " + cut);
            sameStroke(stroke(30), page.strokes.get(1));
        }
    }

    private static void completeCorruptionFailsClosed() throws Exception {
        File directory = directory();
        NotebookStore store = initialized(directory);
        store.appendStroke(stroke(10));
        byte[] good = bytes(directory, "page.nbk");
        // Damage payload, length and checksum independently, retaining a complete file each time.
        int[] offsets = { good.length - 5, good.length - 1, 8, 12, 0, 7 };
        for (int offset : offsets) {
            byte[] bad = good.clone();
            bad[offset] ^= 1;
            Files.write(new File(directory, "page.nbk").toPath(), bad);
            NotebookStore broken = new NotebookStore(directory);
            expectIo(() -> broken.load());
            expectIo(() -> broken.appendStroke(stroke(30)));
            check(Arrays.equals(bad, bytes(directory, "page.nbk")), "corrupt file preserved offset " + offset);
        }
    }

    private static void malformedButChecksummedDataFailsClosed() throws Exception {
        File directory = directory();
        NotebookStore store = initialized(directory);
        byte[] good = bytes(directory, "page.nbk");
        int payloadSize = intAt(good, 8);
        byte[] payload = Arrays.copyOfRange(good, 16, 16 + payloadSize);
        // Valid checksum must not legitimize impossible dimensions, template or list counts.
        byte[] invalidWidth = payload.clone();
        putInt(invalidWidth, 1, Integer.MAX_VALUE);
        byte[] invalidTemplate = payload.clone();
        invalidTemplate[9] = 100;
        byte[] invalidCount = payload.clone();
        putInt(invalidCount, 10, Integer.MAX_VALUE);
        byte[] invalidType = payload.clone();
        invalidType[0] = 99;
        for (byte[] malformed : new byte[][] {invalidWidth, invalidTemplate, invalidCount, invalidType}) {
            byte[] bad = initialFile(malformed);
            Files.write(new File(directory, "page.nbk").toPath(), bad);
            expectIo(() -> new NotebookStore(directory).load());
            check(Arrays.equals(bad, bytes(directory, "page.nbk")), "invalid checksummed file preserved");
        }
        // Non-finite coordinates and negative pressure must also fail even under a recomputed CRC.
        Files.write(new File(directory, "page.nbk").toPath(), good);
        store = new NotebookStore(directory);
        store.load();
        store.appendStroke(stroke(10));
        byte[] full = bytes(directory, "page.nbk");
        int recordStart = good.length;
        int recordSize = intAt(full, recordStart);
        byte[] strokePayload = Arrays.copyOfRange(full, recordStart + 8, recordStart + 8 + recordSize);
        byte[] nan = strokePayload.clone();
        putInt(nan, 17, Float.floatToIntBits(Float.NaN));
        byte[] negativePressure = strokePayload.clone();
        putInt(negativePressure, 33, Float.floatToIntBits(-1));
        for (byte[] malformed : new byte[][] {nan, negativePressure}) {
            ByteArrayOutputStream combined = new ByteArrayOutputStream();
            combined.write(good);
            combined.write(record(malformed));
            Files.write(new File(directory, "page.nbk").toPath(), combined.toByteArray());
            expectIo(() -> new NotebookStore(directory).load());
        }
    }

    private static void truncatedInitialSnapshotIsNotANewPage() throws Exception {
        File directory = directory();
        initialized(directory);
        byte[] good = bytes(directory, "page.nbk");
        for (int cut : new int[] {0, 4, 8, 12, good.length - 1}) {
            byte[] partial = Arrays.copyOf(good, cut);
            Files.write(new File(directory, "page.nbk").toPath(), partial);
            NotebookStore store = new NotebookStore(directory);
            expectIo(() -> store.load());
            expectIo(() -> store.initializeSize(1200, 1600));
            check(Arrays.equals(partial, bytes(directory, "page.nbk")), "initial partial file preserved " + cut);
        }
    }

    private static void templateAndBackupRecovery() throws Exception {
        File directory = directory();
        NotebookStore store = new NotebookStore(directory);
        store.load();
        store.setTemplate(NotebookPage.Template.LINED);
        store.initializeSize(1200, 1600);
        store.appendStroke(stroke(10));
        store.setTemplate(NotebookPage.Template.BLANK);
        NotebookPage page = new NotebookStore(directory).load();
        check(page.template == NotebookPage.Template.BLANK, "new template committed");
        equal(1, page.strokes.size(), "template snapshot preserves strokes");
        // Simulate process death after old primary was moved aside, before new snapshot was published.
        check(new File(directory, "page.nbk").delete(), "remove published primary for interrupted-rename simulation");
        NotebookStore recovered = new NotebookStore(directory);
        page = recovered.load();
        check(page.template == NotebookPage.Template.LINED, "backup is complete preceding template state");
        equal(1, page.strokes.size(), "backup preserves previous committed stroke");
        recovered.appendStroke(stroke(20));
        page = new NotebookStore(directory).load();
        equal(2, page.strokes.size(), "backup recovery can continue writing");
        check(page.template == NotebookPage.Template.LINED, "continued notebook retains recovered template");
    }

    private static void failedSnapshotLeavesCommittedDocument() throws Exception {
        File directory = directory();
        NotebookStore store = initialized(directory);
        store.appendStroke(stroke(10));
        byte[] committed = bytes(directory, "page.nbk");
        check(new File(directory, "page.nbk.tmp").mkdir(), "create blocking temporary directory");
        expectIo(() -> store.setTemplate(NotebookPage.Template.LINED));
        check(Arrays.equals(committed, bytes(directory, "page.nbk")), "failed snapshot preserves primary");
        check(store.current().template == NotebookPage.Template.BLANK, "failed snapshot does not change in-memory state");
        equal(1, new NotebookStore(directory).load().strokes.size(), "committed stroke survives snapshot failure");
        expectIo(() -> store.appendStroke(stroke(20)));

        File another = directory();
        NotebookStore rotation = initialized(another);
        rotation.appendStroke(stroke(10));
        byte[] before = bytes(another, "page.nbk");
        File blockingBackup = new File(another, "page.nbk.bak");
        check(blockingBackup.mkdir(), "create blocking backup directory");
        Files.write(new File(blockingBackup, "occupied").toPath(), new byte[] {1});
        expectIo(() -> rotation.setTemplate(NotebookPage.Template.LINED));
        check(Arrays.equals(before, bytes(another, "page.nbk")), "failed backup rotation retains primary");
        check(new NotebookStore(another).load().template == NotebookPage.Template.BLANK,
              "unpublished temporary snapshot is ignored when primary exists");
    }

    private static void validationDoesNotDiscardCommittedData() throws Exception {
        File directory = directory();
        NotebookStore store = new NotebookStore(directory);
        store.load();
        expectIo(() -> store.appendStroke(stroke(10)));
        store.initializeSize(1200, 1600);
        store.appendStroke(stroke(10));
        byte[] before = bytes(directory, "page.nbk");
        NotebookPage.Stroke outside = new NotebookPage.Stroke(2, 4, 0xff000000,
            Collections.singletonList(new NotebookPage.Point(1201, 10, 100, 1)));
        expectIo(() -> store.appendStroke(outside));
        check(Arrays.equals(before, bytes(directory, "page.nbk")), "rejected stroke does not mutate document");
        expectIllegal(() -> new NotebookPage.Point(Float.POSITIVE_INFINITY, 0, 100, 1));
        expectIllegal(() -> new NotebookPage.Point(0, 0, -1, 1));
        expectIllegal(() -> new NotebookPage.Point(0, 0, 100, Float.NaN));
        expectIllegal(() -> new NotebookPage.Stroke(2, 0, 0xff000000,
            Collections.singletonList(new NotebookPage.Point(0, 0, 100, 1))));
        equal(1, new NotebookStore(directory).load().strokes.size(), "invalid input leaves saved page readable");
    }

    private static NotebookStore initialized(File directory) throws IOException {
        NotebookStore store = new NotebookStore(directory);
        store.load();
        store.initializeSize(1200, 1600);
        return store;
    }

    private static NotebookPage.Stroke stroke(int offset) {
        return new NotebookPage.Stroke(2, 4, 0xff000000, Arrays.asList(
            new NotebookPage.Point(offset, 10, 1000, 0.25f),
            new NotebookPage.Point(offset + 5, 20, 1005, 1.75f)));
    }

    private static File directory() throws IOException {
        File file = Files.createTempDirectory("notebook-store-test-").toFile();
        directories.add(file);
        return file;
    }

    private static byte[] bytes(File directory, String name) throws IOException {
        return Files.readAllBytes(new File(directory, name).toPath());
    }

    private static byte[] initialFile(byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(0x4e424b31);
        output.writeInt(1);
        output.write(record(payload));
        return bytes.toByteArray();
    }

    private static byte[] record(byte[] payload) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        CRC32 crc = new CRC32();
        crc.update(payload);
        output.writeInt(payload.length);
        output.writeInt(~payload.length);
        output.write(payload);
        output.writeInt((int) crc.getValue());
        return bytes.toByteArray();
    }

    private static int intAt(byte[] bytes, int offset) {
        return (bytes[offset] & 255) << 24 | (bytes[offset + 1] & 255) << 16
            | (bytes[offset + 2] & 255) << 8 | bytes[offset + 3] & 255;
    }

    private static void putInt(byte[] bytes, int offset, int value) {
        for (int i = 3; i >= 0; i--) { bytes[offset + i] = (byte) value; value >>>= 8; }
    }

    private static void sameStroke(NotebookPage.Stroke expected, NotebookPage.Stroke actual) {
        equal(expected.penType, actual.penType, "pen type retained");
        equal(expected.color, actual.color, "pen color retained");
        equal(Float.floatToIntBits(expected.width), Float.floatToIntBits(actual.width), "pen width retained exactly");
        equal(expected.points.size(), actual.points.size(), "stroke point count retained");
        for (int i = 0; i < expected.points.size(); i++) {
            NotebookPage.Point a = expected.points.get(i), b = actual.points.get(i);
            equal(Float.floatToIntBits(a.x), Float.floatToIntBits(b.x), "point x retained exactly");
            equal(Float.floatToIntBits(a.y), Float.floatToIntBits(b.y), "point y retained exactly");
            equal(a.time, b.time, "point time retained exactly");
            equal(Float.floatToIntBits(a.pressure), Float.floatToIntBits(b.pressure), "point pressure retained exactly");
        }
    }

    private interface Throwing { void run() throws Exception; }
    private static void expectIo(Throwing task) throws Exception {
        assertions++;
        try { task.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Expected IOException");
    }
    private static void expectIllegal(Throwing task) throws Exception {
        assertions++;
        try { task.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected IllegalArgumentException");
    }
    private static void expectUnsupported(Throwing task) throws Exception {
        assertions++;
        try { task.run(); } catch (UnsupportedOperationException expected) { return; }
        throw new AssertionError("Expected immutable collection");
    }
    private static void equal(long expected, long actual, String label) {
        check(expected == actual, label + ": expected " + expected + ", got " + actual);
    }
    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
    private static void deleteTree(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        if (!file.delete() && file.exists()) throw new IOException("Cannot remove test file " + file);
    }
}
