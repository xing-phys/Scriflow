package org.eink.inkprobe.notebook;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

/** Shared flat e-ink window styling, including the window's Material shadow. */
final class NotebookUi {
    private NotebookUi() { }

    static GradientDrawable border(float density) {
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.WHITE);
        border.setStroke(Math.max(1, Math.round(density)), Color.BLACK);
        return border;
    }

    static void prepare(Window window) {
        if (window == null) return;
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0f);
        window.setWindowAnimations(0);
        window.setElevation(0f);
        window.setBackgroundDrawable(border(window.getContext().getResources().getDisplayMetrics().density));
        View decor = window.getDecorView();
        decor.setElevation(0f); decor.setTranslationZ(0f); decor.setStateListAnimator(null);
    }

    static void show(AlertDialog dialog) {
        dialog.create();
        prepare(dialog.getWindow());
        dialog.show();
        if (dialog.getListView() != null)
            dialog.getListView().setSelector(new ColorDrawable(Color.TRANSPARENT));
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(which);
            if (button != null) { NotebookButton.makeStatic(button); button.setTextSize(13); }
        }
        TextView title = dialog.findViewById(dialog.getContext().getResources()
            .getIdentifier("alertTitle", "id", "android"));
        if (title != null) title.setTextSize(16);
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextSize(13);
    }
}
