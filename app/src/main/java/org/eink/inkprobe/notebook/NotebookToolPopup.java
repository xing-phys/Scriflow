package org.eink.inkprobe.notebook;

import android.app.AlertDialog;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import java.util.ArrayList;
import java.util.List;

/** Popup geometry follows the triggering control, including empty space in a multi-row toolbox. */
final class NotebookToolPopup {
    static NotebookPenSliderLayout.Box place(View workspace, View button, Rect tools, boolean vertical, int width, int height) {
        int[] origin = new int[2]; workspace.getLocationOnScreen(origin);
        int[] location = new int[2]; button.getLocationOnScreen(location);
        List<NotebookPenSliderLayout.Box> occupied = new ArrayList<>();
        controls(button.getRootView(), origin, occupied);
        float density = workspace.getResources().getDisplayMetrics().density;
        return NotebookPenSliderLayout.place(workspace.getWidth(), workspace.getHeight(),
            new NotebookPenSliderLayout.Box(location[0] - origin[0], location[1] - origin[1], Math.max(1, button.getWidth()), Math.max(1, button.getHeight())),
            new NotebookPenSliderLayout.Box(tools.left - origin[0], tools.top - origin[1], Math.max(1, tools.width()), Math.max(1, tools.height())),
            vertical, width, height, Math.round(6 * density), Math.round(8 * density), occupied);
    }

    private static void controls(View view, int[] origin, List<NotebookPenSliderLayout.Box> occupied) {
        if (!view.isShown()) return;
        // Containers own contact streams, but only actual controls obstruct a popup.
        if (!(view instanceof ViewGroup) && (view.isClickable() || view.isFocusable())) {
            Rect rect = new Rect();
            if (view.getLocalVisibleRect(rect) && !rect.isEmpty()) {
                int[] location = new int[2]; view.getLocationOnScreen(location);
                occupied.add(new NotebookPenSliderLayout.Box(rect.left + location[0] - origin[0],
                    rect.top + location[1] - origin[1], rect.width(), rect.height()));
            }
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            controls(((ViewGroup) view).getChildAt(i), origin, occupied);
    }

    static void anchor(AlertDialog dialog, View workspace, View button, Rect tools, boolean vertical) {
        if (!workspace.isAttachedToWindow() || workspace.getWidth() <= 0 || workspace.getHeight() <= 0) return;
        // Set the initial window position before attaching it. Moving a shown
        // Android 10 dialog can leave cached accessibility bounds at its old origin.
        NotebookUi.prepareDialog(dialog);
        Window window = dialog.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decor = window.getDecorView();
        int width = Math.min(Math.round(320 * workspace.getResources().getDisplayMetrics().density), workspace.getWidth());
        decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(workspace.getHeight(), View.MeasureSpec.AT_MOST));
        NotebookPenSliderLayout.Box box = place(workspace, button, tools, vertical, width, Math.max(1, decor.getMeasuredHeight()));
        int[] origin = new int[2]; workspace.getLocationOnScreen(origin);
        WindowManager.LayoutParams params = window.getAttributes();
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = origin[0] + box.left; params.y = origin[1] + box.top;
        params.width = box.width; params.height = box.height; window.setAttributes(params);
    }
    private NotebookToolPopup() { }
}
