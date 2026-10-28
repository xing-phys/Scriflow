package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.view.WindowManager;
import android.widget.PopupWindow;

/** Application panels use the Activity token even when their geometry belongs to a floating note. */
final class NotebookPopupHost {
    private NotebookPopupHost() { }

    static boolean show(PopupWindow popup, View pane, int gravity, int x, int y) {
        Activity activity = activity(pane.getContext());
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
        View root = activity.getWindow().getDecorView();
        if (!pane.isAttachedToWindow() || !root.isAttachedToWindow() || root.getWindowToken() == null) return false;
        try {
            popup.showAtLocation(root, gravity, x, y);
            return true;
        } catch (WindowManager.BadTokenException error) {
            // A pause/destroy can race the attachment check. Losing a UI panel must not fail native handwriting.
            try { if (popup.isShowing()) popup.dismiss(); }
            catch (IllegalArgumentException ignored) { /* WindowManager never accepted the stale window. */ }
            return false;
        }
    }

    private static Activity activity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            Context base = ((ContextWrapper) context).getBaseContext();
            if (base == context) return null;
            context = base;
        }
        return context instanceof Activity ? (Activity) context : null;
    }
}
