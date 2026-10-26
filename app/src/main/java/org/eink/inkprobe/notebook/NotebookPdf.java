package org.eink.inkprobe.notebook;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/** A streaming vector PDF writer. It retains no page after its export completes. */
public final class NotebookPdf {
    public interface PageSource { NotebookPage page(int number) throws IOException; }
    public interface Progress { void pageExported(int completed, int total) throws IOException; }
    public interface OutlineSource { NotebookStrokeOutline outline(NotebookPage.Stroke stroke) throws IOException; }

    private static final double PAGE_WIDTH = 595.2756;
    /** PDF 1.4's maximum page extent in default user-space units. */
    private static final double MAX_PAGE_HEIGHT = 14400;
    private static final long MAX_XREF_OFFSET = 9999999999L;

    private NotebookPdf() {}

    /**
     * Export pages in one-based order without closing the caller's stream.
     * The caller must discard a partially written destination after any failure.
     * Progress runs between pages and may throw IOException to cancel the export.
     */
    public static void write(OutputStream output, String notebookName, int pageCount,
                             PageSource source, Progress progress, OutlineSource outlines) throws IOException {
        if (output == null || notebookName == null || source == null || outlines == null || pageCount < 1 ||
                pageCount > NotebookIndex.MAX_PAGES) {
            throw new IOException("Invalid PDF export request");
        }
        new Writer(output, pageCount, outlines).write(notebookName, source, progress);
    }

    private static final class Writer {
        private final OutputStream output;
        private final OutlineSource outlines;
        private OutputStream pageOutput;
        private final int pageCount, lastPageObject;
        private final long[] offsets;
        private final int[] alphaObjects = new int[256];
        private int lastObject;
        private long position;

        Writer(OutputStream output, int count, OutlineSource outlines) {
            this.output = new BufferedOutputStream(output, 32768);
            this.outlines = outlines;
            pageCount = count;
            lastPageObject = 4 + count * 3;
            lastObject = lastPageObject;
            offsets = new long[lastPageObject + 257];
        }

        void write(String name, PageSource source, Progress progress) throws IOException {
            ascii("%PDF-1.4\n%Scriflow vector export\n");
            object(1); ascii("<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
            object(2); ascii("<< /Type /Pages /Count " + pageCount + " /Kids [");
            for (int page = 1; page <= pageCount; page++) ascii(pageObject(page) + " 0 R ");
            ascii("] >>\nendobj\n");
            object(3); ascii("<< /Title <FEFF");
            // Write UTF-16 code units, including surrogate pairs, directly as hex.
            for (int i = 0; i < name.length(); i++) {
                int c = name.charAt(i);
                ascii(hex(c >> 12) + hex(c >> 8) + hex(c >> 4) + hex(c));
            }
            ascii("> /Creator (Scriflow) >>\nendobj\n");
            for (int number = 1; number <= pageCount; number++) {
                NotebookPage page;
                try { page = source.page(number); }
                catch (RuntimeException error) { throw new IOException("Cannot read PDF page " + number, error); }
                validate(page, number);
                writePage(number, page);
                if (progress != null) progress.pageExported(number, pageCount);
            }
            object(4); ascii("<< /ExtGState << ");
            for (int alpha = 0; alpha < alphaObjects.length; alpha++) {
                if (alphaObjects[alpha] != 0) ascii("/A" + alpha + " " + alphaObjects[alpha] + " 0 R ");
            }
            ascii(">> >>\nendobj\n");
            for (int alpha = 0; alpha < alphaObjects.length; alpha++) {
                if (alphaObjects[alpha] == 0) continue;
                object(alphaObjects[alpha]);
                String opacity = number(alpha / 255.0);
                ascii("<< /Type /ExtGState /CA " + opacity + " /ca " + opacity + " >>\nendobj\n");
            }
            long xref = position;
            ascii("xref\n0 " + (lastObject + 1) + "\n0000000000 65535 f \n");
            for (int id = 1; id <= lastObject; id++) {
                String offset = Long.toString(offsets[id]);
                for (int zero = offset.length(); zero < 10; zero++) ascii("0");
                ascii(offset + " 00000 n \n");
            }
            ascii("trailer\n<< /Size " + (lastObject + 1) + " /Root 1 0 R /Info 3 0 R >>\n" +
                "startxref\n" + xref + "\n%%EOF\n");
            output.flush();
        }

        private void writePage(int index, NotebookPage page) throws IOException {
            int id = pageObject(index), content = id + 1, length = id + 2;
            double scaleX = Math.min(PAGE_WIDTH / page.width, MAX_PAGE_HEIGHT / page.height);
            double scaleY = scaleX;
            double width = page.width * scaleX, height = page.height * scaleY;
            if (page.paperSize != null) {
                width = page.paperSize.pdfWidth(); height = page.paperSize.pdfHeight();
                scaleX = width / page.width; scaleY = height / page.height;
            }
            object(id);
            ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + number(width) + " " +
                number(height) + "] /Resources 4 0 R /Contents " + content + " 0 R >>\nendobj\n");
            object(content); ascii("<< /Length " + length + " 0 R /Filter /FlateDecode >>\nstream\n");
            long contentStart = position;
            Deflater deflater = new Deflater();
            DeflaterOutputStream compressed = new DeflaterOutputStream(new OutputStream() {
                @Override public void write(int value) throws IOException { output.write(value); position++; }
                @Override public void write(byte[] bytes, int offset, int count) throws IOException {
                    output.write(bytes, offset, count); position += count;
                }
            }, deflater, 32768);
            pageOutput = compressed;
            try {
                ascii("q\n" + number(scaleX) + " 0 0 " + number(-scaleY) + " 0 " + number(height) + " cm\n");
                ascii("0 0 " + page.width + " " + page.height + " re W n\n");
                ascii("1 1 1 rg\n0 0 " + page.width + " " + page.height + " re f\n");
                if (page.template == NotebookPage.Template.LINED) {
                    ascii("0.823529 0.823529 0.823529 RG\n1 w\n");
                    for (int y = 72; y < page.height; y += 72) {
                        ascii("0 " + y + " m " + page.width + " " + y + " l S\n");
                    }
                }
                for (NotebookPage.Stroke stroke : page.strokes) writeStroke(stroke);
                ascii("Q\n");
                compressed.finish();
            } finally {
                pageOutput = null;
                deflater.end();
            }
            long contentLength = position - contentStart;
            ascii("\nendstream\nendobj\n");
            object(length); ascii(contentLength + "\nendobj\n");
        }

