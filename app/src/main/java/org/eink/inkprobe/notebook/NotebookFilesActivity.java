package org.eink.inkprobe.notebook;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** E-ink file browser for shared tablet storage, separate from the notebook bookshelf. */
public final class NotebookFilesActivity extends NotebookDisplayActivity {
    private static final int STORAGE_PERMISSION = 2410;
    private NotebookFilesJob job;
    private TextView status, empty, selectedCount, clipboardCount;
    private HorizontalScrollView path;
    private LinearLayout pathParts;
    private String renderedPath;
    private EditText search, saveName;
    private String pickerMode, pickerFilter;
    private LinearLayout saveBar;
    private LinearLayout selectionBar, clipboardBar, accessPanel, footer;
    private NotebookButton up, homeButton, optionsButton, searchAction, searchClose, stop;
    private NotebookBrowserIconButton presentation, selectedActions;
    private android.widget.PopupWindow actionBar;
    private GridView list;
    private AlertDialog dialog;
    private boolean resumed, renderingBusy, renderingAccess, thumbnails, searching;
    private final List<Row> rows = new ArrayList<>();
    private final FileAdapter adapter = new FileAdapter();

    private static final class Row {
        final File file;
        final String title, detail;
        final boolean directory, root;
        Row(File file, String title, String detail, boolean directory, boolean root) {
            this.file = file; this.title = title; this.detail = detail; this.directory = directory; this.root = root;
        }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        pickerMode = getIntent().getStringExtra(NotebookFilePicker.MODE);
        pickerFilter = getIntent().getStringExtra(NotebookFilePicker.FILTER);
        thumbnails = NotebookCollectionLayout.thumbnails(this, "files");
        Object retained = getLastNonConfigurationInstance();
        job = retained instanceof NotebookFilesJob ? (NotebookFilesJob) retained : new NotebookFilesJob(this);
        if (!(retained instanceof NotebookFilesJob) && saved != null) {
            job.folder = saved.getString("folder"); job.query = saved.getString("query", "");
            job.hidden = saved.getBoolean("hidden"); job.move = saved.getBoolean("move");
            job.selecting = saved.getBoolean("selecting");
            try { job.sort = NotebookFiles.Sort.valueOf(saved.getString("sort", "NAME")); }
            catch (IllegalArgumentException ignored) { job.sort = NotebookFiles.Sort.NAME; }
            String[] selected = saved.getStringArray("selected"), clipboard = saved.getStringArray("clipboard");
            if (selected != null) java.util.Collections.addAll(job.selected, selected);
            if (clipboard != null) for (String item : clipboard) job.clipboard.add(new File(item));
        }
        searching = saved == null ? !job.query.isEmpty() : saved.getBoolean("searching", !job.query.isEmpty());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setWindowAnimations(0);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE); root.setPadding(dp(16), dp(10), dp(16), dp(8));
        LinearLayout header = row();
        header.addView(icon(picking() ? "Cancel file selection" : "Bookshelf", picking() ? NotebookIconDrawable.Icon.CLOSE : NotebookIconDrawable.Icon.LIBRARY, this::bookshelf), new LinearLayout.LayoutParams(dp(44), dp(42)));
        TextView title = label("save".equals(pickerMode) ? "Save file" : "open".equals(pickerMode) ? "Choose file" : "Files", 21); title.setTypeface(null, Typeface.BOLD); title.setPadding(dp(12), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        header.addView(deviceSettingsIsland(), new LinearLayout.LayoutParams(-2, dp(32))); root.addView(header);

        LinearLayout navigation = row();
        up = icon("Parent folder", NotebookIconDrawable.Icon.PREVIOUS, this::parent);
        navigation.addView(up, new LinearLayout.LayoutParams(dp(44), dp(42)));
        homeButton = icon("Storage locations", NotebookIconDrawable.Icon.HOME, () -> navigate(null));
        navigation.addView(homeButton, spaced(44, 42));
        path = new HorizontalScrollView(this); path.setHorizontalScrollBarEnabled(false);
        path.setOverScrollMode(View.OVER_SCROLL_NEVER); path.setFillViewport(false);
        path.setBackground(NotebookUi.rounded(getResources().getDisplayMetrics().density, 7));
        pathParts = row(); pathParts.setPadding(dp(8), 0, dp(8), 0);
        path.addView(pathParts, new HorizontalScrollView.LayoutParams(-2, -1));
        LinearLayout.LayoutParams pathSpace = new LinearLayout.LayoutParams(0, dp(40), 1); pathSpace.setMarginStart(dp(6));
        navigation.addView(path, pathSpace);

        search = new EditText(this); search.setTextSize(14); search.setSingleLine(true);
        search.setTextColor(Color.BLACK); search.setHintTextColor(Color.DKGRAY); search.setHint("Search this folder and subfolders");
        search.setContentDescription("Search files by name"); search.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setText(saved == null ? job.query : saved.getString("searchDraft", job.query));
        search.setOnEditorActionListener((view, action, event) -> { if (action == EditorInfo.IME_ACTION_SEARCH) { find(); return true; } return false; });
        navigation.addView(search, new LinearLayout.LayoutParams(0, dp(44), 1));
        searchAction = icon("Search files", NotebookIconDrawable.Icon.SEARCH, () -> {
            if (!ready()) return;
            if (searching) find(); else showSearch(true, true);
        });
        navigation.addView(searchAction, spaced(44, 42));
        searchClose = icon("Close search", NotebookIconDrawable.Icon.CLOSE, this::closeSearch);
        navigation.addView(searchClose, spaced(44, 42));
        presentation = icon("Show file thumbnails", NotebookIconDrawable.Icon.OVERVIEW, () -> {
            if (!ready()) return;
            thumbnails = !thumbnails; NotebookCollectionLayout.setThumbnails(this, "files", thumbnails); render();
        });
        navigation.addView(presentation, spaced(44, 42));
        optionsButton = icon("File options", NotebookIconDrawable.Icon.MORE, this::options);
        navigation.addView(optionsButton, spaced(44, 42));
        root.addView(navigation);

        selectionBar = row(); selectedCount = label("", 13);
        selectionBar.addView(selectedCount, new LinearLayout.LayoutParams(0, dp(42), 1));
        selectionBar.addView(icon("Select all", NotebookIconDrawable.Icon.CHECK, this::selectAll), spaced(44, 42));
        selectedActions = icon("Actions", NotebookIconDrawable.Icon.MORE, this::selectionActions);
        selectionBar.addView(selectedActions, spaced(44, 42));
        selectionBar.addView(icon("Cancel selection", NotebookIconDrawable.Icon.CLOSE, () -> { if (ready()) { job.selecting = false; job.selected.clear(); render(); } }), spaced(44, 42));
        root.addView(selectionBar);

        clipboardBar = row(); clipboardCount = label("", 13);
        clipboardBar.addView(clipboardCount, new LinearLayout.LayoutParams(0, dp(44), 1));
        clipboardBar.addView(icon("Paste here", NotebookIconDrawable.Icon.PASTE, this::paste), spaced(44, 42));
        clipboardBar.addView(icon("Cancel paste", NotebookIconDrawable.Icon.CLOSE, () -> { if (ready()) { job.clipboard.clear(); render(); } }), spaced(44, 42));
        root.addView(clipboardBar);

        accessPanel = new LinearLayout(this); accessPanel.setOrientation(LinearLayout.VERTICAL); accessPanel.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView access = label("Allow storage access to browse and manage files on this tablet.\n\nYou can copy, move, rename, share and delete files in shared storage. Android protects private app data.", 16);
        accessPanel.addView(access); accessPanel.addView(button("Allow storage access", NotebookIconDrawable.Icon.FOLDER, this::requestStorage), new LinearLayout.LayoutParams(-1, dp(48)));
        root.addView(accessPanel);

        FrameLayout body = new FrameLayout(this);
        list = new GridView(this); list.setId(android.R.id.list); list.setBackgroundColor(Color.WHITE);
        list.setStretchMode(GridView.STRETCH_COLUMN_WIDTH); list.setHorizontalSpacing(dp(8)); list.setVerticalSpacing(dp(8));
        list.setOverScrollMode(View.OVER_SCROLL_NEVER); list.setScrollingCacheEnabled(false);
        list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        list.setAdapter(adapter); list.setOnItemClickListener((parent, view, position, id) -> open(rows.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> { toggle(rows.get(position)); return true; });
        empty = label("", 16); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(24), dp(24), dp(24), dp(24));
        body.addView(list, new FrameLayout.LayoutParams(-1, -1)); body.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        list.setEmptyView(empty); root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        saveBar = row();
        saveName = new EditText(this); saveName.setSingleLine(true); saveName.setTextSize(15);
        saveName.setHint("File name"); saveName.setContentDescription("File name");
        saveName.setText(saved == null ? getIntent().getStringExtra(NotebookFilePicker.NAME) : saved.getString("saveName"));
        saveBar.addView(saveName, new LinearLayout.LayoutParams(0, dp(48), 1));
        saveBar.addView(button("Save here", NotebookIconDrawable.Icon.SAVE, this::saveHere), spaced(110, 44));
        root.addView(saveBar);
        footer = row(); status = label("", 12); status.setMaxLines(3);
        footer.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        stop = icon("Stop", NotebookIconDrawable.Icon.STOP, () -> { if (job.busy) job.cancel(); });
        footer.addView(stop, spaced(44, 42)); root.addView(footer); footer.setVisibility(View.GONE);
        setContentView(root); showSearch(searching, false);
    }

