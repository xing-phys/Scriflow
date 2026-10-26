package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.EnumMap;

/** Nonmodal, static selection actions above the native surface. Paper remains touchable. */
final class NotebookSelectionTools {
    enum Action { COPY, DUPLICATE, CUT, DELETE, DONE }
    interface Listener { void onAction(Action action); }
    private final Context context;
    private final LinearLayout content;
    private final TextView status;
    private final PopupWindow popup;
    private final EnumMap<Action, NotebookButton> buttons = new EnumMap<>(Action.class);

    NotebookSelectionTools(Context context, Listener listener) {
        this.context = context;
        content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(6), dp(2), dp(6), dp(2));
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.WHITE);
        background.setStroke(dp(1), Color.BLACK); background.setCornerRadius(dp(8)); content.setBackground(background);
        status = new TextView(context); status.setTextColor(Color.BLACK); status.setTextSize(13);
        status.setGravity(Gravity.CENTER); status.setSingleLine(true);
        content.addView(status, new LinearLayout.LayoutParams(-1, dp(30)));
        LinearLayout row = new LinearLayout(context);
        String[] labels = {"Copy", "Duplicate", "Cut", "Delete", "Done"};
        for (Action action : Action.values()) {
            NotebookButton button = new NotebookButton(context); button.setText(labels[action.ordinal()]);
            button.setTextSize(14); button.setSingleLine(true);
            button.setOnClickListener(v -> listener.onAction(action)); buttons.put(action, button);
            row.addView(button, new LinearLayout.LayoutParams(0, dp(44), 1));
        }
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));
        popup = new PopupWindow(content, 0, 0, false);
        popup.setTouchable(true); popup.setOutsideTouchable(false); popup.setFocusable(false);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED); popup.setClippingEnabled(false);
        popup.setIsLaidOutInScreen(true); popup.setAnimationStyle(0); popup.setElevation(0);
        popup.setEnterTransition(null); popup.setExitTransition(null);
    }

    void show(View anchor, RectF selection, Rect tools, int count) {
        if (!anchor.isAttachedToWindow() || anchor.getWidth() < 1 || anchor.getHeight() < 1) return;
        status.setText(count == 0 ? "No strokes selected · draw around the ink"
            : count + (count == 1 ? " stroke" : " strokes") + " · drag with finger or stylus");
        for (Action action : Action.values()) buttons.get(action).setAvailable(action == Action.DONE || count > 0);
        int width = Math.min(dp(420), Math.max(1, anchor.getWidth() - dp(16)));
        int height = Math.min(anchor.getHeight(), dp(78));
        int[] location = new int[2]; anchor.getLocationOnScreen(location);
        int[] position = NotebookSelectionPlacement.place(anchor.getWidth(), anchor.getHeight(), width, height, dp(8),
            selection.left, selection.top, selection.right, selection.bottom,
            tools.left - location[0], tools.top - location[1], tools.right - location[0], tools.bottom - location[1]);
        popup.setWidth(width); popup.setHeight(height);
        if (popup.isShowing()) popup.update(location[0] + position[0], location[1] + position[1], width, height);
        else popup.showAtLocation(anchor, Gravity.TOP | Gravity.LEFT, location[0] + position[0], location[1] + position[1]);
    }
    void hide() { if (popup.isShowing()) popup.dismiss(); }
    void copied() { status.setText("Copied · Done, then tap Lasso to paste"); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
