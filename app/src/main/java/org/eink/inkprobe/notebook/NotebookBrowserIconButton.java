package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.accessibility.AccessibilityNodeInfo;

/** A centered browser symbol with a static outline and a full 48 dp touch target. */
@android.annotation.SuppressLint("ViewConstructor") // Created with an action and icon; never inflated from XML.
final class NotebookBrowserIconButton extends NotebookButton {
    private final NotebookIconDrawable symbol;
    private final Paint countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int count;

    NotebookBrowserIconButton(Context context, NotebookIconDrawable.Icon icon, String name, Runnable action) {
        super(context);
        symbol = new NotebookIconDrawable(icon);
        symbol.setCallback(this);
        NotebookUi.action(this, null);
        setPadding(0, 0, 0, 0);
        setMinimumWidth(NotebookBrowserUi.dp(context, 48));
        setMinimumHeight(NotebookBrowserUi.dp(context, 48));
        setContentDescription(name);
        setTooltipText(name);
        setOnClickListener(view -> action.run());
        countPaint.setColor(android.graphics.Color.BLACK);
        countPaint.setTextAlign(Paint.Align.CENTER);
        countPaint.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
    }

    void setCount(int value) {
        if (count == value) return;
        count = value;
        setTooltipText(getContentDescription() + " · " + count + " open");
        invalidate();
        sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    void setSymbol(NotebookIconDrawable.Icon icon, String name) { symbol.setIcon(icon); setContentDescription(name); setTooltipText(name); }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int size = NotebookBrowserUi.dp(getContext(), 20);
        int left = (getWidth() - size) / 2, top = (getHeight() - size) / 2;
        symbol.setBounds(left, top, left + size, top + size);
        symbol.draw(canvas);
        if (count > 0) {
            // Keep the number inside the stacked-tab outline even at large system font sizes.
            countPaint.setTextSize(NotebookBrowserUi.dp(getContext(), 9));
            Paint.FontMetrics metrics = countPaint.getFontMetrics();
            canvas.drawText(Integer.toString(count), getWidth() / 2f + size / 24f,
                getHeight() / 2f + size / 12f - (metrics.ascent + metrics.descent) / 2f, countPaint);
        }
    }

    @Override protected boolean verifyDrawable(android.graphics.drawable.Drawable drawable) {
        return drawable == symbol || super.verifyDrawable(drawable);
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (count > 0) info.setContentDescription(getContentDescription() + ", " + count + " open");
    }
}
