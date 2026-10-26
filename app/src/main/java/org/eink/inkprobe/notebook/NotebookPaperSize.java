package org.eink.inkprobe.notebook;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Physical paper dimensions, independent of the display and its orientation. */
public final class NotebookPaperSize {
    public static final double MIN_MM = 20, MAX_MM = 1000;
    /** Stable drawing coordinates: roughly the native e-ink resolution for A5. */
    public static final double PIXELS_PER_MM = 8;
    public static final NotebookPaperSize A5 = new NotebookPaperSize("A5", 148, 210);
    public static final List<NotebookPaperSize> PRESETS = Collections.unmodifiableList(Arrays.asList(
        A5, new NotebookPaperSize("A4", 210, 297), new NotebookPaperSize("B5 (ISO)", 176, 250),
        new NotebookPaperSize("Letter", 215.9, 279.4), new NotebookPaperSize("A6", 105, 148),
        new NotebookPaperSize("A3", 297, 420), new NotebookPaperSize("B4 (ISO)", 250, 353),
        new NotebookPaperSize("B6 (ISO)", 125, 176), new NotebookPaperSize("Legal", 215.9, 355.6),
        new NotebookPaperSize("Executive", 184.15, 266.7)));

    public final String name;
    public final double widthMm, heightMm;

    private NotebookPaperSize(String name, double widthMm, double heightMm) {
        validate(widthMm); validate(heightMm);
        this.name = name; this.widthMm = widthMm; this.heightMm = heightMm;
    }

    public static NotebookPaperSize custom(double widthMm, double heightMm) {
        return new NotebookPaperSize("Custom", widthMm, heightMm);
    }

    public int logicalWidth() { return (int) Math.round(widthMm * PIXELS_PER_MM); }
    public int logicalHeight() { return (int) Math.round(heightMm * PIXELS_PER_MM); }
    public double pdfWidth() { return widthMm * 72 / 25.4; }
    public double pdfHeight() { return heightMm * 72 / 25.4; }

    @Override public boolean equals(Object other) {
        if (!(other instanceof NotebookPaperSize)) return false;
        NotebookPaperSize size = (NotebookPaperSize) other;
        return Double.compare(widthMm, size.widthMm) == 0 && Double.compare(heightMm, size.heightMm) == 0;
    }

    @Override public int hashCode() {
        long width = Double.doubleToLongBits(widthMm), height = Double.doubleToLongBits(heightMm);
        return (int) (width ^ (width >>> 32) ^ height ^ (height >>> 32));
    }

    private static void validate(double dimension) {
        if (Double.isNaN(dimension) || Double.isInfinite(dimension) || dimension < MIN_MM || dimension > MAX_MM) {
            throw new IllegalArgumentException("Enter a size from 20 to 1000 mm.");
        }
    }
}
