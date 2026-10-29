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
import java.util.EnumMap;

/** Two quiet windows above native ink, without reserving a header in the page. */
final class NotebookStatusIslands {
    enum Action { INSERT_FIGURE, FIGURES, INFORMATION, BACKGROUND, PASTE_FIGURE }
    interface ActionListener { void onAction(Action action); }
    interface Listener { void onBoundsChanged(Rect left, Rect right); }

    private final Activity activity;
    private final Listener listener;
    private final LinearLayout notebook, device;
    private final PopupWindow leftWindow, rightWindow;
    private final Rect leftBounds = new Rect(), rightBounds = new Rect();
    private final NotebookIconButton undo, redo;
    private final NotebookDeviceStatus clock;
    private final NotebookButton more;
    private final LinearLayout actions;
    private final EnumMap<Action, NotebookIconButton> actionButtons = new EnumMap<>(Action.class);
    private boolean zenMode;
    private View anchor;
    private final View.OnLayoutChangeListener layoutListener =
            (view, l, t, r, b, oldL, oldT, oldR, oldB) -> position();

    NotebookStatusIslands(Activity activity, NotebookButton noteName, NotebookIconButton previous,
                          NotebookButton pageLabel, NotebookIconButton next, NotebookButton zoom,
                          TextView saveState,
                          NotebookButton more, NotebookDeviceStatus deviceStatus,
                          NotebookIconButton undo, NotebookIconButton redo, ActionListener actionListener, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        this.undo = undo; this.redo = redo;
        this.clock = deviceStatus;
        this.more = more;
        notebook = island();
        LinearLayout top = new LinearLayout(activity); top.setGravity(Gravity.CENTER_VERTICAL);
        // Only the title shrinks on constrained screens; the other tap targets stay distinct.
        top.addView(noteName, new LinearLayout.LayoutParams(0, dp(28), 1));
        top.addView(previous, new LinearLayout.LayoutParams(dp(28), dp(28)));
        top.addView(pageLabel, new LinearLayout.LayoutParams(-2, dp(28)));
        top.addView(next, new LinearLayout.LayoutParams(dp(28), dp(28)));
        top.addView(zoom, new LinearLayout.LayoutParams(-2, dp(28)));
        top.addView(more, new LinearLayout.LayoutParams(dp(28), dp(28)));
        actions = new LinearLayout(activity); actions.setGravity(Gravity.CENTER_VERTICAL);
        addAction(Action.INSERT_FIGURE, NotebookIconDrawable.Icon.INSERT_FIGURE, "Insert figure", actionListener);
        addAction(Action.FIGURES, NotebookIconDrawable.Icon.FIGURES, "Choose a figure", actionListener);
        addAction(Action.INFORMATION, NotebookIconDrawable.Icon.INFORMATION, "Page information and PDF export", actionListener);
        addAction(Action.BACKGROUND, NotebookIconDrawable.Icon.PAPER_LINED, "Use lined background", actionListener);
        addAction(Action.PASTE_FIGURE, NotebookIconDrawable.Icon.PASTE, "Paste figure", actionListener);
        actions.setVisibility(View.GONE);
        top.addView(actions, new LinearLayout.LayoutParams(-2, dp(28)));
        notebook.addView(top, new LinearLayout.LayoutParams(-1, -2));
        notebook.addView(saveState, new LinearLayout.LayoutParams(-1, dp(24)));
        device = island();
        LinearLayout right = new LinearLayout(activity); right.setGravity(Gravity.CENTER_VERTICAL);
        for (NotebookIconButton button : new NotebookIconButton[]{undo, redo}) {
            button.setVisibility(View.GONE);
            LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(dp(28), dp(28));
            item.rightMargin = dp(4); right.addView(button, item);
        }
        right.addView(deviceStatus, new LinearLayout.LayoutParams(-2, dp(24)));
        device.addView(right, new LinearLayout.LayoutParams(-2, -2));
        leftWindow = window(notebook);
        rightWindow = window(device);
    }

