package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.GridLayout;
import android.widget.PopupWindow;
import android.widget.Toast;
import java.util.EnumMap;

/** Nonmodal, static selection actions above the native surface. Paper remains touchable. */
final class NotebookSelectionTools {
    enum Action { COPY, DUPLICATE, MOVE, CUT, DELETE, EXPORT }
    interface Listener { void onAction(Action action); }
    private final Context context;
    private final GridLayout content;
    private final PopupWindow popup;
    private final EnumMap<Action, NotebookIconButton> buttons = new EnumMap<>(Action.class);

    NotebookSelectionTools(Context context, Listener listener) {
        this.context = context;
        content = new GridLayout(context); content.setColumnCount(Action.values().length);
        content.setPadding(dp(6), dp(2), dp(6), dp(2));
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.WHITE);
        background.setStroke(dp(1), Color.BLACK); background.setCornerRadius(dp(8)); content.setBackground(background);
        String[] labels = {"Copy ink", "Duplicate ink", "Move ink to page", "Cut ink", "Delete ink", "Export ink"};
        NotebookIconDrawable.Icon[] icons = {NotebookIconDrawable.Icon.COPY, NotebookIconDrawable.Icon.DUPLICATE,
            NotebookIconDrawable.Icon.MOVE_PAGE, NotebookIconDrawable.Icon.CUT, NotebookIconDrawable.Icon.DELETE,
            NotebookIconDrawable.Icon.EXPORT};
        for (Action action : Action.values()) {
            String label = labels[action.ordinal()];
            NotebookIconButton button = new NotebookIconButton(context, icons[action.ordinal()], label);
            button.setTooltipText(label); button.setOnClickListener(v -> listener.onAction(action));
            buttons.put(action, button);
            GridLayout.LayoutParams item = new GridLayout.LayoutParams(); item.width = dp(32); item.height = dp(32);
            content.addView(button, item);
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
        int width = Math.min(dp(Action.values().length * 32 + 12), Math.max(1, anchor.getWidth() - dp(16)));
        int columns = Math.max(1, Math.min(Action.values().length, (width - dp(12)) / dp(32)));
        layoutActions(columns);
        int height = Math.min(anchor.getHeight(), dp(((Action.values().length + columns - 1) / columns) * 32 + 4));
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
            GridLayout.LayoutParams item = new GridLayout.LayoutParams(); item.width = dp(32); item.height = dp(32);
            content.addView(button, item);
        }
    }
    void hide() { if (popup.isShowing()) popup.dismiss(); }
    void copied() { Toast.makeText(context, "Ink copied", Toast.LENGTH_SHORT).show(); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
