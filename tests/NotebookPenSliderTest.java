package org.eink.inkprobe.notebook;

import java.util.Random;

/** Slider endpoint precision, protected contact ownership and placement around movable tools. */
public final class NotebookPenSliderTest {
    private static int assertions;

    public static void main(String[] args) {
        widthsAndRememberedChoices();
        dragAndCancel();
        contactOwnership();
        repeatedAdjustmentsWithoutClosing();
        placementAtEdges();
        variedWindows();
        System.out.println("NotebookPenSlider: " + assertions + " assertions passed (width, contacts and placement).");
    }

    private static void widthsAndRememberedChoices() {
        near(1, NotebookPenConfig.atFraction(-1), "drag past left clamps to finest width");
        near(10, NotebookPenConfig.atFraction(2), "drag past right clamps to broadest width");
        for (int tenths = 10; tenths <= 100; tenths++) {
            float width = tenths / 10f;
            near(width, NotebookPenConfig.atFraction(NotebookPenConfig.fraction(width)),
                "every intermediate width maps to a reproducible thumb position");
            near(width, NotebookPenConfig.restore(width), "intermediate widths persist across reopening");
        }
        near(3.7f, NotebookPenConfig.atFraction(.3f), "rail selects between former presets");
        check(NotebookPenConfig.label(3.7f).equals("3.7"), "fractional width has a compact readable label");
        check(NotebookPenConfig.label(10).equals("10.0"), "broad endpoint label remains consistent");
    }

    private static void dragAndCancel() {
        NotebookPenSliderState state = new NotebookPenSliderState(4);
        state.begin(7, true, 0); state.move(7, .3f);
        near(3.7f, state.width(), "drag previews intermediate width before committing");
        check(state.active(), "contact remains owned until final UP");
        near(5.5f, state.end(7, .5f, false), "release uses final pointer position");
        check(!state.active() && state.pointer() == -1, "final UP releases ownership");
        check(state.end(7, 1, false) == null, "duplicate terminal event cannot apply twice");

        state = new NotebookPenSliderState(4);
        state.begin(1, false, 1); state.move(1, 0);
        near(4, state.width(), "outside tap and drag never edit the selection");
        check(state.end(1, .5f, false) == null, "outside contact dismisses without applying");
        state.begin(1, true, 1);
        check(state.end(1, 0, true) == null, "system cancellation discards the preview");
        state.begin(1, true, 1); state.cancel();
        check(state.active(), "Back while touching waits for contact completion");
        check(state.end(1, 0, false) == null, "Back cancels selection after the contact ends");
    }

    private static void contactOwnership() {
        NotebookPenSliderState state = new NotebookPenSliderState(4);
        state.begin(3, true, .2f); state.move(9, 1);
        near(2.8f, state.width(), "another finger cannot move the primary thumb");
        state.lift(9, 1);
        check(state.active() && state.pointer() == 3, "secondary lift leaves the primary pointer tracked");
        state.move(3, .4f); state.lift(3, .6f);
        near(6.4f, state.width(), "primary lift freezes at its final location");
        check(state.active() && state.pointer() == -1, "remaining contacts keep native input paused");
        state.move(9, 0);
        near(6.4f, state.end(9, 0, false), "last finger release applies the frozen primary choice");
        state.begin(3, false, .2f); state.move(9, .4f); state.lift(3, 0);
        check(state.end(9, 1, false) == null, "outside-started multi-contact gesture remains cancellation");
    }

