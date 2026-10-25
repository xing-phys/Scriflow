package org.eink.inkprobe.notebook;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Retained transfer job: Activity recreation never repeats a restore publication. */
final class NotebookTransfer {
    private static final ExecutorService files = Executors.newSingleThreadExecutor(r -> new Thread(r, "notebook-transfer"));
    final boolean restoring;
    private final Context context;
    private final NotebookSession session;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable listener;
    volatile boolean busy, complete, cancelled;
    volatile String message;
    volatile File prepared;
    private NotebookBackup.Restored staged;
    NotebookTransfer(Context context, boolean restoring) {
        this.context = context.getApplicationContext(); this.restoring = restoring;
        session = NotebookSession.get(context);
    }
    void observe(Runnable listener) { this.listener = listener; listener.run(); }
    void detach() { listener = null; }
    private void changed() { main.post(() -> { if (listener != null) listener.run(); }); }
    void prepare(int id) {
        busy = true; message = "Preparing editable backup…"; changed();
        session.openNotebook(id, (page, error) -> {
            if (error != null || cancelled) { fail(error); return; }
            final File file;
            try { file = File.createTempFile("notebook-backup-", ".inkbook", context.getCacheDir()); }
            catch (IOException problem) { fail(problem); return; }
            session.writeBackup(file, () -> cancelled, failure -> {
                if (failure != null || cancelled) { file.delete(); fail(failure); return; }
                prepared = file; busy = false; message = "Backup ready. Choose where to save it."; changed();
            });
        });
    }
    void save(Uri uri) {
        if (busy || prepared == null || complete) return;
        busy = true; message = "Saving backup…"; changed();
        files.execute(() -> {
            try (InputStream input = new FileInputStream(prepared);
                 OutputStream output = context.getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new IOException("Cannot open the selected location");
                byte[] bytes = new byte[65536]; int length;
                while ((length = input.read(bytes)) != -1) {
                    if (cancelled) throw new IOException("Transfer cancelled");
                    output.write(bytes, 0, length);
                }
                output.flush();
            } catch (IOException | RuntimeException error) {
                fail(new IOException("Could not save backup; an incomplete file may remain. Choose a new location to retry.", error)); return;
            }
            complete = true; busy = false; message = "Editable backup saved.";
            prepared.delete(); prepared = null; changed();
        });
    }
    void restore(Uri uri) {
        if (busy || complete) return;
        busy = true; message = "Validating backup…"; changed();
        files.execute(() -> {
            try {
                File staging = new File(context.getFilesDir(), "restore-stage-" + UUID.randomUUID());
                InputStream input = context.getContentResolver().openInputStream(uri);
                if (input == null) throw new IOException("Cannot open the selected backup");
                staged = NotebookBackup.read(input, staging, () -> cancelled);
                if (cancelled) { NotebookBackup.deleteTree(staging); fail(null); return; }
                message = "Adding restored notebook…"; changed();
                // Once this barrier is entered it must finish; cancellation never rolls back a committed book.
                session.importBackup(staged, error -> {
                    if (staged.directory.exists()) NotebookBackup.deleteTree(staged.directory);
                    staged = null;
                    if (error != null) { fail(error); return; }
                    complete = true; busy = false; message = "Restored as a new notebook. Existing notebooks were preserved."; changed();
                });
            } catch (IOException | RuntimeException error) { fail(error); }
        });
    }
    void cancel() { cancelled = true; if (!busy && prepared != null) { prepared.delete(); prepared = null; } }
    private void fail(Throwable error) {
        busy = false; message = error == null ? "Transfer cancelled." : error.getMessage(); changed();
    }
}
