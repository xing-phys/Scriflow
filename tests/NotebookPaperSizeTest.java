package org.eink.inkprobe.notebook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/** Physical-size persistence, edit/recovery, transport and vector PDF geometry. */
public final class NotebookPaperSizeTest {
    private interface Action { void run() throws Exception; }
    private static final List<File> directories = new ArrayList<>();
    private static int assertions;

    public static void main(String[] args) throws Exception {
        try {
            presetsAndCustomDimensions();
            storePreservesPhysicalPaperThroughEdits();
            notebookCreationAndPageInheritance();
            interruptedCreationAndOrphanValidation();
            backupAndRestorePreservePhysicalPaper();
            exactPhysicalPdfSizesAndLegacyNormalization();
            legacyVersionTwoReadsStayNonMutating();
            invalidPhysicalMetadataNeverPublishes();
            System.out.println("NotebookPaperSize: " + assertions + " assertions passed (host JVM, real files).");
        } finally { for (File directory : directories) NotebookBackup.deleteTree(directory); }
    }

    private static void presetsAndCustomDimensions() throws Exception {
        check(NotebookPaperSize.PRESETS.get(0) == NotebookPaperSize.A5, "A5 is the default creation preset");
        equal(1184, NotebookPaperSize.A5.logicalWidth(), "A5 logical width");
        equal(1680, NotebookPaperSize.A5.logicalHeight(), "A5 logical height");
        for (NotebookPaperSize size : NotebookPaperSize.PRESETS) {
            check(size.logicalWidth() < NotebookStore.MAX_DIMENSION && size.logicalHeight() < NotebookStore.MAX_DIMENSION,
                "preset fits logical limits: " + size.name);
            check(size.equals(NotebookPaperSize.custom(size.widthMm, size.heightMm)), "dimension identity ignores preset label");
        }
        NotebookPaperSize custom = NotebookPaperSize.custom(297.25, 148.125);
        equal(2378, custom.logicalWidth(), "custom width rounds to stable logical coordinates");
        equal(1185, custom.logicalHeight(), "landscape custom height retained");
        for (double value : new double[]{0, -1, 19.9, 1000.1, Double.NaN, Double.POSITIVE_INFINITY}) {
            expectIllegal(() -> NotebookPaperSize.custom(value, 210));
            expectIllegal(() -> NotebookPaperSize.custom(148, value));
        }
        check(NotebookPaperSize.custom(20, 1000).logicalHeight() == 8000, "supported size bounds accepted");
    }

    private static void storePreservesPhysicalPaperThroughEdits() throws Exception {
        NotebookPaperSize size = NotebookPaperSize.custom(196.375, 248.25);
        File root = directory(); NotebookStore store = new NotebookStore(root); store.load();
        store.initializePage(size, NotebookPage.Template.LINED);
        NotebookPage.Stroke stroke = stroke(20);
        store.appendStroke(stroke); store.setTemplate(NotebookPage.Template.BLANK);
        store.replaceStrokes(Collections.emptyList()); store.appendStroke(stroke);
        NotebookPage page = new NotebookStore(root).load();
        paper(size, page, "all page edits retain physical paper");
        check(page.template == NotebookPage.Template.BLANK && page.strokes.size() == 1, "edits and journal survive reopen");
        NotebookDocument document = new NotebookDocument(root); document.load(); document.append(stroke(40));
        document.undo(); document.redo(); document.retry();
        paper(size, new NotebookStore(root).load(), "document history and retry retain physical paper");
        byte[] durable = Files.readAllBytes(new File(root, "page.nbk").toPath());
        document.setTemplate(NotebookPage.Template.LINED);
        NotebookStore changed = new NotebookStore(root); changed.load();
        check(changed.current().paperSize.equals(size), "template edit keeps physical dimensions");
        check(!Arrays.equals(durable, Files.readAllBytes(new File(root, "page.nbk").toPath())), "fixture exercised snapshot path");
    }

