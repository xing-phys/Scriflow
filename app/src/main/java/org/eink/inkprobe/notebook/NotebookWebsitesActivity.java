package org.eink.inkprobe.notebook;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.GridView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** The bookshelf's permanent Saved websites folder. Listing never contacts the network. */
public final class NotebookWebsitesActivity extends NotebookDisplayActivity {
    private final List<NotebookWebsites.Saved> entries = new ArrayList<>();
    private final WebsiteAdapter adapter = new WebsiteAdapter();
    private TextView status;
    private GridView grid;
    private NotebookButton presentation;
    private boolean active, busy, thumbnails;
    private int generation;
    private AlertDialog shownDialog;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        thumbnails = NotebookCollectionLayout.thumbnails(this, "websites");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); getWindow().setWindowAnimations(0);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE); root.setPadding(dp(18), dp(10), dp(18), dp(10));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(button("Bookshelf", NotebookIconDrawable.Icon.LIBRARY, this::bookshelf), new LinearLayout.LayoutParams(dp(112), dp(38)));
        TextView title = label("Saved websites", 20); title.setPadding(dp(12), 0, dp(8), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        header.addView(deviceSettingsIsland(), new LinearLayout.LayoutParams(-2, dp(32))); root.addView(header);
        NotebookButton browser = button("Open browser", NotebookIconDrawable.Icon.BROWSER, () -> {
            if (!active || busy) return;
            startActivity(new Intent(this, NotebookBrowserActivity.class)); overridePendingTransition(0, 0);
        });
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(browser, new LinearLayout.LayoutParams(dp(160), dp(42)));
        actions.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        presentation = button("", NotebookIconDrawable.Icon.OVERVIEW, () -> {
            thumbnails = !thumbnails; NotebookCollectionLayout.setThumbnails(this, "websites", thumbnails); updatePresentation();
        });
        actions.addView(presentation, new LinearLayout.LayoutParams(dp(44), dp(42))); root.addView(actions);
        status = label("Opening Saved websites…", 12); status.setMaxLines(2);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(30)));
        grid = new GridView(this); grid.setBackgroundColor(Color.WHITE); grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setId(android.R.id.list); grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setHorizontalSpacing(dp(8)); grid.setVerticalSpacing(dp(8));
        grid.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        grid.setAdapter(adapter); grid.setOnItemClickListener((parent, view, position, id) -> open(entries.get(position)));
        TextView empty = label("No saved websites yet.\n\nOpen Browser from the bookshelf, visit a page and tap Save.\nYour saved pages will be available here without Wi-Fi.", 17);
        empty.setGravity(Gravity.CENTER); empty.setPadding(dp(24), dp(24), dp(24), dp(24));
        android.widget.FrameLayout body = new android.widget.FrameLayout(this);
        body.addView(grid, new android.widget.FrameLayout.LayoutParams(-1, -1));
        body.addView(empty, new android.widget.FrameLayout.LayoutParams(-1, -1)); grid.setEmptyView(empty);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root); updatePresentation();
    }

    private void updatePresentation() {
        grid.setNumColumns(NotebookCollectionLayout.columns(this, thumbnails));
        String label = thumbnails ? "Show saved websites as list" : "Show saved website thumbnails";
        NotebookUi.action(presentation, thumbnails ? NotebookIconDrawable.Icon.OUTLINE : NotebookIconDrawable.Icon.OVERVIEW);
        presentation.setContentDescription(label); presentation.setTooltipText(label); adapter.notifyDataSetChanged();
    }

    @Override protected void onResume() { super.onResume(); active = true; refresh(); }
    @Override protected void onPause() {
        active = false; ++generation;
        if (shownDialog != null) { shownDialog.dismiss(); shownDialog = null; }
        super.onPause();
    }

    private void refresh() {
        busy = true; final int request = ++generation;
        NotebookWebsiteTasks.run(() -> new NotebookWebsites(getFilesDir()).list(), (saved, error) -> {
            if (!active || request != generation) return;
            busy = false;
            if (error != null) { problem(error.getMessage()); return; }
            entries.clear(); entries.addAll(saved); adapter.notifyDataSetChanged();
            status.setText(entries.size() + " saved pages · Tap to read offline · Each save keeps one page"); status.setOnClickListener(null);
        });
    }

    private void open(NotebookWebsites.Saved entry) {
        if (!active || busy) return;
        startActivity(new Intent(this, NotebookBrowserActivity.class).putExtra(NotebookBrowserActivity.SAVED_ID, entry.id));
        overridePendingTransition(0, 0);
    }

    private void confirmDelete(NotebookWebsites.Saved entry) {
        if (!active || busy) return;
        shownDialog = new AlertDialog.Builder(this).setTitle("Delete saved website?")
            .setMessage("Remove the offline copy of “" + entry.title + "” from this tablet? You can save the page again while online.")
            .setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) -> {
                busy = true; final int request = ++generation;
                NotebookWebsiteTasks.run(() -> { new NotebookWebsites(getFilesDir()).delete(entry.id); return null; }, (value, error) -> {
                    if (!active || request != generation) return;
                    busy = false;
                    if (error != null) problem(error.getMessage()); else refresh();
                });
            }).create();
        NotebookUi.show(shownDialog);
    }

    private void problem(String message) {
        status.setText("Couldn’t read Saved websites · tap here to retry"); status.setOnClickListener(view -> { if (!busy) refresh(); });
        shownDialog = new AlertDialog.Builder(this).setTitle("Saved websites").setMessage(message).setPositiveButton("Done", null).create();
        NotebookUi.show(shownDialog);
    }

    private void bookshelf() {
        startActivity(new Intent(this, NotebookLibraryActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish(); overridePendingTransition(0, 0);
    }

    private final class WebsiteAdapter extends BaseAdapter {
        @Override public int getCount() { return entries.size(); }
        @Override public Object getItem(int position) { return entries.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            NotebookWebsites.Saved entry = entries.get(position);
            if (thumbnails) return thumbnail(entry);
            LinearLayout row = new LinearLayout(NotebookWebsitesActivity.this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(9), dp(7), dp(9)); row.setBackground(NotebookUi.rounded(getResources().getDisplayMetrics().density, 7));
            LinearLayout info = new LinearLayout(NotebookWebsitesActivity.this); info.setOrientation(LinearLayout.VERTICAL);
            TextView name = label(entry.title, 16); name.setMaxLines(2); name.setEllipsize(TextUtils.TruncateAt.END); info.addView(name);
            TextView url = label(entry.url, 12); url.setSingleLine(true); url.setEllipsize(TextUtils.TruncateAt.MIDDLE); info.addView(url);
            String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(entry.savedAt));
            info.addView(label(date + " · " + Formatter.formatFileSize(NotebookWebsitesActivity.this, entry.bytes), 12));
            info.setContentDescription("Read offline: " + entry.title); info.setOnClickListener(view -> open(entry));
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            NotebookButton delete = button("", NotebookIconDrawable.Icon.DELETE, () -> confirmDelete(entry)); delete.setContentDescription("Delete saved website " + entry.title);
            row.addView(delete, new LinearLayout.LayoutParams(dp(44), dp(42))); return row;
        }

        private View thumbnail(NotebookWebsites.Saved entry) {
            LinearLayout card = new LinearLayout(NotebookWebsitesActivity.this); card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(7), dp(7), dp(7), dp(7)); card.setBackground(NotebookUi.rounded(getResources().getDisplayMetrics().density, 7));
            card.setOnClickListener(view -> open(entry)); card.setContentDescription("Read offline: " + entry.title);
            card.addView(new NotebookBrowserPreview(NotebookWebsitesActivity.this, entry.url, false), new LinearLayout.LayoutParams(-1, -2));
            LinearLayout footer = new LinearLayout(NotebookWebsitesActivity.this); footer.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = label(entry.title, 14); name.setMaxLines(2); name.setEllipsize(TextUtils.TruncateAt.END);
            footer.addView(name, new LinearLayout.LayoutParams(0, dp(48), 1));
            NotebookButton delete = button("", NotebookIconDrawable.Icon.DELETE, () -> confirmDelete(entry));
            delete.setContentDescription("Delete saved website " + entry.title); footer.addView(delete, new LinearLayout.LayoutParams(dp(40), dp(40)));
            card.addView(footer);
            TextView host = label(NotebookBrowserLibrary.host(entry.url), 11); host.setSingleLine(true); host.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(host, new LinearLayout.LayoutParams(-1, dp(20))); return card;
        }
    }

    private NotebookButton button(String text, NotebookIconDrawable.Icon icon, Runnable action) {
        NotebookButton button = new NotebookButton(this); button.setText(text); button.setTextSize(12);
        NotebookUi.action(button, icon); button.setOnClickListener(view -> action.run()); return button;
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(Color.BLACK);
        view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
