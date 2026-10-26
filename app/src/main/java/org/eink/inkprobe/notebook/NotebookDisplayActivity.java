package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.OrientationEventListener;
import android.view.Surface;

/** Foreground-only sensor rotation shared by all production notebook screens. */
public abstract class NotebookDisplayActivity extends Activity {
    private final Handler rotationHandler = new Handler(Looper.getMainLooper());
    private final NotebookSensorRotation rotation = new NotebookSensorRotation();
    private OrientationEventListener orientationSensor;
    private boolean displayResumed, naturalLandscape;
    private final Runnable rotateWhenSettled = () -> {
        if (!displayResumed || NotebookSettings.orientation(this) != NotebookSettings.Orientation.SENSOR) return;
        int angle = rotation.ready(SystemClock.uptimeMillis());
        if (angle >= 0) requestFacing(NotebookSensorRotation.facing(angle, naturalLandscape));
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
        boolean sideways = displayRotation == Surface.ROTATION_90 || displayRotation == Surface.ROTATION_270;
        naturalLandscape = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) != sideways;
        orientationSensor = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override public void onOrientationChanged(int angle) {
                if (!displayResumed) return;
                rotation.sample(angle, SystemClock.uptimeMillis());
                scheduleRotation();
            }
        };
        applyDisplayPreference();
    }

    @Override protected void onResume() {
        super.onResume(); displayResumed = true; refreshDisplayOrientation();
    }

    @Override protected void onPause() {
        displayResumed = false;
        orientationSensor.disable(); rotationHandler.removeCallbacks(rotateWhenSettled); rotation.reset();
        super.onPause();
    }

    protected final void refreshDisplayOrientation() {
        orientationSensor.disable(); rotationHandler.removeCallbacks(rotateWhenSettled); rotation.reset();
        applyDisplayPreference();
        if (displayResumed && NotebookSettings.orientation(this) == NotebookSettings.Orientation.SENSOR
                && orientationSensor.canDetectOrientation()) orientationSensor.enable();
    }

    protected final boolean canUseOrientationSensor() { return orientationSensor.canDetectOrientation(); }

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
            // Hold the current direction until a stable sensor reading requests a concrete
            // orientation. Some e-ink firmware ignores Android's FULL_SENSOR request.
            int angle = (360 - getWindowManager().getDefaultDisplay().getRotation() * 90) % 360;
            requestFacing(NotebookSensorRotation.facing(angle, naturalLandscape));
        } else if (getRequestedOrientation() != preference.requested) setRequestedOrientation(preference.requested);
    }

    private void requestFacing(NotebookSensorRotation.Facing facing) {
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
