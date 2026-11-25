package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
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
    private PopupWindow exportMenu;
    private final Runnable boundsChanged;
    private final Rect bounds = new Rect();
    private int layer = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
    private final EnumMap<Action, NotebookIconButton> buttons = new EnumMap<>(Action.class);

    NotebookSelectionTools(Context context, Listener listener, Runnable boundsChanged) {
        this.context = context; this.boundsChanged = boundsChanged;
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
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED); popup.setClippingEnabled(false);
        popup.setIsLaidOutInScreen(true); popup.setAnimationStyle(0); popup.setElevation(0);
        popup.setEnterTransition(null); popup.setExitTransition(null);
    }

    void floatingBase(boolean floating) {
        int desired = floating ? WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
            : WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
        if (desired == layer) return;
        hide(); layer = desired; popup.setWindowLayoutType(layer);
    }

    void show(View anchor, RectF selection, Rect tools, int count) {
        if (!anchor.isAttachedToWindow() || anchor.getWidth() < 1 || anchor.getHeight() < 1) { hide(); return; }
        if (count == 0) { hide(); return; }
        int columns = Math.max(1, Math.min(Action.values().length, Math.max(1, anchor.getWidth() / Math.max(1, dp(30)))));
        int width = Math.min(anchor.getWidth(), dp(columns * 30));
        layoutActions(columns);
        int height = Math.min(anchor.getHeight(), dp(((Action.values().length + columns - 1) / columns) * 30));
        int[] location = new int[2]; anchor.getLocationOnScreen(location);
        int[] position = NotebookSelectionPlacement.place(anchor.getWidth(), anchor.getHeight(), width, height, dp(8),
            selection.left, selection.top, selection.right, selection.bottom,
            tools.left - location[0], tools.top - location[1], tools.right - location[0], tools.bottom - location[1]);
        Rect next = new Rect(location[0] + position[0], location[1] + position[1],
            location[0] + position[0] + width, location[1] + position[1] + height);
        if (!bounds.equals(next)) { bounds.set(next); boundsChanged.run(); }
        popup.setWidth(width); popup.setHeight(height);
        if (popup.isShowing()) popup.update(location[0] + position[0], location[1] + position[1], width, height);
        else if (!NotebookPopupHost.show(popup, anchor, Gravity.TOP | Gravity.LEFT, next.left, next.top)) clearBounds();
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
    void chooseExport(java.util.function.IntConsumer selected) {
        if (exportMenu != null && exportMenu.isShowing()) { exportMenu.dismiss(); return; }
        View anchor = buttons.get(Action.EXPORT);
        if (!anchor.isAttachedToWindow()) return;
        android.widget.LinearLayout menu = new android.widget.LinearLayout(context);
        menu.setOrientation(android.widget.LinearLayout.VERTICAL);
        menu.setPadding(dp(4), dp(4), dp(4), dp(4));
        menu.setBackground(NotebookUi.rounded(context.getResources().getDisplayMetrics().density, 9));
        String[] formats = {"SVG", "PNG", "JPEG", "WebP", "PDF"};
        for (int i = 0; i < formats.length; i++) {
            final int format = i;
            android.widget.TextView item = new android.widget.TextView(context);
            item.setText(formats[i]); item.setTextSize(14); item.setTextColor(android.graphics.Color.BLACK);
            item.setGravity(Gravity.CENTER_VERTICAL); item.setPadding(dp(12), 0, dp(12), 0);
            item.setOnClickListener(v -> { exportMenu.dismiss(); selected.accept(format); });
            menu.addView(item, new android.widget.LinearLayout.LayoutParams(-1, dp(38)));
        }
        int width = dp(116), height = dp(198);
        int[] location = new int[2]; anchor.getLocationOnScreen(location);
        android.util.DisplayMetrics screen = context.getResources().getDisplayMetrics();
        int x = location[0] + anchor.getWidth() + dp(4);
        if (x + width > screen.widthPixels) x = Math.max(0, location[0] - width - dp(4));
        int y = Math.max(0, Math.min(location[1], screen.heightPixels - height));
        exportMenu = new PopupWindow(menu, width, height, true);
        exportMenu.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        exportMenu.setOutsideTouchable(true); exportMenu.setAnimationStyle(0); exportMenu.setElevation(0);
        exportMenu.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
        exportMenu.setClippingEnabled(true); exportMenu.setIsLaidOutInScreen(true);
        exportMenu.setEnterTransition(null); exportMenu.setExitTransition(null);
        NotebookPopupHost.show(exportMenu, anchor, Gravity.TOP | Gravity.LEFT, x, y);
    }
    Rect bounds() { return new Rect(bounds); }
    void hide() { if (exportMenu != null) { exportMenu.dismiss(); exportMenu = null; } if (popup.isShowing()) popup.dismiss(); clearBounds(); }
    private void clearBounds() { if (!bounds.isEmpty()) { bounds.setEmpty(); boundsChanged.run(); } }
    void copied() { Toast.makeText(context, "Ink copied", Toast.LENGTH_SHORT).show(); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
