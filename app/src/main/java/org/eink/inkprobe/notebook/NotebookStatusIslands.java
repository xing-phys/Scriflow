package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/** Two quiet windows above native ink, without reserving a header in the page. */
final class NotebookStatusIslands {
    interface Listener { void onBoundsChanged(Rect left, Rect right); }

    private final Activity activity;
    private final Listener listener;
    private final LinearLayout notebook, device;
    private final PopupWindow leftWindow, rightWindow;
    private final Rect leftBounds = new Rect(), rightBounds = new Rect();
    private final int preferredLeftWidth;
    private View anchor;
    private final View.OnLayoutChangeListener layoutListener =
            (view, l, t, r, b, oldL, oldT, oldR, oldB) -> position();

    NotebookStatusIslands(Activity activity, TextView pageLabel, TextView saveState,
                          NotebookButton more, NotebookDeviceStatus deviceStatus, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        // Reserve digits for page changes so updating the counter never moves the ink clip.
        preferredLeftWidth = Math.max(dp(176), Math.min(dp(320),
            (int) Math.ceil(pageLabel.getPaint().measureText(pageLabel.getText().toString())) + dp(96)));
        notebook = island();
        LinearLayout top = new LinearLayout(activity); top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(pageLabel, new LinearLayout.LayoutParams(0, dp(28), 1));
        top.addView(more, new LinearLayout.LayoutParams(dp(28), dp(28)));
        notebook.addView(top, new LinearLayout.LayoutParams(-1, -2));
        notebook.addView(saveState, new LinearLayout.LayoutParams(-1, dp(24)));
        device = island();
        device.addView(deviceStatus, new LinearLayout.LayoutParams(-2, dp(24)));
        leftWindow = window(notebook);
        rightWindow = window(device);
    }

    private LinearLayout island() {
        LinearLayout result = new LinearLayout(activity);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setStroke(dp(1), 0xff999999);
        background.setCornerRadius(dp(1000));
        result.setBackground(background);
        result.setStateListAnimator(null);
        result.setElevation(0f);
        return result;
    }

    private PopupWindow window(View content) {
        PopupWindow result = new PopupWindow(content, 0, 0, false);
        result.setTouchable(true);
        result.setOutsideTouchable(false);
        result.setFocusable(false);
        result.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        result.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        result.setClippingEnabled(false);
        result.setIsLaidOutInScreen(true);
        result.setAnimationStyle(0);
        result.setEnterTransition(null);
        result.setExitTransition(null);
        result.setElevation(0f);
        return result;
    }

    void show(View value) {
        if (anchor != value) {
            if (anchor != null) anchor.removeOnLayoutChangeListener(layoutListener);
            anchor = value;
            anchor.addOnLayoutChangeListener(layoutListener);
        }
        position();
    }

    /** Newly shown note popups must stay below the shared app-level status. Keep existing ink holes intact. */
    void raise() {
        if (anchor == null) return;
        leftWindow.dismiss(); rightWindow.dismiss(); position();
    }

    /** Save failures can add a Retry line while native input is already stopped. */
    void refresh() { position(); }

    private void position() {
        if (anchor == null || !anchor.isAttachedToWindow() || anchor.getWidth() < 1
                || anchor.getHeight() < 1) return;
        Rect available = new Rect();
        anchor.getWindowVisibleDisplayFrame(available);
        int[] origin = new int[2];
        anchor.getLocationOnScreen(origin);
        Rect page = new Rect(origin[0], origin[1], origin[0] + anchor.getWidth(), origin[1] + anchor.getHeight());
        if (!available.intersect(page)) available.set(page);
        int margin = Math.min(dp(8), Math.max(0, (available.width() - 1) / 2));
        available.inset(margin, Math.min(margin, Math.max(0, (available.height() - 1) / 2)));
        int unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        device.measure(View.MeasureSpec.makeMeasureSpec(available.width(), View.MeasureSpec.AT_MOST), unspecified);
        int rightWidth = device.getMeasuredWidth();
        // On unusually narrow windows place the right island below the left, without overlap.
        boolean stacked = available.width() < rightWidth + dp(96 + 8);
        int leftWidth = Math.max(1, Math.min(preferredLeftWidth,
            stacked ? available.width() : available.width() - rightWidth - dp(8)));
        notebook.measure(View.MeasureSpec.makeMeasureSpec(leftWidth, View.MeasureSpec.EXACTLY), unspecified);
        leftBounds.set(available.left, available.top, available.left + leftWidth,
            available.top + notebook.getMeasuredHeight());
        int rightTop = stacked ? leftBounds.bottom + dp(8) : available.top;
        rightBounds.set(available.right - rightWidth, rightTop, available.right,
            rightTop + device.getMeasuredHeight());
        // Install both holes before either window becomes visible.
        listener.onBoundsChanged(new Rect(leftBounds), new Rect(rightBounds));
        boolean leftShown = place(leftWindow, leftBounds);
        boolean rightShown = place(rightWindow, rightBounds);
        if (!leftShown || !rightShown) {
            if (!leftShown) leftBounds.setEmpty();
            if (!rightShown) rightBounds.setEmpty();
            listener.onBoundsChanged(new Rect(leftBounds), new Rect(rightBounds));
        }
    }

    private boolean place(PopupWindow window, Rect bounds) {
        if (window.isShowing()) {
            window.update(bounds.left, bounds.top, bounds.width(), bounds.height());
            return true;
        }
        window.setWidth(bounds.width()); window.setHeight(bounds.height());
        return NotebookPopupHost.show(window, anchor, Gravity.TOP | Gravity.LEFT, bounds.left, bounds.top);
    }

    void hide() {
        if (anchor != null) anchor.removeOnLayoutChangeListener(layoutListener);
        anchor = null;
        leftWindow.dismiss(); rightWindow.dismiss();
        leftBounds.setEmpty(); rightBounds.setEmpty();
        listener.onBoundsChanged(new Rect(), new Rect());
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
