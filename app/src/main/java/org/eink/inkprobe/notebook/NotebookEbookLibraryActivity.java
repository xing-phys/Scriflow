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
import android.widget.*;
import java.util.ArrayList;
import java.util.List;

/** A separate, offline book shelf with immutable originals and per-book text anchors. */
public final class NotebookEbookLibraryActivity extends NotebookDisplayActivity {
    private final List<NotebookEbookStore.Book> books = new ArrayList<>();
    private final BookAdapter adapter = new BookAdapter();
    private TextView status;
    private boolean resumed, busy;
    private int generation;
    private AlertDialog dialog;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().setWindowAnimations(0);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE); root.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(button("Bookshelf", NotebookIconDrawable.Icon.LIBRARY, this::finish), new LinearLayout.LayoutParams(dp(116), dp(42)));
        TextView title = label("Books", 21); title.setPadding(dp(12), 0, 0, 0); header.addView(title, new LinearLayout.LayoutParams(0, dp(46), 1));
        header.addView(deviceSettingsIsland()); root.addView(header);
        root.addView(button("Open / import book", NotebookIconDrawable.Icon.PLUS, () -> {
            if (resumed && !busy) startActivity(new Intent(this, NotebookEbookImportActivity.class));
        }), new LinearLayout.LayoutParams(-1, dp(46)));
        status = label("Opening Books…", 13); status.setMaxLines(3); root.addView(status, new LinearLayout.LayoutParams(-1, dp(64)));
        ListView list = new ListView(this); list.setId(android.R.id.list); list.setDividerHeight(dp(7)); list.setAdapter(adapter); list.setBackgroundColor(Color.WHITE);
        list.setOnItemClickListener((p,v,n,id) -> open(books.get(n))); root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
    }
    @Override protected void onResume() { super.onResume(); resumed = true; refresh(); }
    @Override protected void onPause() { resumed = false; generation++; if (dialog != null) dialog.dismiss(); super.onPause(); }
    private void refresh() {
        busy = true; int request = ++generation;
        NotebookEbookTasks.run(() -> new NotebookEbookStore(getFilesDir()).list(), (entries, error) -> {
            if (!resumed || request != generation) return;
            busy = false; books.clear(); if (entries != null) books.addAll(entries); adapter.notifyDataSetChanged();
            status.setText(error != null ? "Couldn’t open Books: " + error.getMessage() : books.isEmpty()
                ? "Import EPUB, MOBI, AZW3, DjVu, FB2, CBZ, HTML or TXT. Books stay available offline. PDFs use Import PDF on the bookshelf."
                : books.size() + " books · Most recently opened first · Tap to continue reading");
            status.setOnClickListener(v -> { if (!busy) refresh(); });
        });
    }
    private void open(NotebookEbookStore.Book book) {
        if (!resumed || busy) return;
        startActivity(new Intent(this, NotebookEbookActivity.class).putExtra("book", book.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); overridePendingTransition(0, 0);
    }
    private void remove(NotebookEbookStore.Book book) {
        if (!resumed || busy) return;
        dialog = new AlertDialog.Builder(this).setTitle("Remove book?")
            .setMessage("Remove “" + book.title + "” and its reading positions, bookmarks and text annotations from Books? The original tablet file and linked handwritten notebooks are kept.")
            .setNegativeButton("Cancel", null).setPositiveButton("Remove", (d, n) -> {
                busy = true; int request = ++generation;
                NotebookEbookTasks.run(() -> { new NotebookEbookStore(getFilesDir()).remove(book.id); return null; }, (value, error) -> {
                    if (!resumed || request != generation) return;
                    busy = false; if (error != null) status.setText(error.getMessage()); else refresh();
                });
            }).create(); NotebookUi.show(dialog);
    }
    private final class BookAdapter extends BaseAdapter {
        @Override public int getCount() { return books.size(); }
        @Override public Object getItem(int n) { return books.get(n); }
        @Override public long getItemId(int n) { return n; }
        @Override public View getView(int n, View old, ViewGroup parent) {
            NotebookEbookStore.Book book = books.get(n); LinearLayout row = new LinearLayout(NotebookEbookLibraryActivity.this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(9), dp(6), dp(9)); row.setBackground(NotebookUi.rounded(getResources().getDisplayMetrics().density, 6));
            LinearLayout info = new LinearLayout(NotebookEbookLibraryActivity.this); info.setOrientation(LinearLayout.VERTICAL);
            TextView title = label(book.title, 17); title.setMaxLines(2); title.setEllipsize(TextUtils.TruncateAt.END); info.addView(title);
            info.addView(label(book.format.toUpperCase(java.util.Locale.ROOT) + " · " + Formatter.formatFileSize(NotebookEbookLibraryActivity.this, book.bytes)
                + (book.author.isEmpty() ? "" : " · " + book.author), 12)); info.setOnClickListener(v -> open(book)); row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            NotebookButton remove = button("", NotebookIconDrawable.Icon.DELETE, () -> remove(book)); remove.setContentDescription("Remove book " + book.title);
            row.addView(remove, new LinearLayout.LayoutParams(dp(44), dp(44))); return row;
        }
    }
    private NotebookButton button(String text, NotebookIconDrawable.Icon icon, Runnable action) {
        NotebookButton view = new NotebookButton(this); view.setText(text); view.setTextSize(13); NotebookUi.action(view, icon); view.setOnClickListener(v -> action.run()); return view;
    }
    private TextView label(String text, int size) { TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(Color.BLACK); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }
}
