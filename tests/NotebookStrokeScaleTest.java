package org.eink.inkprobe.notebook;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.zip.CRC32;

/** Capture-space geometry, durable compatibility, and editable-copy regressions. */
public final class NotebookStrokeScaleTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        nativePixelQuantizationSurvivesEveryView();
        scrollingKeepsWholeBrushInput();
        durableScaleSurvivesEditsAndTransport();
        versionFourReadsStayNonMutating();
        externalGeometryChangesFailClosed();
        invalidGeometryCannotEnterStorage();
        System.out.println("NotebookStrokeScale: " + assertions + " assertions passed (host geometry and real files; native device check separate).");
    }

    private static void nativePixelQuantizationSurvivesEveryView() {
        // Firmware YuanZhuBi uses width unchanged below 4 native pixels; otherwise
        // it truncates width*(1+pressure)/2. The previous scale-first replay differs.
        NotebookViewport writing = new NotebookViewport(1, 1200, 1800, 900, 650, 24, 1.5f, 0);
        float scale = writing.scale(1);
        NotebookPage.Stroke input = stroke(4 * scale, 1, 180, 100, .1f);
        NotebookPage.Stroke local = writing.splitStroke(input, 0).get(1).get(0);
        near(scale, local.captureScale, "native input scale retained after page inverse");
        float live = firmwareWidth(input.width, .1f);
        check(Math.abs(live - firmwareWidth(local.width, .1f) * scale) > .2f,
            "fixture reproduces thicker replay caused by native integer-width rounding");
        for (float zoom : new float[]{.25f, .5f, 1f, 1.5f, 2f, 4f}) {
            NotebookViewport view = new NotebookViewport(1, 1200, 1800, 900, 650, 24, zoom, 0);
            NotebookPage.Stroke shown = view.compose(Collections.singletonMap(1,
                NotebookPage.empty().withSize(1200, 1800).withStroke(local)), 0).strokes.get(0);
            near(input.width, shown.width * shown.captureScale, "every zoom replays original native width");
            near(live / scale, firmwareWidth(shown.width * shown.captureScale, .1f)
                / shown.captureScale / view.scale(1), "every zoom preserves canonical logical outline width");
            near(.1, shown.points.get(0).pressure, "pressure retained");
            check(shown.points.get(0).time == input.points.get(0).time, "event time retained");
        }
        for (float width : new float[]{1, 3.9f, 4, 4.5f, 6, 10}) for (float pressure : new float[]{0, .1f, .5f, 1}) {
            NotebookPage.Stroke screen = stroke(width, 1, 180, 100, pressure);
            NotebookPage.Stroke saved = writing.splitStroke(screen, 0).get(1).get(0);
            near(firmwareWidth(width, pressure) / scale,
                firmwareWidth(saved.width * saved.captureScale, pressure) / saved.captureScale,
                "threshold and pressure cases retain input-space geometry");
        }
        NotebookViewport threshold = new NotebookViewport(1, 1004, 1800, 900, 650, 24);
        NotebookPage.Stroke exact = threshold.splitStroke(stroke(4, 1, 180, 100, .1f), 0).get(1).get(0);
        check(exact.width * exact.captureScale < 4,
            "fixture exposes floating-point reconstruction crossing the four-pixel pressure threshold");
        near(4, exact.captureWidth, "exact input brush width is retained independently of inverse fitting");
        near(2, firmwareWidth(exact.captureWidth, .1f), "exact native pressure behavior survives the rounding boundary");
    }

    private static void scrollingKeepsWholeBrushInput() {
        NotebookPage.Stroke source = new NotebookPage.Stroke(2, 6, 0xff000000, Arrays.asList(
            new NotebookPage.Point(30, 20, 10, .1f), new NotebookPage.Point(40, 500, 100, .8f),
            new NotebookPage.Point(60, 900, 200, .2f)), 1.25f);
        NotebookViewport view = new NotebookViewport(1, 100, 1000, 100, 200, 24);
        NotebookPage page = NotebookPage.empty().withSize(100, 1000).withStroke(source);
        NotebookPage.Stroke top = view.compose(Collections.singletonMap(1, page), 0).strokes.get(0);
        NotebookPage.Stroke middle = view.compose(Collections.singletonMap(1, page), 400).strokes.get(0);
        check(top.points.size() == 3 && middle.points.size() == 3, "scrolling never restarts the native brush at a clipped point");
        near(-380, middle.points.get(0).y, "offscreen original DOWN remains available for native replay");
        check(top.clip != null && middle.clip != null, "finished vectors are clipped to the visible page");
        near(0, middle.clip.top, "viewport clip top"); near(200, middle.clip.bottom, "viewport clip bottom");
        NotebookPage distant = NotebookPage.empty().withSize(100, 1000).withStroke(stroke(6, 1, 50, 800, 1));
        check(view.compose(Collections.singletonMap(1, distant), 0).strokes.isEmpty(),
            "distant ink is excluded before expensive native replay");
        NotebookPage thickEdge = NotebookPage.empty().withSize(100, 1000).withStroke(stroke(20, 1, 50, 205, 1));
        check(view.compose(Collections.singletonMap(1, thickEdge), 0).strokes.size() == 1,
            "broad visible edges are retained when their centreline is outside the view");
    }

    private static void durableScaleSurvivesEditsAndTransport() throws Exception {
        File directory = Files.createTempDirectory("stroke-scale-").toFile();
        File copy = Files.createTempDirectory("stroke-scale-copy-").toFile();
        try {
            NotebookDocument document = new NotebookDocument(directory);
            document.load(); document.initializeSize(1200, 1800);
            NotebookPage.Stroke input = stroke(4, 1.125f, 40, 50, .25f);
            document.append(input);
            near(input.captureScale, new NotebookStore(directory).load().strokes.get(0).captureScale,
                "journal preserves native coordinate scale");
            near(input.captureWidth, new NotebookStore(directory).load().strokes.get(0).captureWidth,
                "journal preserves exact native brush width");
            check(document.undo() && document.redo(), "undo/redo exercises edited snapshot");
            NotebookPage page = new NotebookStore(directory).load();
            near(input.captureScale, page.strokes.get(0).captureScale, "snapshot and redo retain native scale");
            List<NotebookPage.Stroke> moved = NotebookLasso.translated(NotebookLasso.all(page), page, 10, 20, false);
            near(input.captureScale, moved.get(0).captureScale, "lasso translation retains geometry");
            List<NotebookPage.Stroke> pasted = new NotebookLasso.Clipboard(NotebookLasso.all(page)).place(page, 100, 100);
            near(input.captureScale, pasted.get(0).captureScale, "clipboard duplication retains geometry");
            near(input.captureWidth, pasted.get(0).captureWidth, "clipboard duplication retains exact native brush width");
            check(!input.id.equals(pasted.get(0).id), "copied geometry has independent stroke identity");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); NotebookStore.writeSnapshot(bytes, page);
            Files.write(new File(copy, "page.nbk").toPath(), bytes.toByteArray());
            near(input.captureScale, new NotebookStore(copy).load().strokes.get(0).captureScale,
                "transport snapshot preserves scale");
            NotebookBook book = new NotebookBook(directory); book.load();
            book.managePages(NotebookBook.PageAction.DUPLICATE, 1, 0);
            NotebookPage duplicated = book.previewPage(2);
            near(input.captureScale, duplicated.strokes.get(0).captureScale, "page duplication retains native input scale");
            near(input.captureWidth, duplicated.strokes.get(0).captureWidth, "page duplication retains exact native width");
        } finally { NotebookBackup.deleteTree(directory); NotebookBackup.deleteTree(copy); }
    }

    private static void versionFourReadsStayNonMutating() throws Exception {
        File directory = Files.createTempDirectory("stroke-scale-v4-").toFile();
        try {
            ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
            DataOutputStream payload = new DataOutputStream(payloadBytes);
            UUID page = new UUID(1, 2), stroke = new UUID(3, 4);
            payload.writeByte(1); id(payload, page); payload.writeInt(1200); payload.writeInt(1800);
            payload.writeByte(0); payload.writeInt(0); payload.writeByte(0); payload.writeInt(1);
            id(payload, stroke); payload.writeInt(2); payload.writeFloat(4); payload.writeInt(0xff000000);
            payload.writeInt(1); payload.writeFloat(30); payload.writeFloat(40); payload.writeLong(123); payload.writeFloat(.1f);
            byte[] data = payloadBytes.toByteArray(); CRC32 crc = new CRC32(); crc.update(data);
            ByteArrayOutputStream fileBytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(fileBytes);
            out.writeInt(0x4e424b31); out.writeInt(4); out.writeInt(data.length); out.writeInt(~data.length);
            out.write(data); out.writeInt((int) crc.getValue());
            File file = new File(directory, "page.nbk"); byte[] original = fileBytes.toByteArray();
            Files.write(file.toPath(), original);
            NotebookStore store = new NotebookStore(directory); NotebookPage loaded = store.load();
            near(1, loaded.strokes.get(0).captureScale, "legacy unknown native scale retains historical source-space rendering");
            check(stroke.equals(loaded.strokes.get(0).id) && loaded.strokes.get(0).points.get(0).time == 123,
                "legacy stroke identity and timestamps preserved");
            check(Arrays.equals(original, Files.readAllBytes(file.toPath())), "opening existing notes never rewrites their data");
            store.appendStroke(stroke(4, 1.5f, 60, 80, .3f));
            NotebookPage migrated = new NotebookStore(directory).load();
            near(1, migrated.strokes.get(0).captureScale, "migration retains legacy geometry");
            near(1.5, migrated.strokes.get(1).captureScale, "migration saves newly captured scale");
            check(Arrays.equals(original, Files.readAllBytes(new File(directory, "page.nbk.bak").toPath())),
                "first edit preserves the complete old file as backup");
        } finally { NotebookBackup.deleteTree(directory); }
    }

    private static void invalidGeometryCannotEnterStorage() throws Exception {
        for (float scale : new float[]{0, -1, Float.NaN, Float.POSITIVE_INFINITY, 1025}) {
            boolean rejected = false;
            try { stroke(1, scale, 30, 40, 1); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid native scale rejected");
        }
        NotebookPage page = NotebookPage.empty().withSize(100, 200).withStroke(stroke(6, 1, 30, 40, 1));
        NotebookPage display = new NotebookViewport(1, 100, 200, 400, 500, 24).compose(Collections.singletonMap(1, page), 0);
        boolean rejected = false;
        try { NotebookStore.writeSnapshot(new ByteArrayOutputStream(), display); } catch (IOException expected) { rejected = true; }
        check(rejected, "transient viewport geometry cannot replace durable page samples");
    }

    private static void externalGeometryChangesFailClosed() throws Exception {
        for (int field = 0; field < 2; field++) for (boolean retry : new boolean[]{false, true}) {
            File directory = Files.createTempDirectory("stroke-scale-conflict-").toFile();
            try {
                NotebookDocument open = new NotebookDocument(directory); open.load(); open.initializeSize(1200, 1800);
                NotebookPage.Stroke original = stroke(4, 1.125f, 40, 50, .25f); open.append(original);
                NotebookStore external = new NotebookStore(directory); external.load();
                NotebookPage.Stroke changed = new NotebookPage.Stroke(original.id, original.penType, original.width,
                    original.color, original.points, field == 0 ? Math.nextUp(original.captureScale) : original.captureScale,
                    field == 1 ? Math.nextUp(original.captureWidth) : original.captureWidth);
                external.replaceStrokes(Collections.singletonList(changed));
                byte[] before = Files.readAllBytes(new File(directory, "page.nbk").toPath());
                boolean rejected = false;
                try { if (retry) open.retry(); else open.validateDisk(); } catch (IOException expected) { rejected = true; }
                check(rejected, "even a one-bit external capture geometry change is refused by retry/disk validation");
                check(Arrays.equals(before, Files.readAllBytes(new File(directory, "page.nbk").toPath())),
                    "conflicting geometry is preserved on disk without overwriting it");
                check(open.current().strokes.get(0) == original && open.canUndo(),
                    "conflict retains the exact open ink and edit history");
            } finally { NotebookBackup.deleteTree(directory); }
        }
    }

    private static float firmwareWidth(float width, float pressure) { return width < 4 ? width : (int) (width * (1 + pressure) / 2); }
    private static NotebookPage.Stroke stroke(float width, float scale, float x, float y, float pressure) {
        return new NotebookPage.Stroke(2, width, 0xff000000,
            Collections.singletonList(new NotebookPage.Point(x, y, 123, pressure)), scale);
    }
    private static void id(DataOutputStream out, UUID id) throws IOException { out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits()); }
    private static void near(double expected, double actual, String message) { check(Math.abs(expected - actual) < .001, message + ": " + expected + " / " + actual); }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