    private void addAction(Action action, NotebookIconDrawable.Icon icon, String label, ActionListener listener) {
        NotebookIconButton button = new NotebookIconButton(activity, icon, label);
        button.setTooltipText(label); button.setOnClickListener(v -> listener.onAction(action));
        actionButtons.put(action, button); actions.addView(button, new LinearLayout.LayoutParams(dp(28), dp(28)));
    }

    void showActions(boolean paper, boolean lined, boolean paste) {
        if (zenMode) return;
        NotebookIconButton background = actionButtons.get(Action.BACKGROUND);
        background.setIcon(lined ? NotebookIconDrawable.Icon.PAPER_BLANK : NotebookIconDrawable.Icon.PAPER_LINED);
        String label = lined ? "Use blank background" : "Use lined background";
        background.setContentDescription(label); background.setTooltipText(label);
        background.setVisibility(paper ? View.VISIBLE : View.GONE);
        actionButtons.get(Action.PASTE_FIGURE).setVisibility(paste ? View.VISIBLE : View.GONE);
        more.setVisibility(View.GONE); actions.setVisibility(View.VISIBLE); position();
    }

    boolean actionsShowing() { return actions.getVisibility() == View.VISIBLE; }

    boolean collapseActions() {
        if (!actionsShowing()) return false;
        actions.setVisibility(View.GONE); more.setVisibility(View.VISIBLE); position(); return true;
    }

    private int visibleActionCount() {
        int count = 0;
        for (NotebookIconButton button : actionButtons.values()) if (button.getVisibility() == View.VISIBLE) count++;
        return count;
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

    void setZenMode(boolean value) {
        zenMode = value;
        if (value) collapseActions();
        device.setPadding(dp(value ? 4 : 10), dp(value ? 2 : 4), dp(value ? 4 : 10), dp(value ? 2 : 4));
        clock.getLayoutParams().height = dp(value ? 18 : 24);
        clock.requestLayout();
        if (value) {
            undo.setVisibility(View.GONE); redo.setVisibility(View.GONE);
        }
        position();
    }

    boolean historyMatches(boolean canUndo, boolean canRedo) {
        return (undo.getVisibility() == View.VISIBLE) == canUndo && (redo.getVisibility() == View.VISIBLE) == canRedo;
    }

    /** Called between native strokes; hidden actions occupy no space or touch target. */
    void history(boolean canUndo, boolean canRedo) {
        if (zenMode) canUndo = canRedo = false;
        undo.setAvailable(canUndo); redo.setAvailable(canRedo);
        undo.setVisibility(canUndo ? View.VISIBLE : View.GONE);
        redo.setVisibility(canRedo ? View.VISIBLE : View.GONE);
        position();
    }

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
        int minimumLeftWidth = dp(224 + (actionsShowing() ? (visibleActionCount() - 1) * 28 : 0));
        boolean stacked = !zenMode && available.width() < rightWidth + minimumLeftWidth + dp(8);
        int maximumLeftWidth = Math.max(1, stacked ? available.width() : available.width() - rightWidth - dp(8));
        if (zenMode) leftBounds.setEmpty();
        else {
            notebook.measure(View.MeasureSpec.makeMeasureSpec(maximumLeftWidth, View.MeasureSpec.AT_MOST), unspecified);
            int leftWidth = Math.max(1, notebook.getMeasuredWidth());
            leftBounds.set(available.left, available.top, available.left + leftWidth,
                available.top + notebook.getMeasuredHeight());
        }
        int rightTop = stacked ? leftBounds.bottom + dp(8) : available.top;
        rightBounds.set(available.right - rightWidth, rightTop, available.right,
            rightTop + device.getMeasuredHeight());
        // Install both holes before either window becomes visible.
        listener.onBoundsChanged(new Rect(leftBounds), new Rect(rightBounds));
        if (zenMode) leftWindow.dismiss();
        boolean leftShown = zenMode || place(leftWindow, leftBounds);
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
        collapseActions();
        leftWindow.dismiss(); rightWindow.dismiss();
        leftBounds.setEmpty(); rightBounds.setEmpty();
        listener.onBoundsChanged(new Rect(), new Rect());
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
