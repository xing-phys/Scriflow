package org.eink.inkprobe.notebook;

/** Reproduce popup removal cancelling a slider while its release handler is still running. */
public final class NotebookPenSliderCloseTest {
    private static int assertions;

    public static void main(String[] args) {
        releaseSurvivesRemovalCancellation();
        deferredDismissRetainsDecision();
        cancellationDoesNotApply();
        externalDismissAndAccessibility();
        multipleContactsApplyOnce();
        System.out.println("NotebookPenSliderClose: " + assertions + " assertions passed (reentrant popup teardown).");
    }

    private static void releaseSurvivesRemovalCancellation() {
        for (float width : new float[]{1, 3.7f, 10}) {
            Window window = new Window();
            window.slider.begin(7, true, NotebookPenConfig.fraction(6));
            window.slider.move(7, NotebookPenConfig.fraction(width));
            window.close.finish(window.slider.end(7, NotebookPenConfig.fraction(width), false));
            near(width, window.savedWidth, "release applies despite CANCEL during popup removal");
            near(width, window.nativeWidth, "native pen receives the released width");
            near(width, new NotebookPenSliderState(window.savedWidth).width(), "reopening restores the applied choice");
            check(window.callbacks == 1, "selected width is delivered once");
            check(window.removals == 1, "teardown does not recursively dismiss the popup");
            window.close.finish(null); window.close.dismissed();
            near(width, window.savedWidth, "late cancellation cannot undo the applied choice");
            check(window.callbacks == 1, "late events do not deliver again");
        }
    }

    private static void deferredDismissRetainsDecision() {
        int[] removals = {0}, callbacks = {0};
        Float[] result = {null};
        NotebookPenSliderClose close = new NotebookPenSliderClose(() -> removals[0]++, width -> {
            callbacks[0]++; result[0] = width;
        });
        close.finish(2.8f);
        check(callbacks[0] == 0, "width delivery waits for window dismissal");
        close.finish(null); close.finish(10f); // Events before a deferred onDismiss callback.
        close.dismissed(); close.dismissed();
        near(2.8f, result[0], "first release remains final until onDismiss arrives");
        check(removals[0] == 1 && callbacks[0] == 1, "deferred dismissal also runs once");
    }

    private static void cancellationDoesNotApply() {
        Window outside = new Window();
        outside.slider.begin(7, false, 1);
        outside.close.finish(outside.slider.end(7, 1, false));
        unchanged(outside, "outside contact");

        Window back = new Window();
        back.slider.begin(7, true, 1); back.slider.cancel();
        back.close.finish(back.slider.end(7, 1, false));
        unchanged(back, "Back during contact");

        Window system = new Window();
        system.slider.begin(7, true, 0);
        system.close.finish(system.slider.end(7, 0, true));
        unchanged(system, "system cancellation before release");

        Window lifecycle = new Window();
        lifecycle.slider.begin(7, true, 1);
        lifecycle.close.finish(null);
        lifecycle.close.finish(lifecycle.slider.end(7, 1, false));
        unchanged(lifecycle, "lifecycle shutdown");
    }

    private static void externalDismissAndAccessibility() {
        Window external = new Window();
        external.close.dismissed(); external.close.finish(1f);
        near(6, external.savedWidth, "unexpected dismissal preserves existing preference");
        check(external.callbacks == 1 && external.removals == 0, "external dismissal notifies once without re-removal");
        Window accessible = new Window();
        accessible.close.finish(8.9f);
        near(8.9f, accessible.savedWidth, "accessibility choice also survives popup teardown");
        check(accessible.callbacks == 1, "accessibility delivers only once");
    }

    private static void multipleContactsApplyOnce() {
        Window window = new Window();
        window.slider.begin(7, true, 0);
        window.slider.lift(7, .5f);
        check(window.slider.active() && window.callbacks == 0, "primary lift waits for remaining contact");
        window.close.finish(window.slider.end(9, 1, false));
        near(5.5f, window.savedWidth, "last contact applies the primary pointer's frozen width");
        check(window.callbacks == 1, "multi-contact release survives teardown exactly once");
    }

    private static void unchanged(Window window, String reason) {
        near(6, window.savedWidth, reason + " preserves saved width");
        near(6, window.nativeWidth, reason + " preserves live pen width");
        check(window.callbacks == 1 && window.removals == 1, reason + " closes once");
    }

    /** Android removes the child before onDismiss; removal can synchronously send CANCEL. */
    private static final class Window {
        final NotebookPenSliderState slider = new NotebookPenSliderState(6);
        final NotebookPenSliderClose close;
        boolean showing = true;
        float savedWidth = 6, nativeWidth = 6;
        int callbacks, removals;

        Window() {
            close = new NotebookPenSliderClose(this::remove, width -> {
                callbacks++;
                if (width != null) savedWidth = nativeWidth = width;
            });
        }
        void remove() {
            removals++;
            if (showing) {
                showing = false;
                // The UP already ended the gesture, so this yields null: the original bug.
                close.finish(slider.end(7, 0, true));
            }
            close.dismissed();
        }
    }

    private static void near(float expected, float actual, String message) {
        check(Math.abs(expected - actual) < .0001f, message + ": " + expected + " != " + actual);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
