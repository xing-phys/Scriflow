package org.eink.inkprobe.notebook;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** A read-only export job survives the picker and Activity recreation independently of ink. */
final class NotebookPdfExport {
    enum State { PREPARING, READY, SAVING, SAVED, FAILED, CANCELLED }
    interface Listener { void changed(); }
    private static final String PREFIX = "notebook-export-";
    private static final ExecutorService files = Executors.newSingleThreadExecutor();
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean notificationPending = new AtomicBoolean();
    final int notebookId, pageCount;
    final String notebookName;
    private volatile State state = State.PREPARING;
    private volatile int completed;
    private volatile String error;
    private volatile File preparedFile;
    private volatile boolean cancelled;
    private Listener listener;

    NotebookPdfExport(Context context, int notebookId, String name, int pageCount) {
        this.context = context.getApplicationContext();
        this.notebookId = notebookId;
        this.notebookName = name;
        this.pageCount = pageCount;
    }

    State state() { return state; }
    int completedPages() { return completed; }
    String error() { return error; }
    File readyFile() { return preparedFile; }
    void observe(Listener observer) { listener = observer; observer.changed(); }
    void detach(Listener observer) { if (listener == observer) listener = null; }

    /** Called on the export worker; immutable source pages cross a short writer barrier. */
    void prepare(NotebookPdf.PageSource source) {
        prepare(source, null, Collections.emptyList());
    }

    void fail(IOException failure) {
        error = "Couldn’t prepare the PDF. " + detail(failure);
        state = State.FAILED;
        changed();
    }

    void prepare(NotebookPdf.PageSource source, File originalPdf, List<NotebookOutline.Entry> outline) {
        File staging = null;
        try {
            checkCancelled();
            NotebookPdfEngine.initialize(context);
            staging = File.createTempFile(PREFIX, ".pdf", context.getCacheDir());
            try (FileOutputStream raw = new FileOutputStream(staging);
                    BufferedOutputStream buffered = new BufferedOutputStream(raw, 65536);
                    OutputStream checked = new FilterOutputStream(buffered) {
                        @Override public void write(int value) throws IOException {
                            checkCancelled(); out.write(value);
                        }
                        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                            checkCancelled(); out.write(bytes, offset, length);
                        }
                    }) {
                NotebookPdfEngine.write(checked, originalPdf, notebookName, pageCount, number -> {
                    checkCancelled(); return source.page(number);
                }, (count, total) -> {
                    checkCancelled(); completed = count; changed();
                }, stroke -> NotebookStrokeRenderer.outline(stroke, () -> cancelled), outline);
                checked.flush();
                raw.getFD().sync();
            }
            validate(staging);
            synchronized (this) {
                checkCancelled();
                preparedFile = staging;
                state = State.READY;
                staging = null;
            }
        } catch (IOException | RuntimeException failure) {
            error = cancelled ? null : "Couldn’t prepare the PDF. " + detail(failure);
            state = cancelled ? State.CANCELLED : State.FAILED;
        } finally {
            if (staging != null) staging.delete();
            changed();
        }
    }

    /** Restore only finalized cache files; never trust a saved path outside this app's cache. */
    static NotebookPdfExport restore(Context context, String path, int id, String name,
                                     int count, boolean interruptedSave) throws IOException {
        File cache = context.getCacheDir().getCanonicalFile();
        File file = new File(path).getCanonicalFile();
        if (!cache.equals(file.getParentFile()) || !file.getName().startsWith(PREFIX)
                || !file.getName().endsWith(".pdf")) throw new IOException("The prepared PDF is unavailable");
        validate(file);
        NotebookPdfExport job = new NotebookPdfExport(context, id, name, count);
        job.preparedFile = file; job.completed = count; job.state = State.READY;
        if (interruptedSave) job.error = "The previous save was interrupted. Its file may be incomplete. Choose a new location to save again.";
        return job;
    }

    void save(Uri destination) {
        final File source;
        synchronized (this) {
            if (cancelled || preparedFile == null || (state != State.READY && state != State.FAILED)) return;
            source = preparedFile; error = null; state = State.SAVING;
        }
        changed();
        files.execute(() -> copy(source, destination));
    }

    private void copy(File source, Uri destination) {
        boolean attempted = false, copied = false, sourceValid = false;
        String failure = null;
        try {
            // Do not create/truncate a destination before checking the finalized source.
            validate(source);
            sourceValid = true;
            checkCancelled();
            try (FileInputStream input = new FileInputStream(source)) {
                checkCancelled();
                attempted = true;
                try (OutputStream output = context.getContentResolver().openOutputStream(destination, "wt")) {
                    if (output == null) throw new IOException("The selected location could not be opened");
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        checkCancelled();
                        output.write(buffer, 0, count);
                    }
                    checkCancelled();
                    output.flush();
                }
            }
            synchronized (this) {
                checkCancelled();
                copied = true; state = State.SAVED; preparedFile = null;
            }
        } catch (IOException | RuntimeException problem) {
            failure = detail(problem);
        }
        synchronized (this) {
            if (!copied) {
                if (cancelled) { state = State.CANCELLED; preparedFile = null; }
                else if (!sourceValid) {
                    state = State.FAILED; preparedFile = null;
                    error = "The prepared PDF is unavailable. Prepare it again to retry. An empty file may remain at the chosen location.";
                }
                else {
                    state = State.READY;
                    error = "Couldn’t save the PDF. " + failure
                        + (attempted ? " An incomplete PDF may remain at that location." : "")
                        + " Choose a new location to try again.";
                }
            }
        }
        if (copied || cancelled || !sourceValid) source.delete();
        changed();
    }

    void cancel() {
        File cleanup = null;
        synchronized (this) {
            cancelled = true;
            if (state != State.PREPARING && state != State.SAVING) {
                cleanup = preparedFile; preparedFile = null; state = State.CANCELLED;
            }
        }
        if (cleanup != null) {
            final File file = cleanup;
            files.execute(file::delete);
        }
        changed();
    }

    private void checkCancelled() throws IOException {
        if (cancelled) throw new IOException("PDF export cancelled");
    }

    private void changed() {
        if (!notificationPending.compareAndSet(false, true)) return;
        main.post(() -> {
            notificationPending.set(false);
            if (listener != null) listener.changed();
        });
    }

    private static void validate(File file) throws IOException {
        if (file == null || !file.isFile() || file.length() < 20) throw new IOException("The prepared PDF is unavailable");
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            byte[] header = new byte[5]; input.readFully(header);
            if (!"%PDF-".equals(new String(header, StandardCharsets.US_ASCII))) throw new IOException("The prepared PDF is invalid");
            byte[] end = new byte[(int) Math.min(32, input.length())];
            input.seek(input.length() - end.length); input.readFully(end);
            if (!new String(end, StandardCharsets.US_ASCII).trim().endsWith("%%EOF"))
                throw new IOException("The prepared PDF is incomplete");
        }
    }

    private static String detail(Throwable failure) {
        return failure.getMessage() == null ? "Please try again." : failure.getMessage();
    }
}
