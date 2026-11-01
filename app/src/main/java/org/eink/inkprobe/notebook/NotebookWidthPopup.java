package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/** A persistent compact bar; outside input keeps its original stream in the editor window. */
final class NotebookWidthPopup {
    interface Listener {
        boolean onContactStart();
        void onWidthChanged(float width);
        void onContactEnd();
        void onClosed();
        void onBoundsChanged(Rect bounds);
        default void onEraserModeChanged(boolean region){}
    }

    private final Activity activity;
    private final SliderView content;
    private final FrameLayout container;
    private final NotebookIconButton strokeMode,regionMode;
    private final PopupWindow popup;
    private final NotebookPenSliderClose closing;
    private final NotebookWidthRange range;
    private final Listener listener;
    private final Rect bounds = new Rect();
    private boolean cancelAfterContact;
    private boolean modeContactActive,modeContactAccepted;
    private boolean region;
    private View canvasAnchor,buttonAnchor;
    private Rect toolsAnchor;
    private boolean verticalAnchor;

    NotebookWidthPopup(Activity activity, NotebookWidthRange range, float width, float pageScale, Listener listener) {
        this(activity,range,width,pageScale,false,listener);
    }
    NotebookWidthPopup(Activity activity, NotebookWidthRange range, float width, float pageScale, boolean region, Listener listener) {
        this.activity = activity; this.range = range; this.listener = listener;
        this.region=range==NotebookWidthRange.ERASER&&region;
        content = new SliderView(width, pageScale);
        container=new FrameLayout(activity);container.setBackground(null);
        if(range==NotebookWidthRange.ERASER){
            LinearLayout modes=new ModeRow(activity);
            strokeMode=new NotebookIconButton(activity,NotebookIconDrawable.Icon.ERASER,"Stroke eraser");
            regionMode=new NotebookIconButton(activity,NotebookIconDrawable.Icon.REGION_ERASER,"Region eraser; draw a dashed loop to erase whole selected strokes");
            for(NotebookIconButton button:new NotebookIconButton[]{strokeMode,regionMode})button.setTooltipText(button.getContentDescription());
            strokeMode.setOnClickListener(v->chooseMode(false));regionMode.setOnClickListener(v->chooseMode(true));
            modes.addView(strokeMode,new LinearLayout.LayoutParams(dp(32),dp(32)));
            LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(dp(32),dp(32));item.leftMargin=dp(6);
            modes.addView(regionMode,item);container.addView(modes,new FrameLayout.LayoutParams(-2,dp(32)));
            updateModes();
        }else{strokeMode=regionMode=null;}
        container.addView(content,new FrameLayout.LayoutParams(-1,dp(48)));
        // Keep the editor's fullscreen/focus state. Back is handled by its Activity.
        popup = new PopupWindow(container, 0, 0, false);
        popup.setTouchable(true);
        // WATCH_OUTSIDE_TOUCH reports an outside DOWN without stealing it from the page.
        popup.setOutsideTouchable(true);
        popup.setTouchModal(false);
        popup.setTouchInterceptor((view, event) -> {
            if (event.getActionMasked() != MotionEvent.ACTION_OUTSIDE) return false;
            finish(null); return true;
        });
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        popup.setClippingEnabled(false);
        popup.setIsLaidOutInScreen(true);
        popup.setAnimationStyle(0);
        popup.setEnterTransition(null); popup.setExitTransition(null);
        popup.setElevation(0f);
        closing = new NotebookPenSliderClose(this::dismissWindow, ignored -> listener.onClosed());
        popup.setOnDismissListener(this::windowDismissed);
    }

    private void updateModes(){strokeMode.setToolSelected(!region);regionMode.setToolSelected(region);}
    private void chooseMode(boolean value){
        if(region==value||cancelAfterContact)return;
        boolean borrowed=modeContactActive&&modeContactAccepted;
        if(!borrowed&&!listener.onContactStart())return;
        try{
            region=value;listener.onEraserModeChanged(value);updateModes();
            show(canvasAnchor,buttonAnchor,toolsAnchor,verticalAnchor);
        }finally{if(!borrowed)listener.onContactEnd();}
    }

