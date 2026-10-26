package org.eink.inkprobe.notebook;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Independent screen/page round trips, margin clipping, and zoom/rotation anchors. */
public final class NotebookViewportZoomTest {
    private static int assertions;

    public static void main(String[] args) {
        marginsAreNotPaper();
        magnifiedWritingRoundTrips();
        broadLegacyInkStillDisplays();
        eraserFootprintsKeepTheirCentres();
        pinchAndRotationKeepLogicalAnchors();
        anchorsRespectDocumentEdges();
        extremeAspectRatiosFitExactly();
        randomizedWritingRoundTrips();
        invalidGeometryIsRejected();
        System.out.println("NotebookViewportZoom: " + assertions + " assertions passed (pure geometry).");
    }

    private static void marginsAreNotPaper() {
        NotebookViewport view = new NotebookViewport(1, 100, 200, 400, 500, 24, .5f, 99);
        near(100, view.pageLeft(1), "zoomed-out paper centred horizontally");
        near(300, view.pageRight(1), "zoomed-out right edge");
        near(0, view.pan, "small paper has no horizontal pan");
        check(view.pageAt(99, 100, 0) == 0 && view.pageAt(301, 100, 0) == 0, "side margins are not writable");
        check(view.pageAt(200, 401, 0) == 0, "space below short paper is not writable");
        check(view.pageAt(100, 100, 0) == 1 && view.pageAt(300, 100, 0) == 1, "paper edges remain writable");
        check(view.splitStroke(stroke(8, point(50, 100)), 0).isEmpty(), "margin dot discarded");
        check(view.splitStroke(stroke(8, point(99, 20), point(99, 300)), 0).isEmpty(), "margin path is not clamped into edge ink");
        NotebookPage.Stroke crossing = view.splitStroke(stroke(8,
            point(0, 100, 0, 0), point(400, 100, 400, 2)), 0).get(1).get(0);
        near(4, crossing.width, "display width converted into page units");
        endpoint(crossing, 0, 0, 50, 100, .5f);
        endpoint(crossing, 1, 100, 50, 300, 1.5f);
        NotebookPage composed = view.compose(Collections.singletonMap(1, page(100, 200, crossing)), 0);
        endpoint(composed.strokes.get(0), 0, 100, 100, 100, .5f);
        endpoint(composed.strokes.get(0), 1, 300, 100, 300, 1.5f);
    }

    private static void magnifiedWritingRoundTrips() {
        NotebookViewport view = new NotebookViewport(1, 300, 500, 600, 400, 24, 2, 300);
        near(4, view.scale(1), "magnified logical scale");
        near(-300, view.pageLeft(1), "horizontal pan shifts paper");
        near(600, view.maxPan(), "magnified horizontal range");
        NotebookPage.Stroke input = stroke(12, point(40, 30, 11, .2f), point(400, 250, 99, .8f));
        NotebookPage.Stroke local = view.splitStroke(input, 200).get(1).get(0);
        near(3, local.width, "magnified pen width inverse");
        endpoint(local, 0, 85, 57.5, 11, .2f);
        endpoint(local, 1, 175, 112.5, 99, .8f);
        NotebookPage.Stroke display = view.compose(Collections.singletonMap(1, page(300, 500, local)), 200).strokes.get(0);
        sameStroke(input, display, "magnified write/compose round trip");
        check(input.points.get(0).x == 40 && local.points.get(0).x == 85, "round trip does not mutate either input");

        NotebookViewport mixed = new NotebookViewport(Arrays.asList(new NotebookViewport.Size(100, 200),
            new NotebookViewport.Size(200, 100)), 400, 400, 30, .5f, 0);
        near(2, mixed.scale(1), "first mixed paper scale");
        near(1, mixed.scale(2), "second mixed paper scale");
        Map<Integer, List<NotebookPage.Stroke>> pieces = mixed.splitStroke(stroke(4, point(200, 0), point(200, 400)), 130);
        check(pieces.size() == 2 && pieces.containsKey(1) && pieces.containsKey(2), "zoomed continuous writing reaches both page sizes");
        near(100, pieces.get(2).get(0).points.get(0).x, "second paper inverse x uses its own scale");
        near(0, pieces.get(2).get(0).points.get(0).y, "gap crossing begins at second paper edge");
    }