    private static void placementAtEdges() {
        NotebookPenSliderLayout.Box tools = box(80, 8, 532, 48), pen = box(166, 14, 36, 36);
        NotebookPenSliderLayout.Box bar = place(702, 900, pen, tools, false);
        check(bar.top >= tools.bottom() + 6, "horizontal top strip opens below");
        tools = box(80, 844, 532, 48); pen = box(166, 850, 36, 36);
        bar = place(702, 900, pen, tools, false);
        check(bar.bottom() <= tools.top - 6, "bottom strip opens above");
        tools = box(646, 60, 48, 532); pen = box(652, 146, 36, 36);
        bar = place(702, 900, pen, tools, true);
        check(bar.right() <= tools.left - 6, "right vertical strip opens left");
        tools = box(8, 60, 48, 532); pen = box(14, 146, 36, 36);
        bar = place(702, 900, pen, tools, true);
        check(bar.left >= tools.right() + 6, "left vertical strip opens right");
    }

    private static void repeatedAdjustmentsWithoutClosing() {
        for (NotebookWidthRange range : new NotebookWidthRange[]{NotebookWidthRange.PEN, NotebookWidthRange.ERASER}) {
            NotebookPenSliderState slider = new NotebookPenSliderState(range, range.defaultWidth);
            float first = range.atFraction(.7f), second = range.atFraction(.35f);
            slider.begin(1, true, .2f);
            near(first, slider.end(1, .7f, false), "first release applies without retiring slider state");
            slider.begin(1, true, .1f);
            check(slider.end(1, .1f, true) == null, "cancelled second adjustment applies no width");
            near(first, slider.width(), "cancellation restores the last applied width rather than the opening width");
            slider.begin(2, true, .9f); slider.lift(2, .35f);
            check(slider.active(), "multi-contact adjustment keeps its contact barrier after primary lift");
            near(second, slider.end(3, 0, false), "later gesture applies its frozen primary choice");
            slider.begin(1, false, 1);
            check(slider.end(1, 1, false) == null, "an outside cancellation never reapplies a previous width");
            near(second, slider.width(), "an outside contact preserves the latest completed choice");
            slider.choose(range.maximum + range.step);
            near(range.maximum, slider.width(), "accessibility increment clamps at the upper endpoint without closing");
            slider.begin(1, true, .5f);
            try { slider.choose(range.minimum); throw new AssertionError("Accessibility replaced an active touch"); }
            catch (IllegalStateException expected) { assertions++; }
            slider.cancel(); slider.end(1, .5f, false);
            near(range.maximum, slider.width(), "cancelled contact restores the latest accessibility choice");
        }
    }

    private static void variedWindows() {
        Random random = new Random(17);
        for (int i = 0; i < 200; i++) {
            int width = 1 + random.nextInt(1500), height = 1 + random.nextInt(1800);
            boolean vertical = random.nextBoolean();
            int toolsWidth = Math.min(width, vertical ? 48 : 532);
            int toolsHeight = Math.min(height, vertical ? 532 : 48);
            NotebookPenSliderLayout.Box tools = box(random.nextInt(width - toolsWidth + 1),
                random.nextInt(height - toolsHeight + 1), toolsWidth, toolsHeight);
            NotebookPenSliderLayout.Box pen = box(tools.left, tools.top,
                Math.min(36, toolsWidth), Math.min(36, toolsHeight));
            NotebookPenSliderLayout.Box bar = place(width, height, pen, tools, vertical);
            check(bar.left >= 0 && bar.top >= 0 && bar.right() <= width && bar.bottom() <= height,
                "slider stays within narrow, short and ordinary writing windows");
            check(bar.width > 0 && bar.height > 0 && bar.width <= 224 && bar.height <= 48,
                "small windows shrink the bar without creating empty geometry");
        }
    }

    private static NotebookPenSliderLayout.Box place(int w, int h, NotebookPenSliderLayout.Box pen,
                                                    NotebookPenSliderLayout.Box tools, boolean vertical) {
        return NotebookPenSliderLayout.place(w, h, pen, tools, vertical, 224, 48, 6, 8);
    }
    private static NotebookPenSliderLayout.Box box(int x, int y, int w, int h) {
        return new NotebookPenSliderLayout.Box(x, y, w, h);
    }
    private static void near(float expected, float actual, String message) {
        check(Math.abs(expected - actual) < .0001f, message + ": " + expected + " != " + actual);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
