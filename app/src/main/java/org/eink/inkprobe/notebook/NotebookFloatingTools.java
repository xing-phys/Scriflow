package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.FrameLayout;
import java.util.EnumMap;

/** A separate, static window above the native ink surface, moved only by its grip. */
public final class NotebookFloatingTools {
    public enum Action {
        NOTES, ADD_PAGE, PEN, ERASER, LASSO, UNDO, REDO, MORE, OUTLINE, VIEW_BACK, VIEW_FORWARD, ORIENTATION, COLLAPSE
    }

    public interface Listener {
        void onAction(Action action);
        boolean onContactStart();
        void onContactEnd();
        boolean onMoveStart();
        void onMoveEnd();
        void onBoundsChanged(Rect screenBounds);
        void onPositionChanged(float horizontalFraction, float verticalFraction);
    }

    private static final long MOVE_INTERVAL_MS = 32;
    private final Activity activity;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final EnumMap<Action, NotebookIconButton> buttons = new EnumMap<>(Action.class);
    private final ContactLayout content;
    private final PopupWindow popup;
    private final NotebookIconButton grip;
    private final NotebookGripGesture gripMotion;
    private final View[] order;
    private final Rect screenBounds = new Rect();
    private final Rect allowedBounds = new Rect();
    private View anchor;
    private boolean vertical, collapsed;
    private int width, height;
    private int movingPointer = -1;
    private boolean gripGesture, moveAccepted, disposed;
    private boolean contactActive, contactAccepted;
    private float startRawX, startRawY;
    private int startLeft, startTop, pendingLeft, pendingTop;
    private float horizontalFraction, verticalFraction;
    private long lastMove;

