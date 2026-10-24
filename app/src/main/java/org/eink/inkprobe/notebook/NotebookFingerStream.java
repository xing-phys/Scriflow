package org.eink.inkprobe.notebook;

/** Owns a finger gesture until every contact is released, independently of AHW readiness. */
final class NotebookFingerStream {
    enum Dispatch { FORWARD, CANCEL, CONSUME, UNHANDLED }

    // MotionEvent action-mask values, kept here so the ownership rules are host-testable.
    static final int DOWN = 0, UP = 1, MOVE = 2, CANCEL = 3, POINTER_DOWN = 5, POINTER_UP = 6;
    private boolean active, mixed;

    Dispatch accept(int action, boolean hasPen, boolean changedFinger, boolean penDown) {
        if (!active) {
            // Preserve the pen/eraser owner's complete event stream, including CANCEL and
            // MOVE events whose action-index pointer happens to be an accompanying palm.
            if (penDown || action == CANCEL) return Dispatch.UNHANDLED;
            if (action == DOWN && changedFinger && !hasPen && !penDown) {
                active = true;
                return Dispatch.FORWARD;
            }
            // A palm or a finger first observed mid-stream must never begin scrolling later.
            return changedFinger ? Dispatch.CONSUME : Dispatch.UNHANDLED;
        }

        // Once a pen joins, hold the entire stream even if that pen subsequently lifts. A
        // synthetic cancellation is delivered only after the final contact releases, keeping
        // native handwriting off while either the original finger or the pen remains down.
        mixed |= hasPen || penDown;
        if (action == UP || action == CANCEL) {
            Dispatch result = mixed ? Dispatch.CANCEL : Dispatch.FORWARD;
            reset(); // Clear ownership before a callback can resume or recreate the surface.
            return result;
        }
        return mixed ? Dispatch.CONSUME : Dispatch.FORWARD;
    }

    void reset() { active = false; mixed = false; }
}
