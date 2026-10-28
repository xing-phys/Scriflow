package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.PopupWindow;

/** Brief static tool symbol at the top center; no fade, focus change, or per-stroke work. */
final class NotebookToolHint {
    interface Listener { void boundsChanged(Rect bounds); }
    static final long DURATION_MS = 650;
    private final Context context;
    private final Listener listener;
    private final NotebookIconButton icon;
    private final PopupWindow popup;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::hide;
    NotebookToolHint(Context context, Listener listener) {
        this.context = context; this.listener = listener;
        icon = new NotebookIconButton(context, NotebookIconDrawable.Icon.PEN, "Pen");
        icon.setClickable(false); icon.setFocusable(false);
        popup = new PopupWindow(icon, dp(44), dp(44), false);
        popup.setTouchable(false); popup.setOutsideTouchable(false); popup.setClippingEnabled(false);
        popup.setIsLaidOutInScreen(true); popup.setAnimationStyle(0); popup.setElevation(0);
        popup.setEnterTransition(null); popup.setExitTransition(null);
        popup.setOnDismissListener(() -> listener.boundsChanged(new Rect()));
    }
    void show(View root, boolean pen) {
        if (!root.isAttachedToWindow() || root.getWidth() < 1) return;
        main.removeCallbacks(hide);
        icon.setIcon(pen ? NotebookIconDrawable.Icon.PEN : NotebookIconDrawable.Icon.ERASER);
        icon.setContentDescription(pen ? "Pen" : "Eraser");
        int[] origin = new int[2]; root.getLocationOnScreen(origin);
        int size = dp(44), x = origin[0] + (root.getWidth() - size) / 2, y = origin[1] + dp(8);
        listener.boundsChanged(new Rect(x, y, x + size, y + size));
        if (popup.isShowing()) popup.update(x, y, size, size);
        else if (!NotebookPopupHost.show(popup, root, Gravity.TOP | Gravity.LEFT, x, y)) {
            listener.boundsChanged(new Rect()); return;
        }
        main.postDelayed(hide, DURATION_MS);
    }
    void hide() {
        main.removeCallbacks(hide);
        if (popup.isShowing()) popup.dismiss();
        else listener.boundsChanged(new Rect());
    }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
