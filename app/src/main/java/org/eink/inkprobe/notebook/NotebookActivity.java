package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;

/** First notebook milestone: one durable page, with the native editor always handling ink. */
public final class NotebookActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private NotebookSession session;
    private NotebookInk ink;
    private FrameLayout canvas;
    private TextView saveState;
    private Button paperButton;
    private NotebookPage page;
    private IOException shownError;
    private boolean active, penDown, changingPaper, geometryPending;
    private boolean verifyOnReady;
    private int generation;
    private final Runnable updateSaveState = this::showSaveState;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        session = NotebookSession.get(this);
        verifyOnReady = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
            && getIntent().getBooleanExtra("verifyRestore", false);
        getIntent().removeExtra("verifyRestore");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(18), dp(8), dp(12), dp(8));
        TextView title = new TextView(this);
        title.setText("Notebook"); title.setTextColor(Color.BLACK); title.setTextSize(23);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        paperButton = new Button(this);
        paperButton.setText("Blank"); paperButton.setAllCaps(false); paperButton.setTextSize(15);
        paperButton.setEnabled(false); paperButton.setOnClickListener(v -> choosePaper());
        toolbar.addView(paperButton, new LinearLayout.LayoutParams(dp(106), dp(48)));
        Button info = new Button(this);
        info.setText("⋮"); info.setContentDescription("Page information"); info.setTextSize(24);
        info.setOnClickListener(v -> pageInformation());
        toolbar.addView(info, new LinearLayout.LayoutParams(dp(44), dp(48)));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout subline = new LinearLayout(this);
        subline.setPadding(dp(20), 0, dp(20), dp(9));
        TextView pageLabel = new TextView(this);
        pageLabel.setText("Page 1"); pageLabel.setTextSize(13); pageLabel.setTextColor(0xff555555);
        subline.addView(pageLabel, new LinearLayout.LayoutParams(0, -2, 1));
        saveState = new TextView(this);
        saveState.setText("Opening…"); saveState.setTextSize(13); saveState.setTextColor(0xff555555);
        subline.addView(saveState, new LinearLayout.LayoutParams(-2, -2));
        root.addView(subline, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(this); divider.setBackgroundColor(0xffbbbbbb);
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        canvas = new FrameLayout(this); canvas.setBackgroundColor(Color.WHITE);
        root.addView(canvas, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume(); active = true;
        loadPage();
    }

    private void loadPage() {
        final int request = ++generation;
        session.load((loaded, error) -> {
            if (!active || request != generation) return;
            page = loaded;
            if (loaded == null) { showError(error); return; }
            openCanvas(loaded);
            if (error != null) showError(error);
        });
    }

    private void openCanvas(NotebookPage loaded) {
        disposeCanvas();
        shownError = null;
        page = loaded; geometryPending = false;
        paperButton.setText(loaded.template == NotebookPage.Template.LINED ? "Lined" : "Blank");
        paperButton.setEnabled(session.error() == null);
        try {
            ink = new NotebookInk(this, loaded, new NotebookInk.Listener() {
                @Override public void onGeometryReady(int width, int height) {
                    if (geometryPending || !active) return;
                    geometryPending = true;
                    final int request = generation;
                    session.initializeSize(width, height, (sized, error) -> {
                        if (!active || request != generation) return;
                        if (error != null) { showError(error); return; }
                        page = sized;
                        if (ink != null) ink.setPage(sized);
                    });
                }
                @Override public void onStrokeComplete(NotebookPage.Stroke stroke) {
                    session.append(stroke, (updated, error) -> {
                        if (!active) return;
                        page = updated;
                        if (error != null) { showError(error); return; }
                        scheduleSaveState();
                    });
                }
                @Override public void onPenStateChanged(boolean down) {
                    penDown = down;
                    main.removeCallbacks(updateSaveState);
                    if (!down) scheduleSaveState();
                }
                @Override public void onReady() {
                    if (session.error() != null) { showError(session.error()); return; }
                    if (verifyOnReady) {
                        verifyOnReady = false;
                        try { NotebookVerification.write(NotebookActivity.this, page, ink.snapshotNativeStrokes()); }
                        catch (Exception ignored) { /* Explicit debug check does not interrupt writing. */ }
                    }
                    scheduleSaveState();
                }
                @Override public void onError(String message) {
                    showError(new IOException(message));
                }
            });
            canvas.addView(ink.view, new FrameLayout.LayoutParams(-1, -1));
            // Restore even a retained unsaved page. onReady stops input synchronously when
            // storage has failed, leaving the full open page visible while Retry is offered.
            ink.start();
        } catch (Exception error) { showError(new IOException("Could not open the writing surface", error)); }
    }

    private void scheduleSaveState() {
        main.removeCallbacks(updateSaveState);
        if (active && !penDown) main.postDelayed(updateSaveState, 1000);
    }
    private void showSaveState() {
        if (!active || penDown) return;
        NotebookPage current = session.current();
        int strokes = current == null ? 0 : current.strokes.size();
        String text = shownError != null || session.error() != null ? "Not saved · tap here"
            : session.isSaving() ? "Saving…"
            : strokes == 0 ? "Autosave on"
            : "Saved · " + strokes + (strokes == 1 ? " stroke" : " strokes");
        if (!text.contentEquals(saveState.getText())) saveState.setText(text);
        if (session.isSaving()) main.postDelayed(updateSaveState, 1000);
    }
    private void showError(IOException error) {
        shownError = error;
        if (ink != null) ink.stop();
        paperButton.setEnabled(false);
        main.removeCallbacks(updateSaveState);
        saveState.setText("Not saved · tap here");
        saveState.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Couldn’t save the page")
            .setMessage("Writing is paused. Your open page is retained in memory.\n\n"
                + (error == null ? "Please retry." : error.getMessage()))
            .setNegativeButton("Keep open", null)
            .setPositiveButton("Retry", (dialog, which) -> retrySaving()).show());
    }
    private void retrySaving() {
        final int request = ++generation;
        session.retry((restored, error) -> {
            if (!active || request != generation) return;
            if (error != null) { showError(error); return; }
            saveState.setOnClickListener(null);
            openCanvas(restored); scheduleSaveState();
        });
    }

    private void choosePaper() {
        if (page == null || changingPaper) return;
        if (ink != null) ink.stop();
        AlertDialog choice = new AlertDialog.Builder(this)
            .setTitle("Paper")
            .setSingleChoiceItems(new String[]{"Blank", "Lined"},
                page.template == NotebookPage.Template.LINED ? 1 : 0, (dialog, which) -> {
                    NotebookPage.Template selected = which == 1 ? NotebookPage.Template.LINED : NotebookPage.Template.BLANK;
                    changingPaper = true; paperButton.setEnabled(false);
                    saveState.setText("Saving…");
                    dialog.dismiss();
                    final int request = ++generation;
                    session.setTemplate(selected, (updated, error) -> {
                        changingPaper = false;
                        if (!active || request != generation) return;
                        page = updated;
                        if (error != null) { showError(error); return; }
                        openCanvas(updated); scheduleSaveState();
                    });
                }).setNegativeButton("Cancel", null).create();
        choice.setOnDismissListener(dialog -> {
            if (!changingPaper && active) loadPage();
        });
        choice.show();
    }

    private void pageInformation() {
        if (ink != null) ink.stop();
        NotebookPage current = session.current();
        String message = current == null ? "Your page is opening." :
            "Page 1 · " + (current.template == NotebookPage.Template.LINED ? "Lined" : "Blank")
            + "\n" + current.strokes.size() + " strokes\n"
            + (session.error() != null ? "Some writing has not been saved."
                : session.isSaving() ? "Saving your latest writing…" : "Your completed strokes are saved on this tablet.");
        AlertDialog info = new AlertDialog.Builder(this).setTitle("This page")
            .setMessage(message).setPositiveButton("Done", null).create();
        info.setOnDismissListener(dialog -> { if (active) loadPage(); });
        info.show();
    }

    @Override protected void onPause() {
        active = false; ++generation;
        main.removeCallbacks(updateSaveState);
        disposeCanvas();
        super.onPause();
    }
    private void disposeCanvas() {
        penDown = false;
        if (ink != null) { ink.release(); ink = null; }
        if (canvas != null) canvas.removeAllViews();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
