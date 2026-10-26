package org.eink.inkprobe.notebook;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** Small black vector symbols; button borders and selection belong to the host view. */
public final class NotebookIconDrawable extends Drawable {
    public enum Icon {
        LIBRARY, PREVIOUS, NEXT, ADD_PAGE, PEN, ERASER, LASSO, UNDO, REDO,
        PAPER_BLANK, PAPER_LINED, MORE, OVERVIEW, MOVE, LAYOUT_HORIZONTAL, LAYOUT_VERTICAL,
        BATTERY, WIFI, WIFI_OFF
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private Icon icon;

    public NotebookIconDrawable(Icon icon) {
        if (icon == null) throw new IllegalArgumentException("Missing notebook icon");
        this.icon = icon;
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(NotebookSymbols.STROKE_WIDTH);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setIcon(Icon value) {
        if (value == null) throw new IllegalArgumentException("Missing notebook icon");
        if (icon == value) return;
        icon = value;
        invalidateSelf();
    }

    @Override public void draw(final Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        float scale = Math.min(bounds.width(), bounds.height()) / NotebookSymbols.SIZE;
        int save = canvas.save();
        canvas.translate(bounds.exactCenterX() - NotebookSymbols.SIZE * scale / 2f,
                bounds.exactCenterY() - NotebookSymbols.SIZE * scale / 2f);
        canvas.scale(scale, scale);
        NotebookSymbols.draw(icon.name(), new NotebookSymbols.Sink() {
            @Override public void begin() { path.reset(); }
            @Override public void moveTo(float x, float y) { path.moveTo(x, y); }
            @Override public void lineTo(float x, float y) { path.lineTo(x, y); }
            @Override public void quadTo(float x1, float y1, float x2, float y2) {
                path.quadTo(x1, y1, x2, y2);
            }
            @Override public void cubicTo(float x1, float y1, float x2, float y2,
                                           float x3, float y3) {
                path.cubicTo(x1, y1, x2, y2, x3, y3);
            }
            @Override public void close() { path.close(); }
            @Override public void end(boolean filled) {
                paint.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
                canvas.drawPath(path, paint);
            }
        });
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) {
        if (paint.getAlpha() == alpha) return;
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override public void setColorFilter(ColorFilter filter) {
        if (paint.getColorFilter() == filter) return;
        paint.setColorFilter(filter);
        invalidateSelf();
    }

    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 24; }
    @Override public int getIntrinsicHeight() { return 24; }
}
