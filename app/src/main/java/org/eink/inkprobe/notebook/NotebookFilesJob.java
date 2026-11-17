package org.eink.inkprobe.notebook;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.format.Formatter;
import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Retained owner of one file operation; rotation/pause detaches the UI without restarting IO. */
final class NotebookFilesJob {
    interface Work { void run(NotebookFiles files, NotebookFiles.Control control) throws IOException; }
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(r -> new Thread(r, "tablet-files"));
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Context context;
    private Runnable observer;
    private volatile boolean cancelled;
    private long lastProgress;
    boolean busy, selecting, move, hidden;
    String folder, query = "", message = "", error;
    NotebookFiles.Sort sort = NotebookFiles.Sort.NAME;
    NotebookFiles.Listing listing = new NotebookFiles.Listing();
    List<NotebookFileStorage.Volume> volumes = new ArrayList<>();
    final Set<String> selected = new LinkedHashSet<>();
    final List<File> clipboard = new ArrayList<>();

    NotebookFilesJob(Context context) { this.context = context.getApplicationContext(); }
    void observe(Runnable value) { observer = value; if (value != null) value.run(); }
    void cancel() { cancelled = true; message = "Stopping after the current safe step…"; changed(); }
    private void changed() { if (observer != null) observer.run(); }

    void refresh() { start("Reading files…", null); }
    void start(String title, Work work) {
        if (busy) return;
        busy = true; cancelled = false; error = null; message = title; lastProgress = 0; changed();
        final String requestedFolder = folder, requestedQuery = query;
        final boolean requestedHidden = hidden;
        final NotebookFiles.Sort requestedSort = sort;
        IO.execute(() -> {
            IOException failure = null;
            NotebookFiles.Listing entries = null;
            List<NotebookFileStorage.Volume> mounted = new ArrayList<>();
            NotebookFiles files = null;
            String path = requestedFolder;
            NotebookFiles.Control control = new NotebookFiles.Control() {
                @Override public void check() throws InterruptedIOException {
                    if (cancelled) throw new InterruptedIOException("Operation cancelled. Completed items are kept.");
                }
                @Override public void progress(String name, long bytes) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastProgress < 200) return;
                    lastProgress = now;
                    String progress = title + " " + name + (bytes >= 0 ? " · " + Formatter.formatFileSize(context, bytes) : "");
                    main.post(() -> { if (busy && !cancelled) { message = progress; changed(); } });
                }
            };
            try {
                files = NotebookFileStorage.files(context);
                mounted = NotebookFileStorage.volumes(context);
                if (work != null) work.run(files, control);
            } catch (IOException | SecurityException problem) {
                failure = problem instanceof IOException ? (IOException) problem : new IOException("Storage access was denied.", problem);
            } catch (RuntimeException problem) { failure = new IOException("Could not complete the file operation.", problem); }
            // A failed or cancelled mutation can have completed some items. Always
            // display the actual remaining files, while retaining the operation error.
            if (files != null && path != null && (failure == null || work != null)) {
                try { entries = files.list(new File(path), requestedQuery, requestedHidden, requestedSort, failure == null ? control : NotebookFiles.NONE); }
                catch (IOException | SecurityException problem) {
                    if (failure == null) failure = new IOException("Could not read the folder: " + problem.getMessage(), problem);
                }
            }
            final IOException problem = failure;
            final NotebookFiles.Listing result = entries;
            final List<NotebookFileStorage.Volume> roots = mounted;
            main.post(() -> {
                busy = false; volumes = roots;
                if (result != null) {
                    listing = result;
                    Set<String> available = new LinkedHashSet<>();
                    for (NotebookFiles.Entry entry : listing.entries) available.add(entry.file.getAbsolutePath());
                    selected.retainAll(available);
                } else listing = new NotebookFiles.Listing();
                if (work != null) {
                    selected.clear(); selecting = false;
                }
                error = problem == null ? null : problem.getMessage();
                message = problem == null ? (work == null ? "" : "Done") : error;
                changed();
            });
        });
    }
}
