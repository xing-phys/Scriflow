package android.graphics;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;

/** Host-only Java2D raster/clip implementation; no Android drawing methods are stub-executed. */
public final class Canvas {
    private final BufferedImage image;
    private final ArrayDeque<Graphics2D> saved = new ArrayDeque<>();
    private Graphics2D drawing;
    public boolean failNextDraw;
    public Canvas() { this(1, 1); }
    public Canvas(int width, int height) {
        image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        drawing = image.createGraphics(); drawing.setClip(0, 0, width, height);
        drawing.setColor(java.awt.Color.WHITE); drawing.fillRect(0, 0, width, height);
    }
    public int save() {
        int checkpoint = getSaveCount(); saved.push(drawing); drawing = (Graphics2D) drawing.create(); return checkpoint;
    }
    public int getSaveCount() { return saved.size() + 1; }
    public void restoreToCount(int checkpoint) {
        while (getSaveCount() > checkpoint) { drawing.dispose(); drawing = saved.pop(); }
    }
    public boolean clipRect(float left, float top, float right, float bottom) {
        drawing.clip(new Rectangle2D.Float(left, top, Math.max(0, right - left), Math.max(0, bottom - top)));
        return !drawing.getClipBounds().isEmpty();
    }
    public void translate(float x, float y) { drawing.translate(x, y); }
    public void scale(float x, float y) { drawing.scale(x, y); }
    public void drawPath(Path path, Paint paint) {
        if (failNextDraw) { failNextDraw = false; throw new IllegalStateException("Injected raster failure"); }
        drawing.setColor(new java.awt.Color(paint.color, true)); drawing.fill(path.shape);
    }
    public int pixel(int x, int y) { return image.getRGB(x, y); }
}
