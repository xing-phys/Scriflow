package org.eink.inkprobe.notebook;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Mixed pen widths through delayed-UP capture, durable edits, viewport fitting and PDF. */
public final class NotebookPenConfigTest {
    private static int assertions;
    private static final int BLACK = 0xff000000;
    private static final float[] WIDTHS = {1, 2, 3.7f, 4, 6, 8.9f, 10};

    public static void main(String[] args) throws Exception {
        selectionCompatibility();
        delayedCompletionAndDurableEdits();
        fittedCaptureAndPdf();
        System.out.println("NotebookPenConfig: " + assertions + " assertions passed (host JVM, real files).");
    }

    private static void selectionCompatibility() {
        near(4, NotebookPenConfig.DEFAULT_WIDTH, "updating preserves the former default width");
        for (float width : WIDTHS)
            near(width, NotebookPenConfig.restore(width), "legacy and intermediate saved widths restore unchanged");
        for (float invalid : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY, .9f, 10.1f})
            check(NotebookPenConfig.restore(invalid) == NotebookPenConfig.DEFAULT_WIDTH,
                "unknown or invalid saved preference safely restores the old default");
    }

    private static void delayedCompletionAndDurableEdits() throws Exception {
        File directory = Files.createTempDirectory("notebook-widths-").toFile();
        try {
            NotebookDocument document = new NotebookDocument(directory);
            document.load(); document.initializeSize(140, 300);
            // A pre-feature width-4 stroke remains unchanged alongside the new choices.
            List<NotebookPage.Stroke> expected = new ArrayList<>();
            expected.add(new NotebookPage.Stroke(4, 4, BLACK, points(10, 100)));
            document.append(expected.get(0));
            NotebookPenConfig.Style next = new NotebookPenConfig.Style(4, 1, BLACK);
            for (int i = 0; i < WIDTHS.length; i++) {
                next = new NotebookPenConfig.Style(4, WIDTHS[i], BLACK);
                // Capture at DOWN; the selection changes before this UP is materialized.
                NotebookPenConfig.Style down = next;
                next = new NotebookPenConfig.Style(4,
                    WIDTHS[(i + 1) % WIDTHS.length], BLACK);
                NotebookPage.Stroke finished = down.stroke(points(40 + i * 30, 200 + i * 10));
                near(WIDTHS[i], finished.width,
                    "delayed completed stroke keeps the style from its own DOWN");
                expected.add(finished); document.append(finished);
            }
            assertStrokes(expected, new NotebookStore(directory).load());
            check(document.undo(), "undo accepts a stroke written at a different width");
            // Choosing another pen is separate from document history and must keep Redo.
            next = new NotebookPenConfig.Style(4, NotebookPenConfig.DEFAULT_WIDTH, BLACK);
            check(document.canRedo(), "a subsequent pen choice retains redo");
            check(document.redo(), "redo restores the original broad stroke");
            assertStrokes(expected, new NotebookStore(directory).load());
            check(document.erase(Collections.singletonList(new NotebookPage.Point(50, 224.9f, 400, 1)), 0),
                "eraser hits the edge of an extra-broad stroke using its own stored width");
            check(document.current().strokes.size() == expected.size() - 1, "erase removes only the broad stroke");
            check(document.undo(), "undo restores the erased broad stroke");
            assertStrokes(expected, new NotebookStore(directory).load());
            NotebookDocument reopened = new NotebookDocument(directory); reopened.load();
            assertStrokes(expected, reopened.current());
        } finally { deleteTree(directory); }
    }

    private static void fittedCaptureAndPdf() throws Exception {
        List<NotebookPage.Stroke> logical = new ArrayList<>();
        for (int i = 0; i < WIDTHS.length; i++) {
            float width = WIDTHS[i];
            for (int viewportWidth : new int[]{70, 140, 280}) {
                NotebookViewport viewport = new NotebookViewport(2, 140, 300, viewportWidth, 240, 12);
                float scale = viewport.scale(2), scroll = viewport.pageScroll(2);
                float y = viewport.pageTop(2, scroll) + 40 * scale;
                NotebookPage.Stroke captured = new NotebookPenConfig.Style(4, width * scale, BLACK).stroke(
                    Arrays.asList(new NotebookPage.Point(20 * scale, y, 500, .25f),
                        new NotebookPage.Point(80 * scale, y, 510, .75f)));
                Map<Integer, List<NotebookPage.Stroke>> split = viewport.splitStroke(captured, scroll);
                check(split.size() == 1 && split.containsKey(2), "fitted pen input belongs to the intended page");
                NotebookPage.Stroke stored = split.get(2).get(0);
                near(width, stored.width, "inverse fitting stores the selected logical width");
                near(20, stored.points.get(0).x, "inverse fitting retains logical coordinates");
                NotebookPage page = NotebookPage.empty().withSize(140, 300).withStrokes(split.get(2));
                NotebookPage restored = viewport.compose(Collections.singletonMap(2, page), scroll);
                near(captured.width, restored.strokes.get(0).width, "scroll restoration retains the fitted display width");
            }
            logical.add(new NotebookPenConfig.Style(4, width, BLACK).stroke(points(40 + i * 30, 600 + i * 10)));
            // Dots also need the selected diameter rather than the old fixed default.
            logical.add(new NotebookPenConfig.Style(4, width, BLACK).stroke(
                Collections.singletonList(new NotebookPage.Point(100, 40 + i * 30, 700 + i, .5f))));
        }
        NotebookPage page = NotebookPage.empty().withSize(140, 300).withStrokes(logical);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<NotebookPage.Stroke> rendered = new ArrayList<>();
        NotebookPdf.write(output, "Mixed thickness", 1, number -> page, null, stroke -> {
            rendered.add(stroke);
            return NotebookOutlineFixture.outline(stroke);
        });
        for (int i = 0; i < logical.size(); i++)
            check(rendered.get(i) == logical.get(i), "outline provider receives original per-stroke width, pressure and timing");
        check(page.strokes.size() == logical.size(), "PDF export leaves mixed-width page editable");
    }

    private static List<NotebookPage.Point> points(float y, long time) {
        return Arrays.asList(new NotebookPage.Point(20, y, time, .25f),
            new NotebookPage.Point(80, y, time + 10, .75f));
    }

    private static void assertStrokes(List<NotebookPage.Stroke> expected, NotebookPage page) {
        check(expected.size() == page.strokes.size(), "all mixed-width strokes preserved");
        for (int i = 0; i < expected.size(); i++) {
            NotebookPage.Stroke source = expected.get(i), actual = page.strokes.get(i);
            near(source.width, actual.width, "stored width is per stroke");
            check(source.penType == actual.penType && source.color == actual.color, "pen style retained");
            check(source.points.size() == actual.points.size(), "point count retained");
            for (int j = 0; j < source.points.size(); j++) {
                NotebookPage.Point a = source.points.get(j), b = actual.points.get(j);
                near(a.x, b.x, "point x retained"); near(a.y, b.y, "point y retained");
                near(a.pressure, b.pressure, "pressure retained");
                check(a.time == b.time, "point timing retained");
            }
        }
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) throw new AssertionError("Cannot remove pen-width fixture " + file);
    }
    private static void near(float expected, float actual, String message) {
        check(Math.abs(expected - actual) < .001f, message + ": " + expected + " != " + actual);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
