package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupWindow;

/** Four thin opaque edges track resizing without allocating or reflowing a page-sized window. */
final class NotebookResizeOutline {
    private final PopupWindow[] edges = new PopupWindow[4];
    private final int stroke;
    private final Rect bounds = new Rect();
    NotebookResizeOutline(Context context) {
        stroke = Math.max(1, Math.round(2 * context.getResources().getDisplayMetrics().density));
        for (int i = 0; i < edges.length; i++) {
            View line = new View(context); line.setBackgroundColor(Color.BLACK);
            PopupWindow popup = new PopupWindow(line, 1, 1, false);
            popup.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            popup.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_PANEL);
            popup.setTouchable(false); popup.setOutsideTouchable(false); popup.setClippingEnabled(false);
            popup.setIsLaidOutInScreen(true); popup.setAnimationStyle(0); popup.setElevation(0);
            popup.setEnterTransition(null); popup.setExitTransition(null); edges[i] = popup;
        }
    }
    void show(View root, NotebookWorkspace.Box box) {
        if (!root.isAttachedToWindow()) { hide(); return; }
        int[] origin = new int[2]; root.getLocationOnScreen(origin);
        int x = origin[0] + box.x, y = origin[1] + box.y;
        int w = Math.max(stroke * 2, box.width), h = Math.max(stroke * 2, box.height);
        int[][] rectangles = {{x,y,w,stroke},{x,y+h-stroke,w,stroke},
            {x,y+stroke,stroke,h-2*stroke},{x+w-stroke,y+stroke,stroke,h-2*stroke}};
        for (int i=0;i<edges.length;i++) {
            int[] r=rectangles[i]; PopupWindow edge=edges[i];
            if (edge.isShowing()) edge.update(r[0],r[1],r[2],r[3]);
            else {
                edge.setWidth(r[2]); edge.setHeight(r[3]);
                if (!NotebookPopupHost.show(edge,root,Gravity.TOP|Gravity.LEFT,r[0],r[1])) { hide(); return; }
            }
        }
        bounds.set(x,y,x+w,y+h);
    }
    boolean showing() { return edges[0].isShowing(); }
    void hide() {
        for (PopupWindow edge:edges) if (edge.isShowing()) {
            try { edge.dismiss(); } catch (IllegalArgumentException ignored) { }
        }
        bounds.setEmpty();
    }
}
