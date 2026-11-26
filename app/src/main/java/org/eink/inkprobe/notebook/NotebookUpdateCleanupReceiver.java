package org.eink.inkprobe.notebook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Cleanup follows Android's successful package replacement, even before the next app launch. */
public final class NotebookUpdateCleanupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction()))return;
        PendingResult pending=goAsync();
        NotebookUpdates.get(context).afterPackageReplaced(pending::finish);
    }
}
