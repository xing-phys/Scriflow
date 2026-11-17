package org.eink.inkprobe.notebook;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/** Retained system picker / Android Open with entry point for offline books. */
public final class NotebookEbookImportActivity extends NotebookDisplayActivity {
    private static final int PICK = 2471;
    private Job job;
    private TextView status;
    private NotebookButton choose, back;
    private boolean resumed;
    private final Runnable advance = this::advance;
    private static final class Job {
        final Context context;
        boolean initialized, picking, busy, opened;
        String book, message = "Choose an ebook…";
        Runnable observer;
        Job(Context value) { context = value.getApplicationContext(); }
        void changed() { if (observer != null) observer.run(); }
        void start(Uri uri) {
            if (busy) return;
            picking = false; busy = true; initialized = true; message = "Copying ebook…"; changed();
            NotebookEbookTasks.run(() -> {
                if (uri == null || !("content".equals(uri.getScheme()) || "file".equals(uri.getScheme()))) throw new IOException("Choose a local ebook file.");
                String name = uri.getLastPathSegment();
                if ("file".equals(uri.getScheme())) {
                    File file = new File(uri.getPath()).getCanonicalFile();
                    if (file.toPath().startsWith(context.getDataDir().getCanonicalFile().toPath())) throw new IOException("Choose a file from shared storage.");
                    name = file.getName();
                } else try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst() && cursor.getString(0) != null) name = cursor.getString(0);
                }
                if (!NotebookEbookStore.supported(name)) {
                    String mime = context.getContentResolver().getType(uri);
                    if ("application/epub+zip".equals(mime)) name = "Imported book.epub";
                    else if ("application/x-mobipocket-ebook".equals(mime)) name = "Imported book.mobi";
                    else if ("application/vnd.amazon.ebook".equals(mime)) name = "Imported book.azw3";
                    else if ("image/vnd.djvu".equals(mime) || "image/x-djvu".equals(mime)) name = "Imported book.djvu";
                    else if ("text/plain".equals(mime)) name = "Imported book.txt";
                    else if ("text/html".equals(mime)) name = "Imported book.html";
                }
                final long[] previous = {0};
                try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                    return new NotebookEbookStore(context.getFilesDir()).importBook(input, name, bytes -> {
                        long mib = bytes / 1048576;
                        if (mib > previous[0]) { previous[0] = mib; NotebookEbookTasks.MAIN.post(() -> { message = "Copying ebook… " + mib + " MiB"; changed(); }); }
                    });
                }
            }, (id, error) -> {
                busy = false; book = id;
                message = error == null ? "Opening book…" : "Couldn’t import this book. " + error.getMessage(); changed();
            });
        }
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().setWindowAnimations(0);
        Object retained = getLastNonConfigurationInstance(); job = retained instanceof Job ? (Job) retained : new Job(this);
        if (!(retained instanceof Job) && state != null) {
            job.picking = state.getBoolean("picking"); job.initialized = true;
            job.message = job.picking ? "Choose an ebook…" : "The previous import was interrupted. Check Books, or choose the original again.";
        }
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE); root.setPadding(28, 24, 28, 24);
        TextView title = new TextView(this); title.setText("Open ebook"); title.setTextColor(Color.BLACK); title.setTextSize(20); addScreenHeader(root, title);
        status = new TextView(this); status.setTextColor(Color.BLACK); status.setTextSize(16); root.addView(status, new LinearLayout.LayoutParams(-1, 0, 1));
        choose = new NotebookButton(this); choose.setText("Choose book"); choose.setOnClickListener(v -> pick()); root.addView(choose, new LinearLayout.LayoutParams(-1, 88));
        back = new NotebookButton(this); back.setText("Back to Books"); back.setOnClickListener(v -> finish()); root.addView(back, new LinearLayout.LayoutParams(-1, 88)); setContentView(root);
    }
    @Override protected void onResume() { super.onResume(); resumed = true; job.observer = this::render; render(); }
    @Override protected void onPostResume() { super.onPostResume(); status.post(advance); }
    @Override protected void onPause() { resumed = false; job.observer = null; status.removeCallbacks(advance); super.onPause(); }
    private void render() { status.setText(job.message); choose.setEnabled(!job.busy && !job.picking); back.setEnabled(!job.busy); status.removeCallbacks(advance); status.post(advance); }
    private void advance() {
        if (!resumed || isFinishing() || isDestroyed() || !status.isAttachedToWindow()) return;
        if (job.book != null && !job.opened) {
            job.opened = true; startActivity(new Intent(this, NotebookEbookActivity.class).putExtra("book", job.book).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)); finish();
        } else if (!job.initialized) {
            job.initialized = true; Intent incoming = getIntent();
            boolean external = Intent.ACTION_VIEW.equals(incoming.getAction()) || Intent.ACTION_SEND.equals(incoming.getAction());
            if (!external) pick();
            else try {
                Uri uri = Intent.ACTION_SEND.equals(incoming.getAction()) ? incoming.getParcelableExtra(Intent.EXTRA_STREAM) : incoming.getData();
                job.start(uri);
            } catch (RuntimeException error) { job.message = "Choose an ebook from your files."; render(); }
        }
    }
    private void pick() {
        if (!resumed || job.busy || job.picking) return;
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try { job.picking = true; startActivityForResult(pick, PICK); }
        catch (android.content.ActivityNotFoundException error) { job.picking = false; job.message = "No Android file picker is installed."; render(); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK) { job.picking = false; if (result == RESULT_OK && data != null && data.getData() != null) job.start(data.getData()); else finish(); }
    }
    @Override public Object onRetainNonConfigurationInstance() { return job; }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("picking", job.picking); super.onSaveInstanceState(state); }
    @Override public void onBackPressed() { if (!job.busy) super.onBackPressed(); }
}
