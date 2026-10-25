package org.eink.inkprobe.notebook;

/** Single-axis palette sizing, independent of Android display units. */
final class NotebookToolStrip {
    static final class Layout {
        final int button, stripWidth, stripHeight, width, height;
        final boolean scrolling;
        Layout(int button, int stripWidth, int stripHeight, int width, int height, int padding) {
            this.button = button; this.stripWidth = stripWidth; this.stripHeight = stripHeight;
            this.width = width; this.height = height;
            scrolling = stripWidth > width - 2L * padding || stripHeight > height - 2L * padding;
        }
    }

    private NotebookToolStrip() { }

    static Layout measure(boolean vertical, int count, int availableWidth, int availableHeight,
                          int preferred, int minimum, int gap, int padding) {
        if (count < 1 || count > 64 || availableWidth < 1 || availableHeight < 1
                || minimum < 1 || preferred < minimum || gap < 0 || padding < 0)
            throw new IllegalArgumentException("Invalid tool strip dimensions");
        long major = vertical ? availableHeight : availableWidth;
        long cross = vertical ? availableWidth : availableHeight;
        long gaps = (count - 1L) * gap;
        long edges = 2L * padding;
        long fit = (major - gaps - edges) / count;
        int button = (int) Math.max(1, Math.min(cross - edges,
            Math.min(preferred, Math.max(minimum, fit))));
        long length = (long) count * button + gaps;
        if (length + edges > Integer.MAX_VALUE || button + edges > Integer.MAX_VALUE)
            throw new IllegalArgumentException("Tool strip is too large");
        int stripWidth = vertical ? button : (int) length;
        int stripHeight = vertical ? (int) length : button;
        return new Layout(button, stripWidth, stripHeight,
            (int) Math.min(availableWidth, stripWidth + edges),
            (int) Math.min(availableHeight, stripHeight + edges), padding);
    }
}
