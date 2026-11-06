package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import java.util.ArrayList;
import java.util.List;

/** Border-only note frame with two overlaid corner controls and one shared native editor. */
final class NotebookCompanion {
    interface Listener {
        void lookup(String word); void interact(); void menu(); void activate(); void figure(NotebookFigureHit.Hit hit); void collapse(); void boundsChanged();void raised(); void history(boolean forward);
        boolean shouldRaise();void raiseEditor();long stackRevision();
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
    private final NotebookIconButton historyBack, historyForward;
    private final LinearLayout history;
    private final NotebookReader reader;
    private boolean visible, editor, editorPrepared;
    private int popupLayer = -1;
    private final NotebookWindowControls controls;

    NotebookCompanion(Context context, FrameLayout root, NotebookWorkspace workspace, NotebookWorkspace.Pane pane,
            NotebookSession session, Listener listener) {
        this.context = context; this.root = root; this.workspace = workspace; this.pane = pane; this.listener = listener;
        content = new FrameLayout(context) {
            private boolean raiseAfterContact;
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                int action=event.getActionMasked();
                if(action==android.view.MotionEvent.ACTION_DOWN)
                    raiseAfterContact=(!editor||event.getToolType(event.getActionIndex())==android.view.MotionEvent.TOOL_TYPE_FINGER)&&listener.allowInput();
                try{return super.dispatchTouchEvent(event);}finally{
                    if(action==android.view.MotionEvent.ACTION_UP||action==android.view.MotionEvent.ACTION_CANCEL){
                        boolean raise=raiseAfterContact&&action==android.view.MotionEvent.ACTION_UP;raiseAfterContact=false;
                        if(raise){long revision=listener.stackRevision();post(()->{if(revision==listener.stackRevision())raiseFloating();});}
                    }
                }
            }
        }; content.setPadding(dp(2), dp(2), dp(2), dp(2));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(Color.WHITE); content.setBackground(background);
        android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
        border.setColor(Color.TRANSPARENT); border.setStroke(dp(2), Color.BLACK); content.setForeground(border);
        body = new FrameLayout(context); content.addView(body, new FrameLayout.LayoutParams(-1, -1));
        reader = new NotebookReader(context, session, pane, new NotebookReader.Listener() {
            public void interact() { listener.interact(); }
            public void textBoundsChanged() { listener.boundsChanged(); }
            public void lookup(String word) { listener.lookup(word); }
            public void activate() { listener.activate(); }
            public void figure(NotebookFigureHit.Hit hit) { listener.figure(hit); }
            public int writingRevision() { return listener.writingRevision(); }
            public void save() { listener.save(); }
            public boolean allowInput() { return listener.allowInput(); }
        });
        body.addView(reader, new FrameLayout.LayoutParams(-1, -1));
        controls=new NotebookWindowControls(context,root,content,workspace,pane,new NotebookWindowControls.Listener(){
            public void menu(){listener.menu();}public void minimize(){listener.collapse();}
            public boolean beginMove(boolean resize){boolean allowed=listener.beginMove();if(allowed&&resize&&editor)editorPrepared=false;return allowed;}
            public void endMove(){listener.endMove();}public void save(){listener.save();}
            public void position(){NotebookCompanion.this.position();}public boolean ready(){return visible&&(!editor||editorPrepared);}
        },"note");menu=controls.menu;minimize=controls.minimize;
        history = new LinearLayout(context); history.setOrientation(LinearLayout.HORIZONTAL);
        historyBack = new NotebookIconButton(context, NotebookIconDrawable.Icon.VIEW_BACK, "Previous view");
        historyForward = new NotebookIconButton(context, NotebookIconDrawable.Icon.VIEW_FORWARD, "Next view");
        historyBack.setOnClickListener(v -> listener.history(false)); historyForward.setOnClickListener(v -> listener.history(true));
        history.addView(historyBack, new LinearLayout.LayoutParams(dp(22), dp(22)));
        LinearLayout.LayoutParams next = new LinearLayout.LayoutParams(dp(22), dp(22)); next.leftMargin=dp(4);
        history.addView(historyForward, next);
        FrameLayout.LayoutParams historyPosition = new FrameLayout.LayoutParams(-2, dp(22), Gravity.BOTTOM | Gravity.RIGHT);
        historyPosition.setMargins(0, 0, dp(31), dp(5)); content.addView(history, historyPosition);
        history.setVisibility(View.GONE);
        history.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> listener.boundsChanged());
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
        boolean controls = workspace.panes().size() > 1 || workspace.floatingBase();
        menu.setVisibility(controls ? View.VISIBLE : View.GONE); minimize.setVisibility(controls ? View.VISIBLE : View.GONE);
        position();
    }
    void retainEditorFrame(Bitmap frame) {
        editorPrepared = false; reader.retainFrame(frame);
        reader.setVisibility(View.VISIBLE); reader.bringToFront();
    }
    void refreshReadingSettings(){reader.refreshReadingSettings();}
    void history(boolean back, boolean forward) {
        boolean show = workspace.floating(pane) && (back || forward);
        historyBack.setVisibility(back ? View.VISIBLE : View.GONE);
        historyForward.setVisibility(forward ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams next = (LinearLayout.LayoutParams) historyForward.getLayoutParams();
        int margin = back && forward ? dp(4) : 0;
        if (next.leftMargin != margin) { next.leftMargin = margin; historyForward.setLayoutParams(next); }
        history.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    void editorReady() {
        if (!editor || editorPrepared || controls.resizing()) return;
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
            int layer = workspace.floatingBase() ? android.view.WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
                : android.view.WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;
            if (layer != popupLayer) { if (popup.isShowing()) popup.dismiss(); popupLayer = layer; popup.setWindowLayoutType(layer); }
            if (content.getParent() == root) root.removeView(content);
            popup.setWidth(Math.max(1, box.width)); popup.setHeight(Math.max(1, box.height));
            if (popup.isShowing()) popup.update(x, y, box.width, box.height);
            else if (!NotebookPopupHost.show(popup, root, Gravity.TOP | Gravity.LEFT, x, y)) { visible = false; listener.boundsChanged(); return; }
            else listener.raised();
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
    private void raiseFloating() {
        if(!visible||!workspace.floating(pane)||!popup.isShowing()||!listener.allowInput()||!listener.shouldRaise())return;
        if(editor)listener.raiseEditor();else raisePopup();
    }
    /** The active editor is reparented only after its owner pauses native ink. */
    void raisePopup(){if(visible&&workspace.floating(pane)&&popup.isShowing()){popup.dismiss();position();}}
    private void finishResizeOutline() { controls.presented(); }
    List<Rect> exclusions() {
        List<Rect> bounds = new ArrayList<>();
        if (!visible) return bounds;
        bounds.add(reader.textBounds());
        if (!editor && workspace.floating(pane)) bounds.add(screenBounds(content));
        else if (editor) {
            if (menu.getVisibility() == View.VISIBLE) bounds.add(screenBounds(menu));
            if (minimize.getVisibility() == View.VISIBLE) bounds.add(screenBounds(minimize));
            if (history.getVisibility() == View.VISIBLE) bounds.add(screenBounds(history));
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
    void setCacheBudget(NotebookCacheBudget budget) { reader.setCacheBudget(budget); }
    void trimCaches() { reader.trimCaches(); }
    void hide() {
        controls.cancel(); reader.hide(); visible = editorPrepared = false;
        popup.dismiss(); if (content.getParent() == root) root.removeView(content);
        listener.boundsChanged();
    }
    void close() { hide(); reader.close(); }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
