package org.eink.inkprobe.notebook;

/** One-pointer selection movement. Extra contacts cancel instead of committing a jump. */
final class NotebookSelectionDrag {
    private int pointer = -1;
    private float startX, startY, threshold;
    private boolean active, inside, cancelled, moved;
    float dx, dy;

    boolean isActive() { return active; }
    boolean isMoving() { return active && inside && moved && !cancelled; }
    void begin(int pointer, float x, float y, boolean inside, float threshold) {
        reset(); this.pointer = pointer; startX = x; startY = y;
        this.inside = inside; this.threshold = threshold; active = true;
    }
    void move(int pointer, float x, float y, float minX, float maxX, float minY, float maxY) {
        if (!active || cancelled || !inside || this.pointer != pointer) return;
        float offsetX = x - startX, offsetY = y - startY;
        moved |= Math.hypot(offsetX, offsetY) >= threshold;
        if (moved) { dx = Math.max(minX, Math.min(maxX, offsetX)); dy = Math.max(minY, Math.min(maxY, offsetY)); }
    }
    void cancel() { cancelled = true; dx = dy = 0; }
    Result finish(boolean cancel) {
        if (!active) return null;
        Result result = new Result(cancel || cancelled, !inside, moved, dx, dy); reset(); return result;
    }
    void reset() { pointer = -1; active = inside = cancelled = moved = false; dx = dy = 0; }
    static final class Result {
        final boolean cancelled, outside, moved;
        final float dx, dy;
        Result(boolean cancelled, boolean outside, boolean moved, float dx, float dy) {
            this.cancelled = cancelled; this.outside = outside; this.moved = moved; this.dx = dx; this.dy = dy;
        }
    }
}
