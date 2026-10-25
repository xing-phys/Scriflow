package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/**
 * A flat, reusable overview cell. Loading and bitmap ownership belong to the caller.
 * The renderer must run on the notebook worker, never on the UI thread.
 */
public final class NotebookPageThumbnail extends View {
    private static final int LOADING = 0, READY = 1, ERROR = 2;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF imageBounds = new RectF();
    private final RectF bitmapBounds = new RectF();
    private Bitmap bitmap;
    private int pageNumber = 1, state = LOADING;
    private boolean current;

    public NotebookPageThumbnail(Context context) {
        super(context, null, 0);
        setBackgroundColor(Color.WHITE);
        setForeground(null);
        setStateListAnimator(null);
        setElevation(0f);
        setTranslationZ(0f);
        setDefaultFocusHighlightEnabled(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    /** The bitmap remains owned by the caller and must not be recycled while bound. */
    public void bind(int pageNumber, Bitmap preview, boolean current) {
        if (preview == null || preview.isRecycled()) {
            throw new IllegalArgumentException("Missing page preview");
        }
        bindState(pageNumber, current, READY, preview);
    }

    public void showLoading(int pageNumber, boolean current) {
        bindState(pageNumber, current, LOADING, null);
    }

    public void showError(int pageNumber, boolean current) {
        bindState(pageNumber, current, ERROR, null);
    }

    boolean hasPreview(int number) {
        return pageNumber == number && state == READY && bitmap != null;
    }

    /** Drop a recycled grid cell's bitmap reference without recycling shared pixels. */
    public void clear() {
        bitmap = null;
        state = LOADING;
        invalidate();
    }

    private void bindState(int number, boolean selected, int nextState, Bitmap preview) {
        if (number < 1) throw new IllegalArgumentException("Invalid page number");
        pageNumber = number;
        current = selected;
        state = nextState;
        bitmap = preview;
        String description = "Page " + number + (selected ? ", current page" : "");
        if (nextState == LOADING) description += ", preview loading";
        if (nextState == ERROR) description += ", preview unavailable";
        setContentDescription(description);
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(dp(180), widthMeasureSpec);
        int height = Math.round(Math.max(1, width - dp(16)) * 1.2f) + dp(48);
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float margin = dp(8);
        float labelHeight = dp(32);
        imageBounds.set(margin, margin, Math.max(margin + 1, getWidth() - margin),
            Math.max(margin + 1, getHeight() - labelHeight - margin));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xffe6e6e6);
        canvas.drawRect(imageBounds, paint);
        if (state == READY && bitmap != null && !bitmap.isRecycled()) {
            float scale = Math.min(imageBounds.width() / bitmap.getWidth(),
                imageBounds.height() / bitmap.getHeight());
            float width = bitmap.getWidth() * scale, height = bitmap.getHeight() * scale;
            bitmapBounds.set(imageBounds.centerX() - width / 2,
                imageBounds.centerY() - height / 2, imageBounds.centerX() + width / 2,
                imageBounds.centerY() + height / 2);
            canvas.drawBitmap(bitmap, null, bitmapBounds, paint);
        } else {
            paint.setColor(0xff555555);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(13));
            if (state == ERROR) {
                drawCentered(canvas, "Preview", imageBounds.centerY() - sp(8));
                drawCentered(canvas, "unavailable", imageBounds.centerY() + sp(8));
            } else drawCentered(canvas, "Loading…", imageBounds.centerY());
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(current ? 3 : 1));
        paint.setColor(current ? Color.BLACK : 0xff999999);
        canvas.drawRect(imageBounds, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(sp(14));
        String label = "Page " + pageNumber + (current ? " · Current" : "");
        float available = Math.max(1, getWidth() - dp(8));
        float measured = paint.measureText(label);
        if (measured > available) paint.setTextSize(paint.getTextSize() * available / measured);
        float centerY = getHeight() - margin - labelHeight / 2;
        canvas.drawText(label, getWidth() / 2f,
            centerY - (paint.ascent() + paint.descent()) / 2, paint);
    }

    private void drawCentered(Canvas canvas, String text, float centerY) {
        canvas.drawText(text, imageBounds.centerX(),
            centerY - (paint.ascent() + paint.descent()) / 2, paint);
    }

    /**
     * Render the entire immutable page into a bounded RGB_565 thumbnail. No page or
     * point list is retained by the returned bitmap. Native pressure-shaped outlines
     * are replayed one stroke at a time without an artificial minimum ink width.
     */
    public static Bitmap render(NotebookPage page, int width, int height) {
        return render(page, width, height, () -> false);
    }

    static Bitmap render(NotebookPage page, int width, int height, BooleanSupplier cancelled) {
        if (page == null || width < 1 || height < 1 || width > 1024 || height > 1024) {
            throw new IllegalArgumentException("Invalid page thumbnail dimensions");
        }
        checkCancelled(cancelled);
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        result.setDensity(Bitmap.DENSITY_NONE);
        try {
            Canvas canvas = new Canvas(result);
            canvas.drawColor(0xffe6e6e6);
            int pageWidth = page.isInitialized() ? page.width : 1404;
            int pageHeight = page.isInitialized() ? page.height : 1688;
            float scale = Math.min(width / (float) pageWidth, height / (float) pageHeight);
            float left = (width - pageWidth * scale) / 2;
            float top = (height - pageHeight * scale) / 2;
            canvas.translate(left, top);
            canvas.scale(scale, scale);
            canvas.clipRect(0, 0, pageWidth, pageHeight);
            canvas.drawColor(Color.WHITE);
            Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
            if (page.template == NotebookPage.Template.LINED) {
                ink.setColor(0xffd2d2d2);
                ink.setStrokeWidth(Math.max(1f, 0.65f / scale));
                for (int y = 72; y < pageHeight; y += 72) {
                    canvas.drawLine(0, y, pageWidth, y, ink);
                }
            }
            for (NotebookPage.Stroke stroke : page.strokes) {
                checkCancelled(cancelled);
                NotebookStrokeRenderer.draw(canvas, stroke, ink, cancelled);
            }
            return result;
        } catch (RuntimeException | Error error) {
            result.recycle();
            throw error;
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean()) throw new CancellationException("Overview closed");
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private float sp(int value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
