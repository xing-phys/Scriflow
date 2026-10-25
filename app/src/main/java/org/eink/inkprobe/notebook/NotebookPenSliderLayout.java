package org.eink.inkprobe.notebook;

/** Place a compact width bar near the pen while keeping it within the writing area. */
final class NotebookPenSliderLayout {
    static final class Box {
        final int left, top, width, height;
        Box(int left, int top, int width, int height) {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Empty slider geometry");
            this.left = left; this.top = top; this.width = width; this.height = height;
        }
        int right() { return left + width; }
        int bottom() { return top + height; }
    }

    private NotebookPenSliderLayout() { }

    static Box place(int canvasWidth, int canvasHeight, Box pen, Box tools, boolean vertical,
                     int preferredWidth, int preferredHeight, int gap, int margin) {
        if (canvasWidth <= 0 || canvasHeight <= 0 || preferredWidth <= 0 || preferredHeight <= 0
                || gap < 0 || margin < 0) throw new IllegalArgumentException("Invalid slider geometry");
        int minX = Math.min(margin, (canvasWidth - 1) / 2);
        int minY = Math.min(margin, (canvasHeight - 1) / 2);
        int width = Math.min(preferredWidth, canvasWidth - 2 * minX);
        int height = Math.min(preferredHeight, canvasHeight - 2 * minY);
        int maxX = canvasWidth - minX - width, maxY = canvasHeight - minY - height;
        int centeredX = clamp(pen.left + (pen.width - width) / 2, minX, maxX);
        int centeredY = clamp(pen.top + (pen.height - height) / 2, minY, maxY);
        int left = tools.left - gap - width, right = tools.right() + gap;
        int above = tools.top - gap - height, below = tools.bottom() + gap;
        if (vertical) {
            if (left >= minX && left <= maxX) return new Box(left, centeredY, width, height);
            if (right >= minX && right <= maxX) return new Box(right, centeredY, width, height);
        }
        if (below >= minY && below <= maxY) return new Box(centeredX, below, width, height);
        if (above >= minY && above <= maxY) return new Box(centeredX, above, width, height);
        if (left >= minX && left <= maxX) return new Box(left, centeredY, width, height);
        if (right >= minX && right <= maxX) return new Box(right, centeredY, width, height);
        // In a very small window the bar may cover some controls, but remains fully reachable.
        return new Box(centeredX, centeredY, width, height);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