    private static void eraserFootprintsKeepTheirCentres() {
        NotebookViewport small = new NotebookViewport(1, 100, 200, 400, 500, 24, .5f, 0);
        NotebookViewport.EraseGesture left = small.splitErase(Collections.singletonList(point(95, 100)), 8, 0).get(0);
        near(4, left.radius, "eraser radius in page units");
        near(-2.5, left.paths.get(0).get(0).x, "near-margin eraser centre is preserved outside page");
        near(50, left.paths.get(0).get(0).y, "near-margin eraser y inverse");
        check(small.splitErase(Collections.singletonList(point(91, 100)), 8, 0).isEmpty(), "eraser miss does not touch paper");
        NotebookViewport.EraseGesture right = small.splitErase(Collections.singletonList(point(305, 100)), 8, 0).get(0);
        near(102.5, right.paths.get(0).get(0).x, "right-margin eraser centre is not clamped");
        NotebookViewport large = new NotebookViewport(1, 300, 500, 600, 400, 24, 2, 300);
        NotebookViewport.EraseGesture pan = large.splitErase(Collections.singletonList(point(4, 80)), 6, 200).get(0);
        near(1.5, pan.radius, "magnified eraser radius inverse");
        near(76, pan.paths.get(0).get(0).x, "magnified eraser includes horizontal pan");
        near(70, pan.paths.get(0).get(0).y, "magnified eraser includes vertical scroll");
    }

    private static void broadLegacyInkStillDisplays() {
        NotebookViewport view = new NotebookViewport(1, 100, 200, 400, 500, 24, 2, 200);
        NotebookPage.Stroke original = stroke(1024, point(50, 30, 42, .75f));
        NotebookPage source = page(100, 200, original);
        NotebookPage display = view.compose(Collections.singletonMap(1, source), 0);
        check(display.strokes.size() == 1, "broad legacy ink still composes at high zoom");
        near(1024, display.strokes.get(0).width, "native display width capped within supported range");
        near(1024, source.strokes.get(0).width, "defensive display cap keeps durable legacy width unchanged");
        endpoint(display.strokes.get(0), 0, 200, 240, 42, .75f);
    }

    private static void pinchAndRotationKeepLogicalAnchors() {
        NotebookViewport original = new NotebookViewport(4, 100, 160, 600, 500, 24);
        float originalScroll = original.pageScroll(2) + 200;
        near(360, screenX(original, 2, 60), "original logical anchor x");
        near(220, screenY(original, 2, 70, originalScroll), "original logical anchor y");
        NotebookViewport pinched = original.withView(2, 0);
        pinched = pinched.withView(2, pinched.panForPoint(2, 60, 360));
        float pinchScroll = pinched.scrollForPoint(2, 70, 220);
        near(360, screenX(pinched, 2, 60), "pinch keeps anchor x under fingers");
        near(220, screenY(pinched, 2, 70, pinchScroll), "pinch keeps anchor y under fingers");
        near(1, original.zoom, "zoom creates independent immutable geometry");
        near(0, original.pan, "original pan remains unchanged");
        NotebookViewport rotated = new NotebookViewport(4, 100, 160, 1000, 600, 24, 1.5f, 0);
        rotated = rotated.withView(1.5f, rotated.panForPoint(2, 60, 500));
        float rotatedScroll = rotated.scrollForPoint(2, 70, 300);
        near(500, screenX(rotated, 2, 60), "rotation preserves logical centre x");
        near(300, screenY(rotated, 2, 70, rotatedScroll), "rotation preserves logical centre y");
        check(rotated.nearestPage(300, rotatedScroll) == 2, "rotated active page follows preserved centre");
    }

    private static void anchorsRespectDocumentEdges() {
        NotebookViewport view = new NotebookViewport(4, 100, 160, 600, 500, 24, 2, 0);
        near(0, view.panForPoint(1, 0, 300), "anchor cannot pan beyond left edge");
        near(view.maxPan(), view.panForPoint(1, 100, 0), "anchor cannot pan beyond right edge");
        near(0, view.scrollForPoint(1, 0, 250), "anchor cannot scroll above document");
        near(view.maxScroll(), view.scrollForPoint(4, 160, 100), "anchor cannot scroll below document");
        near(view.maxPan(), view.withView(2, Float.MAX_VALUE).pan, "oversized pan clamped at construction");
        near(0, view.withView(2, -100).pan, "negative pan clamped at construction");
        float pageTwoTop = view.pageTop(2, 0);
        check(view.nearestPage(pageTwoTop - 2, 0) == 2, "pinch in gap uses nearest following page");
        check(view.nearestPage(pageTwoTop - 22, 0) == 1, "pinch in gap uses nearest preceding page");
    }

