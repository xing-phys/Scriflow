package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import static org.eink.inkprobe.notebook.NotebookBrowserUi.*;

/** Compact in-browser download history; completed files remain in shared Downloads. */
final class NotebookDownloadDialog {
    private final Activity activity;
    private final NotebookDownloads downloads;
    private final List<NotebookDownloads.Item> items = new ArrayList<>();
    private AlertDialog dialog;
    private int shownWidth, shownHeight;
    private final Runnable update = this::render;
    private final BaseAdapter adapter = new BaseAdapter() {
        public int getCount() { return items.size(); }
        public Object getItem(int position) { return items.get(position); }
        public long getItemId(int position) { return items.get(position).id.hashCode(); }
        public View getView(int position, View recycled, ViewGroup parent) { return row(items.get(position)); }
    };

    NotebookDownloadDialog(Activity activity) { this.activity = activity; downloads = NotebookDownloads.get(activity); }
    AlertDialog show() {
        LinearLayout content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout title = NotebookUi.dialogTitle(activity, "Downloads");
        NotebookUi.titleAction(title, NotebookIconDrawable.Icon.FOLDER, "Open Downloads in Files", this::files);
        NotebookUi.titleAction(title, NotebookIconDrawable.Icon.CLOSE, "Close downloads", () -> dialog.dismiss());
        content.addView(title);
        ListView list = new ListView(activity); NotebookBrowserUi.list(list); list.setPadding(dp(activity, 10), 0, dp(activity, 10), dp(activity, 10));
        TextView empty = label(activity, "No downloads yet", 15); empty.setGravity(Gravity.CENTER); content.addView(empty, new LinearLayout.LayoutParams(-1, dp(activity, 80)));
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1)); list.setEmptyView(empty); list.setAdapter(adapter);
        dialog = new AlertDialog.Builder(activity).setView(content).create();
        dialog.setOnDismissListener(ignored -> downloads.removeObserver(update));
        downloads.observe(update); render(); NotebookUi.show(dialog);
        resize();
        return dialog;
    }
    private void render() { items.clear(); items.addAll(downloads.items()); adapter.notifyDataSetChanged(); if(dialog!=null&&dialog.isShowing())resize(); }
    void resize() {
        if(dialog==null||!dialog.isShowing())return;
        int width=Math.min(dp(activity,620),activity.getResources().getDisplayMetrics().widthPixels-dp(activity,28));
        int height=Math.min(Math.min(dp(activity,600),activity.getResources().getDisplayMetrics().heightPixels-dp(activity,48)),dp(activity,64+72*Math.max(1,Math.min(items.size(),8))));
        if(shownHeight!=height||shownWidth!=width){shownWidth=width;shownHeight=height;dialog.getWindow().setLayout(width,height);}
    }
    private View row(NotebookDownloads.Item item) {
        LinearLayout row = NotebookBrowserUi.row(activity); row.setPadding(dp(activity, 12), dp(activity, 5), dp(activity, 4), dp(activity, 5));
        row.setBackground(NotebookUi.rounded(activity.getResources().getDisplayMetrics().density, 7));
        LinearLayout details = new LinearLayout(activity); details.setOrientation(LinearLayout.VERTICAL);
        TextView name = label(activity, item.name, 15); name.setSingleLine(true); name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        details.addView(name, new LinearLayout.LayoutParams(-1, dp(activity, 26)));
        TextView status = label(activity, description(item), 12); status.setTextColor(Color.DKGRAY); status.setMaxLines(2); status.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(status, new LinearLayout.LayoutParams(-1, -2));
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        NotebookBrowserIconButton more = icon(activity, NotebookIconDrawable.Icon.MORE, "Actions for download " + item.name, () -> {});
        more.setOnClickListener(view -> actions(item, view)); row.addView(more, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)));
        if (item.state == NotebookDownloads.State.COMPLETE) details.setOnClickListener(view -> open(item));
        else details.setOnClickListener(view -> actions(item, more));
        return row;
    }
    private String description(NotebookDownloads.Item item) {
        String size = Formatter.formatShortFileSize(activity, item.bytes);
        switch (item.state) {
            case WAITING: return "Waiting";
            case DOWNLOADING: return item.total > 0 ? size + " / " + Formatter.formatShortFileSize(activity, item.total) + " · " + (int)Math.min(100, item.bytes * 100.0 / item.total) + "%" : size + " downloaded";
            case CANCELING: return "Canceling…";
            case RECOVERING: return "Checking previous download…";
            case COMPLETE: return size + " · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(item.created));
            case CANCELED: return item.error.isEmpty() ? "Canceled" : "Canceled · " + item.error;
            case FAILED: return "Failed · " + item.error;
            default: return "Interrupted · " + item.error;
        }
    }
    private void actions(NotebookDownloads.Item item, View anchor) {
        List<NotebookActionBar.Action> actions = new ArrayList<>();
        if (item.active()) actions.add(NotebookActionBar.action(NotebookIconDrawable.Icon.STOP, "Cancel download", () -> downloads.cancel(item.id)));
        else if (item.state != NotebookDownloads.State.RECOVERING) {
            if (item.state == NotebookDownloads.State.COMPLETE) {
                actions.add(NotebookActionBar.action(NotebookIconDrawable.Icon.READING, "Open download", () -> open(item)));
                actions.add(NotebookActionBar.action(NotebookIconDrawable.Icon.FOLDER, "Open Downloads in Files", this::files));
            }
            actions.add(NotebookActionBar.action(NotebookIconDrawable.Icon.CLOSE, "Remove from download history", () -> remove(item, false)));
            if (!item.uri.isEmpty()) actions.add(NotebookActionBar.action(NotebookIconDrawable.Icon.DELETE, "Delete downloaded file…", () -> confirmDelete(item)));
        }
        NotebookActionBar.show(anchor, actions.toArray(new NotebookActionBar.Action[0]));
    }
    private void remove(NotebookDownloads.Item item, boolean delete) {
        downloads.remove(item.id, delete, error -> { if (error != null && !activity.isFinishing()) Toast.makeText(activity, error, Toast.LENGTH_LONG).show(); });
    }
    private void confirmDelete(NotebookDownloads.Item item) {
        AlertDialog confirm = new AlertDialog.Builder(activity).setTitle("Delete downloaded file?")
            .setMessage(item.name + " will be deleted from tablet storage.")
            .setNegativeButton("Cancel", null).setPositiveButton("Delete", (owner, which) -> remove(item, true)).create();
        NotebookUi.show(confirm);
    }
    private void files() {
        dialog.dismiss(); activity.startActivity(new Intent(activity, NotebookFilesActivity.class).putExtra(NotebookFilesActivity.SHOW_DOWNLOADS, true));
    }
    private void open(NotebookDownloads.Item item) {
        Intent open = NotebookFileStorage.downloadIntent(activity, Uri.parse(item.uri), item.name, item.mime);
        try { activity.startActivity(open); dialog.dismiss(); }
        catch (ActivityNotFoundException | SecurityException error) { Toast.makeText(activity, "No app can open this file. Check Downloads in Files.", Toast.LENGTH_LONG).show(); }
    }
}
