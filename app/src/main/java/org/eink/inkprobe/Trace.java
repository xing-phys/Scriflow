package org.eink.inkprobe;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Point collection is independent of all display operations. Coordinates are surface pixels. */
final class Trace {
    static final class Point {
        final float x, y, pressure;
        final long time, received;
        Point(float x, float y, long time, float pressure, long received) {
            this.x = x; this.y = y; this.time = time; this.pressure = pressure;
            this.received = received;
        }
    }
    static final class Stroke {
        final String source;
        final List<Point> points = new ArrayList<>();
        boolean cancelled;
        Stroke(String source) { this.source = source; }
    }
    private final List<Stroke> strokes = new ArrayList<>();
    private Stroke active;
    private int pointCount, missedDown;
    private final CallbackTiming timing = new CallbackTiming();

    synchronized void point(String source, int action, float x, float y, long time, float pressure) {
        if (action == 0) {
            if (active != null) active.cancelled = true;
            active = new Stroke(source);
            strokes.add(active);
        } else if (active == null) {
            if (action != 2) return;
            missedDown++;
            active = new Stroke(source);
            strokes.add(active);
        }
        long received = SystemClock.uptimeMillis();
        active.points.add(new Point(x, y, time, pressure, received));
        timing.record(time, received);
        pointCount++;
        if (action == 1 || action == 3) {
            active.cancelled = action == 3;
            active = null;
        }
    }
    synchronized void cancel() {
        if (active != null) active.cancelled = true;
        active = null;
    }
    synchronized void clear() { strokes.clear(); active = null; pointCount = 0; missedDown = 0; timing.reset(); }
    synchronized String summary() {
        return strokes.size() + " strokes / " + pointCount + " points"
            + (active != null ? " / pen down" : " / pen up") + " / missing DOWN: " + missedDown;
    }
    synchronized JSONObject json() throws JSONException {
        JSONObject result = new JSONObject();
        result.put("coordinateSpace", "writing-surface physical pixels");
        result.put("pointCount", pointCount);
        result.put("missingDown", missedDown);
        CallbackTiming.Snapshot t = timing.snapshot();
        JSONObject stats = new JSONObject();
        stats.put("sampleCount", t.sampleCount); stats.put("validDelayCount", t.validDelayCount);
        stats.put("negativeDelayCount", t.negativeDelayCount); stats.put("invalidEventTimeCount", t.invalidEventTimeCount);
        stats.put("windowSampleCount", t.windowSampleCount);
        stats.put("p50DelayMs", t.p50DelayMs); stats.put("p95DelayMs", t.p95DelayMs); stats.put("maxDelayMs", t.maxDelayMs);
        stats.put("p50EventGapMs", t.p50EventGapMs); stats.put("p95EventGapMs", t.p95EventGapMs); stats.put("maxEventGapMs", t.maxEventGapMs);
        stats.put("meaning", "callback delivery after framework processing; not optical latency; gaps include pen pauses");
        result.put("callbackTiming", stats);
        JSONArray list = new JSONArray();
        for (Stroke stroke : strokes) {
            JSONObject s = new JSONObject();
            s.put("source", stroke.source);
            s.put("cancelled", stroke.cancelled);
            s.put("complete", stroke != active);
            JSONArray points = new JSONArray();
            JSONArray received = new JSONArray();
            for (Point p : stroke.points) {
                JSONArray a = new JSONArray();
                a.put(p.x); a.put(p.y); a.put(p.time); a.put(p.pressure);
                points.put(a);
                received.put(p.received);
            }
            s.put("points_x_y_time_pressure", points);
            s.put("callbackArrivalUptimeMs", received);
            list.put(s);
        }
        result.put("strokes", list);
        return result;
    }
    synchronized void draw(Canvas c, Paint paint) {
        for (Stroke s : strokes) {
            if (s.cancelled || s.points.isEmpty()) continue;
            Point first = s.points.get(0);
            if (s.points.size() == 1) { c.drawPoint(first.x, first.y, paint); continue; }
            Path path = new Path(); path.moveTo(first.x, first.y);
            for (int i = 1; i < s.points.size(); i++) {
                Point p = s.points.get(i); path.lineTo(p.x, p.y);
            }
            c.drawPath(path, paint);
        }
    }
}