    private static void extremeAspectRatiosFitExactly() {
        for (NotebookPaperSize size : Arrays.asList(NotebookPaperSize.custom(20, 1000), NotebookPaperSize.custom(1000, 20))) {
            NotebookViewport base = new NotebookViewport(1, size.logicalWidth(), size.logicalHeight(), 1200, 800, 24);
            near(1, base.fittedZoom(NotebookViewport.Fit.WIDTH, 1), "Fit width remains the percentage reference");
            float fittedHeight = base.fittedZoom(NotebookViewport.Fit.HEIGHT, 1);
            NotebookViewport height = base.withView(fittedHeight, 0);
            near(800, size.logicalHeight() * height.scale(1), "extreme paper Fit height fills height");
            float fittedPage = base.fittedZoom(NotebookViewport.Fit.PAGE, 1);
            NotebookViewport whole = base.withView(fittedPage, 0);
            check(size.logicalHeight() * whole.scale(1) <= 800.001f && whole.pageRight(1) - whole.pageLeft(1) <= 1200.001f,
                "extreme paper Fit page remains entirely within view");
            if (size.widthMm < size.heightMm) check(fittedPage < .1f, "very tall paper fits below ordinary pinch minimum");
            else check(fittedHeight > 8, "very wide paper fits above ordinary pinch maximum");
        }
    }

    private static void randomizedWritingRoundTrips() {
        Random random = new Random(20261026);
        for (int trial = 0; trial < 200; trial++) {
            int width = 200 + random.nextInt(1600), height = 200 + random.nextInt(2200);
            float zoom = .15f + random.nextFloat() * 5;
            NotebookViewport view = new NotebookViewport(1, width, height, 900, 650, 24, zoom, random.nextFloat() * 900 * zoom);
            float scroll = random.nextFloat() * view.maxScroll();
            float left = Math.max(0, view.pageLeft(1)), right = Math.min(900, view.pageRight(1));
            float top = Math.max(0, view.pageTop(1, scroll)), bottom = Math.min(650, view.pageTop(1, scroll) + height * view.scale(1));
            NotebookPage.Stroke source = stroke(2,
                point(left + (right - left) * .2f, top + (bottom - top) * .25f, 10, .3f),
                point(left + (right - left) * .8f, top + (bottom - top) * .75f, 90, .9f));
            NotebookPage.Stroke local = view.splitStroke(source, scroll).get(1).get(0);
            for (NotebookPage.Point p : local.points)
                check(p.x >= 0 && p.x <= width && p.y >= 0 && p.y <= height, "random inverse transform remains strictly inside page");
            NotebookPage.Stroke display = view.compose(Collections.singletonMap(1, page(width, height, local)), scroll).strokes.get(0);
            sameStroke(source, display, "random zoom and pan round trip");
        }
    }

    private static void invalidGeometryIsRejected() {
        for (float value : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY})
            invalid(() -> new NotebookViewport(1, 100, 200, 400, 500, 24, value, 0));
        NotebookViewport view = new NotebookViewport(1, 100, 200, 400, 500, 24);
        invalid(() -> view.withView(1, Float.NaN));
        invalid(() -> view.panForPoint(1, Float.NaN, 1));
        invalid(() -> view.scrollForPoint(1, 1, Float.POSITIVE_INFINITY));
        invalid(() -> view.fittedZoom(NotebookViewport.Fit.CUSTOM, 1));
    }

    private static float screenX(NotebookViewport view, int id, float x) { return view.pageLeft(id) + x * view.scale(id); }
    private static float screenY(NotebookViewport view, int id, float y, float scroll) { return view.pageTop(id, scroll) + y * view.scale(id); }
    private static NotebookPage page(int width, int height, NotebookPage.Stroke stroke) {
        return NotebookPage.empty().withSize(width, height).withStrokes(Collections.singletonList(stroke));
    }
    private static NotebookPage.Point point(float x, float y) { return point(x, y, 0, 1); }
    private static NotebookPage.Point point(float x, float y, long time, float pressure) { return new NotebookPage.Point(x, y, time, pressure); }
    private static NotebookPage.Stroke stroke(float width, NotebookPage.Point... points) {
        return new NotebookPage.Stroke(0, width, 0xff001122, Arrays.asList(points));
    }
    private static void endpoint(NotebookPage.Stroke stroke, int index, double x, double y, long time, float pressure) {
        NotebookPage.Point point = stroke.points.get(index);
        near(x, point.x, "point x"); near(y, point.y, "point y");
        check(time == point.time, "event time retained"); near(pressure, point.pressure, "pressure retained");
    }
    private static void sameStroke(NotebookPage.Stroke expected, NotebookPage.Stroke actual, String message) {
        near(expected.width, actual.width, message + " width");
        check(expected.penType == actual.penType && expected.color == actual.color && expected.points.size() == actual.points.size(), message + " style and count");
        for (int i = 0; i < expected.points.size(); i++) {
            NotebookPage.Point p = expected.points.get(i);
            endpoint(actual, i, p.x, p.y, p.time, p.pressure);
        }
    }
    private static void near(double expected, double actual, String message) {
        check(Math.abs(expected - actual) <= .002, message + ": expected " + expected + ", got " + actual);
    }
    private static void invalid(Runnable action) {
        try { action.run(); throw new AssertionError("Invalid geometry accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
