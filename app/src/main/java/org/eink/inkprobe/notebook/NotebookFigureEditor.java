package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import java.util.*;

/** In-note selection overlay: existing viewport, nearby actions and per-gesture durable edits. */
final class NotebookFigureEditor extends FrameLayout {
    enum Action { COPY, CUT, DELETE, PASTE }
    interface Listener {
        void preview();
        List<Rect> exclusions();
        void fallback(Canvas canvas, NotebookFigure figure);
        void change(int source, UUID pageId, NotebookFigure before, int target, NotebookFigure after);
        void action(Action action, int page, UUID pageId, NotebookFigure figure);
        void done();
        void navigate(float dx, float dy, float factor, float x, float y);
        void navigationFinished();
    }
    private final FrameLayout anchor;
    private final PopupWindow popup;
    private final Listener listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path outline = new Path();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final NotebookFigureLayer images;
    private final EnumMap<Action, NotebookIconButton> buttons = new EnumMap<>(Action.class);
    private final NotebookIconButton rotate, resize;
    private final ScaleGestureDetector scaleGesture;
    private final float slop;
    private NotebookViewport viewport;
    private float scroll;
    private int page, storedPage, startPage, mode, pointer = -1;
    private UUID pageId;
    private NotebookFigure figure, stored, start;
    private float downX, downY, lastX, lastY, grabX, grabY;
    private boolean busy, awaitingSave, closed, moved, outside, navigating, imageReady, previewing, restorePending;
    private Action pressedAction;
    private boolean pressCandidate;
    private final Runnable edgeScroll = this::edgeScroll;

