package org.eink.inkprobe.notebook;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Crops to actual pressure outlines, including nib extents, rather than sample centers. */
public final class NotebookSelectionExport {
    public static final class Shape {
        public final int color;
        public final NotebookStrokeOutline outline;
        Shape(int color, NotebookStrokeOutline outline) { this.color = color; this.outline = outline; }
    }
    public final List<Shape> shapes;
    public final float left, top, width, height;
    public NotebookSelectionExport(List<NotebookPage.Stroke> strokes, NotebookPdf.OutlineSource source) throws IOException {
        if (strokes == null || strokes.isEmpty()) throw new IOException("Select some strokes first");
        List<Shape> shapes = new ArrayList<>();
        final float[] bounds = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        NotebookStrokeOutline.Sink sink = new NotebookStrokeOutline.Sink() {
            public void moveTo(float x, float y) { point(x, y); }
            public void lineTo(float x, float y) { point(x, y); }
            public void close() { }
            private void point(float x, float y) {
                bounds[0] = Math.min(bounds[0], x); bounds[1] = Math.min(bounds[1], y);
                bounds[2] = Math.max(bounds[2], x); bounds[3] = Math.max(bounds[3], y);
            }
        };
        for (NotebookPage.Stroke stroke : strokes) {
            NotebookStrokeOutline outline = source.outline(stroke);
            if (outline == null) throw new IOException("Missing handwriting outline");
            outline.writeTo(sink); shapes.add(new Shape(stroke.color, outline));
        }
        left = bounds[0] - 8; top = bounds[1] - 8;
        width = Math.max(1, bounds[2] - left + 8); height = Math.max(1, bounds[3] - top + 8);
        if (!NotebookPage.finite(width) || !NotebookPage.finite(height)) throw new IOException("Invalid selection extent");
        this.shapes = java.util.Collections.unmodifiableList(shapes);
    }
    public void writeSvg(OutputStream output) throws IOException {
        Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + n(width)
            + "\" height=\"" + n(height) + "\" viewBox=\"0 0 " + n(width) + " " + n(height) + "\">\n");
        for (Shape shape : shapes) {
            writer.write("<path fill=\"" + String.format(Locale.ROOT, "#%06x", shape.color & 0xffffff)
                + "\" fill-opacity=\"" + n((shape.color >>> 24) / 255f) + "\" fill-rule=\""
                + (shape.outline.evenOdd ? "evenodd" : "nonzero") + "\" d=\"");
            shape.outline.writeTo(new NotebookStrokeOutline.Sink() {
                public void moveTo(float x, float y) throws IOException { writer.write("M" + n(x - left) + " " + n(y - top)); }
                public void lineTo(float x, float y) throws IOException { writer.write("L" + n(x - left) + " " + n(y - top)); }
                public void close() throws IOException { writer.write("Z"); }
            });
            writer.write("\"/>\n");
        }
        writer.write("</svg>\n"); writer.flush();
    }
    private static String n(float number) { return new java.math.BigDecimal(Float.toString(number)).stripTrailingZeros().toPlainString(); }
}
