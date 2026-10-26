package org.eink.inkprobe.notebook;

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
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.TextView;
import java.io.IOException;
import org.eink.inkprobe.R;

/** Notebook home. All file/catalog work goes through the process-wide serialized writer. */
public final class NotebookLibraryActivity extends NotebookDisplayActivity {
    private NotebookSession session;
    private LinearLayout rows;
    private TextView status;
    private AlertDialog shownDialog;
    private boolean active, busy;
    private int generation;
    private Bundle dialogDraft;
    private String draftKind;
    private int draftNotebookId;
    private EditText draftName, draftWidth, draftHeight;
    private Spinner draftSizes;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        dialogDraft = saved == null ? null : saved.getBundle("notebookDialog");
        session = NotebookSession.get(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(24), dp(16), dp(24), dp(16));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label(getString(R.string.app_name), 26);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
        NotebookButton create = button("+ Notebook", this::createNotebook);
        header.addView(create, new LinearLayout.LayoutParams(dp(142), dp(56)));
        NotebookButton settings = button("Settings", () -> startActivity(new Intent(this, NotebookSettingsActivity.class)));
        header.addView(settings, new LinearLayout.LayoutParams(dp(92), dp(56)));
        root.addView(header);
        LinearLayout shortcuts = new LinearLayout(this);
        NotebookButton quick = button("Quick A5 note", () -> createNotebook("Note " + (session.entries().size() + 1), NotebookPaperSize.A5));
        shortcuts.addView(quick, new LinearLayout.LayoutParams(0, dp(48), 1));
        NotebookButton restore = button("Restore backup", () -> transfer(true, 0));
        shortcuts.addView(restore, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(shortcuts);
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
        captureDialogDraft();
        active = false; ++generation;
        if (shownDialog != null) { shownDialog.dismiss(); shownDialog = null; }
        clearDraftFields();
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle saved) {
        captureDialogDraft();
        if (dialogDraft != null) saved.putBundle("notebookDialog", dialogDraft);
        super.onSaveInstanceState(saved);
    }

