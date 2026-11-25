package org.eink.inkprobe.notebook;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import java.util.function.IntConsumer;

/** A compact folder-aware picker shared by floating and split note workflows. */
final class NotebookNotePicker {
    private final Context context;
    private final List<NotebookCatalog.Folder> folders;
    private final List<NotebookCatalog.Entry> entries;
    private final IntConsumer selected;
    private final LinearLayout content;
    private final ScrollView scroll;
    private final TextView location;
    private final AlertDialog dialog;
    private int folder;

    NotebookNotePicker(Context context, String title, List<NotebookCatalog.Folder> folders,
            List<NotebookCatalog.Entry> entries, IntConsumer selected) {
        this.context = context; this.folders = folders; this.entries = entries; this.selected = selected;
        FrameLayout frame = new FrameLayout(context);
        LinearLayout body = new LinearLayout(context); body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(8), dp(6), dp(8), dp(6));
        TextView heading = label(title, 17); heading.setTypeface(null, Typeface.BOLD);
        body.addView(heading, new LinearLayout.LayoutParams(-1, dp(34)));
        location = label("", 13); location.setSingleLine(true); location.setOnClickListener(v -> up());
        body.addView(location, new LinearLayout.LayoutParams(-1, dp(32)));
        scroll = new ScrollView(context); scroll.setFillViewport(false);
        content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, dp(42), dp(4)); scroll.addView(content);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, dp(240)));
        frame.addView(body);
        dialog = new AlertDialog.Builder(context).setView(frame).create();
        NotebookIconButton close = new NotebookIconButton(context, NotebookIconDrawable.Icon.CLOSE, "Close note picker");
        close.setTooltipText("Close"); close.setOnClickListener(v -> dialog.dismiss());
        FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.BOTTOM | Gravity.RIGHT);
        corner.rightMargin = dp(8); corner.bottomMargin = dp(8); frame.addView(close, corner);
        render();
    }
    AlertDialog dialog() { return dialog; }
    private void up() {
        NotebookCatalog.Folder current = NotebookFolders.find(folders, folder);
        if (current != null) { folder = current.parentId; render(); }
    }
    private void render() {
        location.setText(folder == 0 ? "Bookshelf" : "‹ " + NotebookFolders.path(folders, folder));
        location.setContentDescription(folder == 0 ? "Bookshelf" : "Back to parent folder, " + NotebookFolders.path(folders, folder));
        content.removeAllViews(); int count = 0;
        for (NotebookCatalog.Folder item : folders) if (item.parentId == folder) {
            add(item.name + "  ›", NotebookIconDrawable.Icon.FOLDER, () -> { folder = item.id; render(); }); count++;
        }
        for (NotebookCatalog.Entry entry : entries) if (entry.folderId == folder) {
            add(entry.name, NotebookIconDrawable.Icon.PAPER_LINED, () -> { selected.accept(entry.id); dialog.dismiss(); }); count++;
        }
        if (count == 0) { TextView empty = label("No available notes in this folder", 13); content.addView(empty); }
        android.view.ViewGroup.LayoutParams size = scroll.getLayoutParams();
        size.height = dp(Math.min(240, Math.max(42, count * 42 + 4))); scroll.setLayoutParams(size);
    }
    private void add(String text, NotebookIconDrawable.Icon icon, Runnable action) {
        TextView item = label(text, 15); item.setSingleLine(true); item.setEllipsize(android.text.TextUtils.TruncateAt.END);
        NotebookIconDrawable symbol = new NotebookIconDrawable(icon); symbol.setBounds(0, 0, dp(20), dp(20));
        item.setCompoundDrawablesRelative(symbol, null, null, null); item.setCompoundDrawablePadding(dp(10));
        item.setPadding(dp(6), 0, dp(6), 0); item.setOnClickListener(v -> action.run());
        content.addView(item, new LinearLayout.LayoutParams(-1, dp(42)));
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(context); view.setText(text); view.setTextSize(size);
        view.setTextColor(Color.BLACK); view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
