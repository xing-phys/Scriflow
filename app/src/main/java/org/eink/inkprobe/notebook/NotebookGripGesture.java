package org.eink.inkprobe.notebook;

/** A grip tap toggles the tools; movement beyond touch slop commits to dragging. */
final class NotebookGripGesture {
    private final float slopSquared;
    private float x, y;
    private boolean active, dragged, tap;

    NotebookGripGesture(int slop) { slopSquared = (float) slop * slop; }

    void begin(float x, float y) {
        this.x = x; this.y = y;
        active = tap = true;
        dragged = false;
    }

    boolean move(float x, float y) {
        if (!active) return false;
        float dx = x - this.x, dy = y - this.y;
        if (dx * dx + dy * dy > slopSquared) {
            dragged = true;
            tap = false;
        }
        return dragged;
    }

    void cancelTap() { tap = false; }

    boolean finish(boolean cancelled) {
        boolean click = active && tap && !cancelled;
        active = tap = false;
        return click;
    }
}
