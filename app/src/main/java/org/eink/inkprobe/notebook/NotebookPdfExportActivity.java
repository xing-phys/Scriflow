package org.eink.inkprobe.notebook;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;

/** A static export screen whose picker lifecycle is independent of the writing surface. */
public final class NotebookPdfExportActivity extends NotebookDisplayActivity {
    private static final int CREATE_PDF = 2301;
    private static final String TEMP_FILE = "pdfFile";
    private final NotebookPdfExport.Listener listener = this::render;
    private NotebookPdfExport export;
    private TextView status, detail, notice;
    private NotebookButton save, done;
    private int notebookId, pageCount;
    private String notebookName, message;
    private boolean resumed, observing, awaitingPicker, autoPicker = true, savedPdf;
    private boolean validRequest;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        notebookId = getIntent().getIntExtra("notebookId", 0);
        notebookName = getIntent().getStringExtra("notebookName");
        pageCount = getIntent().getIntExtra("pageCount", 0);
        validRequest = notebookId >= 1 && notebookId <= NotebookCatalog.MAX_NOTEBOOKS
            && pageCount >= 1 && pageCount <= NotebookIndex.MAX_PAGES
            && notebookName != null && !notebookName.trim().isEmpty();
        if (state != null) {
            awaitingPicker = state.getBoolean("awaitingPicker", false);
            autoPicker = state.getBoolean("autoPicker", false);
            savedPdf = state.getBoolean("savedPdf", false);
            message = state.getString("message");
        }
        createScreen();
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof NotebookPdfExport) {
            export = (NotebookPdfExport) retained;
        } else if (!validRequest) {
            autoPicker = false;
            message = "The notebook could not be identified. Return to the notebook and try Export PDF again.";
        } else if (state == null) {
            prepare();
        } else if (!savedPdf) {
            String path = state.getString(TEMP_FILE);
            if (path != null) {
                try {
                    boolean interruptedSave = state.getBoolean("saving", false);
                    export = NotebookPdfExport.restore(getApplicationContext(), path,
                        notebookId, notebookName, pageCount, interruptedSave);
                    if (interruptedSave) {
                        autoPicker = false;
                        awaitingPicker = false;
                    }
                } catch (IOException | RuntimeException error) {
                    autoPicker = false;
                    message = "The prepared PDF is no longer available. Tap Retry to prepare it again.";
                }
            } else {
                awaitingPicker = false;
                message = "PDF preparation was interrupted and has restarted.";
                prepare();
            }
        }
        render();
    }

    private void createScreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setWindowAnimations(0);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(24), dp(16), dp(24), dp(16));
        root.addView(label("PDF export", 26), new LinearLayout.LayoutParams(-1, dp(60)));
        TextView notebook = label(notebookName == null ? "Notebook" : notebookName, 18);
        notebook.setMaxLines(2);
        notebook.setEllipsize(TextUtils.TruncateAt.END);
        notebook.setPadding(0, 0, 0, dp(20));
        root.addView(notebook, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(this);
        divider.setBackgroundColor(0xffbbbbbb);
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(28), 0, dp(24));
        status = label("Preparing PDF", 23);
        body.addView(status, new LinearLayout.LayoutParams(-1, -2));
        detail = label("", 16);
        detail.setPadding(0, dp(14), 0, 0);
        body.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        notice = label("", 15);
        notice.setPadding(0, dp(22), 0, 0);
        body.addView(notice, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        save = new NotebookButton(this);
        save.setTextSize(18);
        save.setText("Save PDF");
        save.setOnClickListener(view -> saveOrRetry());
        buttons.addView(save, new LinearLayout.LayoutParams(0, dp(60), 1));
        done = new NotebookButton(this);
        done.setTextSize(18);
        done.setText("Cancel");
        done.setOnClickListener(view -> closeExport());
        buttons.addView(done, new LinearLayout.LayoutParams(dp(120), dp(60)));
        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
    }

    @Override protected void onStart() {
        super.onStart();
        attach();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        render();
    }

    @Override protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override protected void onStop() {
        detach();
        super.onStop();
    }

    @Override public Object onRetainNonConfigurationInstance() { return export; }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("awaitingPicker", awaitingPicker);
        state.putBoolean("autoPicker", autoPicker);
        state.putString("message", message);
        if (export != null) {
            File file = export.readyFile();
            if (file != null) state.putString(TEMP_FILE, file.getAbsolutePath());
            state.putBoolean("saving", export.state() == NotebookPdfExport.State.SAVING);
            state.putBoolean("savedPdf", export.state() == NotebookPdfExport.State.SAVED);
        } else state.putBoolean("savedPdf", savedPdf);
        super.onSaveInstanceState(state);
    }

    private void attach() {
        if (export != null && !observing) {
            observing = true;
            export.observe(listener);
        }
    }

    private void detach() {
        if (export != null && observing) export.detach(listener);
        observing = false;
    }

    private void prepare() {
        if (!validRequest || isFinishing()) return;
        detach();
        if (export != null) export.cancel();
        savedPdf = false;
        try {
            export = NotebookSession.get(this).exportPdf(getApplicationContext(),
                notebookId, notebookName, pageCount);
            // onCreate attaches in onStart; a manual retry is already visible.
            if (resumed) attach();
        } catch (RuntimeException error) {
            export = null;
            autoPicker = false;
            message = "Couldn’t start PDF preparation. Tap Retry to try again.";
        }
    }

    private void saveOrRetry() {
        if (!resumed || awaitingPicker || isFinishing()) return;
        if (export == null || export.state() == NotebookPdfExport.State.FAILED
                && export.readyFile() == null) {
            message = null;
            autoPicker = true;
            prepare();
            render();
        } else if (export.state() == NotebookPdfExport.State.READY
                || export.state() == NotebookPdfExport.State.FAILED && export.readyFile() != null) {
            chooseDestination();
        }
    }

    private void chooseDestination() {
        if (!resumed || awaitingPicker || export == null || export.readyFile() == null
                || isFinishing()) return;
        autoPicker = false;
        awaitingPicker = true;
        message = null;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_TITLE, suggestedName(notebookName))
            .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try {
            startActivityForResult(intent, CREATE_PDF);
            overridePendingTransition(0, 0);
        } catch (ActivityNotFoundException | SecurityException error) {
            awaitingPicker = false;
            message = "Couldn’t open a document picker. Tap Save PDF to try again.";
        }
        render();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != CREATE_PDF) return;
        awaitingPicker = false;
        autoPicker = false;
        Uri destination = result == RESULT_OK && data != null ? data.getData() : null;
        if (destination == null) {
            message = export != null && export.readyFile() != null
                ? "Saving cancelled. Tap Save PDF to choose a location."
                : "Saving cancelled. Tap Retry to prepare the PDF again.";
        } else if (export == null || export.readyFile() == null) {
            message = "The prepared PDF is no longer available. Tap Retry to prepare it again. An empty document may remain at your chosen location.";
        } else {
            message = null;
            // Android may deliver this callback before onResume. The process-owned job
            // can begin copying now; it never depends on the editor's surface or generation.
            export.save(destination);
        }
        render();
    }

    private void render() {
        if (status == null || isFinishing()) return;
        NotebookPdfExport.State state = export == null ? null : export.state();
        String title, explanation;
        String action = "Save PDF";
        boolean maySave = false;
        String close = "Done";
        if (savedPdf || state == NotebookPdfExport.State.SAVED) {
            title = "PDF saved";
            explanation = pageCount + (pageCount == 1 ? " page exported." : " pages exported.");
        } else if (state == NotebookPdfExport.State.PREPARING) {
            title = "Preparing PDF";
            int current = Math.min(pageCount, Math.max(1, export.completedPages() + 1));
            explanation = "Page " + current + " of " + pageCount;
            close = "Cancel";
        } else if (state == NotebookPdfExport.State.SAVING) {
            title = "Saving PDF";
            explanation = "Writing the PDF to your selected location… Cancelling can leave an incomplete PDF there.";
            close = "Cancel";
        } else if (state == NotebookPdfExport.State.READY
                || state == NotebookPdfExport.State.FAILED && export.readyFile() != null) {
            title = awaitingPicker ? "Choose a save location" : "PDF ready";
            explanation = export.error() == null
                ? pageCount + (pageCount == 1 ? " page is ready to save." : " pages are ready to save.")
                : export.error();
            maySave = !awaitingPicker;
        } else if (state == NotebookPdfExport.State.CANCELLED) {
            title = "Export cancelled";
            explanation = "Return to your notebook to continue writing.";
        } else {
            title = "Couldn’t export PDF";
            explanation = export == null || export.error() == null ? "Please retry." : export.error();
            action = "Retry";
            maySave = validRequest && !awaitingPicker;
        }
        setText(status, title);
        setText(detail, explanation);
        setText(notice, message == null ? "" : message);
        setText(save, action);
        save.setAvailable(maySave && !savedPdf);
        setText(done, close);
        if (resumed && !awaitingPicker && autoPicker && state == NotebookPdfExport.State.READY) {
            // Consume the one-shot flag before launching: refreshes and configuration
            // changes cannot open a second picker over the first.
            autoPicker = false;
            chooseDestination();
        }
    }

    private void closeExport() {
        autoPicker = false;
        if (export != null) export.cancel();
        finish();
        overridePendingTransition(0, 0);
    }

    @Override public void onBackPressed() { closeExport(); }

    @Override protected void onDestroy() {
        detach();
        // Configuration changes retain the same job. Home also leaves it available;
        // closing this screen owns cancellation and all temporary-file cleanup.
        if (isFinishing() && export != null) export.cancel();
        super.onDestroy();
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.BLACK);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setBackgroundColor(Color.WHITE);
        view.setStateListAnimator(null);
        view.setDefaultFocusHighlightEnabled(false);
        return view;
    }

    private static void setText(TextView view, String value) {
        if (!value.contentEquals(view.getText())) view.setText(value);
    }

    private static String suggestedName(String name) {
        StringBuilder clean = new StringBuilder();
        if (name != null) {
            for (int i = 0; i < name.length(); i++) {
                char value = name.charAt(i);
                clean.append(value < 32 || value == 127 || value == '/' || value == '\\' ? '_' : value);
            }
        }
        String title = clean.toString().trim();
        if (title.isEmpty() || title.equals(".") || title.equals("..")) title = "Notebook";
        return title + ".pdf";
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
