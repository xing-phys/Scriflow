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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Durable notebook pages, native pen ink, and document-level editing. */
public final class NotebookActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private NotebookSession session;
    private NotebookInk ink;
    private FrameLayout canvas;
    private TextView saveState, pageLabel;
    private NotebookButton paperButton, penButton, eraserButton, undoButton, redoButton;
    private NotebookButton previousButton, nextButton, addButton;
    private NotebookPage page;
    private IOException shownError;
    private boolean active, penDown, geometryPending, editing;
    private NotebookInk.Tool tool = NotebookInk.Tool.PEN;
    private boolean verifyOnReady;
    private int generation;
    private NotebookViewport viewport;
    private final Map<Integer, NotebookPage> pages = new LinkedHashMap<>();
    private final NotebookViewportBuffer viewportBuffer = new NotebookViewportBuffer();
    private final Map<Integer, Bitmap> tiles = new LinkedHashMap<>();
    private Bitmap preview;
    private float scrollY, fingerY, fingerOrigin;
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
        session = NotebookSession.get(this);
        if (saved != null && saved.getBoolean("eraser", false)) tool = NotebookInk.Tool.ERASER;
        verifyOnReady = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0
            && getIntent().getBooleanExtra("verifyRestore", false);
        getIntent().removeExtra("verifyRestore");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(18), dp(8), dp(12), dp(8));
        NotebookButton title = new NotebookButton(this);
        title.setText("Books"); title.setTextSize(15);
        title.setContentDescription("Back to notebook library");
        title.setOnClickListener(v -> returnToLibrary());
        title.setSingleLine(true);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        previousButton = toolButton(toolbar, "‹", 44, () -> switchPage(-1));
        previousButton.setTextSize(26); previousButton.setContentDescription("Previous page");
        nextButton = toolButton(toolbar, "›", 44, () -> switchPage(1));
        nextButton.setTextSize(26); nextButton.setContentDescription("Next page");
        addButton = toolButton(toolbar, "+ Page", 76, this::addPage);
        penButton = toolButton(toolbar, "Pen", 64, () -> selectTool(NotebookInk.Tool.PEN));
        eraserButton = toolButton(toolbar, "Eraser", 76, () -> selectTool(NotebookInk.Tool.ERASER));
        undoButton = toolButton(toolbar, "Undo", 64, () -> changeHistory(false));
        redoButton = toolButton(toolbar, "Redo", 64, () -> changeHistory(true));
        paperButton = new NotebookButton(this);
        paperButton.setText("Blank"); paperButton.setAllCaps(false); paperButton.setTextSize(15);
        paperButton.setAvailable(false); paperButton.setOnClickListener(v -> choosePaper());
        toolbar.addView(paperButton, new LinearLayout.LayoutParams(dp(106), dp(48)));
        NotebookButton info = new NotebookButton(this);
        info.setText("⋮"); info.setContentDescription("Page information"); info.setTextSize(24);
        info.setOnClickListener(v -> pageInformation());
        toolbar.addView(info, new LinearLayout.LayoutParams(dp(44), dp(48)));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout subline = new LinearLayout(this);
        subline.setGravity(Gravity.CENTER_VERTICAL);
        subline.setPadding(dp(20), 0, dp(20), dp(9));
        pageLabel = new TextView(this);
        pageLabel.setText("Page 1 of 1"); pageLabel.setTextSize(13); pageLabel.setTextColor(0xff555555);
        pageLabel.setSingleLine(true); pageLabel.setEllipsize(TextUtils.TruncateAt.END);
        pageLabel.setPadding(0, dp(8), 0, dp(8));
        pageLabel.setBackgroundColor(Color.WHITE);
        pageLabel.setStateListAnimator(null);
        pageLabel.setDefaultFocusHighlightEnabled(false);
        pageLabel.setOnClickListener(v -> pageOverview());
        subline.addView(pageLabel, new LinearLayout.LayoutParams(0, -2, 1));
        saveState = new TextView(this);
        saveState.setSingleLine(true);
        saveState.setText("Opening…"); saveState.setTextSize(13); saveState.setTextColor(0xff555555);
        subline.addView(saveState, new LinearLayout.LayoutParams(-2, -2));
        root.addView(subline, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(this); divider.setBackgroundColor(0xffbbbbbb);
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        canvas = new FrameLayout(this); canvas.setBackgroundColor(Color.WHITE);
        root.addView(canvas, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume(); active = true;
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
        disposeCanvas();
        shownError = null;
        saveState.setOnClickListener(null);
        editing = false;
        page = loaded; geometryPending = false;
        viewport = null; pages.clear(); viewportBuffer.clear(); tiles.clear(); preview = null;
        fetching = dragging = false; scrolling = true; firstViewport = true;
        settleRead = true; ++viewportRevision;
        activePage = session.pageNumber();
        paperButton.setText(loaded.template == NotebookPage.Template.LINED ? "Lined" : "Blank");
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
                    main.removeCallbacks(updateSaveState);
                    if (!down) scheduleSaveState();
                }
                @Override public void onReady() {
                    if (!active || request != generation) return;
                    if (session.error() != null) { showError(session.error()); return; }
                    updateControls();
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
                @Override public void onFingerEvent(MotionEvent event) {
                    if (active && request == generation) finger(event);
                }
            });
            ink.setTool(tool);
            canvas.addView(ink.view, new FrameLayout.LayoutParams(-1, -1));
            // Restore even a retained unsaved page. onReady stops input synchronously when
            // storage has failed, leaving the full open page visible while Retry is offered.
            ink.start();
        } catch (Exception error) { showError(new IOException("Could not open the writing surface", error)); }
    }

    private void configureViewport(int width, int height) {
        viewport = new NotebookViewport(session.pageCount(), page.width, page.height, width, height, dp(12));
        NotebookScrollStore.State position = reopenPosition == null ? session.scrollPosition() : reopenPosition;
        reopenPosition = null;
        scrollY = position == null ? viewport.pageScroll(session.pageNumber())
            : viewport.scrollForAnchor(position.page, position.fraction);
        activePage = viewport.centeredPage(scrollY);
        requestWindow();
    }

    private List<Integer> wantedPages() {
        List<Integer> visible = viewport.visiblePages(scrollY);
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
                openCanvas(seed);
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
        String text = current.template == NotebookPage.Template.LINED ? "Lined" : "Blank";
        if (!text.contentEquals(paperButton.getText())) paperButton.setText(text);
    }

    private void persistPosition() {
        if (viewport == null || shownError != null) return;
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
            editing = false; scrolling = true; dragging = true; moved = false;
            fingerId = event.getPointerId(changed);
            fingerOrigin = fingerY = event.getY(changed);
            return;
        }
        if (!dragging) return;
        int index = event.findPointerIndex(fingerId);
        if (action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(changed) == fingerId) {
            for (int i = 0; i < event.getPointerCount(); i++) {
                if (i == changed || event.getToolType(i) != MotionEvent.TOOL_TYPE_FINGER) continue;
                fingerId = event.getPointerId(i); fingerY = event.getY(i);
                if (!moved) fingerOrigin = fingerY;
                return;
            }
        }
        if (action == MotionEvent.ACTION_MOVE && index >= 0) {
            float y = event.getY(index);
            if (!moved && Math.abs(y - fingerOrigin) < dp(4)) return;
            moved = true;
            scrollY = viewport.clampScroll(scrollY + fingerY - y); fingerY = y;
            ++viewportRevision;
            schedulePreview(); requestWindow();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || index < 0
                || action == MotionEvent.ACTION_POINTER_UP && event.getPointerId(changed) == fingerId) {
            dragging = false; fingerId = -1;
            main.removeCallbacks(previewFrame);
            if (!moved && !settleRead && !firstViewport) {
                scrolling = false; ink.start(); scheduleSaveState();
            } else {
                activePage = viewport.centeredPage(scrollY); savePosition |= moved;
                settleRead = true; ++viewportRevision;
                requestWindow();
            }
        }
    }

    private void schedulePreview() {
        if (active && dragging && !main.hasCallbacks(previewFrame)) main.postDelayed(previewFrame, 24);
    }

    private void drawPreview() {
        if (!active || !dragging || viewport == null || shownError != null) return;
        try {
            if (preview == null) preview = Bitmap.createBitmap(viewport.width, viewport.height, Bitmap.Config.RGB_565);
            Canvas drawing = new Canvas(preview);
            drawing.drawColor(0xffdddddd);
            for (int id : viewport.visiblePages(scrollY)) {
                float top = viewport.pageTop(id, scrollY);
                NotebookPage current = pages.get(id);
                if (current == null) {
                    Paint paint = new Paint(); paint.setColor(Color.WHITE);
                    drawing.drawRect(0, top, viewport.width, top + viewport.pageHeight(id) * viewport.scale(id), paint);
                    paint.setColor(0xff555555); paint.setTextSize(dp(16));
                    drawing.drawText("Loading page " + id + "…", dp(20), Math.max(dp(30), top + dp(30)), paint);
                    continue;
                }
                int height = Math.max(1, Math.round(current.height * viewport.scale(id)));
                if (height <= viewport.height * 2) {
                    Bitmap tile = tiles.get(id);
                    if (tile == null) {
                        tile = Bitmap.createBitmap(viewport.width, height, Bitmap.Config.RGB_565);
                        Canvas tileCanvas = new Canvas(tile);
                        tileCanvas.scale(viewport.scale(id), viewport.scale(id));
                        drawPage(tileCanvas, current, true);
                        tiles.put(id, tile);
                    }
                    drawing.drawBitmap(tile, 0, top, null);
                } else {
                    drawing.save(); drawing.translate(0, top);
                    drawing.scale(viewport.scale(id), viewport.scale(id));
                    drawPage(drawing, current, true); drawing.restore();
                }
            }
            ink.showScrollPreview(preview);
        } catch (Exception error) { showError(new IOException("Could not scroll the notebook", error)); }
    }

    private void drawPaper(Canvas drawing, int width, int height) {
        drawing.drawColor(0xffdddddd);
        if (viewport == null) return;
        for (int id : viewport.visiblePages(scrollY)) {
            NotebookPage current = pages.get(id);
            if (current == null) continue;
            drawing.save(); drawing.translate(0, viewport.pageTop(id, scrollY));
            drawing.scale(viewport.scale(id), viewport.scale(id));
            drawPage(drawing, current, false); drawing.restore();
        }
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
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            for (NotebookPage.Stroke stroke : current.strokes) {
                paint.setColor(stroke.color); paint.setStrokeWidth(stroke.width);
                NotebookPage.Point start = stroke.points.get(0);
                if (stroke.points.size() == 1) drawing.drawPoint(start.x, start.y, paint);
                else {
                    Path path = new Path(); path.moveTo(start.x, start.y);
                    for (int i = 1; i < stroke.points.size(); i++) {
                        NotebookPage.Point point = stroke.points.get(i); path.lineTo(point.x, point.y);
                    }
                    drawing.drawPath(path, paint);
                }
            }
        }
        drawing.restore();
    }

    private NotebookButton toolButton(LinearLayout toolbar, String label, int width, Runnable action) {
        NotebookButton button = new NotebookButton(this);
        button.setText(label); button.setAllCaps(false); button.setTextSize(14);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setAvailable(false); button.setOnClickListener(v -> action.run());
        toolbar.addView(button, new LinearLayout.LayoutParams(dp(width), dp(48)));
        return button;
    }

    private boolean canEdit() {
        return active && !editing && !scrolling && !dragging && !fetching && shownError == null && session.error() == null
            && page != null && ink != null && ink.isReady() && !ink.isPenDown();
    }

    private void updateControls() {
        // Availability is logical, with no animated or temporary disabled appearance.
        // canEdit() gates every action while the surface or writer is busy.
        boolean ready = active && shownError == null && session.error() == null
            && page != null && page.isInitialized();
        paperButton.setAvailable(ready);
        penButton.setAvailable(ready); eraserButton.setAvailable(ready);
        undoButton.setAvailable(ready && session.canUndo(activePage));
        redoButton.setAvailable(ready && session.canRedo(activePage));
        previousButton.setAvailable(ready && activePage > 1);
        nextButton.setAvailable(ready && activePage < session.pageCount());
        addButton.setAvailable(ready && session.pageCount() < NotebookIndex.MAX_PAGES);
        String pen = tool == NotebookInk.Tool.PEN ? "✓ Pen" : "Pen";
        String eraser = tool == NotebookInk.Tool.ERASER ? "✓ Eraser" : "Eraser";
        if (!pen.contentEquals(penButton.getText())) penButton.setText(pen);
        if (!eraser.contentEquals(eraserButton.getText())) eraserButton.setText(eraser);
    }

    private void selectTool(NotebookInk.Tool selected) {
        if (!canEdit() || tool == selected) return;
        tool = selected;
        ink.setTool(selected);
        updateControls();
    }

    private void changeHistory(boolean redo) {
        if (!canEdit() || !(redo ? session.canRedo(activePage) : session.canUndo(activePage))) return;
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

    private void pauseForEdit() {
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
        String shortName = name.codePointCount(0, name.length()) > 24
            ? name.substring(0, name.offsetByCodePoints(0, 24)) + "…" : name;
        String text = shortName + " · Page " + activePage + " of " + session.pageCount() + " · Overview";
        if (!text.contentEquals(pageLabel.getText())) pageLabel.setText(text);
        pageLabel.setContentDescription(session.notebookName() + ", page " + activePage
            + " of " + session.pageCount() + ", open page overview");
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
        editing = false; scrolling = true; savePosition = true;
        scrollY = viewport.pageScroll(target); activePage = target;
        settleRead = true; ++viewportRevision;
        requestWindow();
    }

    private void pageOverview() {
        if (!canEdit()) return;
        pauseForEdit();
        if (shownError != null) return;
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
                if (dialog.chosenPage() > 0) jumpToPage(dialog.chosenPage());
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
        pauseForEdit();
        if (shownError != null) return;
        final int request = generation;
        session.addPage(activePage, (updated, error) -> {
            if (!active || request != generation) return;
            if (error != null) { showError(error); return; }
            viewport = new NotebookViewport(session.pageCount(), page.width, page.height,
                viewport.width, viewport.height, dp(12));
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
        NotebookPage current = pages.get(activePage);
        int strokes = current == null ? 0 : current.strokes.size();
        String text = shownError != null || session.error() != null ? "Not saved · tap here"
            : session.isSaving() ? "Saving…"
            : strokes == 0 ? "Autosave on"
            : "Saved · " + strokes + (strokes == 1 ? " stroke" : " strokes");
        if (!text.contentEquals(saveState.getText())) saveState.setText(text);
        updatePageLabel(); updatePaperButton();
        updateControls();
        if (session.isSaving()) main.postDelayed(updateSaveState, 1000);
    }
    private void showError(IOException error) {
        shownError = error;
        if (ink != null) ink.stop();
        updateControls();
        main.removeCallbacks(updateSaveState);
        saveState.setText("Not saved · tap here");
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
        final int request = generation;
        NotebookPage current = pages.get(activePage);
        String message = current == null ? "Your page is opening." :
            session.notebookName() + "\nPage " + activePage + " of " + session.pageCount() + " · "
            + (current.template == NotebookPage.Template.LINED ? "Lined" : "Blank")
            + "\n" + current.strokes.size() + " strokes\n"
            + (session.error() != null ? "Some writing has not been saved."
                : session.isSaving() ? "Saving your latest writing…" : "Your completed strokes are saved on this tablet.")
            + "\n\nDrag with one finger to scroll and stop anywhere. Write with the stylus. Tap the page indicator for thumbnails and page-number jumps. Use ‹ and › for adjacent pages. + Page adds an empty page at the end. Tap Blank/Lined to change this page’s paper."
            + "\n\nEraser removes whole strokes after you lift the pen. Each page remembers up to 100 Undo/Redo edits while the app process stays open."
            + "\n\nNew writing or a successful erase clears Redo. Changing paper keeps your stroke history.";
        AlertDialog info = new AlertDialog.Builder(this).setTitle("This page")
            .setMessage(message).setPositiveButton("Done", null).create();
        info.setOnDismissListener(dialog -> {
            if (active && request == generation)
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
        active = false; ++generation;
        if (overview != null) { overview.dismiss(); overview = null; }
        main.removeCallbacks(updateSaveState);
        disposeCanvas();
        persistPosition();
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("eraser", tool == NotebookInk.Tool.ERASER);
        super.onSaveInstanceState(state);
    }
    private void disposeCanvas() {
        main.removeCallbacks(previewFrame);
        dragging = false;
        penDown = false;
        if (ink != null) { ink.release(); ink = null; }
        if (canvas != null) canvas.removeAllViews();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onBackPressed() {
        super.onBackPressed(); overridePendingTransition(0, 0);
    }
}
