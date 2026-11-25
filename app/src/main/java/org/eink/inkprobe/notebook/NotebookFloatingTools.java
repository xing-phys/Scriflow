package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.FrameLayout;
import java.util.EnumMap;

/** A separate, static window above the native ink surface, moved only by its grip. */
public final class NotebookFloatingTools {
    public enum Action {
        NOTES, ADD_PAGE, PEN, ERASER, LASSO, MORE, OUTLINE, ZEN, ORIENTATION, COLLAPSE, AI_CHAT,
        TEXT_STYLE, SEARCH_BOOK, BOOKMARK, HANDWRITE, HIGHLIGHT, TEXT_NOTE, COPY_TEXT, LOOKUP, CLEAR_SELECTION
    }

    public interface Listener {
        void onAction(Action action);
        boolean onContactStart();
        void onContactEnd();
        boolean onMoveStart();
        void onMoveEnd();
        void onBoundsChanged(Rect screenBounds);
        void onPositionChanged(float horizontalFraction, float verticalFraction);
        default void onBrowserDismissed() { }
        default void onBrowserContentSizeChanged() { }
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
    private boolean gripGesture, moveAccepted, disposed, hiding;
    private boolean contactActive, contactAccepted;
    private boolean zenContact;
    private LinearLayout browserControls, browserHeader;
    private ScrollView browserScroll;
    private View browserWriting;
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
        this(activity,listener,false);
    }
    static NotebookFloatingTools reading(Activity activity,Listener listener){return new NotebookFloatingTools(activity,listener,true);}
    private NotebookFloatingTools(Activity activity, Listener listener,boolean reading) {
        this.activity = activity;
        this.listener = listener;
        content = new ContactLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(4), dp(4), dp(4), dp(4));
        content.setStateListAnimator(null);
        content.setElevation(0f);
        content.setBackground(null);
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
        add(Action.NOTES, reading ? NotebookIconDrawable.Icon.NOTES : NotebookIconDrawable.Icon.OPEN_NOTES, reading?"Bookmarks and reading notes":"Open notes and minimized windows");
        add(Action.ZEN, NotebookIconDrawable.Icon.FULLSCREEN, "Enter Zen mode");
        add(Action.OUTLINE, NotebookIconDrawable.Icon.OUTLINE, reading?"Contents":"Table of contents; add and edit outline");
        add(Action.ORIENTATION, NotebookIconDrawable.Icon.LAYOUT_VERTICAL, "Switch to vertical tools");
        if(reading){
            add(Action.TEXT_STYLE,NotebookIconDrawable.Icon.FONT_SIZE,"Text and layout");
            add(Action.SEARCH_BOOK,NotebookIconDrawable.Icon.SEARCH,"Search book");
            add(Action.BOOKMARK,NotebookIconDrawable.Icon.BOOKMARK,"Bookmark this position");
            add(Action.HANDWRITE,NotebookIconDrawable.Icon.PEN,"Handwritten note");
            add(Action.HIGHLIGHT,NotebookIconDrawable.Icon.READING,"Highlight selected text");
            add(Action.TEXT_NOTE,NotebookIconDrawable.Icon.ADD_PAGE,"Text note on selection");
            add(Action.COPY_TEXT,NotebookIconDrawable.Icon.COPY,"Copy selected text");
            add(Action.LOOKUP,NotebookIconDrawable.Icon.DICTIONARY,"Look up selected text");
            add(Action.CLEAR_SELECTION,NotebookIconDrawable.Icon.CLOSE,"Clear text selection");
            order=new View[]{grip,button(Action.ORIENTATION),button(Action.TEXT_STYLE),button(Action.OUTLINE),button(Action.SEARCH_BOOK),button(Action.BOOKMARK),button(Action.NOTES),button(Action.HANDWRITE),button(Action.ZEN),
                button(Action.HIGHLIGHT),button(Action.TEXT_NOTE),button(Action.COPY_TEXT),button(Action.LOOKUP),button(Action.CLEAR_SELECTION)};
        }else{
            add(Action.AI_CHAT, NotebookIconDrawable.Icon.AI_CHAT, "Open chats and minimized chat windows");
            add(Action.ADD_PAGE, NotebookIconDrawable.Icon.ADD_PAGE, "Add page");
            add(Action.PEN, NotebookIconDrawable.Icon.PEN, "Pen");
            add(Action.ERASER, NotebookIconDrawable.Icon.ERASER, "Eraser");
            add(Action.LASSO, NotebookIconDrawable.Icon.LASSO, "Lasso: select and move ink");
            order = new View[] {grip, button(Action.ORIENTATION), button(Action.PEN), button(Action.ERASER), button(Action.LASSO),
                button(Action.ADD_PAGE), button(Action.OUTLINE), button(Action.AI_CHAT), button(Action.ZEN), button(Action.NOTES)};
        }
        popup = new PopupWindow(content, 0, 0, false);
        // A dedicated higher application sublayer keeps tools above notes, status and selection panels regardless of show order.
        popup.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
        popup.setTouchable(true);
        popup.setOutsideTouchable(false);
        popup.setFocusable(false);
        // A non-focusable panel otherwise receives ALT_FOCUSABLE_IM's inverse
        // behavior and can appear above the system keyboard.
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
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
            // Back can dismiss a focusable address editor. Restore the single collapsed toolbox.
            if (hasBrowserControls() && !hiding && !disposed) {
                hideKeyboard(); collapsed = true; describeGrip(); listener.onBrowserDismissed();
            }
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

