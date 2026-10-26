package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.os.SystemClock;
import org.eink.inkprobe.DisplayPolicy;
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
public final class NotebookInk implements NotebookInkBackend, SurfaceHolder.Callback {
    /** Default radius retained for the disposable native restoration self-test. */
    public static final float ERASER_RADIUS = NotebookWidthRange.ERASER.defaultWidth / 2f;
    private static final float LASSO_WIDTH = 2.5f;

    public final SurfaceView view;
    interface SampleObserver { void sample(int action, long eventTime); }
    private SampleObserver sampleObserver;
    private final Listener listener;
    private NotebookPage page;
    private final ArrayList<NotebookPage.Stroke> completed = new ArrayList<>();
    private final ArrayDeque<Completion> awaitingCommit = new ArrayDeque<>();
    private final ArrayDeque<NotebookPage.Stroke> nativeCommits = new ArrayDeque<>();
    private final NotebookFingerStream fingerStream = new NotebookFingerStream();
    private final NotebookSelectionStream selectionStream = new NotebookSelectionStream();
    private final NotebookLassoGesture lassoGesture = new NotebookLassoGesture();
    private final NotebookLassoGesture nativeLassoGesture = new NotebookLassoGesture();
    private final NotebookLassoCompletion lassoCompletion = new NotebookLassoCompletion();
    private int lassoPointer = -1, lassoSettleFrames;
    private boolean selectionMode, replaceWidget;
    private final Runnable finishLasso = this::finishLassoAfterInput;
    private Object surface, pointListener, editListener;
    private Bitmap paperBitmap;
    private PaperRenderer paperRenderer;
    private boolean paperDirty, previewVisible;
    private boolean alive = true, wanted, prepared, enabled, restored, attached;
    private boolean penDown, failed, geometryReported, restoring;
    private int surfaceWidth, surfaceHeight, retries;
    private float penWidth = NotebookPenConfig.DEFAULT_WIDTH;
    private NotebookPenConfig.Style configuredStyle;
    private PointBuffer activePoints;
    private PointBuffer eraserPoints;
    private float eraserWidth = NotebookWidthRange.ERASER.defaultWidth;
    private final NotebookEraserCommit eraserCommit = new NotebookEraserCommit();
    private boolean eraserPreviewVisible;
    private final Runnable eraserTimeout = () -> {
        if (eraserCommit.isWaiting()) fail("Native tool did not finish; reopen the notebook", null);
    };
    private Tool tool = Tool.PEN;
    private float scaleX = 1, scaleY = 1, widthScale = 1;
    private Rect overlayScreenBounds;

    public NotebookInk(Context context, NotebookPage page, Listener listener) throws Exception {
        if (page == null || listener == null) throw new IllegalArgumentException("Missing notebook state");
        DisplayPolicy.requireHandwritingPolicy(context);
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
        // All live trails use AHW. Grey eraser and dashed lasso tracks are temporary;
        // only our model edits/selects ink after the native track has finished.
        view.setOnTouchListener((v, event) -> touch(event));
        view.getHolder().addCallback(this);
    }

    @Override public android.view.View view() { return view; }

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
        clearLasso(); selectionStream.reset();
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
        boolean needsRestore = activePoints != null || eraserCommit.isActive() || !awaitingCommit.isEmpty()
            || !nativeCommits.isEmpty() || lassoGesture.isActive() || nativeLassoGesture.isActive() || lassoCompletion.isPending();
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
            clearLasso();
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
        if (!alive || failed || replaceWidget) return false;
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
            if (sameStrokes && samePaper && restored && !previewVisible && !eraserPreviewVisible) return true;
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

    public boolean isReady() {
        return alive && wanted && enabled && !failed && !eraserCommit.isWaiting() && !lassoCompletion.isPending();
    }

    @Override public void setSelectionMode(boolean selected) {
        requirePaused();
        if (selected && !selectionMode) selectionStream.activate(SystemClock.uptimeMillis());
        selectionMode = selected;
        if (!selected) selectionStream.reset();
    }

    public Tool getTool() { return tool; }

    /** Disposable diagnostics only. The notebook editor leaves this null. */
    void setSampleObserver(SampleObserver observer) { sampleObserver = observer; }

    /** Width uses this widget's page coordinates; the editor supplies its fitted width. */
    public void setPenWidth(float width) {
        if (!NotebookPage.finite(width) || width <= 0 || width > 1024)
            throw new IllegalArgumentException("Invalid pen width");
        if (!alive || failed || penWidth == width) return;
        requirePaused();
        penWidth = width;
    }

