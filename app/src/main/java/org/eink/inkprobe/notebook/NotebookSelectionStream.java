package org.eink.inkprobe.notebook;

/** Own fresh region contacts through UP/CANCEL, even while native input is paused for dragging. */
final class NotebookSelectionStream {
    private long activatedAt;
    private boolean owned;
    void activate(long time) { activatedAt = time; owned = false; }
    void reset() { owned = false; }
    boolean accept(int action, long eventTime, boolean ready) {
        // A native UP can precede Android DOWN/MOVE messages from that completed lasso.
        // Such old contacts must not immediately drag the newly selected region.
        if (action == 0 && ready && eventTime >= activatedAt) owned = true;
        if (!owned) return false;
        if (action == 1 || action == 3) owned = false;
        return true;
    }
}
