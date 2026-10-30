package org.eink.inkprobe.notebook;

import java.util.List;

/** Inverse-rotate the viewport so PDF work stays bounded by visible pixels at any zoom. */
final class NotebookMarkdownTiles {
    static List<NotebookPdfTiles.Tile> visible(NotebookFigure figure, float scale,
            float pageLeft, float pageTop, int width, int height) {
        if(figure==null || !Float.isFinite(scale) || scale<=0 || !Float.isFinite(pageLeft)
                || !Float.isFinite(pageTop) || width<1 || height<1)
            throw new IllegalArgumentException("Invalid Markdown tile geometry");
        float cx = pageLeft + figure.centerX() * scale, cy = pageTop + figure.centerY() * scale;
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
        for (int n = 0; n < 4; n++) {
            double x = ((n & 1) == 0 ? 0 : width) - cx, y = (n < 2 ? 0 : height) - cy;
            double u = x * figure.cosine() + y * figure.sine() + figure.width * scale / 2;
            double v = -x * figure.sine() + y * figure.cosine() + figure.height * scale / 2;
            minX = Math.min(minX, u); minY = Math.min(minY, v); maxX = Math.max(maxX, u); maxY = Math.max(maxY, v);
        }
        return NotebookPdfTiles.visible(1, figure.width * (double) scale, figure.height * (double) scale,
            (float) -minX, (float) -minY, Math.max(1, (int) Math.ceil(maxX - minX)), Math.max(1, (int) Math.ceil(maxY - minY)));
    }
    private NotebookMarkdownTiles() { }
}
