package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import java.util.function.Consumer;

/** Small nonmodal view-history buttons above the native writing surface. */
final class NotebookViewNavigation {
    private final Context context;
    private final LinearLayout row;
    private final NotebookIconButton back, forward;
    private final PopupWindow popup;
    private final Consumer<Rect> boundsChanged;
    private final Rect bounds = new Rect();
    private View anchor;
    private boolean previous, next;
    private final View.OnLayoutChangeListener layout = (v,l,t,r,b,ol,ot,or,ob) -> position();

    NotebookViewNavigation(Context context, Consumer<Boolean> action, Consumer<Rect> boundsChanged) {
        this.context = context; this.boundsChanged = boundsChanged;
        row = new LinearLayout(context); row.setOrientation(LinearLayout.HORIZONTAL);
        back = new NotebookIconButton(context, NotebookIconDrawable.Icon.VIEW_BACK, "Previous view");
        forward = new NotebookIconButton(context, NotebookIconDrawable.Icon.VIEW_FORWARD, "Next view");
        back.setOnClickListener(v -> action.accept(false)); forward.setOnClickListener(v -> action.accept(true));
        row.addView(back, new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(dp(32), dp(32)); second.leftMargin = dp(4);
        row.addView(forward, second);
        popup = new PopupWindow(row, 1, 1, false);
        popup.setTouchable(true); popup.setFocusable(false); popup.setOutsideTouchable(false);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        popup.setClippingEnabled(false); popup.setIsLaidOutInScreen(true);
        popup.setAnimationStyle(0); popup.setElevation(0); popup.setEnterTransition(null); popup.setExitTransition(null);
    }

    void show(View value, boolean canBack, boolean canForward) {
        if (!canBack && !canForward || value == null || !value.isAttachedToWindow()) { hide(); return; }
        if (anchor != value) {
            if (anchor != null) anchor.removeOnLayoutChangeListener(layout);
            anchor = value; anchor.addOnLayoutChangeListener(layout);
        }
        previous = canBack; next = canForward;
        back.setVisibility(canBack ? View.VISIBLE : View.GONE);
        forward.setVisibility(canForward ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams second = (LinearLayout.LayoutParams) forward.getLayoutParams();
        int margin = canBack && canForward ? dp(4) : 0;
        if (second.leftMargin != margin) { second.leftMargin = margin; forward.setLayoutParams(second); }
        position();
    }

    private void position() {
        if (anchor == null || !anchor.isAttachedToWindow() || anchor.getWidth() < 1 || anchor.getHeight() < 1) return;
        int width = dp((previous ? 32 : 0) + (next ? 32 : 0) + (previous && next ? 4 : 0));
        int height = dp(32); int[] location = new int[2]; anchor.getLocationOnScreen(location);
        int x = location[0] + Math.max(0, anchor.getWidth() - width - dp(8));
        int y = location[1] + Math.max(0, anchor.getHeight() - height - dp(8));
        popup.setWidth(width); popup.setHeight(height);
        if (popup.isShowing()) popup.update(x, y, width, height);
        else if (!NotebookPopupHost.show(popup, anchor, Gravity.TOP | Gravity.LEFT, x, y)) { hide(); return; }
        Rect nextBounds = new Rect(x, y, x + width, y + height);
        if (!bounds.equals(nextBounds)) { bounds.set(nextBounds); boundsChanged.accept(new Rect(bounds)); }
    }

    void hide() {
        if (anchor != null) anchor.removeOnLayoutChangeListener(layout);
        anchor = null; if (popup.isShowing()) popup.dismiss();
        if (!bounds.isEmpty()) { bounds.setEmpty(); boundsChanged.accept(new Rect()); }
    }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
