package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.inspector.WindowInspector;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/** Explicit, non-restarting cast session. Capture and JPEG encoding never run on the UI thread. */
public final class NotebookCastService extends Service {
    private static final String STOP = "org.eink.inkprobe.STOP_CAST", CHANNEL = "screen_cast";
    private static final int NOTIFICATION = 4301;
    private static WeakReference<NotebookDisplayActivity> foreground = new WeakReference<>(null);
    private static NotebookCastService instance;
    private static Start pending;
    private static String lastStatus = "Casting is off.";
    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread captureThread;
    private Handler capture;
    private NotebookCastServer server;
    private NotebookCastNetwork wifi;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private Bitmap bitmap;
    private ByteBuffer packed;
    private Image pendingImage;
    private boolean sharing, attached, closing, networkRegistered, screenRegistered, displayRegistered;
    private boolean capturedVisible = true;
    private int width, height, sourceWidth, sourceHeight;
    private long lastEncode;

    private static final class Start {
        final Intent consent;
        final String password;
        final NotebookCastNetwork wifi;
        Start(Intent consent, String password, NotebookCastNetwork wifi) {
            this.consent = consent; this.password = password; this.wifi = wifi;
        }
    }

    static boolean running() { return instance != null && instance.server != null && !instance.closing; }
    static boolean starting() { return pending != null; }
    static String address() { return running() ? instance.server.address() : ""; }
    static String status() { return running() ? (instance.sharing ? "Casting Scriflow · View only" : "Cast paused · Open a Scriflow page to share") : lastStatus; }

    static void start(Activity activity, Intent consent, String password, NotebookCastNetwork wifi) {
        if (running() || pending != null) return;
        pending = new Start(consent, password, wifi); lastStatus = "Starting screen cast…";
        try { activity.startForegroundService(new Intent(activity, NotebookCastService.class)); }
        catch (RuntimeException error) { pending = null; lastStatus = "Cannot start screen capture. Try again."; }
    }

    static void stopCasting(Context context) {
        pending = null;
        if (instance != null) instance.finish("Casting stopped.");
        else context.stopService(new Intent(context, NotebookCastService.class));
    }

    static void resumed(NotebookDisplayActivity activity) {
        foreground = new WeakReference<>(activity);
        if (instance != null) instance.refreshVisibility();
    }
    static void paused(NotebookDisplayActivity activity) {
        if (foreground.get() == activity) {
            foreground.clear(); if (instance != null) instance.setSharing(false);
        }
    }
    static void focusChanged(NotebookDisplayActivity activity) {
        if (foreground.get() != activity || instance == null) return;
        // Revoke immediately; inspect app-owned dialog/popup focus after dispatch completes.
        instance.setSharing(false); instance.main.post(instance::refreshVisibility);
    }

