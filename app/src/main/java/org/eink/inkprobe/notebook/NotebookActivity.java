package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.DashPathEffect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;

/** Durable notebook pages, native pen ink, and document-level editing. */
public final class NotebookActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private NotebookSession session;
    private NotebookInkBackend ink;
    private FrameLayout canvas;
    private TextView saveState, pageLabel;
    private NotebookDeviceStatus deviceStatus;
    private NotebookStatusIslands statusIslands;
    private final Rect notebookStatusBounds = new Rect(), deviceStatusBounds = new Rect();
    private NotebookIconButton paperButton, penButton, eraserButton, lassoButton, undoButton, redoButton;
    private NotebookLasso.Selection selection;
    private List<NotebookPage.Point> selectionOutline;
    private int selectionPage;
    private AlertDialog lassoMenu;
    private NotebookSelectionTools selectionTools;
    private final NotebookSelectionDrag selectionDrag = new NotebookSelectionDrag();
    private Bitmap selectionBase;
    private final List<Path> selectionPaths = new ArrayList<>();
    private float selectionPreviewDx, selectionPreviewDy;
    private int selectionPointer = -1;
    private final Runnable selectionFrame = this::drawSelectionFrame;
    private NotebookIconButton previousButton, nextButton, addButton;
    private NotebookFloatingTools floatingTools;
    private float toolsX = 0.5f, toolsY;
    private boolean placeToolsBelowStatus;
    private final Rect toolsBounds = new Rect();
    private boolean toolsContact, toolsMoved;
    private long lastInputAt;
    private NotebookFloatingTools.Action pendingToolAction;
    private NotebookPage page;
    private IOException shownError;
    private boolean active, penDown, geometryPending, editing;
    private NotebookInk.Tool tool = NotebookInk.Tool.PEN;
    private float penWidth = NotebookPenConfig.DEFAULT_WIDTH;
    private float eraserWidth = NotebookWidthRange.ERASER.defaultWidth;
    private NotebookWidthPopup widthPopup;
    private boolean verifyOnReady;
    private int generation;
    private NotebookViewport viewport;
    private final Map<Integer, NotebookPage> pages = new LinkedHashMap<>();
    private final NotebookViewportBuffer viewportBuffer = new NotebookViewportBuffer();
    private final Map<Integer, Bitmap> tiles = new LinkedHashMap<>();
    private Bitmap preview;
    private float scrollY, fingerX, fingerY, fingerOriginX, fingerOrigin;
    private float pinchDistance, pinchX, pinchY;
    private boolean pinching;
    private boolean selectionViewportGesture, selectionMixed;
    private NotebookViewport.Fit zoomFit = NotebookViewport.Fit.WIDTH;
    private float zoom = 1;
    private int viewNotebookId;
    private boolean restoreCenter;
    private int restoreCenterPage;
    private float restoreCenterX, restoreCenterY;
    private PopupWindow zoomBadge;
    private TextView zoomBadgeText;
    private AlertDialog viewMenu;
    private int activePage = 1, fingerId = -1;
    private boolean scrolling, dragging, moved, fetching, firstViewport, savePosition, cleanupInk;
    private boolean settleRead;
    private long viewportRevision;
    private NotebookScrollStore.State reopenPosition;
    private NotebookPageOverview overview;
    private final Runnable previewFrame = this::drawPreview;
    private final Runnable updateSaveState = this::showSaveState;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        NotebookSettings.applyOrientation(this);
        session = NotebookSession.get(this);
        viewNotebookId = session.notebookId();
        if (saved != null && viewNotebookId == saved.getInt("viewNotebookId", -1)) {
            zoom = saved.getFloat("zoom", 1);
            if (!NotebookPage.finite(zoom) || zoom <= 0) zoom = 1;
            try { zoomFit = NotebookViewport.Fit.valueOf(saved.getString("zoomFit", "WIDTH")); }
            catch (IllegalArgumentException ignored) { zoomFit = NotebookViewport.Fit.WIDTH; }
            restoreCenter = saved.getBoolean("viewCenter", false);
            restoreCenterPage = saved.getInt("viewCenterPage", 1);
            restoreCenterX = saved.getFloat("viewCenterX", 0);
            restoreCenterY = saved.getFloat("viewCenterY", 0);
        }
        penWidth = NotebookPenConfig.restore(getPreferences(MODE_PRIVATE)
            .getFloat("penWidth", NotebookPenConfig.DEFAULT_WIDTH));
        eraserWidth = NotebookWidthRange.ERASER.restore(getPreferences(MODE_PRIVATE)
            .getFloat("eraserWidth", NotebookWidthRange.ERASER.defaultWidth));
        if (saved != null && saved.getBoolean("eraser", false)) tool = NotebookInk.Tool.ERASER;
        if (saved != null && saved.getBoolean("lasso", false)) tool = NotebookInk.Tool.LASSO;
        verifyOnReady = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
            && getIntent().getBooleanExtra("verifyRestore", false);
        getIntent().removeExtra("verifyRestore");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        canvas = new FrameLayout(this); canvas.setBackgroundColor(Color.WHITE);
        root.addView(canvas, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        pageLabel = new TextView(this);
        pageLabel.setText(session.notebookName() + " · 1 / 1 · 100%");
        pageLabel.setTextSize(12); pageLabel.setTextColor(Color.BLACK);
        pageLabel.setSingleLine(true); pageLabel.setIncludeFontPadding(false);
        pageLabel.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        pageLabel.setGravity(Gravity.CENTER_VERTICAL);
        pageLabel.setOnClickListener(v -> chooseView());
        zoomBadgeText = new TextView(this);
        zoomBadgeText.setTextSize(18); zoomBadgeText.setTextColor(Color.BLACK);
        zoomBadgeText.setGravity(Gravity.CENTER);
        zoomBadgeText.setPadding(dp(14), dp(7), dp(14), dp(7));
        GradientDrawable badgeBackground = new GradientDrawable();
        badgeBackground.setColor(Color.WHITE); badgeBackground.setStroke(dp(1), Color.BLACK);
        badgeBackground.setCornerRadius(dp(6)); zoomBadgeText.setBackground(badgeBackground);
        zoomBadge = new PopupWindow(zoomBadgeText, -2, -2, false);
        zoomBadge.setTouchable(false); zoomBadge.setAnimationStyle(0);
        zoomBadge.setEnterTransition(null); zoomBadge.setExitTransition(null);
        zoomBadge.setElevation(0f);
        saveState = new TextView(this);
        saveState.setSingleLine(true); saveState.setIncludeFontPadding(false);
        saveState.setText("Not saved · Retry"); saveState.setTextSize(12); saveState.setTextColor(Color.BLACK);
        saveState.setGravity(Gravity.CENTER_VERTICAL);
        saveState.setEllipsize(TextUtils.TruncateAt.END);
        saveState.setVisibility(View.GONE);
        deviceStatus = new NotebookDeviceStatus(this,
            () -> canEdit() && SystemClock.uptimeMillis() - lastInputAt >= 1000);
        statusIslands = new NotebookStatusIslands(this, pageLabel, saveState, deviceStatus, (left, right) -> {
            if (notebookStatusBounds.equals(left) && deviceStatusBounds.equals(right)) return;
            notebookStatusBounds.set(left); deviceStatusBounds.set(right);
            updateOverlayBounds();
        });
        toolsX = getPreferences(MODE_PRIVATE).getFloat("toolsX", 0.5f);
        toolsY = getPreferences(MODE_PRIVATE).getFloat("toolsY", 0f);
        placeToolsBelowStatus = !getPreferences(MODE_PRIVATE).getBoolean("floatingStatusLayout", false);
        floatingTools = new NotebookFloatingTools(this, new NotebookFloatingTools.Listener() {
            @Override public void onAction(NotebookFloatingTools.Action action) {
                if (toolsContact) pendingToolAction = action;
                else performToolAction(action); // Accessibility actions have no touch stream.
            }
            @Override public boolean onContactStart() {
                if (!canEdit()) return false;
                lastInputAt = SystemClock.uptimeMillis();
                toolsContact = true; toolsMoved = false; pendingToolAction = null;
                pauseForEdit();
                if (shownError != null) toolsContact = false;
                return shownError == null;
            }
            @Override public void onContactEnd() { finishToolsContact(); }
            @Override public boolean onMoveStart() { return toolsContact && shownError == null; }
            @Override public void onMoveEnd() { /* Resume only after all toolbar contacts lift. */ }
            @Override public void onBoundsChanged(Rect bounds) {
                if (toolsBounds.equals(bounds)) return;
                if (toolsContact) toolsMoved = true;
                toolsBounds.set(bounds);
                updateOverlayBounds();
            }
            @Override public void onPositionChanged(float x, float y) {
                toolsX = x; toolsY = y;
                getPreferences(MODE_PRIVATE).edit().putFloat("toolsX", x).putFloat("toolsY", y).apply();
            }
        });
        floatingTools.setVertical(getPreferences(MODE_PRIVATE).getBoolean("toolsVertical", false));
        floatingTools.setCollapsed(getPreferences(MODE_PRIVATE).getBoolean("toolsCollapsed", false));
        previousButton = floatingTools.button(NotebookFloatingTools.Action.PREVIOUS);
        nextButton = floatingTools.button(NotebookFloatingTools.Action.NEXT);
        addButton = floatingTools.button(NotebookFloatingTools.Action.ADD_PAGE);
        penButton = floatingTools.button(NotebookFloatingTools.Action.PEN);
        eraserButton = floatingTools.button(NotebookFloatingTools.Action.ERASER);
        lassoButton = floatingTools.button(NotebookFloatingTools.Action.LASSO);
        undoButton = floatingTools.button(NotebookFloatingTools.Action.UNDO);
        redoButton = floatingTools.button(NotebookFloatingTools.Action.REDO);
        paperButton = floatingTools.button(NotebookFloatingTools.Action.PAPER);
        selectionTools = new NotebookSelectionTools(this, this::performSelectionAction);
        updateControls();
    }

    @Override protected void onResume() {
        super.onResume();
        NotebookSettings.applyOrientation(this);
        if (session.notebookId() != viewNotebookId) {
            viewNotebookId = session.notebookId();
            zoom = 1; zoomFit = NotebookViewport.Fit.WIDTH; restoreCenter = false; viewport = null;
        }
        active = true;
        deviceStatus.start();
        canvas.post(() -> { if (active) statusIslands.show(canvas); });
        loadPage();
    }

    private void loadPage() {
        editing = false;
        updateControls();
        final int request = ++generation;
        session.load((loaded, error) -> {
            if (!active || request != generation) return;
            page = loaded;
            if (loaded == null) { showError(error); return; }
            openCanvas(loaded);
            if (error != null) showError(error);
        });
    }

    private void openCanvas(NotebookPage loaded) {
        final int request = ++generation;
        if (!restoreCenter) captureViewCenter();
        disposeCanvas();
        shownError = null;
        saveState.setOnClickListener(null);
        saveState.setVisibility(View.GONE);
        statusIslands.refresh();
        editing = false;
        page = loaded; geometryPending = false;
        viewport = null; pages.clear(); viewportBuffer.clear(); tiles.clear(); preview = null;
        fetching = dragging = false; scrolling = true; firstViewport = true;
        settleRead = true; ++viewportRevision;
        activePage = session.pageNumber();
        updatePaperIcon(loaded.template);
        updatePageLabel();
        updateControls();
        try {
            ink = new NotebookInk(this, NotebookPage.empty(), new NotebookInk.Listener() {
                @Override public void onGeometryReady(int width, int height) {
                    if (geometryPending || !active || request != generation) return;
                    geometryPending = true;
                    ink.pauseForEdit();
                    if (page.isInitialized()) { configureViewport(width, height); return; }
                    session.initializeSize(width, height, (sized, error) -> {
                        if (!active || request != generation) return;
                        if (error != null) { showError(error); return; }
                        page = sized;
                        configureViewport(width, height);
                    });
                }
                @Override public void onStrokeComplete(NotebookPage.Stroke stroke) {
                    acceptStroke(stroke, request);
                }
                @Override public void onPenStateChanged(boolean down) {
                    if (!active || request != generation) return;
                    penDown = down;
                    NotebookSettings.setPenDown(NotebookActivity.this, down);
                    lastInputAt = SystemClock.uptimeMillis();
                    main.removeCallbacks(updateSaveState);
                    if (!down) scheduleSaveState();
                }
                @Override public void onReady() {
                    if (!active || request != generation) return;
                    if (session.error() != null) { showError(session.error()); return; }
                    updateControls();
                    statusIslands.show(canvas);
                    showFloatingTools();
                    if (pendingToolAction != null && !toolsContact && canEdit()) {
                        NotebookFloatingTools.Action action = pendingToolAction;
                        pendingToolAction = null;
                        performToolAction(action);
                    }
                    if (!toolsContact && canEdit()) showSelectionTools();
                    if (verifyOnReady) {
                        verifyOnReady = false;
                        try { NotebookVerification.write(NotebookActivity.this, viewport.compose(pages, scrollY), ink.snapshotNativeStrokes()); }
                        catch (Exception ignored) { /* Explicit debug check does not interrupt writing. */ }
                    }
                    scheduleSaveState();
                }
                @Override public void onError(String message) {
                    if (!active || request != generation) return;
                    showError(new IOException(message));
                }
                @Override public void onEraseGesture(List<NotebookPage.Point> path, float radius) {
                    if (active && request == generation) erase(path, radius);
                }
                @Override public void onLassoGesture(List<NotebookPage.Point> path) {
                    if (active && request == generation) lasso(path);
                }
                @Override public void onSelectionEvent(MotionEvent event) {
                    if (active && request == generation) selectionTouch(event);
                }
                @Override public void onFingerEvent(MotionEvent event) {
                    lastInputAt = SystemClock.uptimeMillis();
                    if (active && request == generation) finger(event);
                }
            });
            ink.setTool(tool);
            ink.setPenWidth(penWidth);
            ink.setEraserWidth(eraserWidth);
            updateOverlayBounds();
            canvas.addView(ink.view(), new FrameLayout.LayoutParams(-1, -1));
            // Restore even a retained unsaved page. onReady stops input synchronously when
            // storage has failed, leaving the full open page visible while Retry is offered.
            ink.start();
        } catch (Exception error) { showError(new IOException("Could not open the writing surface", error)); }
    }

    private void configureViewport(int width, int height) {
        viewport = new NotebookViewport(session.pageCount(), page.width, page.height, width, height, dp(12), zoom, 0);
        int centerPage = Math.max(1, Math.min(session.pageCount(), restoreCenterPage));
        if (zoomFit != NotebookViewport.Fit.CUSTOM) {
            zoom = viewport.fittedZoom(zoomFit, restoreCenter ? centerPage : session.pageNumber());
            viewport = viewport.withView(zoom, 0);
        }
        NotebookScrollStore.State position = reopenPosition == null ? session.scrollPosition() : reopenPosition;
        reopenPosition = null;
        scrollY = position == null ? viewport.pageScroll(session.pageNumber())
            : viewport.scrollForAnchor(position.page, position.fraction);
        if (restoreCenter && NotebookPage.finite(restoreCenterX) && NotebookPage.finite(restoreCenterY)) {
            viewport = viewport.withView(zoom, viewport.panForPoint(centerPage, restoreCenterX, width / 2f));
            scrollY = viewport.scrollForPoint(centerPage, restoreCenterY, height / 2f);
        }
        restoreCenter = false;
        activePage = viewport.centeredPage(scrollY);
        requestWindow();
    }

    private List<Integer> wantedPages() {
        List<Integer> visible = viewport.visiblePages(scrollY);
        if (visible.isEmpty()) visible = java.util.Collections.singletonList(viewport.centeredPage(scrollY));
        List<Integer> result = new ArrayList<>();
        int first = Math.max(1, visible.get(0) - 1);
        int last = Math.min(viewport.pageCount, visible.get(visible.size() - 1) + 1);
        for (int id = first; id <= last; id++) result.add(id);
        return result;
    }

    private void requestWindow() {
        if (!active || viewport == null || fetching || shownError != null) return;
        List<Integer> wanted = wantedPages();
        boolean loaded = true;
        for (int id : wanted) if (!pages.containsKey(id)) loaded = false;
        if (loaded && (dragging || !settleRead)) {
            if (!dragging && scrolling) installViewport();
            return;
        }
        fetching = true;
        final int request = generation;
        final long revision = viewportRevision;
        session.window(wanted, (ignored, error) -> {
            if (!active || request != generation) return;
            fetching = false;
            mergeWindow();
            if (error != null) { showError(error); return; }
            if (!dragging && revision == viewportRevision) settleRead = false;
            // A long drag can outrun a previous read. Load the latest window, never enable
            // writing over a placeholder or reuse an old page's coordinates.
            requestWindow();
            if (dragging) schedulePreview();
        });
    }

    private void mergeWindow() {
        if (viewport == null) return;
        viewportBuffer.window(session.window(), wantedPages());
        updatePreviewPages();
    }

    private void updatePreviewPages() {
        for (Map.Entry<Integer, NotebookPage> entry : viewportBuffer.current().entrySet())
            if (pages.get(entry.getKey()) != entry.getValue()) tiles.remove(entry.getKey());
        pages.clear(); pages.putAll(viewportBuffer.current());
        tiles.keySet().retainAll(wantedPages());
    }

    private void installViewport() {
        if (!active || dragging || editing || viewport == null || shownError != null) return;
        if (settleRead) { requestWindow(); return; }
        for (int id : viewport.visiblePages(scrollY)) if (!pages.containsKey(id)) { requestWindow(); return; }
        try {
            ink.pauseForEdit();
            if (shownError != null) return;
            ink.setPenWidth(fittedPenWidth());
            ink.setEraserWidth(fittedEraserWidth());
            ink.setSelectionMode(selectionOutline != null);
            updateOverlayBounds();
            NotebookPage composed = viewport.compose(pages, scrollY);
            if (firstViewport) {
                ink.setPage(composed); ink.setPaperRenderer(this::drawPaper);
                firstViewport = false;
            } else if (!ink.updateViewport(composed, this::drawPaper)) {
                if (shownError != null) return;
                // Retire a rare unsettled private native stroke. Recreate at the same anchor.
                NotebookPage seed = page;
                reopenPosition = new NotebookScrollStore.State(viewport.anchorPage(scrollY), viewport.anchorFraction(scrollY));
                persistPosition();
                NotebookLasso.Selection retainedSelection = selection;
                List<NotebookPage.Point> retainedOutline = selectionOutline;
                int retainedPage = selectionPage;
                openCanvas(seed);
                selection = retainedSelection; selectionOutline = retainedOutline; selectionPage = retainedPage;
                return;
            }
            scrolling = false; cleanupInk = false;
            updatePageLabel(); updatePaperButton(); updateControls();
            ink.start();
            if (savePosition) { savePosition = false; persistPosition(); }
            scheduleSaveState();
        } catch (Exception error) { showError(new IOException("Could not display the notebook viewport", error)); }
    }

    private void updatePaperButton() {
        NotebookPage current = pages.get(activePage);
        if (current == null) return;
        updatePaperIcon(current.template);
    }

    private void updatePaperIcon(NotebookPage.Template template) {
        boolean lined = template == NotebookPage.Template.LINED;
        paperButton.setIcon(lined ? NotebookIconDrawable.Icon.PAPER_LINED : NotebookIconDrawable.Icon.PAPER_BLANK);
        paperButton.setContentDescription(lined ? "Lined paper; switch to blank paper" : "Blank paper; switch to lined paper");
    }

    private void persistPosition() {
        if (viewport == null || shownError != null || session.error() != null) return;
        // While the overview changes order, this covered canvas still has the old page numbers.
        if (overview != null && (overview.pagesChanged() || overview.hasPendingPageChange())) return;
        final int request = generation;
        session.saveScroll(viewport.anchorPage(scrollY), viewport.anchorFraction(scrollY), (ignored, error) -> {
            if (!active || request != generation) return;
            if (error != null) showError(error);
        });
    }

    private void acceptStroke(NotebookPage.Stroke stroke, int request) {
        if (viewport == null) return;
        Map<Integer, List<NotebookPage.Stroke>> split = viewport.splitStroke(stroke, scrollY);
        int first = split.isEmpty() ? 0 : split.keySet().iterator().next();
        if (first != 0) activePage = first;
        for (NotebookPage.Point point : stroke.points)
            if (viewport.pageAt(point.x, point.y, scrollY) != first) cleanupInk = true;
        if (split.isEmpty()) cleanupInk = true;
        // Immediate preview state includes completed ink even before the background save.
        final long token = viewportBuffer.append(split);
        updatePreviewPages();
        session.appendViewport(split, (ignored, error) -> {
            if (!active || request != generation) return;
            viewportBuffer.complete(token, session.window(), wantedPages());
            updatePreviewPages();
            if (error != null) { showError(error); return; }
            if (dragging) schedulePreview();
            scheduleSaveState();
        });
    }

    private void finger(MotionEvent event) {
        if (viewport == null || shownError != null) return;
        int action = event.getActionMasked(), changed = event.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN) {
            boolean continueLoading = active && scrolling && !dragging && !editing
                && shownError == null && session.error() == null && ink != null && !ink.isPenDown();
            if (!canEdit() && !continueLoading) return;
            pauseForEdit();
            if (shownError != null) return;
            if (selectionOutline != null) { clearSelection(); cleanupInk = true; }
            editing = false; scrolling = true; dragging = true; moved = false;
            pinching = false;
            fingerId = event.getPointerId(changed);
            fingerOriginX = fingerX = event.getX(changed);
            fingerOrigin = fingerY = event.getY(changed);
            return;
        }
        if (!dragging) return;
        int first = -1, second = -1;
        for (int i = 0; i < event.getPointerCount(); i++) {
            if (action == MotionEvent.ACTION_POINTER_UP && i == changed
                    || event.getToolType(i) != MotionEvent.TOOL_TYPE_FINGER) continue;
            if (first < 0) first = i; else if (second < 0) second = i;
        }
        if (first >= 0 && second >= 0 && action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_CANCEL) {
            float x = (event.getX(first) + event.getX(second)) / 2;
            float y = (event.getY(first) + event.getY(second)) / 2;
            float distance = (float) Math.hypot(event.getX(first) - event.getX(second),
                event.getY(first) - event.getY(second));
            if (pinching && action == MotionEvent.ACTION_MOVE && pinchDistance > dp(8) && distance > dp(8)) {
                float minimum = Math.min(.1f, viewport.fittedZoom(NotebookViewport.Fit.PAGE, activePage));
                float maximum = Math.max(8f, viewport.fittedZoom(NotebookViewport.Fit.HEIGHT, activePage));
                float nextZoom = Math.max(minimum, Math.min(maximum, zoom * distance / pinchDistance));
                zoomAt(nextZoom, pinchX, pinchY, x, y);
                zoomFit = NotebookViewport.Fit.CUSTOM;
                moved = true;
                showZoomBadge();
            }
            pinching = true; pinchDistance = distance; pinchX = x; pinchY = y;
            return;
        }
        if (pinching) {
            pinching = false;
            if (first >= 0) {
                fingerId = event.getPointerId(first);
                fingerOriginX = fingerX = event.getX(first);
                fingerOrigin = fingerY = event.getY(first);
            }
        }
        int index = event.findPointerIndex(fingerId);
        if (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(changed) == fingerId) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (i == changed || event.getToolType(i) != MotionEvent.TOOL_TYPE_FINGER) continue;
                fingerId = event.getPointerId(i); fingerX = event.getX(i); fingerY = event.getY(i);
                if (!moved) { fingerOriginX = fingerX; fingerOrigin = fingerY; }
                return;
            }
        }
        if (action == MotionEvent.ACTION_MOVE && index >= 0) {
            float x = event.getX(index), y = event.getY(index);
            if (!moved && Math.hypot(x - fingerOriginX, y - fingerOrigin) < dp(4)) return;
            moved = true;
            viewport = viewport.withView(zoom, viewport.pan + fingerX - x);
            scrollY = viewport.clampScroll(scrollY + fingerY - y); fingerX = x; fingerY = y;
            ++viewportRevision;
            schedulePreview(); requestWindow();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || index < 0
                || action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(changed) == fingerId) {
            dragging = false; fingerId = -1;
            zoomBadge.dismiss();
            main.removeCallbacks(previewFrame);
            if (!moved && !settleRead && !firstViewport && !cleanupInk) {
                scrolling = false; ink.start(); scheduleSaveState();
            } else {
                activePage = viewport.centeredPage(scrollY); savePosition |= moved;
                settleRead = true; ++viewportRevision;
                requestWindow();
            }
        }
    }

    private void zoomAt(float nextZoom, float fromX, float fromY, float toX, float toY) {
        int id = viewport.nearestPage(fromY, scrollY);
        float x = (fromX - viewport.pageLeft(id)) / viewport.scale(id);
        float y = (fromY - viewport.pageTop(id, scrollY)) / viewport.scale(id);
        NotebookViewport next = viewport.withView(nextZoom, 0);
        next = next.withView(nextZoom, next.panForPoint(id, x, toX));
        scrollY = next.scrollForPoint(id, y, toY);
        viewport = next; zoom = nextZoom;
        tiles.clear(); ++viewportRevision;
        updatePageLabel();
        schedulePreview(); requestWindow();
    }

    private String zoomLabel() { return Math.round(zoom * 100) + "%"; }

    private void showZoomBadge() {
        zoomBadgeText.setText(zoomLabel());
        if (zoomBadge.isShowing() || !canvas.isAttachedToWindow()) return;
        zoomBadge.showAtLocation(canvas, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(24));
    }

    private void captureViewCenter() {
        if (viewport == null) return;
        if (overview != null && (overview.pagesChanged() || overview.hasPendingPageChange())) {
            restoreCenter = false;
            return;
        }
        restoreCenter = true; restoreCenterPage = viewport.centeredPage(scrollY);
        restoreCenterX = (viewport.width / 2f - viewport.pageLeft(restoreCenterPage)) / viewport.scale(restoreCenterPage);
        restoreCenterY = (viewport.height / 2f - viewport.pageTop(restoreCenterPage, scrollY)) / viewport.scale(restoreCenterPage);
    }

    private void schedulePreview() {
        if (active && dragging && !main.hasCallbacks(previewFrame)) main.postDelayed(previewFrame, 24);
    }

    private void drawPreview() {
        if (!active || !dragging || viewport == null || shownError != null) return;
        try {
            if (preview == null) preview = Bitmap.createBitmap(viewport.width, viewport.height, Bitmap.Config.RGB_565);
            Canvas drawing = new Canvas(preview);
            drawing.drawColor(Color.WHITE);
            for (int id : viewport.visiblePages(scrollY)) {
                float top = viewport.pageTop(id, scrollY), left = viewport.pageLeft(id);
                NotebookPage current = pages.get(id);
                if (current == null) {
                    Paint paint = new Paint(); paint.setColor(Color.WHITE);
                    drawing.drawRect(left, top, viewport.pageRight(id), top + viewport.pageHeight(id) * viewport.scale(id), paint);
                    paint.setColor(0xff555555); paint.setTextSize(dp(16));
                    drawing.drawText("Loading page " + id + "…", dp(20), Math.max(dp(30), top + dp(30)), paint);
                    continue;
                }
                int width = Math.max(1, (int) Math.ceil(current.width * viewport.scale(id)));
                int height = Math.max(1, (int) Math.ceil(current.height * viewport.scale(id)));
                // A pinch changes scale every frame; render into the reused viewport bitmap
                // instead of repeatedly allocating and discarding page-sized tiles.
                if (!pinching && width <= viewport.width * 2 && height <= viewport.height * 2
                        && (long) width * height <= (long) viewport.width * viewport.height * 2) {
                    Bitmap tile = tiles.get(id);
                    if (tile == null) {
                        tile = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
                        Canvas tileCanvas = new Canvas(tile);
                        tileCanvas.drawColor(Color.WHITE);
                        tileCanvas.scale(viewport.scale(id), viewport.scale(id));
                        drawPage(tileCanvas, current, true);
                        tiles.put(id, tile);
                    }
                    drawing.drawBitmap(tile, left, top, null);
                } else {
                    drawing.save(); drawing.translate(left, top);
                    drawing.scale(viewport.scale(id), viewport.scale(id));
                    drawPage(drawing, current, true); drawing.restore();
                }
            }
            ink.showScrollPreview(preview);
        } catch (Exception error) { showError(new IOException("Could not scroll the notebook", error)); }
    }

    private void drawPaper(Canvas drawing, int width, int height) {
        drawing.drawColor(Color.WHITE);
        if (viewport == null) return;
        for (int id : viewport.visiblePages(scrollY)) {
            NotebookPage current = pages.get(id);
            if (current == null) continue;
            drawing.save(); drawing.translate(viewport.pageLeft(id), viewport.pageTop(id, scrollY));
            drawing.scale(viewport.scale(id), viewport.scale(id));
            drawPage(drawing, current, false); drawing.restore();
        }
        drawSelectionOutline(drawing, 0, 0);
    }

    private void drawPage(Canvas drawing, NotebookPage current, boolean includeInk) {
        drawing.save(); drawing.clipRect(0, 0, current.width, current.height);
        drawing.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (current.template == NotebookPage.Template.LINED) {
            paint.setColor(0xffd2d2d2); paint.setStrokeWidth(1);
            for (int y = 72; y < current.height; y += 72) drawing.drawLine(0, y, current.width, y, paint);
        }
        if (includeInk) {
            for (NotebookPage.Stroke stroke : current.strokes)
                NotebookStrokeRenderer.draw(drawing, stroke, paint);
        }
        drawing.restore();
    }

    private void showFloatingTools() {
        if (floatingTools != null && !floatingTools.isShowing() && canEdit() && overview == null) {
            floatingTools.show(canvas, toolsX, toolsY);
            if (placeToolsBelowStatus && floatingTools.isShowing()) {
                floatingTools.avoidStatusIslands(notebookStatusBounds, deviceStatusBounds);
                placeToolsBelowStatus = false;
                getPreferences(MODE_PRIVATE).edit().putBoolean("floatingStatusLayout", true).apply();
            }
        }
    }

    private void updateOverlayBounds() {
        if (ink == null) return;
        List<Rect> bounds = new ArrayList<>();
        bounds.add(toolsBounds); bounds.add(notebookStatusBounds); bounds.add(deviceStatusBounds);
        if (viewport != null) {
            int[] origin = new int[2]; ink.view().getLocationOnScreen(origin);
            int left = Math.max(0, (int) Math.floor(viewport.pageLeft(1)));
            int right = Math.min(viewport.width, (int) Math.ceil(viewport.pageRight(1)));
            addPageExclusion(bounds, origin, 0, 0, left, viewport.height);
            addPageExclusion(bounds, origin, right, 0, viewport.width, viewport.height);
            int bottom = 0;
            for (int id : viewport.visiblePages(scrollY)) {
                int top = Math.max(0, Math.min(viewport.height, (int) Math.floor(viewport.pageTop(id, scrollY))));
                addPageExclusion(bounds, origin, 0, bottom, viewport.width, top);
                bottom = Math.max(bottom, Math.min(viewport.height,
                    (int) Math.ceil(viewport.pageTop(id, scrollY) + viewport.pageHeight(id) * viewport.scale(id))));
            }
            addPageExclusion(bounds, origin, 0, bottom, viewport.width, viewport.height);
        }
        ink.setOverlayBounds(bounds.toArray(new Rect[0]));
    }

    private void addPageExclusion(List<Rect> bounds, int[] origin, int left, int top, int right, int bottom) {
        if (right > left && bottom > top)
            bounds.add(new Rect(origin[0] + left, origin[1] + top, origin[0] + right, origin[1] + bottom));
    }

    private void finishToolsContact() {
        if (!toolsContact) return;
        lastInputAt = SystemClock.uptimeMillis();
        toolsContact = false;
        if (!active || shownError != null || ink == null) { pendingToolAction = null; return; }
        final int request = generation;
        final boolean restore = toolsMoved;
        session.load((loaded, error) -> {
            if (!active || request != generation) return;
            mergeWindow();
            if (error != null) { pendingToolAction = null; showError(error); return; }
            editing = false;
            // Moving the native clip can reveal ink previously covered by the palette.
            // Recompose once at release; an ordinary tap/no-op reuses the paused tracks.
            if (restore) installViewport(); else ink.start();
        });
    }

    private void performToolAction(NotebookFloatingTools.Action action) {
        if (!canEdit()) return;
        switch (action) {
            case LIBRARY: returnToLibrary(); break;
            case PREVIOUS: switchPage(-1); break;
            case NEXT: switchPage(1); break;
            case ADD_PAGE: addPage(); break;
            case PEN: selectTool(NotebookInk.Tool.PEN); break;
            case ERASER: selectTool(NotebookInk.Tool.ERASER); break;
            case LASSO: selectTool(NotebookInk.Tool.LASSO); break;
            case UNDO: changeHistory(false); break;
            case REDO: changeHistory(true); break;
            case PAPER: choosePaper(); break;
            case MORE: pageInformation(); break;
            case OVERVIEW: pageOverview(); break;
            case ORIENTATION: toggleToolsOrientation(); break;
            case COLLAPSE: toggleToolsCollapsed(); break;
        }
    }

    private void toggleToolsOrientation() {
        if (!canEdit()) return;
        pauseForEdit();
        if (shownError != null) return;
        floatingTools.setVertical(!floatingTools.isVertical());
        getPreferences(MODE_PRIVATE).edit().putBoolean("toolsVertical", floatingTools.isVertical()).apply();
        final int request = generation;
        // Resizing the exclusion can reveal previously covered ink; restore it once.
        session.load((loaded, error) -> finishEdit(request, loaded, error));
    }

    private void toggleToolsCollapsed() {
        if (!canEdit()) return;
        pauseForEdit();
        if (shownError != null) return;
        floatingTools.setCollapsed(!floatingTools.isCollapsed());
        getPreferences(MODE_PRIVATE).edit().putBoolean("toolsCollapsed", floatingTools.isCollapsed()).apply();
        final int request = generation;
        session.load((loaded, error) -> finishEdit(request, loaded, error));
    }

    private boolean canEdit() {
        return active && !editing && !scrolling && !dragging && !selectionDrag.isActive() && !fetching && shownError == null && session.error() == null
            && page != null && ink != null && ink.isReady() && !ink.isPenDown();
    }

    private void updateControls() {
        // Availability is logical, with no animated or temporary disabled appearance.
        // canEdit() gates every action while the surface or writer is busy.
        boolean ready = active && shownError == null && session.error() == null
            && page != null && page.isInitialized();
        paperButton.setAvailable(ready);
        penButton.setAvailable(ready); eraserButton.setAvailable(ready); lassoButton.setAvailable(ready);
        undoButton.setAvailable(ready && session.canUndo(activePage));
        redoButton.setAvailable(ready && session.canRedo(activePage));
        previousButton.setAvailable(ready && activePage > 1);
        nextButton.setAvailable(ready && activePage < session.pageCount());
        addButton.setAvailable(ready && session.pageCount() < NotebookIndex.MAX_PAGES);
        penButton.setToolSelected(tool == NotebookInk.Tool.PEN);
        eraserButton.setToolSelected(tool == NotebookInk.Tool.ERASER);
        lassoButton.setToolSelected(tool == NotebookInk.Tool.LASSO);
        lassoButton.setContentDescription(tool == NotebookInk.Tool.LASSO
            ? "Lasso selected; tap for selection actions and paste" : "Lasso: select and move ink");
        penButton.setPenWidthPreview(fittedPenWidth());
        penButton.setContentDescription("Pen, thickness " + NotebookPenConfig.label(penWidth)
            + (tool == NotebookInk.Tool.PEN ? "; tap for width slider" : "; select pen"));
        eraserButton.setContentDescription("Eraser, width " + NotebookWidthRange.ERASER.label(eraserWidth)
            + (tool == NotebookInk.Tool.ERASER ? "; tap for width slider" : "; select eraser"));
    }

    private void selectTool(NotebookInk.Tool selected) {
        if (!canEdit()) return;
        if (tool == selected) {
            if (selected == NotebookInk.Tool.LASSO) selectionMenu(); else chooseWidth(selected);
            return;
        }
        boolean redraw = selectionOutline != null;
        if (redraw) { pauseForEdit(); clearSelection(); }
        tool = selected;
        ink.setTool(selected);
        if (redraw) { editing = false; installViewport(); }
        updateControls();
    }

    private float fittedPenWidth() {
        return Math.min(1024, penWidth * (viewport == null ? 1f : viewport.scale(activePage)));
    }

    private float fittedEraserWidth() {
        return Math.min(NotebookStore.MAX_DIMENSION,
            eraserWidth * (viewport == null ? 1f : viewport.scale(activePage)));
    }

    private void chooseWidth(NotebookInk.Tool selected) {
        if (!canEdit()) return;
        pauseForEdit();
        if (shownError != null) return;
        final int request = generation;
        final boolean erasing = selected == NotebookInk.Tool.ERASER;
        final NotebookWidthRange range = erasing ? NotebookWidthRange.ERASER : NotebookWidthRange.PEN;
        // Completed UPs keep their old style even if the native commit arrives late.
        session.load((loaded, error) -> {
            if (!active || request != generation) return;
            mergeWindow();
            if (error != null) { showError(error); return; }
            widthPopup = new NotebookWidthPopup(this, range, erasing ? eraserWidth : penWidth,
                viewport.scale(activePage), chosen -> {
                widthPopup = null;
                if (!active || request != generation) return;
                if (chosen != null) {
                    if (erasing) {
                        eraserWidth = chosen;
                        ink.setEraserWidth(fittedEraserWidth());
                    } else {
                        penWidth = chosen;
                        ink.setPenWidth(fittedPenWidth());
                    }
                    getPreferences(MODE_PRIVATE).edit().putFloat(erasing ? "eraserWidth" : "penWidth", chosen).apply();
                }
                // The slider waits until all contacts lift; the paused tracks can be reused.
                editing = false;
                ink.start();
                updateControls();
                scheduleSaveState();
            });
            widthPopup.show(canvas, erasing ? eraserButton : penButton, floatingTools.bounds(), floatingTools.isVertical());
        });
    }

    private void changeHistory(boolean redo) {
        if (!canEdit() || !(redo ? session.canRedo(activePage) : session.canUndo(activePage))) return;
        clearSelection();
        pauseForEdit();
        if (shownError != null) return;
        final int request = generation;
        NotebookSession.Callback completion = (updated, error) -> finishEdit(request, updated, error);
        if (redo) session.redo(activePage, completion); else session.undo(activePage, completion);
    }

    private void erase(List<NotebookPage.Point> path, float radius) {
        if (!canEdit()) return;
        pauseForEdit();
        if (shownError != null) return;
        final int request = generation;
        List<NotebookViewport.EraseGesture> gestures = viewport.splitErase(path, radius, scrollY);
        if (!gestures.isEmpty()) activePage = gestures.get(0).pageId;
        session.eraseViewport(gestures, (updated, error) -> finishEdit(request, updated, error));
    }

    private void lasso(List<NotebookPage.Point> path) {
        if (!canEdit() || viewport == null) return;
        pauseForEdit();
        if (shownError != null) return;
        final int request = generation;
        clearSelection();
        int id = 0;
        for (NotebookPage.Point point : path) {
            id = viewport.pageAt(point.x, point.y, scrollY); if (id != 0) break;
        }
        if (path.isEmpty()) { editing = false; installViewport(); return; }
        if (id == 0) id = viewport.centeredPage(scrollY);
        final int target = id;
        float scale = viewport.scale(id), top = viewport.pageTop(id, scrollY), left = viewport.pageLeft(id);
        List<NotebookPage.Point> local = new ArrayList<>();
        for (NotebookPage.Point p : path)
            local.add(new NotebookPage.Point((p.x - left) / scale, (p.y - top) / scale, p.time, p.pressure));
        selectionPage = id; selectionOutline = NotebookLasso.closedOutline(local);
        session.selectLasso(id, selectionOutline,
            (selected, error) -> finishLasso(request, target, selected, error, true));
    }

    private void finishLasso(int request, int id, NotebookLasso.Selection selected,
            IOException error, boolean keepOutline) {
        if (!active || request != generation) return;
        NotebookLasso.Selection previous = selection;
        selection = selected == null || selected.isEmpty() ? null : selected;
        selectionPage = id; activePage = id;
        if (!keepOutline) {
            if (selection == null) clearSelection();
            else if (previous != null && selectionOutline != null) {
                NotebookPage.Point before = previous.strokes.get(0).points.get(0), after = selection.strokes.get(0).points.get(0);
                selectionOutline = NotebookLasso.offsetOutline(selectionOutline, after.x - before.x, after.y - before.y);
            } else selectionOutline = outlineForSelection(selection);
        }
        selectionPreviewDx = selectionPreviewDy = 0;
        finishEdit(request, session.current(), error);
    }

    private void clearSelection() {
        selection = null; selectionOutline = null;
        if (selectionTools != null) selectionTools.hide();
    }

    private List<NotebookPage.Point> outlineForSelection(NotebookLasso.Selection selected) {
        if (selected == null || selected.isEmpty()) return null;
        NotebookLasso.Bounds b = selected.bounds;
        float padding = dp(10) / viewport.scale(selectionPage);
        float l = Math.max(0, b.left - padding), r = Math.min(viewport.pageWidth(selectionPage), b.right + padding);
        float t = Math.max(0, b.top - padding), bottom = Math.min(viewport.pageHeight(selectionPage), b.bottom + padding);
        return NotebookLasso.closedOutline(java.util.Arrays.asList(
            new NotebookPage.Point(l, t, 0, 1), new NotebookPage.Point(r, t, 0, 1),
            new NotebookPage.Point(r, bottom, 0, 1), new NotebookPage.Point(l, bottom, 0, 1)));
    }

    private RectF selectionScreenBounds() {
        RectF bounds = new RectF();
        if (selectionOutline == null || selectionOutline.isEmpty() || viewport == null) return bounds;
        float scale = viewport.scale(selectionPage), top = viewport.pageTop(selectionPage, scrollY), left = viewport.pageLeft(selectionPage);
        NotebookPage.Point first = selectionOutline.get(0);
        bounds.set(first.x * scale + left, first.y * scale + top, first.x * scale + left, first.y * scale + top);
        for (NotebookPage.Point point : selectionOutline) {
            bounds.left = Math.min(bounds.left, point.x * scale + left); bounds.right = Math.max(bounds.right, point.x * scale + left);
            bounds.top = Math.min(bounds.top, point.y * scale + top); bounds.bottom = Math.max(bounds.bottom, point.y * scale + top);
        }
        return bounds;
    }

    private void drawSelectionOutline(Canvas drawing, float dx, float dy) {
        if (selectionOutline == null || selectionOutline.isEmpty() || viewport == null) return;
        float scale = viewport.scale(selectionPage), top = viewport.pageTop(selectionPage, scrollY), left = viewport.pageLeft(selectionPage);
        Path path = new Path();
        for (int i = 0; i < selectionOutline.size(); i++) {
            NotebookPage.Point p = selectionOutline.get(i);
            float x = p.x * scale + left + dx, y = p.y * scale + top + dy;
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        path.close();
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2.5f);
        paint.setPathEffect(new DashPathEffect(new float[]{12, 10}, 0));
        drawing.drawPath(path, paint);
    }

    private void showSelectionTools() {
        if (selectionOutline != null && selectionTools != null && canEdit() && overview == null && lassoMenu == null)
            selectionTools.show(canvas, selectionScreenBounds(), toolsBounds, selection == null ? 0 : selection.strokes.size());
    }

    private void performSelectionAction(NotebookSelectionTools.Action action) {
        if (!canEdit() || selectionOutline == null) return;
        if (action == NotebookSelectionTools.Action.DONE) {
            pauseForEdit(); clearSelection(); editing = false; installViewport(); return;
        }
        if (selection == null) return;
        if (action == NotebookSelectionTools.Action.COPY) { session.copy(selection); selectionTools.copied(); return; }
        pauseForEdit();
        if (shownError != null) return;
        final int request = generation, id = selectionPage;
        if (action == NotebookSelectionTools.Action.CUT) session.copy(selection);
        float dx = 0, dy = 0;
        NotebookLasso.Action edit = NotebookLasso.Action.DELETE;
        if (action == NotebookSelectionTools.Action.DUPLICATE) {
            NotebookLasso.Bounds b = selection.bounds;
            dx = b.right + 24 <= viewport.pageWidth(id) ? 24 : -Math.min(24, b.left);
            dy = b.bottom + 24 <= viewport.pageHeight(id) ? 24 : -Math.min(24, b.top);
            edit = NotebookLasso.Action.DUPLICATE;
        }
        session.editSelection(id, selection, edit, dx, dy,
            (selected, error) -> finishLasso(request, id, selected, error, false));
    }

    private void selectionTouch(MotionEvent event) {
        lastInputAt = SystemClock.uptimeMillis();
        if (viewport == null || shownError != null) return;
        int action = event.getActionMasked(), changed = event.getActionIndex();
        if (selectionViewportGesture) {
            for (int i = 0; i < event.getPointerCount(); i++)
                if (event.getToolType(i) != MotionEvent.TOOL_TYPE_FINGER) selectionMixed = true;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                selectionViewportGesture = false;
                if (selectionMixed) {
                    MotionEvent cancelled = MotionEvent.obtain(event);
                    cancelled.setAction(MotionEvent.ACTION_CANCEL);
                    try { finger(cancelled); } finally { cancelled.recycle(); }
                } else finger(event);
            } else if (!selectionMixed) finger(event);
            return;
        }
        if (selectionOutline == null) return;
        if (action == MotionEvent.ACTION_DOWN) {
            if (!canEdit()) return;
            int type = event.getToolType(changed);
            if (type != MotionEvent.TOOL_TYPE_FINGER && type != MotionEvent.TOOL_TYPE_STYLUS
                    && type != MotionEvent.TOOL_TYPE_ERASER) return;
            float scale = viewport.scale(selectionPage), top = viewport.pageTop(selectionPage, scrollY);
            boolean inside = selection != null && NotebookLasso.contains(selectionOutline,
                (event.getX(changed) - viewport.pageLeft(selectionPage)) / scale, (event.getY(changed) - top) / scale);
            pauseForEdit();
            if (shownError != null) return;
            selectionPointer = event.getPointerId(changed);
            selectionDrag.begin(selectionPointer, event.getX(changed), event.getY(changed), inside, dp(3));
            if (inside) prepareSelectionPreview();
            return;
        }
        if (!selectionDrag.isActive()) return;
        if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
            boolean fingersOnly = true;
            for (int i = 0; i < event.getPointerCount(); i++)
                fingersOnly &= event.getToolType(i) == MotionEvent.TOOL_TYPE_FINGER;
            if (fingersOnly) {
                resetSelectionPreview(); clearSelection(); cleanupInk = true;
                editing = false; scrolling = true; dragging = true; moved = false; pinching = false;
                selectionViewportGesture = true; selectionMixed = false;
                fingerId = event.getPointerId(0);
                fingerOriginX = fingerX = event.getX(0); fingerOrigin = fingerY = event.getY(0);
                finger(event);
                return;
            }
        }
        int index = event.findPointerIndex(selectionPointer);
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP || index < 0) {
            selectionDrag.cancel(); selectionPreviewDx = selectionPreviewDy = 0;
            if (selectionBase != null && !main.hasCallbacks(selectionFrame)) main.postDelayed(selectionFrame, 32);
        }
        if (index >= 0 && selection != null && (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP)) {
            float scale = viewport.scale(selectionPage); NotebookLasso.Bounds b = selection.bounds;
            selectionDrag.move(selectionPointer, event.getX(index), event.getY(index),
                -b.left * scale, (viewport.pageWidth(selectionPage) - b.right) * scale,
                -b.top * scale, (viewport.pageHeight(selectionPage) - b.bottom) * scale);
            selectionPreviewDx = selectionDrag.dx; selectionPreviewDy = selectionDrag.dy;
            if (!main.hasCallbacks(selectionFrame)) main.postDelayed(selectionFrame, 32);
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            NotebookSelectionDrag.Result result = selectionDrag.finish(action == MotionEvent.ACTION_CANCEL);
            if (result == null) return;
            main.removeCallbacks(selectionFrame);
            if (!result.cancelled && result.moved && !result.outside && selection != null && (result.dx != 0 || result.dy != 0)) {
                drawSelectionFrame(); resetSelectionPreview();
                if (shownError != null) return;
                final int request = generation, id = selectionPage;
                float scale = viewport.scale(id);
                session.editSelection(id, selection, NotebookLasso.Action.MOVE, result.dx / scale, result.dy / scale,
                    (selected, error) -> finishLasso(request, id, selected, error, false));
            } else {
                resetSelectionPreview(); if (!result.cancelled && result.outside) clearSelection();
                editing = false; installViewport();
            }
        }
    }

    /** Cache paper/unselected ink and native pressure paths once, before movement begins. */
    private void prepareSelectionPreview() {
        try {
            selectionBase = Bitmap.createBitmap(viewport.width, viewport.height, Bitmap.Config.RGB_565);
            Canvas drawing = new Canvas(selectionBase); drawing.drawColor(Color.WHITE);
            HashSet<java.util.UUID> selected = new HashSet<>();
            for (NotebookPage.Stroke stroke : selection.strokes) {
                selected.add(stroke.id); selectionPaths.add(NotebookStrokeRenderer.path(stroke, () -> false));
            }
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            for (int id : viewport.visiblePages(scrollY)) {
                NotebookPage current = pages.get(id); if (current == null) continue;
                drawing.save(); drawing.translate(viewport.pageLeft(id), viewport.pageTop(id, scrollY));
                drawing.scale(viewport.scale(id), viewport.scale(id));
                drawPage(drawing, current, false); drawing.clipRect(0, 0, current.width, current.height);
                for (NotebookPage.Stroke stroke : current.strokes)
                    if (id != selectionPage || !selected.contains(stroke.id)) NotebookStrokeRenderer.draw(drawing, stroke, paint);
                drawing.restore();
            }
            selectionPreviewDx = selectionPreviewDy = 0;
        } catch (Exception error) { resetSelectionPreview(); showError(new IOException("Could not prepare selected ink", error)); }
    }

    private void drawSelectionFrame() {
        if (!active || selectionBase == null || selection == null || shownError != null) return;
        try {
            if (preview == null) preview = Bitmap.createBitmap(viewport.width, viewport.height, Bitmap.Config.RGB_565);
            Canvas drawing = new Canvas(preview); drawing.drawBitmap(selectionBase, 0, 0, null);
            float scale = viewport.scale(selectionPage), top = viewport.pageTop(selectionPage, scrollY), left = viewport.pageLeft(selectionPage);
            drawing.save(); drawing.clipRect(Math.max(0, left), Math.max(0, top), Math.min(viewport.width, viewport.pageRight(selectionPage)),
                Math.min(viewport.height, top + viewport.pageHeight(selectionPage) * scale));
            drawing.translate(left + selectionPreviewDx, top + selectionPreviewDy); drawing.scale(scale, scale);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < selectionPaths.size(); i++) {
                paint.setColor(selection.strokes.get(i).color); drawing.drawPath(selectionPaths.get(i), paint);
            }
            drawing.restore(); drawSelectionOutline(drawing, selectionPreviewDx, selectionPreviewDy);
            ink.showScrollPreview(preview);
        } catch (Exception error) { showError(new IOException("Could not move selected ink", error)); }
    }

    private void resetSelectionPreview() {
        main.removeCallbacks(selectionFrame); selectionDrag.reset(); selectionPointer = -1;
        selectionPaths.clear(); selectionPreviewDx = selectionPreviewDy = 0;
        if (selectionBase != null) { selectionBase.recycle(); selectionBase = null; }
    }

    private void selectionMenu() {
        if (!canEdit() || lassoMenu != null) return;
        if (selectionOutline != null) { showSelectionTools(); return; }
        pauseForEdit();
        if (shownError != null) return;
        final int request = generation;
        final int id = activePage;
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        Runnable resume = () -> { editing = false; installViewport(); };
        NotebookPage target = pages.get(id);
        if (session.clipboard() != null && target != null && session.clipboard().fits(target)) {
            labels.add("Paste"); actions.add(() -> {
                float x = Math.max(0, Math.min(target.width,
                    (viewport.width / 2f - viewport.pageLeft(id)) / viewport.scale(id)));
                float y = Math.max(0, Math.min(target.height,
                    (viewport.height / 2f - viewport.pageTop(id, scrollY)) / viewport.scale(id)));
                selectionOutline = null;
                session.paste(id, x, y, (selected, error) -> finishLasso(request, id, selected, error, false));
            });
        }
        if (target != null && !target.strokes.isEmpty()) {
            labels.add("Select all on this page"); actions.add(() -> {
                selection = NotebookLasso.all(target); selectionPage = id;
                selectionOutline = outlineForSelection(selection); resume.run();
            });
        }
        final Runnable[] chosen = {resume};
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(selection == null ? "Lasso" : selection.strokes.size() + " strokes selected")
            .setNegativeButton("Close", null);
        if (labels.isEmpty()) builder.setMessage("Circle the ink you want to select, then lift the stylus.");
        else builder.setItems(labels.toArray(new String[0]), (dialog, which) -> chosen[0] = actions.get(which));
        lassoMenu = builder.create();
        lassoMenu.setOnDismissListener(dialog -> {
            lassoMenu = null;
            if (active && request == generation && shownError == null) chosen[0].run();
        });
        showStaticDialog(lassoMenu);
        if (lassoMenu.getListView() != null) lassoMenu.getListView().setSelector(android.R.color.transparent);
    }

    private void pauseForEdit() {
        if (selectionTools != null) selectionTools.hide();
        editing = true;
        ink.pauseForEdit();
        main.removeCallbacks(updateSaveState);
        updateControls();
    }

    private void finishEdit(int request, NotebookPage updated, IOException error) {
        if (!active || request != generation) return;
        mergeWindow();
        if (error != null) { showError(error); return; }
        editing = false;
        if (viewport == null) return;
        installViewport();
    }

    private void updatePageLabel() {
        String name = session.notebookName();
        String text = name + " · " + activePage + " / " + session.pageCount() + " · " + zoomLabel();
        if (!text.contentEquals(pageLabel.getText())) pageLabel.setText(text);
        pageLabel.setContentDescription(session.notebookName() + ", page " + activePage
            + " of " + session.pageCount() + ", zoom " + zoomLabel() + "; tap for zoom and app settings");
    }

    /** The quiet notebook title also provides zoom presets without enlarging the pen tools. */
    private void chooseView() {
        if (!canEdit() || viewMenu != null) return;
        pauseForEdit();
        if (shownError != null) return;
        clearSelection();
        final int request = generation;
        final int[] choice = {-1};
        final float[] percentages = {.25f, .5f, .75f, 1, 1.25f, 1.5f, 2, 3, 4};
        List<String> labels = new ArrayList<>();
        labels.add("Fit width"); labels.add("Fit height"); labels.add("Fit page");
        for (float percentage : percentages) labels.add(Math.round(percentage * 100) + "%");
        labels.add("App settings");
        viewMenu = new AlertDialog.Builder(this)
            .setTitle("Zoom · " + zoomLabel() + " (100% = fit width)")
            .setItems(labels.toArray(new String[0]), (dialog, which) -> choice[0] = which)
            .setNegativeButton("Close", null).create();
        viewMenu.setOnDismissListener(dialog -> {
            viewMenu = null;
            if (!active || request != generation) return;
            session.load((loaded, error) -> {
                if (!active || request != generation) return;
                mergeWindow();
                if (error != null) { showError(error); return; }
                int which = choice[0];
                if (which == percentages.length + 3) {
                    startActivity(new Intent(this, NotebookSettingsActivity.class));
                    overridePendingTransition(0, 0);
                    return;
                }
                if (which >= 0) {
                    zoomFit = which == 0 ? NotebookViewport.Fit.WIDTH
                        : which == 1 ? NotebookViewport.Fit.HEIGHT
                        : which == 2 ? NotebookViewport.Fit.PAGE : NotebookViewport.Fit.CUSTOM;
                    float target = which < 3 ? viewport.fittedZoom(zoomFit, activePage) : percentages[which - 3];
                    zoomAt(target, viewport.width / 2f, viewport.height / 2f, viewport.width / 2f, viewport.height / 2f);
                    if (which == 1 || which == 2) scrollY = viewport.pageScroll(activePage);
                    savePosition = true;
                }
                editing = false; scrolling = true;
                requestWindow();
            });
        });
        showStaticDialog(viewMenu);
        if (viewMenu.getListView() != null) viewMenu.getListView().setSelector(android.R.color.transparent);
    }

    private void returnToLibrary() {
        if (!canEdit()) return;
        pauseForEdit();
        if (shownError != null) return;
        final int request = generation;
        session.load((loaded, error) -> {
            if (!active || request != generation) return;
            if (error != null) { showError(error); return; }
            startActivity(new Intent(this, NotebookLibraryActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
            overridePendingTransition(0, 0);
        });
    }

    private void switchPage(int offset) {
        int target = activePage + offset;
        if (!canEdit() || target < 1 || target > session.pageCount()) return;
        pauseForEdit();
        if (shownError != null) return;
        jumpToPage(target);
    }

    /** Input has already been paused; the window read precedes native restoration. */
    private void jumpToPage(int target) {
        clearSelection();
        editing = false; scrolling = true; savePosition = true;
        scrollY = viewport.pageScroll(target); activePage = target;
        settleRead = true; ++viewportRevision;
        requestWindow();
    }

    private void pageOverview() {
        if (!canEdit()) return;
        pauseForEdit();
        if (shownError != null) return;
        floatingTools.hide();
        final int request = generation;
        // pauseForEdit flushes completed UP events synchronously. This writer barrier
        // includes their saved vectors before a preview can read them.
        session.load((loaded, error) -> {
            if (!active || request != generation) return;
            mergeWindow();
            if (error != null) { showError(error); return; }
            NotebookPageOverview dialog = new NotebookPageOverview(this, session, activePage);
            overview = dialog;
            dialog.setOnDismissListener(closed -> {
                if (overview == dialog) overview = null;
                if (!active || request != generation) return;
                if (session.error() != null) { showError(session.error()); return; }
                if (dialog.pagesChanged()) {
                    clearSelection(); page = session.current();
                    pages.clear(); viewportBuffer.clear(); tiles.clear();
                    viewport = new NotebookViewport(session.pageCount(), page.width, page.height,
                        viewport.width, viewport.height, dp(12), zoom, viewport.pan);
                    jumpToPage(dialog.chosenPage() > 0 ? dialog.chosenPage() : session.pageNumber());
                } else if (dialog.chosenPage() > 0) jumpToPage(dialog.chosenPage());
                else {
                    // Browsing changed no document or viewport state. Reuse the paused
                    // native tracks rather than repainting all ink on Close/Back.
                    editing = false;
                    ink.start();
                    updateControls();
                    scheduleSaveState();
                }
            });
            dialog.show();
        });
    }

    private void addPage() {
        if (!canEdit() || session.pageCount() >= NotebookIndex.MAX_PAGES) return;
        clearSelection();
        pauseForEdit();
        if (shownError != null) return;
        final int request = generation;
        session.addPage(activePage, (updated, error) -> {
            if (!active || request != generation) return;
            if (error != null) { showError(error); return; }
            viewport = new NotebookViewport(session.pageCount(), page.width, page.height,
                viewport.width, viewport.height, dp(12), zoom, viewport.pan);
            editing = false; scrolling = true; savePosition = true;
            activePage = session.pageCount(); scrollY = viewport.pageScroll(activePage);
            settleRead = true; ++viewportRevision;
            requestWindow();
        });
    }

    private void scheduleSaveState() {
        main.removeCallbacks(updateSaveState);
        if (active && !penDown && !dragging && !scrolling && !editing) main.postDelayed(updateSaveState, 1000);
    }
    private void showSaveState() {
        if (!active || penDown || dragging || scrolling || editing) return;
        if (cleanupInk && canEdit()) {
            pauseForEdit(); editing = false; installViewport(); return;
        }
        // Saving stays automatic; routine save counts no longer occupy the status bar.
        updatePageLabel(); updatePaperButton();
        updateControls();
        deviceStatus.refresh();
        if (session.isSaving()) main.postDelayed(updateSaveState, 1000);
    }
    private void showError(IOException error) {
        shownError = error;
        if (selectionTools != null) selectionTools.hide();
        resetSelectionPreview();
        if (ink != null) ink.stop();
        if (floatingTools != null) floatingTools.hide();
        updateControls();
        main.removeCallbacks(updateSaveState);
        saveState.setVisibility(View.VISIBLE);
        statusIslands.show(canvas);
        saveState.setOnClickListener(v -> {
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Couldn’t save the notebook")
                .setMessage("Writing is paused. Your open pages are retained in memory.\n\n"
                    + (error == null ? "Please retry." : error.getMessage()))
                .setNegativeButton("Keep open", null)
                .setPositiveButton("Retry", (choice, which) -> retrySaving()).create();
            showStaticDialog(dialog);
        });
    }
    private void retrySaving() {
        final int request = ++generation;
        session.retry((restored, error) -> {
            if (!active || request != generation) return;
            if (error != null) { showError(error); return; }
            saveState.setOnClickListener(null);
            saveState.setVisibility(View.GONE);
            statusIslands.refresh();
            if (restored == null) { loadPage(); return; }
            openCanvas(restored); scheduleSaveState();
        });
    }

    private void choosePaper() {
        if (!canEdit()) return;
        NotebookPage.Template selected = pages.get(activePage).template == NotebookPage.Template.LINED
            ? NotebookPage.Template.BLANK : NotebookPage.Template.LINED;
        pauseForEdit();
        if (shownError != null) return;
        final int request = generation;
        session.setTemplate(activePage, selected, (updated, error) -> finishEdit(request, updated, error));
    }

    private void pageInformation() {
        if (!canEdit()) return;
        pauseForEdit();
        if (shownError != null) return;
        floatingTools.hide();
        final int request = generation;
        NotebookPage current = pages.get(activePage);
        String message = current == null ? "Your page is opening." :
            session.notebookName() + "\nPage " + activePage + " of " + session.pageCount() + " · "
            + (current.template == NotebookPage.Template.LINED ? "Lined" : "Blank")
            + "\n" + current.strokes.size() + " strokes\n"
            + (session.error() != null ? "Some writing has not been saved."
                : session.isSaving() ? "Saving your latest writing…" : "Your completed strokes are saved on this tablet.")
            + "\n\nDrag with one finger to scroll and pan. Pinch with two fingers to zoom around their midpoint. Tap the notebook title or zoom percentage for Fit width, Fit height, Fit page, zoom presets, and App settings. New notes start at Fit width; 100% means Fit width. Write with the stylus. Tap the grid icon for thumbnails and page-number jumps. Use the left/right arrows for adjacent pages. The page-plus icon adds an empty page at the end. The paper icon changes this page’s Blank/Lined background. The books icon returns to your library."
            + "\n\nEraser draws a temporary grey trail. Lift the pen to remove whole ink strokes touched by it, together with the trail. Tap the selected Eraser icon to adjust its width (4–240). Current eraser: " + NotebookWidthRange.ERASER.label(eraserWidth) + ". Each page remembers up to 100 Undo/Redo edits while the app process stays open."
            + "\n\nLasso draws a temporary dashed line. Lift the stylus to close the curve automatically. Drag inside with a finger or stylus to move the selected ink. Copy, Duplicate, Cut, Delete, and Done appear beside the region. Done or a tap outside clears the selection; tap the selected Lasso icon afterward for Paste or Select all. Copied ink stays inside Scriflow until the app restarts. Selection and movement stay on one page."
            + "\n\nNew writing or a successful edit clears Redo. Changing paper keeps your stroke history.";
        message += "\n\nTap the dotted grip to collapse or expand the floating tools; drag it to move them, even when collapsed. The direction icon beside it switches between one row and one column. The thicker circle marks the selected tool. The page icon changes Blank/Lined paper; the grid opens the page overview."
            + "\n\nTap the selected Pen icon to open a small width slider. Slide the point and release to apply; tap outside or press Back to cancel. Current thickness: " + NotebookPenConfig.label(penWidth) + ". The small line below the pencil previews its width. New strokes use that width; existing writing keeps its original thickness."
            + "\n\nExport PDF saves all pages in this notebook, including their paper backgrounds and handwriting.";
        final boolean[] exporting = {false};
        AlertDialog info = new AlertDialog.Builder(this).setTitle("This page")
            .setMessage(message).setPositiveButton("Done", null)
            .setNegativeButton("Reset tools", (choice, which) -> {
                toolsX = 0.5f; toolsY = 0f;
                placeToolsBelowStatus = true;
                floatingTools.setVertical(false);
                floatingTools.setCollapsed(false);
                getPreferences(MODE_PRIVATE).edit().putFloat("toolsX", toolsX).putFloat("toolsY", toolsY)
                    .putBoolean("toolsVertical", false).putBoolean("toolsCollapsed", false).apply();
            })
            .setNeutralButton("Export PDF", (choice, which) -> {
                exporting[0] = true;
                session.load((loaded, error) -> {
                    if (!active || request != generation) return;
                    if (error != null) { showError(error); return; }
                    startActivity(new Intent(this, NotebookPdfExportActivity.class)
                        .putExtra("notebookId", session.notebookId())
                        .putExtra("notebookName", session.notebookName())
                        .putExtra("pageCount", session.pageCount()));
                    overridePendingTransition(0, 0);
                });
            }).create();
        info.setOnDismissListener(dialog -> {
            if (!exporting[0] && active && request == generation)
                session.load((loaded, error) -> finishEdit(request, loaded, error));
        });
        showStaticDialog(info);
    }

    private void showStaticDialog(AlertDialog dialog) {
        dialog.create(); // Apply theme defaults before overriding their dim/fade attributes.
        if (dialog.getWindow() != null) {
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            dialog.getWindow().setDimAmount(0f);
            dialog.getWindow().setWindowAnimations(0);
        }
        dialog.show();
        for (int which : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(which);
            if (button != null) NotebookButton.makeStatic(button);
        }
    }

    @Override protected void onPause() {
        boolean saveViewport = overview == null || !overview.pagesChanged() && !overview.hasPendingPageChange();
        // Flush completed pen-UP events against the current layout before retiring its callbacks.
        if (ink != null) ink.pauseForEdit();
        captureViewCenter();
        active = false; ++generation;
        NotebookSettings.setPenDown(this, false);
        deviceStatus.stop();
        statusIslands.hide();
        pendingToolAction = null; toolsContact = false;
        clearSelection(); resetSelectionPreview();
        if (lassoMenu != null) { lassoMenu.dismiss(); lassoMenu = null; }
        if (viewMenu != null) { viewMenu.dismiss(); viewMenu = null; }
        if (floatingTools != null) floatingTools.hide();
        if (overview != null) { overview.dismiss(); overview = null; }
        if (widthPopup != null) { widthPopup.dismiss(); widthPopup = null; }
        main.removeCallbacks(updateSaveState);
        disposeCanvas();
        if (!saveViewport) { viewport = null; restoreCenter = false; }
        if (saveViewport) persistPosition();
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        captureViewCenter();
        state.putInt("viewNotebookId", viewNotebookId);
        state.putFloat("zoom", zoom);
        state.putString("zoomFit", zoomFit.name());
        state.putBoolean("viewCenter", restoreCenter);
        state.putInt("viewCenterPage", restoreCenterPage);
        state.putFloat("viewCenterX", restoreCenterX); state.putFloat("viewCenterY", restoreCenterY);
        state.putBoolean("eraser", tool == NotebookInk.Tool.ERASER);
        state.putBoolean("lasso", tool == NotebookInk.Tool.LASSO);
        super.onSaveInstanceState(state);
    }
    private void disposeCanvas() {
        main.removeCallbacks(previewFrame);
        clearSelection(); resetSelectionPreview();
        dragging = false; pinching = false; selectionViewportGesture = false;
        if (zoomBadge != null) zoomBadge.dismiss();
        penDown = false;
        if (floatingTools != null) floatingTools.hide();
        if (ink != null) { ink.release(); ink = null; }
        if (canvas != null) canvas.removeAllViews();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override protected void onDestroy() {
        if (statusIslands != null) statusIslands.hide();
        if (floatingTools != null) floatingTools.dispose();
        if (selectionTools != null) selectionTools.hide();
        super.onDestroy();
    }
    @Override public void onBackPressed() {
        if (widthPopup != null) { widthPopup.cancel(); return; }
        if (selectionDrag.isActive()) {
            resetSelectionPreview(); editing = false; installViewport(); return;
        }
        if (selectionOutline != null && canEdit()) {
            pauseForEdit(); clearSelection(); editing = false; installViewport(); return;
        }
        super.onBackPressed(); overridePendingTransition(0, 0);
    }
}