    void show(View canvas, View pen, Rect tools, boolean vertical) {
        canvasAnchor=canvas;buttonAnchor=pen;toolsAnchor=new Rect(tools);verticalAnchor=vertical;
        if (!canvas.isAttachedToWindow() || canvas.getWidth() <= 0 || canvas.getHeight() <= 0) {
            finish(null); return;
        }
        int[] origin = new int[2]; canvas.getLocationOnScreen(origin);
        Rect button = new Rect();
        if (pen.getLocalVisibleRect(button)) {
            int[] penOrigin = new int[2]; pen.getLocationOnScreen(penOrigin);
            button.offset(penOrigin[0] - origin[0], penOrigin[1] - origin[1]);
        } else {
            button.set(tools); button.offset(-origin[0], -origin[1]);
        }
        Rect palette = new Rect(tools); palette.offset(-origin[0], -origin[1]);
        NotebookPenSliderLayout.Box bar = NotebookPenSliderLayout.place(canvas.getWidth(), canvas.getHeight(),
            new NotebookPenSliderLayout.Box(button.left, button.top, Math.max(1, button.width()), Math.max(1, button.height())),
            new NotebookPenSliderLayout.Box(palette.left, palette.top, Math.max(1, palette.width()), Math.max(1, palette.height())),
            vertical, dp(region?70:224), dp(range==NotebookWidthRange.ERASER?(region?32:88):48), dp(6), dp(8));
        int offset=range==NotebookWidthRange.ERASER?dp(40):0;
        content.setVisibility(region?View.GONE:View.VISIBLE);
        FrameLayout.LayoutParams slider=(FrameLayout.LayoutParams)content.getLayoutParams();
        slider.topMargin=offset;slider.height=Math.max(1,bar.height-offset);content.setLayoutParams(slider);
        content.bar.set(0, 0, bar.width, Math.max(1,bar.height-offset));
        bounds.set(origin[0] + bar.left, origin[1] + bar.top, origin[0] + bar.right(), origin[1] + bar.bottom());
        // Install the native-ink hole before this window becomes visible.
        listener.onBoundsChanged(new Rect(bounds));
        popup.setWidth(bar.width); popup.setHeight(bar.height);
        try {
            if(popup.isShowing())popup.update(bounds.left,bounds.top,bar.width,bar.height);
            else if (!NotebookPopupHost.show(popup, canvas, Gravity.TOP | Gravity.LEFT, bounds.left, bounds.top)) finish(null);
        }
        catch (RuntimeException error) { finish(null); }
    }

    /** User cancellation waits for the last contact before returning native handwriting. */
    void cancel() {
        if (content.state.active()||modeContactActive) { cancelAfterContact = true; content.state.cancel(); }
        else finish(null);
    }

    /** Lifecycle shutdown already disables input, so it can dismiss immediately. */
    void dismiss() { finish(null); }

    Rect bounds() { return new Rect(bounds); }