        private void writeStroke(NotebookPage.Stroke stroke) throws IOException {
            NotebookStrokeOutline outline;
            try { outline = outlines.outline(stroke); }
            catch (RuntimeException error) { throw new IOException("Could not render PDF handwriting", error); }
            if (outline == null) throw new IOException("Missing PDF handwriting outline");
            int alpha = (stroke.color >>> 24) & 255;
            if (alphaObjects[alpha] == 0) alphaObjects[alpha] = ++lastObject;
            String rgb = number(((stroke.color >>> 16) & 255) / 255.0) + " " +
                number(((stroke.color >>> 8) & 255) / 255.0) + " " + number((stroke.color & 255) / 255.0);
            ascii("/A" + alpha + " gs\n" + rgb + " rg\n");
            // Fill the complete native outline once, retaining pressure, contours and opacity.
            // A PDF line width cannot describe this brush and made old exports too thick.
            outline.writeTo(new NotebookStrokeOutline.Sink() {
                @Override public void moveTo(float x, float y) throws IOException {
                    ascii(coordinate(x) + " " + coordinate(y) + " m\n");
                }
                @Override public void lineTo(float x, float y) throws IOException {
                    ascii(coordinate(x) + " " + coordinate(y) + " l\n");
                }
                @Override public void close() throws IOException { ascii("h\n"); }
            });
            ascii(outline.evenOdd ? "f*\n" : "f\n");
        }

        private void object(int id) throws IOException {
            if (position > MAX_XREF_OFFSET) throw new IOException("PDF exceeds cross-reference size limit");
            offsets[id] = position;
            ascii(id + " 0 obj\n");
        }

        private void ascii(String value) throws IOException {
            byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
            if (pageOutput != null) pageOutput.write(bytes);
            else { output.write(bytes); position += bytes.length; }
        }
    }

    private static int pageObject(int number) { return 5 + (number - 1) * 3; }

    private static void validate(NotebookPage page, int number) throws IOException {
        if (page == null || !page.isInitialized() || page.width > NotebookStore.MAX_DIMENSION ||
                page.height > NotebookStore.MAX_DIMENSION || page.template == null) {
            throw new IOException("PDF page " + number + " has no valid paper size");
        }
    }

    private static String coordinate(float value) throws IOException {
        if (!NotebookPage.finite(value)) throw new IOException("Non-finite PDF coordinate");
        String text = Float.toString(value);
        return text.indexOf('E') >= 0 || text.indexOf('e') >= 0 ?
            new BigDecimal(text).stripTrailingZeros().toPlainString() :
            text.endsWith(".0") ? text.substring(0, text.length() - 2) : text;
    }

    private static String number(double value) throws IOException {
        if (Double.isNaN(value) || Double.isInfinite(value)) throw new IOException("Non-finite PDF number");
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private static String hex(int digit) { return "0123456789ABCDEF".substring(digit & 15, (digit & 15) + 1); }
}
