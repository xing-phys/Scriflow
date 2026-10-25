package org.eink.inkprobe.notebook;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Structural PDF and streaming checks, independent of Android and native ink. */
public final class NotebookPdfTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        vectorsPaperAndMetadata();
        sourceOrderProgressAndCallerOwnership();
        failuresAndCancellation();
        numericPrecisionAndLongPath();
        extremeGeometryFitsPdfLimits();
        libraryExportIsReadOnlyAndKeepsHistory();
        corruptLaterPageDoesNotPoisonEditor();
        writeVisualFixture();
        System.out.println("NotebookPdf: " + assertions + " assertions passed (streaming vector PDF).");
    }

    private static void vectorsPaperAndMetadata() throws Exception {
        List<NotebookPage> pages = Arrays.asList(
            page(100, 200, NotebookPage.Template.LINED,
                stroke(2.5f, 0x80ff0000, point(10, 20), point(80, 90)),
                stroke(8, 0xff000000, point(30, 40)),
                stroke(6, 0xff0000ff, point(50, 60), point(50, 60), point(50, 60))),
            page(200, 100, NotebookPage.Template.BLANK,
                stroke(4, 0xff00ff00, point(-10, 25), point(220, 75))));
        String title = "实验笔记 (study) \\ 🖊";
        byte[] bytes = export(title, pages);
        String pdf = ascii(bytes);
        check(pdf.startsWith("%PDF-1.4\n"), "valid PDF version header");
        contains(pdf, "/Count 2 /Kids [5 0 R 8 0 R ]", "all pages in original order");
        contains(pdf, "/MediaBox [0 0 595.2756 1190.5512]", "portrait source ratio preserved");
        contains(pdf, "/MediaBox [0 0 595.2756 297.6378]", "landscape source ratio preserved");
        contains(pdf, "/Title <FEFF" + hex(title.getBytes(StandardCharsets.UTF_16BE)) + ">",
            "Unicode metadata encoded as UTF-16BE including surrogate pair");
        check(!pdf.contains(title), "metadata cannot inject PDF syntax");
        String first = content(pdf, 6), second = content(pdf, 9);
        contains(first, "5.952756 0 0 -5.952756 0 1190.5512 cm", "top-left coordinates map to PDF");
        contains(first, "0 0 100 200 re W n", "page bounds clip content");
        contains(second, "0 0 200 100 re W n", "each page has its own clip");
        contains(first, "1 1 1 rg\n0 0 100 200 re f", "explicit white paper");
        contains(first, "0 72 m 100 72 l S", "first rule at logical y72");
        contains(first, "0 144 m 100 144 l S", "ruling repeats every72");
        check(!first.contains("0 216 m"), "rules stop at page bottom");
        check(!second.contains("0.823529"), "blank template has no rules");
        contains(first, "1 J\n1 j", "round line caps and joins");
        contains(first, "1 0 0 RG\n1 0 0 rg\n2.5 w", "recorded RGB and stroke width retained");
        contains(first, "10 20 m\n80 90 l\nS", "original vectors retained without rasterization");
        contains(second, "-10 25 m\n220 75 l\nS", "off-page source vectors use explicit page clip");
        equal(8, occurrences(first, " c\n"), "single-point and repeated-point dots use filled circles");
        contains(first, "34 40 m", "dot radius equals half recorded width");
        contains(first, "53 60 m", "repeated identical points retain visible dot");
        contains(pdf, "/CA 0.501961 /ca 0.501961", "ARGB stroke opacity retained");
        equal(1, occurrences(pdf, "/Type /ExtGState /CA 1 /ca 1"), "opaque alpha resource reused");
        check(!pdf.contains("/Subtype /Image"), "ink is exported as vectors");
        verifyStructure(bytes, 2);
    }

    private static void sourceOrderProgressAndCallerOwnership() throws Exception {
        final List<Integer> reads = new ArrayList<>(), progress = new ArrayList<>();
        final TrackingOutput output = new TrackingOutput();
        final int total = 250;
        NotebookPdf.write(output, "Many pages", total, number -> {
            check(number == reads.size() + 1, "source reads strictly sequentially once");
            check(progress.size() == number - 1, "previous page completes before next source read");
            reads.add(number);
            return page(300, 400, number % 2 == 0 ? NotebookPage.Template.LINED : NotebookPage.Template.BLANK);
        }, (done, count) -> {
            check(count == total && done == progress.size() + 1, "progress ordered with stable total");
            check(reads.size() == done, "no prefetching across progress barrier");
            progress.add(done);
        });
        equal(total, reads.size(), "all source pages read exactly once");
        equal(total, progress.size(), "one acknowledgement per page");
        check(!output.closed && output.flushed, "caller output remains open and success flushes");
        output.write(32);
        check(output.bytes.size() > 32, "caller can continue using output stream");
        byte[] bytes = output.bytes.toByteArray();
        verifyStructure(Arrays.copyOf(bytes, bytes.length - 1), total);
    }

    private static void failuresAndCancellation() throws Exception {
        NotebookPage valid = page(100, 200, NotebookPage.Template.BLANK);
        expectIo(() -> NotebookPdf.write(null, "title", 1, n -> valid, null), "missing output rejected");
        expectIo(() -> NotebookPdf.write(new ByteArrayOutputStream(), null, 1, n -> valid, null), "missing title rejected");
        expectIo(() -> NotebookPdf.write(new ByteArrayOutputStream(), "title", 0, n -> valid, null), "zero pages rejected");
        expectIo(() -> NotebookPdf.write(new ByteArrayOutputStream(), "title", -1, n -> valid, null), "negative pages rejected");
        expectIo(() -> NotebookPdf.write(new ByteArrayOutputStream(), "title", Integer.MAX_VALUE, n -> valid, null),
            "oversized export rejected without allocating huge xref");
        expectIo(() -> NotebookPdf.write(new ByteArrayOutputStream(), "title", 1, null, null), "missing source rejected");
        expectIo(() -> NotebookPdf.write(new ByteArrayOutputStream(), "title", 1, n -> null, null), "missing page rejected");
        expectIo(() -> NotebookPdf.write(new ByteArrayOutputStream(), "title", 1, n -> NotebookPage.empty(), null),
            "uninitialized page rejected");
        expectIo(() -> NotebookPdf.write(new ByteArrayOutputStream(), "title", 1,
            n -> page(40000, 10, NotebookPage.Template.BLANK), null), "invalid geometry rejected");
        expectIo(() -> NotebookPdf.write(new ByteArrayOutputStream(), "title", 1,
            n -> page(100, 100, null), null), "invalid template rejected");
        final int[] reads = {0}, completed = {0};
        TrackingOutput sourceFailure = new TrackingOutput();
        expectIo(() -> NotebookPdf.write(sourceFailure, "title", 3, n -> {
            reads[0]++;
            if (n == 2) throw new IOException("source failed");
            return valid;
        }, (done, total) -> completed[0]++), "source failure propagates");
        equal(2, reads[0], "source failure prevents reading later pages");
        equal(1, completed[0], "only completed page acknowledged before source failure");
        check(!sourceFailure.closed, "source failure does not close caller stream");
        expectIo(() -> NotebookPdf.write(new ByteArrayOutputStream(), "title", 1,
            n -> { throw new IllegalStateException("source bug"); }, null), "runtime source error becomes IOException");
        reads[0] = 0;
        TrackingOutput cancelled = new TrackingOutput();
        expectIo(() -> NotebookPdf.write(cancelled, "cancel", 10, n -> { reads[0]++; return valid; },
            (done, total) -> { if (done == 2) throw new IOException("cancelled"); }), "progress cancellation propagates");
        equal(2, reads[0], "cancellation prevents further source reads");
        check(!cancelled.closed, "cancellation does not close output");
        TrackingOutput failedWrite = new TrackingOutput() {
            @Override public void write(byte[] data, int start, int length) throws IOException {
                if (bytes.size() + length > 80) throw new IOException("full destination");
                super.write(data, start, length);
            }
        };
        expectIo(() -> NotebookPdf.write(failedWrite, "write", 1, n -> valid, null), "destination write error propagates");
        check(!failedWrite.closed, "write failure preserves caller ownership");
        TrackingOutput failedFlush = new TrackingOutput() {
            @Override public void flush() throws IOException { throw new IOException("flush failed"); }
        };
        expectIo(() -> NotebookPdf.write(failedFlush, "flush", 1, n -> valid, null), "destination flush error propagates");
        check(!failedFlush.closed, "flush failure preserves caller ownership");
    }

    private static void numericPrecisionAndLongPath() throws Exception {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            List<NotebookPage.Point> points = new ArrayList<>();
            for (int i = 0; i < 12000; i++) points.add(point(i % 1000 / 10f, i % 500 / 5f));
            NotebookPage.Stroke longStroke = new NotebookPage.Stroke(0, 1.25f, 0x40010203, points);
            NotebookPage page = page(100, 100, NotebookPage.Template.BLANK, longStroke,
                stroke(0.00001f, 0xff000000, point(0.0000001f, 0.000001f)));
            byte[] bytes = export("Precision", Collections.singletonList(page));
            String pdf = ascii(bytes), content = content(pdf, 6);
            contains(content, "1.25 w", "width uses locale independent decimal separator");
            contains(content, "0.00001 w", "tiny width avoids PDF-invalid exponent notation");
            contains(content, "0.0000001 0.000001 m", "tiny coordinates avoid exponent notation");
            equal(points.size() - 1, occurrences(content, " l\n"), "long stroke streamed without omitted segments");
            equal(1, occurrences(content, "S\n"), "translucent long stroke has no chunk overlap seams");
            check(!pdf.contains("NaN") && !pdf.contains("Infinity"), "all PDF numeric output finite");
            check(!content.contains(","), "non-English host locale cannot corrupt PDF numbers");
            verifyStructure(bytes, 1);
        } finally { Locale.setDefault(previous); }
    }

    private static void extremeGeometryFitsPdfLimits() throws Exception {
        byte[] bytes = export("Extreme page ratio", Arrays.asList(
            page(1, NotebookStore.MAX_DIMENSION, NotebookPage.Template.BLANK),
            page(NotebookStore.MAX_DIMENSION, 1, NotebookPage.Template.BLANK)));
        String pdf = ascii(bytes);
        contains(pdf, "/MediaBox [0 0 0.439453 14400]", "extreme tall page stays within PDF 1.4 extent");
        contains(pdf, "0.439453 0 0 -0.439453 0 14400 cm", "height cap preserves source aspect by uniform scaling");
        contains(pdf, "/MediaBox [0 0 595.2756 0.018166]", "extreme wide page retains A4-width normalization");
        verifyStructure(bytes, 2);
    }

    private static void libraryExportIsReadOnlyAndKeepsHistory() throws Exception {
        File root = Files.createTempDirectory("notebook-pdf-library-").toFile();
        try {
            prepareLibrary(root);
            NotebookLibrary library = new NotebookLibrary(root);
            library.load();
            library.append(stroke(3, 0xff000000, point(55, 70)));
            library.append(stroke(3, 0xff000000, point(65, 80)));
            library.undo();
            check(library.canUndo() && library.canRedo(), "PDF fixture has both undo and redo history");
            expectIo(() -> library.cachedPage(2), "second source page initially uncached");
            expectIo(() -> library.cachedPage(3), "third source page initially uncached");
            NotebookPage accepted = library.current();
            NotebookScrollStore.State position = library.scrollPosition();
            int selected = library.pageNumber(), notebook = library.notebookId();
            Map<String, byte[]> before = treeBytes(root);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            NotebookPdf.write(output, library.notebookName(), library.pageCount(), library::previewPage, null);
            verifyStructure(output.toByteArray(), 3);
            contains(content(ascii(output.toByteArray()), 6), "55 70 m", "export includes accepted current page edits");
            check(library.current() == accepted, "PDF export retains exact current immutable model");
            equal(selected, library.pageNumber(), "PDF export does not change selected page");
            equal(notebook, library.notebookId(), "PDF export does not change selected notebook");
            check(position.equals(library.scrollPosition()), "PDF export does not change fractional scroll anchor");
            check(library.canUndo() && library.canRedo(), "PDF export preserves both edit history directions");
            check(library.error() == null, "successful PDF export does not latch editor error");
            expectIo(() -> library.cachedPage(2), "export does not retain second source page in editor cache");
            expectIo(() -> library.cachedPage(3), "export does not retain third source page in editor cache");
            assertTreeBytes(before, treeBytes(root), "successful export");
            library.redo();
            equal(accepted.strokes.size() + 1, library.current().strokes.size(), "pre-export redo still restores exact pending edit");
            equal(65, (int) library.current().strokes.get(library.current().strokes.size() - 1).points.get(0).x,
                "redo restores the original point after export");
            library.undo();
            equal(accepted.strokes.size(), library.current().strokes.size(), "undo remains operational after export");
        } finally { deleteTree(root); }
    }

    private static void corruptLaterPageDoesNotPoisonEditor() throws Exception {
        File root = Files.createTempDirectory("notebook-pdf-corrupt-").toFile();
        try {
            prepareLibrary(root);
            NotebookLibrary library = new NotebookLibrary(root);
            library.load();
            expectIo(() -> library.cachedPage(3), "later corrupt source page starts uncached");
            File badFile = new File(root, "pages/3/page.nbk");
            byte[] corrupt = Files.readAllBytes(badFile.toPath());
            corrupt[0] ^= 1;
            Files.write(badFile.toPath(), corrupt);
            NotebookPage accepted = library.current();
            NotebookScrollStore.State position = library.scrollPosition();
            Map<String, byte[]> before = treeBytes(root);
            final List<Integer> reads = new ArrayList<>(), completed = new ArrayList<>();
            expectIo(() -> NotebookPdf.write(new ByteArrayOutputStream(), library.notebookName(), library.pageCount(),
                n -> { reads.add(n); return library.previewPage(n); }, (done, total) -> completed.add(done)),
                "whole-book PDF export aborts at corrupt later page");
            check(reads.equals(Arrays.asList(1, 2, 3)), "failed export visits original pages in order");
            check(completed.equals(Arrays.asList(1, 2)), "corrupt page never receives successful progress");
            check(library.error() == null, "export read failure does not latch library/editor error");
            check(library.current() == accepted && library.pageNumber() == 1 && library.notebookId() == 1,
                "failed export preserves healthy current editor identity and selection");
            check(position.equals(library.scrollPosition()), "failed export preserves exact scroll position");
            expectIo(() -> library.cachedPage(2), "failed export does not cache earlier preview-only page");
            expectIo(() -> library.cachedPage(3), "failed export does not cache corrupt source page");
            assertTreeBytes(before, treeBytes(root), "failed export");
            library.append(stroke(3, 0xff000000, point(75, 90)));
            check(library.error() == null, "healthy editor remains writable after failed PDF export");
            equal(accepted.strokes.size() + 1, library.current().strokes.size(), "healthy page append is accepted after export failure");
            check(Arrays.equals(corrupt, Files.readAllBytes(badFile.toPath())), "subsequent healthy edit preserves corrupt file bytes");
        } finally { deleteTree(root); }
    }

    private static void prepareLibrary(File root) throws Exception {
        NotebookLibrary setup = new NotebookLibrary(root);
        setup.load(); setup.initializeSize(100, 200);
        setup.append(stroke(3, 0xff000000, point(10, 20)));
        setup.addPage(); setup.setTemplate(NotebookPage.Template.LINED);
        setup.append(stroke(3, 0xff000000, point(20, 30)));
        setup.addPage(); setup.append(stroke(3, 0xff000000, point(30, 40)));
        setup.saveScrollPosition(2, .375);
        setup.createNotebook("Unrelated source data"); setup.initializeSize(100, 200);
        setup.append(stroke(3, 0xff000000, point(90, 100)));
        setup.openNotebook(1); setup.selectPage(1);
    }

    private static Map<String, byte[]> treeBytes(File root) throws IOException {
        Map<String, byte[]> result = new TreeMap<>();
        treeBytes(root, root, result);
        return result;
    }

    private static void treeBytes(File root, File file, Map<String, byte[]> result) throws IOException {
        if (file.isFile()) result.put(root.toPath().relativize(file.toPath()).toString(), Files.readAllBytes(file.toPath()));
        else {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot inspect PDF source fixture");
            for (File child : children) treeBytes(root, child, result);
        }
    }

    private static void assertTreeBytes(Map<String, byte[]> expected, Map<String, byte[]> actual, String reason) {
        check(expected.keySet().equals(actual.keySet()), reason + " keeps every source filename");
        for (String path : expected.keySet()) check(Arrays.equals(expected.get(path), actual.get(path)),
            reason + " keeps source bytes: " + path);
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) throw new AssertionError("Cannot remove PDF test fixture " + file);
    }

    private static void writeVisualFixture() throws Exception {
        List<NotebookPage.Stroke> ink = new ArrayList<>();
        // Curves sampled as pen data, several styles, dots, edges, and layered alpha.
        List<NotebookPage.Point> wave = new ArrayList<>();
        for (int x = 80; x <= 1320; x += 4) wave.add(point(x, (float) (280 + 70 * Math.sin(x / 70.0))));
        ink.add(new NotebookPage.Stroke(0, 8, 0xff000000, wave));
        ink.add(stroke(20, 0xff000000, point(100, 450)));
        ink.add(stroke(20, 0xff000000, point(150, 450), point(150, 450)));
        ink.add(stroke(10, 0xffff0000, point(100, 550), point(600, 650), point(1000, 550)));
        ink.add(stroke(80, 0x800000ff, point(200, 850), point(1100, 850)));
        ink.add(stroke(80, 0x80ff0000, point(650, 750), point(650, 950)));
        ink.add(stroke(12, 0xff000000, point(-100, 1100), point(1504, 1100)));
        NotebookPage lined = NotebookPage.empty().withSize(1404, 1688).withTemplate(NotebookPage.Template.LINED).withStrokes(ink);
        NotebookPage blank = page(1200, 800, NotebookPage.Template.BLANK,
            stroke(10, 0xff000000, point(100, 100), point(1100, 100), point(1100, 700), point(100, 700), point(100, 100)),
            stroke(16, 0xff008040, point(250, 500), point(450, 250), point(650, 500), point(850, 250)));
        File directory = new File("build/pdf-test");
        Files.createDirectories(directory.toPath());
        File fixture = new File(directory, "notebook-export.pdf");
        try (OutputStream output = new FileOutputStream(fixture)) {
            NotebookPdf.write(output, "Ink Notebook · 实验笔记 🖊", 2, n -> n == 1 ? lined : blank, null);
        }
        verifyStructure(Files.readAllBytes(fixture.toPath()), 2);
        check(fixture.length() > 2000, "representative production-writer fixture generated");
    }

    private static void verifyStructure(byte[] bytes, int pageCount) throws Exception {
        String pdf = ascii(bytes);
        int start = pdf.lastIndexOf("startxref\n");
        check(start > 0 && pdf.endsWith("%%EOF\n"), "complete PDF trailer");
        long xref = Long.parseLong(pdf.substring(start + 10, pdf.indexOf('\n', start + 10)));
        check(pdf.startsWith("xref\n", (int) xref), "startxref is exact byte offset");
        String[] lines = pdf.substring((int) xref).split("\n");
        int objects = Integer.parseInt(lines[1].split(" ")[1]);
        equal(objects, Integer.parseInt(find(pdf.substring((int) xref), "/Size ([0-9]+)", 1)), "trailer object count matches xref");
        check(lines[2].equals("0000000000 65535 f "), "xref free object entry");
        for (int id = 1; id < objects; id++) {
            check(lines[id + 2].matches("[0-9]{10} 00000 n "), "fixed-width xref entry " + id);
            int offset = Integer.parseInt(lines[id + 2].substring(0, 10));
            check(pdf.startsWith(id + " 0 obj\n", offset), "xref points to exact object byte " + id);
        }
        equal(pageCount, occurrences(pdf, "/Type /Page /Parent"), "page object count matches export");
        for (int page = 1; page <= pageCount; page++) {
            int contentId = 6 + (page - 1) * 3;
            String stream = content(pdf, contentId);
            String declared = find(pdf, "(?:^|\\n)" + (contentId + 1) + " 0 obj\\n([0-9]+)\\nendobj", 1);
            equal(stream.getBytes(StandardCharsets.US_ASCII).length, Long.parseLong(declared), "indirect content length exact " + page);
        }
    }

    private static byte[] export(String title, List<NotebookPage> pages) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        NotebookPdf.write(out, title, pages.size(), n -> pages.get(n - 1), null);
        return out.toByteArray();
    }

    private static String content(String pdf, int id) {
        String object = id + " 0 obj\n";
        int objectStart = pdf.indexOf("\n" + object) + 1;
        int start = pdf.indexOf("stream\n", objectStart) + 7;
        int end = pdf.indexOf("endstream\n", start);
        check(objectStart > 0 && start >= 7 && end >= start, "content stream exists " + id);
        return pdf.substring(start, end);
    }

    private static String find(String text, String regex, int group) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        check(matcher.find(), "expected PDF structure: " + regex);
        return matcher.group(group);
    }

    private static String ascii(byte[] bytes) { return new String(bytes, StandardCharsets.US_ASCII); }
    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format(Locale.ROOT, "%02X", b & 255));
        return out.toString();
    }
    private static int occurrences(String text, String value) {
        int count = 0;
        for (int offset = 0; (offset = text.indexOf(value, offset)) >= 0; offset += value.length()) count++;
        return count;
    }
    private static NotebookPage.Point point(float x, float y) { return new NotebookPage.Point(x, y, 0, 1); }
    private static NotebookPage.Stroke stroke(float width, int color, NotebookPage.Point... points) {
        return new NotebookPage.Stroke(0, width, color, Arrays.asList(points));
    }
    private static NotebookPage page(int width, int height, NotebookPage.Template template, NotebookPage.Stroke... strokes) {
        return NotebookPage.empty().withSize(width, height).withTemplate(template).withStrokes(Arrays.asList(strokes));
    }
    private interface Action { void run() throws Exception; }
    private static void expectIo(Action action, String description) throws Exception {
        try { action.run(); throw new AssertionError(description + " (did not fail)"); }
        catch (IOException expected) { assertions++; }
    }
    private static void contains(String actual, String expected, String description) { check(actual.contains(expected), description); }
    private static void equal(long expected, long actual, String description) { check(expected == actual, description + ": " + expected + " != " + actual); }
    private static void check(boolean condition, String description) {
        assertions++;
        if (!condition) throw new AssertionError(description);
    }
    private static class TrackingOutput extends OutputStream {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        boolean closed, flushed;
        @Override public void write(int value) throws IOException { bytes.write(value); }
        @Override public void write(byte[] data, int start, int length) throws IOException { bytes.write(data, start, length); }
        @Override public void close() { closed = true; }
        @Override public void flush() throws IOException { flushed = true; }
    }
}
