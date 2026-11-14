package org.eink.inkprobe.notebook;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;

/** Floating browser controls; collapsing or entering Zen never changes the page's bounds. */
final class NotebookBrowserTools extends FrameLayout {
    private static final long MOVE_INTERVAL_MS = 32;
    private final NotebookBrowserToolbarState state;
    private final SharedPreferences preferences;
    private final ScrollView panel;
    private final NotebookIconButton grip;
    private final NotebookGripGesture gesture;
    private final NotebookDeviceStatus normalStatus, zenClock;
    private final FrameLayout clockTarget;
    private boolean resumed, dragging;
    private int pointer = -1;
    private float startX, startY, startLeft, startTop, pendingLeft, pendingTop;
    private long lastMove;
    private final Runnable moveFrame = this::applyMove;

    NotebookBrowserTools(Context context, View controls, NotebookDeviceStatus normalStatus, boolean offline, Bundle saved) {
        super(context);
        this.normalStatus = normalStatus;
        preferences = context.getSharedPreferences("browser-ui", Context.MODE_PRIVATE);
        state = new NotebookBrowserToolbarState(saved == null ? preferences.getBoolean("collapsed", offline) : saved.getBoolean("collapsed"),
            saved == null ? preferences.getFloat("horizontal", 0) : saved.getFloat("horizontal"),
            saved == null ? preferences.getFloat("vertical", 0) : saved.getFloat("vertical"));
        if (saved != null) state.zen(saved.getBoolean("zen"));
        setBackground(null); setElevation(0); setStateListAnimator(null);
        panel = new ScrollView(context); panel.setBackground(NotebookUi.rounded(getResources().getDisplayMetrics().density, 7));
        panel.setOverScrollMode(OVER_SCROLL_NEVER); panel.setVerticalScrollBarEnabled(false);
        panel.setSmoothScrollingEnabled(false); panel.setClickable(true);
        panel.addView(controls, new ScrollView.LayoutParams(-1, -2));
        addView(panel, new FrameLayout.LayoutParams(-2, -2));
        grip = new NotebookIconButton(context, NotebookIconDrawable.Icon.MOVE, "Collapse browser controls; drag to move");
        gesture = new NotebookGripGesture(ViewConfiguration.get(context).getScaledTouchSlop());
        grip.setOnClickListener(view -> collapse(!state.collapsed()));
        grip.setOnTouchListener((view, event) -> drag(event));
        addView(grip, new FrameLayout.LayoutParams(dp(36), dp(36)));
        zenClock = new NotebookDeviceStatus(context, () -> true); zenClock.setZenMode(true);
        zenClock.setClickable(false); zenClock.setFocusable(false); zenClock.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        clockTarget = new FrameLayout(context); clockTarget.setPadding(dp(6), dp(4), dp(6), dp(4));
        clockTarget.setBackgroundColor(Color.WHITE); clockTarget.setFocusable(true);
        clockTarget.setContentDescription("Clock; tap to exit Zen mode"); clockTarget.setOnClickListener(view -> zen(false));
        clockTarget.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, android.view.accessibility.AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setContentDescription(zenClock.getContentDescription().toString().replace("tap to open settings", "tap to exit Zen mode"));
            }
        });
        clockTarget.addView(zenClock, new FrameLayout.LayoutParams(-2, -2));
        FrameLayout.LayoutParams clockSpace = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        clockSpace.topMargin = dp(6); clockSpace.setMarginEnd(dp(6)); addView(clockTarget, clockSpace);
        present();
    }

    boolean isZen() { return state.zen(); }
    boolean isCollapsed() { return state.collapsed(); }

    void collapse(boolean value) {
        stopDrag(); state.collapse(value);
        if (value) hideKeyboard();
        present(); remember();
    }

    void zen(boolean value) {
        stopDrag(); state.zen(value);
        if (value) hideKeyboard();
        present();
    }

    void reveal() { state.zen(false); collapse(false); }

    void resumed(boolean value) {
        resumed = value;
        if (!value) stopDrag();
        updateClocks();
    }

    Bundle save() {
        Bundle saved = new Bundle(); saved.putBoolean("collapsed", state.collapsed()); saved.putBoolean("zen", state.zen());
        saved.putFloat("horizontal", state.horizontal()); saved.putFloat("vertical", state.vertical()); return saved;
    }

    void dispose() { resumed = false; stopDrag(); updateClocks(); }

    private void present() {
        panel.setVisibility(state.controlsVisible() ? VISIBLE : GONE);
        grip.setVisibility(state.gripVisible() ? VISIBLE : GONE);
        grip.setContentDescription((state.collapsed() ? "Expand" : "Collapse") + " browser controls; drag to move");
        clockTarget.setVisibility(state.zen() ? VISIBLE : GONE);
        updateClocks(); place();
    }

    private void updateClocks() {
        if (resumed && state.controlsVisible()) normalStatus.start(); else normalStatus.stop();
        if (resumed && state.zen()) zenClock.start(); else zenClock.stop();
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh); post(this::place);
    }

    @Override protected void onDetachedFromWindow() { dispose(); super.onDetachedFromWindow(); }

    private void place() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        int width = Math.max(1, Math.min(dp(560), getWidth() - dp(16)));
        panel.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        NotebookBrowserToolbarState.Layout layout = state.layout(getWidth(), getHeight(), dp(36), width,
            panel.getMeasuredHeight(), dp(8), dp(6));
        size(grip, layout.gripSize, layout.gripSize); grip.setTranslationX(layout.gripLeft); grip.setTranslationY(layout.gripTop);
        size(panel, layout.panelWidth, layout.panelHeight); panel.setTranslationX(layout.panelLeft); panel.setTranslationY(layout.panelTop);
    }

    private void size(View view, int width, int height) {
        android.view.ViewGroup.LayoutParams parameters = view.getLayoutParams();
        if (parameters.width == width && parameters.height == height) return;
        parameters.width = width; parameters.height = height; view.setLayoutParams(parameters);
    }

    private boolean drag(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            dragging = true; pointer = event.getPointerId(0);
            startX = event.getRawX(); startY = event.getRawY();
            startLeft = pendingLeft = grip.getTranslationX(); startTop = pendingTop = grip.getTranslationY();
            gesture.begin(startX, startY); lastMove = SystemClock.uptimeMillis();
            getParent().requestDisallowInterceptTouchEvent(true); return true;
        }
        if (!dragging) return false;
        if (action == MotionEvent.ACTION_MOVE) track(event);
        else if (action == MotionEvent.ACTION_POINTER_DOWN) gesture.cancelTap();
        else if (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex()) == pointer) {
            track(event); gesture.cancelTap(); pointer = -1;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (action == MotionEvent.ACTION_UP) track(event);
            applyMove(); boolean click = gesture.finish(action == MotionEvent.ACTION_CANCEL);
            stopDrag(); remember(); if (click) grip.performClick();
        }
        return true;
    }

    private void track(MotionEvent event) {
        int index = event.findPointerIndex(pointer);
        if (index < 0 || !gesture.move(event.getRawX(index), event.getRawY(index))) return;
        pendingLeft = startLeft + event.getRawX(index) - startX; pendingTop = startTop + event.getRawY(index) - startY;
        long remaining = MOVE_INTERVAL_MS - (SystemClock.uptimeMillis() - lastMove);
        removeCallbacks(moveFrame);
        if (remaining <= 0) applyMove(); else postDelayed(moveFrame, remaining);
    }

    private void applyMove() {
        removeCallbacks(moveFrame);
        if (!dragging) return;
        lastMove = SystemClock.uptimeMillis();
        state.moveTo(pendingLeft, pendingTop, getWidth(), getHeight(), dp(36), dp(8)); place();
    }

    private void stopDrag() {
        removeCallbacks(moveFrame); gesture.finish(true); dragging = false; pointer = -1;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }

    private void hideKeyboard() {
        panel.clearFocus(); InputMethodManager keyboard = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(getWindowToken(), 0);
    }

    private void remember() {
        preferences.edit().putBoolean("collapsed", state.collapsed()).putFloat("horizontal", state.horizontal())
            .putFloat("vertical", state.vertical()).apply();
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
