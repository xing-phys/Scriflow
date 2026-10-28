package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.HashMap;
import java.util.Map;

/** A read pane or the floating editor's frame. The Activity owns the single shared ink/toolbox. */
final class NotebookCompanion {
    interface Listener {
        void menu(); void activate(); void collapse(); void bounds(Rect rect); void navigate(int delta);
        boolean beginMove(); void endMove(); void save();
    }
    static final class ReadingView {
        final float zoom, centerX, centerY;
        ReadingView(float zoom, float centerX, float centerY) {
            this.zoom = zoom; this.centerX = centerX; this.centerY = centerY;
        }
    }
    private final Context context;
    private final FrameLayout root, body;
    private final NotebookWorkspace state;
    private final NotebookSession session;
    private final Listener listener;
    private final boolean editorWindow;
    private final LinearLayout content;
    private final PopupWindow popup;
    private final TextView title, handle;
    private final PageView pageView;
    private final Map<Long, ReadingView> reading = new HashMap<>();
    private NotebookSession.PreviewRequest request;
    private int count = 1, serial, displayedBook, displayedPage;
    private String name;
    private boolean moving, visible;

    NotebookCompanion(Context context, FrameLayout root, NotebookWorkspace state, NotebookSession session,
            Listener listener, boolean editorWindow) {
        this.context = context; this.root = root; this.state = state; this.session = session;
        this.listener = listener; this.editorWindow = editorWindow;
        content = new LinearLayout(context); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(2, 2, 2, 2);
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.WHITE); background.setStroke(2, Color.BLACK); content.setBackground(background);
        title = new TextView(context); title.setTextColor(Color.BLACK); title.setTextSize(13); title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END); title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(dp(6), 0, dp(6), 0); title.setContentDescription("Note title; drag to move floating window");
        content.addView(title, new LinearLayout.LayoutParams(-1, dp(32)));
        LinearLayout controls = new LinearLayout(context); content.addView(controls);
        button(controls, "‹", "Previous page", () -> turn(-1));
        button(controls, "›", "Next page", () -> turn(1));
        button(controls, editorWindow ? "Active" : "Activate", editorWindow ? "This note is active" : "Activate this note in place",
            () -> { if (!editorWindow) listener.activate(); });
        button(controls, "−", "Collapse second note", listener::collapse);
        button(controls, "⋮", "Workspace position and size", listener::menu);
        body = new FrameLayout(context); content.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        pageView = new PageView(context);
        if (!editorWindow) body.addView(pageView, new FrameLayout.LayoutParams(-1, -1));
        handle = new TextView(context); handle.setTextSize(12); handle.setGravity(Gravity.RIGHT); handle.setTextColor(Color.BLACK);
        handle.setContentDescription(editorWindow ? "Drag to resize floating window" : "Tap to fit page; drag to resize floating window");
        content.addView(handle, new LinearLayout.LayoutParams(-1, dp(28)));
        handle.setOnClickListener(v -> { if (!editorWindow) { pageView.reset(); rememberDisplayed(); } });
        title.setOnTouchListener(drag(false)); handle.setOnTouchListener(drag(true));
        popup = new PopupWindow(content, 1, 1, false);
        popup.setTouchable(true); popup.setOutsideTouchable(false); popup.setClippingEnabled(false);
        popup.setIsLaidOutInScreen(true); popup.setAnimationStyle(0); popup.setElevation(0);
        popup.setEnterTransition(null); popup.setExitTransition(null);
    }
    void showEditor(String name, View editor) {
        if (!editorWindow) throw new IllegalStateException("Not an editor frame");
        if (editor.getParent() != body) {
            if (editor.getParent() != null) ((ViewGroup) editor.getParent()).removeView(editor);
            body.addView(editor, new FrameLayout.LayoutParams(-1, -1));
        }
        show(name);
    }
    void show(String name) {
        this.name = name;
        if (!root.isAttachedToWindow() || root.getWidth() < 1) return;
        boolean needsRead = !visible || displayedBook != state.notebookId || displayedPage != state.page;
        position();
        if (editorWindow) title.setText("Active · " + name);
        else if (needsRead) load();
    }
    private boolean floats() { return state.position == NotebookWorkspace.Position.FLOATING && (editorWindow || !state.secondaryActive); }
    void position() {
        if (state.notebookId <= 0 || state.collapsed || !root.isAttachedToWindow()) { hide(); return; }
        NotebookWorkspace.Box box = editorWindow ? state.secondary(root.getWidth(), root.getHeight()) : state.companion(root.getWidth(), root.getHeight());
        int[] location = new int[2]; root.getLocationOnScreen(location);
        int x = location[0] + box.x, y = location[1] + box.y;
        if (floats()) {
            if (content.getParent() == root) root.removeView(content);
            popup.setWidth(Math.max(1, box.width)); popup.setHeight(Math.max(1, box.height));
            if (popup.isShowing()) popup.update(x, y, box.width, box.height);
            else popup.showAtLocation(root, Gravity.TOP | Gravity.LEFT, x, y);
        } else {
            if (popup.isShowing()) popup.dismiss();
            if (content.getParent() != root) {
                if (content.getParent() != null) ((ViewGroup) content.getParent()).removeView(content);
                root.addView(content);
            }
            FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(box.width, box.height);
            layout.leftMargin = box.x; layout.topMargin = box.y; content.setLayoutParams(layout);
        }
        visible = true;
        handle.setText(editorWindow ? "Resize ↘" : floats() ? "Fit page                  Resize ↘" : "Fit page");
        // Embedded reading content sits behind the active floating editor; exclude only an overlay.
        listener.bounds(!editorWindow && floats() ? new Rect(x, y, x + box.width, y + box.height) : new Rect());
    }
    private void button(LinearLayout row, String label, String description, Runnable action) {
        NotebookButton button = new NotebookButton(context); button.setText(label); button.setTextSize(12); button.setContentDescription(description);
        button.setOnClickListener(v -> action.run()); row.addView(button, new LinearLayout.LayoutParams(0, dp(36), 1));
    }
    private void turn(int delta) {
        if (editorWindow) { listener.navigate(delta); return; }
        int next = Math.max(1, Math.min(count, state.page + delta));
        if (next == state.page) return;
        rememberDisplayed(); state.page = next; listener.save(); load();
    }
    private static long key(int book, int page) { return ((long) book << 32) | page; }
    void remember(int book, int page, ReadingView view) { reading.put(key(book, page), view); }
    ReadingView readingView() {
        return pageView.bitmap == null ? reading.get(key(state.notebookId, state.page)) : pageView.capture();
    }
    private void rememberDisplayed() {
        if (!editorWindow && displayedBook > 0 && pageView.bitmap != null)
            remember(displayedBook, displayedPage, pageView.capture());
    }
    private void load() {
        rememberDisplayed();
        if (request != null) request.cancel();
        int token = ++serial;
        displayedBook = state.notebookId; displayedPage = state.page;
        ReadingView saved = reading.get(key(displayedBook, displayedPage));
        pageView.restore(saved); title.setText("Read · " + name + " · " + state.page + " / " + count);
        pageView.bitmap = null; pageView.message = "Loading…"; pageView.invalidate();
        request = session.previewCompanion(state.notebookId, state.page, (bitmap, pages, error) -> {
            if (token != serial || !visible) return;
            count = pages;
            if (state.page > count) { state.page = Math.max(1, count); load(); return; }
            title.setText("Read · " + name + " · " + state.page + " / " + count);
            pageView.bitmap = bitmap;
            pageView.message = error == null ? "" : "Preview unavailable. Reopen this note to retry.";
            pageView.invalidate();
        });
    }
    private View.OnTouchListener drag(boolean resize) {
        return new View.OnTouchListener() {
            float downX, downY, startX, startY, startW, startH;
            boolean dragged;
            @Override public boolean onTouch(View view, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    downX = event.getRawX(); downY = event.getRawY(); startX = state.x; startY = state.y;
                    startW = state.width; startH = state.height; dragged = false;
                    if (floats()) moving = listener.beginMove();
                    return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_MOVE && moving) {
                    float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                    dragged |= Math.abs(dx) + Math.abs(dy) > dp(6);
                    if (resize) {
                        state.width = NotebookWorkspace.clamp(startW + dx / root.getWidth(), .3f, .95f);
                        state.height = NotebookWorkspace.clamp(startH + dy / root.getHeight(), .3f, .95f);
                    } else {
                        state.x = NotebookWorkspace.clamp(startX + dx / Math.max(1, root.getWidth() * (1 - state.width)), 0, 1);
                        state.y = NotebookWorkspace.clamp(startY + dy / Math.max(1, root.getHeight() * (1 - state.height)), 0, 1);
                    }
                    position(); return true;
                }
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    if (moving && event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                        state.x = startX; state.y = startY; state.width = startW; state.height = startH; position();
                    }
                    if (moving) { moving = false; listener.save(); listener.endMove(); }
                    if (resize && !dragged && event.getActionMasked() == MotionEvent.ACTION_UP) view.performClick();
                    return true;
                }
                return true;
            }
        };
    }
    void hide() {
        rememberDisplayed(); visible = false;
        ++serial; if (request != null) { request.cancel(); request = null; }
        popup.dismiss();
        if (content.getParent() == root) root.removeView(content);
        listener.bounds(new Rect());
    }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private static final class PageView extends View {
        Bitmap bitmap;
        String message = "Loading…";
        float zoom = 1, centerX = .5f, centerY = .5f, lastX, lastY;
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final RectF destination = new RectF();
        final ScaleGestureDetector scale;
        PageView(Context context) {
            super(context); setContentDescription("Inactive note page; pinch to zoom and drag to pan without activating");
            scale = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override public boolean onScale(ScaleGestureDetector detector) {
                    zoom = NotebookWorkspace.clamp(zoom * detector.getScaleFactor(), .25f, 8); invalidate(); return true;
                }
            });
        }
        ReadingView capture() { return new ReadingView(zoom, centerX, centerY); }
        void restore(ReadingView view) { zoom = view == null ? 1 : view.zoom; centerX = view == null ? .5f : view.centerX; centerY = view == null ? .5f : view.centerY; }
        void reset() { restore(null); invalidate(); }
        float fit() { return bitmap == null ? 1 : Math.min(getWidth() / (float) bitmap.getWidth(), getHeight() / (float) bitmap.getHeight()); }
        @Override protected void onDraw(Canvas canvas) {
            canvas.drawColor(0xffeeeeee);
            if (bitmap == null) {
                paint.setColor(Color.BLACK); paint.setTextSize(14 * getResources().getDisplayMetrics().scaledDensity);
                canvas.drawText(message, 12, getHeight() / 2f, paint); return;
            }
            float width = bitmap.getWidth() * fit() * zoom, height = bitmap.getHeight() * fit() * zoom;
            float halfX = Math.min(.5f, getWidth() / (2 * width)), halfY = Math.min(.5f, getHeight() / (2 * height));
            centerX = NotebookWorkspace.clamp(centerX, halfX, 1 - halfX); centerY = NotebookWorkspace.clamp(centerY, halfY, 1 - halfY);
            float x = getWidth() / 2f - width * centerX, y = getHeight() / 2f - height * centerY;
            destination.set(x, y, x + width, y + height); canvas.drawBitmap(bitmap, null, destination, paint);
        }
        @Override public boolean performClick() { super.performClick(); return true; }
        @Override public boolean onTouchEvent(MotionEvent event) {
            scale.onTouchEvent(event);
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { lastX = event.getX(); lastY = event.getY(); }
            else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (bitmap != null && !scale.isInProgress() && event.getPointerCount() == 1) {
                    centerX -= (event.getX() - lastX) / Math.max(1, bitmap.getWidth() * fit() * zoom);
                    centerY -= (event.getY() - lastY) / Math.max(1, bitmap.getHeight() * fit() * zoom); invalidate();
                }
                lastX = event.getX(); lastY = event.getY();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
            return true;
        }
    }
}
