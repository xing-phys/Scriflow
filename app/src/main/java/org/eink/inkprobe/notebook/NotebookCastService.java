package org.eink.inkprobe.notebook;

import android.app.Activity;
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
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.Display;
import android.view.WindowManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/** Runs in :cast: image memory, JPEG GC, readback and HTTP never share the pen/UI process. */
public final class NotebookCastService extends Service {
    private static final String STOP_ACTION = "org.eink.inkprobe.STOP_CAST", CHANNEL = "screen_cast";
    private static final int NOTIFICATION = 4301;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final NotebookCastPacer pacer = new NotebookCastPacer();
    private final ByteArrayOutputStream jpeg = new ByteArrayOutputStream(256 * 1024);
    private final Messenger commands = new Messenger(new Handler(Looper.getMainLooper(), this::command));
    private Messenger client;
    private IBinder.DeathRecipient clientDeath;
    private HandlerThread captureThread;
    private Handler capture;
    private NotebookCastServer server;
    private NotebookCastNetwork wifi;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private volatile ImageReader reader;
    private Bitmap bitmap;
    private ByteBuffer packed;
    private volatile boolean closing, sharing;
    private boolean attached, encodeQueued, capturedVisible = true;
    private boolean networkRegistered, screenRegistered, displayRegistered;
    private int sourceWidth, sourceHeight, visibility;
    private long lastLease, focusLostAt;
    private volatile long encodedFrames, lastEncodeMillis;
    private String status = "Starting screen cast…";

