package org.eink.inkprobe.notebook;

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

/** Cross-page pointer anchoring, paper fitting and scrolling, independent of Android. */
public final class NotebookSelectionMoveTest {
    private static int assertions;

    public static void main(String[] args) {
        samePageGrabAndBoundaries();
        mixedPaperAndZoom();
        gapsAndFarPages();
        oversizedInkAndDots();
        edgeScrollRamps();
        randomizedPlacementInvariants();
        invalidInput();
        System.out.println("NotebookSelectionMove: " + assertions + " assertions passed (cross-page placement and edge scrolling).");
    }

    private static void samePageGrabAndBoundaries() {
        NotebookViewport viewport = new NotebookViewport(3, 100, 160, 600, 500, 24);
        NotebookLasso.Bounds bounds = bounds(10, 20, 40, 60);
        NotebookSelectionMove.Destination inside = place(viewport, bounds, 5, -10, 360, 300, 0);
        check(inside.page == 1 && inside.fits, "same-page movement keeps an eligible target");
        near(55, inside.centerX, "off-center grab preserves intended center x");
        near(60, inside.centerY, "off-center grab preserves intended center y");
        near(180, inside.screenLeft, "floating source coordinates translate under pointer");
        near(120, inside.screenTop, "floating preview uses original local coordinates");
        near(360, inside.screenLeft + 30 * inside.scale, "grabbed point stays under pointer x");
        near(300, inside.screenTop + 30 * inside.scale, "grabbed point stays under pointer y");
        NotebookSelectionMove.Destination edge = place(viewport, bounds, 0, 0, -300, -300, 0);
        near(15, edge.centerX, "drop fits complete bounds at page left");
        near(20, edge.centerY, "drop fits complete bounds at page top");
        near(-450, edge.screenLeft, "floating preview remains outside paper before release");
        near(-540, edge.screenTop, "floating preview does not stick to the source edge");
        NotebookSelectionMove.Destination right = place(viewport, bounds, 0, 0, 2000, 200, 0);
        near(85, right.centerX, "drop fits complete bounds at page right");
        near(2000, right.screenLeft + 25 * right.scale, "out-of-page preview still tracks the pointer");
    }

    private static void mixedPaperAndZoom() {
        NotebookViewport viewport = new NotebookViewport(Arrays.asList(new NotebookViewport.Size(100, 160),
            new NotebookViewport.Size(200, 100), new NotebookViewport.Size(80, 240)), 600, 500, 20, 2, 300);
        NotebookLasso.Bounds bounds = bounds(10, 20, 40, 60);
        NotebookSelectionMove.Destination target = place(viewport, bounds, 5, -10, 300, 450, 2000);
        check(target.page == 2 && target.fits, "drop selects mixed-size paper beneath pointer");
        near(6, target.scale, "preview fits the destination paper scale");
        near(95, target.centerX, "horizontal pan participates in destination inverse");
        // Page2 starts at1940; at scroll2000 its top=-60. Pointer450 gives logical85,
        // and the grabbed point is10 units above the center: desired center95, clamped80.
        near(80, target.centerY, "different page height clamps bounds instead of clipping ink");
        near(120, target.screenLeft, "preview x remains pointer-anchored at new scale");
        near(270, target.screenTop, "preview y remains pointer-anchored at new scale");
        near(180, (bounds.right - bounds.left) * target.scale, "logical width is preserved on wider paper");
        NotebookViewport small = viewport.withView(.5f, 0);
        float scroll = small.pageScroll(2);
        NotebookSelectionMove.Destination zoomedOut = place(small, bounds, 5, -10, 300, 70, scroll);
        check(zoomedOut.page == 2, "zoomed-out target accounts for continuous scroll");
        near(1.5f, zoomedOut.scale, "zoomed-out destination scale");
        near(95, zoomedOut.centerX, "centered paper margin is removed in inverse x");
        near(300, zoomedOut.screenLeft + 30 * zoomedOut.scale, "grab x survives zoomed-out margins");
        near(70, zoomedOut.screenTop + 30 * zoomedOut.scale, "grab y survives zoomed-out margins");
    }

