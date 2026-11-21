package org.eink.inkprobe.notebook;

import android.content.Context;

/** The file and saved-page collections share density and presentation controls. */
final class NotebookCollectionLayout {
    static int columns(Context context, boolean thumbnails) {
        boolean landscape = NotebookSettings.landscape(context);
        if (!thumbnails) return landscape ? 2 : 1;
        int width = Math.max(1, context.getResources().getConfiguration().screenWidthDp - 36);
        return Math.max(1, Math.min(landscape ? 6 : 4, (width + 8) / 160));
    }

    static boolean thumbnails(Context context, String collection) {
        return context.getSharedPreferences("collection-layout", Context.MODE_PRIVATE)
            .getBoolean(collection + "Thumbnails", false);
    }

    static void setThumbnails(Context context, String collection, boolean value) {
        context.getSharedPreferences("collection-layout", Context.MODE_PRIVATE).edit()
            .putBoolean(collection + "Thumbnails", value).apply();
    }

    private NotebookCollectionLayout() { }
}