    /** Diameter in widget/page coordinates, captured once at eraser-DOWN. */
    public void setEraserWidth(float width) {
        if (!NotebookPage.finite(width) || width <= 0 || width > NotebookStore.MAX_DIMENSION)
            throw new IllegalArgumentException("Invalid eraser width");
        if (!alive || failed || eraserWidth == width) return;
        requirePaused();
        eraserWidth = width;
    }

    /**
     * Excludes a floating control window from native pen-DOWN handling. Bounds use
     * physical screen pixels, independently of the notebook's logical page scale.
     * Call before displaying or moving the window; pause handwriting before moving
     * it during an existing gesture. A stroke begun on paper may pass behind it.
     */
    public void setOverlayBounds(Rect screenBounds) {
        if (!alive || failed) return;
        overlayScreenBounds = screenBounds == null || screenBounds.isEmpty()
            ? null : new Rect(screenBounds);
        // Layout and framebuffer binding can follow creation of the floating window.
        // tryEnable always installs the latest exclusion before native ink resumes.
        if (!prepared || surface == null) return;
        try { applyOverlayClip(); }
        catch (Exception e) { fail("Cannot protect floating notebook controls", e); }
    }

    private void applyOverlayClip() throws Exception {
        Path clip = null;
        if (overlayScreenBounds != null) {
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            Rect bounds = overlayScreenBounds;
            NotebookOverlayClip.Hole hole = NotebookOverlayClip.localHole(
                location[0], location[1], surfaceWidth, surfaceHeight,
                bounds.left, bounds.top, bounds.right, bounds.bottom);
            if (hole != null) {
                clip = new Path();
                clip.setFillType(Path.FillType.EVEN_ODD);
                clip.addRect(0, 0, surfaceWidth, surfaceHeight, Path.Direction.CW);
                clip.addRect(hole.left, hole.top, hole.right, hole.bottom, Path.Direction.CW);
            }
        }
        // setClip replaces the persistent initial native clip; clipRect would stack
        // records on every drag. Null also clears a retained framebuffer's old clip.
        call(surface, "setClip", new Class<?>[]{Path.class}, clip);
    }

