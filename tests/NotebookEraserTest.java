package org.eink.inkprobe.notebook;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Broad eraser selection, exact brush footprint, protected preview damage and durable edits. */
public final class NotebookEraserTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        independentWidthsAndRelease();
        gapCentresReachBothPages();
        marginCentresAreNotClamped();
        previewDamagePreservesControls();
        broadErasePersistsAndUndoes();
        System.out.println("NotebookEraser: " + assertions + " assertions passed (widths, footprint, damage and files).");
    }

    private static void independentWidthsAndRelease() {
        NotebookWidthRange eraser = NotebookWidthRange.ERASER;
        near(36, eraser.defaultWidth, "default diameter preserves the previous 18-unit radius");
        check(eraser.maximum > NotebookWidthRange.PEN.maximum * 20, "eraser has a much broader range than pen");
        for (int width = 4; width <= 240; width++)
            near(width, eraser.atFraction(eraser.fraction(width)), "every eraser width round-trips through thumb position");
        for (float width : new float[]{Float.NaN, Float.POSITIVE_INFINITY, 0, -1, 241})
            near(36, eraser.restore(width), "invalid eraser preference falls back independently");
        for (float width : new float[]{4, 36, 120, 240}) {
            NotebookPenSliderState slider = new NotebookPenSliderState(eraser, 36);
            NotebookPenSliderClose[] closing = new NotebookPenSliderClose[1];
            Float[] saved = {36f}; int[] callbacks = {0};
            closing[0] = new NotebookPenSliderClose(() -> {
                closing[0].finish(slider.end(3, 0, true)); // Popup-removal CANCEL must not lose the choice.
                closing[0].dismissed();
            }, selected -> { callbacks[0]++; if (selected != null) saved[0] = selected; });
            slider.begin(3, true, eraser.fraction(36));
            slider.move(3, eraser.fraction(width));
            closing[0].finish(slider.end(3, eraser.fraction(width), false));
            near(width, saved[0], "released eraser width is retained across popup teardown");
            near(width, new NotebookPenSliderState(eraser, saved[0]).width(), "eraser choice reopens unchanged");
            check(callbacks[0] == 1, "eraser width delivered once");
            near(3.7f, NotebookWidthRange.PEN.restore(3.7f), "eraser choice leaves fractional pen width valid");
        }
        check(eraser.label(120).equals("120"), "broad widths use compact integer labels");
    }

    private static void gapCentresReachBothPages() {
        NotebookViewport viewport = new NotebookViewport(2, 100, 100, 100, 180, 24);
        List<NotebookPage.Point> path = Collections.singletonList(point(50, 112));
        check(viewport.splitErase(path, 2, 0).isEmpty(), "thin eraser in middle of gap touches neither page");
        List<NotebookViewport.EraseGesture> gestures = viewport.splitErase(path, 20, 0);
        check(gestures.size() == 2, "wide grey brush in gap reaches both visible pages");
        NotebookEdits first = new NotebookEdits(page(100, 100, stroke(50, 95)));
        NotebookEdits second = new NotebookEdits(page(100, 100, stroke(50, 5)));
        check(first.erasePaths(gestures.get(0).paths, gestures.get(0).radius), "grey footprint hits bottom of first page");
        check(second.erasePaths(gestures.get(1).paths, gestures.get(1).radius), "grey footprint hits top of second page");
        check(first.current().strokes.isEmpty() && second.current().strokes.isEmpty(), "both touched strokes removed");
        check(first.undo() && second.undo(), "wide erase remains undoable on each page");
        for (int viewportWidth : new int[]{50, 100, 200}) {
            NotebookViewport fitted = new NotebookViewport(1, 100, 100, viewportWidth, 200, 24);
            float scale = fitted.scale(1);
            List<NotebookViewport.EraseGesture> mapped = fitted.splitErase(
                Collections.singletonList(point(50 * scale, 50 * scale)), 60 * scale, 0);
            near(60, mapped.get(0).radius, "width and eraser hit radius preserve the same fitted proportions");
        }
    }

    private static void marginCentresAreNotClamped() {
        NotebookViewport viewport = new NotebookViewport(1, 100, 100, 100, 100, 24);
        List<NotebookViewport.EraseGesture> gestures = viewport.splitErase(
            Collections.singletonList(point(-3, 50)), 4, 0);
        near(-3, gestures.get(0).paths.get(0).get(0).x, "eraser centre remains outside page instead of being clamped");
        NotebookEdits miss = new NotebookEdits(page(100, 100, stroke(3, 50)));
        check(!miss.erasePaths(gestures.get(0).paths, 4), "visible grey edge does not reach distant ink");
        NotebookEdits hit = new NotebookEdits(page(100, 100, stroke(0, 50)));
        check(hit.erasePaths(gestures.get(0).paths, 4), "same brush correctly reaches ink at the page edge");
        check(viewport.splitErase(Collections.singletonList(point(-20, 50)), 4, 0).isEmpty(),
            "fully offscreen brush does not invent an edge erase");
    }

    private static void previewDamagePreservesControls() {
        NotebookEraserDamage.Box dot = NotebookEraserDamage.sweep(500, 500, 500, 500, 240, 1000, 1000);
        check(dot.left == 378 && dot.top == 378 && dot.right == 622 && dot.bottom == 622,
            "damage includes full broad round-cap diameter and antialias fringe");
        check(NotebookEraserDamage.sweep(-300, -300, -250, -250, 4, 1000, 1000) == null,
            "fully offscreen preview needs no native post");
        Random random = new Random(19);
        for (int i = 0; i < 100; i++) {
            NotebookEraserDamage.Box dirty = NotebookEraserDamage.sweep(random.nextInt(400), random.nextInt(400),
                random.nextInt(400), random.nextInt(400), 4 + random.nextInt(237), 400, 400);
            NotebookEraserDamage.Box hole = new NotebookEraserDamage.Box(120, 100, 300, 160);
            List<NotebookEraserDamage.Box> regions = NotebookEraserDamage.excluding(dirty, hole);
            long sum = 0;
            for (int j = 0; j < regions.size(); j++) {
                NotebookEraserDamage.Box region = regions.get(j);
                check(region.left >= 0 && region.top >= 0 && region.right <= 400 && region.bottom <= 400,
                    "native eraser refresh stays on the surface");
                check(overlap(region, hole) == 0, "native eraser refresh never covers a floating button");
                for (int k = 0; k < j; k++) check(overlap(region, regions.get(k)) == 0,
                    "damage pieces do not redundantly refresh the same pixels");
                sum += area(region);
            }
            check(sum == area(dirty) - overlap(dirty, hole), "only toolbar pixels are excluded from preview damage");
        }
        check(NotebookEraserDamage.excluding(dot, new NotebookEraserDamage.Box(0, 0, 1000, 1000)).isEmpty(),
            "wholly covered damage produces no display update");
    }

    private static void broadErasePersistsAndUndoes() throws Exception {
        File directory = Files.createTempDirectory("notebook-broad-eraser-").toFile();
        try {
            NotebookDocument document = new NotebookDocument(directory);
            document.load(); document.initializeSize(400, 400);
            document.append(stroke(20, 40)); document.append(stroke(80, 80)); document.append(stroke(180, 100));
            List<NotebookPage.Point> path = Arrays.asList(point(50, 0), point(50, 200));
            check(document.erase(path, 60), "wide completed gesture removes touched strokes");
            NotebookPage saved = new NotebookStore(directory).load();
            check(saved.strokes.size() == 1 && saved.strokes.get(0).points.get(0).x == 180,
                "reopening retains only unaffected ink, never the grey preview");
            check(saved.strokes.get(0).color == 0xff000000, "saved ink remains black");
            check(document.undo(), "whole broad gesture is one undo command");
            check(new NotebookStore(directory).load().strokes.size() == 3, "undo durably restores both touched strokes");
            check(document.redo(), "redo applies the complete broad gesture");
            check(new NotebookStore(directory).load().strokes.size() == 1, "redo persists the correct remaining ink");
        } finally { deleteTree(directory); }
    }

    private static long area(NotebookEraserDamage.Box box) {
        return (long) (box.right - box.left) * (box.bottom - box.top);
    }
    private static long overlap(NotebookEraserDamage.Box a, NotebookEraserDamage.Box b) {
        return (long) Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left))
            * Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
    }
    private static NotebookPage.Point point(float x, float y) { return new NotebookPage.Point(x, y, 100, 1); }
    private static NotebookPage.Stroke stroke(float x, float y) {
        return new NotebookPage.Stroke(4, 2, 0xff000000, Collections.singletonList(point(x, y)));
    }
    private static NotebookPage page(int width, int height, NotebookPage.Stroke stroke) {
        return NotebookPage.empty().withSize(width, height).withStroke(stroke);
    }
    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) throw new AssertionError("Cannot remove eraser fixture " + file);
    }
    private static void near(float expected, float actual, String message) {
        check(Math.abs(expected - actual) < .0001f, message + ": " + expected + " != " + actual);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
