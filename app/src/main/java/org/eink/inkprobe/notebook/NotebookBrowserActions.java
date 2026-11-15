package org.eink.inkprobe.notebook;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Note-sized action buttons wrap into balanced rows without stretching their outlines. */
final class NotebookBrowserActions extends ViewGroup {
    private final int gap, target;
    private int columns = 1, cell;

    NotebookBrowserActions(Context context) {
        super(context);
        gap = NotebookBrowserUi.dp(context, 6);
        target = NotebookBrowserUi.dp(context, 32);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int count = 0;
        for (int i = 0; i < getChildCount(); i++) if (getChildAt(i).getVisibility() != GONE) count++;
        int width = resolveSize(Math.max(1, count) * (target + gap) - gap, widthSpec);
        int capacity = Math.max(1, Math.min(Math.max(1, count), (width + gap) / (target + gap)));
        int rows = Math.max(1, (count + capacity - 1) / capacity);
        columns = Math.max(1, (count + rows - 1) / rows);
        cell = Math.max(1, Math.min(target, (width - (columns - 1) * gap) / columns));
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() != GONE) child.measure(MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(target, MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(width, resolveSize(rows * target + (rows - 1) * gap, heightSpec));
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int at = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int x = (at % columns) * (cell + gap), y = (at / columns) * (target + gap);
            if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) x = getWidth() - x - cell;
            child.layout(x, y, x + cell, y + target);
            at++;
        }
    }
}