    NotebookFigureEditor(Context context, FrameLayout anchor, NotebookViewport viewport,
            Map<Integer, NotebookPage> pages, float scroll, int page, NotebookFigure figure, Listener listener) {
        super(context); this.anchor = anchor; this.listener = listener; this.viewport = viewport; this.scroll = scroll;
        this.page = storedPage = page; this.figure = stored = figure; pageId = pages.get(page).id;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        setWillNotDraw(false); setClickable(true); setBackground(null); setClipChildren(false);
        setContentDescription("Selected figure; drag to move between pages, lower-right handle resizes, upper-right handle rotates. Tap outside to finish.");
        images = new NotebookFigureLayer(new NotebookFigureLayer.Listener() {
            public void changed() { prepareImage(); invalidate(); }
            public void failed(java.io.IOException error) { invalidate(); }
        });
        addAction(Action.COPY, NotebookIconDrawable.Icon.COPY, "Copy figure");
        addAction(Action.CUT, NotebookIconDrawable.Icon.CUT, "Cut figure");
        addAction(Action.DELETE, NotebookIconDrawable.Icon.DELETE, "Delete figure");
        addAction(Action.PASTE, NotebookIconDrawable.Icon.PASTE, "Paste figure");
        buttons.get(Action.PASTE).setVisibility(GONE);
        rotate = handle(NotebookIconDrawable.Icon.ROTATE, "Drag to rotate figure freely", 3);
        resize = handle(NotebookIconDrawable.Icon.RESIZE, "Drag to resize figure", 2);
        scaleGesture = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                if (busy) return false;
                navigating = true; mode = 0; main.removeCallbacks(edgeScroll);
                listener.navigate(0, 0, detector.getScaleFactor(), detector.getFocusX(), detector.getFocusY());
                return true;
            }
        });
        popup = new PopupWindow(this, anchor.getWidth(), anchor.getHeight(), false);
        popup.setTouchable(false); popup.setFocusable(false); popup.setOutsideTouchable(false);
        popup.setClippingEnabled(false); popup.setIsLaidOutInScreen(true); popup.setAnimationStyle(0); popup.setElevation(0);
        popup.setEnterTransition(null); popup.setExitTransition(null);
        prepareImage();
    }
    private void addAction(Action action, NotebookIconDrawable.Icon icon, String name) {
        NotebookIconButton button = new NotebookIconButton(getContext(), icon, name);
        button.setTooltipText(name);
        button.setOnClickListener(v -> { if (!busy) listener.action(action, storedPage, pageId, stored); });
        buttons.put(action, button); addView(button, new FrameLayout.LayoutParams(dp(28), dp(28)));
    }
    private NotebookIconButton handle(NotebookIconDrawable.Icon icon, String name, int kind) {
        NotebookIconButton button = new NotebookIconButton(getContext(), icon, name);
        button.setOnTouchListener((v, event) -> {
            if (busy) return true;
            scaleGesture.onTouchEvent(event);
            int[] origin = new int[2]; getLocationOnScreen(origin);
            return gesture(event, event.getRawX() - origin[0], event.getRawY() - origin[1], kind);
        });
        addView(button, new FrameLayout.LayoutParams(dp(28), dp(28))); return button;
    }
    void show() {
        int[] origin = new int[2]; anchor.getLocationOnScreen(origin);
        if (!NotebookPopupHost.show(popup, anchor, Gravity.TOP | Gravity.LEFT, origin[0], origin[1])) listener.done();
    }
    void clipboard(boolean available) { buttons.get(Action.PASTE).setVisibility(available ? VISIBLE : GONE); placeControls(); }
    void update(NotebookViewport value, Map<Integer, NotebookPage> pages, float scroll) {
        if (closed) return;
        viewport = value; this.scroll = scroll;
        NotebookPage loaded = pages.get(storedPage);
        if (!awaitingSave && loaded != null && loaded.figures.contains(stored)) { pageId = loaded.id; busy = false; }
        int[] origin = new int[2]; anchor.getLocationOnScreen(origin);
        if (popup.isShowing()) popup.update(origin[0], origin[1], anchor.getWidth(), anchor.getHeight());
        prepareImage(); placeControls(); invalidate();
    }
    void acknowledged(int target, NotebookFigure value) {
        awaitingSave = false;
        previewing = false; restorePending = true;
        page = storedPage = target; figure = stored = value; pageId = null;
        prepareImage(); placeControls(); invalidate();
    }
    void saving() { busy = awaitingSave = true; placeControls(); }
    void failed() { busy = awaitingSave = previewing = false; restorePending = true; page = storedPage; figure = stored; placeControls(); invalidate(); }
    boolean omits(NotebookFigure value) { return previewing && stored.id.equals(value.id); }
    void nativeReady() { if (!busy && !previewing && !awaitingSave) { restorePending = false; invalidate(); } }
    boolean isBusy() { return busy || restorePending || pointer >= 0 || pressedAction != null; }
    void cancel() { if (!isBusy()) listener.done(); }
    void dismiss() {
        if (closed) return; closed = true; main.removeCallbacks(edgeScroll); images.close();
        popup.dismiss();
    }
    private void prepareImage() {
        if (viewport == null || closed) return;
        float scale = viewport.scale(page);
        imageReady = images.prepare(Collections.singletonList(new NotebookFigureLayer.Request(figure,
            figure.width * (double) scale, figure.height * (double) scale)));
    }
    @Override public void draw(Canvas canvas) {
        if (closed) return;
        int saved = canvas.save();
        int[] origin = new int[2]; anchor.getLocationOnScreen(origin);
        for (Rect bounds : listener.exclusions())
            if (!bounds.isEmpty()) canvas.clipOutRect(bounds.left - origin[0], bounds.top - origin[1], bounds.right - origin[0], bounds.bottom - origin[1]);
        super.draw(canvas); canvas.restoreToCount(saved);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (viewport == null || closed) return;
        float scale = viewport.scale(page);
        if (previewing || restorePending) {
            int saved = canvas.save(); canvas.translate(viewport.pageLeft(page), viewport.pageTop(page, scroll)); canvas.scale(scale, scale);
            if (imageReady) images.draw(canvas, figure); else listener.fallback(canvas, figure);
            canvas.restoreToCount(saved);
        }
        outline.reset();
        for (int i = 0; i < 4; i++) {
            float x = screenX(figure.cornerX(i)), y = screenY(figure.cornerY(i));
            if (i == 0) outline.moveTo(x, y); else outline.lineTo(x, y);
        }
        outline.close(); paint.setStyle(Paint.Style.STROKE); paint.setColor(Color.BLACK); paint.setStrokeWidth(dp(1));
        canvas.drawPath(outline, paint); paint.setStyle(Paint.Style.FILL);
    }
    private float screenX(float x) { return viewport.pageLeft(page) + x * viewport.scale(page); }
    private float screenY(float y) { return viewport.pageTop(page, scroll) + y * viewport.scale(page); }
    private void placeControls() {
        if (viewport == null) return;
        int count = buttons.get(Action.PASTE).getVisibility() == VISIBLE ? 4 : 3;
        float x = screenX(figure.left()), y = screenY(figure.top()) - dp(44);
        if (y < 0) y = screenY(figure.bottom()) + dp(16);
        float[] group = freePosition(x, y, dp(count * 30), dp(28)); x = group[0]; y = group[1];
        for (Action action : Action.values()) {
            NotebookIconButton button = buttons.get(action);
            if (button.getVisibility() == GONE) continue;
            position(button, x, y); button.setAvailable(!busy); x += dp(30);
        }
        float[] corner = freePosition(screenX(figure.cornerX(1)) - dp(14), screenY(figure.cornerY(1)) - dp(14), dp(28), dp(28));
        position(rotate, corner[0], corner[1]);
        corner = freePosition(screenX(figure.cornerX(2)) - dp(14), screenY(figure.cornerY(2)) - dp(14), dp(28), dp(28));
        position(resize, corner[0], corner[1]);
    }
    private float[] freePosition(float x, float y, int width, int height) {
        int[] origin = new int[2]; anchor.getLocationOnScreen(origin);
        List<Rect> blocked = listener.exclusions();
        List<float[]> candidates = new ArrayList<>(); candidates.add(new float[]{x, y});
        for (Rect r : blocked) if (!r.isEmpty()) {
            candidates.add(new float[]{r.left-origin[0]-width-2, y}); candidates.add(new float[]{r.right-origin[0]+2, y});
            candidates.add(new float[]{x, r.top-origin[1]-height-2}); candidates.add(new float[]{x, r.bottom-origin[1]+2});
        }
        float[] best = {Math.max(0, Math.min(Math.max(0, getWidth()-width), x)), Math.max(0, Math.min(Math.max(0, getHeight()-height), y))};
        double distance = Double.POSITIVE_INFINITY;
        for (float[] candidate : candidates) {
            candidate[0] = Math.max(0, Math.min(Math.max(0, getWidth()-width), candidate[0]));
            candidate[1] = Math.max(0, Math.min(Math.max(0, getHeight()-height), candidate[1]));
            boolean overlaps = false;
            for (Rect r : blocked) overlaps |= !r.isEmpty() && candidate[0]+width > r.left-origin[0] && candidate[0] < r.right-origin[0]
                && candidate[1]+height > r.top-origin[1] && candidate[1] < r.bottom-origin[1];
            double next = Math.pow(candidate[0]-x, 2)+Math.pow(candidate[1]-y, 2);
            if (!overlaps && next < distance) { best = candidate; distance = next; }
        }
        return best;
    }
    private void position(NotebookIconButton button, float x, float y) {
        FrameLayout.LayoutParams item = (FrameLayout.LayoutParams) button.getLayoutParams();
        item.leftMargin = Math.round(Math.max(0, Math.min(Math.max(0, getWidth() - dp(28)), x)));
        item.topMargin = Math.round(Math.max(0, Math.min(Math.max(0, getHeight() - dp(28)), y)));
        button.setLayoutParams(item);
    }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) { placeControls(); }
    @Override public boolean onTouchEvent(MotionEvent event) {
        if (busy || closed) return true;
        int action = event.getActionMasked();
        float x = event.getX(), y = event.getY();
        if (action == MotionEvent.ACTION_DOWN) {
            for (Action item : Action.values()) if (buttons.get(item).getVisibility() == VISIBLE && hits(buttons.get(item), x, y)) {
                pressedAction = item; pressCandidate = true; return true;
            }
        }
        if (pressedAction != null) {
            if (action == MotionEvent.ACTION_MOVE) pressCandidate &= hits(buttons.get(pressedAction), x, y);
            if (event.getPointerCount() > 1) pressCandidate = false;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                Action selected = pressedAction; pressedAction = null;
                if (action == MotionEvent.ACTION_UP && pressCandidate) buttons.get(selected).performClick();
            }
            return true;
        }
        scaleGesture.onTouchEvent(event);
        if (event.getPointerCount() > 1) {
            navigating = true; mode = 0; page = storedPage; figure = stored; main.removeCallbacks(edgeScroll);
            if (previewing) { previewing = false; restorePending = true; listener.preview(); }
            if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) {
                int remaining = event.getActionIndex() == 0 ? 1 : 0;
                lastX = event.getX(remaining); lastY = event.getY(remaining);
            }
            return true;
        }
        int forced = action == MotionEvent.ACTION_DOWN ? hits(rotate, x, y) ? 3 : hits(resize, x, y) ? 2 : 0 : 0;
        return gesture(event, x, y, forced);
    }
    private boolean hits(NotebookIconButton button, float x, float y) {
        FrameLayout.LayoutParams item = (FrameLayout.LayoutParams) button.getLayoutParams();
        return x >= item.leftMargin && x <= item.leftMargin + dp(28) && y >= item.topMargin && y <= item.topMargin + dp(28);
    }
    private boolean gesture(MotionEvent event, float x, float y, int forcedMode) {
        int action = event.getActionMasked();
        if (!NotebookPage.finite(x) || !NotebookPage.finite(y)) return true;
        if (action == MotionEvent.ACTION_DOWN) {
            pointer = event.getPointerId(0); start = figure; startPage = page; moved = navigating = false;
            downX = lastX = x; downY = lastY = y;
            float px = (x - viewport.pageLeft(page)) / viewport.scale(page);
            float py = (y - viewport.pageTop(page, scroll)) / viewport.scale(page);
            mode = forcedMode != 0 ? forcedMode : figure.contains(px, py) ? 1 : 0;
            outside = mode == 0; grabX = px - figure.centerX(); grabY = py - figure.centerY();
            if (mode != 0) { previewing = true; listener.preview(); }
            return true;
        }
        if (pointer < 0) return true;
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            mode = 0; navigating = true; main.removeCallbacks(edgeScroll); return true;
        }
        if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            moved |= Math.hypot(x - downX, y - downY) > slop;
            if (moved && !scaleGesture.isInProgress()) {
                if (mode == 0) listener.navigate(lastX - x, lastY - y, 1, x, y);
                else preview(x, y);
            }
            lastX = x; lastY = y;
        }
        if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
            main.removeCallbacks(edgeScroll); pointer = -1;
            listener.navigationFinished();
            boolean changed = action == MotionEvent.ACTION_UP && moved && mode != 0 && !navigating;
            if (changed) {
                figure = figure.fitted(viewport.pageWidth(page), viewport.pageHeight(page)); saving();
                listener.change(storedPage, pageId, stored, page, figure);
            } else {
                page = storedPage; figure = stored; placeControls(); invalidate();
                if (previewing) { previewing = false; restorePending = true; listener.preview(); }
                if (action == MotionEvent.ACTION_UP && outside && !moved && !navigating) listener.done();
            }
        }
        return true;
    }
    private void preview(float x, float y) {
        if (mode == 1) {
            page = viewport.nearestPage(y, scroll);
            figure = NotebookFigureGeometry.move(viewport, start, page, grabX, grabY, x, y, scroll);
            if (!main.hasCallbacks(edgeScroll)) main.postDelayed(edgeScroll, 40);
        } else {
            page = startPage;
            float px = (x - viewport.pageLeft(page)) / viewport.scale(page);
            float py = (y - viewport.pageTop(page, scroll)) / viewport.scale(page);
            if (mode == 2) figure = NotebookFigureGeometry.resize(start,
                start.cornerX(2) + (x - downX) / viewport.scale(page),
                start.cornerY(2) + (y - downY) / viewport.scale(page));
            else {
                float dx = (downX - viewport.pageLeft(page)) / viewport.scale(page);
                float dy = (downY - viewport.pageTop(page, scroll)) / viewport.scale(page);
                figure = NotebookFigureGeometry.rotate(start, dx, dy, px, py);
            }
        }
        prepareImage(); placeControls(); invalidate();
    }
    private void edgeScroll() {
        if (closed || busy || pointer < 0 || mode != 1) return;
        float step = NotebookSelectionMove.edgeScroll(lastY, viewport.height, dp(48), dp(22));
        if (step == 0) return;
        float previous = scroll;
        listener.navigate(0, step, 1, lastX, lastY);
        if (scroll != previous) preview(lastX, lastY);
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