    private void refresh() {
        busy = true;
        final int request = ++generation;
        session.list((page, error) -> {
            if (!active || request != generation) return;
            busy = false; render();
            if (error != null) showProblem(error);
            else restoreDialogDraft();
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
            NotebookButton backup = button("Backup", () -> transfer(false, entry.id));
            row.addView(backup, new LinearLayout.LayoutParams(dp(88), dp(64)));
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

    private void transfer(boolean restore, int id) {
        if (!canAct()) return;
        startActivity(new Intent(this, NotebookTransferActivity.class)
            .putExtra("restore", restore).putExtra("notebookId", id));
        overridePendingTransition(0, 0);
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
        createDialog();
    }

    private void createNotebook(String name, NotebookPaperSize size) {
        if (!canAct()) return;
        if (session.entries().size() >= NotebookCatalog.MAX_NOTEBOOKS) {
            message("Notebook limit", "This library supports up to " + NotebookCatalog.MAX_NOTEBOOKS + " notebooks.");
            return;
        }
        busy = true;
        final int request = generation;
        session.createNotebook(name, size, (page, error) -> {
            if (!active || request != generation) return;
            busy = false;
            if (error != null) { render(); showProblem(error); return; }
            showEditor();
        });
    }

    private void createDialog() {
        createDialog(null);
    }

    private void createDialog(Bundle restored) {
        busy = true;
        final int request = generation;
        final boolean[] submitted = {false};
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(8), dp(24), dp(8));
        EditText name = textField("Notebook name", restored == null ? "" : restored.getString("name", ""));
        content.addView(name, new LinearLayout.LayoutParams(-1, dp(56)));
        content.addView(label("Page size", 16));
        Spinner sizes = new Spinner(this); sizes.setBackgroundColor(Color.WHITE);
        String[] labels = new String[NotebookPaperSize.PRESETS.size() + 1];
        for (int i = 0; i < NotebookPaperSize.PRESETS.size(); i++) {
            NotebookPaperSize size = NotebookPaperSize.PRESETS.get(i);
            labels[i] = size.name + " · " + dimension(size.widthMm) + " × " + dimension(size.heightMm) + " mm";
        }
        labels[labels.length - 1] = "Custom size";
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); sizes.setAdapter(adapter);
        sizes.setContentDescription("Page size");
        content.addView(sizes, new LinearLayout.LayoutParams(-1, dp(56)));
        LinearLayout dimensions = new LinearLayout(this);
        LinearLayout widthColumn = new LinearLayout(this); widthColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout heightColumn = new LinearLayout(this); heightColumn.setOrientation(LinearLayout.VERTICAL);
        widthColumn.addView(label("Width (mm)", 16)); heightColumn.addView(label("Height (mm)", 16));
        EditText width = textField("Width in millimetres", restored == null ? "148" : restored.getString("width", "148"));
        EditText height = textField("Height in millimetres", restored == null ? "210" : restored.getString("height", "210"));
        width.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        height.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        widthColumn.addView(width, new LinearLayout.LayoutParams(-1, dp(56)));
        heightColumn.addView(height, new LinearLayout.LayoutParams(-1, dp(56)));
        dimensions.addView(widthColumn, new LinearLayout.LayoutParams(0, -2, 1));
        dimensions.addView(heightColumn, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(dimensions);
        TextView hint = label("Custom sizes: 20–1000 mm per side. Paper size also sets the PDF page size.", 14);
        hint.setPadding(0, dp(8), 0, 0); content.addView(hint);
        sizes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean custom = position == NotebookPaperSize.PRESETS.size();
                if (!custom) {
                    NotebookPaperSize size = NotebookPaperSize.PRESETS.get(position);
                    width.setText(dimension(size.widthMm)); height.setText(dimension(size.heightMm));
                }
                width.setEnabled(custom); height.setEnabled(custom);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        if (restored != null) sizes.setSelection(Math.max(0, Math.min(labels.length - 1, restored.getInt("size", 0))));
        draftKind = "create"; draftName = name; draftSizes = sizes; draftWidth = width; draftHeight = height;
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.WHITE); scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("New notebook").setView(scroll)
            .setNegativeButton("Cancel", null).setPositiveButton("Create", null).create();
        dialog.setOnDismissListener(ignored -> {
            if (!submitted[0] && active && request == generation) busy = false;
            if (active) { dialogDraft = null; clearDraftFields(); }
        });
        showStaticDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String normalized;
            try { normalized = NotebookCatalog.normalizedName(name.getText().toString()); }
            catch (IllegalArgumentException error) { name.setError(error.getMessage()); return; }
            NotebookPaperSize paper;
            if (sizes.getSelectedItemPosition() < NotebookPaperSize.PRESETS.size()) {
                paper = NotebookPaperSize.PRESETS.get(sizes.getSelectedItemPosition());
            } else {
                final double w, h;
                try { w = Double.parseDouble(width.getText().toString().replace(',', '.')); }
                catch (NumberFormatException error) { width.setError("Enter a width from 20 to 1000 mm."); return; }
                try { h = Double.parseDouble(height.getText().toString().replace(',', '.')); }
                catch (NumberFormatException error) { height.setError("Enter a height from 20 to 1000 mm."); return; }
                try { paper = NotebookPaperSize.custom(w, h); }
                catch (IllegalArgumentException error) {
                    EditText invalid = !Double.isNaN(w) && !Double.isInfinite(w)
                        && w >= NotebookPaperSize.MIN_MM && w <= NotebookPaperSize.MAX_MM ? height : width;
                    invalid.setError(error.getMessage()); return;
                }
            }
            submitted[0] = true; dialogDraft = null; dialog.dismiss();
            if (active && request == generation) { busy = false; createNotebook(normalized, paper); }
        });
    }

    private EditText textField(String hint, String value) {
        EditText field = new EditText(this);
        field.setTextColor(Color.BLACK); field.setBackgroundColor(Color.WHITE);
        field.setSingleLine(true); field.setTextSize(20);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setHint(hint); field.setContentDescription(hint); field.setText(value);
        return field;
    }

    private static String dimension(double value) {
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private void renameNotebook(NotebookCatalog.Entry entry) {
        renameNotebook(entry, entry.name);
    }

    private void renameNotebook(NotebookCatalog.Entry entry, String existing) {
        if (!canAct()) return;
        nameDialog("Rename notebook", existing, entry.id, name -> {
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
    private void nameDialog(String title, String existing, int notebookId, NameAction action) {
        busy = true;
        final int request = generation;
        final boolean[] submitted = {false};
        EditText field = new EditText(this);
        field.setTextColor(Color.BLACK); field.setBackgroundColor(Color.WHITE);
        field.setSingleLine(true); field.setTextSize(20);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setHint("Notebook name"); field.setText(existing); field.selectAll();
        draftKind = "rename"; draftNotebookId = notebookId; draftName = field;
        LinearLayout content = new LinearLayout(this);
        content.setPadding(dp(24), dp(12), dp(24), 0);
        content.addView(field, new LinearLayout.LayoutParams(-1, dp(56)));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setView(content)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create();
        dialog.setOnDismissListener(ignored -> {
            if (!submitted[0] && active && request == generation) busy = false;
            if (active) { dialogDraft = null; clearDraftFields(); }
        });
        showStaticDialog(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            final String name;
            try { name = NotebookCatalog.normalizedName(field.getText().toString()); }
            catch (IllegalArgumentException error) { field.setError(error.getMessage()); return; }
            submitted[0] = true; dialogDraft = null;
            dialog.dismiss();
            if (active && request == generation) action.run(name);
        });
    }

    private void captureDialogDraft() {
        if (draftKind == null || draftName == null) return;
        Bundle state = new Bundle(); state.putString("kind", draftKind);
        state.putString("name", draftName.getText().toString()); state.putInt("notebookId", draftNotebookId);
        if (draftSizes != null) {
            state.putInt("size", draftSizes.getSelectedItemPosition());
            state.putString("width", draftWidth.getText().toString());
            state.putString("height", draftHeight.getText().toString());
        }
        dialogDraft = state;
    }

    private void restoreDialogDraft() {
        if (dialogDraft == null || !canAct()) return;
        Bundle restored = dialogDraft; dialogDraft = null;
        if ("create".equals(restored.getString("kind"))) createDialog(restored);
        else if ("rename".equals(restored.getString("kind"))) {
            for (NotebookCatalog.Entry entry : session.entries()) if (entry.id == restored.getInt("notebookId")) {
                renameNotebook(entry, restored.getString("name", entry.name)); break;
            }
        }
    }

    private void clearDraftFields() {
        draftKind = null; draftName = draftWidth = draftHeight = null; draftSizes = null; draftNotebookId = 0;
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
