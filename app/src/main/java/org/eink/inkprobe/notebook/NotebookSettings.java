package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;

/** App-wide display preferences; document geometry is independent of screen rotation. */
final class NotebookSettings {
    enum Orientation {
        PORTRAIT("Portrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
        LANDSCAPE("Landscape", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
        SENSOR("Follow device sensor", ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);

        final String label;
        final int requested;
        Orientation(String label, int requested) { this.label = label; this.requested = requested; }
    }

    private static final String PREFERENCES = "notebookSettings";
    private static final String ORIENTATION = "orientation";

    private NotebookSettings() { }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    static Orientation orientation(Context context) {
        String stored = preferences(context).getString(ORIENTATION, Orientation.PORTRAIT.name());
        for (Orientation value : Orientation.values()) if (value.name().equals(stored)) return value;
        return Orientation.PORTRAIT;
    }

    static void setOrientation(Activity activity, Orientation orientation) {
        if (orientation == null) throw new IllegalArgumentException("Missing orientation");
        preferences(activity).edit().putString(ORIENTATION, orientation.name()).apply();
        applyOrientation(activity);
    }

    /** Call on create and resume so an Activity below Settings also receives a changed choice. */
    static void applyOrientation(Activity activity) {
        int requested = orientation(activity).requested;
        if (activity.getRequestedOrientation() != requested) activity.setRequestedOrientation(requested);
    }

    /** Keep the physical surface stable until a pen contact has finished. */
    static void setPenDown(Activity activity, boolean down) {
        if (orientation(activity) != Orientation.SENSOR) return;
        if (down) {
            if (activity.getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LOCKED)
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        } else applyOrientation(activity);
    }
}
