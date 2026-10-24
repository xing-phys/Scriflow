package org.eink.inkprobe.notebook;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Geometry checks independent of Android rendering and device storage. */
public final class NotebookViewportTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        layoutAndArbitraryScroll();
        anchorRoundTrips();
        crossPageWritingAndClippedAttributes();
        clippedExcursionsNeverBecomeChords();
        dotsAndZeroLengthSegments();
        mixedPageSizesAndComposition();
        eraseMappingAndImmutableOutputs();
        sampledSegmentClipping();
        roundedClippingIsAcceptedByStorage();
        strictTransformedBounds();
        invalidInput();
        System.out.println("NotebookViewport: " + assertions + " assertions passed (pure geometry).");
    }

    private static void layoutAndArbitraryScroll() {
        NotebookViewport layout = new NotebookViewport(3, 100, 100, 100, 180, 24);
        near(168, layout.maxScroll(), "three pages and two gaps");
        near(0, layout.clampScroll(-20), "clamp top");
        near(168, layout.clampScroll(999), "clamp bottom");
        near(-20.75, layout.pageTop(1, 20.75f), "fractional scroll does not snap");
        near(103.25, layout.pageTop(2, 20.75f), "page two includes gap");
        check(layout.visiblePages(80).equals(Arrays.asList(1, 2, 3)), "all partially visible pages enumerated");
        check(layout.visiblePages(100).equals(Arrays.asList(2, 3)), "zero-area page boundary excluded");
        check(layout.pageAt(50, 10, 80) == 1, "pen start chooses first page");
        check(layout.pageAt(50, 30, 80) == 0, "gap is not writable");
        check(layout.pageAt(50, 50, 80) == 2, "pen start chooses second page");
        check(layout.pageAt(-1, 10, 80) == 0 && layout.pageAt(50, 181, 80) == 0, "outside viewport");
        check(layout.centeredPage(80) == 2, "active page at viewport center");
        NotebookViewport shortBook = new NotebookViewport(1, 100, 40, 100, 200, 24);
        near(0, shortBook.maxScroll(), "short notebook cannot scroll");
        check(shortBook.pageAt(10, 80, 0) == 0, "blank below short book is not writable");
        check(shortBook.centeredPage(0) == 1, "short book still has active page");
        near(168, layout.pageScroll(3), "last page jump clamps instead of overscrolling");
    }

    private static void anchorRoundTrips() {
        NotebookViewport layout = new NotebookViewport(8, 1404, 1688, 702, 900, 24);
        for (float offset = 0; offset < layout.maxScroll(); offset += 17.25f) {
            int page = layout.anchorPage(offset);
            double fraction = layout.anchorFraction(offset);
            check(fraction >= 0 && fraction < 1, "anchor fraction covers content and gap");
            near(offset, layout.scrollForAnchor(page, fraction), "anchor preserves arbitrary position");
        }
        near(layout.maxScroll(), layout.scrollForAnchor(layout.anchorPage(layout.maxScroll()),
            layout.anchorFraction(layout.maxScroll())), "bottom anchor roundtrip");
        NotebookViewport pages = new NotebookViewport(3, 100, 100, 100, 50, 24);
        check(pages.anchorPage(110) == 1, "gap anchor belongs to preceding page");
        near(110, pages.scrollForAnchor(1, pages.anchorFraction(110)), "gap anchor restored");
        check(pages.anchorPage(124) == 2 && pages.anchorFraction(124) == 0, "exact next-page anchor");
        near(pages.maxScroll(), pages.scrollForAnchor(3, 1), "anchor beyond bottom clamped");
    }

    private static void crossPageWritingAndClippedAttributes() {
        NotebookViewport layout = new NotebookViewport(3, 100, 100, 100, 180, 24);
        NotebookPage.Stroke input = stroke(6, point(50, -10, 0, 0), point(50, 190, 200, 2));
        Map<Integer, List<NotebookPage.Stroke>> split = layout.splitStroke(input, 80);
        check(split.size() == 3, "cross-page stroke reaches three pages");
        NotebookPage.Stroke first = split.get(1).get(0), second = split.get(2).get(0), third = split.get(3).get(0);
        endpoint(first, 0, 50, 80, 10, .1f);
        endpoint(first, 1, 50, 100, 30, .3f);
        endpoint(second, 0, 50, 0, 54, .54f);
        endpoint(second, 1, 50, 100, 154, 1.54f);
        endpoint(third, 0, 50, 0, 178, 1.78f);
        endpoint(third, 1, 50, 12, 190, 1.9f);
        near(6, second.width, "pen width retained");
        check(second.color == input.color && second.penType == input.penType, "style retained");
        near(-10, input.points.get(0).y, "input stroke not mutated");
        Map<Integer, NotebookPage> pages = new HashMap<>();
        for (Map.Entry<Integer, List<NotebookPage.Stroke>> entry : split.entrySet()) pages.put(entry.getKey(), page(100, 100, entry.getValue()));
        NotebookPage composed = layout.compose(pages, 80);
        check(composed.width == 100 && composed.height == 180 && composed.strokes.size() == 3, "synthetic viewport geometry");
        check(composed.template == NotebookPage.Template.BLANK && composed.pointCount == 6, "synthetic tracks and point total");
        endpoint(composed.strokes.get(1), 0, 50, 44, 54, .54f);
        endpoint(composed.strokes.get(1), 1, 50, 144, 154, 1.54f);
    }

    private static void clippedExcursionsNeverBecomeChords() {
        NotebookViewport layout = new NotebookViewport(1, 100, 100, 100, 100, 24);
        NotebookPage.Stroke excursion = stroke(4, point(10, 20), point(-10, 20), point(-10, 80), point(10, 80));
        List<NotebookPage.Stroke> pieces = layout.splitStroke(excursion, 0).get(1);
        check(pieces.size() == 2, "outside excursion splits stroke");
        endpoint(pieces.get(0), 1, 0, 20, 0, 1);
        endpoint(pieces.get(1), 0, 0, 80, 0, 1);
        check(layout.splitStroke(stroke(4, point(-10, 10), point(-10, 90)), 0).isEmpty(), "offscreen path is not clamped into border ink");
        Map<Integer, NotebookPage> pages = Collections.singletonMap(1, page(100, 100, Arrays.asList(excursion)));
        check(layout.compose(pages, 0).strokes.size() == 2, "composition also separates excursions");
        List<NotebookPage.Stroke> crossing = layout.splitStroke(stroke(4, point(-20, -20), point(120, 120)), 0).get(1);
        endpoint(crossing.get(0), 0, 0, 0, 0, 1);
        endpoint(crossing.get(0), 1, 100, 100, 0, 1);
        List<NotebookPage.Stroke> tangent = layout.splitStroke(stroke(4, point(-10, 10), point(10, -10)), 0).get(1);
        check(tangent.get(0).points.size() == 1, "corner tangent retained as dot");
    }

    private static void dotsAndZeroLengthSegments() {
        NotebookViewport layout = new NotebookViewport(2, 100, 100, 100, 180, 24);
        Map<Integer, List<NotebookPage.Stroke>> dot = layout.splitStroke(stroke(4, point(20, 150, 50, .8f)), 0);
        check(dot.size() == 1 && dot.containsKey(2), "dot selects correct page");
        endpoint(dot.get(2).get(0), 0, 20, 26, 50, .8f);
        check(layout.splitStroke(stroke(4, point(20, 112)), 0).isEmpty(), "dot in gap discarded");
        check(layout.splitStroke(stroke(4, point(101, 20)), 0).isEmpty(), "outside dot discarded");
        List<NotebookPage.Stroke> repeated = layout.splitStroke(stroke(4, point(20, 20), point(20, 20)), 0).get(1);
        check(repeated.size() == 1 && repeated.get(0).points.size() == 1, "zero-length segment is a dot");
        check(layout.splitStroke(stroke(4, point(0, 0)), 0).containsKey(1), "boundary dot preserved");
    }

    private static void mixedPageSizesAndComposition() {
        NotebookViewport layout = new NotebookViewport(Arrays.asList(new NotebookViewport.Size(100, 100),
                new NotebookViewport.Size(200, 400)), 100, 180, 24);
        near(144, layout.maxScroll(), "varying page sizes fit width");
        near(.5, layout.scale(2), "second page scale");
        List<NotebookPage.Stroke> split = layout.splitStroke(stroke(4, point(50, 44), point(50, 144)), 80).get(2);
        near(8, split.get(0).width, "inverse width scaling");
        endpoint(split.get(0), 0, 100, 0, 0, 1);
        endpoint(split.get(0), 1, 100, 200, 0, 1);
        NotebookPage original = page(200, 400, Arrays.asList(stroke(8, point(100, 10, 10, .2f), point(100, 350, 350, .8f))));
        NotebookPage composed = layout.compose(Collections.singletonMap(2, original), 80);
        NotebookPage.Stroke visible = composed.strokes.get(0);
        near(4, visible.width, "composition width scaling");
        endpoint(visible, 0, 50, 49, 10, .2f);
        near(180, visible.points.get(1).y, "long segment intersects viewport bottom");
        check(visible.points.get(1).time == 272, "clipped time interpolation");
        near(.2 + .6 * 262 / 340, visible.points.get(1).pressure, "clipped pressure interpolation");
        check(layout.compose(Collections.<Integer, NotebookPage>emptyMap(), 80).strokes.isEmpty(), "unloaded pages do not materialize tracks");
        expectInvalid(() -> layout.compose(Collections.singletonMap(2, page(100, 100, Arrays.asList(stroke(4, point(1, 1))))), 80));
    }

    private static void eraseMappingAndImmutableOutputs() {
        NotebookViewport layout = new NotebookViewport(Arrays.asList(new NotebookViewport.Size(100, 100),
                new NotebookViewport.Size(200, 400)), 100, 180, 24);
        List<NotebookViewport.EraseGesture> gestures = layout.splitErase(Arrays.asList(point(50, 10), point(50, 170)), 12, 80);
        check(gestures.size() == 2, "eraser maps both visible pages");
        check(gestures.get(1).pageId == 2, "eraser IDs explicit and ordered");
        near(24, gestures.get(1).radius, "eraser radius converted into logical pixels");
        near(0, gestures.get(1).paths.get(0).get(0).y, "eraser clipped at page top");
        near(252, gestures.get(1).paths.get(0).get(1).y, "eraser logical endpoint");
        List<NotebookViewport.EraseGesture> separated = layout.splitErase(Arrays.asList(point(10, 20), point(-10, 20), point(-10, 10), point(10, 10)), 3, 0);
        check(separated.get(0).paths.size() == 2, "erase does not bridge separate visits");
        check(layout.splitErase(Arrays.asList(point(10, 112)), 3, 0).isEmpty(), "gap-only erase skipped");
        expectImmutable(() -> gestures.clear());
        expectImmutable(() -> gestures.get(0).paths.clear());
        expectImmutable(() -> gestures.get(0).paths.get(0).clear());
        Map<Integer, List<NotebookPage.Stroke>> split = layout.splitStroke(stroke(4, point(20, 20)), 0);
        expectImmutable(() -> split.clear());
        expectImmutable(() -> split.get(1).clear());
    }

    private static void sampledSegmentClipping() {
        NotebookViewport layout = new NotebookViewport(1, 100, 100, 100, 100, 24);
        Random random = new Random(731);
        for (int trial = 0; trial < 300; trial++) {
            NotebookPage.Point a = point(random.nextFloat() * 300 - 100, random.nextFloat() * 300 - 100);
            NotebookPage.Point b = point(random.nextFloat() * 300 - 100, random.nextFloat() * 300 - 100);
            Map<Integer, List<NotebookPage.Stroke>> result = layout.splitStroke(stroke(4, a, b), 0);
            for (List<NotebookPage.Stroke> list : result.values()) for (NotebookPage.Stroke stroke : list) {
                for (NotebookPage.Point p : stroke.points) {
                    check(p.x >= 0 && p.x <= 100 && p.y >= 0 && p.y <= 100, "clipped endpoint strictly inside page");
                    double cross = (p.x - a.x) * (b.y - a.y) - (p.y - a.y) * (b.x - a.x);
                    check(Math.abs(cross) < .01, "clipping preserves source segment instead of clamping corners");
                }
            }
            for (int sample = 0; sample <= 20; sample++) {
                float t = sample / 20f, x = a.x + (b.x - a.x) * t, y = a.y + (b.y - a.y) * t;
                if (x > .001 && x < 99.999 && y > .001 && y < 99.999) check(!result.isEmpty(), "any interior segment sample retained");
            }
        }
    }

    private static void roundedClippingIsAcceptedByStorage() throws Exception {
        NotebookViewport layout = new NotebookViewport(5, 692, 723, 1322, 2832, 24);
        Map<Integer, List<NotebookPage.Stroke>> split = layout.splitStroke(stroke(4,
                point(367.20837f, -1), point(457.07785f, 2833)), 3138.3281f);
        // Previously the bottom of page 4 became y=723.00006 after inverse scaling.
        NotebookPage.Stroke piece = split.get(4).get(0);
        near(723, piece.points.get(piece.points.size() - 1).y, "rounded page-bottom regression");
        File directory = Files.createTempDirectory("viewport-bounds-").toFile();
        try {
            for (Map.Entry<Integer, List<NotebookPage.Stroke>> entry : split.entrySet()) {
                File pageDirectory = new File(directory, Integer.toString(entry.getKey()));
                NotebookStore store = new NotebookStore(pageDirectory);
                store.load(); store.initializeSize(692, 723);
                for (NotebookPage.Stroke part : entry.getValue()) store.appendStroke(part);
                check(new NotebookStore(pageDirectory).load().strokes.size() == entry.getValue().size(),
                        "every clipped piece persists and reloads without a bounds error");
            }
        } finally { deleteTree(directory); }
    }

    private static void strictTransformedBounds() {
        Random random = new Random(5);
        for (int trial = 0; trial < 500; trial++) {
            int width = 500 + random.nextInt(1501), height = 600 + random.nextInt(2401);
            int viewportWidth = 500 + random.nextInt(1501), viewportHeight = 600 + random.nextInt(2401);
            NotebookViewport layout = new NotebookViewport(5, width, height, viewportWidth, viewportHeight, 24);
            float scroll = random.nextFloat() * layout.maxScroll();
            NotebookPage.Stroke source = stroke(4,
                    point(viewportWidth * (random.nextFloat() * 1.4f - .2f), -1),
                    point(viewportWidth * (random.nextFloat() * 1.4f - .2f), viewportHeight + 1));
            Map<Integer, NotebookPage> pages = new HashMap<>();
            for (Map.Entry<Integer, List<NotebookPage.Stroke>> entry : layout.splitStroke(source, scroll).entrySet()) {
                NotebookEdits editor = new NotebookEdits(page(width, height, Collections.<NotebookPage.Stroke>emptyList()));
                for (NotebookPage.Stroke part : entry.getValue()) {
                    checkBounds(part.points, width, height);
                    check(editor.append(part), "clipped stroke passes strict model validation");
                }
                pages.put(entry.getKey(), editor.current());
            }
            for (NotebookPage.Stroke part : layout.compose(pages, scroll).strokes)
                checkBounds(part.points, viewportWidth, viewportHeight);
            for (NotebookViewport.EraseGesture gesture : layout.splitErase(source.points, 12, scroll))
                for (List<NotebookPage.Point> path : gesture.paths) checkBounds(path, width, height);
        }
    }

    private static void checkBounds(List<NotebookPage.Point> points, int width, int height) {
        for (NotebookPage.Point p : points) check(p.x >= 0 && p.x <= width && p.y >= 0 && p.y <= height,
                "transformed endpoint respects strict persistence bounds");
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) throw new AssertionError("Could not remove test file: " + file);
    }

    private static void invalidInput() {
        expectInvalid(() -> new NotebookViewport(0, 100, 100, 100, 100, 24));
        expectInvalid(() -> new NotebookViewport(1, 0, 100, 100, 100, 24));
        expectInvalid(() -> new NotebookViewport(1, 100, 100, 0, 100, 24));
        expectInvalid(() -> new NotebookViewport(1, 100, 100, 100, 100, -1));
        expectInvalid(() -> new NotebookViewport(1, 100, 100, 100, 100, Float.NaN));
        NotebookViewport layout = new NotebookViewport(2, 100, 100, 100, 100, 24);
        expectInvalid(() -> layout.clampScroll(Float.NaN));
        expectInvalid(() -> layout.clampScroll(Float.POSITIVE_INFINITY));
        expectInvalid(() -> layout.pageTop(0, 0));
        expectInvalid(() -> layout.scale(3));
        expectInvalid(() -> layout.scrollForAnchor(1, -.1));
        expectInvalid(() -> layout.scrollForAnchor(1, Double.NaN));
        expectInvalid(() -> layout.splitStroke(null, 0));
        expectInvalid(() -> layout.splitErase(Collections.<NotebookPage.Point>emptyList(), 4, 0));
        expectInvalid(() -> layout.splitErase(Arrays.asList(point(1, 1)), -1, 0));
    }

    private static NotebookPage page(int width, int height, List<NotebookPage.Stroke> strokes) { return NotebookPage.empty().withSize(width, height).withStrokes(strokes); }
    private static NotebookPage.Point point(float x, float y) { return point(x, y, 0, 1); }
    private static NotebookPage.Point point(float x, float y, long time, float pressure) { return new NotebookPage.Point(x, y, time, pressure); }
    private static NotebookPage.Stroke stroke(float width, NotebookPage.Point... points) { return new NotebookPage.Stroke(2, width, 0xff123456, Arrays.asList(points)); }
    private static void endpoint(NotebookPage.Stroke stroke, int i, float x, float y, long time, float pressure) { NotebookPage.Point p = stroke.points.get(i); near(x, p.x, "point x"); near(y, p.y, "point y"); check(p.time == time, "point time"); near(pressure, p.pressure, "point pressure"); }
    private static void near(double expected, double actual, String message) { check(Math.abs(expected - actual) <= .001, message + " expected " + expected + " actual " + actual); }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static void expectInvalid(Runnable action) { try { action.run(); } catch (IllegalArgumentException expected) { assertions++; return; } throw new AssertionError("Expected IllegalArgumentException"); }
    private static void expectImmutable(Runnable action) { try { action.run(); } catch (UnsupportedOperationException expected) { assertions++; return; } throw new AssertionError("Expected UnsupportedOperationException"); }
}
