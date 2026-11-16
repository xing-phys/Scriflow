package org.eink.inkprobe.notebook;

import android.content.Context;
import android.content.SharedPreferences;

/** One writing configuration for ordinary notes, AI workspaces and browser notes. */
final class NotebookToolSettings {
    private static SharedPreferences preferences(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences shared = app.getSharedPreferences("note-tools", Context.MODE_PRIVATE);
        if (!shared.getBoolean("migrated", false)) {
            // The normal notebook's established choices win. Fall back per key if
            // someone has only used notes inside AI chat or the browser so far.
            SharedPreferences[] legacy = {
                app.getSharedPreferences("notebook.NotebookActivity", Context.MODE_PRIVATE),
                app.getSharedPreferences("notebook.NotebookAiActivity", Context.MODE_PRIVATE),
                app.getSharedPreferences("notebook.NotebookBrowserActivity", Context.MODE_PRIVATE)
            };
            SharedPreferences.Editor editor = shared.edit();
            for (String key : new String[]{"penWidth", "eraserWidth", "regionEraser"}) {
                if (shared.contains(key)) continue;
                for (SharedPreferences source : legacy) if (source.contains(key)) {
                    if (key.equals("regionEraser")) editor.putBoolean(key, source.getBoolean(key, false));
                    else editor.putFloat(key, source.getFloat(key, key.equals("penWidth") ? NotebookPenConfig.DEFAULT_WIDTH : NotebookWidthRange.ERASER.defaultWidth));
                    break;
                }
            }
            editor.putBoolean("migrated", true).apply();
        }
        return shared;
    }

    static float penWidth(Context context) { return NotebookPenConfig.restore(preferences(context).getFloat("penWidth", NotebookPenConfig.DEFAULT_WIDTH)); }
    static float eraserWidth(Context context) { return NotebookWidthRange.ERASER.restore(preferences(context).getFloat("eraserWidth", NotebookWidthRange.ERASER.defaultWidth)); }
    static boolean regionEraser(Context context) { return preferences(context).getBoolean("regionEraser", false); }
    static void width(Context context, boolean eraser, float width) {
        preferences(context).edit().putFloat(eraser ? "eraserWidth" : "penWidth",
            (eraser ? NotebookWidthRange.ERASER : NotebookWidthRange.PEN).restore(width)).apply();
    }
    static void regionEraser(Context context, boolean region) { preferences(context).edit().putBoolean("regionEraser", region).apply(); }
    private NotebookToolSettings() { }
}
