package org.eink.inkprobe.notebook;

import java.util.List;

/** Adjustable widths in stored page units, plus immutable style captured at pen-DOWN. */
final class NotebookPenConfig {
    static final float MIN_WIDTH = 1f, MAX_WIDTH = 10f, DEFAULT_WIDTH = 4f;

    private NotebookPenConfig() { }
    static float restore(float width) {
        return !NotebookPage.finite(width) || width < MIN_WIDTH || width > MAX_WIDTH
            ? DEFAULT_WIDTH : Math.round(width * 10f) / 10f;
    }
    static float atFraction(float position) {
        if (!NotebookPage.finite(position)) return DEFAULT_WIDTH;
        return restore(MIN_WIDTH + Math.max(0, Math.min(1, position)) * (MAX_WIDTH - MIN_WIDTH));
    }
    static float fraction(float width) {
        return (restore(width) - MIN_WIDTH) / (MAX_WIDTH - MIN_WIDTH);
    }
    static String label(float width) {
        int tenths = Math.round(restore(width) * 10f);
        return (tenths / 10) + "." + (tenths % 10);
    }

    /** One style object belongs to a stroke even if the next stroke uses another width. */
    static final class Style {
        final int penType, color;
        final float width;
        Style(int penType, float width, int color) {
            if (penType < 0 || penType > 255 || !NotebookPage.finite(width)
                    || width <= 0 || width > 1024) throw new IllegalArgumentException("Invalid pen style");
            this.penType = penType; this.width = width; this.color = color;
        }
        NotebookPage.Stroke stroke(List<NotebookPage.Point> points) {
            return new NotebookPage.Stroke(penType, width, color, points);
        }
    }
}
