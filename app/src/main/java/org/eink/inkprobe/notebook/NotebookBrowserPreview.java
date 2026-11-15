package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.*;
import android.widget.ImageView;

/** A 4:3 monochrome preview; missing/evicted images have an explicit local placeholder. */
final class NotebookBrowserPreview extends ImageView {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final NotebookIconDrawable symbol;
    private final boolean website;

    NotebookBrowserPreview(Context context, String url, boolean add) {
        super(context); setScaleType(ScaleType.CENTER_CROP); setBackgroundColor(Color.WHITE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        symbol = new NotebookIconDrawable(add ? NotebookIconDrawable.Icon.PLUS : url == null || url.isEmpty() ? NotebookIconDrawable.Icon.HOME : NotebookIconDrawable.Icon.BROWSER);
        website = url != null && !url.isEmpty();
        NotebookBrowserPreviews.bind(this, url);
    }

    @Override public void setImageBitmap(Bitmap bitmap) { if (bitmap == null) setImageDrawable(null); else super.setImageBitmap(bitmap); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec); setMeasuredDimension(width, width * 3 / 4);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getDrawable() != null) return;
        int size = NotebookBrowserUi.dp(getContext(), 24), x = (getWidth() - size) / 2, y = (getHeight() - size) / 2;
        symbol.setBounds(x, y, x + size, y + size); symbol.draw(canvas);
        if (website) { paint.setStyle(Paint.Style.FILL); paint.setColor(0xff555555); paint.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity); paint.setTextAlign(Paint.Align.CENTER); canvas.drawText("No preview yet", getWidth() / 2f, y + size + NotebookBrowserUi.dp(getContext(), 20), paint); }
        paint.setStyle(Paint.Style.STROKE); paint.setColor(0xffbbbbbb); paint.setStrokeWidth(1);
        canvas.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1, paint);
    }
}
