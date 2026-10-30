package org.eink.inkprobe.notebook;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;

/** Production viewport splitting through library/book persistence and process-style reopen. */
public final class NotebookViewportSaveTest {
    private static int assertions;
    private static long eventTime = 100;
    private static final byte[] SOURCE = "%PDF-1.4\noriginal-source-fixture\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("notebook-viewport-save-").toFile();
        try {
            exercise(new File(root, "legacy"), 4);
            exercise(new File(root, "new"), 5);
            exercise(new File(root, "pdf"), 0);
            System.out.println("NotebookViewportSave: " + assertions
                + " assertions passed (viewport to library/book, real files, migration and reopen).");
        } finally { NotebookBackup.deleteTree(root); }
    }

    private static void exercise(File root, int version) throws Exception {
        List<NotebookViewport.Size> sizes = Arrays.asList(new NotebookViewport.Size(1004, 1600),
            new NotebookViewport.Size(version == 0 ? 1201 : 1004, version == 0 ? 1703 : 1600),
            new NotebookViewport.Size(version == 0 ? 901 : 1004, version == 0 ? 1409 : 1600));
        NotebookLibrary library = fixture(root, version, sizes);
        int notebook = library.notebookId();
        File book = library.currentDirectory();
        Map<Integer, NotebookPage> expected = new LinkedHashMap<>();
        Map<Integer, byte[]> oldFiles = new LinkedHashMap<>();
        for (int number = 1; number <= sizes.size(); number++) {
            expected.put(number, library.page(number));
            oldFiles.put(number, Files.readAllBytes(pageFile(book, number).toPath()));
        }
        int initialSelection = library.pageNumber();
        int gestures = 0;
        for (float zoom : new float[]{.25f, .5f, 1f, 1.125f, 2f, 4f}) {
            NotebookViewport viewport = new NotebookViewport(sizes, 900, 650, 24, zoom,
                Math.max(0, 900 * zoom - 900) / 3);
            for (float scroll : new float[]{0, viewport.scrollForPoint(2, sizes.get(1).height * .45f, 325),
                    viewport.maxScroll()}) {
                // Each visible page gets exact viewport/page-border samples and an interior sample.
                for (int number : viewport.visiblePages(scroll)) {
                    float left = Math.max(0, viewport.pageLeft(number));
                    float right = Math.min(viewport.width, viewport.pageRight(number));
                    float top = Math.max(0, viewport.pageTop(number, scroll));
                    float bottom = Math.min(viewport.height,
                        viewport.pageTop(number, scroll) + viewport.pageHeight(number) * viewport.scale(number));
                    List<NotebookPage.Point> points = Arrays.asList(point(left, top, .1f),
                        point((left + right) / 2, (top + bottom) / 2, .8f), point(right, bottom, 0));
                    save(library, expected, viewport, scroll, new NotebookPage.Stroke(2,
                        gestures % 2 == 0 ? 4f : 4.1f, 0xff000000, points, 1f));
                    gestures++;
                    // A single-sample tap at an edge must also produce a durable stroke.
                    save(library, expected, viewport, scroll, new NotebookPage.Stroke(2, 3.9f,
                        0xff000000, Arrays.asList(point(left, bottom, .2f)), 1.25f, 4.875f));
                    gestures++;
                }
                if (viewport.visiblePages(scroll).size() > 1) {
                    // One completed gesture crosses page borders and gaps before being split.
                    save(library, expected, viewport, scroll, new NotebookPage.Stroke(2, 6.25f,
                        0xff000000, Arrays.asList(point(450, 0, .3f), point(450, 325, 1f),
                            point(450, 650, 0)), .9f, 5.625f));
                    gestures++;
                }
                check(library.pageNumber() == initialSelection, "Viewport saving never publishes page navigation");
                verify(library, book, expected, version == 0);
                // Refresh the same navigation metadata consulted by the Android session.
                check(library.navigationMetadata().size() == sizes.size(), "Navigation metadata stays available after writing");
                library.load();
            }
            library = new NotebookLibrary(root); library.load(); library.openNotebook(notebook);
            check(library.error() == null, "Cold reopen remains writable after each zoom batch");
            verify(library, book, expected, version == 0);
        }
        check(gestures > 30, "Fixture persists repeated gestures, not only the first append");
        if (version == 4) for (int number = 1; number <= sizes.size(); number++) {
            byte[] current = Files.readAllBytes(pageFile(book, number).toPath());
            check(java.nio.ByteBuffer.wrap(current).getInt(4) == 8, "First viewport edit migrates the legacy page to v8");
            check(Arrays.equals(oldFiles.get(number), Files.readAllBytes(
                new File(pageFile(book, number).getParentFile(), "page.nbk.bak").toPath())),
                "Migration preserves the exact predecessor page as backup");
        }
        if (version == 0)
            check(Arrays.equals(SOURCE, Files.readAllBytes(new File(book, "source.pdf").toPath())),
                "Repeated PDF-note saves never modify the original source PDF");
    }

    private static void save(NotebookLibrary library, Map<Integer, NotebookPage> expected,
            NotebookViewport viewport, float scroll, NotebookPage.Stroke input) throws Exception {
        Map<Integer, List<NotebookPage.Stroke>> split = viewport.splitStroke(input, scroll);
        check(!split.isEmpty(), "An on-page completed stroke has durable pieces");
        for (Map.Entry<Integer, List<NotebookPage.Stroke>> entry : split.entrySet()) {
            int number = entry.getKey();
            for (NotebookPage.Stroke piece : entry.getValue()) {
                sameFloat(input.width / viewport.scale(number), piece.width, "Inverse fitting preserves nominal width");
                sameFloat(input.captureScale * viewport.scale(number), piece.captureScale, "Inverse fitting preserves capture scale");
                sameFloat(input.captureWidth, piece.captureWidth, "Inverse fitting retains exact native brush width");
                check(piece.clip == null, "Viewport-only clip state does not enter durable pieces");
                for (NotebookPage.Point point : piece.points)
                    check(point.x >= 0 && point.y >= 0 && point.x <= viewport.pageWidth(number)
                        && point.y <= viewport.pageHeight(number), "Border samples stay inside durable page dimensions");
            }
            // This is the same per-page operation used by NotebookSession.appendViewport().
            library.appendStrokesToPage(number, entry.getValue());
            NotebookPage page = expected.get(number);
            for (NotebookPage.Stroke piece : entry.getValue()) page = page.withStroke(piece);
            expected.put(number, page);
            check(library.error() == null, "A completed viewport stroke does not latch a save error");
        }
    }

    private static void verify(NotebookLibrary library, File book, Map<Integer, NotebookPage> expected,
            boolean pdf) throws Exception {
        for (Map.Entry<Integer, NotebookPage> entry : expected.entrySet()) {
            NotebookPage disk = new NotebookStore(pageFile(book, entry.getKey()).getParentFile()).load();
            samePage(entry.getValue(), disk);
            samePage(entry.getValue(), library.page(entry.getKey()));
            check(disk.pdfPage == (pdf ? entry.getKey() : 0), "Source PDF page association survives each save and reopen");
        }
    }

    private static void samePage(NotebookPage expected, NotebookPage actual) {
        check(expected.id.equals(actual.id) && expected.width == actual.width && expected.height == actual.height
            && expected.template == actual.template && expected.pdfPage == actual.pdfPage,
            "Page identity, geometry, paper and PDF association remain exact");
        check(expected.strokes.size() == actual.strokes.size() && expected.pointCount == actual.pointCount,
            "Every completed split piece and sample survives storage");
        for (int i = 0; i < expected.strokes.size(); i++) {
            NotebookPage.Stroke a = expected.strokes.get(i), b = actual.strokes.get(i);
            check(a.id.equals(b.id) && a.penType == b.penType && a.color == b.color,
                "Stroke identity and style survive storage");
            sameFloat(a.width, b.width, "Saved nominal width is bit exact");
            sameFloat(a.captureScale, b.captureScale, "Saved capture scale is bit exact");
            sameFloat(a.captureWidth, b.captureWidth, "Saved native width is bit exact");
            check(a.points.size() == b.points.size(), "Sample count remains exact");
            for (int p = 0; p < a.points.size(); p++) {
                NotebookPage.Point x = a.points.get(p), y = b.points.get(p);
                sameFloat(x.x, y.x, "Saved x is bit exact"); sameFloat(x.y, y.y, "Saved y is bit exact");
                sameFloat(x.pressure, y.pressure, "Saved pressure is bit exact");
                check(x.time == y.time, "Saved event time is exact");
            }
        }
    }

    private static NotebookLibrary fixture(File root, int version, List<NotebookViewport.Size> sizes) throws Exception {
        check(root.mkdirs(), "Create isolated library fixture");
        if (version == 4) {
            for (int number = 1; number <= sizes.size(); number++)
                legacyPage(pageFile(root, number), sizes.get(number - 1));
            NotebookIndex index = new NotebookIndex(root); index.load(); index.initializeImported(sizes.size());
        }
        NotebookLibrary library = new NotebookLibrary(root); library.load();
        if (version == 5) {
            library.createNotebook("New viewport note"); library.initializeSize(sizes.get(0).width, sizes.get(0).height);
            for (int number = 2; number <= sizes.size(); number++) library.addPage();
        } else if (version == 0) {
            File stage = new File(root, "staging"); check(stage.mkdir(), "Create unpublished PDF stage");
            Files.write(new File(stage, "source.pdf").toPath(), SOURCE);
            List<NotebookPage> pages = NotebookPdfStaging.pages(stage, sizes);
            NotebookIndex index = new NotebookIndex(stage); index.load(); index.initializeImported(pages.size());
            NotebookPageCatalog.seed(stage, pages);
            library.importNotebook(new NotebookBackup.Restored(stage, "Imported viewport PDF", pages.size()));
            library.load();
        }
        return library;
    }

    private static void legacyPage(File file, NotebookViewport.Size size) throws Exception {
        check(file.getParentFile().isDirectory() || file.getParentFile().mkdirs(), "Create legacy page directory");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream payload = new DataOutputStream(bytes);
        UUID page = UUID.randomUUID(), stroke = UUID.randomUUID();
        payload.writeByte(1); id(payload, page); payload.writeInt(size.width); payload.writeInt(size.height);
        payload.writeByte(0); payload.writeInt(0); payload.writeByte(0); payload.writeInt(1);
        id(payload, stroke); payload.writeInt(2); payload.writeFloat(4); payload.writeInt(0xff000000);
        payload.writeInt(1); payload.writeFloat(30); payload.writeFloat(40); payload.writeLong(25); payload.writeFloat(.2f);
        byte[] record = bytes.toByteArray(); CRC32 crc = new CRC32(); crc.update(record);
        ByteArrayOutputStream framed = new ByteArrayOutputStream(); DataOutputStream output = new DataOutputStream(framed);
        output.writeInt(0x4e424b31); output.writeInt(4); output.writeInt(record.length); output.writeInt(~record.length);
        output.write(record); output.writeInt((int) crc.getValue());
        Files.write(file.toPath(), framed.toByteArray());
    }

    private static NotebookPage.Point point(float x, float y, float pressure) {
        return new NotebookPage.Point(x, y, eventTime++, pressure);
    }
    private static File pageFile(File book, int number) {
        return new File(NotebookBackup.pageDirectory(book, number), "page.nbk");
    }
    private static void id(DataOutputStream out, UUID value) throws Exception {
        out.writeLong(value.getMostSignificantBits()); out.writeLong(value.getLeastSignificantBits());
    }
    private static void sameFloat(float expected, float actual, String message) {
        check(Float.floatToIntBits(expected) == Float.floatToIntBits(actual), message + ": " + expected + " / " + actual);
    }
    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
}
