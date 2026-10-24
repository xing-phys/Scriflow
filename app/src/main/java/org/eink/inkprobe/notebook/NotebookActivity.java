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
import java.util.List;

/** Durable notebook pages, native pen ink, and document-level editing. */
public final class NotebookActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private NotebookSession session;
    private NotebookInk ink;
    private FrameLayout canvas;
    private TextView saveState, pageLabel;
    private NotebookButton paperButton, penButton, eraserButton, undoButton, redoButton;
    private NotebookButton previousButton, nextButton, addButton;
    private NotebookPage page;
    private IOException shownError;
    private boolean active, penDown, geometryPending, editing;
    private NotebookInk.Tool tool = NotebookInk.Tool.PEN;
    private boolean verifyOnReady;
    private int generation;
    private final Runnable updateSaveState = this::showSaveState;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        session = NotebookSession.get(this);
        if (saved != null && saved.getBoolean("eraser", false)) tool = NotebookInk.Tool.ERASER;
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
        title.setText("Ink"); title.setTextColor(Color.BLACK); title.setTextSize(23);
        title.setSingleLine(true);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        previousButton = toolButton(toolbar, "‹", 44, () -> switchPage(-1));
        previousButton.setTextSize(26); previousButton.setContentDescription("Previous page");
        nextButton = toolButton(toolbar, "›", 44, () -> switchPage(1));
        nextButton.setTextSize(26); nextButton.setContentDescription("Next page");
        addButton = toolButton(toolbar, "+ Page", 76, this::addPage);
        penButton = toolButton(toolbar, "Pen", 64, () -> selectTool(NotebookInk.Tool.PEN));
        eraserButton = toolButton(toolbar, "Eraser", 76, () -> selectTool(NotebookInk.Tool.ERASER));
        undoButton = toolButton(toolbar, "Undo", 64, () -> changeHistory(false));
        redoButton = toolButton(toolbar, "Redo", 64, () -> changeHistory(true));
        paperButton = new NotebookButton(this);
        paperButton.setText("Blank"); paperButton.setAllCaps(false); paperButton.setTextSize(15);
        paperButton.setAvailable(false); paperButton.setOnClickListener(v -> choosePaper());
        toolbar.addView(paperButton, new LinearLayout.LayoutParams(dp(106), dp(48)));
        NotebookButton info = new NotebookButton(this);
        info.setText("⋮"); info.setContentDescription("Page information"); info.setTextSize(24);
        info.setOnClickListener(v -> pageInformation());
        toolbar.addView(info, new LinearLayout.LayoutParams(dp(44), dp(48)));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout subline = new LinearLayout(this);
        subline.setPadding(dp(20), 0, dp(20), dp(9));
        pageLabel = new TextView(this);
        pageLabel.setText("Page 1 of 1"); pageLabel.setTextSize(13); pageLabel.setTextColor(0xff555555);
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
        editing = false;
        updateControls();
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
        final int request = ++generation;
        disposeCanvas();
        shownError = null;
        saveState.setOnClickListener(null);
        editing = false;
        page = loaded; geometryPending = false;
        paperButton.setText(loaded.template == NotebookPage.Template.LINED ? "Lined" : "Blank");
        updatePageLabel();
        updateControls();
        try {
            ink = new NotebookInk(this, loaded, new NotebookInk.Listener() {
                @Override public void onGeometryReady(int width, int height) {
                    if (geometryPending || !active || request != generation) return;
                    geometryPending = true;
                    session.initializeSize(width, height, (sized, error) -> {
                        if (!active || request != generation) return;
                        if (error != null) { showError(error); return; }
                        page = sized;
                        if (ink != null) ink.setPage(sized);
                    });
                }
                @Override public void onStrokeComplete(NotebookPage.Stroke stroke) {
                    session.append(stroke, (updated, error) -> {
                        if (!active || request != generation) return;
                        page = updated;
                        if (error != null) { showError(error); return; }
                        scheduleSaveState();
                    });
                }
                @Override public void onPenStateChanged(boolean down) {
                    if (!active || request != generation) return;
                    penDown = down;
                    main.removeCallbacks(updateSaveState);
                    if (!down) scheduleSaveState();
                }
                @Override public void onReady() {
                    if (!active || request != generation) return;
                    if (session.error() != null) { showError(session.error()); return; }
                    updateControls();
                    if (verifyOnReady) {
                        verifyOnReady = false;
                        try { NotebookVerification.write(NotebookActivity.this, page, ink.snapshotNativeStrokes()); }
                        catch (Exception ignored) { /* Explicit debug check does not interrupt writing. */ }
                    }
                    scheduleSaveState();
                }
                @Override public void onError(String message) {
                    if (!active || request != generation) return;
                    showError(new IOException(message));
                }
                @Override public void onEraseGesture(List<NotebookPage.Point> path, float radius) {
                    if (active && request == generation) erase(path, radius);
                }
            });
            ink.setTool(tool);
            canvas.addView(ink.view, new FrameLayout.LayoutParams(-1, -1));
            // Restore even a retained unsaved page. onReady stops input synchronously when
            // storage has failed, leaving the full open page visible while Retry is offered.
            ink.start();
        } catch (Exception error) { showError(new IOException("Could not open the writing surface", error)); }
    }

    private NotebookButton toolButton(LinearLayout toolbar, String label, int width, Runnable action) {
        NotebookButton button = new NotebookButton(this);
        button.setText(label); button.setAllCaps(false); button.setTextSize(14);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setAvailable(false); button.setOnClickListener(v -> action.run());
        toolbar.addView(button, new LinearLayout.LayoutParams(dp(width), dp(48)));
        return button;
    }

    private boolean canEdit() {
        return active && !editing && shownError == null && session.error() == null
            && page != null && ink != null && ink.isReady() && !ink.isPenDown();
    }

    private void updateControls() {
        // Availability is logical, with no animated or temporary disabled appearance.
        // canEdit() gates every action while the surface or writer is busy.
        boolean ready = active && shownError == null && session.error() == null
            && page != null && page.isInitialized();
        paperButton.setAvailable(ready);
        penButton.setAvailable(ready); eraserButton.setAvailable(ready);
        undoButton.setAvailable(ready && session.canUndo());
        redoButton.setAvailable(ready && session.canRedo());
        previousButton.setAvailable(ready && session.pageNumber() > 1);
        nextButton.setAvailable(ready && session.pageNumber() < session.pageCount());
        addButton.setAvailable(ready && session.pageCount() < NotebookIndex.MAX_PAGES);
        String pen = tool == NotebookInk.Tool.PEN ? "✓ Pen" : "Pen";
        String eraser = tool == NotebookInk.Tool.ERASER ? "✓ Eraser" : "Eraser";
        if (!pen.contentEquals(penButton.getText())) penButton.setText(pen);
        if (!eraser.contentEquals(eraserButton.getText())) eraserButton.setText(eraser);
    }

    private void selectTool(NotebookInk.Tool selected) {
        if (!canEdit() || tool == selected) return;
        tool = selected;
        ink.setTool(selected);
        updateControls();
    }

    private void changeHistory(boolean redo) {
        if (!canEdit() || !(redo ? session.canRedo() : session.canUndo())) return;
        pauseForEdit();
        if (shownError != null) return;
        final int request = generation;
        NotebookSession.Callback completion = (updated, error) -> finishEdit(request, updated, error);
        if (redo) session.redo(completion); else session.undo(completion);
    }

    private void erase(List<NotebookPage.Point> path, float radius) {
        if (!canEdit()) return;
        pauseForEdit();
        if (shownError != null) return;
        final int request = generation;
        session.erase(path, radius, (updated, error) -> finishEdit(request, updated, error));
    }

    private void pauseForEdit() {
        editing = true;
        ink.pauseForEdit();
        main.removeCallbacks(updateSaveState);
        updateControls();
    }

    private void finishEdit(int request, NotebookPage updated, IOException error) {
        if (!active || request != generation) return;
        // Keep the surface and paper visible. The adapter posts the replacement tracks in
        // one frame, or skips drawing entirely for a no-hit erase.
        if (updated == null || ink == null) {
            showError(error == null ? new IOException("The writing surface is unavailable.") : error);
            return;
        }
        page = updated;
        updatePageLabel();
        String paper = updated.template == NotebookPage.Template.LINED ? "Lined" : "Blank";
        if (!paper.contentEquals(paperButton.getText())) paperButton.setText(paper);
        try {
            // A rare unfinished vendor track or changed surface size still needs a fresh
            // widget; ordinary completed edits reuse the visible surface.
            if (!ink.applyEditPage(updated) && shownError == null) openCanvas(updated);
        }
        catch (Exception failure) {
            showError(new IOException("Could not update the writing surface", failure));
            return;
        }
        editing = false;
        if (error != null) { showError(error); return; }
        if (shownError != null) return;
        ink.start();
        updateControls();
        scheduleSaveState();
    }

    private void updatePageLabel() {
        String text = "Page " + session.pageNumber() + " of " + session.pageCount();
        if (!text.contentEquals(pageLabel.getText())) pageLabel.setText(text);
    }

    private void switchPage(int offset) {
        int target = session.pageNumber() + offset;
        if (!canEdit() || target < 1 || target > session.pageCount()) return;
        pauseForEdit();
        if (shownError != null) return;
        final int request = generation;
        session.selectPage(target, (updated, error) -> finishEdit(request, updated, error));
    }

    private void addPage() {
        if (!canEdit() || session.pageCount() >= NotebookIndex.MAX_PAGES) return;
        pauseForEdit();
        if (shownError != null) return;
        final int request = generation;
        session.addPage((updated, error) -> finishEdit(request, updated, error));
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
        updateControls();
        if (session.isSaving()) main.postDelayed(updateSaveState, 1000);
    }
    private void showError(IOException error) {
        shownError = error;
        if (ink != null) ink.stop();
        updateControls();
        main.removeCallbacks(updateSaveState);
        saveState.setText("Not saved · tap here");
        saveState.setOnClickListener(v -> {
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Couldn’t save the notebook")
                .setMessage("Writing is paused. Your open pages are retained in memory.\n\n"
                    + (error == null ? "Please retry." : error.getMessage()))
                .setNegativeButton("Keep open", null)
                .setPositiveButton("Retry", (choice, which) -> retrySaving()).create();
            showStaticDialog(dialog);
        });
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
        if (!canEdit()) return;
        NotebookPage.Template selected = page.template == NotebookPage.Template.LINED
            ? NotebookPage.Template.BLANK : NotebookPage.Template.LINED;
        pauseForEdit();
        if (shownError != null) return;
        final int request = generation;
        session.setTemplate(selected, (updated, error) -> finishEdit(request, updated, error));
    }

    private void pageInformation() {
        if (!canEdit()) return;
        pauseForEdit();
        if (shownError != null) return;
        final int request = generation;
        NotebookPage current = session.current();
        String message = current == null ? "Your page is opening." :
            "Page " + session.pageNumber() + " of " + session.pageCount() + " · "
            + (current.template == NotebookPage.Template.LINED ? "Lined" : "Blank")
            + "\n" + current.strokes.size() + " strokes\n"
            + (session.error() != null ? "Some writing has not been saved."
                : session.isSaving() ? "Saving your latest writing…" : "Your completed strokes are saved on this tablet.")
            + "\n\nUse ‹ and › to change pages. + Page adds an empty page at the end using this page’s paper. Tap Blank/Lined to change this page’s paper."
            + "\n\nEraser removes whole strokes after you lift the pen. Each page remembers up to 100 Undo/Redo edits while the app process stays open."
            + "\n\nNew writing or a successful erase clears Redo. Changing paper keeps your stroke history.";
        AlertDialog info = new AlertDialog.Builder(this).setTitle("This page")
            .setMessage(message).setPositiveButton("Done", null).create();
        info.setOnDismissListener(dialog -> {
            if (active && request == generation)
                session.load((loaded, error) -> finishEdit(request, loaded, error));
        });
        showStaticDialog(info);
    }

    private void showStaticDialog(AlertDialog dialog) {
        dialog.create(); // Apply theme defaults before overriding their dim/fade attributes.
        if (dialog.getWindow() != null) {
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0f);
            dialog.getWindow().setWindowAnimations(0);
        }
        dialog.show();
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(which);
            if (button != null) NotebookButton.makeStatic(button);
        }
    }

    @Override protected void onPause() {
        active = false; ++generation;
        main.removeCallbacks(updateSaveState);
        disposeCanvas();
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("eraser", tool == NotebookInk.Tool.ERASER);
        super.onSaveInstanceState(state);
    }
    private void disposeCanvas() {
        penDown = false;
        if (ink != null) { ink.release(); ink = null; }
        if (canvas != null) canvas.removeAllViews();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
