package org.eink.inkprobe.notebook;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** A folder or folded-page icon with a readable, high-contrast file-type badge. */
final class NotebookFileTypeDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path();
    private final NotebookIconDrawable folder;
    private final String type;

    NotebookFileTypeDrawable(String name, boolean directory) {
        folder = directory ? new NotebookIconDrawable(NotebookIconDrawable.Icon.FOLDER) : null;
        type = NotebookFileTypes.label(name);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        if (folder != null) { folder.setBounds(bounds); folder.draw(canvas); return; }
        float scale = Math.min(bounds.width(), bounds.height()) / 32f;
        int saved = canvas.save();
        canvas.translate(bounds.exactCenterX() - 16 * scale, bounds.exactCenterY() - 16 * scale);
        canvas.scale(scale, scale);
        outline.reset(); outline.moveTo(5, 2); outline.lineTo(20, 2); outline.lineTo(27, 9);
        outline.lineTo(27, 30); outline.lineTo(5, 30); outline.close();
        paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.FILL); canvas.drawPath(outline, paint);
        paint.setColor(Color.BLACK); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f);
        canvas.drawPath(outline, paint); canvas.drawLine(20, 2, 20, 9, paint); canvas.drawLine(20, 9, 27, 9, paint);
        paint.setStyle(Paint.Style.FILL); canvas.drawRect(5, 17, 27, 27, paint);
        paint.setTextSize(7.5f);
        float width = paint.measureText(type); if (width > 20) paint.setTextSize(7.5f * 20 / width);
        paint.setColor(Color.WHITE); canvas.drawText(type, 16, 22 - (paint.ascent() + paint.descent()) / 2, paint);
        canvas.restoreToCount(saved);
    }

    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); if (folder != null) folder.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); if (folder != null) folder.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 32; }
    @Override public int getIntrinsicHeight() { return 32; }
}
