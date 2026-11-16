package org.eink.inkprobe.notebook;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** App-level audio permission shared by current website input and future native voice features. */
final class NotebookMicrophone {
    static final int REQUEST = 6212;
    private final Activity activity;
    private final List<Consumer<Boolean>> pending = new ArrayList<>();
    private boolean resumed, requesting, needed, closed;
    private Boolean result;

    NotebookMicrophone(Activity activity) { this.activity = activity; }
    boolean granted() { return activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }

    void resumed(boolean value) {
        resumed = value;
        if (value) { deliver(); if (needed && !requesting) launch(); }
    }

    void askOnce() {
        if (!granted() && !activity.getSharedPreferences("microphone", Activity.MODE_PRIVATE).getBoolean("asked", false))
            request(value -> { });
    }

    void request(Consumer<Boolean> callback) {
        if (closed) { callback.accept(false); return; }
        pending.add(callback);
        if (granted()) { result = true; deliver(); return; }
        needed = true;
        if (resumed && !requesting) launch();
    }

    private void launch() {
        if (closed || !resumed || requesting || !needed) return;
        needed = false; requesting = true;
        activity.getSharedPreferences("microphone", Activity.MODE_PRIVATE).edit().putBoolean("asked", true).apply();
        activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST);
    }

    boolean permissionResult(int code) {
        if (code != REQUEST) return false;
        requesting = false; needed = false; result = granted(); deliver(); return true;
    }

    private void deliver() {
        if (!resumed || result == null || closed) return;
        boolean allowed = result; result = null;
        List<Consumer<Boolean>> callbacks = new ArrayList<>(pending); pending.clear();
        for (Consumer<Boolean> callback : callbacks) callback.accept(allowed);
    }

    void close() {
        closed = true;
        List<Consumer<Boolean>> callbacks = new ArrayList<>(pending); pending.clear();
        for (Consumer<Boolean> callback : callbacks) callback.accept(false);
    }
}
