package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Joins the two native observers without ever turning a preview into document ink. */
final class NotebookEraserCommit {
    static final int COLOR = 0xffaaaaaa;
    private boolean active, lifted, committed, cancelled;
    private float diameter;
    private long upTime, trackTime;
    private List<NotebookPage.Point> path;

    void begin(float width) {
        if (active) throw new IllegalStateException("Previous native eraser has not finished");
        if (!NotebookPage.finite(width) || width <= 0 || width > NotebookStore.MAX_DIMENSION)
            throw new IllegalArgumentException("Invalid native eraser width");
        active = true;
        diameter = width;
    }

    void up(List<NotebookPage.Point> points, long time, boolean cancel) {
        if (!active || lifted || points == null || points.isEmpty())
            throw new IllegalStateException("Unexpected native eraser UP");
        path = Collections.unmodifiableList(new ArrayList<>(points));
        upTime = time;
        cancelled = cancel;
        lifted = true;
    }

    void track(long time) {
        if (!active || committed) throw new IllegalStateException("Unexpected native eraser track");
        trackTime = time;
        committed = true;
    }

    boolean isActive() { return active; }
    boolean isWaiting() { return active && lifted; }

    Result takeReady() {
        if (!active || !lifted || !committed) return null;
        if (upTime != trackTime) throw new IllegalStateException("Native eraser UP and track differ");
        Result result = new Result(path, diameter / 2f, cancelled);
        reset(); // Clear before notification: the listener pauses and reapplies the viewport.
        return result;
    }

    void reset() {
        active = lifted = committed = cancelled = false;
        path = null;
    }

    static final class Result {
        final List<NotebookPage.Point> path;
        final float radius;
        final boolean cancelled;
        Result(List<NotebookPage.Point> path, float radius, boolean cancelled) {
            this.path = path; this.radius = radius; this.cancelled = cancelled;
        }
    }
}