    public void setTool(Tool tool) {
        if (tool == null) throw new IllegalArgumentException("Missing tool");
        if (!alive || failed || this.tool == tool) return;
        // A completed UP remains an edit even if its native commit notification is pending.
        // An unfinished pen stroke or eraser gesture is discarded on a tool change.
        boolean needsRestore = activePoints != null || eraserCommit.isActive() || !awaitingCommit.isEmpty()
            || !nativeCommits.isEmpty() || lassoGesture.isActive() || nativeLassoGesture.isActive() || lassoCompletion.isPending();
        try {
            if (surface != null) call(surface, "setAutoHandWrite", new Class<?>[]{boolean.class}, false);
            enabled = false;
            flushCompletedUps();
            if (failed) return;
            this.tool = tool;
            activePoints = null;
            clearEraser();
            clearLasso(); selectionStream.reset();
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
        sampleObserver = null;
        detach();
        surface = null;
        view.getHolder().removeCallback(this);
        view.setOnTouchListener(null);
        // SurfacePicture may still be drawing this bitmap. Let its owner release it naturally.
        paperBitmap = null;
        paperRenderer = null;
    }

    private void tryEnable() {
        if (!alive || failed || !wanted || enabled || lassoCompletion.isPending()) return;
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
            else if (previewVisible || paperDirty || eraserPreviewVisible) {
                if (paperDirty) paintPaper(paperBitmap);
                replaceNativeTracks();
                previewVisible = false;
            }
            call(surface, "setAhwToolTypes", new Class<?>[]{int[].class}, tool == Tool.ERASER
                ? new int[]{MotionEvent.TOOL_TYPE_STYLUS, MotionEvent.TOOL_TYPE_ERASER}
                : new int[]{MotionEvent.TOOL_TYPE_STYLUS});
            call(surface, "setNeedAddDownEvent", new Class<?>[]{boolean.class}, true);
            int ballpoint = Class.forName("android.eink.view.HandPenConfig")
                .getField("PEN_TYPE_YUANZHUBI").getInt(null);
            int nativePen = tool == Tool.LASSO ? Class.forName("android.eink.view.HandPenConfig")
                .getField("PEN_TYPE_DASH").getInt(null) : ballpoint;
            call(surface, "setAhwPen", new Class<?>[]{int.class, float.class, int.class},
                nativePen, (tool == Tool.LASSO ? LASSO_WIDTH : tool == Tool.PEN ? penWidth : eraserWidth) * widthScale,
                tool == Tool.ERASER ? NotebookEraserCommit.COLOR : Color.BLACK);
            configuredStyle = new NotebookPenConfig.Style(ballpoint, penWidth, Color.BLACK);
            call(view, "setState", new Class<?>[]{int.class},
                view.getClass().getField("STATE_HADNWRITE").getInt(null));
            applyOverlayClip();
            call(surface, "setAutoHandWrite", new Class<?>[]{boolean.class}, !selectionMode);
            if (Boolean.TRUE.equals(get(surface, "isAutoHandWrite")) == selectionMode) {
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
                if ("onAhwAddPoint".equals(method.getName()) && enabled && !restoring) {
                    if (sampleObserver != null) sampleObserver.sample((Integer) args[0], (Long) args[3]);
                    if (tool == Tool.PEN) point((Integer) args[0], (Float) args[1], (Float) args[2],
                        (Long) args[3], (Float) args[4]);
                    else if (tool == Tool.ERASER) eraserPoint((Integer) args[0], (Float) args[1], (Float) args[2],
                        (Long) args[3], (Float) args[4]);
                    else if (!selectionMode) nativeLassoPoint((Integer) args[0], (Float) args[1], (Float) args[2],
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
                if ("onEdit".equals(method.getName()) && enabled && !restoring) {
                    if (tool == Tool.PEN) nativeCommit(); else if (tool == Tool.ERASER) nativeEraserCommit();
                }
                return null;
            });
        call(view, "setOnEditListener", new Class<?>[]{editType}, editListener);
    }

    private boolean touch(MotionEvent event) {
        if (!alive || failed) return true;
        if (selectionMode) {
            if (selectionStream.accept(event.getActionMasked(), event.getEventTime(), isReady())) {
                listener.onSelectionEvent(event);
            }
            return true;
        }
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
        if (tool == Tool.LASSO && (isReady() || lassoGesture.isActive())) { captureLasso(event); return true; }
        if (!isReady()) return true;
        if (tool == Tool.PEN) {
            // Prevent a physical eraser tip from silently changing native track history.
            return event.getToolType(event.getActionIndex()) != MotionEvent.TOOL_TYPE_STYLUS;
        }
        // AHW receives stylus samples directly, independently of Android dispatch.
        // Consume Android eraser-tip events to prevent the widget's destructive pixel eraser.
        return true;
    }

    /** Android UP completes the lasso even when the dashed native brush has no edit callback. */
    private void captureLasso(MotionEvent event) {
        try {
            int action = event.getActionMasked(), changed = event.getActionIndex();
            if (action == MotionEvent.ACTION_DOWN) {
                if (lassoCompletion.isPending()) return;
                if (event.getToolType(changed) != MotionEvent.TOOL_TYPE_STYLUS) return;
                lassoPointer = event.getPointerId(changed); lassoGesture.begin(); setPenDown(true);
                eraserPreviewVisible = true;
            }
            if (!lassoGesture.isActive()) return;
            int index = event.findPointerIndex(lassoPointer);
            boolean lifted = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP
                && event.getPointerId(changed) == lassoPointer;
            boolean cancelled = action == MotionEvent.ACTION_CANCEL || index < 0;
            if (index >= 0 && (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || lifted)) {
                for (int h = 0; h < event.getHistorySize(); h++) lassoGesture.add(
                    event.getHistoricalX(index, h) / scaleX, event.getHistoricalY(index, h) / scaleY,
                    event.getHistoricalEventTime(h), event.getHistoricalPressure(index, h));
                lassoGesture.add(event.getX(index) / scaleX, event.getY(index) / scaleY,
                    event.getEventTime(), event.getPressure(index));
            }
            if (lifted || cancelled) {
                List<NotebookPage.Point> path = lassoGesture.finish(cancelled); lassoPointer = -1;
                completeLassoInput(path, false);
            }
        } catch (Exception error) { fail("Cannot finish lasso input", error); }
        // AHW draws the dash; MOVE only collects primitive samples, including event history.
    }

    private void nativeLassoPoint(int action, float x, float y, long time, float pressure) {
        try {
            if (action == MotionEvent.ACTION_DOWN) {
                if (lassoCompletion.isPending()) return;
                nativeLassoGesture.begin(); eraserPreviewVisible = true; setPenDown(true);
            }
            if (!nativeLassoGesture.isActive()) return;
            nativeLassoGesture.add(x / scaleX, y / scaleY, time, pressure);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                completeLassoInput(nativeLassoGesture.finish(action == MotionEvent.ACTION_CANCEL), true);
        } catch (Exception error) { fail("Cannot capture native lasso input", error); }
    }

    private void completeLassoInput(List<NotebookPage.Point> path, boolean nativeSamples) throws Exception {
        if (!lassoCompletion.offer(path, nativeSamples)) return;
        call(surface, "setAutoHandWrite", new Class<?>[]{boolean.class}, false);
        setPenDown(false); lassoSettleFrames = 0; view.postDelayed(finishLasso, 16);
    }

    private void finishLassoAfterInput() {
        if (!alive || failed || !lassoCompletion.isPending()) return;
        try {
            if (Boolean.TRUE.equals(get(view, "isReceivingTrack"))) {
                if (++lassoSettleFrames < 16) { view.postDelayed(finishLasso, 16); return; }
                // Some dashed brushes do not settle a saved track. Retire their accumulator
                // through the editor's existing widget-replacement path, preserving selection.
                replaceWidget = true;
            }
            List<NotebookPage.Point> path = lassoCompletion.take();
            listener.onLassoGesture(path);
        } catch (Exception error) { fail("Cannot complete lasso selection", error); }
    }

    private void clearLasso() {
        view.removeCallbacks(finishLasso); lassoGesture.reset(); nativeLassoGesture.reset();
        lassoCompletion.reset(); lassoPointer = -1;
    }

    private static boolean isEraserInput(int type) {
        return type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER;
    }

    private void clearEraser() {
        view.removeCallbacks(eraserTimeout);
        eraserPoints = null;
        eraserCommit.reset();
    }

    private void eraserPoint(int action, float x, float y, long time, float pressure) {
        try {
            if (action == MotionEvent.ACTION_DOWN) {
                eraserCommit.begin(eraserWidth);
                eraserPoints = new PointBuffer();
                eraserPreviewVisible = true;
                setPenDown(true);
            }
            if (eraserPoints != null) {
                if (!NotebookPage.finite(x) || !NotebookPage.finite(y))
                    throw new IllegalArgumentException("Invalid eraser coordinate");
                // Off-page centres must survive: a broad brush can reach ink from a gap.
                eraserPoints.add(x / scaleX, y / scaleY, time, pressure);
            }
            if ((action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                    && eraserPoints != null) {
                eraserCommit.up(eraserPoints.toPoints(), time, action == MotionEvent.ACTION_CANCEL);
                eraserPoints = null;
                // Observe the already queued track commit, but block another gesture until
                // its temporary grey track has been removed by the canonical edit.
                call(surface, "setAutoHandWrite", new Class<?>[]{boolean.class}, false);
                setPenDown(false);
                view.postDelayed(eraserTimeout, 1500);
                finishNativeErase();
            }
        } catch (Exception error) { fail("Cannot capture native tool gesture", error); }
        // AHW owns live drawing: no Canvas work, invalidate or UI update per MOVE.
    }

    private void nativeEraserCommit() {
        try {
            List<?> tracks = (List<?>) get(view, "getTrackList");
            if (tracks.size() != completed.size() + 1)
                throw new IllegalStateException("Unexpected temporary tool track count");
            Object track = tracks.get(tracks.size() - 1);
            List<?> points = (List<?>) get(track, "getPoints");
            if (points.isEmpty()) throw new IllegalStateException("Empty temporary tool track");
            Object last = points.get(points.size() - 1);
            eraserCommit.track(last.getClass().getField("eventtime").getLong(last));
            finishNativeErase();
        } catch (Exception error) { fail("Cannot finish native tool gesture", error); }
    }

    private void finishNativeErase() throws Exception {
        NotebookEraserCommit.Result result = eraserCommit.takeReady();
        if (result == null) return;
        view.removeCallbacks(eraserTimeout);
        if (result.cancelled) {
            // Replace paper and original tracks together; no history entry for cancellation.
            enabled = false;
            replaceNativeTracks();
            if (wanted) view.post(this::tryEnable);
        } else {
            // The private native accumulator is settled. Neither temporary trail enters
            // completed, onStrokeComplete, autosave, undo history, or PDF output.
            listener.onEraseGesture(result.path, result.radius);
        }
    }

    private void point(int action, float x, float y, long time, float pressure) {
        try {
            if (action == MotionEvent.ACTION_DOWN) {
                if (configuredStyle == null) throw new IllegalStateException("Pen style has not been configured");
                activePoints = new PointBuffer(configuredStyle);
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
                NotebookPage.Stroke stroke = completion.points.toStroke();
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
        eraserPreviewVisible = false;
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
        clearLasso(); selectionStream.reset();
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
        private final NotebookPenConfig.Style style;
        private float[] coordinates = new float[128 * 3];
        private long[] times = new long[128];
        private int size;

        PointBuffer() { this(null); } // Eraser capture only needs its point path.
        PointBuffer(NotebookPenConfig.Style style) { this.style = style; }

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

        NotebookPage.Stroke toStroke() {
            if (style == null) throw new IllegalStateException("Completed stroke has no captured pen style");
            return style.stroke(toPoints());
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
