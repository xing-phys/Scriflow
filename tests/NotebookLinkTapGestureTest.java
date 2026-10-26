package org.eink.inkprobe.notebook;

/** Navigation must not mistake a palm, pinch, drag, or pen contact for a PDF-link tap. */
public final class NotebookLinkTapGestureTest {
    private static int assertions;
    public static void main(String[] args) {
        NotebookLinkTapGesture gesture = new NotebookLinkTapGesture();
        gesture.begin(100, 200, 1000, 4, 0);
        check(gesture.finish(101, 202, 1150, 0, false), "short finger tap follows a link");
        check(!gesture.finish(101, 202, 1160, 0, false), "tap is consumed once");
        gesture.begin(100, 200, 1000, 4, 0);
        gesture.move(105, 200); gesture.move(100, 200);
        check(!gesture.finish(100, 200, 1150, 0, false), "drag returning to origin never follows a link");
        gesture.begin(100, 200, 1000, 4, 0); gesture.cancel();
        check(!gesture.finish(100, 200, 1150, 0, false), "second finger and cancellation suppress links");
        gesture.begin(100, 200, 1000, 4, 0);
        check(!gesture.finish(100, 200, 1150, 1, false), "completed pen activity suppresses resting palm");
        gesture.begin(100, 200, 1000, 4, 0);
        check(!gesture.finish(100, 200, 1150, 0, true), "active stylus suppresses link activation");
        gesture.begin(100, 200, 1000, 4, 0);
        check(!gesture.finish(100, 200, 1500, 0, false), "long resting contact is not a tap");
        gesture.begin(100, 200, 1000, 4, 0);
        check(!gesture.finish(100, 200, 999, 0, false), "time reversal cannot activate a link");
        System.out.println("NotebookLinkTapGesture: " + assertions + " assertions passed.");
    }
    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
}
