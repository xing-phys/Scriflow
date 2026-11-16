package org.eink.inkprobe.notebook;

/** Place a tool popup by its actual button, within the full workspace and clear of controls. */
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
        return place(canvasWidth, canvasHeight, pen, tools, vertical, preferredWidth, preferredHeight, gap, margin, java.util.Collections.emptyList());
    }

    static Box place(int canvasWidth, int canvasHeight, Box pen, Box tools, boolean vertical,
                     int preferredWidth, int preferredHeight, int gap, int margin, java.util.List<Box> controls) {
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
        Box[] candidates = vertical ? new Box[]{new Box(left, centeredY, width, height), new Box(right, centeredY, width, height),
            new Box(centeredX, below, width, height), new Box(centeredX, above, width, height)}
            : new Box[]{new Box(centeredX, below, width, height), new Box(centeredX, above, width, height),
                new Box(right, centeredY, width, height), new Box(left, centeredY, width, height)};
        java.util.List<Box> choices = new java.util.ArrayList<>();
        if (!controls.isEmpty()) {
            // A browser panel has several rows and unused cells. Adjacent empty
            // space is closer than its outer border, without covering any button.
            choices.add(new Box(centeredX, pen.bottom() + gap, width, height));
            choices.add(new Box(centeredX, pen.top - gap - height, width, height));
            choices.add(new Box(pen.right() + gap, centeredY, width, height));
            choices.add(new Box(pen.left - gap - width, centeredY, width, height));
        }
        java.util.Collections.addAll(choices, candidates);
        Box nearest = null; long distance = Long.MAX_VALUE;
        for (Box candidate : choices) {
            if (candidate.left < minX || candidate.left > maxX || candidate.top < minY || candidate.top > maxY) continue;
            boolean obstructed = false;
            for (Box control : controls) if (candidate.left < control.right() + gap && candidate.right() > control.left - gap
                    && candidate.top < control.bottom() + gap && candidate.bottom() > control.top - gap) { obstructed = true; break; }
            if (obstructed) continue;
            long dx = Math.max(0, Math.max(pen.left - candidate.right(), candidate.left - pen.right()));
            long dy = Math.max(0, Math.max(pen.top - candidate.bottom(), candidate.top - pen.bottom()));
            long value = dx * dx + dy * dy;
            if (value < distance) { nearest = candidate; distance = value; }
        }
        if (nearest != null) return nearest;
        // In a very small window the bar may cover some controls, but remains fully reachable.
        return new Box(centeredX, centeredY, width, height);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
