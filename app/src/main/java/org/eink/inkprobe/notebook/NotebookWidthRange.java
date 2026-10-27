package org.eink.inkprobe.notebook;

/** Remembered tool widths: pen uses its selected page/screen mode; eraser is a page-unit diameter. */
final class NotebookWidthRange {
    static final NotebookWidthRange PEN = new NotebookWidthRange("Pen thickness", 1, 10, 4, 10);
    static final NotebookWidthRange ERASER = new NotebookWidthRange("Eraser width", 4, 240, 36, 1);

    final String name;
    final float minimum, maximum, defaultWidth, step;
    private final int precision;

    private NotebookWidthRange(String name, float minimum, float maximum, float defaultWidth, int precision) {
        this.name = name; this.minimum = minimum; this.maximum = maximum;
        this.defaultWidth = defaultWidth; this.precision = precision; this.step = 1f / precision;
    }
    float restore(float width) {
        return !NotebookPage.finite(width) || width < minimum || width > maximum
            ? defaultWidth : Math.round(width * precision) / (float) precision;
    }
    float atFraction(float position) {
        if (!NotebookPage.finite(position)) return defaultWidth;
        return restore(minimum + Math.max(0, Math.min(1, position)) * (maximum - minimum));
    }
    float fraction(float width) { return (restore(width) - minimum) / (maximum - minimum); }
    String label(float width) {
        int ticks = Math.round(restore(width) * precision);
        return precision == 1 ? Integer.toString(ticks) : (ticks / 10) + "." + (ticks % 10);
    }
}
