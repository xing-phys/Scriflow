package org.eink.inkprobe.notebook;

import android.app.Activity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Persistent microphone decisions belong to one exact HTTPS origin, never an entire browser session. */
final class NotebookWebsitePermissions {
    static final int ASK = 0, ALLOW = 1, BLOCK = 2;
    private static final String STORE = "website-microphone";
    private static boolean valid(String origin) {
        return origin != null && origin.startsWith("https://") && origin.equals(NotebookBrowserAddress.origin(origin));
    }
    static int get(Activity activity, String origin) {
        if (!valid(origin)) return ASK;
        int value = activity.getSharedPreferences(STORE, Activity.MODE_PRIVATE).getInt(origin, ASK);
        return value == ALLOW || value == BLOCK ? value : ASK;
    }
    static void set(Activity activity, String origin, int state) {
        if (!valid(origin) || state < ASK || state > BLOCK) throw new IllegalArgumentException("Invalid website permission");
        android.content.SharedPreferences.Editor edit = activity.getSharedPreferences(STORE, Activity.MODE_PRIVATE).edit();
        if (state == ASK) edit.remove(origin); else edit.putInt(origin, state);
        edit.apply();
    }
    static List<String> origins(Activity activity) {
        List<String> result = new ArrayList<>();
        for (String origin : activity.getSharedPreferences(STORE, Activity.MODE_PRIVATE).getAll().keySet())
            if (valid(origin) && get(activity, origin) != ASK) result.add(origin);
        Collections.sort(result); return result;
    }
    private NotebookWebsitePermissions() { }
}
