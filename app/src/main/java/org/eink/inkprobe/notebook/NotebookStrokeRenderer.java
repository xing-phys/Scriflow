package org.eink.inkprobe.notebook;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Matrix;
import android.view.MotionEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

/** Replays saved samples through the firmware brush; never touches the live writing surface. */
public final class NotebookStrokeRenderer {
    // At source-page scale this is far below a pixel, including at normal PDF zoom levels.
    static final float OUTLINE_ERROR = 0.05f;
    private static final BooleanSupplier NEVER_CANCELLED = () -> false;
    private static final ThreadLocal<Map<Integer, Pen>> PENS =
        ThreadLocal.withInitial(HashMap::new);

    private NotebookStrokeRenderer() { }

    public static void draw(Canvas canvas, NotebookPage.Stroke stroke, Paint paint) {
        draw(canvas, stroke, paint, NEVER_CANCELLED);
    }

    static void draw(Canvas canvas, NotebookPage.Stroke stroke, Paint paint, BooleanSupplier cancelled) {
        Path outline = path(stroke, cancelled);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(stroke.color);
        canvas.drawPath(outline, paint);
    }

    public static NotebookStrokeOutline outline(NotebookPage.Stroke stroke) {
        return outline(stroke, NEVER_CANCELLED);
    }

    static NotebookStrokeOutline outline(NotebookPage.Stroke stroke, BooleanSupplier cancelled) {
        Path path = path(stroke, cancelled);
        Path.FillType fill = path.getFillType();
        if (fill != Path.FillType.WINDING && fill != Path.FillType.EVEN_ODD)
            throw new IllegalStateException("Unsupported native stroke fill");
        checkCancelled(cancelled);
        return NotebookStrokeOutline.take(path.approximate(OUTLINE_ERROR), fill == Path.FillType.EVEN_ODD);
    }

    static Path path(NotebookPage.Stroke stroke, BooleanSupplier cancelled) {
        checkCancelled(cancelled);
        try {
            Map<Integer, Pen> pens = PENS.get();
            Pen pen = pens.get(stroke.penType);
            if (pen == null) { pen = new Pen(stroke.penType); pens.put(stroke.penType, pen); }
            // Huawei's brush rounds pressure widths in native pixels and changes its
            // pressure rule below four pixels. Replaying after scaling the samples is
            // therefore different from scaling its finished outline. Always replay in
            // the original input coordinate space, then transform the finished vector.
            pen.width.invoke(pen.value, stroke.captureWidth);
            pen.color.invoke(pen.value, stroke.color);
            // Match HWTrackHelper.drawTrack's exact replay order, including its initial MOVE.
            pen.point(MotionEvent.ACTION_DOWN, stroke.points.get(0), stroke.captureScale);
            for (int i = 0; i < stroke.points.size() - 1; i++) {
                if ((i & 255) == 0) checkCancelled(cancelled);
                pen.point(MotionEvent.ACTION_MOVE, stroke.points.get(i), stroke.captureScale);
            }
            pen.point(MotionEvent.ACTION_UP, stroke.points.get(stroke.points.size() - 1), stroke.captureScale);
            Object track = pen.track.invoke(pen.value);
            if (track == null) throw new IllegalStateException("Native brush returned no stroke");
            Path result = (Path) pen.path.invoke(track);
            if (result == null || result.isEmpty()) throw new IllegalStateException("Native brush returned no outline");
            Path owned = new Path(result); // Never mutate a firmware-owned path.
            if (stroke.captureScale != 1f) {
                Matrix inverse = new Matrix();
                inverse.setScale(1f / stroke.captureScale, 1f / stroke.captureScale);
                owned.transform(inverse);
            }
            if (stroke.clip != null) {
                Path clip = new Path();
                clip.addRect(stroke.clip.left, stroke.clip.top, stroke.clip.right, stroke.clip.bottom, Path.Direction.CW);
                if (!owned.op(clip, Path.Op.INTERSECT))
                    throw new IllegalStateException("Could not clip handwriting to its page");
            }
            return owned;
        } catch (ReflectiveOperationException error) {
            Throwable cause = error instanceof InvocationTargetException
                ? ((InvocationTargetException) error).getCause() : error;
            throw new IllegalStateException("Could not render native handwriting: " + cause, cause);
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted())
            throw new CancellationException("Handwriting render cancelled");
    }

    private static final class Pen {
        final Object value;
        final Method width, color, input, track, path;
        // A zero-sized offscreen Canvas lets the firmware accumulate its complete native path
        // without rasterizing a second bitmap or binding anything to the e-ink display.
        final Canvas offscreen = new Canvas();

        Pen(int type) throws ReflectiveOperationException {
            Class<?> api = Class.forName("android.eink.view.HandPen");
            Constructor<?> create = api.getConstructor(int.class, boolean.class);
            value = create.newInstance(type, true);
            api.getMethod("setCanvasBoard", Canvas.class).invoke(value, offscreen);
            width = api.getMethod("setStrokeWidth", float.class);
            color = api.getMethod("setStrokeColor", int.class);
            input = api.getMethod("onTouchEvent", int.class, float.class, float.class, long.class, float.class);
            track = api.getMethod("getTrack");
            path = Class.forName("android.eink.view.IPenTrack").getMethod("getPath");
        }

        void point(int action, NotebookPage.Point point, float scale) throws ReflectiveOperationException {
            input.invoke(value, action, point.x * scale, point.y * scale, point.time, point.pressure);
        }
    }
}