    private final Runnable moveFrame = this::applyPendingMove;
    private final View.OnLayoutChangeListener layoutListener =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (isShowing() && !gripGesture) position(horizontalFraction, verticalFraction);
            };

    public NotebookFloatingTools(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        content = new ContactLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(4), dp(4), dp(4));
        content.setStateListAnimator(null);
        content.setElevation(0f);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(1000));
        background.setStroke(dp(1), Color.BLACK);
        content.setBackground(background);
        gripMotion = new NotebookGripGesture(ViewConfiguration.get(activity).getScaledTouchSlop());
        grip = new NotebookIconButton(activity, NotebookIconDrawable.Icon.MOVE,
                "Collapse tools; drag to move");
        grip.setOnClickListener(view -> listener.onAction(Action.COLLAPSE));
        grip.setOnTouchListener((view, event) -> {
            // A narrow-window strip can scroll, but its grip always moves the window.
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN)
                view.getParent().requestDisallowInterceptTouchEvent(true);
            boolean consumed = drag(event);
            if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                view.getParent().requestDisallowInterceptTouchEvent(false);
            return consumed;
        });
        add(Action.NOTES, NotebookIconDrawable.Icon.NOTES, "Open notes and minimized windows");
        add(Action.ADD_PAGE, NotebookIconDrawable.Icon.ADD_PAGE, "Add page");
        add(Action.PEN, NotebookIconDrawable.Icon.PEN, "Pen");
        add(Action.ERASER, NotebookIconDrawable.Icon.ERASER, "Eraser");
        add(Action.LASSO, NotebookIconDrawable.Icon.LASSO, "Lasso: select and move ink");
        add(Action.UNDO, NotebookIconDrawable.Icon.UNDO, "Undo");
        add(Action.REDO, NotebookIconDrawable.Icon.REDO, "Redo");
        add(Action.OUTLINE, NotebookIconDrawable.Icon.OUTLINE, "Table of contents; add and edit outline");
        add(Action.VIEW_BACK, NotebookIconDrawable.Icon.VIEW_BACK, "Go to previous view");
        add(Action.VIEW_FORWARD, NotebookIconDrawable.Icon.VIEW_FORWARD, "Go to next view");
        add(Action.ORIENTATION, NotebookIconDrawable.Icon.LAYOUT_VERTICAL, "Switch to vertical tools");
        order = new View[] {grip, button(Action.ORIENTATION), button(Action.PEN), button(Action.ERASER), button(Action.LASSO), button(Action.UNDO),
            button(Action.REDO),
            button(Action.ADD_PAGE), button(Action.OUTLINE), button(Action.VIEW_BACK),
            button(Action.VIEW_FORWARD), button(Action.NOTES)};
        popup = new PopupWindow(content, 0, 0, false);
        // A dedicated higher application sublayer keeps tools above notes, status and selection panels regardless of show order.
        popup.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
        popup.setTouchable(true);
        popup.setOutsideTouchable(false);
        popup.setFocusable(false);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        popup.setClippingEnabled(false);
        popup.setIsLaidOutInScreen(true);
        popup.setAnimationStyle(0);
        popup.setEnterTransition(null);
        popup.setExitTransition(null);
        popup.setElevation(0f);
        content.getViewTreeObserver().addOnPreDrawListener(() -> {
            if (popup.isShowing() && content.getWidth() > 0 && content.getHeight() > 0) {
                int[] location = new int[2];
                content.getLocationOnScreen(location);
                Rect actual = new Rect(location[0], location[1],
                        location[0] + content.getWidth(), location[1] + content.getHeight());
                if (!screenBounds.equals(actual)) {
                    screenBounds.set(actual);
                    listener.onBoundsChanged(new Rect(screenBounds));
                }
            }
            return true;
        });
        popup.setOnDismissListener(() -> {
            if (anchor != null) anchor.removeOnLayoutChangeListener(layoutListener);
            stopDrag();
            if (!screenBounds.isEmpty()) {
                screenBounds.setEmpty();
                listener.onBoundsChanged(new Rect());
            }
            finishContact();
        });
    }

    private void add(Action action, NotebookIconDrawable.Icon icon, String name) {
        NotebookIconButton button = new NotebookIconButton(activity, icon, name);
        button.setOnClickListener(view -> {
            if (!gripGesture) listener.onAction(action);
        });
        buttons.put(action, button);
    }

    public NotebookIconButton button(Action action) { return buttons.get(action); }

    public boolean isVertical() { return vertical; }

    public boolean isCollapsed() { return collapsed; }

    /** Keep the grip at the same screen position where space permits. Ink must be paused. */
    public void setCollapsed(boolean value) {
        if (collapsed == value) return;
        collapsed = value;
        grip.setContentDescription(value ? "Expand tools; drag to move" : "Collapse tools; drag to move");
        if (isShowing()) {
            position(horizontalFraction, verticalFraction, true);
            rememberPosition();
        }
    }

    /** Initial placement only. Subsequent grip drags remain unrestricted by the islands. */
    void avoidStatusIslands(Rect... islands) {
        boolean overlaps = false;
        int top = screenBounds.top;
        for (Rect island : islands) {
            if (Rect.intersects(screenBounds, island)) overlaps = true;
            if (!island.isEmpty()) top = Math.max(top, island.bottom + dp(8));
        }
        if (!overlaps || !isShowing()) return;
        pendingTop = Math.max(allowedBounds.top, Math.min(allowedBounds.bottom - height, top));
        setBounds(pendingLeft, pendingTop);
        popup.update(pendingLeft, pendingTop, width, height);
        rememberPosition();
    }

    /** Caller pauses native ink before changing the window's exclusion rectangle. */
    public void setVertical(boolean value) {
        if (vertical == value) return;
        vertical = value;
        button(Action.ORIENTATION).setIcon(value ? NotebookIconDrawable.Icon.LAYOUT_HORIZONTAL
            : NotebookIconDrawable.Icon.LAYOUT_VERTICAL);
        button(Action.ORIENTATION).setContentDescription(value
            ? "Switch to horizontal tools" : "Switch to vertical tools");
        if (isShowing()) position(horizontalFraction, verticalFraction);
    }

    /** Fractions describe the available travel, rather than screen pixels. */
    public void show(View anchor, float horizontalFraction, float verticalFraction) {
        if (disposed || anchor == null || !anchor.isAttachedToWindow()
                || anchor.getWidth() == 0 || anchor.getHeight() == 0) return;
        if (this.anchor != anchor) {
            if (this.anchor != null) this.anchor.removeOnLayoutChangeListener(layoutListener);
            this.anchor = anchor;
        }
        anchor.removeOnLayoutChangeListener(layoutListener);
        anchor.addOnLayoutChangeListener(layoutListener);
        position(horizontalFraction, verticalFraction);
        if (!popup.isShowing()) {
            try {
                if (!NotebookPopupHost.show(popup, anchor, Gravity.TOP | Gravity.LEFT, screenBounds.left, screenBounds.top)) {
                    anchor.removeOnLayoutChangeListener(layoutListener);
                    screenBounds.setEmpty(); listener.onBoundsChanged(new Rect());
                }
            } catch (RuntimeException error) {
                anchor.removeOnLayoutChangeListener(layoutListener);
                screenBounds.setEmpty();
                listener.onBoundsChanged(new Rect());
                throw error;
            }
        }
    }

    public void hide() {
        main.removeCallbacks(moveFrame);
        if (popup.isShowing()) popup.dismiss();
        else {
            stopDrag();
            if (anchor != null) anchor.removeOnLayoutChangeListener(layoutListener);
            finishContact();
        }
    }

    public boolean isShowing() { return popup.isShowing(); }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        hide();
        anchor = null;
    }

    public Rect bounds() { return new Rect(screenBounds); }

    private void position(float horizontal, float vertical) {
        position(horizontal, vertical, false);
    }

    private void position(float horizontal, float vertical, boolean keepGripPosition) {
        if (anchor == null) return;
        int previousLeft = screenBounds.left, previousTop = screenBounds.top;
        horizontalFraction = fraction(horizontal);
        verticalFraction = fraction(vertical);
        updateAllowedBounds();
        NotebookToolStrip.Layout layout = NotebookToolStrip.measure(this.vertical, collapsed ? 1 : order.length,
                Math.max(1, allowedBounds.width()), Math.max(1, allowedBounds.height()),
                dp(32), dp(28), dp(4), dp(4));
        width = layout.width; height = layout.height;
        buildStrip(layout);
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        int left = allowedBounds.left + Math.round(horizontalFraction
                * Math.max(0, allowedBounds.width() - width));
        int top = allowedBounds.top + Math.round(verticalFraction
                * Math.max(0, allowedBounds.height() - height));
        if (keepGripPosition) {
            left = Math.max(allowedBounds.left, Math.min(allowedBounds.right - width, previousLeft));
            top = Math.max(allowedBounds.top, Math.min(allowedBounds.bottom - height, previousTop));
        }
        pendingLeft = left;
        pendingTop = top;
        setBounds(left, top);
        popup.setWidth(width);
        popup.setHeight(height);
        if (popup.isShowing()) popup.update(left, top, width, height);
    }

    private void updateAllowedBounds() {
        anchor.getWindowVisibleDisplayFrame(allowedBounds);
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        Rect writingArea = new Rect(location[0], location[1],
                location[0] + anchor.getWidth(), location[1] + anchor.getHeight());
        Rect locallyVisible = new Rect();
        if (anchor.getLocalVisibleRect(locallyVisible)) {
            locallyVisible.offset(location[0], location[1]);
            if (!writingArea.intersect(locallyVisible)) writingArea.setEmpty();
        }
        if (!allowedBounds.intersect(writingArea)) allowedBounds.set(writingArea);
        int margin = dp(8);
        if (allowedBounds.width() > margin * 2 && allowedBounds.height() > margin * 2)
            allowedBounds.inset(margin, margin);
    }

    private void buildStrip(NotebookToolStrip.Layout layout) {
        content.removeAllViews();
        for (View view : order) {
            if (view.getParent() instanceof LinearLayout)
                ((LinearLayout) view.getParent()).removeView(view);
        }
        LinearLayout strip = new LinearLayout(activity);
        strip.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        for (int index = 0; index < (collapsed ? 1 : order.length); index++) {
            LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(layout.button, layout.button);
            if (index != 0) {
                if (vertical) item.topMargin = dp(4); else item.leftMargin = dp(4);
            }
            strip.addView(order[index], item);
        }
        if (vertical) {
            ScrollView scroll = new ScrollView(activity);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.setVerticalScrollBarEnabled(false);
            scroll.setVerticalFadingEdgeEnabled(false);
            scroll.setSmoothScrollingEnabled(false);
            scroll.addView(strip, new FrameLayout.LayoutParams(layout.stripWidth, layout.stripHeight));
            content.addView(scroll, new LinearLayout.LayoutParams(
                Math.max(1, width - dp(8)), Math.max(1, height - dp(8))));
        } else {
            HorizontalScrollView scroll = new HorizontalScrollView(activity);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setHorizontalFadingEdgeEnabled(false);
            scroll.setSmoothScrollingEnabled(false);
            scroll.addView(strip, new FrameLayout.LayoutParams(layout.stripWidth, layout.stripHeight));
            content.addView(scroll, new LinearLayout.LayoutParams(
                Math.max(1, width - dp(8)), Math.max(1, height - dp(8))));
        }
    }

    private boolean drag(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            gripGesture = true;
            moveAccepted = listener.onMoveStart();
            movingPointer = event.getPointerId(0);
            startRawX = event.getRawX();
            startRawY = event.getRawY();
            gripMotion.begin(startRawX, startRawY);
            startLeft = pendingLeft = screenBounds.left;
            startTop = pendingTop = screenBounds.top;
            lastMove = SystemClock.uptimeMillis();
            return true;
        }
        if (!gripGesture) return false;
        if (action == MotionEvent.ACTION_MOVE && moveAccepted && movingPointer != -1) {
            track(event);
            if (SystemClock.uptimeMillis() - lastMove >= MOVE_INTERVAL_MS) applyPendingMove();
            else if (!main.hasCallbacks(moveFrame))
                main.postDelayed(moveFrame, MOVE_INTERVAL_MS
                        - (SystemClock.uptimeMillis() - lastMove));
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            gripMotion.cancelTap();
        } else if (action == MotionEvent.ACTION_POINTER_UP
                && event.getPointerId(event.getActionIndex()) == movingPointer) {
            gripMotion.cancelTap();
            if (moveAccepted) { track(event); applyPendingMove(); }
            // Freeze after the grip finger lifts; resume ink only after all fingers lift.
            movingPointer = -1;
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (moveAccepted) {
                if (action == MotionEvent.ACTION_UP && movingPointer != -1) track(event);
                applyPendingMove();
            }
            boolean click = gripMotion.finish(action == MotionEvent.ACTION_CANCEL || !moveAccepted);
            stopDrag();
            if (click) grip.performClick();
        }
        return true;
    }

    private void track(MotionEvent event) {
        int index = event.findPointerIndex(movingPointer);
        if (index < 0) return;
        // getRawX/Y(index) requires API 29; the common window offset works on API 23 too.
        float rawX = event.getX(index) + event.getRawX() - event.getX(0);
        float rawY = event.getY(index) + event.getRawY() - event.getY(0);
        if (!gripMotion.move(rawX, rawY)) return;
        pendingLeft = Math.max(allowedBounds.left,
                Math.min(allowedBounds.right - width, startLeft + Math.round(rawX - startRawX)));
        pendingTop = Math.max(allowedBounds.top,
                Math.min(allowedBounds.bottom - height, startTop + Math.round(rawY - startRawY)));
    }

    private void applyPendingMove() {
        main.removeCallbacks(moveFrame);
        if (!moveAccepted || !popup.isShowing()) return;
        lastMove = SystemClock.uptimeMillis();
        if (screenBounds.left == pendingLeft && screenBounds.top == pendingTop) return;
        setBounds(pendingLeft, pendingTop);
        // Native input exclusion is updated by setBounds before this window moves.
        popup.update(pendingLeft, pendingTop, -1, -1);
    }

    private void stopDrag() {
        main.removeCallbacks(moveFrame);
        if (!gripGesture) return;
        boolean accepted = moveAccepted;
        gripGesture = false;
        gripMotion.finish(true);
        moveAccepted = false;
        movingPointer = -1;
        if (accepted) {
            rememberPosition();
            listener.onMoveEnd();
        }
    }

    private void rememberPosition() {
        horizontalFraction = relative(screenBounds.left - allowedBounds.left, allowedBounds.width() - width);
        verticalFraction = relative(screenBounds.top - allowedBounds.top, allowedBounds.height() - height);
        listener.onPositionChanged(horizontalFraction, verticalFraction);
    }

    private void finishContact() {
        if (!contactActive) return;
        boolean accepted = contactAccepted;
        contactActive = false;
        contactAccepted = false;
        if (accepted) listener.onContactEnd();
    }

    /** A contact pauses native ink even when it begins on an ordinary button. */
    private final class ContactLayout extends LinearLayout {
        ContactLayout(Activity context) { super(context); }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                finishContact();
                contactActive = true;
                contactAccepted = listener.onContactStart();
            }
            if (!contactActive) return true;
            try {
                if (contactAccepted) super.dispatchTouchEvent(event);
                return true;
            } finally {
                // Button clicks run inside super first. A second finger keeps ink paused.
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL)
                    finishContact();
            }
        }
    }

    private void setBounds(int left, int top) {
        Rect value = new Rect(left, top, left + width, top + height);
        if (screenBounds.equals(value)) return;
        screenBounds.set(value);
        listener.onBoundsChanged(new Rect(screenBounds));
    }

    private float relative(int offset, int travel) {
        return travel <= 0 ? 0f : fraction((float) offset / travel);
    }

    private float fraction(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? 0f
                : Math.max(0f, Math.min(1f, value));
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