    @Override protected void onResume() {
        super.onResume(); resumed = true; job.observe(this::render);
        if (NotebookFileStorage.hasAccess(this) && !job.busy) job.refresh();
    }
    @Override protected void onPause() {
        resumed = false; job.observe(null);
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        if (actionBar != null) { actionBar.dismiss(); actionBar = null; }
        super.onPause();
    }
    @Override public Object onRetainNonConfigurationInstance() { return job; }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("saveName", saveName.getText().toString());
        state.putString("folder", job.folder); state.putString("query", job.query);
        state.putBoolean("searching", searching);
        state.putString("searchDraft", search.getText().toString()); state.putString("sort", job.sort.name());
        state.putBoolean("hidden", job.hidden); state.putBoolean("move", job.move); state.putBoolean("selecting", job.selecting);
        state.putStringArray("selected", job.selected.toArray(new String[0]));
        String[] clipboard = new String[job.clipboard.size()];
        for (int i = 0; i < clipboard.length; i++) clipboard[i] = job.clipboard.get(i).getAbsolutePath();
        state.putStringArray("clipboard", clipboard); super.onSaveInstanceState(state);
    }

    private void render() {
        if (!resumed) return;
        boolean access = NotebookFileStorage.hasAccess(this);
        if (job.busy && renderingBusy && access == renderingAccess) {
            // Progress updates change only text. Rebuilding a large directory for
            // every copy buffer notice would stall touch input and repaint e-ink.
            status.setText(job.message);
            if (rows.isEmpty()) empty.setText(access ? job.message : "");
            return;
        }
        renderingBusy = job.busy; renderingAccess = access;
        list.setNumColumns(NotebookCollectionLayout.columns(this, thumbnails));
        String presentationLabel = thumbnails ? "Show files as list" : "Show file thumbnails";
        presentation.setSymbol(thumbnails ? NotebookIconDrawable.Icon.OUTLINE : NotebookIconDrawable.Icon.OVERVIEW, presentationLabel);
        accessPanel.setVisibility(access ? View.GONE : View.VISIBLE);
        saveBar.setVisibility(access && "save".equals(pickerMode) ? View.VISIBLE : View.GONE);
        selectionBar.setVisibility(access && job.selecting ? View.VISIBLE : View.GONE);
        clipboardBar.setVisibility(access && !job.clipboard.isEmpty() ? View.VISIBLE : View.GONE);
        selectedCount.setText(job.selected.size() + " selected");
        clipboardCount.setText(job.clipboard.size() + (job.move ? " to move" : " to copy"));
        renderPath();
        up.setEnabled(!job.busy && job.folder != null); stop.setVisibility(job.busy ? View.VISIBLE : View.GONE);
        list.setEnabled(access && !job.busy);
        rows.clear();
        if (access && job.folder == null) {
            for (NotebookFileStorage.Volume volume : job.volumes) rows.add(new Row(volume.directory, volume.title,
                Formatter.formatFileSize(this, volume.directory.getUsableSpace()) + " free · " + volume.directory.getAbsolutePath(), true, true));
            if (!job.volumes.isEmpty()) {
                File primary = job.volumes.get(0).directory;
                String[] folders = {Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_DOCUMENTS, Environment.DIRECTORY_PICTURES,
                    Environment.DIRECTORY_DCIM, Environment.DIRECTORY_MUSIC, Environment.DIRECTORY_MOVIES};
                for (String name : folders) {
                    File folder = new File(primary, name);
                    if (folder.isDirectory()) rows.add(new Row(folder, name.equals(Environment.DIRECTORY_DOWNLOADS) ? "Downloads" : name,
                        "Internal storage", true, true));
                }
            }
        } else if (access) for (NotebookFiles.Entry entry : job.listing.entries) {
            if ("open".equals(pickerMode) && !entry.directory && !NotebookFilePicker.accepts(pickerFilter, entry.file.getName())) continue;
            String detail = (entry.directory ? "" : Formatter.formatFileSize(this, entry.bytes) + " · ")
                + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(entry.modified));
            if (!job.query.isEmpty()) detail += " · " + displayPath(entry.file.getParentFile());
            rows.add(new Row(entry.file, entry.file.getName(), detail, entry.directory, false));
        }
        adapter.notifyDataSetChanged();
        empty.setText(!access ? "" : job.busy ? job.message : job.error != null ? job.error
            : job.folder == null ? "No mounted storage found. Reconnect the drive and refresh."
            : job.query.isEmpty() ? "This folder is empty." : "No matching files in this folder or its subfolders.");
        String info = "";
        if (job.listing.limited) info = "Limit reached; narrow your search";
        if (job.listing.skipped > 0) info += (info.isEmpty() ? "" : " · ") + job.listing.skipped + " protected folders or links skipped";
        status.setText(!access ? "" : job.busy || job.error != null ? job.message : info);
        footer.setVisibility(access && (job.busy || job.error != null || !info.isEmpty()) ? View.VISIBLE : View.GONE);
        status.setOnClickListener(v -> { if (ready()) job.refresh(); });
    }

    private boolean ready() { return resumed && !job.busy && NotebookFileStorage.hasAccess(this); }
    private void requestStorage() {
        if (!resumed || job.busy) return;
        if (Build.VERSION.SDK_INT >= 30) {
            try { startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()))); }
            catch (ActivityNotFoundException unavailable) {
                try { startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)); }
                catch (ActivityNotFoundException missing) { problem("This Android version does not provide the All files access setting."); }
            }
        } else if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                || !getPreferences(MODE_PRIVATE).getBoolean("storageBlocked", false)) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, STORAGE_PERMISSION);
        } else {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == STORAGE_PERMISSION) {
            if (grants.length > 0) getPreferences(MODE_PRIVATE).edit().putBoolean("storageBlocked",
                !NotebookFileStorage.hasAccess(this) && !shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)).apply();
            if (resumed) { if (NotebookFileStorage.hasAccess(this)) job.refresh(); else render(); }
        }
    }

    private void navigate(File folder) {
        if (!ready()) return;
        job.folder = folder == null ? null : folder.getAbsolutePath(); job.query = ""; search.setText("");
        showSearch(false, false);
        job.selected.clear(); job.selecting = false; job.listing = new NotebookFiles.Listing(); job.refresh(); list.setSelection(0);
    }
    private void parent() {
        if (!ready() || job.folder == null) return;
        File folder = new File(job.folder);
        for (NotebookFileStorage.Volume volume : job.volumes) if (folder.equals(volume.directory)) { navigate(null); return; }
        navigate(folder.getParentFile());
    }
    private String displayPath(File file) {
        for (NotebookFileStorage.Volume volume : job.volumes) if (file.toPath().startsWith(volume.directory.toPath())) {
            String relative = volume.directory.toPath().relativize(file.toPath()).toString();
            return volume.title + (relative.isEmpty() ? "" : " / " + relative.replace(File.separator, " / "));
        }
        return file.getAbsolutePath();
    }
    private void renderPath() {
        String currentPath = job.folder == null ? "" : job.folder;
        StringBuilder signature = new StringBuilder(currentPath);
        for (NotebookFileStorage.Volume volume : job.volumes)
            signature.append('\0').append(volume.directory).append('\0').append(volume.title);
        if (signature.toString().equals(renderedPath)) return;
        renderedPath = signature.toString();
        pathParts.removeAllViews();
        if (job.folder == null) { addPathPart("Storage locations", null, true); return; }
        File current = new File(job.folder);
        for (NotebookFileStorage.Volume volume : job.volumes) if (current.toPath().startsWith(volume.directory.toPath())) {
            File segment = volume.directory;
            addPathPart(volume.title, segment, segment.equals(current));
            for (java.nio.file.Path name : volume.directory.toPath().relativize(current.toPath())) {
                if (name.toString().isEmpty()) continue;
                segment = new File(segment, name.toString());
                addPathPart(name.toString(), segment, segment.equals(current));
            }
            path.post(() -> path.fullScroll(View.FOCUS_RIGHT));
            return;
        }
        addPathPart(current.getAbsolutePath(), current, true);
        path.post(() -> path.fullScroll(View.FOCUS_RIGHT));
    }

    private void addPathPart(String name, File destination, boolean current) {
        if (pathParts.getChildCount() > 0) {
            TextView separator = label("›", 18); separator.setGravity(Gravity.CENTER); separator.setTextColor(0xff777777);
            pathParts.addView(separator, new LinearLayout.LayoutParams(dp(18), -1));
        }
        TextView part = label(name, 13); part.setSingleLine(true); part.setEllipsize(TextUtils.TruncateAt.END);
        part.setMaxWidth(dp(180)); part.setPadding(dp(4), 0, dp(4), 0);
        if (current) part.setTypeface(null, Typeface.BOLD);
        part.setContentDescription(destination == null ? "Storage locations" : "Go to " + displayPath(destination));
        part.setOnClickListener(v -> navigate(destination));
        pathParts.addView(part, new LinearLayout.LayoutParams(-2, -1));
    }
    private void find() {
        if (!ready()) return;
        if (job.folder == null) {
            if (job.volumes.isEmpty()) return;
            job.folder = job.volumes.get(0).directory.getAbsolutePath();
        }
        job.query = search.getText().toString().trim(); job.selected.clear(); job.selecting = false;
        InputMethodManager keyboard = getSystemService(InputMethodManager.class);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
        search.clearFocus(); job.refresh();
    }

    private void showSearch(boolean visible, boolean focus) {
        searching = visible;
        path.setVisibility(visible ? View.GONE : View.VISIBLE);
        search.setVisibility(visible ? View.VISIBLE : View.GONE);
        up.setVisibility(visible ? View.GONE : View.VISIBLE);
        homeButton.setVisibility(visible ? View.GONE : View.VISIBLE);
        presentation.setVisibility(visible ? View.GONE : View.VISIBLE);
        optionsButton.setVisibility(visible ? View.GONE : View.VISIBLE);
        searchClose.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible && focus) {
            search.requestFocus(); search.post(() -> {
                InputMethodManager keyboard = getSystemService(InputMethodManager.class);
                if (searching && keyboard != null) keyboard.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT);
            });
        } else if (!visible) {
            InputMethodManager keyboard = getSystemService(InputMethodManager.class);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(search.getWindowToken(), 0);
            search.clearFocus();
        }
    }

    private void closeSearch() {
        if (!resumed || job.busy) return;
        boolean filtered = !job.query.isEmpty();
        search.setText(""); job.query = ""; showSearch(false, false);
        if (filtered && NotebookFileStorage.hasAccess(this)) job.refresh();
    }

    private void options() {
        if (!ready()) return;
        if (picking()) {
            String[] actions = {"New folder", "Sort by…", job.hidden ? "Hide hidden files" : "Show hidden files", "Refresh"};
            show(new AlertDialog.Builder(this).setTitle("Files").setItems(actions, (d, which) -> {
                if (which == 0) { if (job.folder == null) problem("Open a storage location first."); else editName(null); }
                else if (which == 1) sort(); else if (which == 2) { job.hidden = !job.hidden; job.refresh(); } else job.refresh();
            }).setNegativeButton("Close", null).create()); return;
        }
        String[] choices = {"New folder", "Select files", "Sort by…", job.hidden ? "Hide hidden files" : "Show hidden files", "Refresh"};
        show(new AlertDialog.Builder(this).setTitle("Files").setItems(choices, (d, which) -> {
            if (which == 0) { if (job.folder == null) problem("Open a storage location first."); else editName(null); }
            else if (which == 1) { if (job.folder != null) { job.selecting = true; render(); } }
            else if (which == 2) sort();
            else if (which == 3) { job.hidden = !job.hidden; job.refresh(); }
            else job.refresh();
        }).setNegativeButton("Close", null).create());
    }
    private void sort() {
        show(new AlertDialog.Builder(this).setTitle("Sort files · folders first")
            .setSingleChoiceItems(new String[]{"Name (A–Z)", "Date (newest first)", "Size (largest first)", "File type"}, job.sort.ordinal(), (d, which) -> {
                d.dismiss(); job.sort = NotebookFiles.Sort.values()[which]; job.refresh();
            }).setNegativeButton("Cancel", null).create());
    }
    private void open(Row item) {
        if (!ready()) return;
        if (job.selecting && !item.root) { toggle(item); return; }
        if (item.directory) navigate(item.file);
        else if ("open".equals(pickerMode)) {
            try { setResult(RESULT_OK, new Intent().setData(NotebookTransferProvider.uri(this, item.file))); finish(); overridePendingTransition(0, 0); }
            catch (IOException | SecurityException error) { problem(error.getMessage()); }
        } else if ("save".equals(pickerMode)) {
            Toast.makeText(this, "Choose a new file name below", Toast.LENGTH_SHORT).show();
        } else if (NotebookFileStorage.mime(item.file).equals("application/pdf")) {
            try {
                startActivity(new Intent(this, NotebookPdfImportActivity.class).setAction(Intent.ACTION_VIEW)
                    .setDataAndType(NotebookFileProvider.uri(this, item.file), "application/pdf"));
                overridePendingTransition(0, 0);
            } catch (IOException | SecurityException error) { problem(error.getMessage()); }
        } else if (NotebookEbookStore.supported(item.file.getName())) {
            try {
                startActivity(new Intent(this, NotebookEbookImportActivity.class).setAction(Intent.ACTION_VIEW)
                    .setDataAndType(NotebookFileProvider.uri(this, item.file), NotebookFileStorage.mime(item.file)));
                overridePendingTransition(0,0);
            } catch(IOException | SecurityException error) { problem(error.getMessage()); }
        } else openWith(item.file);
    }
    private void fileActions(Row item, View anchor) {
        if (!ready() || item.root) return;
        if (job.selecting) { toggle(item); return; }
        List<File> files = java.util.Collections.singletonList(item.file);
        List<NotebookActionBar.Action> actions = new ArrayList<>();
        if (!item.directory) {
            actions.add(NotebookActionBar.action(NotebookIconDrawable.Icon.READING, "Open", () -> open(item)));
            actions.add(NotebookActionBar.action(NotebookIconDrawable.Icon.OPEN_FILE, "Open with…", () -> openWith(item.file)));
            actions.add(NotebookActionBar.action(NotebookIconDrawable.Icon.SHARE, "Share", () -> share(files)));
        }
        actions.add(NotebookActionBar.action(NotebookIconDrawable.Icon.COPY, "Copy", () -> clipboard(files, false)));
        actions.add(NotebookActionBar.action(NotebookIconDrawable.Icon.CUT, "Move", () -> clipboard(files, true)));
        actions.add(NotebookActionBar.action(NotebookIconDrawable.Icon.PEN, "Rename", () -> editName(item.file)));
        actions.add(NotebookActionBar.action(NotebookIconDrawable.Icon.DELETE, "Delete…", () -> confirmDelete(files)));
        actions.add(NotebookActionBar.action(NotebookIconDrawable.Icon.INFORMATION, "Details", () -> details(item)));
        actions.add(NotebookActionBar.action(NotebookIconDrawable.Icon.CHECK, "Select", () -> toggle(item)));
        showActions(anchor, actions.toArray(new NotebookActionBar.Action[0]));
    }
    private void showActions(View anchor, NotebookActionBar.Action... actions) {
        if (actionBar != null) actionBar.dismiss();
        actionBar = NotebookActionBar.show(anchor, actions);
    }
    private View itemActions(Row item) {
        NotebookBrowserIconButton more = icon("Actions for " + item.title, NotebookIconDrawable.Icon.MORE, () -> {});
        more.setOnClickListener(v -> fileActions(item, v)); return more;
    }
    private void toggle(Row item) {
        if (!ready() || item.root || picking()) return;
        job.selecting = true; String path = item.file.getAbsolutePath();
        if (!job.selected.remove(path)) job.selected.add(path); render();
    }
    private void selectAll() {
        if (!ready()) return;
        for (Row item : rows) if (!item.root) job.selected.add(item.file.getAbsolutePath()); render();
    }
    private List<File> selectedFiles() {
        List<File> result = new ArrayList<>(); for (String path : job.selected) result.add(new File(path)); return result;
    }
    private void selectionActions() {
        if (!ready() || job.selected.isEmpty()) return;
        List<File> files = selectedFiles();
        showActions(selectedActions,
            NotebookActionBar.action(NotebookIconDrawable.Icon.COPY, "Copy", () -> clipboard(files, false)),
            NotebookActionBar.action(NotebookIconDrawable.Icon.CUT, "Move", () -> clipboard(files, true)),
            NotebookActionBar.action(NotebookIconDrawable.Icon.SHARE, "Share files", () -> share(files)),
            NotebookActionBar.action(NotebookIconDrawable.Icon.DELETE, "Delete…", () -> confirmDelete(files)));
    }
    private void clipboard(List<File> files, boolean move) {
        job.clipboard.clear(); job.clipboard.addAll(files); job.move = move; job.selected.clear(); job.selecting = false; render();
        Toast.makeText(this, "Open the destination folder, then tap Paste here", Toast.LENGTH_LONG).show();
    }
    private void paste() {
        if (!ready() || job.clipboard.isEmpty()) return;
        if (job.folder == null || !job.query.isEmpty()) { problem("Open the destination folder and clear search before pasting."); return; }
        List<File> files = new ArrayList<>(job.clipboard); File destination = new File(job.folder); boolean move = job.move;
        // Clear only for paste attempts. Creating a destination folder while a
        // clipboard is pending must leave those copied/moved items available.
        job.clipboard.clear();
        job.start(move ? "Moving…" : "Copying…", (storage, control) -> storage.transfer(files, destination, move, control));
    }
    private void confirmDelete(List<File> files) {
        String names = files.size() == 1 ? "“" + files.get(0).getName() + "”" : files.size() + " selected items";
        show(new AlertDialog.Builder(this).setTitle("Delete permanently?")
            .setMessage("Delete " + names + " from tablet storage? Folders include all their contents, including hidden files. This cannot be undone. These files do not go to the notebook Trash.")
            .setNegativeButton("Cancel", null).setPositiveButton("Delete permanently", (d, which) -> {
                if (ready()) job.start("Deleting…", (storage, control) -> storage.delete(files, control));
            }).create());
    }
    private void editName(File file) {
        if (!ready() || job.folder == null) return;
        EditText name = new EditText(this); name.setSingleLine(true); name.setTextSize(16);
        name.setInputType(android.text.InputType.TYPE_CLASS_TEXT); if (file != null) name.setText(file.getName());
        name.setSelectAllOnFocus(true); name.setHint(file == null ? "Folder name" : "File name");
        LinearLayout content = new LinearLayout(this); content.setPadding(dp(20), dp(8), dp(20), 0);
        content.addView(name, new LinearLayout.LayoutParams(-1, dp(52)));
        AlertDialog editor = new AlertDialog.Builder(this).setTitle(file == null ? "New folder" : "Rename")
            .setView(content).setNegativeButton("Cancel", null).setPositiveButton(file == null ? "Create" : "Rename", null).create();
        show(editor);
        editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!ready()) return;
            String value = name.getText().toString();
            try { NotebookFiles.name(value); }
            catch (IOException error) { name.setError(error.getMessage()); return; }
            File parent = new File(job.folder); editor.dismiss();
            job.start(file == null ? "Creating folder…" : "Renaming…", (storage, control) -> {
                if (file == null) storage.createFolder(parent, value); else storage.rename(file, value);
            });
        });
    }
    private void details(Row item) {
        String message = "Name: " + item.file.getName() + "\n\nLocation: " + item.file.getAbsolutePath()
            + "\n\nType: " + (item.directory ? "Folder" : NotebookFileStorage.mime(item.file))
            + (item.directory ? "" : "\n\nSize: " + Formatter.formatFileSize(this, item.file.length()) + " (" + item.file.length() + " bytes)")
            + "\n\nModified: " + DateFormat.getDateTimeInstance().format(new Date(item.file.lastModified()))
            + "\n\n" + (item.file.canWrite() ? "Writable" : "Read-only");
        show(new AlertDialog.Builder(this).setTitle("Details").setMessage(message).setPositiveButton("Done", null).create());
    }
    private void openWith(File file) {
        try {
            Uri uri = NotebookFileProvider.uri(this, file);
            Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, NotebookFileStorage.mime(file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri(file.getName(), uri));
            startActivity(Intent.createChooser(intent, "Open with"));
        } catch (ActivityNotFoundException unavailable) { problem("No installed app can open this file type."); }
        catch (IOException | SecurityException error) { problem(error.getMessage()); }
    }
    private void share(List<File> files) {
        try {
            if (files.size() > 100) throw new IOException("Share up to 100 files at a time.");
            ArrayList<Uri> uris = new ArrayList<>(); String type = null;
            for (File file : files) {
                if (file.isDirectory()) throw new IOException("Share individual files. Folders can be copied or moved.");
                uris.add(NotebookFileProvider.uri(this, file));
                String current = NotebookFileStorage.mime(file); type = type == null ? current : type.equals(current) ? type : "*/*";
            }
            if (uris.isEmpty()) return;
            ClipData clip = ClipData.newRawUri("Files", uris.get(0));
            for (int i = 1; i < uris.size(); i++) clip.addItem(new ClipData.Item(uris.get(i)));
            Intent send = new Intent(uris.size() == 1 ? Intent.ACTION_SEND : Intent.ACTION_SEND_MULTIPLE)
                .setType(type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(clip);
            if (uris.size() == 1) send.putExtra(Intent.EXTRA_STREAM, uris.get(0)); else send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            startActivity(Intent.createChooser(send, "Share files"));
        } catch (ActivityNotFoundException unavailable) { problem("No installed app can share these files."); }
        catch (IOException | SecurityException error) { problem(error.getMessage()); }
    }

    private final class FileAdapter extends BaseAdapter {
        @Override public int getCount() { return rows.size(); }
        @Override public Object getItem(int position) { return rows.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View recycled, ViewGroup parent) {
            if (thumbnails) return thumbnail(rows.get(position));
            Row item = rows.get(position); LinearLayout row = row();
            row.setPadding(dp(8), dp(6), dp(6), dp(6)); row.setMinimumHeight(dp(66));
            row.setBackground(NotebookUi.rounded(getResources().getDisplayMetrics().density, 6));
            if (job.selecting && !item.root) {
                CheckBox check = new CheckBox(NotebookFilesActivity.this); check.setChecked(job.selected.contains(item.file.getAbsolutePath()));
                check.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.BLACK));
                check.setContentDescription("Select " + item.title); check.setOnClickListener(v -> toggle(item));
                row.addView(check, new LinearLayout.LayoutParams(dp(44), dp(44)));
            }
            LinearLayout info = new LinearLayout(NotebookFilesActivity.this); info.setOrientation(LinearLayout.VERTICAL);
            ImageView type = new ImageView(NotebookFilesActivity.this);
            type.setImageDrawable(new NotebookFileTypeDrawable(item.file.getName(), item.directory));
            type.setContentDescription(item.directory ? "Folder" : NotebookFileTypes.label(item.file.getName()) + " file");
            type.setOnClickListener(v -> open(item)); type.setOnLongClickListener(v -> { toggle(item); return true; });
            LinearLayout.LayoutParams typeSpace = new LinearLayout.LayoutParams(dp(38), dp(42)); typeSpace.setMarginEnd(dp(10)); row.addView(type, typeSpace);
            TextView name = label(item.title, 16); name.setMaxLines(2); name.setEllipsize(TextUtils.TruncateAt.END);
            info.addView(name); TextView detail = label(item.detail, 12); detail.setSingleLine(true); detail.setEllipsize(TextUtils.TruncateAt.MIDDLE); info.addView(detail);
            info.setOnClickListener(v -> open(item)); info.setOnLongClickListener(v -> { toggle(item); return true; });
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            if (!item.root && !picking()) row.addView(itemActions(item), new LinearLayout.LayoutParams(dp(44), dp(44)));
            return row;
        }

        private View thumbnail(Row item) {
            LinearLayout card = new LinearLayout(NotebookFilesActivity.this); card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(7), dp(7), dp(7), dp(7));
            card.setBackground(NotebookUi.rounded(getResources().getDisplayMetrics().density, 7));
            card.setOnClickListener(v -> open(item)); card.setOnLongClickListener(v -> { toggle(item); return true; });
            card.addView(new NotebookFileThumbnail(NotebookFilesActivity.this, item.file, item.directory), new LinearLayout.LayoutParams(-1, dp(108)));
            LinearLayout footer = row();
            if (job.selecting && !item.root) {
                CheckBox check = new CheckBox(NotebookFilesActivity.this); check.setChecked(job.selected.contains(item.file.getAbsolutePath()));
                check.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.BLACK));
                check.setContentDescription("Select " + item.title); check.setOnClickListener(v -> toggle(item));
                footer.addView(check, new LinearLayout.LayoutParams(dp(40), dp(44)));
            }
            TextView name = label(item.title, 14); name.setMaxLines(2); name.setEllipsize(TextUtils.TruncateAt.END);
            name.setOnClickListener(v -> open(item)); name.setOnLongClickListener(v -> { toggle(item); return true; });
            footer.addView(name, new LinearLayout.LayoutParams(0, dp(48), 1));
            if (!item.root && !picking()) footer.addView(itemActions(item), new LinearLayout.LayoutParams(dp(40), dp(40)));
            card.addView(footer);
            TextView detail = label(item.detail, 11); detail.setSingleLine(true); detail.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(detail, new LinearLayout.LayoutParams(-1, dp(20))); return card;
        }
    }
    private boolean picking() { return "open".equals(pickerMode) || "save".equals(pickerMode); }
    private void saveHere() {
        if (!ready()) return;
        if (job.folder == null || !job.query.isEmpty()) { problem("Open a destination folder and clear search first."); return; }
        try {
            String name = NotebookFiles.name(saveName.getText().toString());
            NotebookFiles files = NotebookFileStorage.files(this);
            File file = files.reserveFile(new File(job.folder), name);
            try { setResult(RESULT_OK, new Intent().setData(NotebookTransferProvider.uri(this, file))); }
            catch (IOException | SecurityException error) { file.delete(); throw error; }
            finish(); overridePendingTransition(0, 0);
        } catch (IOException | SecurityException error) { problem(error.getMessage()); }
    }
    private void bookshelf() {
        if (job.busy) { Toast.makeText(this, "Wait for the operation or tap Stop", Toast.LENGTH_SHORT).show(); return; }
        finish(); overridePendingTransition(0, 0);
    }
    @Override public void onBackPressed() {
        if (job.busy) { job.cancel(); return; }
        if (job.selecting) { job.selecting = false; job.selected.clear(); render(); }
        else if (searching || !job.query.isEmpty()) closeSearch();
        else if (job.folder != null && NotebookFileStorage.hasAccess(this)) parent();
        else { super.onBackPressed(); overridePendingTransition(0, 0); }
    }
    private void problem(String message) {
        if (resumed) show(new AlertDialog.Builder(this).setTitle("Files").setMessage(message == null ? "Could not access this file." : message).setPositiveButton("Done", null).create());
    }
    private void show(AlertDialog value) {
        if (dialog != null) dialog.dismiss(); dialog = value; NotebookUi.show(value);
    }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private LinearLayout.LayoutParams spaced(int width, int height) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(width), dp(height)); size.setMarginStart(dp(6)); return size;
    }
    private NotebookBrowserIconButton icon(String description, NotebookIconDrawable.Icon icon, Runnable action) {
        return new NotebookBrowserIconButton(this, icon, description, action);
    }
    private NotebookButton button(String text, NotebookIconDrawable.Icon icon, Runnable action) {
        NotebookButton view = new NotebookButton(this); view.setText(text); view.setTextSize(12);
        NotebookUi.action(view, icon); view.setOnClickListener(v -> action.run()); return view;
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(Color.BLACK); view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
