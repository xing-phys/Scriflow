package org.eink.inkprobe.notebook;

import java.util.List;

/** Adjustable page/screen nib widths, plus immutable style captured at pen-DOWN. */
final class NotebookPenConfig {
    enum WidthMode { PAGE, SCREEN }
    static final float MIN_WIDTH = NotebookWidthRange.PEN.minimum;
    static final float MAX_WIDTH = NotebookWidthRange.PEN.maximum;
    static final float DEFAULT_WIDTH = NotebookWidthRange.PEN.defaultWidth;

    private NotebookPenConfig() { }
    static float restore(float width) {
        return NotebookWidthRange.PEN.restore(width);
    }
    static float atFraction(float position) {
        return NotebookWidthRange.PEN.atFraction(position);
    }
    static float fraction(float width) {
        return NotebookWidthRange.PEN.fraction(width);
    }
    static String label(float width) {
        return NotebookWidthRange.PEN.label(width);
    }

    /** Select the native AHW width before input starts; neither mode adds drawing to callbacks. */
    static float viewportWidth(float selected, WidthMode mode, float pageScale) {
        if (mode == null || !NotebookPage.finite(selected) || selected <= 0
                || !NotebookPage.finite(pageScale) || pageScale <= 0)
            throw new IllegalArgumentException("Invalid pen fitting");
        return mode == WidthMode.SCREEN ? Math.min(1024, selected)
            : (float) Math.min(1024d, (double) selected * pageScale);
    }

    /** Map an unchanged native nib into the current surface coordinates, without fitting it to paper. */
    static Style nativeInput(int penType, float nativeWidth, int color, float pixelsPerUnit) {
        return new Style(penType, nativeWidth / pixelsPerUnit, color, pixelsPerUnit, nativeWidth);
    }

    /** One style object belongs to a stroke even if the next stroke uses another width. */
    static final class Style {
        final int penType, color;
        final float width;
        final float captureScale;
        final float captureWidth;
        Style(int penType, float width, int color) {
            this(penType, width, color, 1f);
        }
        Style(int penType, float width, int color, float captureScale) {
            this(penType, width, color, captureScale, width * captureScale);
        }
        private Style(int penType, float width, int color, float captureScale, float captureWidth) {
            if (penType < 0 || penType > 255 || !NotebookPage.finite(width)
                    || width <= 0 || !NotebookPage.finite(captureScale) || captureScale <= 0
                    || !NotebookPage.finite(width * captureScale) || width * captureScale > 1024
                    || !NotebookPage.finite(captureWidth) || captureWidth <= 0 || captureWidth > 1024)
                throw new IllegalArgumentException("Invalid pen style");
            this.penType = penType; this.width = width; this.color = color;
            this.captureScale = captureScale; this.captureWidth = captureWidth;
        }
        NotebookPage.Stroke stroke(List<NotebookPage.Point> points) {
            return new NotebookPage.Stroke(penType, width, color, points, captureScale, captureWidth);
        }
    }
}
