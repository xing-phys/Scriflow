package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import static org.eink.inkprobe.notebook.NotebookBrowserUi.*;

/** Shared preview cards keep page text, selection and secondary actions in separate regions. */
final class NotebookBrowserCard extends LinearLayout {
    NotebookBrowserCard(Context context, String title, String url, String description, boolean selected,
            boolean add, Runnable open, NotebookIconDrawable.Icon actionIcon, String actionName, Runnable action) {
        this(context, title, url, description, selected, add, open, actionIcon, actionName, action, false);
    }
    NotebookBrowserCard(Context context, String title, String url, String description, boolean selected,
            boolean add, Runnable open, NotebookIconDrawable.Icon actionIcon, String actionName, Runnable action, boolean compact) {
        super(context); setOrientation(VERTICAL); setPadding(dp(context, 8), dp(context, 8), dp(context, 8), dp(context, 8));
        GradientDrawable border = NotebookUi.rounded(getResources().getDisplayMetrics().density, 7);
        if (selected) border.setStroke(dp(context, 2), Color.BLACK); setBackground(border);
        setContentDescription(description); setFocusable(true); setOnClickListener(view -> open.run());
        addView(new NotebookBrowserPreview(context, url, add), new LayoutParams(-1, -2));
        LinearLayout footer = row(context); footer.setPadding(dp(context, 4), dp(context, 8), 0, 0);
        TextView name = label(context, (selected ? "✓ " : "") + title, 14); name.setMaxLines(2); name.setEllipsize(TextUtils.TruncateAt.END); name.setGravity(Gravity.CENTER_VERTICAL); name.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        footer.addView(name, new LayoutParams(0, dp(context, compact ? 40 : 48), 1));
        if (action != null) footer.addView(icon(context, actionIcon, actionName, action), beside(context, compact ? 40 : 48, compact ? 40 : 48)); addView(footer);
        TextView host = label(context, url == null || url.isEmpty() ? "" : NotebookBrowserLibrary.host(url), 11); host.setSingleLine(true); host.setEllipsize(TextUtils.TruncateAt.END); host.setPadding(dp(context, 4), dp(context, 4), dp(context, 4), 0); addView(host, new LayoutParams(-1, dp(context, compact ? 18 : 22)));
    }
}
