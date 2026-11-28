package org.eink.inkprobe.notebook;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns one local sharing session while the user moves between app screens. */
public final class NotebookShareService extends Service {
    private static final String CHANNEL = "wifi_sharing", STOP = "org.eink.inkprobe.STOP_WIFI_SHARING";
    private static final int NOTIFICATION = 4303;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    // The Activity and the service lifecycle use the same Android main thread.
    private static NotebookShareService current;
    private static String status = "Sharing is off.";
    private static boolean starting;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ConnectivityManager.NetworkCallback network = new ConnectivityManager.NetworkCallback() {
        @Override public void onLost(Network lost) {
            if (wifi != null && wifi.network.equals(lost)) finish("Wi-Fi disconnected. Start sharing after reconnecting.");
        }
        @Override public void onLinkPropertiesChanged(Network changed, LinkProperties properties) {
            if (wifi == null || !wifi.network.equals(changed)) return;
            for (android.net.LinkAddress link : properties.getLinkAddresses()) if (wifi.address.equals(link.getAddress())) return;
            finish("The Wi-Fi address changed. Start sharing again.");
        }
    };
    private NotebookCastNetwork wifi;
    private NotebookShareServer server;
    private NotebookShareStorage storage;
    private ExecutorService files;
    private boolean watchingWifi, staging;
    private String code, fileStatus = "";

    static NotebookShareService active() { return current; }
    static String status() { return status; }
    static boolean starting() { return starting; }
    NotebookShareServer server() { return server; }
    String code() { return code; }
    String fileStatus() { return fileStatus; }
    boolean staging() { return staging; }

    static void start(Context context) {
        if (starting || current != null) return;
        status = "Starting Wi-Fi sharing…";
        starting = true;
        try { context.startForegroundService(new Intent(context, NotebookShareService.class)); }
        catch (RuntimeException error) {
            status = "Sharing could not start. Return to Scriflow and try again.";
            starting = false;
        }
    }

    void stop() { finish("Sharing stopped."); }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && STOP.equals(intent.getAction())) { stop(); return START_NOT_STICKY; }
        if (server != null) return START_NOT_STICKY;
        if (!starting || intent == null) { stopSelf(); return START_NOT_STICKY; }
        try {
            startForeground(NOTIFICATION, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            wifi = NotebookCastNetwork.find(this);
            if (wifi == null) { finish("Connect the tablet to Wi-Fi first."); return START_NOT_STICKY; }
            storage = new NotebookShareStorage(this);
            files = Executors.newSingleThreadExecutor(task -> new Thread(task, "wifi-share-staging"));
            files.execute(storage::cleanOldSessions);
            code = pairingCode();
            server = new NotebookShareServer(wifi.address, code, assets(), storage);
            getSystemService(ConnectivityManager.class).registerNetworkCallback(
                new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), network, main);
            watchingWifi = true;
            if (!wifi.matches(this)) { finish("Wi-Fi changed. Start sharing again."); return START_NOT_STICKY; }
            fileStatus = "";
            status = "Sharing is on.";
            current = this;
        } catch (IOException | RuntimeException failure) {
            finish("Sharing could not start. Check Wi-Fi and try again.");
        } finally {
            starting = false;
        }
        return START_NOT_STICKY;
    }

    void stageFiles(List<Uri> selected) {
        if (staging || server == null || selected.isEmpty()) return;
        staging = true;
        fileStatus = "Preparing files…";
        NotebookShareServer target = server;
        NotebookShareStorage destination = storage;
        files.execute(() -> {
            int count = 0;
            String firstError = null;
            for (Uri uri : selected) {
                if (target.closed()) break;
                try {
                    destination.stage(uri, target);
                    count++;
                } catch (IOException | RuntimeException failure) {
                    if (firstError == null) firstError = failure instanceof IOException && failure.getMessage() != null
                        ? failure.getMessage() : "A selected file could not be read.";
                }
            }
            String result = count == selected.size() ? (count == 1 ? "File ready to download." : count + " files ready to download.")
                : count + " of " + selected.size() + " files shared. " + firstError;
            main.post(() -> {
                if (server != target) return;
                staging = false;
                fileStatus = result;
            });
        });
    }

    void withdraw(NotebookShareServer.Item item) {
        File file = server.withdraw(item);
        if (file != null) files.execute(file::delete);
    }

    private Notification notification() {
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel(CHANNEL, "Wi-Fi Sharing", NotificationManager.IMPORTANCE_LOW));
        Intent panel = new Intent(this, NotebookShareActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open = PendingIntent.getActivity(this, NOTIFICATION, panel, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, NOTIFICATION, new Intent(this, NotebookShareService.class).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Scriflow Wi-Fi Sharing").setContentText("Tap for the address and pairing code.")
            .setOngoing(true).setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open)
            .addAction(new Notification.Action.Builder(null, "Stop sharing", stop).build()).build();
    }

    private Map<String, byte[]> assets() throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        for (String name : new String[]{"index.html", "share.css", "share.js"}) {
            try (InputStream input = getAssets().open("share/" + name); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                files.put(name, output.toByteArray());
            }
        }
        return files;
    }

    private static String pairingCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder code = new StringBuilder(8);
        for (int i = 0; i < 8; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return code.toString();
    }

    private void finish(String reason) {
        status = reason;
        releaseSession();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releaseSession() {
        if (current == this) current = null;
        if (server != null) { server.close(); server = null; }
        if (watchingWifi) {
            getSystemService(ConnectivityManager.class).unregisterNetworkCallback(network);
            watchingWifi = false;
        }
        if (files != null) {
            files.execute(storage::cleanup);
            files.shutdown();
            files = null;
        }
        wifi = null;
        code = null;
        staging = false;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        if (current == this) status = "Sharing stopped.";
        releaseSession();
        super.onDestroy();
    }
}
