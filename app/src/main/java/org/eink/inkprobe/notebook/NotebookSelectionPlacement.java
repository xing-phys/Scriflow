package org.eink.inkprobe.notebook;

/** Bounded static action-bar placement near a selection, with the main palette as an obstacle. */
final class NotebookSelectionPlacement {
    static int[] place(int width, int height, int barWidth, int barHeight, int margin,
                       float left, float top, float right, float bottom,
                       int avoidLeft, int avoidTop, int avoidRight, int avoidBottom) {
        int maxX = Math.max(0, width - barWidth), maxY = Math.max(0, height - barHeight);
        int x = clamp(Math.round((left + right - barWidth) / 2), 0, maxX);
        int below = clamp(Math.round(bottom) + margin, 0, maxY);
        int above = clamp(Math.round(top) - margin - barHeight, 0, maxY);
        int y = bottom + margin + barHeight <= height ? below : above;
        if (overlaps(x, y, barWidth, barHeight, avoidLeft, avoidTop, avoidRight, avoidBottom)) {
            int alternate = y == below ? above : below;
            if (!overlaps(x, alternate, barWidth, barHeight, avoidLeft, avoidTop, avoidRight, avoidBottom)) y = alternate;
            else if (avoidBottom + margin + barHeight <= height) y = avoidBottom + margin;
            else if (avoidTop >= barHeight + margin) y = avoidTop - barHeight - margin;
        }
        return new int[]{x, clamp(y, 0, maxY)};
    }
    private static boolean overlaps(int x, int y, int w, int h, int l, int t, int r, int b) {
        return l < r && t < b && x < r && x + w > l && y < b && y + h > t;
    }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
}
