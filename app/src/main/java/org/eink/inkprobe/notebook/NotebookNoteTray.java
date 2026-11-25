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
        show(anchor,notesButton,tools,vertical,panes,"Open a new floating note",NotebookIconDrawable.Icon.PAPER_BLANK);
    }
    void show(View anchor,View notesButton,Rect tools,boolean vertical,List<NotebookWorkspace.Pane> panes,String addName,NotebookIconDrawable.Icon restoreIcon) {
        show(anchor,notesButton,tools,vertical,panes,addName,restoreIcon,null);
    }
    void show(View anchor,View notesButton,Rect tools,boolean vertical,List<NotebookWorkspace.Pane> panes,String addName,NotebookIconDrawable.Icon restoreIcon,Runnable history) {
        hide();
        LinearLayout row = new LinearLayout(context); row.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.WHITE);
        background.setStroke(dp(1), Color.BLACK); background.setCornerRadius(dp(12)); row.setBackground(background);
        NotebookIconButton add = new NotebookIconButton(context, restoreIcon == NotebookIconDrawable.Icon.PAPER_BLANK
            ? NotebookIconDrawable.Icon.OPEN_NOTES : NotebookIconDrawable.Icon.PLUS, addName);
        add.setTooltipText(addName);
        add.setOnClickListener(v -> listener.add()); row.addView(add, new LinearLayout.LayoutParams(dp(32), dp(32)));
        int count = 1;
        if(history!=null){
            NotebookIconButton saved=new NotebookIconButton(context,NotebookIconDrawable.Icon.OUTLINE,"Saved chat history");saved.setTooltipText("Saved chat history");saved.setOnClickListener(v->history.run());
            LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(dp(32),dp(32));if(vertical)item.topMargin=dp(4);else item.leftMargin=dp(4);row.addView(saved,item);count++;
        }
        for (NotebookWorkspace.Pane pane : panes) {
            if (!pane.collapsed) continue;
            String description = "Reopen " + listener.name(pane.notebookId);
            NotebookIconButton button = new NotebookIconButton(context, restoreIcon, description);
            button.setTooltipText(description);
            button.setOnClickListener(v -> listener.reopen(pane));
            LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(dp(32), dp(32));
            if (vertical) item.topMargin = dp(4); else item.leftMargin = dp(4);
            row.addView(button, item); count++;
        }
        int[] origin = new int[2]; anchor.getLocationOnScreen(origin);
        int length = dp(8 + 32 * count + 4 * (count - 1));
        if (!anchor.isAttachedToWindow() || anchor.getWidth() <= 0 || anchor.getHeight() <= 0) return;
        NotebookPenSliderLayout.Box placement = NotebookToolPopup.place(anchor, notesButton, tools, vertical,
            vertical ? dp(40) : length, vertical ? length : dp(40));
        int width = placement.width, height = placement.height;
        FrameLayout scroll;
        if (vertical) { ScrollView view = new ScrollView(context); view.setFillViewport(true); view.addView(row); scroll = view; }
        else { HorizontalScrollView view = new HorizontalScrollView(context); view.setFillViewport(true); view.addView(row); scroll = view; }
        int x = origin[0] + placement.left, y = origin[1] + placement.top;
        popup = new PopupWindow(scroll, width, height, false); popup.setAnimationStyle(0);
        popup.setWindowLayoutType(android.view.WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
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
