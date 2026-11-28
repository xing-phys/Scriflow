package org.eink.inkprobe.notebook;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

/** Stages files picked on the tablet and publishes completed uploads to Downloads. */
final class NotebookShareStorage implements NotebookShareServer.Inbox {
    static final String DOWNLOAD_FOLDER = "Scriflow Wi-Fi Sharing";
    private final ContentResolver resolver;
    private final File cache, staging;

    NotebookShareStorage(Context context) {
        resolver = context.getContentResolver();
        cache = new File(context.getCacheDir(), "wifi-sharing");
        staging = new File(cache, UUID.randomUUID().toString());
    }

    void cleanOldSessions() {
        File[] sessions = cache.listFiles();
        if (sessions != null) for (File session : sessions)
            if (!session.equals(staging)) clear(session);
    }

    void cleanup() { clear(staging); }

    private static void clear(File directory) {
        File[] files = directory.listFiles();
        if (files != null) for (File file : files) file.delete();
        directory.delete();
    }

    void stage(Uri uri, NotebookShareServer server) throws IOException {
        long limit = server.stagingLimit();
        if (!ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) throw new IOException("Choose a file from the file picker.");
        String name = null;
        long size = -1;
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameColumn >= 0) name = cursor.getString(nameColumn);
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn);
            }
        }
        if (size > NotebookShareServer.MAX_FILE_BYTES) throw new IOException("Choose a file of 1 GB or less.");
        if (size > limit) throw new IOException("Remove shared files to make room for this file.");
        if (!staging.isDirectory() && !staging.mkdirs()) throw new IOException("Could not prepare temporary storage.");
        File file = File.createTempFile("share-", ".tmp", staging);
        boolean offered = false;
        try {
            long written;
            try (InputStream input = resolver.openInputStream(uri); OutputStream output = new FileOutputStream(file)) {
                if (input == null) throw new IOException("The selected file is unavailable.");
                written = NotebookDownloadStream.copy(input, output, size, limit, new NotebookDownloadStream.Control() {
                    @Override public boolean cancelled() { return server.closed(); }
                    @Override public void progress(long bytes) { }
                });
            }
            server.offerFile(name == null ? "shared-file" : name, file, written);
            offered = true;
        } finally {
            if (!offered) file.delete();
        }
    }

    @Override public NotebookShareServer.PendingFile create(String name) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, name);
        values.put(MediaStore.Downloads.MIME_TYPE, NotebookFileStorage.mime(new File(name)));
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOAD_FOLDER + "/");
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Could not create a file in Downloads.");
        return new NotebookShareServer.PendingFile() {
            private boolean published;

            @Override public OutputStream open() throws IOException {
                OutputStream output = resolver.openOutputStream(uri, "w");
                if (output == null) throw new IOException("Could not open the destination file.");
                return output;
            }

            @Override public NotebookShareServer.SavedFile publish() throws IOException {
                ContentValues visible = new ContentValues();
                visible.put(MediaStore.Downloads.IS_PENDING, 0);
                if (resolver.update(uri, visible, null, null) != 1) throw new IOException("Could not publish the file.");
                published = true;
                return new NotebookShareServer.SavedFile(displayName(uri, name), uri.toString());
            }

            @Override public void close() {
                if (!published) try { resolver.delete(uri, null, null); } catch (RuntimeException ignored) { }
            }
        };
    }

    private String displayName(Uri uri, String fallback) {
        try (Cursor cursor = resolver.query(uri, new String[]{MediaStore.Downloads.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0);
        } catch (RuntimeException ignored) { }
        return fallback;
    }
}
