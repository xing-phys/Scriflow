package org.eink.inkprobe.notebook;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Private in-app picker URIs. The separately shared file provider remains read-only. */
public final class NotebookTransferProvider extends ContentProvider {
    static Uri uri(Context context, File file) throws IOException {
        File checked = NotebookFileStorage.files(context).checked(file, false);
        if (!checked.isFile()) throw new IOException("Choose an available regular file.");
        return new Uri.Builder().scheme("content").authority(context.getPackageName() + ".transfers")
            .appendPath(checked.getAbsolutePath()).build();
    }
    private File file(Uri uri) throws FileNotFoundException {
        try {
            if (!"content".equals(uri.getScheme()) || !(getContext().getPackageName() + ".transfers").equals(uri.getAuthority())
                    || uri.getPathSegments().size() != 1 || uri.getQuery() != null || uri.getFragment() != null)
                throw new IOException("Invalid file URI.");
            File file = NotebookFileStorage.files(getContext()).checked(new File(uri.getPathSegments().get(0)), false);
            if (!file.isFile()) throw new IOException("This file is no longer available.");
            return file;
        } catch (IOException | SecurityException error) { throw new FileNotFoundException(error.getMessage()); }
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) {
        try { return NotebookFileStorage.mime(file(uri)); }
        catch (FileNotFoundException error) { return "application/octet-stream"; }
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        int flags;
        if ("r".equals(mode)) flags = ParcelFileDescriptor.MODE_READ_ONLY;
        else if ("w".equals(mode) || "wt".equals(mode)) flags = ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_TRUNCATE;
        else throw new FileNotFoundException("Unsupported file mode.");
        return ParcelFileDescriptor.open(file(uri), flags);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        try {
            File file = file(uri);
            String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
            MatrixCursor cursor = new MatrixCursor(columns); Object[] row = new Object[columns.length];
            for (int i = 0; i < columns.length; i++) row[i] = OpenableColumns.DISPLAY_NAME.equals(columns[i]) ? file.getName()
                : OpenableColumns.SIZE.equals(columns[i]) ? file.length() : null;
            cursor.addRow(row); return cursor;
        } catch (FileNotFoundException error) { return null; }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