    private static void notebookCreationAndPageInheritance() throws Exception {
        NotebookLibrary library = legacyLibrary(directory());
        NotebookPaperSize size = NotebookPaperSize.custom(280.5, 180.25);
        library.createNotebook("Landscape paper", size);
        paper(size, library.current(), "custom notebook initialized before opening");
        library.append(stroke(30)); library.addPage();
        paper(size, library.current(), "Add page inherits physical paper");
        library.managePages(NotebookBook.PageAction.DUPLICATE, 1, 0);
        library.managePages(NotebookBook.PageAction.INSERT_BEFORE, 1, 0);
        library.managePages(NotebookBook.PageAction.INSERT_AFTER, 1, 0);
        for (int number = 1; number <= library.pageCount(); number++)
            paper(size, library.previewPage(number), "managed page " + number + " inherits physical paper");
        library.openNotebook(1);
        check(library.current().paperSize == null && library.current().width == 100 && library.current().height == 120,
            "legacy notebook geometry is unchanged");
    }

    private static void interruptedCreationAndOrphanValidation() throws Exception {
        File root = directory(); NotebookLibrary library = legacyLibrary(root);
        library.renameNotebook(1, "Legacy");
        File blocker = new File(root, "library.nbl.tmp"); check(blocker.mkdir(), "block catalog publication");
        expectIo(() -> library.createNotebook("A5 after retry", NotebookPaperSize.A5));
        check(library.notebookId() == 1, "failed sized creation retains visible notebook");
        paper(NotebookPaperSize.A5, new NotebookStore(new File(root, "notebooks/2")).load(), "sized page durable before catalog");
        check(blocker.delete(), "unblock catalog publication"); library.retry();
        paper(NotebookPaperSize.A5, library.current(), "retry opens exact chosen paper");
        check(library.entries().size() == 2, "retry creates one notebook");
        File mismatchRoot = directory(); NotebookLibrary mismatch = legacyLibrary(mismatchRoot);
        NotebookStore orphan = new NotebookStore(new File(mismatchRoot, "notebooks/2")); orphan.load();
        orphan.initializePage(NotebookPaperSize.custom(210, 297), NotebookPage.Template.BLANK);
        byte[] before = Files.readAllBytes(new File(mismatchRoot, "notebooks/2/page.nbk").toPath());
        expectIo(() -> mismatch.createNotebook("Wrong paper", NotebookPaperSize.A5));
        check(Arrays.equals(before, Files.readAllBytes(new File(mismatchRoot, "notebooks/2/page.nbk").toPath())),
            "different-size orphan preserved");
        File exactRoot = directory(); NotebookLibrary exact = legacyLibrary(exactRoot);
        NotebookStore exactOrphan = new NotebookStore(new File(exactRoot, "notebooks/2")); exactOrphan.load();
        exactOrphan.initializePage(NotebookPaperSize.A5, NotebookPage.Template.BLANK);
        exact.createNotebook("Recovered A5", NotebookPaperSize.A5);
        paper(NotebookPaperSize.A5, exact.current(), "exact sized blank orphan recovered");
    }

    private static void backupAndRestorePreservePhysicalPaper() throws Exception {
        NotebookLibrary source = legacyLibrary(directory());
        NotebookPaperSize size = NotebookPaperSize.custom(215.9, 279.4);
        source.createNotebook("Letter backup", size); source.append(stroke(50)); source.addPage();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NotebookBackup.write(bytes, source.notebookName(), source.pageCount(), source::previewPage, () -> false);
        File staging = new File(directory(), "staging");
        NotebookBackup.Restored restored = NotebookBackup.read(new ByteArrayInputStream(bytes.toByteArray()), staging, () -> false);
        NotebookLibrary target = legacyLibrary(directory()); target.importNotebook(restored); target.load();
        for (int page = 1; page <= 2; page++) paper(size, target.previewPage(page), "restored physical paper " + page);
        check(target.previewPage(1).strokes.size() == 1, "editable backup retains writing");
        target.addPage(); paper(size, target.current(), "new page in restored book inherits physical paper");
    }

