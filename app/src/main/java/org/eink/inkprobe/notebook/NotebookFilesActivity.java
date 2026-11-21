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
    private TextView path, status, empty, selectedCount, clipboardCount;
    private EditText search;
    private LinearLayout selectionBar, clipboardBar, accessPanel;
    private NotebookButton up, stop, presentation;
    private GridView list;
    private AlertDialog dialog;
    private boolean resumed, renderingBusy, renderingAccess, thumbnails;
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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setWindowAnimations(0);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE); root.setPadding(dp(16), dp(10), dp(16), dp(8));
        LinearLayout header = row();
        header.addView(button("Bookshelf", NotebookIconDrawable.Icon.LIBRARY, this::bookshelf), new LinearLayout.LayoutParams(dp(116), dp(42)));
        TextView title = label("Files", 21); title.setTypeface(null, Typeface.BOLD); title.setPadding(dp(12), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        header.addView(deviceSettingsIsland(), new LinearLayout.LayoutParams(-2, dp(32))); root.addView(header);

        LinearLayout navigation = row();
        up = icon("Parent folder", NotebookIconDrawable.Icon.PREVIOUS, this::parent);
        navigation.addView(up, new LinearLayout.LayoutParams(dp(44), dp(42)));
        navigation.addView(icon("Storage locations", NotebookIconDrawable.Icon.HOME, () -> navigate(null)), spaced(44, 42));
        path = label("Storage locations", 14); path.setSingleLine(true); path.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        path.setPadding(dp(8), 0, dp(8), 0); path.setOnClickListener(v -> ancestors());
        path.setContentDescription("Current folder. Tap to navigate to a parent folder.");
        navigation.addView(path, new LinearLayout.LayoutParams(0, dp(44), 1));
        presentation = icon("Show file thumbnails", NotebookIconDrawable.Icon.OVERVIEW, () -> {
            if (!ready()) return;
            thumbnails = !thumbnails; NotebookCollectionLayout.setThumbnails(this, "files", thumbnails); render();
        });
        navigation.addView(presentation, spaced(44, 42));
        navigation.addView(icon("File options", NotebookIconDrawable.Icon.MORE, this::options), spaced(44, 42));
        root.addView(navigation);

        LinearLayout find = row(); search = new EditText(this); search.setTextSize(14); search.setSingleLine(true);
        search.setTextColor(Color.BLACK); search.setHintTextColor(Color.DKGRAY); search.setHint("Search this folder and subfolders");
        search.setContentDescription("Search files by name"); search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setText(saved == null ? job.query : saved.getString("searchDraft", job.query));
        search.setOnEditorActionListener((view, action, event) -> { if (action == EditorInfo.IME_ACTION_SEARCH) { find(); return true; } return false; });
        find.addView(search, new LinearLayout.LayoutParams(0, dp(46), 1));
        find.addView(icon("Search files", NotebookIconDrawable.Icon.SEARCH, this::find), spaced(44, 42));
        find.addView(icon("Clear search", NotebookIconDrawable.Icon.CLOSE, () -> { if (ready()) { search.setText(""); job.query = ""; job.refresh(); } }), spaced(44, 42));
        root.addView(find);

        selectionBar = row(); selectedCount = label("", 13);
        selectionBar.addView(selectedCount, new LinearLayout.LayoutParams(0, dp(42), 1));
        selectionBar.addView(button("Select all", null, this::selectAll), spaced(92, 42));
        selectionBar.addView(button("Actions", null, this::selectionActions), spaced(84, 42));
        selectionBar.addView(button("Cancel", null, () -> { if (ready()) { job.selecting = false; job.selected.clear(); render(); } }), spaced(78, 42));
        root.addView(selectionBar);

        clipboardBar = row(); clipboardCount = label("", 13);
        clipboardBar.addView(clipboardCount, new LinearLayout.LayoutParams(0, dp(44), 1));
        clipboardBar.addView(button("Paste here", NotebookIconDrawable.Icon.PASTE, this::paste), spaced(116, 42));
        clipboardBar.addView(button("Cancel", null, () -> { if (ready()) { job.clipboard.clear(); render(); } }), spaced(78, 42));
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
        LinearLayout footer = row(); status = label("", 12); status.setMaxLines(3);
        footer.addView(status, new LinearLayout.LayoutParams(0, dp(58), 1));
        stop = button("Stop", NotebookIconDrawable.Icon.STOP, () -> { if (job.busy) job.cancel(); });
        footer.addView(stop, new LinearLayout.LayoutParams(dp(80), dp(42))); root.addView(footer);
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume(); resumed = true; job.observe(this::render);
        if (NotebookFileStorage.hasAccess(this) && !job.busy) job.refresh();
    }
    @Override protected void onPause() {
        resumed = false; job.observe(null);
        if (dialog != null) { dialog.dismiss(); dialog = null; }
        super.onPause();
    }
    @Override public Object onRetainNonConfigurationInstance() { return job; }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("folder", job.folder); state.putString("query", job.query);
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
        NotebookUi.action(presentation, thumbnails ? NotebookIconDrawable.Icon.OUTLINE : NotebookIconDrawable.Icon.OVERVIEW);
        presentation.setContentDescription(presentationLabel); presentation.setTooltipText(presentationLabel);
        accessPanel.setVisibility(access ? View.GONE : View.VISIBLE);
        selectionBar.setVisibility(access && job.selecting ? View.VISIBLE : View.GONE);
        clipboardBar.setVisibility(access && !job.clipboard.isEmpty() ? View.VISIBLE : View.GONE);
        selectedCount.setText(job.selected.size() + " selected");
        clipboardCount.setText(job.clipboard.size() + (job.move ? " to move" : " to copy"));
        path.setText(job.folder == null ? "Storage locations" : displayPath(new File(job.folder)));
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
            String detail = (entry.directory ? "Folder" : Formatter.formatFileSize(this, entry.bytes))
                + " · " + DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(entry.modified));
            if (!job.query.isEmpty()) detail += " · " + displayPath(entry.file.getParentFile());
            rows.add(new Row(entry.file, entry.file.getName(), detail, entry.directory, false));
        }
        adapter.notifyDataSetChanged();
        empty.setText(!access ? "" : job.busy ? job.message : job.error != null ? job.error
            : job.folder == null ? "No mounted storage found. Reconnect the drive and refresh."
            : job.query.isEmpty() ? "This folder is empty." : "No matching files in this folder or its subfolders.");
        String info = job.folder == null ? "Choose storage or a common folder" : rows.size() + " items · Hold to select · ⋮ for actions";
        if (!job.query.isEmpty()) info = rows.size() + " matches for “" + job.query + "” · This folder and subfolders";
        if (job.listing.limited) info += " · Limit reached; narrow your search";
        if (job.listing.skipped > 0) info += " · " + job.listing.skipped + " protected folders or links skipped";
        status.setText(!access ? "Storage access is only needed for Files." : job.busy || job.error != null ? job.message : info);
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
    private void ancestors() {
        if (!ready() || job.folder == null) return;
        List<File> paths = new ArrayList<>(); File current = new File(job.folder);
        for (NotebookFileStorage.Volume volume : job.volumes) if (current.toPath().startsWith(volume.directory.toPath())) {
            while (current != null && current.toPath().startsWith(volume.directory.toPath())) { paths.add(current); current = current.getParentFile(); }
            break;
        }
        String[] names = new String[paths.size()]; for (int i = 0; i < names.length; i++) names[i] = displayPath(paths.get(i));
        show(new AlertDialog.Builder(this).setTitle("Go to folder").setItems(names, (d, which) -> navigate(paths.get(which)))
            .setNegativeButton("Close", null).create());
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

    private void options() {
        if (!ready()) return;
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
        else if (NotebookFileStorage.mime(item.file).equals("application/pdf")) {
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
    private void fileActions(Row item) {
        if (!ready() || item.root) return;
        if (job.selecting) { toggle(item); return; }
        List<String> choices = new ArrayList<>();
        if (!item.directory) { choices.add("Open"); choices.add("Open with…"); choices.add("Share"); }
        choices.add("Copy"); choices.add("Move"); choices.add("Rename"); choices.add("Delete…"); choices.add("Details"); choices.add("Select");
        show(new AlertDialog.Builder(this).setTitle(item.title).setItems(choices.toArray(new String[0]), (d, which) -> {
            String action = choices.get(which);
            List<File> files = java.util.Collections.singletonList(item.file);
            if (action.equals("Open")) open(item);
            else if (action.equals("Open with…")) openWith(item.file);
            else if (action.equals("Share")) share(files);
            else if (action.equals("Copy") || action.equals("Move")) clipboard(files, action.equals("Move"));
            else if (action.equals("Rename")) editName(item.file);
            else if (action.equals("Delete…")) confirmDelete(files);
            else if (action.equals("Details")) details(item);
            else toggle(item);
        }).setNegativeButton("Cancel", null).create());
    }
    private void toggle(Row item) {
        if (!ready() || item.root) return;
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
        show(new AlertDialog.Builder(this).setTitle(files.size() + " selected").setItems(new String[]{"Copy", "Move", "Share files", "Delete…"}, (d, which) -> {
            if (which <= 1) clipboard(files, which == 1);
            else if (which == 2) share(files); else confirmDelete(files);
        }).setNegativeButton("Cancel", null).create());
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
            TextView name = label(item.title, 16); name.setMaxLines(2); name.setEllipsize(TextUtils.TruncateAt.END);
            NotebookIconDrawable symbol = new NotebookIconDrawable(item.directory ? NotebookIconDrawable.Icon.FOLDER : NotebookIconDrawable.Icon.READING);
            symbol.setBounds(0, 0, dp(22), dp(22)); name.setCompoundDrawablesRelative(symbol, null, null, null); name.setCompoundDrawablePadding(dp(8));
            info.addView(name); TextView detail = label(item.detail, 12); detail.setSingleLine(true); detail.setEllipsize(TextUtils.TruncateAt.MIDDLE); info.addView(detail);
            info.setOnClickListener(v -> open(item)); info.setOnLongClickListener(v -> { toggle(item); return true; });
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
            if (!item.root) row.addView(icon("Actions for " + item.title, NotebookIconDrawable.Icon.MORE, () -> fileActions(item)), new LinearLayout.LayoutParams(dp(44), dp(44)));
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
            if (!item.root) footer.addView(icon("Actions for " + item.title, NotebookIconDrawable.Icon.MORE, () -> fileActions(item)), new LinearLayout.LayoutParams(dp(40), dp(40)));
            card.addView(footer);
            TextView detail = label(item.detail, 11); detail.setSingleLine(true); detail.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(detail, new LinearLayout.LayoutParams(-1, dp(20))); return card;
        }
    }
    private void bookshelf() {
        if (job.busy) { Toast.makeText(this, "Wait for the operation or tap Stop", Toast.LENGTH_SHORT).show(); return; }
        finish(); overridePendingTransition(0, 0);
    }
    @Override public void onBackPressed() {
        if (job.busy) { job.cancel(); return; }
        if (job.selecting) { job.selecting = false; job.selected.clear(); render(); }
        else if (!job.query.isEmpty()) { job.query = ""; search.setText(""); job.refresh(); }
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
    private NotebookButton icon(String description, NotebookIconDrawable.Icon icon, Runnable action) {
        NotebookButton view = button("", icon, action); view.setContentDescription(description); view.setTooltipText(description); return view;
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
