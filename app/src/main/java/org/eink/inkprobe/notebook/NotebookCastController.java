package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.view.inspector.WindowInspector;
import java.lang.ref.WeakReference;

/** UI-process control only. No screen pixels, JPEG buffers, sockets or capture services live here. */
final class NotebookCastController {
    static final int REGISTER = 1, VISIBILITY = 2, CONTACT = 3, STOP = 4, STATUS = 5;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static WeakReference<NotebookDisplayActivity> foreground = new WeakReference<>(null);
    private static Context app;
    private static Messenger remote;
    private static boolean bound, running, starting;
    private static int contact;
    private static String address = "", status = "Casting is off.";
    private static long lastLease;
    private static int lastVisibility = -1;
    private static final Messenger replies = new Messenger(new Handler(Looper.getMainLooper(), message -> {
        if (message.what != STATUS) return false;
        running = message.getData().getBoolean("running"); starting = message.getData().getBoolean("starting");
        address = message.getData().getString("address", ""); status = message.getData().getString("status", "Casting stopped.");
        if (!running && !starting) disconnect();
        return true;
    }));
    private static final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            remote = new Messenger(binder); lastVisibility = -1; lastLease = 0;
            Message register = Message.obtain(null, REGISTER); register.replyTo = replies;
            send(register); refresh();
            main.removeCallbacks(watch); main.post(watch);
        }
        @Override public void onServiceDisconnected(ComponentName name) { lost(); }
        @Override public void onBindingDied(ComponentName name) { lost(); }
        @Override public void onNullBinding(ComponentName name) { lost(); }
    };
    private static void lost() {
        running = false; starting = false; address = ""; status = "Casting stopped. Start again to reconnect."; disconnect();
    }
    private static void disconnect() {
        main.removeCallbacks(watch); remote = null;
        if (bound && app != null) { bound = false; try { app.unbindService(connection); } catch (IllegalArgumentException ignored) { } }
    }
    private static void bind(Context context, int flags) {
        app = context.getApplicationContext();
        if (bound) return;
        bound = app.bindService(new Intent(app, NotebookCastService.class), connection, flags | Context.BIND_WAIVE_PRIORITY);
    }

    static boolean running() { return running; }
    static boolean starting() { return starting; }
    static String address() { return address; }
    static String status() { return status; }
    static void start(Activity activity, Intent consent, String password, NotebookCastNetwork wifi) {
        if (running || starting) return;
        starting = true; status = "Starting screen cast…"; app = activity.getApplicationContext();
        Intent command = new Intent(app, NotebookCastService.class).setAction("start")
            .putExtra("consent", consent).putExtra("password", password).putExtra("address", wifi.address.getHostAddress());
        try {
            app.startForegroundService(command);
            // A previous non-creating recovery bind may still be waiting for a service.
            disconnect(); bind(app, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException error) { starting = false; status = "Cannot start screen capture. Try again."; disconnect(); }
    }
    static void stopCasting(Context context) {
        send(Message.obtain(null, STOP));
        running = false; starting = false; address = ""; status = "Casting stopped.";
        disconnect(); context.stopService(new Intent(context, NotebookCastService.class));
    }
    static void resumed(NotebookDisplayActivity activity) {
        foreground = new WeakReference<>(activity);
        // Reconnect to a surviving foreground cast after Android recreates the UI process.
        if (!bound) bind(activity, 0);
        refresh();
    }
    static void paused(NotebookDisplayActivity activity) {
        if (foreground.get() == activity) { foreground.clear(); contact = NotebookCastPacer.IDLE; refresh(); }
    }
    static void focusChanged(NotebookDisplayActivity activity) {
        if (foreground.get() != activity || remote == null) return;
        refresh(); main.removeCallbacks(settledFocus); main.postDelayed(settledFocus, 32);
    }
    private static final Runnable settledFocus = NotebookCastController::refresh;
    static void contact(NotebookDisplayActivity activity, boolean down, boolean protect) {
        int mode = !down ? NotebookCastPacer.IDLE : protect ? NotebookCastPacer.PROTECTED : NotebookCastPacer.PEN;
        if (foreground.get() != activity || contact == mode) return;
        contact = mode;
        if (remote != null) send(Message.obtain(null, CONTACT, mode, 0));
    }
    private static final Runnable watch = new Runnable() {
        @Override public void run() { if (remote != null) { refresh(); main.postDelayed(this, 200); } }
    };
    private static void refresh() {
        if (remote == null || app == null) return;
        NotebookDisplayActivity activity = foreground.get();
        boolean allowed = activity != null && activity.allowScreenCast() && !activity.isFinishing()
            && !activity.isInMultiWindowMode() && !activity.isInPictureInPictureMode()
            && app.getSystemService(PowerManager.class).isInteractive() && !app.getSystemService(KeyguardManager.class).isKeyguardLocked();
        boolean focused = false;
        if (allowed) for (View root : WindowInspector.getGlobalWindowViews()) {
            if (!root.hasWindowFocus() || !root.isShown()) continue;
            if (root.getLayoutParams() instanceof WindowManager.LayoutParams
                    && ((((WindowManager.LayoutParams)root.getLayoutParams()).flags & WindowManager.LayoutParams.FLAG_SECURE) != 0)) continue;
            focused = true; break;
        }
        int visibility = !allowed ? 0 : focused ? 2 : 1;
        long now = SystemClock.uptimeMillis();
        if (visibility == lastVisibility && now-lastLease < 1000) return;
        lastVisibility = visibility; lastLease = now;
        Message message = Message.obtain(null, VISIBILITY, visibility, contact);
        send(message);
    }
    private static void send(Message message) {
        if (remote == null) return;
        try { remote.send(message); } catch (RemoteException dead) { lost(); }
    }
}
