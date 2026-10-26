package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Durable source identity, outline recovery, editable backup and navigation history regressions. */
public final class NotebookPdfModelTest {
    private static int assertions;
    private static final List<File> roots = new ArrayList<>();
    private interface Action { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        try {
            sourcePageSurvivesEditing(); outlineFollowsPageIdentity(); outlineRecovery();
            completeBackupRoundTrip(); legacyBackupRead(); incompleteBackupRejected(); missingSourceFailsClosed(); viewHistory();
            System.out.println("NotebookPdfModel: " + assertions + " assertions passed (host JVM, real files).");
        } finally { for (File root : roots) NotebookBackup.deleteTree(root); }
    }

    private static File root() throws IOException {
        File file = Files.createTempDirectory("ink-pdf-model-").toFile(); roots.add(file); return file;
    }
    private static NotebookPage.Stroke stroke() {
        return new NotebookPage.Stroke(1, 2.5f, 0xff334455, Arrays.asList(
            new NotebookPage.Point(10, 20, 100, .25f), new NotebookPage.Point(20, 30, 110, 1.2f)));
    }
    private static NotebookBook pdfBook(File directory) throws IOException {
        // Byte fixture is deliberately treated as opaque: storage must never rewrite source bytes.
        Files.write(new File(directory, "source.pdf").toPath(), "%PDF-1.7\nsource bytes\n%%EOF\n".getBytes("UTF-8"));
        NotebookStore store = new NotebookStore(directory); store.load(); store.initializePdfPage(600, 800, 7);
        NotebookBook book = new NotebookBook(directory); book.load(); return book;
    }
    private static void sourcePageSurvivesEditing() throws Exception {
        File directory = root(); NotebookBook book = pdfBook(directory);
        NotebookPage original = book.current();
        book.append(stroke()); book.setTemplate(NotebookPage.Template.LINED); book.undo(); book.redo();
        check(book.current().pdfPage == 7, "append, template, undo and redo retain PDF background identity");
        NotebookPage reloaded = new NotebookStore(directory).load();
        check(reloaded.pdfPage == 7 && reloaded.id.equals(original.id), "v4 PDF identity survives process recreation");
        check(!NotebookDocument.samePage(reloaded, reloaded.withPdfPage(8)), "retry validation checks source page identity");
        check(reloaded.withSize(601, 801).pdfPage == 7 && reloaded.withPaperSize(null).pdfPage == 7,
            "all immutable page copies retain source identity");
        book.managePages(NotebookBook.PageAction.DUPLICATE, 1, 1);
        check(book.current().pdfPage == 7 && !book.current().id.equals(original.id), "duplicate has independent ink and same PDF background");
        book.managePages(NotebookBook.PageAction.INSERT_AFTER, 2, 2);
        check(book.current().pdfPage == 0 && book.current().strokes.isEmpty(), "insert creates ordinary blank paper");
        book.addPage(); check(book.current().pdfPage == 0, "append creates ordinary blank paper");
        check(new NotebookStore(directory).load().pdfPage == 7, "page management never alters stored source identity");
        File ordinary = root(); NotebookStore store = new NotebookStore(ordinary); store.load(); store.initializeSize(30, 40);
        expect(() -> store.initializePdfPage(30, 40, 1));
        expect(() -> new NotebookStore(root()).initializePdfPage(30, 40, 1));
        expect(() -> original.withPdfPage(-1));
    }
    private static void outlineFollowsPageIdentity() throws Exception {
        File directory = root(); NotebookBook book = pdfBook(directory);
        UUID first = book.current().id;
        book.addPage(); UUID second = book.current().id;
        List<NotebookOutline.Entry> outline = Arrays.asList(
            new NotebookOutline.Entry("Introduction", first, 0, 0),
            new NotebookOutline.Entry("My derivation", second, .375, 1));
        book.setOutline(outline);
        check(new NotebookBook(directory).sourcePdf().isFile(), "source path independent of load state");
        NotebookBook again = new NotebookBook(directory); again.load();
        check(again.outline().equals(outline), "outline title, nesting, entry IDs and exact destination survive reopen");
        expect(() -> again.outline().clear());
        again.managePages(NotebookBook.PageAction.MOVE, 2, 1);
        check(again.pageNumber(second) == 1 && again.pageNumber(first) == 2, "UUID outline targets follow reordered pages");
        again.managePages(NotebookBook.PageAction.DELETE, 1, 1);
        check(again.pageNumber(second) == 0 && again.outline().equals(outline), "deleted target stays recoverable by page undo");
        again.undoPageChange(); check(again.pageNumber(second) == 1, "undo restores existing outline destination");
        expect(() -> new NotebookOutline.Entry(" ", first, 0, 0));
        expect(() -> new NotebookOutline.Entry("Title", first, 1, 0));
        expect(() -> book.setOutline(Arrays.asList(outline.get(0), outline.get(0))));
        check(NotebookOutline.load(directory).equals(outline), "invalid outline edits preserve acknowledged headings");
    }
    private static void outlineRecovery() throws Exception {
        File directory = root(); UUID page = UUID.randomUUID();
        List<NotebookOutline.Entry> before = Collections.singletonList(new NotebookOutline.Entry("Before", page, 0, 0));
        List<NotebookOutline.Entry> after = Collections.singletonList(new NotebookOutline.Entry("After", page, .5, 0));
        NotebookOutline.save(directory, before);
        File document = new File(directory, "outline.nbo"), backup = new File(directory, "outline.nbo.bak");
        check(document.renameTo(backup), "inject interrupted outline rename");
        check(NotebookOutline.load(directory).equals(before) && !document.exists(), "backup recovery reads without modifying files");
        NotebookOutline.save(directory, after);
        check(NotebookOutline.load(directory).equals(after), "editing after backup recovery publishes exact new outline");
        File temporary = new File(directory, "outline.nbo.tmp"); check(temporary.mkdir(), "inject outline publication failure");
        expect(() -> NotebookOutline.save(directory, before));
        check(NotebookOutline.load(directory).equals(after), "failed heading save preserves last durable outline");
        check(temporary.delete(), "clear injected failure");
        byte[] bytes = Files.readAllBytes(document.toPath()); bytes[bytes.length - 1] ^= 1;
        Files.write(document.toPath(), bytes);
        expect(() -> NotebookOutline.load(directory)); expect(() -> NotebookOutline.save(directory, before));
        check(Arrays.equals(bytes, Files.readAllBytes(document.toPath())), "corrupt primary outline is never overwritten or hidden");
    }
    private static byte[] archive(NotebookBook book) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        NotebookBackup.write(output, "Annotated paper", book.pageCount(), book::previewPage,
            book.sourcePdf(), book.outline(), () -> false);
        return output.toByteArray();
    }
    private static void completeBackupRoundTrip() throws Exception {
        File source = root(); NotebookBook book = pdfBook(source); book.append(stroke());
        List<NotebookOutline.Entry> outline = Collections.singletonList(new NotebookOutline.Entry("Section", book.current().id, .25, 2));
        book.setOutline(outline); byte[] originalSource = Files.readAllBytes(book.sourcePdf().toPath());
        File staging = new File(root(), "restore");
        NotebookBackup.Restored restored = NotebookBackup.read(new ByteArrayInputStream(archive(book)), staging, () -> false);
        NotebookBook copy = new NotebookBook(restored.directory); copy.load();
        check(Arrays.equals(originalSource, Files.readAllBytes(copy.sourcePdf().toPath())), "backup preserves original PDF bytes exactly");
        check(copy.outline().equals(outline), "backup preserves editable nested outline");
        check(NotebookDocument.samePage(book.current(), copy.current()), "backup retains PDF metadata and vector ink exactly");
        File library = root(); NotebookLibrary initial = new NotebookLibrary(library); initial.load();
        NotebookRestore.publish(library, restored); NotebookLibrary reopened = new NotebookLibrary(library); reopened.load();
        check(reopened.entries().size() == 2 && reopened.current().pdfPage == 7, "restored PDF publishes through recoverable catalog transaction");
        File published = new File(new File(library, "notebooks"), "2");
        check(Arrays.equals(originalSource, Files.readAllBytes(new File(published, "source.pdf").toPath())), "catalog publication retains original source asset");
        check(NotebookOutline.load(published).equals(outline), "catalog publication retains outline asset");
        expect(() -> NotebookBackup.write(new ByteArrayOutputStream(), "Incomplete", 1, book::previewPage, () -> false));
    }
    private static void legacyBackupRead() throws Exception {
        NotebookPage page = NotebookPage.empty().withSize(100, 120); ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("manifest")); DataOutputStream header = new DataOutputStream(zip);
            header.writeInt(0x494e4b42); header.writeInt(1); header.writeUTF("Legacy note"); header.writeInt(1); zip.closeEntry();
            zip.putNextEntry(new ZipEntry("pages/1.nbk")); NotebookStore.writeSnapshot(zip, page); zip.closeEntry();
        }
        NotebookBackup.Restored restored = NotebookBackup.read(new ByteArrayInputStream(bytes.toByteArray()), new File(root(), "v1"), () -> false);
        NotebookBook book = new NotebookBook(restored.directory); book.load();
        check(book.current().id.equals(page.id) && book.sourcePdf() == null && book.outline().isEmpty(), "v1 editable backups remain readable");
    }
    private static void incompleteBackupRejected() throws Exception {
        NotebookBook book = pdfBook(root()); byte[] original = archive(book);
        for (String omitted : Arrays.asList("source.pdf", "outline.nbo")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(original)); ZipOutputStream zip = new ZipOutputStream(bytes)) {
                ZipEntry entry; byte[] buffer = new byte[8192]; int length;
                while ((entry = input.getNextEntry()) != null) {
                    if (entry.getName().equals(omitted)) continue;
                    zip.putNextEntry(new ZipEntry(entry.getName()));
                    while ((length = input.read(buffer)) != -1) zip.write(buffer, 0, length);
                    zip.closeEntry();
                }
            }
            File staging = new File(root(), "incomplete");
            expect(() -> NotebookBackup.read(new ByteArrayInputStream(bytes.toByteArray()), staging, () -> false));
            check(!staging.exists(), "missing " + omitted + " rejects restore and cleans private staging");
        }
    }
    private static void missingSourceFailsClosed() throws Exception {
        File directory = root(); NotebookBook cached = pdfBook(directory); cached.addPage();
        byte[] ink = Files.readAllBytes(new File(directory, "page.nbk").toPath());
        check(cached.sourcePdf().delete(), "inject missing original PDF");
        expect(() -> cached.previewPage(1)); expect(() -> cached.page(1));
        NotebookBook reopened = new NotebookBook(directory); reopened.load();
        check(reopened.current().pdfPage == 0, "ordinary selected page can load while PDF source is unavailable");
        expect(() -> reopened.page(1));
        check(Arrays.equals(ink, Files.readAllBytes(new File(directory, "page.nbk").toPath())), "missing source never rewrites PDF-backed ink");

        File emptyDirectory = root(); NotebookBook empty = pdfBook(emptyDirectory);
        Files.write(empty.sourcePdf().toPath(), new byte[0]);
        expect(() -> empty.append(stroke())); expect(() -> new NotebookBook(emptyDirectory).load());
        check(new NotebookStore(emptyDirectory).load().strokes.isEmpty(), "empty source blocks ink changes without changing acknowledged page");

        File library = root(); NotebookLibrary initial = new NotebookLibrary(library); initial.load();
        File staging = new File(root(), "restore"); check(staging.mkdir(), "create missing-source restore fixture");
        NotebookStore blank = new NotebookStore(staging); blank.load(); blank.initializeSize(100, 120);
        NotebookStore background = new NotebookStore(NotebookBackup.pageDirectory(staging, 2));
        background.load(); background.initializePdfPage(100, 120, 1);
        NotebookIndex index = new NotebookIndex(staging); index.load(); index.save(2, 1);
        NotebookBackup.Restored prepared = new NotebookBackup.Restored(staging, "Missing source", 2);
        expect(() -> NotebookRestore.publish(library, prepared));
        check(new NotebookCatalog(library).load().entries.size() == 1, "restore validates original source even when selected page is ordinary");
        File parent = new File(library, "notebooks"); File[] staged = parent.listFiles((dir, name) -> name.startsWith(".restore-"));
        check(staged != null && staged.length == 1 && new File(library, "restore.pending").isFile(), "failed restore preserves recoverable transaction and staged data");
        Files.write(new File(staged[0], "source.pdf").toPath(), "%PDF-1.7\nrestored source\n".getBytes("UTF-8"));
        check(NotebookRestore.recover(library), "restoring missing asset resumes exact transaction");
        check(new NotebookCatalog(library).load().entries.size() == 2, "recovery publishes only after all PDF backgrounds have a source");
    }
    private static void viewHistory() throws Exception {
        UUID page = UUID.randomUUID(), other = UUID.randomUUID();
        NotebookViewHistory.State a = new NotebookViewHistory.State(page, 10, 20, 1, "WIDTH"),
            b = new NotebookViewHistory.State(other, 35, 75, 2.5f, "PAGE"),
            c = new NotebookViewHistory.State(page, -4, 500, .5f, "WIDTH");
        NotebookViewHistory history = new NotebookViewHistory();
        check(!history.canBack() && !history.canForward() && history.back(a) == null, "history starts empty");
        history.record(a); history.record(b);
        check(history.back(c).equals(b), "previous view restores page, exact center, zoom and fit");
        check(history.back(b).equals(a) && history.canForward(), "history supports successive back operations");
        check(history.forward(a).equals(b) && history.forward(b).equals(c), "forward restores the departed views");
        history.back(c); history.record(a); check(!history.canForward(), "following a new destination discards forward branch");
        history.clear(); check(!history.canBack() && !history.canForward(), "switching notebooks clears view history");
        for (int i = 0; i < 150; i++) history.record(new NotebookViewHistory.State(page, 0, i, 1, "WIDTH"));
        int count = 0; while (history.canBack()) { history.back(c); count++; }
        check(count == 100, "history has a bounded process-local footprint");
        expect(() -> new NotebookViewHistory.State(page, Float.NaN, 0, 1, "WIDTH"));
        expect(() -> new NotebookViewHistory.State(page, 0, 0, 0, "WIDTH"));
    }
    private static void expect(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Expected rejection"); }
        catch (IOException | IllegalArgumentException | IllegalStateException | UnsupportedOperationException expected) { assertions++; }
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
