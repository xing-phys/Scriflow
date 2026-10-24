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
            durableUnsizedFirstPage();
            immutableModelAndExactReload();
            appendDoesNotRewriteEarlierPoints();
            everyInterruptedAppendBoundary();
            completeCorruptionFailsClosed();
            malformedButChecksummedDataFailsClosed();
            truncatedInitialSnapshotIsNotANewPage();
            templateAndBackupRecovery();
            failedSnapshotLeavesCommittedDocument();
            validationDoesNotDiscardCommittedData();
            release07FixtureSurvivesEdits();
            editSnapshotAndContinuedWriting();
            interruptedEditSnapshotPublication();
            rejectedEditsPreserveCommittedData();
            atomicPageInitialization();
            failedFirstPagePublicationAndRetry();
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

    private static void durableUnsizedFirstPage() throws Exception {
        File directory = directory();
        NotebookStore store = new NotebookStore(directory);
        store.load(); store.initializeEmpty(); store.sync();
        check(new File(directory, "page.nbk").isFile(), "unsized first page exists before catalog publication");
        NotebookStore reopened = new NotebookStore(directory);
        NotebookPage page = reopened.load();
        check(!page.isInitialized() && page.strokes.isEmpty(), "durable unsized page reloads empty");
        byte[] original = bytes(directory, "page.nbk");
        reopened.initializeEmpty();
        check(Arrays.equals(original, bytes(directory, "page.nbk")), "repeat empty initialization preserves bytes");
        reopened.initializeSize(1200, 1600);
        original = bytes(directory, "page.nbk");
        expectIo(() -> reopened.initializeEmpty());
        check(Arrays.equals(original, bytes(directory, "page.nbk")), "empty initialization cannot reset sized page");
        reopened.appendStroke(stroke(10));
        original = bytes(directory, "page.nbk");
        expectIo(() -> reopened.initializeEmpty());
        check(Arrays.equals(original, bytes(directory, "page.nbk")), "empty initialization cannot reset writing");
        File linedDirectory = directory();
        NotebookStore lined = new NotebookStore(linedDirectory);
        lined.load(); lined.setTemplate(NotebookPage.Template.LINED);
        byte[] linedBytes = bytes(linedDirectory, "page.nbk");
        expectIo(() -> lined.initializeEmpty());
        check(Arrays.equals(linedBytes, bytes(linedDirectory, "page.nbk")), "empty initialization cannot reset unsized lined page");
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

    /** Fixed release-0.7 bytes, not produced by the writer under test: lined snapshot + append. */
    private static void release07FixtureSurvivesEdits() throws Exception {
        String hex = "4e424b310000000100000046ffffffb901000004b0000006400100000001"
            + "0000000240800000ff00000000000002412000004120000000000000000003e8"
            + "3e8000004170000041a0000000000000000003ed3fe00000ee792135"
            + "00000039ffffffc6020000000240800000ff0000000000000241a0000041200000"
            + "00000000000003e83e80000041c8000041a0000000000000000003ed3fe0000044dd9104";
        File directory = directory();
        byte[] fixture = new byte[hex.length() / 2];
        for (int i = 0; i < fixture.length; i++) fixture[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        Files.write(new File(directory, "page.nbk").toPath(), fixture);
        NotebookStore store = new NotebookStore(directory);
        NotebookPage page = store.load();
        equal(1200, page.width, "release fixture width");
        equal(1600, page.height, "release fixture height");
        check(page.template == NotebookPage.Template.LINED, "release fixture lined paper");
        equal(2, page.strokes.size(), "release fixture snapshot and journal strokes");
        sameStroke(stroke(10), page.strokes.get(0));
        sameStroke(stroke(20), page.strokes.get(1));
        check(Arrays.equals(fixture, bytes(directory, "page.nbk")), "reading release fixture changes no bytes");
        store.replaceStrokes(Collections.singletonList(page.strokes.get(1)));
        store.appendStroke(stroke(30));
        NotebookPage reopened = new NotebookStore(directory).load();
        equal(2, reopened.strokes.size(), "release fixture supports edit then append");
        sameStroke(stroke(20), reopened.strokes.get(0));
        sameStroke(stroke(30), reopened.strokes.get(1));
        equal(1, intAt(bytes(directory, "page.nbk"), 4), "editing keeps version 1 on disk");
    }

    private static void editSnapshotAndContinuedWriting() throws Exception {
        File directory = directory();
        NotebookStore store = initialized(directory);
        store.setTemplate(NotebookPage.Template.LINED);
        store.appendStroke(stroke(10));
        store.appendStroke(stroke(20));
        NotebookPage original = store.current();
        ArrayList<NotebookPage.Stroke> retained = new ArrayList<>(Collections.singletonList(original.strokes.get(1)));
        NotebookPage edited = store.replaceStrokes(retained);
        retained.clear();
        equal(2, original.strokes.size(), "edit does not mutate preceding page");
        equal(1, edited.strokes.size(), "edit owns replacement list");
        equal(2, edited.pointCount, "edit recounts points");
        equal(1200, edited.width, "edit retains logical width");
        equal(1600, edited.height, "edit retains logical height");
        check(edited.template == NotebookPage.Template.LINED, "edit retains paper");
        NotebookPage reopened = new NotebookStore(directory).load();
        equal(1, reopened.strokes.size(), "subset replacement durable immediately");
        sameStroke(stroke(20), reopened.strokes.get(0));
        store.replaceStrokes(Collections.<NotebookPage.Stroke>emptyList());
        reopened = new NotebookStore(directory).load();
        equal(0, reopened.strokes.size(), "erasing last stroke persists empty page");
        equal(0, reopened.pointCount, "empty edit resets point count");
        check(reopened.template == NotebookPage.Template.LINED, "empty edit retains paper");
        store.appendStroke(stroke(30));
        reopened = new NotebookStore(directory).load();
        equal(1, reopened.strokes.size(), "drawing after erase-all persists");
        sameStroke(stroke(30), reopened.strokes.get(0));
        store.replaceStrokes(original.strokes);
        reopened = new NotebookStore(directory).load();
        equal(2, reopened.strokes.size(), "undo can durably restore old stroke list");
        sameStroke(stroke(10), reopened.strokes.get(0));
        sameStroke(stroke(20), reopened.strokes.get(1));
    }

    private static void interruptedEditSnapshotPublication() throws Exception {
        File source = directory();
        NotebookStore store = initialized(source);
        store.appendStroke(stroke(10));
        byte[] old = bytes(source, "page.nbk");
        store.replaceStrokes(Collections.<NotebookPage.Stroke>emptyList());
        byte[] edited = bytes(source, "page.nbk");
        // Before publication, both a partial and a complete temporary file must leave primary intact.
        for (int cut : new int[] {0, 8, edited.length - 1, edited.length}) {
            File copy = directory();
            Files.write(new File(copy, "page.nbk").toPath(), old);
            Files.write(new File(copy, "page.nbk.tmp").toPath(), Arrays.copyOf(edited, cut));
            equal(1, new NotebookStore(copy).load().strokes.size(), "primary wins over uncommitted edit " + cut);
        }
        // Between renames, recover the last completed document, not an uncommitted replacement.
        File betweenRenames = directory();
        Files.write(new File(betweenRenames, "page.nbk.bak").toPath(), old);
        Files.write(new File(betweenRenames, "page.nbk.tmp").toPath(), edited);
        NotebookStore recovered = new NotebookStore(betweenRenames);
        equal(1, recovered.load().strokes.size(), "backup wins during interrupted edit publication");
        recovered.replaceStrokes(Collections.<NotebookPage.Stroke>emptyList());
        equal(0, new NotebookStore(betweenRenames).load().strokes.size(), "edit after backup recovery publishes correctly");
        // An I/O failure before publication cannot erase the last saved page or claim success.
        File blocked = directory();
        NotebookStore failing = initialized(blocked);
        failing.appendStroke(stroke(10));
        byte[] committed = bytes(blocked, "page.nbk");
        check(new File(blocked, "page.nbk.tmp").mkdir(), "block edit temporary output");
        expectIo(() -> failing.replaceStrokes(Collections.<NotebookPage.Stroke>emptyList()));
        check(Arrays.equals(committed, bytes(blocked, "page.nbk")), "failed edit preserves committed bytes");
        equal(1, failing.current().strokes.size(), "failed edit preserves in-memory page");
        expectIo(() -> failing.appendStroke(stroke(20)));
    }

    private static void rejectedEditsPreserveCommittedData() throws Exception {
        File directory = directory();
        NotebookStore store = initialized(directory);
        store.appendStroke(stroke(10));
        byte[] committed = bytes(directory, "page.nbk");
        NotebookPage.Stroke outside = new NotebookPage.Stroke(2, 4, 0xff000000,
            Collections.singletonList(new NotebookPage.Point(1201, 10, 100, 1)));
        expectIo(() -> store.replaceStrokes(Arrays.asList(stroke(20), outside)));
        expectIllegal(() -> store.replaceStrokes(null));
        expectIllegal(() -> store.replaceStrokes(Arrays.asList(stroke(20), null)));
        expectIo(() -> store.replaceStrokes(Collections.nCopies(NotebookStore.MAX_STROKES + 1, stroke(20))));
        NotebookPage.Stroke manyPoints = new NotebookPage.Stroke(2, 4, 0xff000000,
            Collections.nCopies(NotebookStore.MAX_POINTS_PER_STROKE, new NotebookPage.Point(20, 20, 100, 1)));
        expectIo(() -> store.replaceStrokes(Collections.nCopies(6, manyPoints)));
        check(Arrays.equals(committed, bytes(directory, "page.nbk")), "invalid edits preserve exact document bytes");
        equal(1, store.current().strokes.size(), "invalid edits preserve in-memory state");
        store.appendStroke(stroke(30));
        equal(2, new NotebookStore(directory).load().strokes.size(), "validation rejection allows subsequent valid writes");
    }

    private static NotebookStore initialized(File directory) throws IOException {
        NotebookStore store = new NotebookStore(directory);
        store.load();
        store.initializeSize(1200, 1600);
        return store;
    }

    private static void atomicPageInitialization() throws Exception {
        File directory = directory(); NotebookStore store = new NotebookStore(directory); store.load();
        expectIo(() -> store.initializePage(0, 1600, NotebookPage.Template.LINED));
        expectIllegal(() -> store.initializePage(1200, 1600, null));
        equal(0, directory.list().length, "invalid first-page metadata writes nothing");
        NotebookPage page = store.initializePage(1200, 1600, NotebookPage.Template.LINED);
        equal(1200, page.width, "atomic initialization width"); equal(1600, page.height, "atomic initialization height");
        check(page.template == NotebookPage.Template.LINED, "first snapshot already has requested paper");
        check(!new File(directory, "page.nbk.bak").exists(), "first initialization uses one publication");
        byte[] original = bytes(directory, "page.nbk");
        store.initializePage(1200, 1600, NotebookPage.Template.LINED);
        check(Arrays.equals(original, bytes(directory, "page.nbk")), "matching empty initialization is a byte-preserving no-op");
        expectIo(() -> store.initializePage(1100, 1600, NotebookPage.Template.LINED));
        expectIo(() -> store.initializePage(1200, 1600, NotebookPage.Template.BLANK));
        check(Arrays.equals(original, bytes(directory, "page.nbk")), "unexpected initialization metadata preserves page");
        store.appendStroke(stroke(10)); byte[] writing = bytes(directory, "page.nbk");
        expectIo(() -> store.initializePage(1200, 1600, NotebookPage.Template.LINED));
        check(Arrays.equals(writing, bytes(directory, "page.nbk")), "initialization never erases existing writing");
        check(new NotebookStore(directory).load().template == NotebookPage.Template.LINED, "atomic paper survives reload");
    }

    private static void failedFirstPagePublicationAndRetry() throws Exception {
        File directory = directory(); NotebookStore store = new NotebookStore(directory); store.load();
        File temporary = new File(directory, "page.nbk.tmp"); check(temporary.mkdir(), "block first snapshot temporary path");
        expectIo(() -> store.initializePage(1200, 1600, NotebookPage.Template.LINED));
        check(!new File(directory, "page.nbk").exists(), "failed first publication has no intermediate primary");
        check(!store.current().isInitialized(), "failed first publication leaves current page unsized");
        expectIo(() -> store.initializePage(1200, 1600, NotebookPage.Template.LINED));
        check(temporary.delete(), "remove first-publication blocker");
        store.load(); store.initializePage(1200, 1600, NotebookPage.Template.LINED);
        byte[] complete = bytes(directory, "page.nbk");
        File interrupted = directory(); Files.write(new File(interrupted, "page.nbk.tmp").toPath(), complete);
        NotebookStore recovered = new NotebookStore(interrupted); NotebookPage page = recovered.load();
        check(page.template == NotebookPage.Template.LINED, "first fully written temporary has correct paper");
        equal(1200, page.width, "first fully written temporary has correct dimensions");
        recovered.initializePage(1200, 1600, NotebookPage.Template.LINED);
        check(Arrays.equals(complete, bytes(interrupted, "page.nbk")), "temporary recovery republishes exact complete first snapshot");
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
