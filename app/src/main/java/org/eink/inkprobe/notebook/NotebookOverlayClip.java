package org.eink.inkprobe.notebook;

/** Screen-to-surface geometry for native handwriting exclusion, without page scaling. */
final class NotebookOverlayClip {
    static final class Hole {
        final int left, top, right, bottom;

        private Hole(int left, int top, int right, int bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
    }

    private NotebookOverlayClip() { }

    /** Null means that the floating window covers no pixel of the physical surface. */
    static Hole localHole(int surfaceLeft, int surfaceTop, int width, int height,
                          int screenLeft, int screenTop, int screenRight, int screenBottom) {
        if (width <= 0 || height <= 0 || screenRight <= screenLeft || screenBottom <= screenTop) return null;
        // Subtract in long arithmetic: screen coordinates need not have a zero origin,
        // and an offscreen window must not wrap into a large false exclusion region.
        int left = clamp((long) screenLeft - surfaceLeft, width);
        int top = clamp((long) screenTop - surfaceTop, height);
        int right = clamp((long) screenRight - surfaceLeft, width);
        int bottom = clamp((long) screenBottom - surfaceTop, height);
        return left < right && top < bottom ? new Hole(left, top, right, bottom) : null;
    }

    private static int clamp(long value, int maximum) {
        return (int) Math.max(0L, Math.min(maximum, value));
    }
}
