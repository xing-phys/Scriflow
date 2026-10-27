package org.eink.inkprobe.notebook;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Native nib width through delayed-UP capture, durable edits, viewport fitting and PDF. */
public final class NotebookPenConfigTest {
    private static int assertions;
    private static final int BLACK = 0xff000000;
    private static final float[] WIDTHS = {1, 2, 3.7f, 4, 6, 8.9f, 10};

    public static void main(String[] args) throws Exception {
        selectionCompatibility();
        delayedCompletionAndDurableEdits();
        fittedCaptureAndPdf();
        fixedScreenInputKeepsPhysicalNibAcrossViews();
        fixedPageInputKeepsNormalizedPressureAcrossViews();
        tinyPdfKeepsTransientDisplayWidthOutOfStorage();
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

    private static void fixedScreenInputKeepsPhysicalNibAcrossViews() throws Exception {
        // The former live configuration scaled the nib before the firmware saw it.
        // Below four native pixels pressure is ignored; at/above four it is rounded.
        // This is a captured-firmware rule fixture, not a host native-brush substitute.
        check(firmwareWidth(4 * .5f, .5f) != firmwareWidth(4, .5f),
            "fixture reproduces zoom changing the selected nib's pressure response");
        check(firmwareWidth(4 * .5f, .1f) / .5f != firmwareWidth(4, .1f),
            "inverse fitting cannot undo a firmware pressure-threshold change");

        float roundingScale = 900f / 1004;
        NotebookPenConfig.Style exact = NotebookPenConfig.nativeInput(2, 4, BLACK, roundingScale);
        check(exact.width * exact.captureScale < 4,
            "fixture crosses the pressure threshold if exact native width is reconstructed");
        check(Float.floatToIntBits(exact.captureWidth) == Float.floatToIntBits(4),
            "native input retains the exact nib width independently of inverse fitting");
        NotebookPage.Stroke delayed = exact.stroke(points(40, 100));
        NotebookPenConfig.Style next = NotebookPenConfig.nativeInput(2, 10, BLACK, .5f);
        check(next.captureWidth == 10 && delayed.captureWidth == 4,
            "a delayed completion retains its DOWN-time native nib after the next style changes");
        near(roundingScale, delayed.captureScale, "delayed completion retains its own coordinate scale");

        File directory = Files.createTempDirectory("native-nib-views-").toFile();
        List<NotebookViewport.Size> sizes = Arrays.asList(new NotebookViewport.Size(1200, 1800),
            new NotebookViewport.Size(1800, 1200), new NotebookViewport.Size(600, 2200));
        try {
            for (int orientation = 0; orientation < 2; orientation++) {
                int viewportWidth = orientation == 0 ? 900 : 1200;
                int viewportHeight = orientation == 0 ? 650 : 800;
                List<NotebookPage> reopened = new ArrayList<>();
                for (int number = 1; number <= sizes.size(); number++) {
                    NotebookViewport.Size size = sizes.get(number - 1);
                    File pageDirectory = new File(directory, orientation + "-" + number);
                    NotebookDocument document = new NotebookDocument(pageDirectory);
                    document.load(); document.initializeSize(size.width, size.height);
                    List<NotebookPage.Stroke> expected = new ArrayList<>();
                    for (float zoom : new float[]{.25f, .5f, 1, 1.125f, 2, 4}) {
                        NotebookViewport view = new NotebookViewport(sizes, viewportWidth,
                            viewportHeight, 24, zoom, 0);
                        float pageScale = view.scale(number), scroll = view.pageScroll(number);
                        float left = view.pageLeft(number), top = view.pageTop(number, scroll);
                        for (float surfaceScale : new float[]{.5f, 1, roundingScale}) {
                            for (float nativeWidth : WIDTHS) {
                                NotebookPenConfig.Style down = NotebookPenConfig.nativeInput(
                                    2, nativeWidth, BLACK, surfaceScale);
                                near(nativeWidth / surfaceScale, down.width,
                                    "widget coordinates account for its framebuffer fitting once");
                                near(surfaceScale, down.captureScale, "native coordinate scale captured at DOWN");
                                check(Float.floatToIntBits(down.captureWidth) == Float.floatToIntBits(nativeWidth),
                                    "selected nib is sent to native input without zoom-dependent rounding");
                                List<NotebookPage.Point> screenPoints = Arrays.asList(
                                    new NotebookPage.Point(left + 20, top + 40, 1000, 0),
                                    new NotebookPage.Point(left + 40, top + 45, 1010, .1f),
                                    new NotebookPage.Point(left + 60, top + 50, 1020, .5f),
                                    new NotebookPage.Point(left + 80, top + 55, 1030, 1));
                                NotebookPage.Stroke captured = down.stroke(screenPoints);
                                Map<Integer, List<NotebookPage.Stroke>> split = view.splitStroke(captured, scroll);
                                check(split.size() == 1 && split.containsKey(number),
                                    "native input belongs to its mixed-size page at every zoom and orientation");
                                NotebookPage.Stroke saved = split.get(number).get(0);
                                near(nativeWidth / (surfaceScale * pageScale), saved.width,
                                    "a screen-constant nib is converted to that page's logical units");
                                near(surfaceScale * pageScale, saved.captureScale,
                                    "stored source scale includes both independent fitting steps");
                                check(Float.floatToIntBits(saved.captureWidth) == Float.floatToIntBits(nativeWidth),
                                    "mixed-size page inversion preserves the exact native nib");
                                NotebookPage source = NotebookPage.empty().withSize(size.width, size.height).withStroke(saved);
                                NotebookPage.Stroke shown = view.compose(Collections.singletonMap(number, source), scroll)
                                    .strokes.get(0);
                                near(nativeWidth, shown.width * surfaceScale,
                                    "writing and restoring at its capture view have the same physical nib width");
                                near(surfaceScale, shown.captureScale,
                                    "restored input-space geometry uses its original physical coordinate scale");
                                for (int sample = 0; sample < screenPoints.size(); sample++) {
                                    NotebookPage.Point original = screenPoints.get(sample), restored = shown.points.get(sample);
                                    near(original.x, restored.x, "restored x matches captured widget position");
                                    near(original.y, restored.y, "restored y matches captured widget position");
                                    check(Float.floatToIntBits(original.pressure) == Float.floatToIntBits(restored.pressure),
                                        "raw pressure is unchanged by zoom and orientation");
                                    check(original.time == restored.time, "raw event timing is unchanged by zoom and orientation");
                                    near(firmwareWidth(nativeWidth, original.pressure),
                                        firmwareWidth(shown.captureWidth, restored.pressure) / shown.captureScale * surfaceScale,
                                        "restored pressure-rule result matches the selected native nib");
                                }
                                document.append(saved); expected.add(saved);
                            }
                        }
                    }
                    NotebookPage loaded = new NotebookStore(pageDirectory).load();
                    assertStrokes(expected, loaded);
                    reopened.add(loaded);
                }
                assertPdfInputs(reopened, "Native nib after rotation");
            }
        } finally { deleteTree(directory); }
    }

    private static void fixedPageInputKeepsNormalizedPressureAcrossViews() throws Exception {
        NotebookPenConfig.Style down = NotebookPenConfig.pageInput(2, 4, BLACK, .5f);
        NotebookPenConfig.Style next = NotebookPenConfig.nativeInput(2, 10, BLACK, 1);
        check(down.pageInput && !next.pageInput, "page and screen styles select their own input pipeline");
        NotebookPage.Stroke delayed = down.stroke(points(40, 100));
        near(2, delayed.width, "delayed page-mode completion keeps its DOWN-time screen width");
        near(2, delayed.captureScale, "delayed page-mode completion keeps its DOWN-time canonical coordinate conversion");
        near(4, delayed.captureWidth, "switching mode before completion cannot change the canonical brush width");

        File directory = Files.createTempDirectory("page-nib-views-").toFile();
        List<NotebookViewport.Size> sizes = Arrays.asList(new NotebookViewport.Size(1200, 1800),
            new NotebookViewport.Size(1800, 1200), new NotebookViewport.Size(600, 2200));
        List<NotebookPage.Point> logicalPoints = Arrays.asList(
            new NotebookPage.Point(20, 40, 1000, 0), new NotebookPage.Point(40, 45, 1010, .1f),
            new NotebookPage.Point(60, 50, 1020, .5f), new NotebookPage.Point(80, 55, 1030, 1));
        try {
            for (int orientation = 0; orientation < 2; orientation++) {
                int viewportWidth = orientation == 0 ? 900 : 1200;
                int viewportHeight = orientation == 0 ? 650 : 800;
                List<NotebookPage> reopened = new ArrayList<>();
                for (int number = 1; number <= sizes.size(); number++) {
                    NotebookViewport.Size size = sizes.get(number - 1);
                    File pageDirectory = new File(directory, orientation + "-" + number);
                    NotebookDocument document = new NotebookDocument(pageDirectory);
                    document.load(); document.initializeSize(size.width, size.height);
                    List<NotebookPage.Stroke> expected = new ArrayList<>();
                    for (float zoom : new float[]{.25f, .5f, 1, 1.125f, 2, 4}) {
                        NotebookViewport view = new NotebookViewport(sizes, viewportWidth,
                            viewportHeight, 24, zoom, 0);
                        float pageScale = view.scale(number), scroll = view.pageScroll(number);
                        float left = view.pageLeft(number), top = view.pageTop(number, scroll);
                        List<NotebookPage.Point> screenPoints = new ArrayList<>();
                        for (NotebookPage.Point point : logicalPoints)
                            screenPoints.add(new NotebookPage.Point(left + point.x * pageScale,
                                top + point.y * pageScale, point.time, point.pressure));
                        for (float pageWidth : WIDTHS) {
                            NotebookPenConfig.Style style = NotebookPenConfig.pageInput(2, pageWidth, BLACK, pageScale);
                            check(style.pageInput, "fixed-page width always requests the canonical input pipeline");
                            near(pageWidth * pageScale, style.width, "page nib preview follows the paper's display scale");
                            near(1f / pageScale, style.captureScale, "only coordinates are inversely transformed before brush input");
                            check(Float.floatToIntBits(style.captureWidth) == Float.floatToIntBits(pageWidth),
                                "zoom never changes the native brush width or its pressure threshold");
                            NotebookPage.Stroke captured = style.stroke(screenPoints);
                            Map<Integer, List<NotebookPage.Stroke>> split = view.splitStroke(captured, scroll);
                            check(split.size() == 1 && split.containsKey(number),
                                "canonical input belongs to its mixed-size page in both orientations");
                            NotebookPage.Stroke saved = split.get(number).get(0);
                            near(pageWidth, saved.width, "new strokes have the same page width at every zoom and orientation");
                            near(1, saved.captureScale, "stored canonical brush coordinates are normalized to the page");
                            check(Float.floatToIntBits(saved.captureWidth) == Float.floatToIntBits(pageWidth),
                                "stored canonical width remains exact at the four-pixel pressure boundary");
                            NotebookPage source = NotebookPage.empty().withSize(size.width, size.height).withStroke(saved);
                            for (int sample = 0; sample < logicalPoints.size(); sample++) {
                                NotebookPage.Point original = logicalPoints.get(sample), stored = saved.points.get(sample);
                                near(original.x, stored.x * saved.captureScale, "canonical x is independent of capture zoom");
                                near(original.y, stored.y * saved.captureScale, "canonical y is independent of capture zoom");
                                check(Float.floatToIntBits(original.pressure) == Float.floatToIntBits(stored.pressure),
                                    "fixed-page mode passes the original pressure to the same canonical brush");
                                check(original.time == stored.time, "fixed-page mode preserves brush timing");
                                near(firmwareWidth(pageWidth, original.pressure),
                                    firmwareWidth(saved.captureWidth, stored.pressure) / saved.captureScale,
                                    "normalized pressure-rule result is independent of capture zoom and orientation");
                            }
                            // Existing ink follows paper scaling when subsequently viewed elsewhere.
                            for (float displayZoom : new float[]{.5f, 2}) {
                                NotebookViewport display = new NotebookViewport(sizes, viewportWidth,
                                    viewportHeight, 24, displayZoom, 0);
                                NotebookPage.Stroke shown = display.compose(Collections.singletonMap(number, source),
                                    display.pageScroll(number)).strokes.get(0);
                                near(pageWidth * display.scale(number), shown.width,
                                    "completed page-mode ink scales with the paper after capture");
                                near(1f / display.scale(number), shown.captureScale,
                                    "display changes transform the finished canonical geometry");
                                check(Float.floatToIntBits(shown.captureWidth) == Float.floatToIntBits(pageWidth),
                                    "scroll and display zoom never change the canonical pressure brush");
                            }
                            document.append(saved); expected.add(saved);
                        }
                    }
                    NotebookPage loaded = new NotebookStore(pageDirectory).load();
                    assertStrokes(expected, loaded); reopened.add(loaded);
                }
                assertPdfInputs(reopened, "Page nib after rotation");
            }
        } finally { deleteTree(directory); }
    }

    private static void assertPdfInputs(List<NotebookPage> pages, String title) throws Exception {
        List<NotebookPage.Stroke> expected = new ArrayList<>();
        for (NotebookPage page : pages) expected.addAll(page.strokes);
        final int[] rendered = {0};
        NotebookPdf.write(new ByteArrayOutputStream(), title, pages.size(), number -> pages.get(number - 1),
            null, stroke -> {
                check(stroke == expected.get(rendered[0]++),
                    "PDF receives the exact reopened canonical width, raw pressure and timing");
                return NotebookOutlineFixture.outline(stroke);
            });
        check(rendered[0] == expected.size(), "PDF exports every mixed-size stroke without changing its brush input");
    }

    private static void tinyPdfKeepsTransientDisplayWidthOutOfStorage() throws Exception {
        NotebookViewport view = new NotebookViewport(1, 1, 2, 1400, 1000, 24);
        NotebookPenConfig.Style style = NotebookPenConfig.pageInput(2, 10, BLACK, view.scale(1));
        near(14000, style.width, "a tiny PDF page can have a display width above the native nib limit");
        near(10, style.captureWidth, "tiny-page fitting keeps the actual native brush within its supported width");
        NotebookPage.Stroke captured = style.stroke(Arrays.asList(
            new NotebookPage.Point(140, 140, 10, .1f), new NotebookPage.Point(280, 280, 20, 1)));
        NotebookPage.Stroke logical = view.splitStroke(captured, 0).get(1).get(0);
        near(10, logical.width, "inverse fitting restores the selected durable page width");
        near(1, logical.captureScale, "tiny-page brush input normalizes to logical coordinates");
        near(10, logical.captureWidth, "tiny-page inversion preserves the exact native nib");
        File directory = Files.createTempDirectory("tiny-pdf-nib-").toFile();
        try {
            NotebookStore.initializePdfImport(directory, 1, 2, 1);
            NotebookDocument document = new NotebookDocument(directory); document.load(); document.append(logical);
            NotebookPage loaded = new NotebookStore(directory).load();
            assertStrokes(Collections.singletonList(logical), loaded);
            check(loaded.pdfPage == 1 && loaded.width == 1 && loaded.height == 2,
                "tiny-page save retains the original PDF page reference and geometry");
            NotebookPage shown = view.compose(Collections.singletonMap(1, loaded), 0);
            near(14000, shown.strokes.get(0).width, "restored tiny-page ink remains representable at high display scale");
            for (NotebookPage transientPage : Arrays.asList(
                    NotebookPage.empty().withSize(view.width, view.height).withStroke(captured), shown)) {
                boolean rejected = false;
                try { NotebookStore.writeSnapshot(new ByteArrayOutputStream(), transientPage); }
                catch (IOException expected) { rejected = true; }
                check(rejected, "transient broad display geometry cannot replace durable logical-page ink");
            }
            assertPdfInputs(Collections.singletonList(loaded), "Tiny PDF canonical nib");
        } finally { deleteTree(directory); }
    }

    private static float firmwareWidth(float width, float pressure) {
        return width < 4 ? width : (int) (width * (1 + pressure) / 2);
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
            near(source.captureScale, actual.captureScale, "stored native coordinate scale is per stroke");
            check(Float.floatToIntBits(source.captureWidth) == Float.floatToIntBits(actual.captureWidth),
                "stored native nib width is exact and per stroke");
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
