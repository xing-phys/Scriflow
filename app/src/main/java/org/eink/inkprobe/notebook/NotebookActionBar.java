package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

/** A small, scrollable row of labeled icon actions anchored to an item or toolbar. */
final class NotebookActionBar {
    static final class Action {
        final NotebookIconDrawable.Icon icon;
        final String label;
        final Runnable run;
        Action(NotebookIconDrawable.Icon icon, String label, Runnable run) {
            this.icon = icon; this.label = label; this.run = run;
        }
    }
    static Action action(NotebookIconDrawable.Icon icon, String label, Runnable run) {
        return new Action(icon, label, run);
    }
    static PopupWindow show(View anchor, Action... actions) {
        if (anchor == null || !anchor.isAttachedToWindow() || actions.length == 0) return null;
        Context context = anchor.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        int buttonSize = Math.round(44 * density), inset = Math.round(4 * density);
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(Math.max(1, screenWidth - 2 * inset), 2 * inset + actions.length * buttonSize);
        LinearLayout row = new LinearLayout(context); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(inset, inset, inset, inset);
        row.setBackground(NotebookUi.rounded(density, 12));
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setFillViewport(false); scroll.setHorizontalScrollBarEnabled(false); scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(row);
        PopupWindow popup = new PopupWindow(scroll, width, buttonSize + 2 * inset, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true); popup.setClippingEnabled(true); popup.setAnimationStyle(0); popup.setElevation(0);
        for (Action action : actions) {
            NotebookBrowserIconButton button = new NotebookBrowserIconButton(context, action.icon, action.label,
                () -> { popup.dismiss(); action.run.run(); });
            row.addView(button, new LinearLayout.LayoutParams(buttonSize, buttonSize));
        }
        int[] location = new int[2]; anchor.getLocationOnScreen(location);
        int target = Math.max(inset, Math.min(screenWidth - inset - width, location[0] + anchor.getWidth() - width));
        try { popup.showAsDropDown(anchor, target - location[0], inset, Gravity.LEFT); }
        catch (WindowManager.BadTokenException error) { return null; }
        return popup;
    }
    private NotebookActionBar() { }
}
