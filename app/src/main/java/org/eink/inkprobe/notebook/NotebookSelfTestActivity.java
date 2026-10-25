package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Debug-only, disposable firmware round-trip check. Never opens the user's notebook store. */
public final class NotebookSelfTestActivity extends Activity implements NotebookInk.Listener {
    private static final String TAG = "NotebookSelfTest";
    private static final float TOLERANCE = 0.02f;
    private final long startedAtMillis = System.currentTimeMillis();
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final List<NotebookPage.Stroke> emittedStrokes = new ArrayList<>();
    private NotebookInk ink;
    private NotebookPage expected;
    private NotebookPage.Template template;
    private TextView status;
    private boolean completed, verificationQueued, waitingForEditResume;
    private JSONObject pendingResult;
    private Surface editSurface;
    private Object editPaper;
    private boolean nativeEraserTest;
    private int eraserGestures;
    private float testEraserWidth;
    private JSONObject eraserResult;
    private long timingStart, timingEnd, timingSamples, timingTotal, timingEarlyMax, timingLateMax,
        timingLast, timingMax;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
            finish();
            return;
        }
        template = "lined".equalsIgnoreCase(getIntent().getStringExtra("template"))
            ? NotebookPage.Template.LINED : NotebookPage.Template.BLANK;
        nativeEraserTest = getIntent().getBooleanExtra("nativeEraserTest", false);
        testEraserWidth = NotebookWidthRange.ERASER.restore(getIntent().getFloatExtra("eraserWidth", 36));
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        status = new TextView(this);
        status.setText("Disposable notebook self-test · " + template.name().toLowerCase());
        status.setTextColor(Color.BLACK);
        status.setTextSize(14);
        status.setSingleLine(true);
        status.setPadding(16, 12, 16, 12);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);
        try {
            ink = new NotebookInk(this, NotebookPage.empty().withTemplate(template), this);
            if (nativeEraserTest) {
                ink.setTool(NotebookInk.Tool.ERASER);
                ink.setEraserWidth(testEraserWidth);
                ink.setSampleObserver(this::recordNativeSample);
            }
            root.addView(ink.view, new LinearLayout.LayoutParams(-1, 0, 1));
        } catch (Exception error) {
            fail("construct", error);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (ink != null) ink.start();
    }

    @Override protected void onPause() {
        if (ink != null) ink.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (ink != null) ink.release();
        writer.shutdown();
        super.onDestroy();
    }

    @Override public void onGeometryReady(int width, int height) {
        if (completed || expected != null) return;
        try {
            NotebookPage.Stroke dot = new NotebookPage.Stroke(2, 4f, Color.BLACK, Arrays.asList(
                new NotebookPage.Point(width * 0.18f, height * 0.20f, 1000L, 0.45f),
                new NotebookPage.Point(width * 0.18f, height * 0.20f, 1008L, 0.45f)));
            NotebookPage.Stroke curve = new NotebookPage.Stroke(2, 7f, 0xff555555, Arrays.asList(
                new NotebookPage.Point(width * 0.28f, height * 0.42f, 2000L, 0.20f),
                new NotebookPage.Point(width * 0.35f, height * 0.32f, 2012L, 0.35f),
                new NotebookPage.Point(width * 0.43f, height * 0.27f, 2024L, 0.55f),
                new NotebookPage.Point(width * 0.52f, height * 0.31f, 2036L, 0.80f),
                new NotebookPage.Point(width * 0.61f, height * 0.43f, 2048L, 0.65f),
                new NotebookPage.Point(width * 0.70f, height * 0.49f, 2060L, 0.40f),
                new NotebookPage.Point(width * 0.78f, height * 0.42f, 2072L, 0.20f)));
            expected = NotebookPage.empty().withTemplate(template).withSize(width, height)
                .withStroke(dot).withStroke(curve);
            ink.setPage(expected);
        } catch (Exception error) {
            fail("seed", error);
        }
    }

    @Override public void onReady() {
        if (completed || verificationQueued || expected == null || ink == null) return;
        if (nativeEraserTest) { verifyNativeEraserReady(); return; }
        // Read after the adapter's completed restore callback unwinds.
        verificationQueued = true;
        ink.view.post(() -> {
            verificationQueued = false;
            if (waitingForEditResume) verifyEditResume(); else verifyRestore();
        });
    }

    @Override public void onStrokeComplete(NotebookPage.Stroke stroke) {
        // Physical input on this disposable screen never reaches the user's notebook store.
        emittedStrokes.add(stroke);
    }

    @Override public void onPenStateChanged(boolean down) { }

    @Override public void onError(String message) {
        fail("adapter", new IllegalStateException(message));
    }

    private void verifyNativeEraserReady() {
        try {
            require(ink.isReady(), "Native eraser not ready");
            Object surface = call(ink.view, "getFBSurface");
            require(Boolean.TRUE.equals(call(surface, "isAutoHandWrite")), "Eraser AHW is disabled");
            float fittedWidth = testEraserWidth * (float) Math.sqrt(
                (double) ink.view.getWidth() / expected.width * ink.view.getHeight() / expected.height);
            near((Float) call(surface, "getAhwPenWidth"), fittedWidth, "Native eraser width differs");
            require((Integer) call(surface, "getAhwPenColor") == NotebookEraserCommit.COLOR,
                "Native eraser color differs");
            require(emittedStrokes.isEmpty(), "Grey preview emitted a saved pen stroke");
            verifyNativePage(expected);
            if (editSurface == null) {
                editSurface = ink.view.getHolder().getSurface();
                editPaper = call(call(ink.view, "getSurfacePicture"), "getBitmap");
            }
            verifyEditIdentities();
            JSONObject result = eraserResult == null ? baseResult() : eraserResult;
            result.put("passed", true);
            result.put("nativeEraserTest", true);
            result.put("autoHandWrite", true);
            result.put("logicalWidth", testEraserWidth);
            result.put("nativeWidth", fittedWidth);
            result.put("nativeColor", Integer.toHexString(NotebookEraserCommit.COLOR));
            result.put("gestures", eraserGestures);
            result.put("canonicalTracks", expected.strokes.size());
            result.put("previewEmittedAsInk", false);
            result.put("surfaceAndPaperIdentityRetained", true);
            if (timingSamples > 0) result.put("inputTiming", nativeTiming());
            writeResult(result, "Native eraser · width " + (int) testEraserWidth
                + " · gestures " + eraserGestures + " · draw across the curve or on blank paper");
        } catch (Exception error) { fail("native-eraser-ready", error); }
    }

    private void recordNativeSample(int action, long time) {
        if (action == MotionEvent.ACTION_DOWN) {
            timingStart = time;
            timingSamples = timingTotal = timingEarlyMax = timingLateMax = timingMax = 0;
        }
        long delay = Math.max(0, SystemClock.uptimeMillis() - time);
        timingEnd = time;
        timingSamples++;
        timingTotal += delay;
        timingLast = delay;
        timingMax = Math.max(timingMax, delay);
        if (time - timingStart < 3000) timingEarlyMax = Math.max(timingEarlyMax, delay);
        else timingLateMax = Math.max(timingLateMax, delay);
    }

    private JSONObject nativeTiming() throws Exception {
        JSONObject result = new JSONObject();
        result.put("samples", timingSamples);
        result.put("durationMs", timingEnd - timingStart);
        result.put("meanDeliveryDelayMs", (double) timingTotal / timingSamples);
        result.put("maxDeliveryDelayMs", timingMax);
        result.put("firstThreeSecondsMaxMs", timingEarlyMax);
        result.put("laterMaxMs", timingLateMax);
        result.put("upDeliveryDelayMs", timingLast);
        result.put("opticalLatencyMeasured", false);
        return result;
    }

    @Override public void onEraseGesture(List<NotebookPage.Point> path, float radius) {
        if (!nativeEraserTest || completed) return;
        try {
            require(ink.isReady() && !ink.isPenDown(), "Eraser callback arrived before readiness");
            require(!Boolean.TRUE.equals(call(ink.view, "isReceivingTrack")), "Native track still receiving");
            List<NotebookPage.Stroke> tracks = ink.snapshotNativeStrokes();
            require(tracks.size() == expected.strokes.size() + 1, "Temporary native track missing");
            NotebookPage.Stroke preview = tracks.get(tracks.size() - 1);
            near(preview.width, testEraserWidth, "Temporary native track width differs");
            require(preview.color == NotebookEraserCommit.COLOR, "Temporary native track is not grey");
            near(radius * 2, testEraserWidth, "Erase footprint differs from native width");
            NotebookEdits edits = new NotebookEdits(expected);
            boolean hit = edits.erase(path, radius);
            expected = edits.current();
            ink.pauseForEdit();
            require(ink.applyEditPage(expected), "Native eraser required widget replacement");
            verifyEditIdentities();
            verifyNativePage(expected);
            require(emittedStrokes.isEmpty(), "Temporary grey became document ink");
            eraserGestures++;
            eraserResult = baseResult();
            eraserResult.put("lastGestureHit", hit);
            eraserResult.put("lastGesturePoints", path.size());
            eraserResult.put("nativeAccumulatorSettledBeforeEdit", true);
            eraserResult.put("temporaryTrackRemovedInPlace", true);
            ink.start();
        } catch (Exception error) { fail("native-eraser-gesture", error); }
    }

    private void verifyRestore() {
        if (completed || ink == null || expected == null) return;
        try {
            int points = verifyNativePage(expected);
            int nativeCount = ink.getNativeTrackCount();
            require(nativeCount == expected.strokes.size(), "Native track count differs from snapshot");
            JSONObject paper = verifyPaper();
            JSONObject result = baseResult();
            result.put("passed", true);
            result.put("nativeTrackCount", nativeCount);
            result.put("pointCount", points);
            result.put("coordinatesStylePressureTimeMatch", true);
            result.put("paper", paper);
            result.put("physicalStylusLatencyTested", false);
            if (getIntent().getBooleanExtra("outlines", false)) {
                // Exercise the same native APIs from an ordinary APK UID, not just app_process.
                Bitmap bitmap = Bitmap.createBitmap(expected.width, expected.height, Bitmap.Config.ARGB_8888);
                try {
                    android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap);
                    android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    int vertices = 0;
                    for (NotebookPage.Stroke stroke : expected.strokes) {
                        NotebookStrokeRenderer.draw(canvas, stroke, paint);
                        vertices += NotebookStrokeRenderer.outline(stroke).vertexCount();
                    }
                    require(vertices > 0, "Native pressure outlines empty");
                    result.put("nativeOutlinesFromApkUid", true);
                    result.put("outlineVertices", vertices);
                } finally { bitmap.recycle(); }
            }
            if (getIntent().getBooleanExtra("inPlaceEdits", false)) {
                result.put("inPlaceEdits", verifyInPlaceEdits());
                pendingResult = result;
                waitingForEditResume = true;
                ink.start();
                return;
            }
            finishVerification(result);
        } catch (Exception error) {
            fail("verify", error);
        }
    }

    private int verifyNativePage(NotebookPage page) throws Exception {
        List<NotebookPage.Stroke> actual = ink.snapshotNativeStrokes();
        require(actual.size() == page.strokes.size(), "Native stroke count changed");
        int points = 0;
        for (int index = 0; index < actual.size(); index++) {
            NotebookPage.Stroke restored = actual.get(index);
            NotebookPage.Stroke original = page.strokes.get(index);
            require(restored.penType == original.penType, "Pen type changed");
            require(restored.color == original.color, "Pen color changed");
            near(restored.width, original.width, "Pen width changed");
            require(restored.points.size() == original.points.size(), "Point count changed");
            for (int point = 0; point < restored.points.size(); point++) {
                NotebookPage.Point after = restored.points.get(point);
                NotebookPage.Point before = original.points.get(point);
                near(after.x, before.x, "Point x changed");
                near(after.y, before.y, "Point y changed");
                near(after.pressure, before.pressure, "Point pressure changed");
                require(after.time == before.time, "Point event time changed");
                points++;
            }
        }
        require(ink.getNativeTrackCount() == page.strokes.size(), "Native track count differs from snapshot");
        return points;
    }

    /** Runs only against the disposable synthetic document and never opens NotebookStore. */
    private JSONObject verifyInPlaceEdits() throws Exception {
        require(ink.isReady(), "Initial editor is not ready");
        require(emittedStrokes.isEmpty(), "Unexpected physical input before the edit check");
        editSurface = ink.view.getHolder().getSurface();
        editPaper = call(call(ink.view, "getSurfacePicture"), "getBitmap");
        NotebookEdits edits = new NotebookEdits(expected);
        ink.pauseForEdit();
        require(!ink.isReady(), "Editor accepted input while paused for edits");
        require(edits.erase(Arrays.asList(expected.strokes.get(0).points.get(0)),
            NotebookInk.ERASER_RADIUS), "Synthetic erase did not hit the dot");
        require(edits.current().strokes.size() == 1, "Synthetic erase removed the wrong strokes");
        applyAndVerifyEdit(edits.current());
        require(edits.undo(), "Synthetic erase could not be undone");
        applyAndVerifyEdit(edits.current());
        require(edits.redo(), "Synthetic erase could not be redone");
        applyAndVerifyEdit(edits.current());
        require(edits.undo(), "Second undo failed");
        applyAndVerifyEdit(edits.current());

        List<?> beforeNoHit = new ArrayList<>((List<?>) call(ink.view, "getTrackList"));
        require(!edits.erase(Arrays.asList(new NotebookPage.Point(
            expected.width * 0.96f, expected.height * 0.96f, 9000L, 0.5f)),
            NotebookInk.ERASER_RADIUS), "Synthetic blank-space eraser hit a stroke");
        require(edits.canRedo(), "No-hit erase cleared redo");
        applyAndVerifyEdit(edits.current());
        List<?> afterNoHit = (List<?>) call(ink.view, "getTrackList");
        require(beforeNoHit.size() == afterNoHit.size(), "No-hit erase changed native track count");
        for (int index = 0; index < beforeNoHit.size(); index++) {
            require(beforeNoHit.get(index) == afterNoHit.get(index), "No-hit erase replaced a native track");
        }

        require(edits.redo(), "Redo after no-hit erase failed");
        applyAndVerifyEdit(edits.current());
        require(edits.undo(), "Final undo failed");
        applyAndVerifyEdit(edits.current());

        // A second disposable page has its own model and the opposite paper. Switching
        // must repaint the already bound bitmap without replacing the visible Surface.
        NotebookPage.Template otherTemplate = template == NotebookPage.Template.BLANK
            ? NotebookPage.Template.LINED : NotebookPage.Template.BLANK;
        NotebookPage otherPage = NotebookPage.empty().withSize(expected.width, expected.height)
            .withTemplate(otherTemplate);
        applyAndVerifyEdit(otherPage);
        NotebookPage.Stroke otherStroke = new NotebookPage.Stroke(2, 5f, Color.BLACK, Arrays.asList(
            new NotebookPage.Point(expected.width * 0.12f, expected.height * 0.72f, 10000L, 0.30f),
            new NotebookPage.Point(expected.width * 0.20f, expected.height * 0.78f, 10012L, 0.65f),
            new NotebookPage.Point(expected.width * 0.31f, expected.height * 0.73f, 10024L, 0.25f)));
        otherPage = otherPage.withStroke(otherStroke);
        applyAndVerifyEdit(otherPage);
        applyAndVerifyEdit(expected);
        applyAndVerifyEdit(otherPage);
        applyAndVerifyEdit(expected);
        verifyNativePage(expected);
        require(emittedStrokes.isEmpty(), "Synthetic edits emitted a new pen stroke");
        JSONObject result = new JSONObject();
        result.put("syntheticEraseUndoRedoNoHitRoundTrip", true);
        result.put("syntheticEmptyAndWrittenPageSwitchRoundTrip", true);
        result.put("oppositeTemplatesRepaintedInPlace", true);
        result.put("surfaceAndPaperIdentityRetained", true);
        result.put("noHitNativeTrackIdentityRetained", true);
        result.put("coordinatesStylePressureTimeMatch", true);
        result.put("originalDocumentRestored", true);
        result.put("physicalStylusTest", false);
        result.put("displayBlinkMeasured", false);
        return result;
    }

    private void applyAndVerifyEdit(NotebookPage updated) throws Exception {
        require(ink.applyEditPage(updated), "Clean synthetic edit required a replacement widget");
        require(!completed, "Adapter failed during in-place editing");
        require(!ink.isReady(), "Editor resumed before explicit start");
        verifyEditIdentities();
        verifyNativePage(updated);
        verifyPaper(updated.template);
    }

    private void verifyEditIdentities() throws Exception {
        require(ink.view.getHolder().getSurface() == editSurface, "In-place edit replaced the Surface");
        require(call(call(ink.view, "getSurfacePicture"), "getBitmap") == editPaper,
            "In-place edit replaced the paper bitmap");
    }

    private void verifyEditResume() {
        if (completed || ink == null || pendingResult == null) return;
        try {
            require(ink.isReady(), "Editor did not resume after in-place edits");
            verifyEditIdentities();
            verifyNativePage(expected);
            pendingResult.getJSONObject("inPlaceEdits").put("inputReadyAfterResume", true);
            waitingForEditResume = false;
            JSONObject result = pendingResult;
            pendingResult = null;
            finishVerification(result);
        } catch (Exception error) { fail("edit-resume", error); }
    }

    private void finishVerification(JSONObject result) throws Exception {
        if (getIntent().getBooleanExtra("pauseBoundary", false)) {
            result.put("pauseBoundary", verifyPauseBoundary());
            require(!completed, "Adapter failed during the simulated callback check");
        }
        completed = true;
        writeResult(result, "PASS · " + template.name().toLowerCase() + " · 2 native tracks / "
            + result.getInt("pointCount") + " points");
    }

    private JSONObject verifyPauseBoundary() throws Exception {
        require(emittedStrokes.isEmpty(), "Unexpected input before the simulated callback check");
        NotebookPage.Stroke lifted = new NotebookPage.Stroke(2, 4f, Color.BLACK, Arrays.asList(
            new NotebookPage.Point(expected.width * 0.22f, expected.height * 0.62f, 3000L, 0.25f),
            new NotebookPage.Point(expected.width * 0.28f, expected.height * 0.66f, 3012L, 0.50f),
            new NotebookPage.Point(expected.width * 0.34f, expected.height * 0.64f, 3024L, 0.10f)));
        // Invoke our own adapter's callback consumer, not a vendor private API. Deliberately
        // omit nativeCommit: this reproduces pause after UP but before the separate track event.
        Method point = NotebookInk.class.getDeclaredMethod("point", int.class, float.class,
            float.class, long.class, float.class);
        point.setAccessible(true);
        injectPoint(point, MotionEvent.ACTION_DOWN, lifted.points.get(0));
        injectPoint(point, MotionEvent.ACTION_MOVE, lifted.points.get(1));
        injectPoint(point, MotionEvent.ACTION_UP, lifted.points.get(2));
        require(emittedStrokes.isEmpty(), "The delayed native track event was not isolated");
        ink.stop();
        require(emittedStrokes.size() == 1, "Stopping lost the completed UP stroke");
        NotebookPage.Stroke actual = emittedStrokes.get(0);
        require(actual.penType == lifted.penType && actual.color == lifted.color,
            "Pause fallback changed the pen type or color");
        near(actual.width, lifted.width, "Pause fallback changed the pen width");
        require(actual.points.size() == lifted.points.size(), "Pause fallback changed the point count");
        for (int i = 0; i < lifted.points.size(); i++) {
            NotebookPage.Point before = lifted.points.get(i), after = actual.points.get(i);
            near(after.x, before.x, "Pause fallback changed point x");
            near(after.y, before.y, "Pause fallback changed point y");
            near(after.pressure, before.pressure, "Pause fallback changed pressure");
            require(after.time == before.time, "Pause fallback changed event time");
        }
        ink.stop();
        require(emittedStrokes.size() == 1, "Repeated stop duplicated the UP stroke");

        // This test bypasses the observer's enabled filter so the stopped adapter can consume
        // another isolated callback sequence without restarting or redrawing the firmware view.
        injectPoint(point, MotionEvent.ACTION_DOWN,
            new NotebookPage.Point(120f, 150f, 4000L, 0.30f));
        injectPoint(point, MotionEvent.ACTION_MOVE,
            new NotebookPage.Point(130f, 160f, 4012L, 0.40f));
        ink.stop();
        require(emittedStrokes.size() == 1, "Stopping saved an unfinished stroke");
        injectPoint(point, MotionEvent.ACTION_DOWN,
            new NotebookPage.Point(160f, 180f, 5000L, 0.30f));
        injectPoint(point, MotionEvent.ACTION_CANCEL,
            new NotebookPage.Point(170f, 190f, 5012L, 0.00f));
        ink.stop();
        require(emittedStrokes.size() == 1, "Stopping saved a cancelled stroke");
        require(!completed, "Adapter failed during the simulated callback check");

        JSONObject result = new JSONObject();
        result.put("simulatedCallbackBoundaryTest", true);
        result.put("physicalStylusTest", false);
        result.put("completedUpEmittedExactlyOnce", true);
        result.put("coordinatesStylePressureTimeMatch", true);
        result.put("partialStrokeDiscarded", true);
        result.put("cancelledStrokeDiscarded", true);
        result.put("emittedStrokeCount", emittedStrokes.size());
        result.put("emittedPointCount", actual.points.size());
        return result;
    }

    private void injectPoint(Method method, int action, NotebookPage.Point point) throws Exception {
        try {
            method.invoke(ink, action, point.x, point.y, point.time, point.pressure);
        } catch (InvocationTargetException error) {
            throw new Exception("Simulated point callback: " + error.getCause(), error.getCause());
        }
    }

    private JSONObject verifyPaper() throws Exception {
        return verifyPaper(template);
    }

    private JSONObject verifyPaper(NotebookPage.Template expectedTemplate) throws Exception {
        Object picture = call(ink.view, "getSurfacePicture");
        Bitmap bitmap = (Bitmap) call(picture, "getBitmap");
        JSONObject result = new JSONObject();
        if (bitmap == null) {
            require(expectedTemplate == NotebookPage.Template.BLANK, "Lined paper has no bitmap");
            require(((Integer) call(picture, "getColor")) == Color.WHITE, "Blank paper is not white");
            result.put("representation", "solid-white");
            return result;
        }
        require(!bitmap.isRecycled(), "Paper bitmap recycled");
        require(bitmap.getWidth() == ink.view.getWidth() && bitmap.getHeight() == ink.view.getHeight(),
            "Paper bitmap differs from measured surface size");
        int nonWhite = 0;
        for (int y = 0; y < bitmap.getHeight(); y++) {
            int pixel = bitmap.getPixel(bitmap.getWidth() / 2, y);
            require(Color.alpha(pixel) == 255, "Paper is not opaque");
            if (pixel != Color.WHITE) nonWhite++;
        }
        if (expectedTemplate == NotebookPage.Template.BLANK) require(nonWhite == 0, "Blank paper contains ruling");
        else require(nonWhite > 0 && nonWhite < bitmap.getHeight() / 3, "Lined ruling not found on white paper");
        result.put("representation", "bitmap");
        result.put("width", bitmap.getWidth());
        result.put("height", bitmap.getHeight());
        result.put("nonWhiteCenterColumnPixels", nonWhite);
        return result;
    }

    private JSONObject baseResult() throws Exception {
        JSONObject result = new JSONObject();
        result.put("schema", 1);
        result.put("startedAtMillis", startedAtMillis);
        result.put("template", template == null ? "unknown" : template.name().toLowerCase());
        result.put("disposableSyntheticDocument", true);
        result.put("userNotebookFilesOpened", false);
        if (expected != null) {
            result.put("pageWidth", expected.width);
            result.put("pageHeight", expected.height);
        }
        return result;
    }

    private void fail(String stage, Exception error) {
        if (completed) return;
        completed = true;
        Log.e(TAG, stage, error);
        try {
            JSONObject result = baseResult();
            result.put("passed", false);
            result.put("stage", stage);
            result.put("error", error.toString());
            writeResult(result, "FAIL · " + stage + " · " + error.getMessage());
        } catch (Exception jsonError) {
            Log.e(TAG, "Cannot construct failure result", jsonError);
        }
    }

    private void writeResult(JSONObject result, String message) {
        final String json = result.toString();
        writer.execute(() -> {
            try {
                File directory = new File(getFilesDir(), "notebook-selftest");
                if (!directory.isDirectory() && !directory.mkdirs()) {
                    throw new IllegalStateException("Cannot create self-test directory");
                }
                try (FileOutputStream output = new FileOutputStream(new File(directory, "result.json"))) {
                    output.write(json.getBytes(StandardCharsets.UTF_8));
                    output.getFD().sync();
                }
                Log.i(TAG, json);
                runOnUiThread(() -> { if (!isFinishing()) status.setText(message); });
            } catch (Exception error) {
                Log.e(TAG, "Cannot write self-test result", error);
                runOnUiThread(() -> { if (!isFinishing()) status.setText("FAIL · result write · " + error.getMessage()); });
            }
        });
    }

    private static Object call(Object object, String method) throws Exception {
        try {
            return object.getClass().getMethod(method).invoke(object);
        } catch (InvocationTargetException error) {
            throw new Exception(method + ": " + error.getCause(), error.getCause());
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private static void near(float actual, float expected, String message) {
        require(NotebookPage.finite(actual) && Math.abs(actual - expected) <= TOLERANCE,
            message + " (actual=" + actual + ", expected=" + expected + ")");
    }
}
