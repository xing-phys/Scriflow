package org.eink.inkprobe.notebook;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Feeds page-space samples to the installed native pressure brush and scales its
 * finished geometry on the framebuffer, rather than changing the brush width.
 *
 * <p>The cached MatePad firmware provides the relevant contract: HandPen's
 * setSurfaceBoard(ISurface) binds the same native surface used by AHW, and
 * EffectPen(true) draws each input sample to its active and backing buffers.
 * FBHandSurfaceClient.applyMC applies global exclusion clipping before its
 * drawing matrix. Its automatic pen posts do not transform their dirty bounds,
 * so this bridge also posts a conservative physical rectangle explicitly.
 * These are findings from the installed firmware source interfaces and native
 * disassembly, not a claim of physical-device acceptance of this bridge.</p>
 *
 * <p>AHW remains responsible for sample capture and completion bookkeeping. The
 * caller gives its capture pen a transparent color while this visible pen is
 * active. Calls belong to the capture callback thread; page/view changes must
 * wait for pen lift. No outline, bitmap, or Android view is rebuilt per MOVE.</p>
 */
final class NotebookPagePen {
    private static final int DOWN = 0, UP = 1, MOVE = 2, CANCEL = 3;
    // EffectPen.draw's ordinary black-ink framebuffer post, not a View policy.
    private static final int POST_BLACK_INK = 0x00200001;

    private final Object surface, pen;
    private final int surfaceWidth, surfaceHeight;
    private final Method save, scale, restore, post, setWidth, input, track, bounds;
    private final Field boundsLeft, boundsTop, boundsRight, boundsBottom;
    private float logicalWidth, scaleX, scaleY, maxPressure;
    private double left, top, right, bottom;
    private boolean configured, inStroke;

    NotebookPagePen(Object surface, int penType, int surfaceWidth, int surfaceHeight) throws Exception {
        if (surface == null || penType < 0 || penType > 255 || surfaceWidth <= 0 || surfaceHeight <= 0)
            throw new IllegalArgumentException("Invalid page pen surface");
        this.surface = surface;
        this.surfaceWidth = surfaceWidth;
        this.surfaceHeight = surfaceHeight;
        Class<?> surfaceApi = surface.getClass();
        save = surfaceApi.getMethod("save");
        scale = surfaceApi.getMethod("scale", float.class, float.class);
        restore = surfaceApi.getMethod("restore");
        post = surfaceApi.getMethod("postFrameBuffer", int.class, int.class, int.class, int.class, int.class);
        Class<?> penApi = Class.forName("android.eink.view.HandPen");
        pen = penApi.getConstructor(int.class, boolean.class).newInstance(penType, true);
        invoke(penApi.getMethod("setSurfaceBoard", Class.forName("android.eink.view.ISurface")), pen, surface);
        invoke(penApi.getMethod("setStrokeColor", int.class), pen, 0xff000000);
        setWidth = penApi.getMethod("setStrokeWidth", float.class);
        input = penApi.getMethod("onTouchEvent", int.class, float.class, float.class, long.class, float.class);
        track = penApi.getMethod("getTrack");
        bounds = Class.forName("android.eink.view.IPenTrack").getMethod("getBounds");
        Class<?> rectangle = bounds.getReturnType();
        boundsLeft = rectangle.getField("left");
        boundsTop = rectangle.getField("top");
        boundsRight = rectangle.getField("right");
        boundsBottom = rectangle.getField("bottom");
    }

    /** Freeze the width and transform for the following DOWN-to-UP input stream. */
    void begin(float logicalWidth, float pixelsPerUnitX, float pixelsPerUnitY) throws Exception {
        if (inStroke) throw new IllegalStateException("Finish the page pen stroke before changing its style");
        if (!positive(logicalWidth) || logicalWidth > 1024 || !positive(pixelsPerUnitX)
                || !positive(pixelsPerUnitY) || !positive(1f / pixelsPerUnitX)
                || !positive(1f / pixelsPerUnitY))
            throw new IllegalArgumentException("Invalid page pen style");
        invoke(setWidth, pen, logicalWidth);
        this.logicalWidth = logicalWidth;
        scaleX = pixelsPerUnitX;
        scaleY = pixelsPerUnitY;
        configured = true;
        clearBounds();
    }

