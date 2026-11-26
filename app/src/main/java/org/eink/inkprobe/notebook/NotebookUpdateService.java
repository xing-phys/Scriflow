package org.eink.inkprobe.notebook;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Keeps an explicitly requested update download active while Settings is covered. */
public final class NotebookUpdateService extends Service {
    private static final String CHANNEL="app_updates"; private static final int NOTIFICATION=4303;
    private NotebookUpdates updates; private final Runnable listener=this::refresh;
    @Override public void onCreate(){
        super.onCreate();getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel(CHANNEL,"App updates",NotificationManager.IMPORTANCE_LOW));
        updates=NotebookUpdates.get(this);updates.observe(listener);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){startForeground(NOTIFICATION,notification());refresh();return START_NOT_STICKY;}
    private Notification notification(){
        NotebookUpdates.Snapshot state=updates.snapshot();
        Intent open=new Intent(this,NotebookSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder result=new Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Scriflow update").setContentText(state.phase==NotebookUpdates.Phase.VERIFYING?"Verifying update…":"Downloading update…")
            .setOnlyAlertOnce(true).setOngoing(true).setContentIntent(pending);
        if(state.total>0)result.setProgress(100,(int)Math.min(100,state.bytes*100.0/state.total),false);else result.setProgress(0,0,true);
        return result.build();
    }
    private void refresh(){if(!updates.transferring()){stopForeground(true);stopSelf();}else getSystemService(NotificationManager.class).notify(NOTIFICATION,notification());}
    @Override public void onDestroy(){updates.removeObserver(listener);super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