    private void finish(Float width) { closing.finish(width); }
    private void dismissWindow() {
        if (popup.isShowing()) popup.dismiss(); else windowDismissed();
    }
    private void windowDismissed() {
        content.endContact();
        endModeContact();
        if (!bounds.isEmpty()) {
            bounds.setEmpty(); listener.onBoundsChanged(new Rect());
        }
        closing.dismissed();
    }
    private int dp(float value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    private void endModeContact(){
        if(!modeContactActive)return;
        boolean accepted=modeContactAccepted;modeContactActive=modeContactAccepted=false;
        if(accepted)listener.onContactEnd();
    }
    private final class ModeRow extends LinearLayout {
        ModeRow(Activity activity){super(activity);}
        @Override public boolean dispatchTouchEvent(MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){
                endModeContact();modeContactActive=true;modeContactAccepted=listener.onContactStart();
            }
            try{if(modeContactAccepted)super.dispatchTouchEvent(event);return true;}
            finally{
                if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                    endModeContact();if(cancelAfterContact)finish(null);
                }
            }
        }
    }

    private final class SliderView extends View {
        final NotebookPenSliderState state;
        final RectF bar = new RectF();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float pageScale;
        private boolean contactAccepted;

        SliderView(float width, float pageScale) {
            super(activity, null, 0);
            state = new NotebookPenSliderState(range, width); this.pageScale = pageScale;
            setFocusable(true); setClickable(true);
            setBackground(null); setForeground(null); setStateListAnimator(null);
            setDefaultFocusHighlightEnabled(false);
            setSoundEffectsEnabled(false); setHapticFeedbackEnabled(false);
            describe();
        }

        private boolean hasPreview() { return bar.width() >= dp(128); }
        private float railLeft() { return bar.left + Math.min(dp(16), bar.width() / 5f); }
        private float railRight() {
            return bar.right - (hasPreview() ? dp(56) : Math.min(dp(16), bar.width() / 5f));
        }
        private float fraction(float x) { return (x - railLeft()) / Math.max(1f, railRight() - railLeft()); }
        private void describe() {
            setContentDescription(range.name + " " + range.label(state.width())
                + "; slide to adjust, release to apply; touch outside to close");
        }

        @Override protected void onDraw(Canvas canvas) {
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
            canvas.drawRoundRect(bar, dp(10), dp(10), paint);
            paint.setColor(Color.BLACK); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1));
            canvas.drawRoundRect(bar, dp(10), dp(10), paint);
            canvas.save(); canvas.clipRect(bar);
            float left = railLeft(), right = railRight(), y = bar.centerY();
            float x = left + range.fraction(state.width()) * (right - left);
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setColor(0xff888888); paint.setStrokeWidth(dp(1));
            canvas.drawLine(left, y, right, y, paint);
            paint.setColor(Color.BLACK); paint.setStrokeWidth(dp(2));
            canvas.drawLine(left, y, x, y, paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, dp(8), paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(1.5f)); paint.setColor(Color.BLACK);
            canvas.drawCircle(x, y, dp(8), paint);
            paint.setStyle(Paint.Style.FILL); canvas.drawCircle(x, y, dp(2), paint);
            if (hasPreview()) {
                float previewX = bar.right - dp(25);
                if (range == NotebookWidthRange.ERASER) {
                    paint.setColor(NotebookEraserCommit.COLOR);
                    canvas.drawCircle(previewX, y - dp(6), dp(3) + dp(8) * range.fraction(state.width()), paint);
                } else {
                    paint.setStrokeWidth(Math.max(1f, Math.min(dp(6), state.width() * pageScale)));
                    canvas.drawLine(previewX - dp(11), y - dp(5), previewX + dp(11), y - dp(5), paint);
                }
                paint.setColor(Color.BLACK);
                paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(dp(11));
                canvas.drawText(range.label(state.width()), previewX, bar.bottom - dp(7), paint);
            }
            canvas.restore();
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked(), changed = event.getActionIndex();
            float before = state.width();
            if (action == MotionEvent.ACTION_DOWN) {
                contactAccepted = listener.onContactStart();
                state.begin(event.getPointerId(0), contactAccepted && bar.contains(event.getX(), event.getY()), fraction(event.getX()));
            } else if (action == MotionEvent.ACTION_MOVE) {
                int index = event.findPointerIndex(state.pointer());
                if (index >= 0) state.move(state.pointer(), fraction(event.getX(index)));
                else if (state.pointer() >= 0) state.cancel();
            } else if (action == MotionEvent.ACTION_POINTER_UP) {
                state.lift(event.getPointerId(changed), fraction(event.getX(changed)));
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                Float chosen = state.end(event.getPointerId(changed), fraction(event.getX(changed)),
                    action == MotionEvent.ACTION_CANCEL);
                if (chosen != null) listener.onWidthChanged(chosen);
                endContact();
                if (cancelAfterContact) finish(null);
            }
            if (before != state.width()) {
                describe();
                // Only this little bar changes during dragging; the page and toolbar stay static.
                invalidate((int) bar.left - 1, (int) bar.top - 1, (int) bar.right + 1, (int) bar.bottom + 1);
            }
            return true;
        }

        void endContact() {
            if (!contactAccepted) return;
            contactAccepted = false;
            listener.onContactEnd();
        }

        @Override public CharSequence getAccessibilityClassName() { return SeekBar.class.getName(); }
        @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,
                range.minimum, range.maximum, state.width()));
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
            int[] origin = new int[2]; getLocationOnScreen(origin);
            Rect bounds = new Rect(); bar.roundOut(bounds);
            Rect parent=new Rect(bounds);parent.offset(getLeft(),getTop());info.setBoundsInParent(parent);
            bounds.offset(origin[0], origin[1]); info.setBoundsInScreen(bounds);
        }
        @Override public boolean performAccessibilityAction(int action, Bundle args) {
            float width;
            if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId() && args != null) {
                width = args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, state.width());
            } else if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) width = state.width() + range.step;
            else if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) width = state.width() - range.step;
            else return super.performAccessibilityAction(action, args);
            if (state.active() || !NotebookPage.finite(width) || !listener.onContactStart()) return false;
            state.choose(width);
            listener.onWidthChanged(state.width());
            listener.onContactEnd();
            describe(); invalidate();
            return true;
        }
    }
}
