package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Color;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

/** A static toolbar control: touching it does not animate or invert its pixels. */
public final class NotebookButton extends Button {
    private boolean available = true;

    public NotebookButton(Context context) {
        // Do not inherit the theme's animated Material button background.
        super(context, null, 0);
        makeStatic(this);
        setAllCaps(false);
        setMinWidth(0); setMinimumWidth(0);
        setMinHeight(0); setMinimumHeight(0);
        setPadding(0, 0, 0, 0);
    }

    /** Also removes the theme's touch animation from standard dialog buttons. */
    public static void makeStatic(Button button) {
        button.setBackgroundTintList(null);
        button.setBackgroundColor(Color.WHITE);
        button.setForeground(null);
        button.setStateListAnimator(null);
        button.setElevation(0f);
        button.setTranslationZ(0f);
        button.setDefaultFocusHighlightEnabled(false);
        // A single color has no pressed/focused/disabled color transition.
        button.setTextColor(Color.BLACK);
        button.setHighlightColor(Color.TRANSPARENT);
    }

    /**
     * Gate actions without redrawing an unavailable/available toolbar state.
     * The Activity separately guards temporary busy states; availability represents
     * persistent conditions such as whether this page has an Undo command.
     */
    public void setAvailable(boolean value) {
        if (available == value) return;
        available = value;
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    public boolean isAvailable() { return available; }

    @Override public boolean performClick() {
        return available && super.performClick();
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setEnabled(isEnabled() && available);
        info.setClickable(isClickable() && available);
    }

    @Override public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        super.onInitializeAccessibilityEvent(event);
        event.setEnabled(isEnabled() && available);
    }
}
