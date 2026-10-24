package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.IOException;

/** Notebook home. All file/catalog work goes through the process-wide serialized writer. */
public final class NotebookLibraryActivity extends Activity {
    private NotebookSession session;
    private LinearLayout rows;
    private TextView status;
    private AlertDialog shownDialog;
    private boolean active, busy;
    private int generation;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        session = NotebookSession.get(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(24), dp(16), dp(24), dp(16));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("Notebooks", 26);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
        NotebookButton create = button("+ Notebook", this::createNotebook);
        header.addView(create, new LinearLayout.LayoutParams(dp(142), dp(56)));
        root.addView(header);
        status = label("Opening library…", 14);
        status.setPadding(0, dp(8), 0, dp(16));
        root.addView(status);
        View line = new View(this); line.setBackgroundColor(0xffbbbbbb);
        root.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE); scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume(); active = true; refresh();
    }
    @Override protected void onPause() {
        active = false; ++generation;
        if (shownDialog != null) { shownDialog.dismiss(); shownDialog = null; }
        super.onPause();
    }

    private void refresh() {
        busy = true;
        final int request = ++generation;
        session.list((page, error) -> {
            if (!active || request != generation) return;
            busy = false; render();
            if (error != null) showProblem(error);
        });
    }

    private void render() {
        rows.removeAllViews();
        for (NotebookCatalog.Entry entry : session.entries()) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(12), 0, dp(12));
            NotebookButton open = button(entry.name, () -> openNotebook(entry.id));
            open.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            open.setTextSize(21); open.setSingleLine(true); open.setEllipsize(TextUtils.TruncateAt.END);
            open.setContentDescription("Open notebook " + entry.name);
            row.addView(open, new LinearLayout.LayoutParams(0, dp(64), 1));
            NotebookButton rename = button("Rename", () -> renameNotebook(entry));
            rename.setContentDescription("Rename notebook " + entry.name);
            row.addView(rename, new LinearLayout.LayoutParams(dp(96), dp(64)));
            rows.addView(row, new LinearLayout.LayoutParams(-1, -2));
            View line = new View(this); line.setBackgroundColor(0xffdddddd);
            rows.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        status.setOnClickListener(null);
        status.setText("Choose a notebook to continue. Pages save automatically.");
    }

    private boolean canAct() { return active && !busy; }

    private void openNotebook(int id) {
        if (!canAct()) return;
        busy = true;
        final int request = generation;
        session.openNotebook(id, (page, error) -> {
            if (!active || request != generation) return;
            busy = false;
            if (error != null) { render(); showProblem(error); return; }
            showEditor();
        });
    }

    private void showEditor() {
        busy = true;
        startActivity(new Intent(this, NotebookActivity.class));
        overridePendingTransition(0, 0);
    }

    private void createNotebook() {
        if (!canAct()) return;
        if (session.entries().size() >= NotebookCatalog.MAX_NOTEBOOKS) {
            message("Notebook limit", "This library supports up to " + NotebookCatalog.MAX_NOTEBOOKS + " notebooks.");
            return;
        }
        nameDialog("New notebook", "", name -> {
            busy = true;
            final int request = generation;
            session.createNotebook(name, (page, error) -> {
                if (!active || request != generation) return;
                busy = false;
                if (error != null) { render(); showProblem(error); return; }
                showEditor();
            });
        });
    }

    private void renameNotebook(NotebookCatalog.Entry entry) {
        if (!canAct()) return;
        nameDialog("Rename notebook", entry.name, name -> {
            busy = true;
            final int request = generation;
            session.renameNotebook(entry.id, name, (page, error) -> {
                if (!active || request != generation) return;
                busy = false; render();
                if (error != null) showProblem(error);
            });
        });
    }

    private interface NameAction { void run(String name); }
    private void nameDialog(String title, String existing, NameAction action) {
        busy = true;
        final int request = generation;
        final boolean[] submitted = {false};
        EditText field = new EditText(this);
        field.setTextColor(Color.BLACK); field.setBackgroundColor(Color.WHITE);
        field.setSingleLine(true); field.setTextSize(20);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setHint("Notebook name"); field.setText(existing); field.selectAll();
        LinearLayout content = new LinearLayout(this);
        content.setPadding(dp(24), dp(12), dp(24), 0);
        content.addView(field, new LinearLayout.LayoutParams(-1, dp(56)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(content)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnDismissListener(ignored -> {
            if (!submitted[0] && active && request == generation) busy = false;
        });
        showStaticDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String name;
            try { name = NotebookCatalog.normalizedName(field.getText().toString()); }
            catch (IllegalArgumentException error) { field.setError(error.getMessage()); return; }
            submitted[0] = true;
            dialog.dismiss();
            if (active && request == generation) action.run(name);
        });
    }

    private void showProblem(IOException error) {
        status.setText("Couldn’t complete the last action · tap to retry");
        status.setOnClickListener(v -> {
            if (!canAct()) return;
            busy = true;
            final int request = generation;
            session.retry((page, failure) -> {
                if (!active || request != generation) return;
                busy = false; render();
                if (failure != null) {
                    showProblem(failure);
                    message("Couldn’t complete the action", failure.getMessage());
                }
            });
        });
        message("Couldn’t complete the action", error.getMessage());
    }

    private void message(String title, String message) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title)
            .setMessage(message == null ? "Please retry." : message).setPositiveButton("Done", null).create();
        showStaticDialog(dialog);
    }

    private void showStaticDialog(AlertDialog dialog) {
        shownDialog = dialog;
        dialog.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0f); dialog.getWindow().setWindowAnimations(0);
        }
        dialog.show();
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE}) {
            Button button = dialog.getButton(which);
            if (button != null) NotebookButton.makeStatic(button);
        }
    }

    private NotebookButton button(String text, Runnable action) {
        NotebookButton button = new NotebookButton(this);
        button.setText(text); button.setTextSize(16);
        button.setOnClickListener(v -> action.run());
        return button;
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextColor(Color.BLACK); view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onBackPressed() {
        super.onBackPressed(); overridePendingTransition(0, 0);
    }
}
