package org.eink.inkprobe.notebook;

import android.content.Context;
import android.content.Intent;

/** Explicit, private entry points into the app's shared-storage browser. */
final class NotebookFilePicker {
    static final String MODE = "pickerMode", FILTER = "pickerFilter", NAME = "pickerName";
    private NotebookFilePicker() { }
    static Intent open(Context context, String filter) {
        return new Intent(context, NotebookFilesActivity.class).putExtra(MODE, "open").putExtra(FILTER, filter);
    }
    static Intent save(Context context, String name) {
        return new Intent(context, NotebookFilesActivity.class).putExtra(MODE, "save").putExtra(NAME, name);
    }
    static boolean accepts(String filter, String name) {
        String extension = NotebookFiles.extension(name);
        if ("pdf".equals(filter)) return extension.equals("pdf");
        if ("ebook".equals(filter)) return NotebookEbookStore.supported(name);
        if ("backup".equals(filter)) return extension.equals("inkbook");
        if ("image".equals(filter)) {
            String mime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            return extension.equals("svg") || extension.equals("svgz") || extension.equals("bmp")
                || (mime != null && mime.startsWith("image/"));
        }
        if ("font".equals(filter)) return extension.equals("ttf") || extension.equals("otf");
        return true;
    }
}
