package org.eink.inkprobe.notebook;

/** One hardware gesture per switch; defer rather than interrupt an unfinished native stroke. */
public final class NotebookStylusShortcut {
    public enum Target { PEN, ERASER }
    public static final long DEBOUNCE_MS = 250, DEFER_MS = 2000;
    private long lastGesture = -1, requestedAt;
    private Target pending;

    public boolean request(long now, boolean penSelected) {
        if (now < 0 || lastGesture >= 0 && (now < lastGesture || now - lastGesture < DEBOUNCE_MS)) return false;
        if (pending != null && now - requestedAt > DEFER_MS) pending = null;
        Target current = pending == null ? (penSelected ? Target.PEN : null) : pending;
        pending = current == Target.PEN ? Target.ERASER : Target.PEN;
        requestedAt = lastGesture = now;
        return true;
    }
    public Target take(long now, boolean ready) {
        if (pending == null) return null;
        if (now < requestedAt || now - requestedAt > DEFER_MS) { pending = null; return null; }
        if (!ready) return null;
        Target result = pending; pending = null; return result;
    }
    public boolean hasPending() { return pending != null; }
    public void discard() { pending = null; }
    public void reset() { pending = null; lastGesture = -1; }
}
