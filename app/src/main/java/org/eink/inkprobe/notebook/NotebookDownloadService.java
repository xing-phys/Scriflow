package org.eink.inkprobe.notebook;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Keeps active transfers alive while the user moves elsewhere in Scriflow. */
public final class NotebookDownloadService extends Service {
    private static final String CHANNEL = "browser_downloads";
    private static final int NOTIFICATION = 4302;
    private NotebookDownloads downloads;
    private final Runnable update = this::refresh;

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager notifications = getSystemService(NotificationManager.class);
        notifications.createNotificationChannel(new NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW));
        downloads = NotebookDownloads.get(this); downloads.observe(update);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION, notification());
        refresh(); return START_NOT_STICKY;
    }
    private Notification notification() {
        int count = downloads.activeCount();
        Intent open = new Intent(this, NotebookBrowserActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(NotebookBrowserActivity.SHOW_DOWNLOADS, true);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Scriflow downloads").setContentText(count + (count == 1 ? " download in progress" : " downloads in progress"))
            .setContentIntent(pending).setOnlyAlertOnce(true).setOngoing(true).build();
    }
    private void refresh() {
        if (downloads.activeCount() == 0) { stopForeground(true); stopSelf(); }
        else getSystemService(NotificationManager.class).notify(NOTIFICATION, notification());
    }
    @Override public void onDestroy() { downloads.removeObserver(update); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
