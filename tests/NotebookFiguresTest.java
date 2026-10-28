package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.CRC32;

public final class NotebookFiguresTest {
    private static int assertions;
    private static final List<File> roots = new ArrayList<>();
    private interface Action { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        try {
            immutableGeometry(); durableHistory(); backupAndPageCommands(); versionFiveMigration(); companionReads(); exportCrop();
            System.out.println("NotebookFigures: " + assertions + " assertions passed (figures, backup, crop and isolated reads).");
        } finally { for (File root : roots) NotebookBackup.deleteTree(root); }
    }
    private static void immutableGeometry() throws Exception {
        byte[] bytes = NotebookFigureFixture.png(true); byte[] original = bytes.clone();
        NotebookFigure figure = new NotebookFigure(UUID.randomUUID(), 10, 20, 30, 40, bytes);
        bytes[0] = 0; byte[] copy = figure.png(); copy[0] = 0;
        check(Arrays.equals(original, figure.png()), "Figure never exposes mutable stored pixels");
        NotebookPage page = NotebookPage.empty().withSize(200, 300).withFigures(Collections.singletonList(figure));
        check(page.withTemplate(NotebookPage.Template.LINED).withStroke(stroke()).withStrokes(Collections.emptyList()).figures.equals(page.figures), "Ink and paper copies retain figures");
        reject(() -> page.withFigures(Arrays.asList(figure, figure)));
        reject(() -> page.withFigures(Collections.singletonList(figure.placed(190, 0, 30, 30))));
        reject(() -> figure.placed(Float.NaN, 0, 1, 1));
        reject(() -> page.figures.clear());
        byte[] oversized = original.clone(); ByteBuffer.wrap(oversized).putInt(16, 1000000);
        reject(() -> new NotebookFigure(UUID.randomUUID(), 0, 0, 10, 10, oversized));
        check(!NotebookDocument.samePage(page, page.withFigures(Collections.emptyList())), "Disk conflict detection includes figures");
        reject(() -> NotebookPdf.write(new ByteArrayOutputStream(), "Figure", 1, number -> page, null, stroke -> null));
    }
    private static void durableHistory() throws Exception {
        File root = root(); NotebookDocument document = new NotebookDocument(root); document.load(); document.initializeSize(200, 300);
        NotebookFigure figure = figure(); document.setFigures(Collections.singletonList(figure)); document.append(stroke());
        document.undo(); check(document.current().figures.size() == 1 && document.current().strokes.isEmpty(), "Undo writing keeps figures");
        document.undo(); check(document.current().figures.isEmpty(), "Undo removes insertion");
        document.redo(); document.redo();
        document.setFigures(Collections.singletonList(figure.placed(40, 50, 50, 60)));
        document.undo(); check(document.current().figures.get(0).equals(figure), "Undo placement restores exact geometry");
        document.redo();
        check(NotebookDocument.samePage(document.current(), new NotebookStore(root).load()), "Placement persists across reopen");
        File blocker = new File(root, "page.nbk.tmp"); check(blocker.mkdir(), "Inject publication failure");
        reject(() -> document.setFigures(Collections.emptyList()));
        check(document.current().figures.isEmpty(), "Failed deletion retains intended page in memory");
        check(blocker.delete(), "Remove failure"); document.retry();
        check(new NotebookStore(root).load().figures.isEmpty(), "Retry durably publishes figure deletion");
        document.undo(); check(document.current().figures.size() == 1, "Retry preserves undo history");
    }
    private static void backupAndPageCommands() throws Exception {
        NotebookBook book = new NotebookBook(root()); book.load(); book.initializeSize(200, 300); book.setFigures(1, Collections.singletonList(figure())); book.append(stroke());
        NotebookPage original = book.current();
        book.managePages(NotebookBook.PageAction.DUPLICATE, 1, 1);
        check(book.current().figures.size() == 1 && !book.current().figures.get(0).id.equals(original.figures.get(0).id), "Duplicate page gives figures independent identities");
        check(Arrays.equals(book.current().figures.get(0).png(), original.figures.get(0).png()), "Duplicate retains figure pixels");
        book.managePages(NotebookBook.PageAction.INSERT_AFTER, 2, 2);
        check(book.current().figures.isEmpty(), "Inserted blank page never inherits figures");
        book.moveSelection(1, 3, NotebookLasso.all(book.page(1)), 100, 100);
        book.undoOnPage(3);
        check(book.page(1).figures.equals(original.figures) && book.page(3).figures.isEmpty(), "Linked stroke undo leaves both figure layers unchanged");
        ByteArrayOutputStream archive = new ByteArrayOutputStream();
        NotebookBackup.write(archive, "Figures", book.pageCount(), book::previewPage, () -> false);
        NotebookBackup.Restored restored = NotebookBackup.read(new ByteArrayInputStream(archive.toByteArray()), new File(root(), "restore"), () -> false);
        NotebookBook copy = new NotebookBook(restored.directory); copy.load();
        for (int i = 1; i <= book.pageCount(); i++) check(NotebookDocument.samePage(book.page(i), copy.page(i)), "Backup round trip retains figures and ink on page " + i);
    }
    private static void versionFiveMigration() throws Exception {
        File root = root(); NotebookPage page = NotebookPage.empty().withSize(200, 300).withStroke(stroke());
        ByteArrayOutputStream encoded = new ByteArrayOutputStream(); NotebookStore.writeSnapshot(encoded, page);
        byte[] v6 = encoded.toByteArray(); int size = ByteBuffer.wrap(v6).getInt(8) - 4;
        ByteArrayOutputStream fixture = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(fixture);
        out.writeInt(0x4e424b31); out.writeInt(5); out.writeInt(size); out.writeInt(~size); out.write(v6, 16, size);
        CRC32 crc = new CRC32(); crc.update(v6, 16, size); out.writeInt((int) crc.getValue());
        File path = new File(root, "page.nbk"); Files.write(path.toPath(), fixture.toByteArray());
        NotebookDocument document = new NotebookDocument(root); document.load();
        check(NotebookDocument.samePage(page, document.current()), "v5 page reads with an empty figure layer");
        check(Arrays.equals(fixture.toByteArray(), Files.readAllBytes(path.toPath())), "Reading v5 does not migrate it");
        document.setFigures(Collections.singletonList(figure()));
        check(ByteBuffer.wrap(Files.readAllBytes(path.toPath())).getInt(4) == 6, "Figure edit publishes v6");
        check(Arrays.equals(fixture.toByteArray(), Files.readAllBytes(new File(root, "page.nbk.bak").toPath())), "Migration preserves exact v5 predecessor");
    }
    private static void companionReads() throws Exception {
        File root = root(); NotebookLibrary library = new NotebookLibrary(root); library.load(); library.initializeSize(200, 300); library.append(stroke());
        NotebookPage first = library.current(); library.createNotebook("Second"); library.initializeSize(200, 300); library.append(stroke()); library.addPage();
        int second = library.notebookId(); library.openNotebook(1);
        byte[] catalog = Files.readAllBytes(new File(root, "library.nbl").toPath());
        check(library.companionMetadata(second).size() == 2 && library.companionMetadata(second).get(0).width == 200, "Companion metadata supports continuous layout without changing selection");
        check(library.companionCount(second) == 2 && library.companionPage(second, 1).strokes.size() == 1, "Companion can navigate a cached other notebook");
        check(library.notebookId() == 1 && library.current() == first && library.canUndo(), "Companion reads preserve active document and history");
        check(Arrays.equals(catalog, Files.readAllBytes(new File(root, "library.nbl").toPath())), "Companion reads never change catalog selection");
        NotebookLibrary cold = new NotebookLibrary(root); cold.load();
        check(cold.companionPage(second, 1).strokes.size() == 1 && cold.notebookId() == 1, "Cold companion opens independently of active selection");
        library.openNotebook(second); library.undoOnPage(1); library.openNotebook(1); library.undo();
        check(library.current().strokes.isEmpty(), "Switching editing focus retains each book's undo history");
    }
    private static void exportCrop() throws Exception {
        NotebookPage.Stroke stroke = new NotebookPage.Stroke(1, 4, 0x804080ff, Collections.singletonList(new NotebookPage.Point(100, 200, 1, 1)));
        NotebookSelectionExport export = new NotebookSelectionExport(Collections.singletonList(stroke), ignored ->
            new NotebookStrokeOutline(new float[]{0, 95, 190, .25f, 108, 190, .5f, 108, 215, 1, 95, 215}, true));
        check(export.left == 87 && export.top == 182 && export.width == 29 && export.height == 41, "Crop includes pressure footprint and padding");
        ByteArrayOutputStream output = new ByteArrayOutputStream(); export.writeSvg(output); String svg = output.toString("UTF-8");
        check(svg.contains("viewBox=\"0 0 29 41\"") && svg.contains("M8 8L21 8L21 33L8 33Z"), "SVG translates outlines into a tight positive crop");
        check(svg.contains("#4080ff") && svg.contains("fill-opacity=\"0.5019608\"") && svg.contains("evenodd"), "SVG preserves color, alpha, and fill rule");
        javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(output.toByteArray()));
        reject(() -> new NotebookSelectionExport(Collections.emptyList(), ignored -> null));
    }
    private static NotebookFigure figure() throws IOException { return new NotebookFigure(UUID.randomUUID(), 10, 20, 30, 40, NotebookFigureFixture.png(true)); }
    private static NotebookPage.Stroke stroke() { return new NotebookPage.Stroke(1, 2, 0xff000000, Collections.singletonList(new NotebookPage.Point(30, 40, 10, 1))); }
    private static File root() throws IOException { File root = Files.createTempDirectory("notebook-figures-").toFile(); roots.add(root); return root; }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void reject(Action action) throws Exception { try { action.run(); } catch (IOException | IllegalArgumentException | UnsupportedOperationException expected) { assertions++; return; } throw new AssertionError("Expected rejected input/failure"); }
}
