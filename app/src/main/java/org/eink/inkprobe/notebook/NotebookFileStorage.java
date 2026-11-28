package org.eink.inkprobe.notebook;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.webkit.MimeTypeMap;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Mounted shared volumes only; the notebook library and private app directories are not roots. */
final class NotebookFileStorage {
    static final class Volume {
        final File directory;
        final String title;
        Volume(File directory, String title) { this.directory = directory; this.title = title; }
    }
    private NotebookFileStorage() { }

    static boolean hasAccess(Context context) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    static List<Volume> volumes(Context context) throws IOException {
        List<Volume> result = new ArrayList<>();
        add(result, Environment.getExternalStorageDirectory(), "Internal storage");
        StorageManager manager = context.getSystemService(StorageManager.class);
        if (manager != null) for (StorageVolume volume : manager.getStorageVolumes()) {
            if (!Environment.MEDIA_MOUNTED.equals(volume.getState()) && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(volume.getState())) continue;
            File directory = null;
            if (Build.VERSION.SDK_INT >= 30) directory = volume.getDirectory();
            else if (volume.getUuid() != null) directory = new File("/storage", volume.getUuid());
            if (directory != null) add(result, directory, volume.isPrimary() ? "Internal storage" : volume.getDescription(context));
        }
        // Android 10 exposes app-specific paths for additional mounted volumes. Strip the
        // documented suffix, rather than interpreting arbitrary filesystem directories as drives.
        for (File directory : context.getExternalFilesDirs(null)) {
            if (directory == null) continue;
            String suffix = "/Android/data/" + context.getPackageName() + "/files";
            String path = directory.getAbsolutePath();
            if (path.endsWith(suffix)) add(result, new File(path.substring(0, path.length() - suffix.length())), "SD card / USB storage");
        }
        return result;
    }

    private static void add(List<Volume> volumes, File directory, String title) throws IOException {
        File canonical = directory.getCanonicalFile();
        if (!canonical.isDirectory()) return;
        for (Volume existing : volumes) if (existing.directory.equals(canonical)) return;
        volumes.add(new Volume(canonical, title));
    }

    static NotebookFiles files(Context context) throws IOException {
        if (!hasAccess(context)) throw new IOException("Allow storage access from Files to manage tablet files.");
        List<File> roots = new ArrayList<>();
        for (Volume volume : volumes(context)) roots.add(volume.directory);
        return new NotebookFiles(roots);
    }

    static String mime(File file) {
        String extension = NotebookFiles.extension(file.getName());
        if (extension.equals("md") || extension.equals("markdown")) return "text/markdown";
        String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return type == null ? "application/octet-stream" : type;
    }

    static Intent downloadIntent(Context context, Uri uri, String name, String mime) {
        String fileMime = mime(new File(name));
        if (mime == null || mime.equals("application/octet-stream")) mime = fileMime;
        Intent open = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if ("application/pdf".equals(fileMime) || "application/pdf".equals(mime)) open.setClass(context, NotebookPdfImportActivity.class);
        else if (NotebookEbookStore.supported(name)) open.setClass(context, NotebookEbookImportActivity.class);
        return open;
    }
}
