package org.eink.inkprobe.notebook;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;

/** App-wide preferences; document geometry is independent of screen rotation. */
final class NotebookSettings {
    enum BookshelfView { LIST, THUMBNAILS }
    enum Orientation {
        PORTRAIT("Portrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
        LANDSCAPE("Landscape", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
        // NotebookDisplayActivity drives concrete directions from sensor readings.
        SENSOR("Follow device sensor", ActivityInfo.SCREEN_ORIENTATION_LOCKED);

        final String label;
        final int requested;
        Orientation(String label, int requested) { this.label = label; this.requested = requested; }
    }

    private static final String PREFERENCES = "notebookSettings";
    private static final String ORIENTATION = "orientation";
    private static final String PEN_WIDTH_MODE = "penWidthMode";

    private NotebookSettings() { }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    static Orientation orientation(Context context) {
        String stored = preferences(context).getString(ORIENTATION, Orientation.PORTRAIT.name());
        for (Orientation value : Orientation.values()) if (value.name().equals(stored)) return value;
        return Orientation.PORTRAIT;
    }

    static void setOrientation(Context context, Orientation orientation) {
        if (orientation == null) throw new IllegalArgumentException("Missing orientation");
        preferences(context).edit().putString(ORIENTATION, orientation.name()).apply();
    }

    static NotebookSensorRotation.Facing displayFacing(Context context, NotebookSensorRotation.Facing fallback) {
        String stored = preferences(context).getString("displayFacing", "");
        for (NotebookSensorRotation.Facing facing : NotebookSensorRotation.Facing.values())
            if (facing.name().equals(stored)) return facing;
        return fallback;
    }

    static void saveDisplayFacing(Context context, NotebookSensorRotation.Facing facing) {
        preferences(context).edit().putString("displayFacing", facing.name()).apply();
    }

    static boolean pinchZoomEnabled(Context context) {
        return preferences(context).getBoolean("pinchZoom", true);
    }

    static void setPinchZoomEnabled(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("pinchZoom", enabled).apply();
    }

    static NotebookPenConfig.WidthMode penWidthMode(Context context) {
        String stored = preferences(context).getString(PEN_WIDTH_MODE, NotebookPenConfig.WidthMode.PAGE.name());
        for (NotebookPenConfig.WidthMode mode : NotebookPenConfig.WidthMode.values())
            if (mode.name().equals(stored)) return mode;
        return NotebookPenConfig.WidthMode.PAGE;
    }

    static void setPenWidthMode(Context context, NotebookPenConfig.WidthMode mode) {
        if (mode == null) throw new IllegalArgumentException("Missing pen width mode");
        preferences(context).edit().putString(PEN_WIDTH_MODE, mode.name()).apply();
    }

    static BookshelfView bookshelfView(Context context) {
        return "THUMBNAILS".equals(preferences(context).getString("bookshelfView", "LIST"))
            ? BookshelfView.THUMBNAILS : BookshelfView.LIST;
    }

    static void setBookshelfView(Context context, BookshelfView view) {
        preferences(context).edit().putString("bookshelfView", view.name()).apply();
    }

    static boolean landscape(Context context) {
        return context.getResources().getConfiguration().orientation
            == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }

    static int bookshelfRows(Context context, boolean landscape) {
        return boundedPreference(context, "shelfRows" + landscape, landscape ? 3 : 4, 1, 12);
    }

    static int bookshelfColumns(Context context, boolean landscape) {
        return boundedPreference(context, "shelfColumns" + landscape, landscape ? 5 : 3, 1, 12);
    }

    static int pageColumns(Context context, boolean landscape) {
        return boundedPreference(context, "pageColumns" + landscape, landscape ? 8 : 5, 2, 12);
    }

    static void setGridPreference(Context context, String kind, boolean landscape, int value) {
        if (!kind.equals("shelfRows") && !kind.equals("shelfColumns") && !kind.equals("pageColumns"))
            throw new IllegalArgumentException("Unknown grid preference");
        if (value < (kind.equals("pageColumns") ? 2 : 1) || value > 12)
            throw new IllegalArgumentException("Grid size is out of range");
        preferences(context).edit().putInt(kind + landscape, value).apply();
    }

    static boolean keepScreenOn(Context context) {
        return preferences(context).getBoolean("keepScreenOn", false);
    }

    static void setKeepScreenOn(Context context, boolean enabled) {
        preferences(context).edit().putBoolean("keepScreenOn", enabled).apply();
    }

    private static int boundedPreference(Context context, String key, int fallback, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, preferences(context).getInt(key, fallback)));
    }
}
