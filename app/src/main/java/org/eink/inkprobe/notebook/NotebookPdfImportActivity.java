package org.eink.inkprobe.notebook;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Storage Access Framework import; the picker opens directly and the imported note opens on success. */
public final class NotebookPdfImportActivity extends NotebookDisplayActivity {
    private static final int PICK_PDF = 2402;
    private NotebookPdfImport job;
    private LinearLayout root;
    private TextView status;
    private NotebookButton choose, done;
    private boolean resumed;
    private final Runnable nextStep = this::advance;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setWindowAnimations(0);
        Object retained = getLastNonConfigurationInstance();
        job = retained instanceof NotebookPdfImport ? (NotebookPdfImport) retained : new NotebookPdfImport(this);
        if (!(retained instanceof NotebookPdfImport) && saved != null) {
            String phase = saved.getString("importPhase");
            // Preserve an already launched system picker when updating from the earlier controller.
            if (phase == null && saved.getBoolean("awaiting", false)) phase = "PICKING";
            if (phase == null && saved.getBoolean("busy", false)) phase = "IMPORTING";
            job.flow.restore(phase, saved.getInt("importedNotebookId", 0));
            String message = saved.getString("importMessage");
            if (message != null) job.message = message;
            if ("IMPORTING".equals(phase))
                job.message = "Import was interrupted. Check the library for the imported notebook, or choose the PDF again.";
        }
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE); root.setPadding(32, 24, 32, 24);
        TextView title = new TextView(this); title.setTextColor(Color.BLACK); title.setTextSize(17);
        title.setText("Importing PDF"); addScreenHeader(root, title);
        status = new TextView(this); status.setTextColor(Color.BLACK); status.setTextSize(14);
        root.addView(status, new LinearLayout.LayoutParams(-1, 0, 1));
        choose = new NotebookButton(this); choose.setText("Choose another PDF"); choose.setOnClickListener(v -> pick());
        choose.setVisibility(View.GONE); root.addView(choose, new LinearLayout.LayoutParams(-1, 64));
        done = new NotebookButton(this); done.setText("Back to library"); done.setOnClickListener(v -> onBackPressed());
        done.setVisibility(View.GONE); root.addView(done, new LinearLayout.LayoutParams(-1, 64));
        setContentView(root);
    }
    @Override protected void onResume() { super.onResume(); resumed = true; job.observe(this::render); }
    @Override protected void onPostResume() { super.onPostResume(); root.post(nextStep); }
    @Override protected void onPause() {
        resumed = false; root.removeCallbacks(nextStep); job.detach(); super.onPause();
    }
    private void render() {
        status.setText(job.message);
        boolean failed = job.flow.phase() == NotebookPdfImportFlow.Phase.ERROR;
        choose.setVisibility(failed ? View.VISIBLE : View.GONE);
        done.setVisibility(failed ? View.VISIBLE : View.GONE);
        root.removeCallbacks(nextStep); root.post(nextStep);
    }
    /** Deferred until the resumed, attached instance owns a valid window, including landscape recreation. */
    private void advance() {
        if (!resumed || isFinishing() || isDestroyed() || !root.isAttachedToWindow()) return;
        NotebookPdfImportFlow.Phase phase = job.flow.phase();
        if (phase == NotebookPdfImportFlow.Phase.CHOOSE) pick();
        else if (phase == NotebookPdfImportFlow.Phase.READY && job.flow.beginOpen()) {
            startActivity(new Intent(this, NotebookActivity.class));
            finish(); overridePendingTransition(0, 0);
        } else if (phase == NotebookPdfImportFlow.Phase.OPENED || phase == NotebookPdfImportFlow.Phase.CANCELLED) finish();
    }
    private void pick() {
        if (!resumed || isFinishing() || isDestroyed() || !root.isAttachedToWindow() || !job.flow.beginPicker()) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf");
        try { startActivityForResult(intent, PICK_PDF); }
        catch (android.content.ActivityNotFoundException error) {
            job.flow.failed(); job.message = "No system file picker is available."; render();
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_PDF || job.flow.phase() != NotebookPdfImportFlow.Phase.PICKING) return;
        Uri uri = data == null ? null : data.getData();
        if (result == RESULT_OK && uri != null) job.importPdf(uri);
        else { job.flow.cancelPicker(); finish(); overridePendingTransition(0, 0); }
    }
    @Override public Object onRetainNonConfigurationInstance() { return job; }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("importPhase", job.flow.phase().name());
        state.putInt("importedNotebookId", job.flow.notebookId());
        state.putString("importMessage", job.message); super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() {
        if (job.flow.busy()) return;
        super.onBackPressed(); overridePendingTransition(0, 0);
    }
}
