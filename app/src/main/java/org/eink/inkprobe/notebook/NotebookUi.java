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
    private static final String COMPACT_CLOSE_TAG = "scriflow-dialog-close";
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

    /** A compact, accessible title row shared by list dialogs. */
    static LinearLayout dialogTitle(Context context, String title) {
        float density = context.getResources().getDisplayMetrics().density;
        int inset = Math.round(12 * density);
        LinearLayout row = new LinearLayout(context); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(inset, Math.round(4 * density), inset, Math.round(4 * density));
        TextView label = new TextView(context); label.setText(title); label.setTextColor(Color.BLACK);
        label.setTextSize(16); label.setTypeface(null, android.graphics.Typeface.BOLD);
        label.setSingleLine(true); label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, Math.round(40 * density), 1));
        return row;
    }

    static NotebookIconButton titleAction(LinearLayout title, NotebookIconDrawable.Icon icon, String name, Runnable action) {
        Context context = title.getContext();
        int size = Math.round(40 * context.getResources().getDisplayMetrics().density);
        NotebookIconButton button = new NotebookIconButton(context, icon, name); button.setTooltipText(name);
        button.setOnClickListener(v -> action.run()); title.addView(button, new LinearLayout.LayoutParams(size, size));
        return button;
    }

    static void prepare(Window window) {
        if (window == null) return;
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setDimAmount(0f);
        window.setWindowAnimations(0);
        window.setElevation(0f);
        window.setBackgroundDrawable(rounded(window.getContext().getResources().getDisplayMetrics().density, 14));
        View decor = window.getDecorView();
        // Custom list backgrounds can otherwise paint over the window's bottom edge.
        GradientDrawable outline = rounded(window.getContext().getResources().getDisplayMetrics().density, 14);
        outline.setColor(Color.TRANSPARENT); decor.setForeground(outline);
        decor.setElevation(0f); decor.setTranslationZ(0f); decor.setStateListAnimator(null);
    }

    /** Prepare before measuring as well as before showing anchored tool dialogs. */
    static void prepareDialog(AlertDialog dialog) {
        dialog.create();
        Window window = dialog.getWindow();
        prepare(window);
        if (window == null || !(window.getDecorView() instanceof FrameLayout)) return;
        FrameLayout decor = (FrameLayout) window.getDecorView();
        if (decor.findViewWithTag(COMPACT_CLOSE_TAG) != null) return;
        Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        Button dismiss = isDismiss(negative) ? negative
            : isDone(positive) ? positive : null;
        if (dismiss == null || !(dismiss.getParent() instanceof ViewGroup)) return;
        boolean otherActions = dismiss != positive && visible(positive) || dismiss != neutral && visible(neutral)
            || dismiss != negative && visible(negative);
        ViewGroup bar = (ViewGroup) dismiss.getParent();
        float density = dialog.getContext().getResources().getDisplayMetrics().density;
        int size = Math.round(40 * density), margin = Math.round(8 * density);
        String label = dismiss.getText().toString();
        NotebookIconButton close = new NotebookIconButton(dialog.getContext(), NotebookIconDrawable.Icon.CLOSE, label);
        close.setTag(COMPACT_CLOSE_TAG); close.setTooltipText(label);
        // Route through Android's original button to retain its listener and dismiss ordering.
        close.setOnClickListener(v -> dismiss.performClick());
        dismiss.setVisibility(View.GONE);
        if (otherActions) {
            ViewGroup.LayoutParams dimensions = bar instanceof LinearLayout
                ? new LinearLayout.LayoutParams(size, size) : new ViewGroup.LayoutParams(size, size);
            bar.addView(close, dimensions);
        } else {
            int panelId = dialog.getContext().getResources().getIdentifier("buttonPanel", "id", "android");
            View panel = panelId == 0 ? null : dialog.findViewById(panelId);
            if (panel != null) panel.setVisibility(View.GONE); else bar.setVisibility(View.GONE);
            reserveCloseSpace(dialog, size + margin);
            FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(size, size, Gravity.BOTTOM | Gravity.RIGHT);
            corner.rightMargin = margin; corner.bottomMargin = margin; decor.addView(close, corner);
        }
    }

    private static boolean visible(Button button) {
        return button != null && button.getVisibility() == View.VISIBLE;
    }
    private static boolean isDismiss(Button button) {
        if (!visible(button)) return false;
        String text = button.getText().toString();
        return "Cancel".equalsIgnoreCase(text) || "Close".equalsIgnoreCase(text) || "Done".equalsIgnoreCase(text);
    }
    private static boolean isDone(Button button) {
        return visible(button) && "Done".equalsIgnoreCase(button.getText().toString());
    }
    private static void reserveCloseSpace(AlertDialog dialog, int space) {
        View custom = dialog.findViewById(android.R.id.custom);
        View content = custom instanceof ViewGroup && ((ViewGroup) custom).getChildCount() > 0
            ? custom : dialog.getListView() != null ? dialog.getListView() : dialog.findViewById(android.R.id.message);
        if (content != null) content.setPaddingRelative(content.getPaddingStart(), content.getPaddingTop(),
            content.getPaddingEnd() + space, content.getPaddingBottom());
    }

    static void show(AlertDialog dialog) {
        prepareDialog(dialog);
        dialog.show();
        // Some Android themes finish creating their buttons only after show().
        prepareDialog(dialog);
        if (dialog.getListView() != null)
            dialog.getListView().setSelector(new ColorDrawable(Color.TRANSPARENT));
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(which);
            if (button != null) { NotebookButton.makeStatic(button); button.setTextSize(13); }
        }
        TextView title = dialog.findViewById(dialog.getContext().getResources()
            .getIdentifier("alertTitle", "id", "android"));
        if (title != null) { title.setTextSize(16); title.setTypeface(null, android.graphics.Typeface.BOLD); }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextSize(13);
    }
}
