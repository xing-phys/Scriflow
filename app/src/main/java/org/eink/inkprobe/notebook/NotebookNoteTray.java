package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import java.util.List;

/** Small companion strip beside the toolbox: add a floating note or restore a minimized note. */
final class NotebookNoteTray {
    interface Listener { void add(); void reopen(NotebookWorkspace.Pane pane); void boundsChanged(); String name(int id); }
    private final Context context;
    private final Listener listener;
    private PopupWindow popup;
    private final Rect bounds = new Rect();
    NotebookNoteTray(Context context, Listener listener) { this.context = context; this.listener = listener; }
    Rect bounds() { return new Rect(bounds); }
    boolean showing() { return popup != null && popup.isShowing(); }
    void show(View anchor, View notesButton, Rect tools, boolean vertical, List<NotebookWorkspace.Pane> panes) {
        hide();
        LinearLayout row = new LinearLayout(context); row.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.WHITE);
        background.setStroke(dp(1), Color.BLACK); background.setCornerRadius(dp(12)); row.setBackground(background);
        NotebookIconButton add = new NotebookIconButton(context, NotebookIconDrawable.Icon.PLUS, "Open a new floating note");
        add.setOnClickListener(v -> listener.add()); row.addView(add, new LinearLayout.LayoutParams(dp(32), dp(32)));
        int count = 1;
        for (NotebookWorkspace.Pane pane : panes) {
            if (!pane.collapsed) continue;
            String description = "Reopen " + listener.name(pane.notebookId);
            NotebookIconButton button = new NotebookIconButton(context, NotebookIconDrawable.Icon.PAPER_BLANK, description);
            button.setTooltipText(description);
            button.setOnClickListener(v -> listener.reopen(pane));
            LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(dp(32), dp(32));
            if (vertical) item.topMargin = dp(4); else item.leftMargin = dp(4);
            row.addView(button, item); count++;
        }
        int[] origin = new int[2]; anchor.getLocationOnScreen(origin);
        int[] button = new int[2]; notesButton.getLocationOnScreen(button);
        int length = dp(8 + 32 * count + 4 * (count - 1));
        int width = vertical ? dp(40) : Math.min(anchor.getWidth() - dp(16), length);
        int height = vertical ? Math.min(anchor.getHeight() - dp(16), length) : dp(40);
        FrameLayout scroll;
        if (vertical) { ScrollView view = new ScrollView(context); view.setFillViewport(true); view.addView(row); scroll = view; }
        else { HorizontalScrollView view = new HorizontalScrollView(context); view.setFillViewport(true); view.addView(row); scroll = view; }
        // Align the first icon with its button, even when the toolbox strip has scrolled.
        int x = vertical ? tools.right + dp(6) : button[0] + notesButton.getWidth() / 2 - dp(20);
        int y = vertical ? button[1] + notesButton.getHeight() / 2 - dp(20) : tools.bottom + dp(6);
        if (vertical && x + width > origin[0] + anchor.getWidth() - dp(8)) x = tools.left - width - dp(6);
        if (!vertical && y + height > origin[1] + anchor.getHeight() - dp(8)) y = tools.top - height - dp(6);
        x = Math.max(origin[0] + dp(8), Math.min(origin[0] + anchor.getWidth() - width - dp(8), x));
        y = Math.max(origin[1] + dp(8), Math.min(origin[1] + anchor.getHeight() - height - dp(8), y));
        popup = new PopupWindow(scroll, width, height, false); popup.setAnimationStyle(0);
        popup.setClippingEnabled(false); popup.setIsLaidOutInScreen(true); popup.setOutsideTouchable(false);
        popup.setEnterTransition(null); popup.setExitTransition(null);
        popup.setOnDismissListener(() -> { bounds.setEmpty(); listener.boundsChanged(); });
        bounds.set(x, y, x + width, y + height); listener.boundsChanged();
        if (!NotebookPopupHost.show(popup, anchor, Gravity.TOP | Gravity.LEFT, x, y)) {
            bounds.setEmpty(); listener.boundsChanged();
        }
    }
    void hide() { if (popup != null) popup.dismiss(); popup = null; bounds.setEmpty(); listener.boundsChanged(); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
