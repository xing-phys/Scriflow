package org.eink.inkprobe.notebook;

/** Shared optional-cache allowances. Required visible pixels always take precedence over fallback. */
final class NotebookCacheBudget {
    static final long PDF_PIXELS = 6_000_000;
    static final int ACTIVE_POINTS = 65_536, READER_POINTS = 65_536;
    final long pdfPixelsPerNote;
    final int activePoints, readerPoints;
    NotebookCacheBudget(int visibleNotes) {
        if (visibleNotes < 1) throw new IllegalArgumentException("No visible note");
        pdfPixelsPerNote = Math.max(1, PDF_PIXELS / visibleNotes);
        activePoints = ACTIVE_POINTS;
        readerPoints = Math.min(32_768, READER_POINTS / Math.max(1, visibleNotes - 1));
    }
    static long retainedPdfPixels(long required, long allowance) {
        if (required < 0 || allowance < 1) throw new IllegalArgumentException("Invalid PDF cache budget");
        long twice = required > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : required * 2;
        return Math.max(required, Math.min(allowance, twice));
    }
}
