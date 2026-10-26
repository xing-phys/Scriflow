package org.eink.inkprobe.notebook;

/** Stable gravity sectors, independent of Android's firmware rotation policy. */
final class NotebookSensorRotation {
    static final long SETTLE_MS = 600;
    enum Facing { PORTRAIT, LANDSCAPE, REVERSE_PORTRAIT, REVERSE_LANDSCAPE }
    private int lastAngle = -1, candidate = -1;
    private long candidateSince;
    private boolean writing;

    void sample(int angle, long now) {
        lastAngle = angle;
        if (writing) return;
        int next = sector(angle);
        if (next != candidate) { candidate = next; candidateSince = now; }
    }

    void setWriting(boolean down, long now) {
        writing = down; candidate = -1;
        if (!down) sample(lastAngle, now);
    }

    int ready(long now) {
        return !writing && candidate >= 0 && now - candidateSince >= SETTLE_MS ? candidate : -1;
    }

    long remaining(long now) {
        return writing || candidate < 0 ? -1 : Math.max(0, SETTLE_MS - (now - candidateSince));
    }

    void reset() { writing = false; lastAngle = candidate = -1; }

    private static int sector(int angle) {
        if (angle < 0 || angle >= 360) return -1;
        int nearest = ((angle + 45) / 90 * 90) % 360;
        int distance = Math.abs(angle - nearest);
        return Math.min(distance, 360 - distance) <= 30 ? nearest : -1;
    }

    /** OrientationEventListener angles run opposite to Display rotation constants. */
    static Facing facing(int angle, boolean naturalLandscape) {
        if (naturalLandscape) {
            switch (angle) {
                case 0: return Facing.LANDSCAPE;
                case 90: return Facing.PORTRAIT;
                case 180: return Facing.REVERSE_LANDSCAPE;
                case 270: return Facing.REVERSE_PORTRAIT;
                default: throw new IllegalArgumentException("Invalid orientation sector");
            }
        }
        switch (angle) {
            case 0: return Facing.PORTRAIT;
            case 90: return Facing.REVERSE_LANDSCAPE;
            case 180: return Facing.REVERSE_PORTRAIT;
            case 270: return Facing.LANDSCAPE;
            default: throw new IllegalArgumentException("Invalid orientation sector");
        }
    }
}