    /** Browser and writing actions share this window, contact barrier, grip and native-ink hole. */
    void browserControls(LinearLayout controls, LinearLayout header, View writing) {
        browserControls = controls; browserHeader = header; browserWriting = writing;
        vertical = false;
        browserScroll = new ScrollView(activity);
        browserScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        browserScroll.setVerticalScrollBarEnabled(false);
        browserScroll.setSmoothScrollingEnabled(false);
        browserScroll.addView(controls, new ScrollView.LayoutParams(-1, -2));
        controls.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob) -> {
            if (b - t != ob - ot && popup.isShowing()) listener.onBrowserContentSizeChanged();
        });
        controls.setFocusableInTouchMode(true);
        popup.setTouchModal(false);
        popup.setOutsideTouchable(true);
        popup.setTouchInterceptor((view, event) -> {
            if (event.getActionMasked() != MotionEvent.ACTION_OUTSIDE) return false;
            // The same outside DOWN still reaches the page/note; only the address editor loses focus.
            hideKeyboard(); return true;
        });
        describeGrip();
    }

    void browserWritingVisible(boolean visible) {
        if (browserWriting == null || (browserWriting.getVisibility() == View.VISIBLE) == visible) return;
        browserWriting.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (isShowing()) { position(horizontalFraction, verticalFraction, true); rememberPosition(); }
    }

    boolean hasBrowserControls() { return browserControls != null; }
    void reflowBrowserControls() {
        if (hasBrowserControls() && isShowing()) { position(horizontalFraction, verticalFraction, true); rememberPosition(); }
    }

    void hideKeyboard() {
        if (!hasBrowserControls()) return;
        InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(content.getWindowToken(), 0);
        browserControls.requestFocus();
        browserTextInput(null, false);
    }

    void browserTextInput(View editor, boolean enabled) {
        if (!hasBrowserControls()) return;
        if (popup.isFocusable() != enabled) {
            popup.setFocusable(enabled);
            if (popup.isShowing()) popup.update();
        }
        if (enabled && editor != null) editor.post(() -> {
            if (!disposed && popup.isShowing() && editor.hasFocus() && editor.hasWindowFocus()) {
                InputMethodManager keyboard = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
                if (keyboard != null) keyboard.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    void appendDebug(org.json.JSONObject result) throws org.json.JSONException {
        result.put("toolboxVisible", isShowing()).put("toolboxCollapsed", collapsed)
            .put("noteToolsVisible", browserWriting != null && browserWriting.getVisibility() == View.VISIBLE);
        org.json.JSONArray controls = new org.json.JSONArray();
        if (isShowing()) debugControls(content, controls);
        result.put("toolboxControls", controls);
    }

    private void debugControls(View view, org.json.JSONArray controls) throws org.json.JSONException {
        if (!view.isShown()) return;
        if (view.getContentDescription() != null) {
            int[] location = new int[2]; view.getLocationOnScreen(location);
            controls.put(new org.json.JSONObject().put("name", view.getContentDescription()).put("x", location[0])
                .put("y", location[1]).put("width", view.getWidth()).put("height", view.getHeight()));
        }
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++)
            debugControls(((ViewGroup) view).getChildAt(i), controls);
    }

    boolean isZenContact() { return zenContact; }

    public boolean isCollapsed() { return collapsed; }

    /** Keep the grip at the same screen position where space permits. Ink must be paused. */
    public void setCollapsed(boolean value) {
        if (collapsed == value) return;
        if (value) hideKeyboard();
        collapsed = value;
        describeGrip();
        if (isShowing()) {
            position(horizontalFraction, verticalFraction, true);
            rememberPosition();
        }
    }

    private void describeGrip() {
        grip.setContentDescription((collapsed ? "Expand" : "Collapse")
            + (hasBrowserControls() ? " browser toolbox; drag to move" : " tools; drag to move"));
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
        hideKeyboard();
        if (popup.isShowing()) {
            hiding = true;
            try { popup.dismiss(); } finally { hiding = false; }
        }
        else {
            stopDrag();
            if (anchor != null) anchor.removeOnLayoutChangeListener(layoutListener);
            finishContact();
        }
    }

    public boolean isShowing() { return popup.isShowing(); }

    /** Restore the app's tool layer after a note is presented above a main chat. */
    void raise() {
        if (!popup.isShowing() || anchor == null || contactActive || gripGesture) return;
        View current = anchor; float x = horizontalFraction, y = verticalFraction;
        hide(); show(current, x, y);
    }

    public void dispose() {
        if (disposed) return;
        disposed = true;
        hide();
        anchor = null;
    }

    public Rect bounds() { return new Rect(screenBounds); }
    View controlsView() { return content; }

    private void position(float horizontal, float vertical) {
        position(horizontal, vertical, false);
    }

    private void position(float horizontal, float vertical, boolean keepGripPosition) {
        if (anchor == null) return;
        int previousLeft = screenBounds.left, previousTop = screenBounds.top;
        int[] previousGrip = new int[2];
        if (keepGripPosition && hasBrowserControls()) grip.getLocationOnScreen(previousGrip);
        horizontalFraction = fraction(horizontal);
        verticalFraction = fraction(vertical);
        updateAllowedBounds();
        if (hasBrowserControls()) measureBrowser();
        else {
            int count=0;for(View view:order)if(view.getVisibility()!=View.GONE)count++;
            NotebookToolStrip.Layout layout = NotebookToolStrip.measure(this.vertical, collapsed ? 1 : count,
                    Math.max(1, allowedBounds.width()), Math.max(1, allowedBounds.height()),
                    dp(32), dp(28), dp(6), dp(4));
            width = layout.width; height = layout.height;
            buildStrip(layout);
            content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        }
        int left = allowedBounds.left + Math.round(horizontalFraction
                * Math.max(0, allowedBounds.width() - width));
        int top = allowedBounds.top + Math.round(verticalFraction
                * Math.max(0, allowedBounds.height() - height));
        if (keepGripPosition) {
            if (hasBrowserControls()) {
                previousLeft = previousGrip[0] - content.getPaddingLeft() - (collapsed ? 0 : browserControls.getPaddingLeft());
                previousTop = previousGrip[1] - content.getPaddingTop() - (collapsed ? 0 : browserControls.getPaddingTop());
            }
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

    private void measureBrowser() {
        // Keep the address field attached while moving/resizing: rebuilding it would lose focus and the keyboard.
        View desired = collapsed ? grip : browserScroll;
        if (content.getChildCount() != 1 || content.getChildAt(0) != desired) {
            content.removeAllViews();
            if (grip.getParent() instanceof ViewGroup) ((ViewGroup) grip.getParent()).removeView(grip);
            if (!collapsed) {
                LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(dp(32), dp(32));
                item.rightMargin = dp(6); browserHeader.addView(grip, 0, item);
            }
            content.addView(desired, new LinearLayout.LayoutParams(collapsed ? dp(32) : -1, collapsed ? dp(32) : -2));
            content.setBackground(collapsed ? null : NotebookUi.rounded(activity.getResources().getDisplayMetrics().density, 7));
        }
        width = Math.min(collapsed ? dp(40) : dp(400), Math.max(1, allowedBounds.width()));
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(Math.max(1, allowedBounds.height()), View.MeasureSpec.AT_MOST));
        height = Math.max(1, content.getMeasuredHeight());
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
        int index=0;
        for (View control:order) {
            if(control.getVisibility()==View.GONE)continue;
            if(collapsed&&index>0)break;
            LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(layout.button, layout.button);
            if (index != 0) {
                if (vertical) item.topMargin = dp(6); else item.leftMargin = dp(6);
            }
            strip.addView(control, item);index++;
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
        zenContact = false;
    }

    /** A contact pauses native ink even when it begins on an ordinary button. */
    private final class ContactLayout extends LinearLayout {
        ContactLayout(Activity context) { super(context); }

        @Override public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                finishContact();
                NotebookIconButton zen = button(Action.ZEN);
                int[] location = new int[2]; zen.getLocationOnScreen(location);
                zenContact = zen.isShown() && event.getRawX() >= location[0]
                    && event.getRawX() < location[0] + zen.getWidth() && event.getRawY() >= location[1]
                    && event.getRawY() < location[1] + zen.getHeight();
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
