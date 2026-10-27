package org.eink.inkprobe.notebook;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Storage Access Framework import, with no broad storage permission. */
public final class NotebookPdfImportActivity extends NotebookDisplayActivity {
    private static final int PICK_PDF = 2402;
    private NotebookPdfImport job;
    private TextView status;
    private NotebookButton choose, done;
    private boolean awaiting;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setWindowAnimations(0);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE); root.setPadding(32, 24, 32, 24);
        TextView title = new TextView(this); title.setTextColor(Color.BLACK); title.setTextSize(17);
        title.setText("Import PDF as notebook"); addScreenHeader(root, title);
        status = new TextView(this); status.setTextColor(Color.BLACK); status.setTextSize(14);
        root.addView(status, new LinearLayout.LayoutParams(-1, 0, 1));
        choose = new NotebookButton(this); choose.setText("Choose PDF"); choose.setOnClickListener(v -> pick());
        root.addView(choose, new LinearLayout.LayoutParams(-1, 64));
        done = new NotebookButton(this); done.setText("Back to library"); done.setOnClickListener(v -> onBackPressed());
        root.addView(done, new LinearLayout.LayoutParams(-1, 64)); setContentView(root);
        Object retained = getLastNonConfigurationInstance();
        job = retained instanceof NotebookPdfImport ? (NotebookPdfImport) retained : new NotebookPdfImport(this);
        awaiting = saved != null && saved.getBoolean("awaiting", false);
        if (retained == null && saved != null && saved.getBoolean("busy", false))
            job.message = "Import was interrupted. Return to the library to check whether it finished, or choose the PDF again.";
    }
    @Override protected void onResume() { super.onResume(); job.observe(this::render); }
    @Override protected void onPause() { job.detach(); super.onPause(); }
    private void render() {
        status.setText(job.message);
        choose.setVisibility(job.busy || job.complete ? View.GONE : View.VISIBLE);
        done.setVisibility(job.busy ? View.INVISIBLE : View.VISIBLE);
    }
    private void pick() {
        if (awaiting || job.busy || job.complete) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/pdf");
        try { awaiting = true; startActivityForResult(intent, PICK_PDF); }
        catch (android.content.ActivityNotFoundException error) { awaiting = false; status.setText("No system file picker is available."); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_PDF) return;
        awaiting = false;
        Uri uri = data == null ? null : data.getData();
        if (result == RESULT_OK && uri != null) job.importPdf(uri);
    }
    @Override public Object onRetainNonConfigurationInstance() { return job; }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("awaiting", awaiting); state.putBoolean("busy", job.busy); super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() {
        if (job.busy) return;
        super.onBackPressed(); overridePendingTransition(0, 0);
    }
}
