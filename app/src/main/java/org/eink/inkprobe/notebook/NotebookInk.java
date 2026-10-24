package org.eink.inkprobe.notebook;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** MatePad Paper editor bridge. The firmware owns live ink; our model owns completed strokes. */
public final class NotebookInk implements SurfaceHolder.Callback {
    public enum Tool { PEN, ERASER }

    /** Fixed radius in the page's logical pixels, independent of the displayed surface size. */
    public static final float ERASER_RADIUS = 18f;

    /** Paints only the opaque paper/background of the current physical viewport. */
    public interface PaperRenderer {
        void draw(Canvas canvas, int width, int height);
    }

    public interface Listener {
        void onGeometryReady(int width, int height);
        void onStrokeComplete(NotebookPage.Stroke stroke);
        void onPenStateChanged(boolean down);
        void onReady();
        void onError(String message);
        default void onEraseGesture(List<NotebookPage.Point> path, float radius) { }
        /** Synchronous borrowed event; callers must not retain it after this callback. */
        default void onFingerEvent(MotionEvent event) { }
    }

    public final SurfaceView view;
    private final Listener listener;
    private NotebookPage page;
    private final ArrayList<NotebookPage.Stroke> completed = new ArrayList<>();
    private final ArrayDeque<Completion> awaitingCommit = new ArrayDeque<>();
    private final ArrayDeque<NotebookPage.Stroke> nativeCommits = new ArrayDeque<>();
    private final NotebookFingerStream fingerStream = new NotebookFingerStream();
    private Object surface, pointListener, editListener;
    private Bitmap paperBitmap;
    private PaperRenderer paperRenderer;
    private boolean paperDirty, previewVisible;
    private boolean alive = true, wanted, prepared, enabled, restored, attached;
    private boolean penDown, failed, geometryReported, restoring;
    private int surfaceWidth, surfaceHeight, retries, configuredPenType = -1;
    private PointBuffer activePoints;
    private PointBuffer eraserPoints;
    private int eraserPointer = -1;
    private Tool tool = Tool.PEN;
    private float scaleX = 1, scaleY = 1, widthScale = 1;

    public NotebookInk(Context context, NotebookPage page, Listener listener) throws Exception {
        if (page == null || listener == null) throw new IllegalArgumentException("Missing notebook state");
        ApplicationInfo info = context.getPackageManager().getApplicationInfo(
            context.getPackageName(), PackageManager.GET_META_DATA);
        if (info.metaData == null || info.metaData.getInt("hw_eink.app.refresh.mode", -1) != 3) {
            throw new IllegalStateException("Handwriting display policy 3 is required");
        }
        this.page = page;
        this.listener = listener;
        completed.addAll(page.strokes);
        Class<?> editor = Class.forName("android.eink.view.edit.HWEditView");
        view = (SurfaceView) editor.getConstructor(Context.class).newInstance(context);
        // Firmware starts this wrapper with AHW enabled before its buffer is bound. Keep
        // input off from construction: first-page geometry and disk load may be asynchronous.
        surface = get(view, "getFBSurface");
        if (surface == null) throw new IllegalStateException("Native writing surface is unavailable");
        call(surface, "setAutoHandWrite", new Class<?>[]{boolean.class}, false);
        // Keep the known-working native RGBA8888 format, Z ordering and internal pen listener.
        call(get(view, "getSurfacePicture"), "bindColor", new Class<?>[]{int.class}, Color.WHITE);
        // Native firmware handles pen ink. Erasing is a document operation collected through
        // ordinary touch input with native handwriting off; no vendor pixel eraser is involved.
        view.setOnTouchListener((v, event) -> touch(event));
        view.getHolder().addCallback(this);
    }

    public void start() {
        if (!alive || failed) return;
        wanted = true;
        retries = 0;
        view.post(this::tryEnable);
    }

