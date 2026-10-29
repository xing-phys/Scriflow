package org.eink.inkprobe.notebook;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Image transfers own their job across rotation; file providers never run on the UI/writer thread. */
public final class NotebookMediaActivity extends NotebookDisplayActivity {
    private static final int PICK = 2510;
    private static final String[] FORMATS = {"SVG", "PNG", "JPEG", "WebP", "PDF"};
    private static final String[] EXTENSIONS = {"svg", "png", "jpg", "webp", "pdf"};
    private static final String[] TYPES = {"image/svg+xml", "image/png", "image/jpeg", "image/webp", "application/pdf"};
    private Job job;
    private TextView status;
    private LinearLayout actions;
    private LinearLayout root;
    private boolean awaiting, resumed;
    private final Runnable nextStep = this::advance;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setWindowAnimations(0);
        Object retained = getLastNonConfigurationInstance();
        job = retained instanceof Job ? (Job) retained : new Job(this, getIntent().getBooleanExtra("import", false));
        if (retained == null) {
            job.book = getIntent().getIntExtra("notebookId", 0);
            job.number = getIntent().getIntExtra("page", 1);
            job.pageId = getIntent().getStringExtra("pageId");
            job.source = getIntent().getStringExtra("source");
            if (saved != null) {
                job.format = saved.getInt("format", 1);
                job.prepared = checkedFile(saved.getString("prepared"));
                job.pickerStarted = saved.getBoolean("pickerStarted", saved.getBoolean("awaiting"));
                job.complete = saved.getBoolean("complete"); job.failed = saved.getBoolean("failed");
                String message = saved.getString("message"); if (message != null) job.message = message;
                if (saved.getBoolean("busy")) {
                    job.failed = true; job.message = "Transfer interrupted. Choose the image or prepare the export again.";
                }
            }
        }
        awaiting = saved != null && saved.getBoolean("awaiting");
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE); root.setPadding(24, 20, 24, 20);
        TextView title = new TextView(this); title.setText(job.importing ? "Insert figure" : "Export selected ink");
        title.setTextColor(Color.BLACK); title.setTextSize(20); addScreenHeader(root, title);
        status = new TextView(this); status.setTextColor(Color.BLACK); status.setTextSize(16);
        root.addView(status, new LinearLayout.LayoutParams(-1, 0, 1));
        actions = new LinearLayout(this); actions.setOrientation(LinearLayout.VERTICAL); root.addView(actions);
        setContentView(root);
    }
    private File checkedFile(String name) {
        if (name == null) return null;
        try {
            File file = new File(name).getCanonicalFile();
            return getCacheDir().getCanonicalFile().equals(file.getParentFile()) && file.getName().startsWith("selection-export-")
                && file.isFile() ? file : null;
        } catch (IOException error) { return null; }
    }
    @Override protected void onResume() { super.onResume(); resumed = true; job.listener = this::render; render(); }
    @Override protected void onPostResume() { super.onPostResume(); root.post(nextStep); }
    @Override protected void onPause() {
        resumed = false; root.removeCallbacks(nextStep); job.listener = null; super.onPause();
    }
    private void render() {
        status.setText(job.message); actions.removeAllViews();
        if (job.importing) {
            if (job.failed && !job.busy) {
                add("Choose another image…", () -> pick(true)); add("Back to note", this::onBackPressed);
            }
            root.removeCallbacks(nextStep); root.post(nextStep); return;
        }
        if (job.busy) return;
        if (!job.complete) {
            if (job.importing) add("Choose image…", () -> pick(true));
            else {
                for (int i = 0; i < FORMATS.length; i++) {
                    final int format = i;
                    add("Prepare " + FORMATS[i], () -> job.prepare(format));
                }
                if (job.prepared != null) add("Save " + FORMATS[job.format] + "…", () -> pick(false));
            }
        }
        add("Back to note", this::onBackPressed);
    }
    private void advance() {
        if (!resumed || isFinishing() || isDestroyed() || !root.isAttachedToWindow() || !job.importing) return;
        if (job.complete) { finish(); overridePendingTransition(0, 0); }
        else if (!job.busy && !awaiting && !job.pickerStarted && !job.failed) pick(true);
    }
    private void add(String label, Runnable action) {
        NotebookButton button = new NotebookButton(this); button.setText(label);
        button.setOnClickListener(v -> action.run()); actions.addView(button, new LinearLayout.LayoutParams(-1, Math.round(48 * getResources().getDisplayMetrics().density)));
    }
    private void pick(boolean importing) {
        if (awaiting) return;
        Intent intent = new Intent(importing ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType(importing ? "*/*" : TYPES[job.format]);
        if (!importing) intent.putExtra(Intent.EXTRA_TITLE, "selected-ink." + EXTENSIONS[job.format]);
        try {
            awaiting = true; if (importing) { job.pickerStarted = true; job.failed = false; }
            startActivityForResult(intent, PICK);
        } catch (android.content.ActivityNotFoundException error) {
            awaiting = false; job.failed = true; job.message = "No system file picker is available."; render();
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK) return;
        awaiting = false;
        if (result == RESULT_OK && data != null && data.getData() != null) {
            if (job.importing) job.insert(data.getData()); else job.save(data.getData());
        } else if (job.importing) { finish(); overridePendingTransition(0, 0); }
    }
    @Override public Object onRetainNonConfigurationInstance() { return job; }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("awaiting", awaiting); state.putBoolean("busy", job.busy); state.putInt("format", job.format);
        state.putBoolean("pickerStarted", job.pickerStarted); state.putBoolean("complete", job.complete);
        state.putBoolean("failed", job.failed); state.putString("message", job.message);
        if (job.prepared != null) state.putString("prepared", job.prepared.getPath());
        super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() {
        if (job.busy) return;
        if (job.prepared != null) job.prepared.delete();
        job.cleanupSource(); super.onBackPressed();
    }
    private static final class Job {
        private static final ExecutorService files = Executors.newSingleThreadExecutor();
        final android.content.Context context;
        final boolean importing;
        final Handler main = new Handler(Looper.getMainLooper());
        Runnable listener;
        boolean busy, complete, pickerStarted, failed;
        int book, number, format = 1;
        String pageId, source;
        String message;
        File prepared;
        Job(android.content.Context context, boolean importing) {
            this.context = context.getApplicationContext(); this.importing = importing;
            message = importing ? "Opening image picker…"
                : "Choose an export format. SVG and PDF keep vector handwriting; PNG and WebP have a transparent background, JPEG has white paper.";
        }
        void changed() { if (listener != null) listener.run(); }
        void fail(Exception error) { busy = false; failed = true; message = error.getMessage() == null ? "Transfer failed. Please retry." : error.getMessage(); changed(); }
        void insert(Uri uri) {
            busy = true; failed = false; message = "Reading image…"; changed();
            files.execute(() -> {
                try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                    NotebookImages.Imported image = NotebookImages.read(input);
                    NotebookSession session = NotebookSession.get(context);
                    session.insertFigure(book, number, pageId, image, (page, error) -> {
                        busy = false;
                        if (error != null) { fail(error); return; }
                        complete = true; message = "Figure inserted."; changed();
                    });
                } catch (Exception error) { main.post(() -> fail(error)); }
            });
        }
        File sourceDirectory() throws IOException {
            if (source == null) throw new IOException("Select the ink again to export");
            File directory = new File(source).getCanonicalFile();
            if (!context.getCacheDir().getCanonicalFile().equals(directory.getParentFile())
                    || !directory.getName().startsWith("selection-source-")) throw new IOException("Invalid selection source");
            return directory;
        }
        void cleanupSource() {
            try { File directory = sourceDirectory(); new File(directory, "page.nbk").delete(); directory.delete(); }
            catch (IOException ignored) { }
        }
        void prepare(int selectedFormat) {
            busy = true; message = "Preparing " + FORMATS[selectedFormat] + "…"; changed();
            files.execute(() -> {
                File destination = null;
                try {
                    NotebookPage page = new NotebookStore(sourceDirectory()).load();
                    NotebookSelectionExport geometry = new NotebookSelectionExport(page.strokes, NotebookStrokeRenderer::outline);
                    destination = File.createTempFile("selection-export-", "." + EXTENSIONS[selectedFormat], context.getCacheDir());
                    try (FileOutputStream out = new FileOutputStream(destination)) {
                        if (selectedFormat == 0) geometry.writeSvg(out);
                        else renderExport(geometry, selectedFormat, out);
                        out.getFD().sync();
                    }
                    final File result = destination;
                    main.post(() -> {
                        if (prepared != null) prepared.delete();
                        prepared = result; format = selectedFormat; busy = false;
                        message = FORMATS[format] + " ready. Choose Save to select a destination."; changed();
                    });
                } catch (Exception error) { if (destination != null) destination.delete(); main.post(() -> fail(error)); }
            });
        }
        void save(Uri uri) {
            if (prepared == null) return;
            busy = true; message = "Saving…"; changed(); final File file = prepared;
            files.execute(() -> {
                try (InputStream input = new FileInputStream(file);
                     OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) throw new IOException("Cannot open the selected destination");
                    byte[] buffer = new byte[65536]; int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    output.flush();
                } catch (Exception error) {
                    main.post(() -> fail(new IOException("Save failed. An incomplete file may remain. Choose a new destination to retry. " + error.getMessage()))); return;
                }
                main.post(() -> { busy = false; complete = true; file.delete(); prepared = null; message = "Selected ink saved."; changed(); });
            });
        }
    }
    private static void renderExport(NotebookSelectionExport geometry, int format, OutputStream output) throws IOException {
        if (format == 4) {
            PdfDocument pdf = new PdfDocument();
            try {
                float scale = Math.min(1, 14400f / Math.max(geometry.width, geometry.height));
                PdfDocument.Page page = pdf.startPage(new PdfDocument.PageInfo.Builder(
                    Math.max(1, (int) Math.ceil(geometry.width * scale)), Math.max(1, (int) Math.ceil(geometry.height * scale)), 1).create());
                page.getCanvas().scale(scale, scale); drawGeometry(page.getCanvas(), geometry);
                pdf.finishPage(page); pdf.writeTo(output);
            } finally { pdf.close(); }
        } else {
            float scale = Math.min(2, 2048f / Math.max(geometry.width, geometry.height));
            Bitmap bitmap = Bitmap.createBitmap(Math.max(1, (int) Math.ceil(geometry.width * scale)),
                Math.max(1, (int) Math.ceil(geometry.height * scale)), Bitmap.Config.ARGB_8888);
            try {
                Canvas canvas = new Canvas(bitmap); if (format == 2) canvas.drawColor(Color.WHITE);
                canvas.scale(scale, scale); drawGeometry(canvas, geometry);
                Bitmap.CompressFormat type = format == 2 ? Bitmap.CompressFormat.JPEG
                    : format == 3 ? Bitmap.CompressFormat.WEBP : Bitmap.CompressFormat.PNG;
                if (!bitmap.compress(type, format == 2 ? 95 : 100, output)) throw new IOException("Cannot encode the image");
            } finally { bitmap.recycle(); }
        }
    }
    private static void drawGeometry(Canvas canvas, NotebookSelectionExport geometry) throws IOException {
        canvas.translate(-geometry.left, -geometry.top); Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (NotebookSelectionExport.Shape shape : geometry.shapes) {
            Path path = new Path();
            path.setFillType(shape.outline.evenOdd ? Path.FillType.EVEN_ODD : Path.FillType.WINDING);
            shape.outline.writeTo(new NotebookStrokeOutline.Sink() {
                public void moveTo(float x, float y) { path.moveTo(x, y); }
                public void lineTo(float x, float y) { path.lineTo(x, y); }
                public void close() { path.close(); }
            });
            paint.setColor(shape.color); canvas.drawPath(path, paint);
        }
    }
}
