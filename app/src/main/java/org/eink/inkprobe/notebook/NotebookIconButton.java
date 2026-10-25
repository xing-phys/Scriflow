package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

/** A static e-ink button: its only visual state change is an explicitly selected tool. */
public final class NotebookIconButton extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF border = new RectF();
    private final NotebookIconDrawable symbol;
    private boolean available = true;
    private boolean toolSelected;
    private boolean selectable;
    private CharSequence accessibleName;
    private final int touchSlop;
    private int touchPointer = -1;
    private boolean touchGesture, clickCandidate;

    public NotebookIconButton(Context context, NotebookIconDrawable.Icon icon, String name) {
        super(context, null, 0);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        symbol = new NotebookIconDrawable(icon);
        symbol.setCallback(this);
        setBackground(null);
        setForeground(null);
        setStateListAnimator(null);
        setElevation(0f);
        setTranslationZ(0f);
        setDefaultFocusHighlightEnabled(false);
        setClickable(true);
        setFocusable(true);
        setSoundEffectsEnabled(false);
        setHapticFeedbackEnabled(false);
        setMinimumWidth(dp(48));
        setMinimumHeight(dp(48));
        setContentDescription(name);
    }

    public void setIcon(NotebookIconDrawable.Icon icon) {
        symbol.setIcon(icon);
    }

    /** Availability gates clicks without changing the pixels during saves or loading. */
    public void setAvailable(boolean value) {
        if (available == value) return;
        available = value;
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    public boolean isAvailable() { return available; }

    public void setToolSelected(boolean value) {
        selectable = true;
        if (toolSelected == value) return;
        toolSelected = value;
        updateAccessibleName();
        invalidate();
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    public boolean isToolSelected() { return toolSelected; }

    @Override public void setContentDescription(CharSequence value) {
        accessibleName = value;
        updateAccessibleName();
    }

    private void updateAccessibleName() {
        super.setContentDescription(toolSelected && accessibleName != null
                ? accessibleName + ", selected" : accessibleName);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize(dp(48), widthSpec), resolveSize(dp(48), heightSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Pressed, focused, hovered, and unavailable all draw exactly the same pixels.
        float stroke = dpFloat(toolSelected ? 3f : 1f);
        float inset = stroke / 2f;
        border.set(inset, inset, getWidth() - inset, getHeight() - inset);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(border, dpFloat(12), dpFloat(12), paint);
        paint.setColor(Color.BLACK);
        paint.setStrokeWidth(stroke);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(border, dpFloat(12), dpFloat(12), paint);
        int size = Math.min(dp(26), Math.min(getWidth(), getHeight()));
        int left = (getWidth() - size) / 2;
        int top = (getHeight() - size) / 2;
        symbol.setBounds(left, top, left + size, top + size);
        symbol.draw(canvas);
    }

    @Override protected boolean verifyDrawable(Drawable drawable) {
        return drawable == symbol || super.verifyDrawable(drawable);
    }

    @Override public boolean performClick() {
        return available && super.performClick();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (!isEnabled() || !isClickable()) return false;
            touchGesture = true;
            touchPointer = event.getPointerId(0);
            clickCandidate = available && contains(event.getX(0), event.getY(0), 0);
            return true;
        }
        if (!touchGesture) return false;
        if (action == MotionEvent.ACTION_POINTER_DOWN
                || action == MotionEvent.ACTION_POINTER_UP) {
            clickCandidate = false;
        } else if (action == MotionEvent.ACTION_MOVE) {
            int index = event.findPointerIndex(touchPointer);
            if (index < 0 || !contains(event.getX(index), event.getY(index), touchSlop))
                clickCandidate = false;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            int index = event.findPointerIndex(touchPointer);
            boolean click = action == MotionEvent.ACTION_UP && clickCandidate && index >= 0
                    && contains(event.getX(index), event.getY(index), 0);
            touchGesture = false;
            clickCandidate = false;
            touchPointer = -1;
            // View's default ACTION_UP posts performClick. Dispatch synchronously so
            // the palette can queue this action before resuming native ink on UP.
            if (click) performClick();
        }
        // Never setPressed: a press does not dirty or animate this e-ink control.
        return true;
    }

    private boolean contains(float x, float y, int slop) {
        return x >= -slop && y >= -slop && x < getWidth() + slop
                && y < getHeight() + slop;
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(Button.class.getName());
        info.setEnabled(isEnabled() && available);
        info.setClickable(isClickable() && available);
        info.setCheckable(selectable);
        info.setChecked(toolSelected);
        info.setSelected(toolSelected);
    }

    @Override public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setClassName(Button.class.getName());
        event.setEnabled(isEnabled() && available);
        event.setChecked(toolSelected);
    }

    private int dp(float value) { return Math.round(dpFloat(value)); }
    private float dpFloat(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