    /** Native surface pixels in; unchanged pressure and time reach the page-space brush. */
    void point(int action, float nativeX, float nativeY, long time, float pressure) throws Exception {
        if (!configured || (!inStroke && action != DOWN))
            throw new IllegalStateException("Begin the page pen stroke before adding points");
        if (action != DOWN && action != MOVE && action != UP && action != CANCEL)
            throw new IllegalArgumentException("Invalid page pen action");
        if (!finite(nativeX) || !finite(nativeY) || !finite(pressure) || pressure < 0 || time < 0
                || !finite(nativeX / scaleX) || !finite(nativeY / scaleY))
            throw new IllegalArgumentException("Invalid page pen point");
        if (action == DOWN) {
            if (inStroke) throw new IllegalStateException("The page pen stroke is already in progress");
            clearBounds();
            inStroke = true;
        }
        maxPressure = Math.max(maxPressure, pressure);
        include(nativeX, nativeY, nativeX, nativeY);
        invoke(save, surface);
        Exception failure = null;
        try {
            invoke(scale, surface, scaleX, scaleY);
            invoke(input, pen, action, nativeX / scaleX, nativeY / scaleY, time, pressure);
        } catch (Exception error) {
            failure = error;
            throw error;
        } finally {
            try { invoke(restore, surface); }
            catch (Exception error) {
                if (failure != null) failure.addSuppressed(error);
                else throw error;
            }
        }
        // getTrack belongs to a completed stroke; before UP it can be null or
        // refer to an earlier stroke. Full input bounds cover the live curve.
        if (action == UP || action == CANCEL) {
            includeCompletedBounds();
            inStroke = false;
        }
        postDirty();
    }

    private void includeCompletedBounds() throws Exception {
        Object completed = invoke(track, pen);
        if (completed == null) return;
        Object rectangle = invoke(bounds, completed);
        if (rectangle == null) return;
        float l = boundsLeft.getFloat(rectangle), t = boundsTop.getFloat(rectangle);
        float r = boundsRight.getFloat(rectangle), b = boundsBottom.getFloat(rectangle);
        if (finite(l) && finite(t) && finite(r) && finite(b) && r >= l && b >= t)
            include((double) l * scaleX, (double) t * scaleY, (double) r * scaleX, (double) b * scaleY);
    }

    private void postDirty() throws Exception {
        // The whole active input hull covers native Bezier control points and
        // caps. Also cover retained samples above nominal unit pressure rather
        // than assuming that every firmware/input source reports at most one.
        double padding = Math.max(2, 1d + maxPressure);
        double padX = Math.max(2, (double) logicalWidth * scaleX * padding);
        double padY = Math.max(2, (double) logicalWidth * scaleY * padding);
        int l = bounded(Math.floor(left - padX), surfaceWidth);
        int t = bounded(Math.floor(top - padY), surfaceHeight);
        int r = bounded(Math.ceil(right + padX), surfaceWidth);
        int b = bounded(Math.ceil(bottom + padY), surfaceHeight);
        if (r > l && b > t) invoke(post, surface, l, t, r, b, POST_BLACK_INK);
    }

    private void clearBounds() {
        left = top = Double.POSITIVE_INFINITY;
        right = bottom = Double.NEGATIVE_INFINITY;
        maxPressure = 0;
    }

    private void include(double l, double t, double r, double b) {
        left = Math.min(left, l); top = Math.min(top, t);
        right = Math.max(right, r); bottom = Math.max(bottom, b);
    }

    private static int bounded(double value, int limit) { return (int) Math.max(0, Math.min(limit, value)); }
    private static boolean finite(float value) { return !Float.isNaN(value) && !Float.isInfinite(value); }
    private static boolean positive(float value) { return finite(value) && value > 0; }

    private static Object invoke(Method method, Object target, Object... arguments) throws Exception {
        try { return method.invoke(target, arguments); }
        catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Native page pen call failed", cause);
        }
    }
}
