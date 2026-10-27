package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Screen-resolution PDF tiles. Work and memory depend on the visible screen, never the zoomed page area. */
public final class NotebookPdfTiles {
    public static final int EDGE = 512;
    private NotebookPdfTiles() { }

    public static final class Tile {
        public final int page, fullWidth, fullHeight, left, top, width, height;
        private Tile(int page, int fullWidth, int fullHeight, int left, int top) {
            this.page = page; this.fullWidth = fullWidth; this.fullHeight = fullHeight;
            this.left = left; this.top = top;
            width = Math.min(EDGE, fullWidth - left); height = Math.min(EDGE, fullHeight - top);
        }
        public long pixels() { return (long) width * height; }
        public float leftIn(float pageWidth) { return (float) left / fullWidth * pageWidth; }
        public float topIn(float pageHeight) { return (float) top / fullHeight * pageHeight; }
        public float rightIn(float pageWidth) { return (float) (left + width) / fullWidth * pageWidth; }
        public float bottomIn(float pageHeight) { return (float) (top + height) / fullHeight * pageHeight; }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Tile)) return false;
            Tile tile = (Tile) other;
            return page == tile.page && fullWidth == tile.fullWidth && fullHeight == tile.fullHeight
                && left == tile.left && top == tile.top;
        }
        @Override public int hashCode() { return Objects.hash(page, fullWidth, fullHeight, left, top); }
    }

    public static List<Tile> visible(int page, double renderedWidth, double renderedHeight,
            float pageLeft, float pageTop, int screenWidth, int screenHeight) {
        if (page < 1 || !Double.isFinite(renderedWidth) || !Double.isFinite(renderedHeight)
                || renderedWidth <= 0 || renderedHeight <= 0 || renderedWidth > Integer.MAX_VALUE - EDGE
                || renderedHeight > Integer.MAX_VALUE - EDGE || !Float.isFinite(pageLeft)
                || !Float.isFinite(pageTop) || screenWidth < 1 || screenHeight < 1)
            throw new IllegalArgumentException("Invalid PDF tile geometry");
        int fullWidth = (int) Math.ceil(renderedWidth), fullHeight = (int) Math.ceil(renderedHeight);
        int left = (int) Math.max(0, Math.floor(-(double) pageLeft));
        int top = (int) Math.max(0, Math.floor(-(double) pageTop));
        int right = (int) Math.min(fullWidth, Math.ceil((double) screenWidth - pageLeft));
        int bottom = (int) Math.min(fullHeight, Math.ceil((double) screenHeight - pageTop));
        if (right <= left || bottom <= top) return Collections.emptyList();
        List<Tile> result = new ArrayList<>();
        for (int y = top / EDGE * EDGE; y < bottom; y += EDGE)
            for (int x = left / EDGE * EDGE; x < right; x += EDGE)
                result.add(new Tile(page, fullWidth, fullHeight, x, y));
        return Collections.unmodifiableList(result);
    }
}
