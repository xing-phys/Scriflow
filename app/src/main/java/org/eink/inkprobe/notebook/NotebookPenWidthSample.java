package org.eink.inkprobe.notebook;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/** An actual fitted-width line sample, independent of font glyphs and touch state. */
final class NotebookPenWidthSample extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int width, height;

    NotebookPenWidthSample(float penWidth, int width, int height) {
        this.width = width; this.height = height;
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(penWidth);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float inset = Math.max(paint.getStrokeWidth() / 2f, bounds.width() / 12f);
        canvas.drawLine(bounds.left + inset, bounds.exactCenterY(),
            bounds.right - inset, bounds.exactCenterY(), paint);
    }
    @Override public int getIntrinsicWidth() { return width; }
    @Override public int getIntrinsicHeight() { return height; }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public void setAlpha(int value) { paint.setAlpha(value); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter value) { paint.setColorFilter(value); invalidateSelf(); }
}