    @Override public void onCreate() { super.onCreate(); instance = this; }
    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) { finish("Casting stopped."); return START_NOT_STICKY; }
        if (server != null) return START_NOT_STICKY;
        Start start = pending; pending = null;
        if (start == null) { stopSelf(); return START_NOT_STICKY; }
        try {
            NotificationManager notices = getSystemService(NotificationManager.class);
            notices.createNotificationChannel(new NotificationChannel(CHANNEL, "Screen cast", NotificationManager.IMPORTANCE_LOW));
            PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, NotebookCastActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, NotebookCastService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(this, CHANNEL).setSmallIcon(org.eink.inkprobe.R.drawable.ic_cast)
                .setContentTitle("Scriflow screen cast").setContentText("Sharing over local Wi-Fi. Tap for address and controls.")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "Stop casting", stop).build()).build();
            startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            wifi = start.wifi;
            if (!wifi.matches(this)) throw new IOException("Wi-Fi changed. Connect and start again.");
            Map<String, byte[]> assets = new HashMap<>();
            for (String name : new String[]{"index.html", "viewer.js", "viewer.css"}) {
                try (InputStream input = getAssets().open("cast/" + name); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    assets.put(name, output.toByteArray());
                }
            }
            try { server = new NotebookCastServer(wifi.address, 8080, start.password, assets); }
            catch (java.net.BindException busy) { server = new NotebookCastServer(wifi.address, 0, start.password, assets); }
            captureThread = new HandlerThread("screen-cast-capture", android.os.Process.THREAD_PRIORITY_BACKGROUND);
            captureThread.start(); capture = new Handler(captureThread.getLooper());
            projection = getSystemService(MediaProjectionManager.class).getMediaProjection(Activity.RESULT_OK, start.consent);
            if (projection == null) throw new IOException("Screen capture permission was not granted.");
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { finish("Android stopped screen capture. Start again to reconnect."); }
                @Override public void onCapturedContentResize(int w, int h) { if (w > 0 && h > 0) resize(w, h); }
                @Override public void onCapturedContentVisibilityChanged(boolean visible) { capturedVisible = visible; refreshVisibility(); }
            }, main);
            Point size = new Point(); getSystemService(WindowManager.class).getDefaultDisplay().getRealSize(size);
            resize(size.x, size.y);
            ConnectivityManager connectivity = getSystemService(ConnectivityManager.class);
            connectivity.registerNetworkCallback(new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), network, main);
            networkRegistered = true;
            IntentFilter screen = new IntentFilter(Intent.ACTION_SCREEN_OFF); screen.addAction(Intent.ACTION_SHUTDOWN);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, screen, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenOff, screen);
            screenRegistered = true;
            getSystemService(DisplayManager.class).registerDisplayListener(displayListener, main); displayRegistered = true;
            if (!wifi.matches(this)) throw new IOException("Wi-Fi changed. Start casting again.");
            refreshVisibility(); main.post(watchVisibility);
        } catch (IOException | RuntimeException error) {
            finish(error instanceof IOException ? error.getMessage() : "Screen capture could not start. Check Android permission and try again.");
        }
        return START_NOT_STICKY;
    }

    private void refreshVisibility() {
        NotebookDisplayActivity activity = foreground.get();
        boolean visible = capturedVisible && activity != null && activity.allowScreenCast() && !activity.isFinishing()
            && !activity.isInMultiWindowMode() && !activity.isInPictureInPictureMode()
            && getSystemService(PowerManager.class).isInteractive() && !getSystemService(KeyguardManager.class).isKeyguardLocked();
        boolean focused = false;
        if (visible) for (View root : WindowInspector.getGlobalWindowViews()) {
            if (!root.hasWindowFocus() || !root.isShown()) continue;
            if (root.getLayoutParams() instanceof WindowManager.LayoutParams
                    && ((((WindowManager.LayoutParams) root.getLayoutParams()).flags & WindowManager.LayoutParams.FLAG_SECURE) != 0)) continue;
            focused = true; break;
        }
        setSharing(visible && focused);
    }

    private void setSharing(boolean enabled) {
        if (closing || server == null || sharing == enabled) return;
        sharing = enabled; server.frames.sharing(enabled, System.nanoTime());
        main.removeCallbacks(attachSurface);
        if (virtualDisplay != null) {
            // Android can retire the display before delivering projection.onStop().
            try { virtualDisplay.setSurface(null); } catch (IllegalStateException | IllegalArgumentException ignored) { }
            attached = false;
        }
        if (enabled) main.postDelayed(attachSurface, 400);
    }
    private final Runnable attachSurface = () -> {
        refreshVisibility();
        if (!closing && sharing && virtualDisplay != null && reader != null && !attached) {
            try { virtualDisplay.setSurface(reader.getSurface()); attached = true; }
            catch (IllegalStateException | IllegalArgumentException stopped) { finish("Android ended screen capture. Start casting again."); }
        }
    };
    private final Runnable watchVisibility = new Runnable() {
        @Override public void run() {
            if (closing) return;
            refreshVisibility(); main.postDelayed(this, 200);
        }
    };

    private void resize(int w, int h) {
        if (closing || projection == null || (w == sourceWidth && h == sourceHeight)) return;
        setSharing(false); sourceWidth = w; sourceHeight = h;
        double scale = Math.min(1, 1600.0 / Math.max(w, h));
        width = Math.max(1, (int) Math.round(w * scale)); height = Math.max(1, (int) Math.round(h * scale));
        ImageReader old = reader;
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        reader.setOnImageAvailableListener(source -> {
            try {
                Image image = source.acquireLatestImage(); if (image == null) return;
                if (pendingImage != null) pendingImage.close(); pendingImage = image;
                capture.removeCallbacks(encodeFrame);
                capture.postDelayed(encodeFrame, Math.max(0, 250 - (SystemClock.uptimeMillis() - lastEncode)));
            } catch (IllegalStateException ignored) { /* Retired reader during rotation. */ }
        }, capture);
        if (virtualDisplay == null) virtualDisplay = projection.createVirtualDisplay("Scriflow screen cast", width, height,
            getResources().getConfiguration().densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, null, null, main);
        else { virtualDisplay.setSurface(null); virtualDisplay.resize(width, height, getResources().getConfiguration().densityDpi); }
        attached = false;
        if (old != null) {
            old.setOnImageAvailableListener(null, null);
            capture.post(() -> {
                capture.removeCallbacks(encodeFrame);
                if (pendingImage != null) { pendingImage.close(); pendingImage = null; }
                old.close();
            });
        }
        refreshVisibility();
    }

    private final Runnable encodeFrame = () -> {
        Image image = pendingImage; pendingImage = null;
        if (image == null) return;
        try {
            NotebookCastServer active = server;
            if (active == null) return;
            long ticket = active.frames.ticket(image.getTimestamp()); if (ticket < 0) return;
            int w = image.getWidth(), h = image.getHeight();
            if (bitmap == null || bitmap.getWidth() != w || bitmap.getHeight() != h) {
                if (bitmap != null) bitmap.recycle();
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); packed = ByteBuffer.allocateDirect(w * h * 4);
            }
            Image.Plane plane = image.getPlanes()[0];
            if (plane.getPixelStride() != 4) throw new IllegalStateException("Unsupported capture format");
            ByteBuffer source = plane.getBuffer(); packed.clear();
            for (int y = 0; y < h; y++) {
                source.limit(source.capacity()); source.position(y * plane.getRowStride()); source.limit(source.position() + w * 4);
                packed.put(source);
            }
            packed.flip(); bitmap.copyPixelsFromBuffer(packed);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)) active.frames.publish(ticket, output.toByteArray());
            lastEncode = SystemClock.uptimeMillis();
        } catch (RuntimeException error) { main.post(() -> finish("Screen capture failed. Start casting again.")); }
        finally { image.close(); }
    };

    private final ConnectivityManager.NetworkCallback network = new ConnectivityManager.NetworkCallback() {
        @Override public void onLost(Network lost) { if (wifi != null && wifi.network.equals(lost)) finish("Wi-Fi disconnected. Start casting again after reconnecting."); }
        @Override public void onLinkPropertiesChanged(Network changed, LinkProperties properties) { if (wifi != null && wifi.network.equals(changed) && !wifi.matches(NotebookCastService.this)) finish("Wi-Fi address changed. Start casting again."); }
    };
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { finish("Casting stopped when the screen turned off."); }
    };
    private final DisplayManager.DisplayListener displayListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int id) { }
        @Override public void onDisplayRemoved(int id) { }
        @Override public void onDisplayChanged(int id) {
            if (id != Display.DEFAULT_DISPLAY || Build.VERSION.SDK_INT >= 34) return;
            Point size = new Point(); getSystemService(WindowManager.class).getDefaultDisplay().getRealSize(size); resize(size.x, size.y);
        }
    };

    private void finish(String status) {
        if (closing) return;
        setSharing(false); closing = true; lastStatus = status == null ? "Casting stopped." : status;
        if (server != null) server.close(); stopForeground(true); stopSelf();
    }
    @Override public void onDestroy() {
        if (!closing) { setSharing(false); closing = true; lastStatus = "Casting stopped."; }
        main.removeCallbacksAndMessages(null);
        if (networkRegistered) getSystemService(ConnectivityManager.class).unregisterNetworkCallback(network);
        if (screenRegistered) unregisterReceiver(screenOff);
        if (displayRegistered) getSystemService(DisplayManager.class).unregisterDisplayListener(displayListener);
        if (server != null) server.close();
        if (virtualDisplay != null) virtualDisplay.release();
        if (projection != null) projection.stop();
        final ImageReader retired = reader;
        if (retired != null) retired.setOnImageAvailableListener(null, null);
        if (capture != null) {
            // Let queued retired-reader cleanup finish before the final cleanup.
            capture.removeCallbacks(encodeFrame);
            capture.post(() -> {
                if (pendingImage != null) { pendingImage.close(); pendingImage = null; }
                if (retired != null) retired.close();
                if (bitmap != null) bitmap.recycle(); bitmap = null; packed = null;
                captureThread.quitSafely();
            });
        }
        if (instance == this) instance = null;
        super.onDestroy();
    }
}
