package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import java.util.ArrayList;
import java.util.List;

/** Border-only note frame with two overlaid corner controls and one shared native editor. */
final class NotebookCompanion {
    interface Listener {
        void menu(); void activate(); void collapse(); void boundsChanged();
        boolean beginMove(); void endMove(); void save(); boolean allowInput();
    }
    static final class ReadingView {
        final float zoom, centerX, centerY;
        ReadingView(float zoom, float centerX, float centerY) {
            this.zoom = zoom; this.centerX = centerX; this.centerY = centerY;
        }
    }
    private final Context context;
    private final FrameLayout root, content, body;
    private final NotebookWorkspace workspace;
    private final NotebookWorkspace.Pane pane;
    private final Listener listener;
    private final PopupWindow popup;
    private final NotebookIconButton menu, minimize;
    private final NotebookReader reader;
    private boolean visible, editor, editorPrepared;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Grip menuGrip, resizeGrip;

    NotebookCompanion(Context context, FrameLayout root, NotebookWorkspace workspace, NotebookWorkspace.Pane pane,
            NotebookSession session, Listener listener) {
        this.context = context; this.root = root; this.workspace = workspace; this.pane = pane; this.listener = listener;
        content = new FrameLayout(context); content.setPadding(1, 1, 1, 1);
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.WHITE); background.setStroke(1, Color.BLACK); content.setBackground(background);
        body = new FrameLayout(context); content.addView(body, new FrameLayout.LayoutParams(-1, -1));
        reader = new NotebookReader(context, session, pane, new NotebookReader.Listener() {
            public void activate() { listener.activate(); }
            public void save() { listener.save(); }
            public boolean allowInput() { return listener.allowInput(); }
        });
        body.addView(reader, new FrameLayout.LayoutParams(-1, -1));
        menu = new NotebookIconButton(context, NotebookIconDrawable.Icon.MORE, "Note options; hold and drag to move floating note");
        minimize = new NotebookIconButton(context, NotebookIconDrawable.Icon.MINIMIZE, "Minimize note; hold and drag to resize floating note");
        menu.setOnClickListener(v -> listener.menu()); minimize.setOnClickListener(v -> listener.collapse());
        FrameLayout.LayoutParams left = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.BOTTOM | Gravity.LEFT);
        left.setMargins(dp(5), 0, 0, dp(5)); content.addView(menu, left);
        FrameLayout.LayoutParams right = new FrameLayout.LayoutParams(dp(32), dp(32), Gravity.BOTTOM | Gravity.RIGHT);
        right.setMargins(0, 0, dp(5), dp(5)); content.addView(minimize, right);
        menuGrip = new Grip(false); resizeGrip = new Grip(true);
        menu.setOnTouchListener(menuGrip); minimize.setOnTouchListener(resizeGrip);
        popup = new PopupWindow(content, 1, 1, false);
        popup.setTouchable(true); popup.setOutsideTouchable(false); popup.setClippingEnabled(false);
        popup.setIsLaidOutInScreen(true); popup.setAnimationStyle(0); popup.setElevation(0);
        popup.setEnterTransition(null); popup.setExitTransition(null);
        content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> listener.boundsChanged());
    }
    void show(String name, View writingSurface) {
        if (pane.collapsed || !root.isAttachedToWindow() || root.getWidth() < 1) { hide(); return; }
        boolean editing = writingSurface != null;
        if (editing != editor) editorPrepared = false;
        editor = editing;
        if (editor) {
            if (writingSurface.getParent() != body) {
                if (writingSurface.getParent() != null) ((ViewGroup) writingSurface.getParent()).removeView(writingSurface);
                // Keep the existing reader above the native surface until a complete editable viewport is posted.
                body.addView(writingSurface, 0, new FrameLayout.LayoutParams(-1, -1));
            }
        } else { reader.setVisibility(View.VISIBLE); reader.bringToFront(); reader.open(); }
        menu.setContentDescription(name + ": options; hold and drag to move floating note");
        minimize.setContentDescription(name + ": minimize; hold and drag to resize floating note");
        boolean controls = workspace.panes().size() > 1;
        menu.setVisibility(controls ? View.VISIBLE : View.GONE); minimize.setVisibility(controls ? View.VISIBLE : View.GONE);
        position();
    }
    void retainEditorFrame(Bitmap frame) {
        editorPrepared = false; reader.retainFrame(frame);
        reader.setVisibility(View.VISIBLE); reader.bringToFront();
    }

    void editorReady() {
        if (!editor || editorPrepared) return;
        editorPrepared = true;
        content.postOnAnimation(() -> {
            if (!editor || !editorPrepared) return;
            reader.setVisibility(View.GONE); reader.hide();
        });
    }

    void position() {
        if (pane.collapsed || !root.isAttachedToWindow()) { hide(); return; }
        NotebookWorkspace.Box box = workspace.box(pane, root.getWidth(), root.getHeight());
        int[] location = new int[2]; root.getLocationOnScreen(location);
        int x = location[0] + box.x, y = location[1] + box.y;
        if (workspace.floating(pane)) {
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
            layout.leftMargin = box.x; layout.topMargin = box.y;
            FrameLayout.LayoutParams old = (FrameLayout.LayoutParams) content.getLayoutParams();
            if (old == null || old.width != layout.width || old.height != layout.height
                    || old.leftMargin != layout.leftMargin || old.topMargin != layout.topMargin) content.setLayoutParams(layout);
        }
        visible = true; listener.boundsChanged();
    }
    List<Rect> exclusions() {
        List<Rect> bounds = new ArrayList<>();
        if (!visible) return bounds;
        if (!editor && workspace.floating(pane)) bounds.add(screenBounds(content));
        else if (editor) {
            if (menu.getVisibility() == View.VISIBLE) bounds.add(screenBounds(menu));
            if (minimize.getVisibility() == View.VISIBLE) bounds.add(screenBounds(minimize));
        }
        return bounds;
    }
    private Rect screenBounds(View view) {
        int[] xy = new int[2]; view.getLocationOnScreen(xy);
        return new Rect(xy[0], xy[1], xy[0] + view.getWidth(), xy[1] + view.getHeight());
    }
    ReadingView readingView() {
        reader.remember(); return pane.hasView ? new ReadingView(pane.zoom, pane.centerX, pane.centerY) : null;
    }
    private final class Grip implements View.OnTouchListener {
        final boolean resize;
        float downX, downY, startX, startY, startW, startH;
        boolean moving, exceededSlop, held;
        final Runnable hold = () -> {
            held = true;
            if (workspace.floating(pane) && !exceededSlop) moving = listener.beginMove();
        };
        Grip(boolean resize) { this.resize = resize; }
        @Override public boolean onTouch(View view, MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downX = event.getRawX(); downY = event.getRawY();
                startX = pane.x; startY = pane.y; startW = pane.width; startH = pane.height;
                moving = exceededSlop = held = false;
                main.postDelayed(hold, ViewConfiguration.getLongPressTimeout()); return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                if (!moving && Math.hypot(dx, dy) > ViewConfiguration.get(context).getScaledTouchSlop()) {
                    exceededSlop = true; main.removeCallbacks(hold);
                }
                if (moving) {
                    if (resize) {
                        float left = startX * (1 - startW), top = startY * (1 - startH);
                        pane.width = NotebookWorkspace.clamp(startW + dx / Math.max(1, root.getWidth()), .3f, Math.min(.95f, 1 - left));
                        pane.height = NotebookWorkspace.clamp(startH + dy / Math.max(1, root.getHeight()), .3f, Math.min(.95f, 1 - top));
                        pane.x = NotebookWorkspace.clamp(left / (1 - pane.width), 0, 1);
                        pane.y = NotebookWorkspace.clamp(top / (1 - pane.height), 0, 1);
                    } else {
                        pane.x = NotebookWorkspace.clamp(startX + dx / Math.max(1, root.getWidth() * (1 - pane.width)), 0, 1);
                        pane.y = NotebookWorkspace.clamp(startY + dy / Math.max(1, root.getHeight() * (1 - pane.height)), 0, 1);
                    }
                    position();
                }
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                main.removeCallbacks(hold);
                boolean wasMoving = moving; moving = false;
                if (wasMoving) {
                    if (action == MotionEvent.ACTION_CANCEL) {
                        pane.x = startX; pane.y = startY; pane.width = startW; pane.height = startH; position();
                    }
                    listener.save(); listener.endMove();
                } else if (!held && !exceededSlop && action == MotionEvent.ACTION_UP) view.performClick();
                return true;
            }
            return true;
        }
        void cancel() { main.removeCallbacks(hold); moving = false; }
    }
    void hide() {
        menuGrip.cancel(); resizeGrip.cancel(); reader.hide(); visible = editorPrepared = false;
        popup.dismiss(); if (content.getParent() == root) root.removeView(content);
        listener.boundsChanged();
    }
    void close() { hide(); reader.close(); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
