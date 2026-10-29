package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Continuous read-only viewport using original vector ink and screen-resolution PDF tiles. */
final class NotebookReader extends View {
    interface Listener { void activate(); void save(); boolean allowInput(); }
    private final NotebookSession session;
    private final NotebookWorkspace.Pane pane;
    private final Listener listener;
    private final NotebookPdfBackgrounds pdf;
    private final NotebookFigureLayer images;
    private final ScaleGestureDetector scale;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Map<Integer, NotebookPage> pages = new LinkedHashMap<>();
    private final NotebookStrokeRenderer.Cache paths = new NotebookStrokeRenderer.Cache(32768);
    private final Set<Integer> errors = new HashSet<>();
    private List<NotebookViewport.Size> sizes = Collections.emptyList();
    private NotebookSession.PreviewRequest metadataRequest, pageRequest;
    private NotebookViewport viewport;
    private File source;
    private Bitmap retainedFrame;
    private boolean pdfReady = true, pdfFailed, imagesReady = true, imagesFailed;
    private float scrollY, lastX, lastY;
    private boolean opened, loadingPage, stylusContact, contactAccepted;
    private int epoch;
    private String message = "Loading…";

    NotebookReader(Context context, NotebookSession session, NotebookWorkspace.Pane pane, Listener listener) {
        super(context); this.session = session; this.pane = pane; this.listener = listener;
        setContentDescription("Note; scroll and pinch with fingers, tap with stylus to edit");
        pdf = new NotebookPdfBackgrounds(context, new NotebookPdfBackgrounds.Listener() {
            public void changed() { preparePdf(); invalidate(); }
            public void failed(IOException error) { pdfFailed = true; message = "PDF unavailable. Reopen this note to retry."; invalidate(); }
        }, false);
        images = new NotebookFigureLayer(new NotebookFigureLayer.Listener() {
            public void changed() { preparePdf(); invalidate(); }
            public void failed(IOException error) { imagesFailed = true; message = "Figure unavailable. Reopen this note to retry."; invalidate(); }
        });
        scale = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector detector) {
                if (viewport == null) return false;
                float x = detector.getFocusX(), y = detector.getFocusY();
                int page = viewport.nearestPage(y, scrollY);
                float logicalX = (x - viewport.pageLeft(page)) / viewport.scale(page);
                float logicalY = (y - viewport.pageTop(page, scrollY)) / viewport.scale(page);
                float minimum = Math.min(.1f, viewport.fittedZoom(NotebookViewport.Fit.PAGE, page));
                float maximum = Math.max(8f, viewport.fittedZoom(NotebookViewport.Fit.HEIGHT, page));
                float zoom = NotebookWorkspace.clamp(viewport.zoom * detector.getScaleFactor(), minimum, maximum);
                viewport = viewport.withView(zoom, 0);
                viewport = viewport.withView(zoom, viewport.panForPoint(page, logicalX, x));
                scrollY = viewport.scrollForPoint(page, logicalY, y);
                changed(); return true;
            }
        });
    }
    /** A temporary complete frame, never a zoom/render source. Original vectors replace it when ready. */
    void retainFrame(Bitmap frame) { retainedFrame = frame; invalidate(); }

    void open() {
        if (opened) return;
        opened = true; message = "Loading…";
        final int request = ++epoch;
        metadataRequest = session.companionMetadata(pane.notebookId, (loaded, file, error) -> {
            if (!opened || request != epoch) return;
            if (error != null || loaded.isEmpty()) { retainedFrame = null; message = "Note unavailable. Reopen it to retry."; invalidate(); return; }
            sizes = loaded; source = file;
            pane.page = Math.max(1, Math.min(sizes.size(), pane.page));
            configure();
        });
    }
    private void configure() {
        if (sizes.isEmpty() || getWidth() < 1 || getHeight() < 1) return;
        viewport = new NotebookViewport(sizes, getWidth(), getHeight(), dp(12), pane.hasView ? pane.zoom : 1, 0);
        if (pane.hasView) {
            viewport = viewport.withView(viewport.zoom, viewport.panForPoint(pane.page,
                pane.centerX * viewport.pageWidth(pane.page), getWidth() / 2f));
            scrollY = viewport.scrollForPoint(pane.page, pane.centerY * viewport.pageHeight(pane.page), getHeight() / 2f);
        } else scrollY = viewport.pageScroll(pane.page);
        changed();
    }
    @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        remember(); configure();
    }
    void remember() {
        if (viewport == null) return;
        int number = viewport.centeredPage(scrollY);
        pane.page = number; pane.zoom = viewport.zoom; pane.hasView = true;
        pane.centerX = (viewport.width / 2f - viewport.pageLeft(number)) / (viewport.scale(number) * viewport.pageWidth(number));
        pane.centerY = (viewport.height / 2f - viewport.pageTop(number, scrollY)) / (viewport.scale(number) * viewport.pageHeight(number));
    }
    private void changed() { remember(); requestPages(); preparePdf(); invalidate(); }
    private void requestPages() {
        if (!opened || viewport == null) return;
        List<Integer> visible = viewport.visiblePages(scrollY);
        pages.keySet().retainAll(visible); errors.retainAll(visible);
        if (loadingPage) return;
        for (int number : visible) if (!pages.containsKey(number) && !errors.contains(number)) {
            loadingPage = true; final int request = epoch;
            pageRequest = session.companionPage(pane.notebookId, number, (page, error) -> {
                if (!opened || request != epoch) return;
                loadingPage = false;
                if (error != null) errors.add(number);
                else if (viewport.visiblePages(scrollY).contains(number)) pages.put(number, page);
                requestPages(); preparePdf(); invalidate();
            });
            break;
        }
    }
    private void preparePdf() {
        if (viewport == null || !opened) return;
        List<NotebookPdfTiles.Tile> tiles = new ArrayList<>(); Set<Integer> sources = new HashSet<>();
        for (int number : viewport.visiblePages(scrollY)) {
            NotebookPage page = pages.get(number);
            if (page == null || page.pdfPage <= 0) continue;
            sources.add(page.pdfPage);
            tiles.addAll(NotebookPdfTiles.visible(page.pdfPage, viewport.pageWidth(number) * (double) viewport.scale(number),
                viewport.pageHeight(number) * (double) viewport.scale(number), viewport.pageLeft(number),
                viewport.pageTop(number, scrollY), viewport.width, viewport.height));
        }
        pdfReady = pdf.prepare(source, tiles, sources);
        imagesReady = images.prepare(NotebookFigureLayer.visible(viewport, pages, scrollY));
    }
    private boolean frameReady() {
        if (viewport == null || !pdfReady && !pdfFailed || !imagesReady && !imagesFailed) return false;
        for (int number : viewport.visiblePages(scrollY)) if (!pages.containsKey(number) && !errors.contains(number)) return false;
        return true;
    }
    @Override protected void onDraw(Canvas canvas) {
        if (retainedFrame != null) {
            if (!frameReady()) {
                canvas.drawBitmap(retainedFrame, null, new RectF(0, 0, getWidth(), getHeight()), paint); return;
            }
            retainedFrame = null; // Display lists may still reference the bitmap; let GC reclaim it.
        }
        paths.prepare(pages.values());
        canvas.drawColor(0xffeeeeee);
        if (viewport == null) { label(canvas, message); return; }
        for (int number : viewport.visiblePages(scrollY)) {
            int save = canvas.save();
            canvas.translate(viewport.pageLeft(number), viewport.pageTop(number, scrollY));
            float factor = viewport.scale(number); canvas.scale(factor, factor);
            int width = viewport.pageWidth(number), height = viewport.pageHeight(number);
            canvas.clipRect(0, 0, width, height); canvas.drawColor(Color.WHITE);
            NotebookPage page = pages.get(number);
            if (page != null) {
                if (page.pdfPage > 0) {
                    paint.setStyle(Paint.Style.FILL);
                    if (!pdf.draw(canvas, page.pdfPage, width, height, paint)) {
                        paint.setColor(Color.DKGRAY); paint.setTextSize(16 / factor);
                        canvas.drawText(message.startsWith("PDF") ? message : "Loading PDF…", 12 / factor, 28 / factor, paint);
                    }
                } else if (page.template == NotebookPage.Template.LINED) {
                    paint.setColor(0xffd2d2d2); paint.setStrokeWidth(1); paint.setStyle(Paint.Style.STROKE);
                    for (int y = 72; y < height; y += 72) canvas.drawLine(0, y, width, y, paint);
                }
                images.draw(canvas, page);
                paint.setStyle(Paint.Style.FILL);
                for (NotebookPage.Stroke stroke : page.strokes) paths.draw(canvas, stroke, paint);
            } else {
                paint.setStyle(Paint.Style.FILL); paint.setColor(Color.DKGRAY); paint.setTextSize(16 / factor);
                canvas.drawText(errors.contains(number) ? "Page unavailable. Reopen to retry." : "Loading…", 12 / factor, 28 / factor, paint);
            }
            canvas.restoreToCount(save);
        }
    }
    private void label(Canvas canvas, String text) {
        paint.setStyle(Paint.Style.FILL); paint.setColor(Color.DKGRAY); paint.setTextSize(dp(14));
        canvas.drawText(text, dp(12), getHeight() / 2f, paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked(), type = event.getToolType(0);
        if (action == MotionEvent.ACTION_DOWN) contactAccepted = listener.allowInput();
        if (!contactAccepted) return true;
        if (type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER || stylusContact) {
            if (action == MotionEvent.ACTION_DOWN) stylusContact = true;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                boolean activate = stylusContact && action == MotionEvent.ACTION_UP; stylusContact = false;
                if (activate) { remember(); listener.activate(); }
            }
            return true;
        }
        scale.onTouchEvent(event);
        if (action == MotionEvent.ACTION_DOWN) { lastX = event.getX(); lastY = event.getY(); }
        else if (action == MotionEvent.ACTION_MOVE) {
            if (viewport != null && !scale.isInProgress() && event.getPointerCount() == 1) {
                viewport = viewport.withView(viewport.zoom, viewport.pan - (event.getX() - lastX));
                scrollY = viewport.clampScroll(scrollY - (event.getY() - lastY)); changed();
            }
            lastX = event.getX(); lastY = event.getY();
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            int index = event.getActionIndex() == 0 ? 1 : 0;
            lastX = event.getX(index); lastY = event.getY(index);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            remember(); listener.save(); if (action == MotionEvent.ACTION_UP) performClick();
        }
        return true;
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    void setCacheBudget(NotebookCacheBudget budget) { paths.setPointLimit(budget.readerPoints); pdf.setCachePixelLimit(budget.pdfPixelsPerNote); }
    void trimCaches() { paths.clear(); pdf.trimMemory(); }
    void hide() {
        remember(); opened = false; ++epoch; loadingPage = false; stylusContact = false;
        if (metadataRequest != null) metadataRequest.cancel();
        if (pageRequest != null) pageRequest.cancel();
        viewport = null; sizes = Collections.emptyList(); pages.clear(); paths.clear(); errors.clear(); pdf.clear(); images.clear();
        retainedFrame = null; pdfReady = imagesReady = true; pdfFailed = imagesFailed = false;
    }
    void close() { hide(); pdf.close(); images.close(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