    private static void gapsAndFarPages() {
        NotebookViewport viewport = new NotebookViewport(20, 100, 100, 100, 100, 20);
        NotebookLasso.Bounds bounds = bounds(10, 20, 30, 40);
        check(place(viewport, bounds, 0, 0, 40, 105, 0).page == 1, "near half-gap belongs to previous page");
        check(place(viewport, bounds, 0, 0, 40, 115, 0).page == 2, "far half-gap belongs to next page");
        check(place(viewport, bounds, 0, 0, 40, 110, 0).page == 1, "exact gap tie is stable on previous page");
        NotebookSelectionMove.Destination far = place(viewport, bounds, 2, 3, 50, 50, viewport.pageScroll(17));
        check(far.page == 17 && far.fits, "drag can pass many pages beyond its original page");
        near(48, far.centerX, "far-page x is page-local"); near(47, far.centerY, "far-page y is page-local");
        check(place(viewport, bounds, 0, 0, 40, -10000, 0).page == 1, "pointer above notebook targets first page");
        check(place(viewport, bounds, 0, 0, 40, 10000, viewport.maxScroll()).page == 20, "pointer below notebook targets final page");
        near(10, place(viewport, bounds, 0, 0, 40, -10000, 0).centerY, "far-off preview still clamps accepted drop");
        near(90, place(viewport, bounds, 0, 0, 40, 10000, viewport.maxScroll()).centerY, "final-page drop preserves complete bounds");
    }

    private static void oversizedInkAndDots() {
        NotebookViewport viewport = new NotebookViewport(Arrays.asList(new NotebookViewport.Size(400, 400),
            new NotebookViewport.Size(80, 80)), 400, 300, 20);
        NotebookLasso.Bounds wide = bounds(10, 20, 110, 60);
        NotebookSelectionMove.Destination rejected = place(viewport, wide, 4, 5, 210, 150, 420);
        check(rejected.page == 2 && !rejected.fits, "larger ink is rejected on a smaller destination");
        near(210, rejected.screenLeft + 64 * rejected.scale, "oversized preview stays under grab x");
        near(150, rejected.screenTop + 45 * rejected.scale, "oversized preview stays under grab y");
        near(500, (wide.right - wide.left) * rejected.scale, "rejected ink is never shrunk to fit");
        near(40, rejected.centerX, "oversized fallback center stays meaningful");
        NotebookSelectionMove.Destination tall = place(viewport, bounds(10, 10, 20, 120), 0, 0, 200, 150, 420);
        check(!tall.fits, "excess height alone rejects drop");
        NotebookSelectionMove.Destination dot = place(viewport, bounds(40, 50, 40, 50), 0, 0, 200, 150, 420);
        check(dot.fits && dot.page == 2, "zero-extent dot remains movable");
        near(40, dot.centerX, "dot x maps without artificial size"); near(30, dot.centerY, "dot y maps without artificial size");
        NotebookSelectionMove.Destination exact = place(viewport, bounds(10, 10, 90, 90), 0, 0, 20, 20, 420);
        check(exact.fits, "exact-size group fits the complete smaller page");
        near(40, exact.centerX, "exact-size group has one valid x"); near(40, exact.centerY, "exact-size group has one valid y");
    }

    private static void edgeScrollRamps() {
        near(-24, scroll(0), "top edge scrolls up at maximum step");
        near(-12, scroll(30), "top band ramps proportionally");
        near(0, scroll(60), "top inner boundary stops scrolling");
        near(0, scroll(300), "middle of screen does not scroll");
        near(0, scroll(540), "bottom inner boundary stops scrolling");
        near(12, scroll(570), "bottom band ramps proportionally");
        near(24, scroll(600), "bottom edge scrolls down at maximum step");
        near(-24, scroll(-1000), "outside top remains capped");
        near(24, scroll(1000), "outside bottom remains capped");
        near(0, NotebookSelectionMove.edgeScroll(10, 600, 0, 24), "zero edge disables scrolling");
        near(0, NotebookSelectionMove.edgeScroll(10, 600, 60, 0), "zero step disables scrolling");
        near(0, NotebookSelectionMove.edgeScroll(20, 40, 60, 24), "overlapping edge bands keep the middle neutral");
        for (int y = -100; y <= 700; y++) {
            float value = scroll(y);
            check(value >= -24 && value <= 24, "every scroll step is bounded");
            near(-value, scroll(600 - y), "edge scrolling is symmetric");
        }
        NotebookViewport viewport = new NotebookViewport(4, 100, 100, 100, 100, 20);
        near(0, viewport.clampScroll(0 + scroll(0)), "auto-scroll cannot pass document top");
        near(viewport.maxScroll(), viewport.clampScroll(viewport.maxScroll() + scroll(600)), "auto-scroll cannot pass document bottom");
    }

