package android.eink.view;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;

/** Synthetic polygon brush for failure injection, not a Huawei brush fidelity model. */
public final class HandPen {
    public static final List<float[]> samples = new ArrayList<>();
    public static float capturedWidth;
    public static int capturedColor, capturedType;
    public static boolean capturedOutline;
    public interface InputObserver { void input(int action, float x, float y, long time, float pressure); }
    public static InputObserver observer;
    public static boolean failNextInput, nullTrack;
    public static RectF trackBoundsOverride;
    public static ISurface surface;
    private Path path;
    public HandPen(int type, boolean outline) { capturedType = type; capturedOutline = outline; }
    public void setCanvasBoard(Canvas canvas) { }
    public void setSurfaceBoard(ISurface board) { surface = board; }
    public void setStrokeWidth(float width) { capturedWidth = width; }
    public void setStrokeColor(int color) { capturedColor = color; }
    public void onTouchEvent(int action, float x, float y, long time, float pressure) {
        if (action == MotionEvent.ACTION_DOWN) { path = new Path(); path.moveTo(x, y); samples.clear(); }
        else path.lineTo(x, y);
        samples.add(new float[]{action, x, y, time, pressure});
        if (observer != null) observer.input(action, x, y, time, pressure);
        if (failNextInput) { failNextInput = false; throw new IllegalStateException("Injected native input failure"); }
        if (action == MotionEvent.ACTION_UP) path.close();
    }
    public IPenTrack getTrack() {
        if (nullTrack) return null;
        return new IPenTrack() {
            @Override public Path getPath() { return path; }
            @Override public RectF getBounds() {
                if (trackBoundsOverride != null) return trackBoundsOverride;
                float left = Float.POSITIVE_INFINITY, top = Float.POSITIVE_INFINITY;
                float right = Float.NEGATIVE_INFINITY, bottom = Float.NEGATIVE_INFINITY;
                for (float[] sample : samples) {
                    left = Math.min(left, sample[1]); top = Math.min(top, sample[2]);
                    right = Math.max(right, sample[1]); bottom = Math.max(bottom, sample[2]);
                }
                return new RectF(left - capturedWidth / 2, top - capturedWidth / 2,
                    right + capturedWidth / 2, bottom + capturedWidth / 2);
            }
        };
    }
}
