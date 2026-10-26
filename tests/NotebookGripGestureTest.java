package org.eink.inkprobe.notebook;

/** Tap/drag intent must survive jitter, cancellation and additional fingers. */
public final class NotebookGripGestureTest {
    private static int assertions;

    public static void main(String[] args) {
        NotebookGripGesture gesture = new NotebookGripGesture(8);
        gesture.begin(100, 200);
        check(!gesture.move(103, 204), "small jitter does not move the dock");
        check(gesture.finish(false), "a short tap toggles the tools");
        check(!gesture.finish(false), "release toggles only once");

        gesture.begin(100, 200);
        check(!gesture.move(108, 200), "the slop boundary is still a tap");
        check(gesture.move(109, 200), "crossing slop begins dragging");
        check(gesture.move(100, 200), "returning to the start remains a drag");
        check(!gesture.finish(false), "a drag must never collapse the dock");

        gesture.begin(0, 0);
        check(gesture.move(6, 6), "diagonal movement uses distance, not separate axes");
        check(!gesture.finish(false), "diagonal dragging is not a click");

        gesture.begin(0, 0);
        check(!gesture.finish(true), "cancelled taps do not toggle");
        gesture.begin(0, 0);
        gesture.cancelTap();
        check(!gesture.finish(false), "additional pointers cancel a tap");
        gesture.begin(0, 0);
        gesture.cancelTap();
        check(gesture.move(20, 0), "a second finger does not prevent a grip drag");
        check(!gesture.finish(false), "a multi-touch drag cannot toggle");

        check(!gesture.move(20, 20), "movement after release is ignored");
        gesture.begin(20, 30);
        check(gesture.finish(false), "a new contact clears the old drag state");
        System.out.println("NotebookGripGesture: " + assertions + " assertions passed (tap/drag intent).");
    }

    private static void check(boolean condition, String detail) {
        assertions++;
        if (!condition) throw new AssertionError(detail);
    }
}