    private static void randomizedPlacementInvariants() {
        Random random = new Random(20261027);
        NotebookLasso.Bounds bounds = bounds(10, 20, 40, 60);
        for (int trial = 0; trial < 400; trial++) {
            NotebookViewport viewport = new NotebookViewport(Arrays.asList(new NotebookViewport.Size(100, 160),
                new NotebookViewport.Size(200, 100), new NotebookViewport.Size(80, 240)), 600, 500, 20,
                .25f + random.nextFloat() * 3, random.nextFloat() * 1000);
            float x = random.nextFloat() * 1200 - 300, y = random.nextFloat() * 900 - 200;
            float grabX = random.nextFloat() * 30 - 15, grabY = random.nextFloat() * 40 - 20;
            NotebookSelectionMove.Destination move = place(viewport, bounds, grabX, grabY, x, y, random.nextFloat() * viewport.maxScroll());
            check(move.fits && move.page >= 1 && move.page <= 3, "valid source group has a real eligible destination");
            check(move.centerX >= 15 - .001 && move.centerX <= viewport.pageWidth(move.page) - 15 + .001, "complete horizontal group stays inside drop page");
            check(move.centerY >= 20 - .001 && move.centerY <= viewport.pageHeight(move.page) - 20 + .001, "complete vertical group stays inside drop page");
            near(x, move.screenLeft + (25 + grabX) * move.scale, "random pointer-anchor x");
            near(y, move.screenTop + (40 + grabY) * move.scale, "random pointer-anchor y");
            near(viewport.scale(move.page), move.scale, "random preview uses destination scale");
        }
    }

    private static void invalidInput() {
        NotebookViewport viewport = new NotebookViewport(2, 100, 100, 200, 300, 20);
        NotebookLasso.Bounds bounds = bounds(10, 20, 40, 60);
        expect(() -> place(null, bounds, 0, 0, 0, 0, 0));
        expect(() -> place(viewport, null, 0, 0, 0, 0, 0));
        expect(() -> NotebookSelectionMove.place(viewport, 0, bounds, 0, 0, 0, 0, 0));
        expect(() -> NotebookSelectionMove.place(viewport, 3, bounds, 0, 0, 0, 0, 0));
        expect(() -> place(viewport, bounds, Float.NaN, 0, 0, 0, 0));
        expect(() -> place(viewport, bounds, 0, Float.POSITIVE_INFINITY, 0, 0, 0));
        expect(() -> place(viewport, bounds, 0, 0, Float.NaN, 0, 0));
        expect(() -> place(viewport, bounds, 0, 0, 0, Float.NEGATIVE_INFINITY, 0));
        expect(() -> place(viewport, bounds, 0, 0, 0, 0, Float.NaN));
        expect(() -> NotebookSelectionMove.edgeScroll(0, 0, 10, 1));
        expect(() -> NotebookSelectionMove.edgeScroll(Float.NaN, 100, 10, 1));
        expect(() -> NotebookSelectionMove.edgeScroll(0, 100, -1, 1));
        expect(() -> NotebookSelectionMove.edgeScroll(0, 100, 10, -1));
    }

    private static NotebookSelectionMove.Destination place(NotebookViewport viewport, NotebookLasso.Bounds bounds,
            float grabX, float grabY, float x, float y, float scroll) {
        return NotebookSelectionMove.place(viewport, 1, bounds, grabX, grabY, x, y, scroll);
    }
    private static float scroll(float y) { return NotebookSelectionMove.edgeScroll(y, 600, 60, 24); }
    private static NotebookLasso.Bounds bounds(float l, float t, float r, float b) {
        return new NotebookLasso.Bounds(Collections.singletonList(new NotebookPage.Stroke(2, 2, 0xff000000,
            Arrays.asList(new NotebookPage.Point(l, t, 1, .5f), new NotebookPage.Point(r, b, 2, .5f)))));
    }
    private static void near(float expected, float actual, String message) {
        check(Math.abs(expected - actual) < .002f, message + " (expected " + expected + ", got " + actual + ")");
    }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private interface Task { void run(); }
    private static void expect(Task task) { try { task.run(); } catch (IllegalArgumentException expected) { assertions++; return; } throw new AssertionError("Expected invalid geometry rejection"); }
}
