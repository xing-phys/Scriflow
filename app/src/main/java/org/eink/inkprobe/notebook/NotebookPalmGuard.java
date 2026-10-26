package org.eink.inkprobe.notebook;

/** Pen proximity and recent writing outrank finger navigation. No model edits or timers. */
final class NotebookPalmGuard {
    static final long QUIET_MS = 600;
    private boolean hovering, penDown;
    private long lastPen = Long.MIN_VALUE;

    void penSeen(long now) { lastPen = now; }
    void hover(boolean nearby, long now) { hovering = nearby; penSeen(now); }
    void penContact(boolean down, long now) { penDown = down; penSeen(now); }

    boolean rejectFingers(long now, boolean broadContact) {
        return broadContact || hovering || penDown || lastPen != Long.MIN_VALUE && now - lastPen < QUIET_MS;
    }

    /** Zero/unknown contact sizes are common on older firmware and are not rejected. */
    static boolean broadContact(float majorPixels, float pixelsPerMm) {
        return majorPixels > 0 && pixelsPerMm > 0 && majorPixels / pixelsPerMm >= 24;
    }
}