    /** Activity recreates this widget after pause, so its private partial native stroke is discarded. */
    public void stop() {
        wanted = false;
        fingerStream.reset();
        // UP and the firmware's track commit can arrive as separate queue messages. Preserve a
        // completed UP even when pause occurs before the later native track notification.
        flushCompletedUps();
        enabled = false;
        activePoints = null;
        clearEraser();
        awaitingCommit.clear();
        nativeCommits.clear();
        setPenDown(false);
        if (surface != null) {
            try { call(surface, "setAutoHandWrite", new Class<?>[]{boolean.class}, false); }
            catch (Exception e) { fail("Cannot stop handwriting", e); }
        }
        restored = false;
    }

    /**
     * Pause document editing without removing the native surface or its paper. A clean
     * completed page stays visible; only partial or unmatched native ink needs restoration.
     */
    public void pauseForEdit() {
        if (!alive || failed) return;
        boolean needsRestore = activePoints != null || !awaitingCommit.isEmpty()
            || !nativeCommits.isEmpty();
        wanted = false;
        enabled = false;
        try {
            if (surface != null) call(surface, "setAutoHandWrite", new Class<?>[]{boolean.class}, false);
            // The firmware can have received an UP before its internal track handler has run.
            // Canonical replacement below must also repair that unsettled native track list.
            needsRestore |= Boolean.TRUE.equals(get(view, "isReceivingTrack"));
            flushCompletedUps();
            if (failed) return;
            activePoints = null;
            clearEraser();
            awaitingCommit.clear();
            nativeCommits.clear();
            setPenDown(false);
            if (needsRestore) restored = false;
        } catch (Exception e) { fail("Cannot pause notebook editing", e); }
    }

    /**
     * Apply a page or completed edit on this paused surface. The existing bitmap stays bound;
     * paper changes repaint that owned bitmap before one draw posts paper and tracks. False
     * requests a fresh widget when native input or surface geometry cannot safely be reused.
     */
    public boolean applyEditPage(NotebookPage page) {
        if (!alive || failed) return false;
        if (page == null) throw new IllegalArgumentException("Missing page");
        if (wanted || enabled || penDown) throw new IllegalStateException("Pause before editing the page");
        if (page.width != this.page.width || page.height != this.page.height) {
            return false;
        }
        try {
            if (!prepared || !view.getHolder().getSurface().isValid() || paperBitmap == null
                    || paperBitmap.getWidth() != surfaceWidth || paperBitmap.getHeight() != surfaceHeight
                    || Boolean.TRUE.equals(get(view, "isReceivingTrack"))) return false;
            boolean sameStrokes = sameStrokeReferences(completed, page.strokes);
            boolean samePaper = this.page.template == page.template && !paperDirty;
            this.page = page;
            completed.clear();
            completed.addAll(page.strokes);
            if (sameStrokes && samePaper && restored && !previewVisible) return true;
            restored = false;
            restoring = true;
            try {
                if (!samePaper) paintPaper(paperBitmap);
                replaceNativeTracks();
                restored = true;
                previewVisible = false;
            } finally { restoring = false; }
            return true;
        } catch (Exception e) {
            fail("Cannot update notebook ink", e);
            return false;
        }
    }

    /** Set before initial start, or while paused; rendering waits for canonical restoration. */
    public void setPaperRenderer(PaperRenderer renderer) {
        requirePaused();
        paperRenderer = renderer;
        paperDirty = true;
    }

    /** Post a composed viewport on the existing surface after scrolling or an edit. */
    public boolean updateViewport(NotebookPage viewport, PaperRenderer renderer) {
        setPaperRenderer(renderer);
        return applyEditPage(viewport);
    }

