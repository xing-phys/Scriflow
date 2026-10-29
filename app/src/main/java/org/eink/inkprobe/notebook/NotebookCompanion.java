package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Rect;
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
        void interact(); void menu(); void activate(); void figure(NotebookFigureHit.Hit hit); void collapse(); void boundsChanged();
        boolean beginMove(); void endMove(); void save(); boolean allowInput(); int writingRevision();
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
    private final NotebookResizeOutline resizeOutline;
    private Grip menuGrip, resizeGrip;

    NotebookCompanion(Context context, FrameLayout root, NotebookWorkspace workspace, NotebookWorkspace.Pane pane,
            NotebookSession session, Listener listener) {
        this.context = context; this.root = root; this.workspace = workspace; this.pane = pane; this.listener = listener;
        content = new FrameLayout(context); content.setPadding(dp(2), dp(2), dp(2), dp(2));
        resizeOutline = new NotebookResizeOutline(context);
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.WHITE); content.setBackground(background);
        android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
        border.setColor(Color.TRANSPARENT); border.setStroke(dp(2), Color.BLACK); content.setForeground(border);
        body = new FrameLayout(context); content.addView(body, new FrameLayout.LayoutParams(-1, -1));
        reader = new NotebookReader(context, session, pane, new NotebookReader.Listener() {
            public void interact() { listener.interact(); }
            public void activate() { listener.activate(); }
            public void figure(NotebookFigureHit.Hit hit) { listener.figure(hit); }
            public int writingRevision() { return listener.writingRevision(); }
            public void save() { listener.save(); }
            public boolean allowInput() { return listener.allowInput(); }
        });
        body.addView(reader, new FrameLayout.LayoutParams(-1, -1));
        menu = new NotebookIconButton(context, NotebookIconDrawable.Icon.MORE, "Note options; drag to move floating note");
        minimize = new NotebookIconButton(context, NotebookIconDrawable.Icon.MINIMIZE, "Minimize note; drag to resize floating note");
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
        content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            listener.boundsChanged(); finishResizeOutline();
        });
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
        menu.setContentDescription(name + ": options; drag to move floating note");
        minimize.setContentDescription(name + ": minimize; drag to resize floating note");
        boolean controls = workspace.panes().size() > 1;
        menu.setVisibility(controls ? View.VISIBLE : View.GONE); minimize.setVisibility(controls ? View.VISIBLE : View.GONE);
        position();
    }
    void retainEditorFrame(Bitmap frame) {
        editorPrepared = false; reader.retainFrame(frame);
        reader.setVisibility(View.VISIBLE); reader.bringToFront();
    }

    void editorReady() {
        if (!editor || editorPrepared || resizeGrip.moving) return;
        editorPrepared = true;
        content.postOnAnimation(() -> {
            if (!editor || !editorPrepared) return;
            reader.setVisibility(View.GONE); reader.hide(); finishResizeOutline();
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
            else if (!NotebookPopupHost.show(popup, root, Gravity.TOP | Gravity.LEFT, x, y)) { visible = false; listener.boundsChanged(); return; }
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
        visible = true; listener.boundsChanged(); finishResizeOutline();
    }
    private void finishResizeOutline() {
        if (resizeGrip.moving || !resizeOutline.showing() || !workspace.panes().contains(pane) || editor && !editorPrepared) return;
        NotebookWorkspace.Box box = workspace.box(pane, root.getWidth(), root.getHeight());
        if (content.getWidth() != box.width || content.getHeight() != box.height) return;
        content.postOnAnimation(() -> {
            if (!visible || !resizeOutline.showing() || !workspace.panes().contains(pane)) return;
            NotebookWorkspace.Box current = workspace.box(pane, root.getWidth(), root.getHeight());
            if (!resizeGrip.moving && (!editor || editorPrepared)
                    && content.getWidth() == current.width && content.getHeight() == current.height) resizeOutline.hide();
        });
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
        final NotebookGripGesture gesture = new NotebookGripGesture(ViewConfiguration.get(context).getScaledTouchSlop());
        float downX, downY, startX, startY, startW, startH;
        boolean moving, rejected;
        Grip(boolean resize) { this.resize = resize; }
        @Override public boolean onTouch(View view, MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                downX = event.getRawX(); downY = event.getRawY();
                startX = pane.x; startY = pane.y; startW = pane.width; startH = pane.height;
                moving = rejected = false; gesture.begin(downX, downY); return true;
            }
            if (action == MotionEvent.ACTION_MOVE) {
                begin(event.getRawX(), event.getRawY());
                if (moving) update(event.getRawX(), event.getRawY());
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                if (action == MotionEvent.ACTION_UP) begin(event.getRawX(), event.getRawY());
                boolean click = gesture.finish(action == MotionEvent.ACTION_CANCEL);
                boolean wasMoving = moving;
                if (wasMoving) {
                    if (action == MotionEvent.ACTION_CANCEL) { restore(); resizeOutline.hide(); }
                    else update(event.getRawX(), event.getRawY());
                    moving = false; listener.save(); listener.endMove();
                } else if (click && !rejected && action == MotionEvent.ACTION_UP) view.performClick();
                return true;
            }
            return true;
        }
        private void begin(float x, float y) {
            if (gesture.move(x, y) && !moving && !rejected && workspace.floating(pane)) {
                moving = listener.beginMove(); rejected = !moving;
                if (moving && resize && editor) editorPrepared = false;
                if (moving && !resize) resizeOutline.hide();
            }
        }
        private void update(float x, float y) {
            float dx = x - downX, dy = y - downY;
            if (resize) {
                float left = startX * (1 - startW), top = startY * (1 - startH);
                pane.width = NotebookWorkspace.clamp(startW + dx / Math.max(1, root.getWidth()), .3f, Math.min(.95f, 1 - left));
                pane.height = NotebookWorkspace.clamp(startH + dy / Math.max(1, root.getHeight()), .3f, Math.min(.95f, 1 - top));
                pane.x = NotebookWorkspace.clamp(left / (1 - pane.width), 0, 1);
                pane.y = NotebookWorkspace.clamp(top / (1 - pane.height), 0, 1);
                resizeOutline.show(root, workspace.box(pane, root.getWidth(), root.getHeight()));
            } else {
                pane.x = NotebookWorkspace.clamp(startX + dx / Math.max(1, root.getWidth() * (1 - pane.width)), 0, 1);
                pane.y = NotebookWorkspace.clamp(startY + dy / Math.max(1, root.getHeight() * (1 - pane.height)), 0, 1);
                position();
            }
        }
        private void restore() { pane.x = startX; pane.y = startY; pane.width = startW; pane.height = startH; }
        void cancel() {
            if (moving) restore();
            moving = false; gesture.finish(true); resizeOutline.hide();
        }
    }
    void setCacheBudget(NotebookCacheBudget budget) { reader.setCacheBudget(budget); }
    void trimCaches() { reader.trimCaches(); }
    void hide() {
        menuGrip.cancel(); resizeGrip.cancel(); resizeOutline.hide(); reader.hide(); visible = editorPrepared = false;
        popup.dismiss(); if (content.getParent() == root) root.removeView(content);
        listener.boundsChanged();
    }
    void close() { hide(); reader.close(); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
