package org.eink.inkprobe.notebook;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.Space;
import android.view.Gravity;
import android.view.ViewGroup;

/** Shared flat e-ink window styling, including the window's Material shadow. */
final class NotebookUi {
    private NotebookUi() { }

    static GradientDrawable border(float density) {
        GradientDrawable border = new GradientDrawable();
        border.setColor(Color.WHITE);
        border.setStroke(Math.max(1, Math.round(density)), Color.BLACK);
        return border;
    }

    /** Static monochrome controls share a fine outline and never animate on e-ink. */
    static GradientDrawable rounded(float density, float radius) {
        GradientDrawable shape = border(density);
        shape.setCornerRadius(radius * density);
        return shape;
    }

    static LinearLayout statusIsland(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout result = new LinearLayout(context); result.setOrientation(LinearLayout.VERTICAL);
        result.setPadding(Math.round(10*density), Math.round(4*density), Math.round(10*density), Math.round(4*density));
        GradientDrawable background = new GradientDrawable(); background.setColor(Color.WHITE);
        background.setStroke(Math.max(1, Math.round(density)), 0xff999999); background.setCornerRadius(1000*density);
        result.setBackground(background); result.setStateListAnimator(null); result.setElevation(0f);
        return result;
    }

    /** Reserve header space, but place the status at the same screen corner as the editor. */
    static View pinStatus(View content, View island) {
        if (!(island.getParent() instanceof ViewGroup)) return content;
        Context context = content.getContext();
        int margin = Math.round(8 * context.getResources().getDisplayMetrics().density);
        island.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        ViewGroup parent = (ViewGroup) island.getParent();
        int index = parent.indexOfChild(island);
        ViewGroup.LayoutParams reserved = island.getLayoutParams();
        reserved.width = island.getMeasuredWidth();
        if (reserved instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams spacing = (ViewGroup.MarginLayoutParams) reserved;
            spacing.setMarginStart(Math.max(spacing.getMarginStart(), margin));
        }
        parent.removeViewAt(index);
        parent.addView(new Space(context), index, reserved);
        FrameLayout frame = new FrameLayout(context);
        frame.addView(content, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        corner.topMargin = margin; corner.rightMargin = margin;
        frame.addView(island, corner);
        return frame;
    }

    static void action(NotebookButton button, NotebookIconDrawable.Icon icon) {
        Context context = button.getContext();
        float density = context.getResources().getDisplayMetrics().density;
        button.setBackground(rounded(density, 7));
        button.setPadding(Math.round(10*density), 0, Math.round(10*density), 0);
        button.setGravity(android.view.Gravity.CENTER);
        if (icon != null) {
            NotebookIconDrawable symbol = new NotebookIconDrawable(icon);
            int size = Math.round(17*density); symbol.setBounds(0, 0, size, size);
            button.setCompoundDrawablesRelative(symbol, null, null, null);
            button.setCompoundDrawablePadding(Math.round(6*density));
        }
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
