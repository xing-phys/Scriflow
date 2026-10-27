package org.eink.inkprobe.notebook;

import java.util.List;

/** Adjustable widths in stored page units, plus immutable style captured at pen-DOWN. */
final class NotebookPenConfig {
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

    /** One style object belongs to a stroke even if the next stroke uses another width. */
    static final class Style {
        final int penType, color;
        final float width;
        final float captureScale;
        Style(int penType, float width, int color) {
            this(penType, width, color, 1f);
        }
        Style(int penType, float width, int color, float captureScale) {
            if (penType < 0 || penType > 255 || !NotebookPage.finite(width)
                    || width <= 0 || width > 1024 || !NotebookPage.finite(captureScale) || captureScale <= 0
                    || !NotebookPage.finite(width * captureScale) || width * captureScale > 1024)
                throw new IllegalArgumentException("Invalid pen style");
            this.penType = penType; this.width = width; this.color = color;
            this.captureScale = captureScale;
        }
        NotebookPage.Stroke stroke(List<NotebookPage.Point> points) {
            return new NotebookPage.Stroke(penType, width, color, points, captureScale);
        }
    }
}