    /**
     * One display post during finger scrolling, without rebuilding native tracks or changing
     * the saved/completed model. Canonical tracks are restored before the next pen input.
     */
    public boolean showScrollPreview(Bitmap preview) {
        requirePaused();
        if (!alive || failed) return false;
        if (preview == null || preview.isRecycled())
            throw new IllegalArgumentException("Missing scroll preview");
        if (!prepared || !view.getHolder().getSurface().isValid()) return false;
        if (preview.getWidth() != surfaceWidth || preview.getHeight() != surfaceHeight)
            throw new IllegalArgumentException("Scroll preview differs from viewport size");
        try {
            Canvas canvas = view.getHolder().lockCanvas();
            if (canvas == null) return false;
            try {
                canvas.drawColor(Color.WHITE);
                canvas.drawBitmap(preview, 0, 0, null);
                previewVisible = true;
            } finally { view.getHolder().unlockCanvasAndPost(canvas); }
            return true;
        } catch (Exception e) {
            fail("Cannot display notebook scrolling", e);
            return false;
        }
    }

    private void requirePaused() {
        if (wanted || enabled || penDown)
            throw new IllegalStateException("Pause before changing the notebook viewport");
    }

    private static boolean sameStrokeReferences(List<NotebookPage.Stroke> left,
                                                 List<NotebookPage.Stroke> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) if (left.get(i) != right.get(i)) return false;
        return true;
    }

    public boolean isPenDown() { return penDown; }

    /** Ready to accept the selected tool; eraser readiness does not imply native AHW is on. */
    public boolean isReady() { return alive && wanted && enabled && !failed; }

    public Tool getTool() { return tool; }

    public void setTool(Tool tool) {
        if (tool == null) throw new IllegalArgumentException("Missing tool");
        if (!alive || failed || this.tool == tool) return;
        // A completed UP remains an edit even if its native commit notification is pending.
        // An unfinished pen stroke or eraser gesture is discarded on a tool change.
        boolean needsRestore = activePoints != null || !awaitingCommit.isEmpty()
            || !nativeCommits.isEmpty();
        try {
            if (surface != null) call(surface, "setAutoHandWrite", new Class<?>[]{boolean.class}, false);
            enabled = false;
            flushCompletedUps();
            if (failed) return;
            this.tool = tool;
            activePoints = null;
            clearEraser();
            awaitingCommit.clear();
            nativeCommits.clear();
            setPenDown(false);
            if (needsRestore) restored = false;
            retries = 0;
            if (wanted) view.post(this::tryEnable);
        } catch (Exception e) { fail("Cannot change notebook tool", e); }
    }

    /** Use only for initial load and explicit paper changes, never for an autosave acknowledgement. */
    public void setPage(NotebookPage page) {
        if (!alive || failed) return;
        if (page == null) throw new IllegalArgumentException("Missing page");
        if (penDown) throw new IllegalStateException("Finish the stroke before changing the page");
        boolean resume = wanted;
        stop();
        this.page = page;
        completed.clear();
        completed.addAll(page.strokes);
        geometryReported = false;
        wanted = resume;
        retries = 0;
        if (wanted) view.post(this::tryEnable);
    }

    public void release() {
        if (!alive) return;
        stop();
        alive = false;
        detach();
        surface = null;
        view.getHolder().removeCallback(this);
        view.setOnTouchListener(null);
        // SurfacePicture may still be drawing this bitmap. Let its owner release it naturally.
        paperBitmap = null;
        paperRenderer = null;
    }

    private void tryEnable() {
        if (!alive || failed || !wanted || enabled) return;
        if (!prepared || !view.getHolder().getSurface().isValid()) {
            if (++retries <= 20) view.postDelayed(this::tryEnable, 100);
            else fail("Writing surface did not become ready", null);
            return;
        }
        if (!page.isInitialized()) {
            if (!geometryReported) {
                geometryReported = true;
                listener.onGeometryReady(surfaceWidth, surfaceHeight);
            }
            return;
        }
        try {
            surface = get(view, "getFBSurface");
            if (surface == null || get(view, "getActiveBuffer") == null) {
                throw new IllegalStateException("Native writing buffer is unavailable");
            }
            call(surface, "setAutoHandWrite", new Class<?>[]{boolean.class}, false);
            if (!attached) attach();
            scaleX = (float) surfaceWidth / page.width;
            scaleY = (float) surfaceHeight / page.height;
            widthScale = (float) Math.sqrt((double) scaleX * scaleY);
            if (!restored) restoreDocument();
            else if (previewVisible || paperDirty) {
                if (paperDirty) paintPaper(paperBitmap);
                replaceNativeTracks();
                previewVisible = false;
            }
            call(surface, "setAhwToolTypes", new Class<?>[]{int[].class},
                new int[]{MotionEvent.TOOL_TYPE_STYLUS});
            call(surface, "setNeedAddDownEvent", new Class<?>[]{boolean.class}, true);
            int ballpoint = Class.forName("android.eink.view.HandPenConfig")
                .getField("PEN_TYPE_YUANZHUBI").getInt(null);
            configuredPenType = ballpoint;
            call(surface, "setAhwPen", new Class<?>[]{int.class, float.class, int.class},
                ballpoint, 4f * widthScale, Color.BLACK);
            call(view, "setState", new Class<?>[]{int.class},
                view.getClass().getField("STATE_HADNWRITE").getInt(null));
            boolean autoInk = tool == Tool.PEN;
            call(surface, "setAutoHandWrite", new Class<?>[]{boolean.class}, autoInk);
            if (Boolean.TRUE.equals(get(surface, "isAutoHandWrite")) != autoInk) {
                throw new IllegalStateException("Firmware refused the selected notebook tool");
            }
            enabled = true;
            listener.onReady();
        } catch (Exception e) { fail("Cannot prepare native notebook", e); }
    }

    private void attach() throws Exception {
        Class<?> touchType = Class.forName("android.eink.view.IAhwTouchListener");
        pointListener = Proxy.newProxyInstance(touchType.getClassLoader(), new Class<?>[]{touchType},
            (proxy, method, args) -> {
                Object base = objectMethod(proxy, method, args, "NotebookPenListener");
                if (base != NOT_OBJECT_METHOD) return base;
                if ("onAhwAddPoint".equals(method.getName()) && enabled && !restoring && tool == Tool.PEN) {
                    point((Integer) args[0], (Float) args[1], (Float) args[2],
                        (Long) args[3], (Float) args[4]);
                }
                return null;
            });
        // The exposed setter multiplexes our observer with HWEditView's internal track listener.
        call(surface, "setAhwTouchListener", new Class<?>[]{touchType}, pointListener);
        attached = true;
        Class<?> editType = Class.forName("android.eink.view.edit.HWEditView$OnEditListener");
        editListener = Proxy.newProxyInstance(editType.getClassLoader(), new Class<?>[]{editType},
            (proxy, method, args) -> {
                Object base = objectMethod(proxy, method, args, "NotebookCommitListener");
                if (base != NOT_OBJECT_METHOD) return base;
                if ("onEdit".equals(method.getName()) && enabled && !restoring && tool == Tool.PEN) nativeCommit();
                return null;
            });
        call(view, "setOnEditListener", new Class<?>[]{editType}, editListener);
    }

    private boolean touch(MotionEvent event) {
        if (!alive || failed) return true;
        // Own an accepted finger stream while AHW is paused. A pen joining that stream must
        // not hide its terminal event or restart native handwriting with contacts still down.
        boolean penPresent = false;
        for (int i = 0; i < event.getPointerCount(); i++)
            if (isEraserInput(event.getToolType(i))) { penPresent = true; break; }
        boolean changedFinger = event.getPointerCount() > 0
            && event.getToolType(event.getActionIndex()) == MotionEvent.TOOL_TYPE_FINGER;
        NotebookFingerStream.Dispatch dispatch = fingerStream.accept(event.getActionMasked(),
            penPresent, changedFinger, penDown);
        if (dispatch != NotebookFingerStream.Dispatch.UNHANDLED) {
            MotionEvent cancellation = null;
            try {
                if (dispatch == NotebookFingerStream.Dispatch.FORWARD) listener.onFingerEvent(event);
                else if (dispatch == NotebookFingerStream.Dispatch.CANCEL) {
                    cancellation = MotionEvent.obtain(event);
                    cancellation.setAction(MotionEvent.ACTION_CANCEL);
                    listener.onFingerEvent(cancellation);
                }
            } catch (Exception e) { fail("Cannot handle notebook scrolling", e); }
            finally { if (cancellation != null) cancellation.recycle(); }
            return true;
        }
        if (!isReady()) return true;
        if (tool == Tool.PEN) {
            // Prevent a physical eraser tip from silently changing native track history.
            return event.getToolType(event.getActionIndex()) != MotionEvent.TOOL_TYPE_STYLUS;
        }
        try {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_CANCEL) {
                clearEraser();
                setPenDown(false);
                return true;
            }
            int changed = event.getActionIndex();
            if ((action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN)
                    && eraserPointer == -1 && isEraserInput(event.getToolType(changed))) {
                eraserPointer = event.getPointerId(changed);
                eraserPoints = new PointBuffer();
                addEraserPoint(event, changed, -1);
                setPenDown(true);
            } else if (eraserPointer != -1) {
                int index = event.findPointerIndex(eraserPointer);
                if (index < 0) {
                    clearEraser();
                    setPenDown(false);
                    return true;
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    for (int h = 0; h < event.getHistorySize(); h++) addEraserPoint(event, index, h);
                    addEraserPoint(event, index, -1);
                } else if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP)
                        && event.getPointerId(changed) == eraserPointer) {
                    addEraserPoint(event, index, -1);
                    List<NotebookPage.Point> path = eraserPoints.toPoints();
                    clearEraser();
                    setPenDown(false);
                    listener.onEraseGesture(path, ERASER_RADIUS);
                }
            }
        } catch (Exception e) { fail("Cannot capture eraser input", e); }
        // Consume all eraser-mode touch, including fingers. No redraw or document edit on MOVE.
        return true;
    }

    private static boolean isEraserInput(int type) {
        return type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER;
    }

    private void addEraserPoint(MotionEvent event, int index, int history) {
        float x = history < 0 ? event.getX(index) : event.getHistoricalX(index, history);
        float y = history < 0 ? event.getY(index) : event.getHistoricalY(index, history);
        float pressure = history < 0 ? event.getPressure(index) : event.getHistoricalPressure(index, history);
        long time = history < 0 ? event.getEventTime() : event.getHistoricalEventTime(history);
        eraserPoints.add(clamp(x / scaleX, page.width), clamp(y / scaleY, page.height), time, pressure);
    }

    private void clearEraser() {
        eraserPointer = -1;
        eraserPoints = null;
    }

    private void point(int action, float x, float y, long time, float pressure) {
        try {
            if (action == MotionEvent.ACTION_DOWN) {
                activePoints = new PointBuffer();
                setPenDown(true);
            }
            if (activePoints != null) {
                activePoints.add(clamp(x / scaleX, page.width), clamp(y / scaleY, page.height),
                    time, pressure);
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (activePoints != null) {
                    awaitingCommit.addLast(new Completion(time, action == MotionEvent.ACTION_CANCEL, activePoints));
                    if (awaitingCommit.size() > 32) throw new IllegalStateException("Native stroke commits stalled");
                }
                activePoints = null;
                setPenDown(false);
                drainCommits();
            }
        } catch (Exception e) { fail("Cannot capture pen input", e); }
        // No application invalidation, status update, file write or log occurs per MOVE.
    }

    private void nativeCommit() {
        try {
            List<?> tracks = (List<?>) get(view, "getTrackList");
            if (tracks.size() != completed.size() + nativeCommits.size() + 1) {
                throw new IllegalStateException("Native and saved stroke counts differ");
            }
            nativeCommits.addLast(readTrack(tracks.get(tracks.size() - 1)));
            // Both firmware observers run on the main queue. Drain from both sides so a complete
            // stroke reaches autosave before a following onPause, in either listener order.
            drainCommits();
        } catch (Exception e) { fail("Cannot read completed native stroke", e); }
    }

    private void drainCommits() {
        while (alive && !failed && !nativeCommits.isEmpty()) {
            NotebookPage.Stroke stroke = nativeCommits.peekFirst();
            long end = stroke.points.get(stroke.points.size() - 1).time;
            while (!awaitingCommit.isEmpty() && awaitingCommit.peekFirst().time < end) {
                // A previous cancellation need not produce a native track.
                Completion older = awaitingCommit.removeFirst();
                if (!older.cancelled) {
                    fail("Native stroke finished without a matching pen-up event", null);
                    return;
                }
            }
            if (awaitingCommit.isEmpty()) return; // The external UP observer has not run yet.
            if (awaitingCommit.peekFirst().time != end) {
                fail("Native stroke finished without a matching pen-up event", null);
                return;
            }
            Completion completion = awaitingCommit.removeFirst();
            nativeCommits.removeFirst();
            if (completion.cancelled) {
                // A cancelled stroke is not a durable document edit. Remove transient native ink.
                if (penDown || !nativeCommits.isEmpty()) {
                    fail("Cancelled pen input could not be safely reset", null);
                    return;
                }
                try {
                    call(surface, "setAutoHandWrite", new Class<?>[]{boolean.class}, false);
                    restoreDocument();
                    call(surface, "setAutoHandWrite", new Class<?>[]{boolean.class},
                        wanted && enabled && tool == Tool.PEN);
                } catch (Exception e) { fail("Cannot discard cancelled stroke", e); }
                continue;
            }
            completed.add(stroke);
            listener.onStrokeComplete(stroke);
        }
    }

    private void flushCompletedUps() {
        if (!alive || failed || restoring) return;
        drainCommits();
        if (failed) return;
        try {
            while (!awaitingCommit.isEmpty()) {
                Completion completion = awaitingCommit.removeFirst();
                if (completion.cancelled) continue;
                if (configuredPenType < 0) throw new IllegalStateException("Completed stroke has no pen style");
                NotebookPage.Stroke stroke = completion.points.toStroke(configuredPenType);
                completed.add(stroke);
                // Remove the token before notification so another pause cannot submit it twice.
                // Input stays disabled until canonical restoration or widget replacement.
                listener.onStrokeComplete(stroke);
            }
        } catch (Exception e) { fail("Cannot preserve completed pen input", e); }
    }

    private void restoreDocument() throws Exception {
        restoring = true;
        try {
            Bitmap background = Bitmap.createBitmap(surfaceWidth, surfaceHeight, Bitmap.Config.ARGB_8888);
            background.setDensity(Bitmap.DENSITY_NONE);
            paintPaper(background);
            // bindBitmap and setTrackList redraw synchronously on a prepared native surface.
            call(get(view, "getSurfacePicture"), "bindBitmap", new Class<?>[]{Bitmap.class}, background);
            paperBitmap = background;
            replaceNativeTracks();
            restored = true;
            previewVisible = false;
        } finally { restoring = false; }
    }

    private void paintPaper(Bitmap bitmap) {
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        if (paperRenderer != null) {
            int checkpoint = canvas.save();
            try { paperRenderer.draw(canvas, bitmap.getWidth(), bitmap.getHeight()); }
            finally { canvas.restoreToCount(checkpoint); }
            paperDirty = false;
            return;
        }
        if (page.template == NotebookPage.Template.LINED) {
            Paint rule = new Paint();
            rule.setColor(Color.rgb(210, 210, 210));
            rule.setStrokeWidth(Math.max(1f, widthScale));
            for (float y = 72; y < page.height; y += 72)
                canvas.drawLine(0, y * scaleY, surfaceWidth, y * scaleY, rule);
        }
        paperDirty = false;
    }

    private void replaceNativeTracks() throws Exception {
        ArrayList<Object> tracks = new ArrayList<>();
        Object creator = get(view, "getHWTrackCreator");
        Method createPoint = creator.getClass().getMethod("createHWPoint",
            float.class, float.class, long.class, float.class);
        Method createTrack = creator.getClass().getMethod("createHWTrack",
            int.class, float.class, int.class, long.class, List.class, RectF.class);
        for (NotebookPage.Stroke stroke : completed) {
            tracks.add(createTrack(creator, createPoint, createTrack, stroke));
        }
        // HWEditView draws its bound paper and all replacement tracks before one surface post.
        // Rebinding paper here would request a separate draw and expose blank intermediate ink.
        call(view, "setTrackList", new Class<?>[]{List.class}, tracks);
        if (getNativeTrackCount() != completed.size()) {
            throw new IllegalStateException("Firmware did not restore the complete page");
        }
    }

    private Object createTrack(Object creator, Method createPoint, Method createTrack,
                               NotebookPage.Stroke stroke) throws Exception {
        ArrayList<Object> points = new ArrayList<>();
        float left = Float.MAX_VALUE, top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        for (NotebookPage.Point point : stroke.points) {
            float x = point.x * scaleX, y = point.y * scaleY;
            points.add(invoke(creator, createPoint, x, y, point.time, point.pressure));
            left = Math.min(left, x); top = Math.min(top, y);
            right = Math.max(right, x); bottom = Math.max(bottom, y);
        }
        float width = stroke.width * widthScale;
        // Match HWTrackHelper.calcBounds: conservative padding is one complete pen width.
        RectF bounds = new RectF(left - width, top - width, right + width, bottom + width);
        return invoke(creator, createTrack, stroke.penType, width, stroke.color, 0L, points, bounds);
    }

    private NotebookPage.Stroke readTrack(Object track) throws Exception {
        ArrayList<NotebookPage.Point> points = new ArrayList<>();
        List<?> nativePoints = (List<?>) get(track, "getPoints");
        if (nativePoints.isEmpty()) throw new IllegalStateException("Native stroke contains no points");
        Class<?> pointType = nativePoints.get(0).getClass();
        Field x = pointType.getField("x"), y = pointType.getField("y");
        Field time = pointType.getField("eventtime"), pressure = pointType.getField("pressure");
        for (Object point : nativePoints) {
            points.add(new NotebookPage.Point(clamp(x.getFloat(point) / scaleX, page.width),
                clamp(y.getFloat(point) / scaleY, page.height), time.getLong(point), pressure.getFloat(point)));
        }
        return new NotebookPage.Stroke((Integer) get(track, "getPenType"),
            (Float) get(track, "getPenWidth") / widthScale,
            (Integer) get(track, "getPenColor"), points);
    }

    /** Readback of this owned native widget, used by the disposable restoration check. */
    public List<NotebookPage.Stroke> snapshotNativeStrokes() throws Exception {
        ArrayList<NotebookPage.Stroke> result = new ArrayList<>();
        for (Object track : (List<?>) get(view, "getTrackList")) result.add(readTrack(track));
        return Collections.unmodifiableList(result);
    }

    public int getNativeTrackCount() throws Exception { return ((List<?>) get(view, "getTrackList")).size(); }

    public int getPagePointCount() {
        int count = 0;
        for (NotebookPage.Stroke stroke : completed) count += stroke.points.size();
        return count;
    }

    private void setPenDown(boolean down) {
        if (penDown != down) {
            penDown = down;
            listener.onPenStateChanged(down);
        }
    }

    private void fail(String message, Exception cause) {
        if (failed || !alive) return;
        failed = true;
        fingerStream.reset();
        wanted = false;
        enabled = false;
        activePoints = null;
        clearEraser();
        awaitingCommit.clear();
        nativeCommits.clear();
        if (surface != null) {
            try { call(surface, "setAutoHandWrite", new Class<?>[]{boolean.class}, false); }
            catch (Exception ignored) { /* Preserve the first setup/capture failure. */ }
        }
        view.setEnabled(false);
        setPenDown(false);
        listener.onError(message + (cause == null ? "" : ": " + cause.getMessage()));
    }

    private void detach() {
        if (surface != null && attached) {
            try {
                Class<?> touchType = Class.forName("android.eink.view.IAhwTouchListener");
                call(surface, "setAhwTouchListener", new Class<?>[]{touchType}, new Object[]{null});
            } catch (Exception ignored) { /* Auto handwriting has already been disabled. */ }
        }
        try {
            Class<?> editType = Class.forName("android.eink.view.edit.HWEditView$OnEditListener");
            call(view, "setOnEditListener", new Class<?>[]{editType}, new Object[]{null});
        } catch (Exception ignored) { /* This instance is being discarded. */ }
        attached = false;
        pointListener = null;
        editListener = null;
    }

    @Override public void surfaceCreated(SurfaceHolder holder) { prepared = false; }

    @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        boolean resume = wanted;
        stop();
        wanted = resume;
        surfaceWidth = width;
        surfaceHeight = height;
        prepared = width > 0 && height > 0;
        geometryReported = false;
        if (wanted) view.post(this::tryEnable);
    }

    @Override public void surfaceDestroyed(SurfaceHolder holder) {
        boolean resume = wanted;
        stop();
        wanted = resume;
        prepared = false;
        detach();
        surface = null;
    }

    private static float clamp(float value, int maximum) {
        if (!NotebookPage.finite(value)) throw new IllegalArgumentException("Invalid pen coordinate");
        return Math.max(0, Math.min(maximum, value));
    }

    private static Object get(Object target, String name) throws Exception {
        return call(target, name, new Class<?>[0]);
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        return invoke(target, target.getClass().getMethod(name, types), args);
    }

    private static Object invoke(Object target, Method method, Object... args) throws Exception {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw new Exception(method.getName() + ": " + cause, cause);
        }
    }

    private static final Object NOT_OBJECT_METHOD = new Object();

    private static Object objectMethod(Object proxy, Method method, Object[] args, String description) {
        switch (method.getName()) {
            case "toString": return description;
            case "hashCode": return System.identityHashCode(proxy);
            case "equals": return proxy == args[0];
            default: return NOT_OBJECT_METHOD;
        }
    }

    private static final class Completion {
        final long time;
        final boolean cancelled;
        final PointBuffer points;
        Completion(long time, boolean cancelled, PointBuffer points) {
            this.time = time;
            this.cancelled = cancelled;
            this.points = points;
        }
    }

    /** Primitive capture avoids allocating a second Point object for every live MOVE. */
    private static final class PointBuffer {
        private float[] coordinates = new float[128 * 3];
        private long[] times = new long[128];
        private int size;

        void add(float x, float y, long time, float pressure) {
            if (!NotebookPage.finite(pressure) || pressure < 0 || time < 0) {
                throw new IllegalArgumentException("Invalid pen point");
            }
            if (size >= NotebookStore.MAX_POINTS_PER_STROKE) {
                throw new IllegalStateException("This stroke exceeds the page storage limit");
            }
            if (size == times.length) {
                int capacity = Math.min(NotebookStore.MAX_POINTS_PER_STROKE, times.length * 2);
                times = Arrays.copyOf(times, capacity);
                coordinates = Arrays.copyOf(coordinates, capacity * 3);
            }
            int index = size * 3;
            coordinates[index] = x;
            coordinates[index + 1] = y;
            coordinates[index + 2] = pressure;
            times[size++] = time;
        }

        NotebookPage.Stroke toStroke(int penType) {
            return new NotebookPage.Stroke(penType, 4f, Color.BLACK, toPoints());
        }

        List<NotebookPage.Point> toPoints() {
            ArrayList<NotebookPage.Point> points = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                int index = i * 3;
                points.add(new NotebookPage.Point(coordinates[index], coordinates[index + 1],
                    times[i], coordinates[index + 2]));
            }
            return Collections.unmodifiableList(points);
        }
    }
}
