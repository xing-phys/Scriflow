package android.eink.view;

import android.graphics.Canvas;
import android.graphics.Path;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;

/** Synthetic polygon brush for failure injection, not a Huawei brush fidelity model. */
public final class HandPen {
    public static final List<float[]> samples = new ArrayList<>();
    public static float capturedWidth;
    private Path path;
    public HandPen(int type, boolean outline) { }
    public void setCanvasBoard(Canvas canvas) { }
    public void setStrokeWidth(float width) { capturedWidth = width; }
    public void setStrokeColor(int color) { }
    public void onTouchEvent(int action, float x, float y, long time, float pressure) {
        if (action == MotionEvent.ACTION_DOWN) { path = new Path(); path.moveTo(x, y); samples.clear(); }
        else path.lineTo(x, y);
        samples.add(new float[]{action, x, y, time, pressure});
        if (action == MotionEvent.ACTION_UP) path.close();
    }
    public IPenTrack getTrack() { return () -> path; }
}