    @Override public IBinder onBind(Intent intent) { return commands.getBinder(); }
    private boolean command(Message message) {
        switch (message.what) {
            case NotebookCastController.REGISTER:
                if (client != null && clientDeath != null) client.getBinder().unlinkToDeath(clientDeath, 0);
                client = message.replyTo;
                if (client == null) return true;
                clientDeath = () -> {
                    if (server != null) server.frames.sharing(false, System.nanoTime());
                    main.post(() -> finish("Casting stopped when Scriflow closed."));
                };
                try { client.getBinder().linkToDeath(clientDeath, 0); } catch (RemoteException dead) { finish("Scriflow closed."); }
                lastLease = SystemClock.uptimeMillis(); publishStatus(); return true;
            case NotebookCastController.VISIBILITY:
                lastLease = SystemClock.uptimeMillis();
                if (visibility != message.arg1) focusLostAt = lastLease;
                visibility = message.arg1; pacer.contact(message.arg2 != 0); updateCapture(); return true;
            case NotebookCastController.CONTACT: pacer.contact(message.arg1 != 0); return true;
            case NotebookCastController.STOP: finish("Casting stopped."); return true;
            default: return false;
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP_ACTION.equals(intent.getAction())) { finish("Casting stopped."); return START_NOT_STICKY; }
        if (server != null) return START_NOT_STICKY;
        Intent consent = intent == null ? null : intent.getParcelableExtra("consent");
        String password = intent == null ? null : intent.getStringExtra("password");
        String expectedAddress = intent == null ? null : intent.getStringExtra("address");
        if (intent != null) { intent.removeExtra("password"); intent.removeExtra("consent"); }
        if (consent == null || password == null) { finish("Casting stopped."); return START_NOT_STICKY; }
        try {
            getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL, "Screen cast", NotificationManager.IMPORTANCE_LOW));
            PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, NotebookCastActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent stop = PendingIntent.getService(this, 1, new Intent(this, NotebookCastService.class).setAction(STOP_ACTION), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(this, CHANNEL).setSmallIcon(org.eink.inkprobe.R.drawable.ic_cast)
                .setContentTitle("Scriflow screen cast").setContentText("Sharing over local Wi-Fi. Tap for address and controls.")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(null, "Stop casting", stop).build()).build();
            startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            wifi = NotebookCastNetwork.find(this);
            if (wifi == null || !wifi.address.getHostAddress().equals(expectedAddress) || !wifi.matches(this))
                throw new IOException("Wi-Fi changed. Connect and start again.");
            Map<String, byte[]> assets = new HashMap<>();
            for (String name : new String[]{"index.html", "viewer.js", "viewer.css"}) {
                try (InputStream input = getAssets().open("cast/" + name); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    assets.put(name, output.toByteArray());
                }
            }
            try { server = new NotebookCastServer(wifi.address, 8080, password, assets); }
            catch (java.net.BindException busy) { server = new NotebookCastServer(wifi.address, 0, password, assets); }
            captureThread = new HandlerThread("screen-cast-capture", android.os.Process.THREAD_PRIORITY_LOWEST);
            captureThread.start(); capture = new Handler(captureThread.getLooper());
            projection = getSystemService(MediaProjectionManager.class).getMediaProjection(Activity.RESULT_OK, consent);
            if (projection == null) throw new IOException("Screen capture permission was not granted.");
            projection.registerCallback(new MediaProjection.Callback() {
                @Override public void onStop() { finish("Android stopped screen capture. Start again to reconnect."); }
                @Override public void onCapturedContentResize(int w, int h) { if (w > 0 && h > 0) resize(w, h); }
                @Override public void onCapturedContentVisibilityChanged(boolean visible) { capturedVisible = visible; updateCapture(); }
            }, main);
            Point size = new Point(); getSystemService(WindowManager.class).getDefaultDisplay().getRealSize(size); resize(size.x, size.y);
            getSystemService(ConnectivityManager.class).registerNetworkCallback(
                new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), network, main);
            networkRegistered = true;
            IntentFilter screen = new IntentFilter(Intent.ACTION_SCREEN_OFF); screen.addAction(Intent.ACTION_SHUTDOWN);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, screen, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(screenOff, screen);
            screenRegistered = true;
            getSystemService(DisplayManager.class).registerDisplayListener(displayListener, main); displayRegistered = true;
            if (!wifi.matches(this)) throw new IOException("Wi-Fi changed. Start casting again.");
            lastLease = SystemClock.uptimeMillis(); status = "Cast paused · Open a Scriflow page to share";
            publishStatus(); main.post(watch);
        } catch (IOException | RuntimeException error) {
            finish(error instanceof IOException ? error.getMessage() : "Screen capture could not start. Check Android permission and try again.");
        }
        return START_NOT_STICKY;
    }

    private void updateCapture() {
        if (closing || server == null) return;
        long now = SystemClock.uptimeMillis();
        boolean allowed = visibility > 0 && capturedVisible && now-lastLease < 2500;
        boolean focused = allowed && visibility == 2;
        if (focused) {
            if (!sharing) {
                server.frames.sharing(true, System.nanoTime()); sharing = true;
                // A paused reader may already have a pending image and no new callback.
                // Drain it on the worker so the compositor can supply a fresh app frame.
                if (capture != null) capture.post(this::requestImage);
            }
        } else if (allowed && now-focusLostAt < 250) {
            // Revoke all new pixels immediately, retaining only the previous safe image during app-popup focus handoff.
            server.frames.hold(); sharing = false;
        } else { server.frames.sharing(false, System.nanoTime()); sharing = false; }
        // Focus handoffs never reconfigure the display. Genuine background/private/no-viewer transitions detach it.
        boolean wantSurface = allowed && (focused || (attached && now-focusLostAt < 250)) && server.hasRecentViewer();
        if (virtualDisplay != null && attached != wantSurface) {
            try {
                virtualDisplay.setSurface(wantSurface && reader != null ? reader.getSurface() : null); attached = wantSurface;
                if (wantSurface && capture != null) capture.post(this::requestImage);
            }
            catch (IllegalStateException | IllegalArgumentException stopped) { finish("Android ended screen capture. Start casting again."); return; }
        }
        String nextStatus = focused ? "Casting Scriflow · View only" : "Cast paused · Open a Scriflow page to share";
        if (!nextStatus.equals(status)) { status = nextStatus; publishStatus(); }
    }
    private final Runnable watch = new Runnable() {
        @Override public void run() { if (!closing) { updateCapture(); main.postDelayed(this, 100); } }
    };

    private void resize(int w, int h) {
        if (closing || projection == null || (w == sourceWidth && h == sourceHeight)) return;
        if (server != null) server.frames.hold(); sharing = false; sourceWidth = w; sourceHeight = h;
        double scale = Math.min(1, (double)NotebookCastPacer.MAX_EDGE/Math.max(w,h));
        int width = Math.max(1, (int)Math.round(w*scale)), height = Math.max(1, (int)Math.round(h*scale));
        ImageReader old = reader;
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
        reader.setOnImageAvailableListener(source -> {
            // Do not acquire/copy every compositor frame. Keep the reader's latest pending image until the next budget slot.
            requestImage();
        }, capture);
        if (virtualDisplay == null) virtualDisplay = projection.createVirtualDisplay("Scriflow screen cast", width, height,
            getResources().getConfiguration().densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, null, null, main);
        else { virtualDisplay.setSurface(null); virtualDisplay.resize(width, height, getResources().getConfiguration().densityDpi); }
        attached = false;
        if (old != null) {
            old.setOnImageAvailableListener(null, null);
            capture.post(() -> {
                capture.removeCallbacks(encodeFrame); encodeQueued = false; pacer.reset(); old.close();
                if (!closing) { encodeQueued = true; capture.postDelayed(encodeFrame, 60); }
            });
        }
        updateCapture();
    }

    private void requestImage() {
        if (!encodeQueued && !closing) {
            encodeQueued = true; capture.postDelayed(encodeFrame, pacer.delay(SystemClock.uptimeMillis()));
        }
    }

    private final Runnable encodeFrame = () -> {
        encodeQueued = false;
        long start = SystemClock.uptimeMillis();
        Image image = null;
        try {
            if (closing || server == null || !sharing || !server.hasRecentViewer()) return;
            ImageReader current = reader; if (current == null) return;
            image = current.acquireLatestImage(); if (image == null) return;
            long ticket = server.frames.ticket(image.getTimestamp()); if (ticket < 0) return;
            int w = image.getWidth(), h = image.getHeight();
            if (bitmap == null || bitmap.getWidth() != w || bitmap.getHeight() != h) {
                if (bitmap != null) bitmap.recycle();
                bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); packed = ByteBuffer.allocateDirect(w*h*4);
            }
            Image.Plane plane = image.getPlanes()[0];
            if (plane.getPixelStride() != 4) throw new IllegalStateException("Unsupported capture format");
            ByteBuffer source = plane.getBuffer();
            if (plane.getRowStride() == w*4) { source.rewind(); bitmap.copyPixelsFromBuffer(source); }
            else {
                packed.clear();
                for (int y = 0; y < h; y++) {
                    source.limit(source.capacity()); source.position(y*plane.getRowStride()); source.limit(source.position()+w*4); packed.put(source);
                }
                packed.flip(); bitmap.copyPixelsFromBuffer(packed);
            }
            jpeg.reset();
            if (bitmap.compress(Bitmap.CompressFormat.JPEG, NotebookCastPacer.JPEG_QUALITY, jpeg)
                    && server.frames.publish(ticket, jpeg.toByteArray())) encodedFrames++;
        } catch (IllegalStateException retired) {
            // A queued callback may belong to a reader retired by rotation.
        } catch (RuntimeException error) { main.post(() -> finish("Screen capture failed. Start casting again.")); }
        finally {
            if (image != null) image.close();
            lastEncodeMillis = SystemClock.uptimeMillis()-start; pacer.completed(start, SystemClock.uptimeMillis());
        }
    };

    @Override protected void dump(java.io.FileDescriptor fd, java.io.PrintWriter writer, String[] args) {
        writer.println("capture_process=" + android.os.Process.myPid());
        writer.println("capture_attached=" + attached);
        writer.println("frames_encoded=" + encodedFrames);
        writer.println("last_encode_ms=" + lastEncodeMillis);
    }

    private void publishStatus() {
        if (client == null) return;
        Message message = Message.obtain(null, NotebookCastController.STATUS);
        message.getData().putBoolean("running", server != null && !closing);
        message.getData().putBoolean("starting", server == null && !closing);
        message.getData().putString("address", server != null && !closing ? server.address() : "");
        message.getData().putString("status", status);
        try { client.send(message); } catch (RemoteException dead) { main.post(() -> finish("Casting stopped when Scriflow closed.")); }
    }
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
            Point size = new Point(); getSystemService(WindowManager.class).getDefaultDisplay().getRealSize(size); resize(size.x,size.y);
        }
    };
    private void finish(String reason) {
        if (closing) return;
        closing = true; sharing = false; status = reason == null ? "Casting stopped." : reason;
        if (server != null) server.close(); publishStatus(); stopForeground(true); stopSelf();
    }
    @Override public void onDestroy() {
        if (!closing) { closing = true; sharing = false; status = "Casting stopped."; if (server != null) server.close(); publishStatus(); }
        main.removeCallbacksAndMessages(null);
        if (client != null && clientDeath != null) client.getBinder().unlinkToDeath(clientDeath,0);
        if (networkRegistered) getSystemService(ConnectivityManager.class).unregisterNetworkCallback(network);
        if (screenRegistered) unregisterReceiver(screenOff);
        if (displayRegistered) getSystemService(DisplayManager.class).unregisterDisplayListener(displayListener);
        if (virtualDisplay != null) virtualDisplay.release();
        if (projection != null) projection.stop();
        ImageReader retired = reader;
        if (retired != null) retired.setOnImageAvailableListener(null,null);
        if (capture != null) {
            capture.removeCallbacks(encodeFrame);
            capture.post(() -> { if (retired != null) retired.close(); if (bitmap != null) bitmap.recycle(); bitmap = null; packed = null; captureThread.quitSafely(); });
        }
        super.onDestroy();
    }
}
