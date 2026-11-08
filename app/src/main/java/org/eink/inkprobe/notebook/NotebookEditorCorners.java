package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import java.util.ArrayList;
import java.util.List;

/** Small windows above the active note's Z-on-top native writing surface. */
final class NotebookEditorCorners {
    private final Context context;
    private final FrameLayout root;
    private final Runnable boundsChanged;
    final NotebookWindowControls controls;
    private final NotebookIconButton back, forward;
    private final LinearLayout history;
    private final PopupWindow left, right, navigation;
    private final Rect leftBounds = new Rect(), rightBounds = new Rect(), historyBounds = new Rect();
    private boolean canBack, canForward;
    private int layer = -1;

    NotebookEditorCorners(Context context, FrameLayout root, FrameLayout content, NotebookWorkspace workspace,
            NotebookWorkspace.Pane pane, NotebookWindowControls.Listener listener, Runnable boundsChanged,
            java.util.function.Consumer<Boolean> navigate) {
        this.context = context; this.root = root; this.boundsChanged = boundsChanged;
        controls = new NotebookWindowControls(context, root, content, workspace, pane, listener, "note", true);
        back = new NotebookIconButton(context, NotebookIconDrawable.Icon.VIEW_BACK, "Previous view");
        forward = new NotebookIconButton(context, NotebookIconDrawable.Icon.VIEW_FORWARD, "Next view");
        back.setOnClickListener(v -> navigate.accept(false)); forward.setOnClickListener(v -> navigate.accept(true));
        history = new LinearLayout(context); history.setOrientation(LinearLayout.HORIZONTAL);
        history.addView(back, new LinearLayout.LayoutParams(dp(22), dp(22)));
        LinearLayout.LayoutParams next = new LinearLayout.LayoutParams(dp(22), dp(22)); next.leftMargin = dp(4);
        history.addView(forward, next);
        left = window(controls.menu); right = window(controls.minimize); navigation = window(history);
    }

    private PopupWindow window(View content) {
        PopupWindow popup = new PopupWindow(content, 1, 1, false);
        popup.setTouchable(true); popup.setOutsideTouchable(false); popup.setFocusable(false);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED); popup.setClippingEnabled(false);
        popup.setIsLaidOutInScreen(true); popup.setAnimationStyle(0); popup.setElevation(0);
        popup.setEnterTransition(null); popup.setExitTransition(null);
        return popup;
    }

    void name(String name) {
        controls.menu.setContentDescription(name + ": options; drag to move floating note");
        controls.minimize.setContentDescription(name + ": minimize; drag to resize floating note");
    }

    void history(boolean backAvailable, boolean forwardAvailable) {
        canBack = backAvailable; canForward = forwardAvailable;
        back.setVisibility(canBack ? View.VISIBLE : View.GONE);
        forward.setVisibility(canForward ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) forward.getLayoutParams();
        int margin = canBack && canForward ? dp(4) : 0;
        if (params.leftMargin != margin) { params.leftMargin = margin; forward.setLayoutParams(params); }
    }

    void position(int x, int y, int width, int height, boolean subPanel, boolean raise) {
        int desiredLayer = subPanel ? WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
            : WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        if (raise || layer != desiredLayer) {
            dismissWindows();
            if (layer != desiredLayer) {
                layer = desiredLayer;
                left.setWindowLayoutType(layer); right.setWindowLayoutType(layer); navigation.setWindowLayoutType(layer);
            }
        }
        Rect oldLeft = new Rect(leftBounds), oldRight = new Rect(rightBounds), oldHistory = new Rect(historyBounds);
        int size = dp(22), inset = dp(2) + dp(5), top = y + height - inset - size;
        leftBounds.set(x + inset, top, x + inset + size, top + size);
        rightBounds.set(x + width - inset - size, top, x + width - inset, top + size);
        int historyWidth = (canBack ? size : 0) + (canForward ? size : 0) + (canBack && canForward ? dp(4) : 0);
        if (historyWidth > 0) historyBounds.set(rightBounds.left - dp(4) - historyWidth, top, rightBounds.left - dp(4), top + size);
        else historyBounds.setEmpty();
        // Native ink is clipped before any overlay is shown or moved.
        if (!oldLeft.equals(leftBounds) || !oldRight.equals(rightBounds) || !oldHistory.equals(historyBounds)) boundsChanged.run();
        boolean changed = !present(left, leftBounds) | !present(right, rightBounds);
        if (historyWidth > 0) changed |= !present(navigation, historyBounds);
        else if (navigation.isShowing()) navigation.dismiss();
        if (changed) boundsChanged.run();
    }

    private boolean present(PopupWindow popup, Rect bounds) {
        int width = bounds.width(), height = bounds.height();
        if (popup.isShowing()) popup.update(bounds.left, bounds.top, width, height);
        else {
            popup.setWidth(width); popup.setHeight(height);
            if (!NotebookPopupHost.show(popup, root, Gravity.TOP | Gravity.LEFT, bounds.left, bounds.top)) {
                bounds.setEmpty(); return false;
            }
        }
        return true;
    }

    List<Rect> bounds() {
        List<Rect> result = new ArrayList<>();
        if (!leftBounds.isEmpty()) result.add(new Rect(leftBounds));
        if (!rightBounds.isEmpty()) result.add(new Rect(rightBounds));
        if (!historyBounds.isEmpty()) result.add(new Rect(historyBounds));
        return result;
    }

    void hide() {
        dismissWindows();
        if (!leftBounds.isEmpty() || !rightBounds.isEmpty() || !historyBounds.isEmpty()) {
            leftBounds.setEmpty(); rightBounds.setEmpty(); historyBounds.setEmpty(); boundsChanged.run();
        }
    }
    private void dismissWindows() { left.dismiss(); right.dismiss(); navigation.dismiss(); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
