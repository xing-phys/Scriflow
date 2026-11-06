package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.PopupWindow;
import android.widget.Toast;
import java.util.EnumMap;

/** Nonmodal, static selection actions above the native surface. Paper remains touchable. */
final class NotebookSelectionTools {
    enum Action { COPY, DUPLICATE, MOVE, CUT, DELETE, EXPORT, AI_CHAT }
    interface Listener { void onAction(Action action); }
    private final Context context;
    private final GridLayout content;
    private final PopupWindow popup;
    private final EnumMap<Action, NotebookIconButton> buttons = new EnumMap<>(Action.class);

    NotebookSelectionTools(Context context, Listener listener) {
        this.context = context;
        content = new GridLayout(context); content.setColumnCount(Action.values().length);
        // Match the independent 28 dp circular figure/Markdown actions and 2 dp gaps.
        content.setPadding(0, 0, 0, 0);
        content.setBackground(null);
        String[] labels = {"Copy ink", "Duplicate ink", "Move ink to page", "Cut ink", "Delete ink", "Export ink", "Append ink to AI chat"};
        NotebookIconDrawable.Icon[] icons = {NotebookIconDrawable.Icon.COPY, NotebookIconDrawable.Icon.DUPLICATE,
            NotebookIconDrawable.Icon.MOVE_PAGE, NotebookIconDrawable.Icon.CUT, NotebookIconDrawable.Icon.DELETE,
            NotebookIconDrawable.Icon.EXPORT, NotebookIconDrawable.Icon.AI_CHAT};
        for (Action action : Action.values()) {
            String label = labels[action.ordinal()];
            NotebookIconButton button = new NotebookIconButton(context, icons[action.ordinal()], label);
            button.setTooltipText(label); button.setOnClickListener(v -> listener.onAction(action));
            buttons.put(action, button);
            content.addView(button, actionLayout());
        }

        popup = new PopupWindow(content, 0, 0, false);
        popup.setTouchable(true); popup.setOutsideTouchable(false); popup.setFocusable(false);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setClippingEnabled(false);
        popup.setIsLaidOutInScreen(true); popup.setAnimationStyle(0); popup.setElevation(0);
        popup.setEnterTransition(null); popup.setExitTransition(null);
    }

    void show(View anchor, RectF selection, Rect tools, int count) {
        if (!anchor.isAttachedToWindow() || anchor.getWidth() < 1 || anchor.getHeight() < 1) return;
        if (count == 0) { hide(); return; }
        int columns = Math.max(1, Math.min(Action.values().length, Math.max(1, anchor.getWidth() / Math.max(1, dp(30)))));
        int width = Math.min(anchor.getWidth(), dp(columns * 30));
        layoutActions(columns);
        int height = Math.min(anchor.getHeight(), dp(((Action.values().length + columns - 1) / columns) * 30));
        int[] location = new int[2]; anchor.getLocationOnScreen(location);
        int[] position = NotebookSelectionPlacement.place(anchor.getWidth(), anchor.getHeight(), width, height, dp(8),
            selection.left, selection.top, selection.right, selection.bottom,
            tools.left - location[0], tools.top - location[1], tools.right - location[0], tools.bottom - location[1]);
        popup.setWidth(width); popup.setHeight(height);
        if (popup.isShowing()) popup.update(location[0] + position[0], location[1] + position[1], width, height);
        else NotebookPopupHost.show(popup, anchor, Gravity.TOP | Gravity.LEFT, location[0] + position[0], location[1] + position[1]);
    }
    private void layoutActions(int columns) {
        if (content.getColumnCount() == columns) return;
        // GridLayout retains assigned spans after measuring. Retire them before
        // reducing the column count when a floating pane narrows or rotates.
        content.removeAllViews(); content.setColumnCount(columns);
        for (NotebookIconButton button : buttons.values()) {
            content.addView(button, actionLayout());
        }
    }
    private GridLayout.LayoutParams actionLayout() {
        GridLayout.LayoutParams item = new GridLayout.LayoutParams();
        item.width = dp(28); item.height = dp(28); item.rightMargin = dp(2); item.bottomMargin = dp(2);
        return item;
    }
    void hide() { if (popup.isShowing()) popup.dismiss(); }
    void copied() { Toast.makeText(context, "Ink copied", Toast.LENGTH_SHORT).show(); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
