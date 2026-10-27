package org.eink.inkprobe.notebook;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import android.widget.GridView;
import android.widget.BaseAdapter;
import android.widget.AbsListView;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
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
    private GridView rows;
    private TextView path;
    private NotebookButton trashButton, folderButton;
    private int currentFolder, thumbnailHeight, shelfRevision;
    private boolean inTrash, thumbnails;
    private final List<Object> items = new ArrayList<>();
    private final ShelfAdapter adapter = new ShelfAdapter();
    private final Map<Integer, Bitmap> previews = new LinkedHashMap<>(32, 0.75f, true);
    private final Set<Integer> unavailable = new HashSet<>();
    private NotebookSession.PreviewRequest previewRequest;
    private final Runnable loadVisible = this::loadNextPreview;
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
        currentFolder = saved == null ? 0 : saved.getInt("folder", 0);
        inTrash = saved != null && saved.getBoolean("trash", false);
        session = NotebookSession.get(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setWindowAnimations(0);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label(getString(R.string.app_name), 17), new LinearLayout.LayoutParams(0, dp(36), 1));
        header.addView(deviceSettingsButton(), new LinearLayout.LayoutParams(-2, dp(32)));
        root.addView(header);
        LinearLayout shortcuts = new LinearLayout(this);
        shortcuts.addView(button("+ Notebook", this::createNotebook), new LinearLayout.LayoutParams(0, dp(36), 1));
        shortcuts.addView(button("Quick A5 note", () -> createNotebook("Note " + (session.entries().size() + 1), NotebookPaperSize.A5)), new LinearLayout.LayoutParams(0, dp(36), 1));
        shortcuts.addView(button("Import PDF", () -> {
            if (!canAct()) return;
            startActivity(new Intent(this, NotebookPdfImportActivity.class)); overridePendingTransition(0, 0);
        }), new LinearLayout.LayoutParams(0, dp(36), 1));
        shortcuts.addView(button("Restore backup", () -> transfer(true, 0)), new LinearLayout.LayoutParams(0, dp(36), 1));
        root.addView(shortcuts);
        LinearLayout navigation = new LinearLayout(this); navigation.setGravity(Gravity.CENTER_VERTICAL);
        NotebookButton home = button("Bookshelf", () -> navigate(0, false));
        navigation.addView(home, new LinearLayout.LayoutParams(dp(78), dp(34)));
        path = label("", 13); path.setSingleLine(true); path.setEllipsize(TextUtils.TruncateAt.END);
        navigation.addView(path, new LinearLayout.LayoutParams(0, dp(34), 1));
        folderButton = button("+ Folder", this::createFolder);
        navigation.addView(folderButton, new LinearLayout.LayoutParams(dp(76), dp(34)));
        trashButton = button("Trash", () -> navigate(0, true));
        navigation.addView(trashButton, new LinearLayout.LayoutParams(dp(86), dp(34)));
        root.addView(navigation);
        status = label("Opening bookshelf…", 12); root.addView(status, new LinearLayout.LayoutParams(-1, dp(26)));
        rows = new GridView(this); rows.setBackgroundColor(Color.WHITE);
        rows.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        rows.setSelector(new ColorDrawable(Color.TRANSPARENT)); rows.setDrawSelectorOnTop(false);
        rows.setHorizontalSpacing(dp(6)); rows.setVerticalSpacing(dp(2));
        rows.setOverScrollMode(View.OVER_SCROLL_NEVER); rows.setScrollingCacheEnabled(false);
        rows.setDefaultFocusHighlightEnabled(false);
        rows.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < items.size()) openShelfItem(items.get(position));
        });
        rows.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < items.size()) manageShelfItem(items.get(position));
            return true;
        });
        rows.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(AbsListView view, int state) { schedulePreviews(); }
            @Override public void onScroll(AbsListView view, int first, int visible, int total) { schedulePreviews(); }
        });
        rows.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            int height = Math.max(1, (rows.getHeight() - dp(4) * (NotebookSettings.bookshelfRows(this, NotebookSettings.landscape(this)) - 1))
                / NotebookSettings.bookshelfRows(this, NotebookSettings.landscape(this)));
            if (height != thumbnailHeight) {
                thumbnailHeight = height;
                if (thumbnails) adapter.notifyDataSetChanged();
            }
            schedulePreviews();
        });
        rows.setAdapter(adapter);
        root.addView(rows, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume(); active = true; refresh();
    }
    @Override protected void onPause() {
        captureDialogDraft();
        active = false; ++generation; clearPreviews();
        if (shownDialog != null) { shownDialog.dismiss(); shownDialog = null; }
        clearDraftFields();
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle saved) {
        captureDialogDraft();
        saved.putInt("folder", currentFolder); saved.putBoolean("trash", inTrash);
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
        clearPreviews(); items.clear(); ++shelfRevision;
        if (currentFolder != 0 && !hasFolder(currentFolder)) currentFolder = 0;
        thumbnails = !inTrash && NotebookSettings.bookshelfView(this) == NotebookSettings.BookshelfView.THUMBNAILS;
        int columns = thumbnails ? NotebookSettings.bookshelfColumns(this, NotebookSettings.landscape(this))
            : NotebookSettings.landscape(this) ? 2 : 1;
        rows.setNumColumns(columns); rows.setVerticalSpacing(dp(thumbnails ? 4 : 2));
        if (!inTrash && currentFolder == 0) items.addAll(session.folders());
        int trashed = 0, count = 0;
        for (NotebookCatalog.Entry entry : session.entries()) {
            if (entry.status == NotebookCatalog.TRASH || entry.status == NotebookCatalog.PURGING) trashed++;
            if (inTrash ? entry.status == NotebookCatalog.TRASH || entry.status == NotebookCatalog.PURGING
                : entry.status == NotebookCatalog.ACTIVE && entry.folderId == currentFolder) { items.add(entry); count++; }
        }
        path.setText(inTrash ? "/ Trash" : currentFolder == 0 ? "" : "/ " + folderName(currentFolder));
        trashButton.setText("Trash (" + trashed + ")");
        folderButton.setVisibility(inTrash || currentFolder != 0 ? View.GONE : View.VISIBLE);
        status.setOnClickListener(null);
        status.setText(inTrash ? count + " deleted notebooks · Restore or delete permanently from ⋮"
            : count + " notebooks" + (items.isEmpty() ? " · Create a notebook or folder to begin." : " · ⋮ for actions · Hold to manage"));
        adapter.notifyDataSetChanged(); schedulePreviews();
    }

    private boolean hasFolder(int id) {
        for (NotebookCatalog.Folder folder : session.folders()) if (folder.id == id) return true;
        return false;
    }
    private String folderName(int id) {
        for (NotebookCatalog.Folder folder : session.folders()) if (folder.id == id) return folder.name;
        return "Bookshelf";
    }
    private void navigate(int folder, boolean trash) {
        if (!canAct()) return;
        currentFolder = folder; inTrash = trash; render(); rows.setSelection(0);
    }

    private void change(Consumer<NotebookSession.Callback> operation) {
        if (!canAct()) return;
        busy = true; clearPreviews(); final int request = generation;
        operation.accept((page, error) -> {
            if (!active || request != generation) return;
            busy = false; render(); if (error != null) showProblem(error);
        });
    }
    private void notebookActions(NotebookCatalog.Entry entry) {
        if (!canAct()) return;
        if (entry.status == NotebookCatalog.PURGING) {
            message("Deleting notebook", "Deletion was interrupted. Tap Retry to finish deleting this notebook."); return;
        }
        boolean trash = entry.status == NotebookCatalog.TRASH;
        String[] actions = trash ? new String[]{"Restore notebook", "Delete permanently"}
            : new String[]{"Rename", "Move to folder", "Backup", "Move to Trash"};
        AlertDialog menu = new AlertDialog.Builder(this).setTitle(entry.name).setItems(actions, (dialog, which) -> {
            if (trash) {
                if (which == 0) change(callback -> session.restoreNotebook(entry.id, callback));
                else confirmPermanentDelete(entry);
            } else if (which == 0) renameNotebook(entry);
            else if (which == 1) moveNotebook(entry);
            else if (which == 2) transfer(false, entry.id);
            else change(callback -> session.trashNotebook(entry.id, callback));
        }).setNegativeButton("Close", null).create();
        showStaticDialog(menu);
    }
    private void confirmPermanentDelete(NotebookCatalog.Entry entry) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Permanently delete “" + entry.name + "”?")
            .setMessage("All pages and the notebook's imported PDF will be removed from this tablet. This cannot be undone.")
            .setNegativeButton("Cancel", null).setPositiveButton("Delete permanently",
                (choice, which) -> change(callback -> session.permanentlyDeleteNotebook(entry.id, callback))).create();
        showStaticDialog(dialog);
    }
    private void moveNotebook(NotebookCatalog.Entry entry) {
        List<NotebookCatalog.Folder> folders = session.folders();
        String[] names = new String[folders.size() + 1]; names[0] = "Bookshelf (no folder)";
        for (int i = 0; i < folders.size(); i++) names[i + 1] = folders.get(i).name;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Move “" + entry.name + "”")
            .setItems(names, (choice, which) -> change(callback -> session.moveNotebook(entry.id,
                which == 0 ? 0 : folders.get(which - 1).id, callback))).setNegativeButton("Cancel", null).create();
        showStaticDialog(dialog);
    }
    private void createFolder() {
        if (!canAct()) return;
        nameDialog("New folder", "", 0, name -> change(callback -> session.createFolder(name, callback)));
    }
    private void folderActions(NotebookCatalog.Folder folder) {
        if (!canAct()) return;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(folder.name)
            .setItems(new String[]{"Rename folder", "Remove folder (keep notebooks)"}, (choice, which) -> {
                if (which == 0) nameDialog("Rename folder", folder.name, folder.id,
                    name -> change(callback -> session.renameFolder(folder.id, name, callback)));
                else change(callback -> session.deleteFolder(folder.id, callback));
            }).setNegativeButton("Close", null).create();
        showStaticDialog(dialog);
    }

    private void clearPreviews() {
        if (previewRequest != null) { previewRequest.cancel(); previewRequest = null; }
        if (rows != null) rows.removeCallbacks(loadVisible);
        previews.clear(); unavailable.clear();
    }
    private void schedulePreviews() {
        if (!active || busy || !thumbnails || rows == null) return;
        rows.removeCallbacks(loadVisible); rows.post(loadVisible);
    }
    private void loadNextPreview() {
        if (!active || busy || !thumbnails || previewRequest != null) return;
        for (int i = 0; i < rows.getChildCount(); i++) {
            View cell = rows.getChildAt(i);
            if (!(cell.getTag() instanceof NotebookCatalog.Entry)) continue;
            NotebookCatalog.Entry entry = (NotebookCatalog.Entry) cell.getTag();
            NotebookPageThumbnail thumbnail = (NotebookPageThumbnail) ((LinearLayout) cell).getChildAt(0);
            if (thumbnail.hasPreview(entry.id) || previews.containsKey(entry.id) || unavailable.contains(entry.id)) continue;
            final int request = generation;
            previewRequest = session.previewNotebook(entry.id, (bitmap, error) -> {
                previewRequest = null;
                if (!active || request != generation || !thumbnails) { if (bitmap != null) bitmap.recycle(); return; }
                if (error != null || bitmap == null) unavailable.add(entry.id);
                else {
                    previews.put(entry.id, bitmap);
                    while (previews.size() > 32) previews.remove(previews.keySet().iterator().next());
                }
                for (int j = 0; j < rows.getChildCount(); j++) {
                    View visible = rows.getChildAt(j);
                    if (visible.getTag() instanceof NotebookCatalog.Entry && ((NotebookCatalog.Entry) visible.getTag()).id == entry.id)
                        bindPreview((NotebookPageThumbnail) ((LinearLayout) visible).getChildAt(0), entry);
                }
                schedulePreviews();
            });
            return;
        }
    }
    private void bindPreview(NotebookPageThumbnail thumbnail, NotebookCatalog.Entry entry) {
        Bitmap bitmap = previews.get(entry.id);
        if (bitmap != null) thumbnail.bind(entry.id, bitmap, false);
        else if (unavailable.contains(entry.id)) thumbnail.showError(entry.id, false);
        else thumbnail.showLoading(entry.id, false);
        thumbnail.setContentDescription("Open notebook " + entry.name);
    }

    private void openShelfItem(Object item) {
        if (!canAct()) return;
        if (item instanceof NotebookCatalog.Folder) navigate(((NotebookCatalog.Folder) item).id, false);
        else if (item instanceof NotebookCatalog.Entry) {
            NotebookCatalog.Entry entry = (NotebookCatalog.Entry) item;
            if (entry.status == NotebookCatalog.ACTIVE) openNotebook(entry.id); else notebookActions(entry);
        }
    }

    private void manageShelfItem(Object item) {
        if (!canAct()) return;
        if (item instanceof NotebookCatalog.Folder) folderActions((NotebookCatalog.Folder) item);
        else if (item instanceof NotebookCatalog.Entry) notebookActions((NotebookCatalog.Entry) item);
    }

    /** Rebind data without replacing pressed children when GridView relays out its first cell. */
    private final class ShelfCell extends LinearLayout {
        final int type;
        private final LinearLayout title;
        private final NotebookButton nameButton, menu;
        private final View preview;
        private int boundRevision = -1;

        ShelfCell(int type) {
            super(NotebookLibraryActivity.this);
            this.type = type;
            setOrientation(LinearLayout.VERTICAL); setBackgroundColor(Color.WHITE);
            setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            if (type == 1) {
                TextView image = label("", 13); image.setGravity(Gravity.CENTER);
                image.setBackground(NotebookUi.border(getResources().getDisplayMetrics().density)); preview = image;
            } else if (type == 2) {
                NotebookPageThumbnail image = new NotebookPageThumbnail(NotebookLibraryActivity.this);
                image.setLabelVisible(false); preview = image;
            } else preview = null;
            if (preview != null) {
                preview.setOnClickListener(v -> openShelfItem(getTag()));
                preview.setOnLongClickListener(v -> { manageShelfItem(getTag()); return true; });
                addView(preview, new LinearLayout.LayoutParams(-1, 0, 1));
            }
            title = new LinearLayout(NotebookLibraryActivity.this); title.setGravity(Gravity.CENTER_VERTICAL);
            nameButton = button("", () -> openShelfItem(getTag()));
            nameButton.setSingleLine(true); nameButton.setEllipsize(TextUtils.TruncateAt.END);
            nameButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); nameButton.setTextSize(type == 0 ? 14 : 12);
            nameButton.setOnLongClickListener(v -> { manageShelfItem(getTag()); return true; });
            title.addView(nameButton, new LinearLayout.LayoutParams(0, -1, 1));
            menu = button("⋮", () -> manageShelfItem(getTag())); menu.setTextSize(18);
            title.addView(menu, new LinearLayout.LayoutParams(dp(30), -1));
            addView(title, new LinearLayout.LayoutParams(-1, type == 0 ? 0 : dp(28), type == 0 ? 1 : 0));
            if (type == 0) {
                View line = new View(NotebookLibraryActivity.this); line.setBackgroundColor(0xffcccccc);
                addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }

        void bind(Object item) {
            int height = type == 0 ? dp(40) : thumbnailHeight > 0 ? thumbnailHeight : dp(96);
            ViewGroup.LayoutParams parameters = getLayoutParams();
            if (getTag() == item && boundRevision == shelfRevision && parameters != null && parameters.height == height) return;
            setTag(item); boundRevision = shelfRevision;
            if (parameters == null) setLayoutParams(new AbsListView.LayoutParams(-1, height));
            else if (parameters.height != height) { parameters.height = height; setLayoutParams(parameters); }
            boolean folder = item instanceof NotebookCatalog.Folder;
            String name = folder ? ((NotebookCatalog.Folder) item).name : ((NotebookCatalog.Entry) item).name;
            String shown = (folder && type == 0 ? "▸ " : "") + name;
            if (!shown.contentEquals(nameButton.getText())) nameButton.setText(shown);
            nameButton.setContentDescription((folder ? "Open folder " : inTrash ? "Manage deleted notebook " : "Open notebook ") + name);
            menu.setContentDescription("Manage " + (folder ? "folder " : "notebook ") + name);
            if (type != 0) {
                LinearLayout.LayoutParams titleLayout = (LinearLayout.LayoutParams) title.getLayoutParams();
                int titleHeight = Math.min(dp(28), Math.max(1, height / 3));
                if (titleLayout.height != titleHeight) { titleLayout.height = titleHeight; title.setLayoutParams(titleLayout); }
                if (folder) {
                    int count = 0;
                    for (NotebookCatalog.Entry entry : session.entries())
                        if (entry.folderId == ((NotebookCatalog.Folder) item).id && entry.status == NotebookCatalog.ACTIVE) count++;
                    TextView image = (TextView) preview;
                    String summary = "Folder\n" + count + " notebooks";
                    if (!summary.contentEquals(image.getText())) image.setText(summary);
                    image.setContentDescription("Open folder " + name);
                } else bindPreview((NotebookPageThumbnail) preview, (NotebookCatalog.Entry) item);
            }
        }
    }

    private final class ShelfAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public boolean hasStableIds() { return true; }
        @Override public long getItemId(int position) {
            Object item = items.get(position);
            return item instanceof NotebookCatalog.Folder ? 2L * ((NotebookCatalog.Folder) item).id + 1
                : 2L * ((NotebookCatalog.Entry) item).id;
        }
        @Override public int getViewTypeCount() { return 3; }
        @Override public int getItemViewType(int position) {
            return !thumbnails ? 0 : items.get(position) instanceof NotebookCatalog.Folder ? 1 : 2;
        }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            int type = getItemViewType(position);
            ShelfCell cell = recycled instanceof ShelfCell && ((ShelfCell) recycled).type == type
                ? (ShelfCell) recycled : new ShelfCell(type);
            cell.bind(items.get(position)); schedulePreviews(); return cell;
        }
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
        if (session.availableNotebookId() == 0) {
            message("Notebook limit", "This library supports up to " + NotebookCatalog.MAX_NOTEBOOKS + " notebooks.");
            return;
        }
        createDialog();
    }

    private void createNotebook(String name, NotebookPaperSize size) {
        if (!canAct()) return;
        if (session.availableNotebookId() == 0) {
            message("Notebook limit", "This library supports up to " + NotebookCatalog.MAX_NOTEBOOKS + " notebooks.");
            return;
        }
        busy = true;
        final int request = generation;
        session.createNotebook(name, size, inTrash ? 0 : currentFolder, (page, error) -> {
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
        content.addView(name, new LinearLayout.LayoutParams(-1, dp(38)));
        content.addView(label("Page size", 13));
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
        content.addView(sizes, new LinearLayout.LayoutParams(-1, dp(38)));
        LinearLayout dimensions = new LinearLayout(this);
        LinearLayout widthColumn = new LinearLayout(this); widthColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout heightColumn = new LinearLayout(this); heightColumn.setOrientation(LinearLayout.VERTICAL);
        widthColumn.addView(label("Width (mm)", 13)); heightColumn.addView(label("Height (mm)", 13));
        EditText width = textField("Width in millimetres", restored == null ? "148" : restored.getString("width", "148"));
        EditText height = textField("Height in millimetres", restored == null ? "210" : restored.getString("height", "210"));
        width.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        height.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        widthColumn.addView(width, new LinearLayout.LayoutParams(-1, dp(38)));
        heightColumn.addView(height, new LinearLayout.LayoutParams(-1, dp(38)));
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
        field.setSingleLine(true); field.setTextSize(14);
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
        field.setSingleLine(true); field.setTextSize(14);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        field.setHint(title.toLowerCase(java.util.Locale.ROOT).contains("folder") ? "Folder name" : "Notebook name"); field.setText(existing); field.selectAll();
        draftKind = title.toLowerCase(java.util.Locale.ROOT).contains("folder") ? notebookId == 0 ? "folderCreate" : "folderRename" : "rename"; draftNotebookId = notebookId; draftName = field;
        LinearLayout content = new LinearLayout(this);
        content.setPadding(dp(24), dp(12), dp(24), 0);
        content.addView(field, new LinearLayout.LayoutParams(-1, dp(38)));
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
            if (title.toLowerCase(java.util.Locale.ROOT).contains("folder")) {
                for (NotebookCatalog.Folder folder : session.folders()) if (folder.id != notebookId && folder.name.equalsIgnoreCase(name)) {
                    field.setError("A folder with this name already exists"); return;
                }
            }
            submitted[0] = true; dialogDraft = null;
            dialog.dismiss();
            if (active && request == generation) { busy = false; action.run(name); }
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
        else if ("folderCreate".equals(restored.getString("kind"))) {
            nameDialog("New folder", restored.getString("name", ""), 0, name -> change(callback -> session.createFolder(name, callback)));
        } else if ("folderRename".equals(restored.getString("kind"))) {
            int id = restored.getInt("notebookId");
            if (hasFolder(id)) nameDialog("Rename folder", restored.getString("name", ""), id,
                name -> change(callback -> session.renameFolder(id, name, callback)));
        } else if ("rename".equals(restored.getString("kind"))) {
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
        NotebookUi.show(dialog);
    }

    private NotebookButton button(String text, Runnable action) {
        NotebookButton button = new NotebookButton(this);
        button.setText(text); button.setTextSize(13);
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
        if (currentFolder != 0 || inTrash) { navigate(0, false); return; }
        super.onBackPressed(); overridePendingTransition(0, 0);
    }
}
