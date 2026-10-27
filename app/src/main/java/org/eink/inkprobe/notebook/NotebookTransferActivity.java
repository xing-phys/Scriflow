package org.eink.inkprobe.notebook;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** System document picker access; no broad storage permission and no interaction with live ink. */
public final class NotebookTransferActivity extends NotebookDisplayActivity {
    private static final int PICK = 2401;
    private NotebookTransfer job;
    private TextView status;
    private NotebookButton choose, done;
    private boolean awaiting;
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setWindowAnimations(0);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE); root.setPadding(32, 24, 32, 24);
        boolean restoring = getIntent().getBooleanExtra("restore", false);
        TextView title = new TextView(this); title.setTextColor(Color.BLACK); title.setTextSize(17);
        title.setText(restoring ? "Restore notebook" : "Editable backup"); addScreenHeader(root, title);
        status = new TextView(this); status.setTextColor(Color.BLACK); status.setTextSize(14);
        root.addView(status, new LinearLayout.LayoutParams(-1, 0, 1));
        choose = new NotebookButton(this); choose.setText(restoring ? "Choose backup" : "Save backup");
        choose.setOnClickListener(v -> pick()); root.addView(choose, new LinearLayout.LayoutParams(-1, 64));
        done = new NotebookButton(this); done.setText("Back to library"); done.setOnClickListener(v -> onBackPressed());
        root.addView(done, new LinearLayout.LayoutParams(-1, 64)); setContentView(root);
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof NotebookTransfer) job = (NotebookTransfer) retained;
        else {
            job = new NotebookTransfer(this, restoring);
            if (saved == null && !restoring) job.prepare(getIntent().getIntExtra("notebookId", 0));
            else job.message = restoring ? "Choose an .inkbook backup. It will be restored as a separate notebook."
                : "Backup preparation was interrupted. Return to the library and start again.";
        }
        awaiting = saved != null && saved.getBoolean("awaiting", false);
    }
    @Override protected void onResume() {
        super.onResume(); job.observe(this::render);
    }
    @Override protected void onPause() { job.detach(); super.onPause(); }
    private void render() {
        status.setText(job.message == null ? "Preparing…" : job.message);
        choose.setVisibility(!job.complete && !job.busy && (job.restoring || job.prepared != null) ? View.VISIBLE : View.GONE);
        done.setVisibility(job.busy ? View.INVISIBLE : View.VISIBLE);
    }
    private void pick() {
        if (job.busy || job.complete || awaiting) return;
        Intent intent = new Intent(job.restoring ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType(job.restoring ? "*/*" : "application/zip");
        if (!job.restoring) intent.putExtra(Intent.EXTRA_TITLE, "notebook.inkbook");
        try { awaiting = true; startActivityForResult(intent, PICK); }
        catch (android.content.ActivityNotFoundException error) { awaiting = false; status.setText("No system file picker is available."); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK) return;
        awaiting = false;
        Uri uri = data == null ? null : data.getData();
        if (result == RESULT_OK && uri != null) { if (job.restoring) job.restore(uri); else job.save(uri); }
    }
    @Override public Object onRetainNonConfigurationInstance() { return job; }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("awaiting", awaiting); super.onSaveInstanceState(state);
    }
    @Override public void onBackPressed() {
        if (job.busy) return;
        job.cancel(); super.onBackPressed(); overridePendingTransition(0, 0);
    }
}
