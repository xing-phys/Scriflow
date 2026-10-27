package org.eink.inkprobe.notebook;

import android.eink.view.HandPen;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import java.util.ArrayList;
import java.util.Arrays;

/** Executes the production renderer with Java2D drawing and a failing Path.op test double. */
public final class NotebookStrokeRendererTest {
    private static int assertions;
    private static final int BLACK = 0xff000000, WHITE = 0xffffffff;
    public static void main(String[] args) {
        complexClipsSurviveBooleanFailure();
        emptyClipIsNotAnError();
        failedDrawingRestoresCallerState();
        captureGeometryIsUnchanged();
        System.out.println("NotebookStrokeRenderer: " + assertions
            + " assertions passed (production renderer, Java2D contract fixtures; not native verification).");
    }

    private static void complexClipsSurviveBooleanFailure() {
        ArrayList<NotebookPage.Point> points = new ArrayList<>();
        for (int i = 0; i <= 400; i++) {
            double angle = i * Math.PI / 25;
            points.add(point((float) (50 + 65 * Math.sin(angle)), (float) (50 + 65 * Math.sin(angle * 2)), i));
            if (i % 20 == 0) points.add(points.get(points.size() - 1));
        }
        NotebookPage.Stroke raw = new NotebookPage.Stroke(2, 4.1f, BLACK, points, 1.125f, 4.6125f);
        NotebookPage.Stroke clipped = raw.inView(raw.width, raw.points, raw.captureScale, 20, 25, 75, 80);
        Canvas expected = new Canvas(100, 100), actual = new Canvas(100, 100);
        Paint paint = new Paint();
        int checkpoint = expected.save(); expected.clipRect(20, 25, 75, 80);
        NotebookStrokeRenderer.draw(expected, raw, paint); expected.restoreToCount(checkpoint);
        NotebookStrokeRenderer.draw(actual, clipped, paint);
        check(actual.getSaveCount() == 1, "Rendering restores the caller's save stack");
        int visible = 0;
        for (int y = 0; y < 100; y++) for (int x = 0; x < 100; x++) {
            check(expected.pixel(x, y) == actual.pixel(x, y), "Complex filled geometry matches canvas clipping");
            if (actual.pixel(x, y) == BLACK) {
                visible++; check(x >= 20 && x < 75 && y >= 25 && y < 80, "Ink stays inside its page clip");
            }
        }
        check(visible > 50, "Complex clipped handwriting remains visible");
        check(Path.booleanOperations == 0, "Drawing succeeds without depending on failed path boolean operations");
        drawSquare(actual, 1, 1, 10, 10);
        check(actual.pixel(5, 5) == BLACK, "Page clip does not leak into the caller's next draw");
    }

    private static void emptyClipIsNotAnError() {
        NotebookPage.Stroke raw = square();
        Canvas canvas = new Canvas(100, 100);
        NotebookStrokeRenderer.draw(canvas, raw.inView(raw.width, raw.points, raw.captureScale,
            40, 40, 40, 80), new Paint());
        check(canvas.pixel(50, 50) == WHITE && canvas.getSaveCount() == 1,
            "An empty page clip paints no ink and restores caller state");
        drawSquare(canvas, 1, 1, 10, 10);
        check(canvas.pixel(5, 5) == BLACK, "Drawing can continue after an empty clip");
    }

    private static void failedDrawingRestoresCallerState() {
        NotebookPage.Stroke raw = square(); Canvas canvas = new Canvas(100, 100);
        int caller = canvas.save(); canvas.translate(2, 3);
        int nested = canvas.getSaveCount(); canvas.failNextDraw = true;
        boolean failed = false;
        try { NotebookStrokeRenderer.draw(canvas,
            raw.inView(raw.width, raw.points, raw.captureScale, 20, 20, 80, 80), new Paint()); }
        catch (IllegalStateException expected) { failed = expected.getMessage().equals("Injected raster failure"); }
        check(failed, "Real drawing failures still propagate");
        check(canvas.getSaveCount() == nested, "A failed draw restores the caller's nested save count");
        drawSquare(canvas, 0, 0, 8, 8);
        check(canvas.pixel(4, 5) == BLACK && canvas.pixel(0, 0) == WHITE,
            "Failed drawing preserves the caller's transform and removes only its own clip");
        canvas.restoreToCount(caller);
    }

    private static void captureGeometryIsUnchanged() {
        NotebookPage.Stroke raw = square();
        NotebookPage.Stroke clipped = raw.inView(raw.width, raw.points, raw.captureScale, 30, 30, 70, 70);
        float[] full = NotebookStrokeRenderer.path(raw, () -> false).approximate(.05f);
        float[] fromClip = NotebookStrokeRenderer.path(clipped, () -> false).approximate(.05f);
        check(Arrays.equals(full, fromClip), "A viewport clip does not alter the reusable vector outline");
        check(HandPen.capturedWidth == raw.captureWidth, "Replay uses the exact captured native brush width");
        check(HandPen.samples.get(0)[1] == raw.points.get(0).x * raw.captureScale,
            "Replay retains capture-space coordinates before inverse path scaling");
        check(full[1] == raw.points.get(0).x && full[2] == raw.points.get(0).y,
            "Returned geometry remains in logical page coordinates");
        check(raw.points == clipped.points || raw.points.equals(clipped.points), "Viewport clipping retains all raw samples");
    }

    private static NotebookPage.Stroke square() {
        return new NotebookPage.Stroke(2, 4.1f, BLACK, Arrays.asList(point(10, 10, 1), point(90, 10, 2),
            point(90, 90, 3), point(10, 90, 4)), 1.25f, 5.125f);
    }
    private static NotebookPage.Point point(float x, float y, long time) {
        return new NotebookPage.Point(x, y, time, .5f);
    }
    private static void drawSquare(Canvas canvas, float left, float top, float right, float bottom) {
        Path path = new Path(); path.addRect(left, top, right, bottom, Path.Direction.CW);
        canvas.drawPath(path, new Paint());
    }
    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
}
