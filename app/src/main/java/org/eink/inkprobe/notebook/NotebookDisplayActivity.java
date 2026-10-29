package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.Intent;
import android.graphics.Point;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.OrientationEventListener;
import android.view.Display;
import android.view.WindowManager;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Foreground-only sensor rotation shared by all production notebook screens. */
public abstract class NotebookDisplayActivity extends Activity implements NotebookDisplayState.Target {
    private static NotebookDisplayState sharedDisplay;
    private NotebookDisplayState displayState;
    private final Handler rotationHandler = new Handler(Looper.getMainLooper());
    private final NotebookSensorRotation rotation = new NotebookSensorRotation();
    private OrientationEventListener orientationSensor;
    private boolean displayResumed, naturalLandscape;
    private NotebookDeviceStatus headerStatus;
    private final Runnable rotateWhenSettled = () -> {
        if (!displayResumed || NotebookSettings.orientation(this) != NotebookSettings.Orientation.SENSOR) return;
        int angle = rotation.ready(SystemClock.uptimeMillis());
        if (angle >= 0) shareFacing(NotebookSensorRotation.facing(angle, naturalLandscape));
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Display display = getWindowManager().getDefaultDisplay();
        int displayRotation = display.getRotation();
        Point physicalSize = new Point(); display.getRealSize(physicalSize);
        // A background Activity's resource configuration may already reflect a requested
        // direction while the physical display still belongs to a different foreground UI.
        naturalLandscape = NotebookSensorRotation.naturalLandscape(displayRotation, physicalSize.x, physicalSize.y);
        if (sharedDisplay == null) {
            int angle = (360 - displayRotation * 90) % 360;
            sharedDisplay = new NotebookDisplayState(NotebookSettings.displayFacing(this,
                NotebookSensorRotation.facing(angle, naturalLandscape)));
        }
        displayState = sharedDisplay;
        orientationSensor = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override public void onOrientationChanged(int angle) {
                if (!displayResumed) return;
                rotation.sample(angle, SystemClock.uptimeMillis());
                scheduleRotation();
            }
        };
        applyDisplayPreference();
        displayState.attach(this);
    }

    @Override protected void onResume() {
        super.onResume();
        displayState.attach(this);
        displayResumed = true; refreshDisplayOrientation();
        refreshScreenOn();
        if (headerStatus != null) headerStatus.start();
    }

    @Override protected void onPause() {
        displayResumed = false;
        refreshScreenOn();
        if (headerStatus != null) headerStatus.stop();
        orientationSensor.disable(); rotationHandler.removeCallbacks(rotateWhenSettled); rotation.reset();
        super.onPause();
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) NotebookImages.clearCache();
    }
    @Override public void onLowMemory() { super.onLowMemory(); NotebookImages.clearCache(); }

    @Override protected void onDestroy() {
        if (displayState != null) displayState.detach(this);
        super.onDestroy();
    }

    protected final void refreshDisplayOrientation() {
        orientationSensor.disable(); rotationHandler.removeCallbacks(rotateWhenSettled); rotation.reset();
        applyDisplayPreference();
        if (displayResumed && NotebookSettings.orientation(this) == NotebookSettings.Orientation.SENSOR
                && orientationSensor.canDetectOrientation()) orientationSensor.enable();
    }

    protected final boolean canUseOrientationSensor() { return orientationSensor.canDetectOrientation(); }

    protected final NotebookDeviceStatus deviceSettingsButton() {
        if (headerStatus == null) headerStatus = new NotebookDeviceStatus(this, () -> true);
        return headerStatus;
    }

    protected final void addScreenHeader(LinearLayout root, TextView title) {
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        int height = Math.round(36 * getResources().getDisplayMetrics().density);
        title.setSingleLine(true); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        header.addView(title, new LinearLayout.LayoutParams(0, height, 1));
        header.addView(deviceSettingsButton(), new LinearLayout.LayoutParams(-2, height));
        root.addView(header);
    }

    protected void openAppSettings() {
        if (this instanceof NotebookSettingsActivity) return;
        startActivity(new Intent(this, NotebookSettingsActivity.class)); overridePendingTransition(0, 0);
    }

    protected final void refreshScreenOn() {
        // A window hint only: never acquire a CPU wake lock or override cover/power-button sleep.
        if (displayResumed && NotebookSettings.keepScreenOn(this))
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    protected final void setWritingContact(boolean down) {
        rotation.setWriting(down, SystemClock.uptimeMillis()); scheduleRotation();
    }

    private void scheduleRotation() {
        rotationHandler.removeCallbacks(rotateWhenSettled);
        if (!displayResumed) return;
        long delay = rotation.remaining(SystemClock.uptimeMillis());
        if (delay >= 0) rotationHandler.postDelayed(rotateWhenSettled, delay);
    }

    private void applyDisplayPreference() {
        NotebookSettings.Orientation preference = NotebookSettings.orientation(this);
        if (preference == NotebookSettings.Orientation.SENSOR) {
            // Inherit the app's accepted direction, rather than this Activity's old display
            // configuration. Sensor settling is only for a genuinely new physical turn.
            onDisplayFacing(displayState.current());
        } else shareFacing(preference == NotebookSettings.Orientation.LANDSCAPE
            ? NotebookSensorRotation.Facing.LANDSCAPE : NotebookSensorRotation.Facing.PORTRAIT);
    }

    private void shareFacing(NotebookSensorRotation.Facing facing) {
        if (displayState.publish(facing)) NotebookSettings.saveDisplayFacing(this, displayState.current());
        onDisplayFacing(displayState.current());
    }

    @Override public final void onDisplayFacing(NotebookSensorRotation.Facing facing) {
        if (isFinishing() || isDestroyed()) return;
        int requested;
        switch (facing) {
            case LANDSCAPE: requested = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; break;
            case REVERSE_LANDSCAPE: requested = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE; break;
            case REVERSE_PORTRAIT: requested = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT; break;
            default: requested = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        if (getRequestedOrientation() != requested) setRequestedOrientation(requested);
    }
}
