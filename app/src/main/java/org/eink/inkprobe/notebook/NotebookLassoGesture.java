package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Standard pointer samples define selection; the firmware independently draws the live dash. */
final class NotebookLassoGesture {
    private float[] coordinates = new float[384];
    private long[] times = new long[128];
    private int size;
    private boolean active;

    boolean isActive() { return active; }
    void begin() { size = 0; active = true; }
    void reset() { size = 0; active = false; }

    void add(float x, float y, long time, float pressure) {
        if (!active) return;
        if (!NotebookPage.finite(x) || !NotebookPage.finite(y) || !NotebookPage.finite(pressure)
                || pressure < 0 || time < 0) throw new IllegalArgumentException("Invalid lasso sample");
        if (size >= NotebookStore.MAX_POINTS_PER_STROKE) throw new IllegalArgumentException("Lasso is too long");
        if (size == times.length) {
            int capacity = Math.min(NotebookStore.MAX_POINTS_PER_STROKE, size * 2);
            times = java.util.Arrays.copyOf(times, capacity);
            coordinates = java.util.Arrays.copyOf(coordinates, capacity * 3);
        }
        int index = size * 3;
        coordinates[index] = x; coordinates[index + 1] = y; coordinates[index + 2] = pressure;
        times[size++] = time;
    }

    List<NotebookPage.Point> finish(boolean cancelled) {
        if (!active) return null;
        active = false;
        if (cancelled || size == 0) { size = 0; return Collections.emptyList(); }
        List<NotebookPage.Point> path = new ArrayList<>(size + 1);
        for (int i = 0; i < size; i++) path.add(new NotebookPage.Point(
            coordinates[i * 3], coordinates[i * 3 + 1], times[i], coordinates[i * 3 + 2]));
        size = 0;
        return NotebookLasso.closedOutline(path);
    }
}
