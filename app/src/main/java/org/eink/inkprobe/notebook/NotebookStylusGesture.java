package org.eink.inkprobe.notebook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

/** The same foreground M-Pencil body-gesture action used by this firmware's PenKit. */
final class NotebookStylusGesture {
    static final String ACTION = "com.huawei.stylus.action.BUTTON_DOUBLE_PRESSED";
    private final Context context;
    private final Runnable listener;
    private boolean registered;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (registered && intent != null && ACTION.equals(intent.getAction())) listener.run();
        }
    };
    NotebookStylusGesture(Context context, Runnable listener) { this.context = context; this.listener = listener; }
    void start() {
        if (registered) return;
        context.registerReceiver(receiver, new IntentFilter(ACTION)); registered = true;
    }
    void stop() {
        if (!registered) return;
        registered = false; context.unregisterReceiver(receiver);
    }
}