    private static void exactPhysicalPdfSizesAndLegacyNormalization() throws Exception {
        List<NotebookPaperSize> sizes = new ArrayList<>(NotebookPaperSize.PRESETS);
        sizes.add(NotebookPaperSize.custom(297.125, 148.375)); sizes.add(NotebookPaperSize.custom(20, 1000));
        for (NotebookPaperSize size : sizes) {
            NotebookPage page = NotebookPage.empty().withSize(size.logicalWidth(), size.logicalHeight()).withPaperSize(size);
            String pdf = pdf(page); Matcher media = Pattern.compile("/MediaBox \\[0 0 ([0-9.]+) ([0-9.]+)\\]").matcher(pdf);
            check(media.find(), "physical PDF media box present");
            close(size.pdfWidth(), Double.parseDouble(media.group(1)), "exact physical PDF width " + size.name);
            close(size.pdfHeight(), Double.parseDouble(media.group(2)), "exact physical PDF height " + size.name);
        }
        check(pdf(NotebookPage.empty().withSize(100, 200)).contains("/MediaBox [0 0 595.2756 1190.5512]"),
            "legacy PDF retains old A4-width normalization");
    }

    private static void legacyVersionTwoReadsStayNonMutating() throws Exception {
        File root = directory(); UUID id = UUID.randomUUID();
        ByteArrayOutputStream payload = new ByteArrayOutputStream(); DataOutputStream p = new DataOutputStream(payload);
        p.writeByte(1); p.writeLong(id.getMostSignificantBits()); p.writeLong(id.getLeastSignificantBits());
        p.writeInt(100); p.writeInt(120); p.writeByte(0); p.writeInt(0); p.flush();
        byte[] record = payload.toByteArray();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x4e424b31); out.writeInt(2); writeRecord(out, record);
        UUID strokeId = UUID.randomUUID();
        ByteArrayOutputStream append = new ByteArrayOutputStream(); DataOutputStream a = new DataOutputStream(append);
        a.writeByte(2); a.writeLong(strokeId.getMostSignificantBits()); a.writeLong(strokeId.getLeastSignificantBits());
        a.writeInt(0); a.writeFloat(3); a.writeInt(0xff000000); a.writeInt(1);
        a.writeFloat(50); a.writeFloat(20); a.writeLong(1); a.writeFloat(1); a.flush();
        writeRecord(out, append.toByteArray()); out.flush();
        Files.write(new File(root, "page.nbk").toPath(), bytes.toByteArray());
        NotebookStore store = new NotebookStore(root); NotebookPage page = store.load();
        check(page.paperSize == null && page.id.equals(id), "v2 IDs retained without invented physical dimensions");
        check(page.strokes.size() == 1 && page.strokes.get(0).id.equals(strokeId), "v2 journal and stroke ID retained");
        check(Arrays.equals(bytes.toByteArray(), Files.readAllBytes(new File(root, "page.nbk").toPath())), "v2 read changes no bytes");
        store.appendStroke(stroke(10));
        NotebookPage migrated = new NotebookStore(root).load();
        check(migrated.paperSize == null && migrated.id.equals(id) && migrated.strokes.size() == 2
            && migrated.strokes.get(0).id.equals(strokeId), "v2 edit retains legacy identity, journal and PDF mapping");
    }

    private static void invalidPhysicalMetadataNeverPublishes() throws Exception {
        File root = directory(); NotebookStore store = new NotebookStore(root); store.load();
        expectIo(() -> store.initializePage(100, 120, NotebookPaperSize.A5, NotebookPage.Template.BLANK));
        check(root.list().length == 0, "mismatched logical and physical geometry creates no files");
        expectIo(() -> NotebookStore.writeSnapshot(new ByteArrayOutputStream(),
            NotebookPage.empty().withSize(100, 120).withPaperSize(NotebookPaperSize.A5)));
        NotebookPage blank = NotebookPage.empty().withSize(1184, 1680);
        check(!NotebookDocument.samePage(blank, blank.withPaperSize(NotebookPaperSize.A5)),
            "retry equality includes physical dimensions");
        File corruptRoot = directory(); NotebookStore valid = new NotebookStore(corruptRoot); valid.load();
        valid.initializePage(NotebookPaperSize.A5, NotebookPage.Template.BLANK);
        byte[] file = Files.readAllBytes(new File(corruptRoot, "page.nbk").toPath());
        byte[] payload = Arrays.copyOfRange(file, 16, 16 + ByteBuffer.wrap(file).getInt(8));
        byte[] unknownFlag = payload.clone(); unknownFlag[25] = 2;
        assertRejectedSnapshot(corruptRoot, unknownFlag, "unknown v3 physical size flag rejected");
        byte[] nonFinite = payload.clone(); ByteBuffer.wrap(nonFinite).putDouble(26, Double.NaN);
        assertRejectedSnapshot(corruptRoot, nonFinite, "non-finite v3 physical dimensions rejected");
        byte[] mismatched = payload.clone(); ByteBuffer.wrap(mismatched).putDouble(26, 149);
        assertRejectedSnapshot(corruptRoot, mismatched, "mismatched stored physical/logical dimensions rejected");
        byte[] outsideBounds = payload.clone(); ByteBuffer.wrap(outsideBounds).putDouble(34, 1001);
        assertRejectedSnapshot(corruptRoot, outsideBounds, "out-of-range v3 physical dimensions rejected");
    }

    private static void assertRejectedSnapshot(File root, byte[] payload, String message) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x4e424b31); out.writeInt(3); writeRecord(out, payload); out.flush();
        byte[] corrupt = bytes.toByteArray(); File file = new File(root, "page.nbk"); Files.write(file.toPath(), corrupt);
        expectIo(() -> new NotebookStore(root).load());
        check(Arrays.equals(corrupt, Files.readAllBytes(file.toPath())), message + " without changing saved bytes");
    }

    private static void writeRecord(DataOutputStream out, byte[] payload) throws Exception {
        CRC32 crc = new CRC32(); crc.update(payload);
        out.writeInt(payload.length); out.writeInt(~payload.length); out.write(payload); out.writeInt((int) crc.getValue());
    }

    private static String pdf(NotebookPage page) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NotebookPdf.write(bytes, "Paper", 1, number -> page, null, NotebookOutlineFixture::outline);
        return new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1);
    }
    private static NotebookLibrary legacyLibrary(File root) throws Exception {
        NotebookLibrary library = new NotebookLibrary(root); library.load(); library.initializeSize(100, 120); return library;
    }
    private static NotebookPage.Stroke stroke(float x) {
        return new NotebookPage.Stroke(0, 3, 0xff000000,
            Collections.singletonList(new NotebookPage.Point(x, 20, 1, 1)));
    }
    private static File directory() throws Exception {
        File result = Files.createTempDirectory("notebook-paper-").toFile(); directories.add(result); return result;
    }
    private static void paper(NotebookPaperSize expected, NotebookPage page, String message) {
        check(expected.equals(page.paperSize) && page.width == expected.logicalWidth()
            && page.height == expected.logicalHeight(), message);
    }
    private static void expectIllegal(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Invalid dimension accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    private static void expectIo(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Invalid operation accepted"); }
        catch (IOException expected) { assertions++; }
    }
    private static void equal(int expected, int actual, String message) { check(expected == actual, message); }
    private static void close(double expected, double actual, String message) { check(Math.abs(expected - actual) < 0.000001, message); }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
