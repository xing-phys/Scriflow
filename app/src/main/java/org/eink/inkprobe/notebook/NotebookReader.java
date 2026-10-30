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
import java.util.UUID;

/** Continuous read-only viewport using original vector ink and screen-resolution PDF tiles. */
final class NotebookReader extends View {
    interface Listener { void textBoundsChanged(); void lookup(String word); void interact(); void activate(); void figure(NotebookFigureHit.Hit hit); void save(); boolean allowInput(); int writingRevision(); }
    private final NotebookSession session;
    private final NotebookWorkspace.Pane pane;
    private final Listener listener;
    private final NotebookPdfBackgrounds pdf;
    private final NotebookFigureLayer images;
    private final ScaleGestureDetector scale;
    private final NotebookLinkTapGesture figureTap = new NotebookLinkTapGesture();
    private final float pixelsPerMm;
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
    private float downX,downY;
    private boolean pageTurnEligible;
    private int fingerWritingRevision;
    private boolean opened, loadingPage, stylusContact, contactAccepted;
    private int epoch;
    private String message = "Loading…";
    private NotebookPdfReading.Request cropRequest;
    private final Set<UUID> cropFailures=new HashSet<>();
    private NotebookReadingSettings readingSettings;
    private final NotebookPdfPress pdfPress;
    private final NotebookPdfTextTools pdfTextTools;
    private NotebookPdfReading.Request textRequest;
    private boolean navigationMoved;

    NotebookReader(Context context, NotebookSession session, NotebookWorkspace.Pane pane, Listener listener) {
        super(context); this.session = session; this.pane = pane; this.listener = listener;
        float dpi = getResources().getDisplayMetrics().xdpi;
        if (dpi < 80 || dpi > 1000) dpi = getResources().getDisplayMetrics().density * 160;
        pixelsPerMm = dpi / 25.4f;
        pdfTextTools=new NotebookPdfTextTools(this,listener::lookup,listener::textBoundsChanged);
        pdfPress=new NotebookPdfPress(new NotebookPdfPress.Listener(){
            public boolean eligible(float x,float y){return pdfWordEligible(x,y);}
            public int revision(){return listener.writingRevision();}
            public boolean penDown(){return !listener.allowInput();}
            public void held(float x,float y){figureTap.cancel();pageTurnEligible=false;selectPdfWord(x,y);}
        },dp(4),pixelsPerMm);
        setContentDescription("Note; scroll and pinch with fingers, tap a figure with a finger or tap with stylus to edit");
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
        NotebookReadingSettings settings=session.readingSettings(pane.notebookId);
        boolean fitted=viewport!=null&&viewport.singlePage!=0&&Math.abs(viewport.zoom-viewport.fittedZoom(NotebookViewport.Fit.PAGE,pane.page))<.0001;
        boolean changedFlow=readingSettings!=null&&readingSettings.singlePage!=settings.singlePage;
        readingSettings=settings;
        viewport = new NotebookViewport(NotebookPdfReading.layout(sizes,source,settings), getWidth(), getHeight(), dp(12), pane.hasView ? pane.zoom : 1, 0)
            .withSinglePage(settings.singlePage?pane.page:0);
        if(settings.singlePage&&(!pane.hasView||changedFlow||fitted))viewport=viewport.withView(viewport.fittedZoom(NotebookViewport.Fit.PAGE,pane.page),0);
        if(changedFlow&&!settings.singlePage)viewport=viewport.withView(1,0);
        if (pane.hasView) {
            viewport = viewport.withView(viewport.zoom, viewport.panForPoint(pane.page,
                pane.centerX * viewport.pageWidth(pane.page), getWidth() / 2f));
            scrollY = viewport.scrollForPoint(pane.page, pane.centerY * viewport.pageHeight(pane.page), getHeight() / 2f);
        } else scrollY = viewport.pageScroll(pane.page);
        changed();
    }
    void refreshReadingSettings(){remember();cropFailures.clear();if(cropRequest!=null){cropRequest.cancel();cropRequest=null;}configure();}
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
    private void changed() { clearPdfText(); remember(); requestPages(); preparePdf(); invalidate(); }
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
        NotebookReadingSettings settings=session.readingSettings(pane.notebookId);
        pdf.setContrast(settings.contrast);
        if(!prepareCrop(settings)){pdfReady=false;return;}
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
    private boolean prepareCrop(NotebookReadingSettings settings){
        if(source==null||settings.crop==NotebookReadingSettings.Crop.NONE||settings.crop==NotebookReadingSettings.Crop.MANUAL)return true;
        for(int n:viewport.visiblePages(scrollY)){
            NotebookPage page=pages.get(n);if(page==null||page.pdfPage==0||cropFailures.contains(page.id))continue;
            NotebookCropBox area=settings.crop==NotebookReadingSettings.Crop.AUTO?NotebookCropBox.FULL:settings.manual(n,page.id);
            if(NotebookPdfReading.cached(source,page.pdfPage,area)!=null)continue;
            if(cropRequest==null){final int request=epoch;
                cropRequest=NotebookPdfReading.crop(source,page.pdfPage,page.width,page.height,area,(box,error)->{
                    cropRequest=null;if(!opened||request!=epoch)return;
                    if(error!=null)cropFailures.add(page.id);
                    remember();configure();
                });
            }
            return false;
        }
        return true;
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
            canvas.clipRect(viewport.viewLeft(number),viewport.viewTop(number,scrollY),viewport.viewRight(number),viewport.viewBottom(number,scrollY));
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
        if(action==MotionEvent.ACTION_DOWN)clearPdfText();
        if (action == MotionEvent.ACTION_DOWN) contactAccepted = listener.allowInput();
        if (!contactAccepted) { figureTap.cancel(); return true; }
        if (action == MotionEvent.ACTION_DOWN) listener.interact();
        if(pdfPress.touch(event))return true;
        if (type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER || stylusContact) {
            figureTap.cancel();
            if (action == MotionEvent.ACTION_DOWN) stylusContact = true;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                boolean activate = stylusContact && action == MotionEvent.ACTION_UP; stylusContact = false;
                if (activate) { remember(); listener.activate(); }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_DOWN)
            figureTap.begin(event.getX(), event.getY(), event.getEventTime(), dp(4), listener.writingRevision());
        if (action == MotionEvent.ACTION_MOVE) figureTap.move(event.getX(), event.getY());
        for (int i=0;i<event.getPointerCount();i++)
            if (event.getToolType(i) != MotionEvent.TOOL_TYPE_FINGER
                    || NotebookPalmGuard.broadContact(event.getTouchMajor(i), pixelsPerMm)) figureTap.cancel();
        if (event.getPointerCount() != 1 || action == MotionEvent.ACTION_POINTER_DOWN) figureTap.cancel();
        scale.onTouchEvent(event);
        if(event.getPointerCount()!=1||action==MotionEvent.ACTION_POINTER_DOWN)pageTurnEligible=false;
        if (action == MotionEvent.ACTION_DOWN) { lastX = downX=event.getX(); lastY = downY=event.getY();navigationMoved=false;fingerWritingRevision=listener.writingRevision();pageTurnEligible=event.getPointerCount()==1; }
        else if (action == MotionEvent.ACTION_MOVE) {
            if(!navigationMoved&&event.getPointerCount()==1&&Math.hypot(event.getX()-downX,event.getY()-downY)<dp(4))return true;
            navigationMoved=true;
            if (viewport != null && !scale.isInProgress() && event.getPointerCount() == 1) {
                viewport = viewport.withView(viewport.zoom, viewport.pan - (event.getX() - lastX));
                scrollY = viewport.clampScroll(scrollY - (event.getY() - lastY)); changed();
            }
            lastX = event.getX(); lastY = event.getY();
        } else if (action == MotionEvent.ACTION_POINTER_UP) {
            int index = event.getActionIndex() == 0 ? 1 : 0;
            lastX = event.getX(index); lastY = event.getY(index);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if(action==MotionEvent.ACTION_UP&&pageTurnEligible&&fingerWritingRevision==listener.writingRevision()&&listener.allowInput()){
                int target=NotebookPageTurn.target(viewport,event.getX()-downX,event.getY()-downY,dp(48));
                if(target>0){viewport=viewport.forPage(target);viewport=viewport.withView(viewport.fittedZoom(NotebookViewport.Fit.PAGE,target),0);
                    scrollY=viewport.pageScroll(target);figureTap.cancel();changed();listener.save();return true;}
            }
            remember(); listener.save();
            boolean tapped = action == MotionEvent.ACTION_UP && figureTap.finish(event.getX(), event.getY(),
                event.getEventTime(), listener.writingRevision(), !listener.allowInput());
            figureTap.cancel();
            if (tapped) {
                NotebookFigureHit.Hit hit = NotebookFigureHit.find(viewport, pages, scrollY, event.getX(), event.getY());
                if (hit != null) { listener.figure(hit); return true; }
            }
            if (action == MotionEvent.ACTION_UP) performClick();
        }
        return true;
    }
    private boolean pdfWordEligible(float x,float y){
        if(!opened||viewport==null||source==null||!listener.allowInput())return false;
        int n=viewport.pageAt(x,y,scrollY);NotebookPage page=pages.get(n);
        return page!=null&&page.pdfPage>0&&x>=viewport.viewLeft(n)&&x<=viewport.viewRight(n)
            &&NotebookFigureHit.find(viewport,pages,scrollY,x,y)==null;
    }
    private void selectPdfWord(float x,float y){
        if(!pdfWordEligible(x,y))return;
        int n=viewport.pageAt(x,y,scrollY);NotebookPage page=pages.get(n);final int request=epoch,revision=listener.writingRevision();
        NotebookViewport view=viewport;float top=view.pageTop(n,scrollY),left=view.pageLeft(n),scale=view.scale(n);
        float nx=(x-left)/(page.width*scale),ny=(y-top)/(page.height*scale);
        textRequest=NotebookPdfReading.text(source,page.pdfPage,(text,error)->{
            textRequest=null;
            if(!opened||request!=epoch||view!=viewport||revision!=listener.writingRevision()||!listener.allowInput())return;
            if(error!=null){android.widget.Toast.makeText(getContext(),error.getMessage(),android.widget.Toast.LENGTH_LONG).show();return;}
            NotebookPdfText.Word word=text.at(nx,ny);
            if(word==null){if(text.words.isEmpty())android.widget.Toast.makeText(getContext(),"This page has no selectable text layer",android.widget.Toast.LENGTH_SHORT).show();return;}
            pdfTextTools.show(word,new RectF(left+word.left*page.width*scale,top+word.top*page.height*scale,
                left+word.right*page.width*scale,top+word.bottom*page.height*scale));listener.lookup(word.text);
        });
    }
    private void clearPdfText(){
        pdfPress.cancel();if(textRequest!=null){textRequest.cancel();textRequest=null;}pdfTextTools.hide();
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    void setCacheBudget(NotebookCacheBudget budget) { paths.setPointLimit(budget.readerPoints); pdf.setCachePixelLimit(budget.pdfPixelsPerNote); }
    android.graphics.Rect textBounds(){return pdfTextTools.bounds();}
    void trimCaches() { paths.clear(); pdf.trimMemory(); }
    void hide() {
        clearPdfText();
        if(cropRequest!=null){cropRequest.cancel();cropRequest=null;}cropFailures.clear();
        figureTap.cancel(); remember(); opened = false; ++epoch; loadingPage = false; stylusContact = false;
        if (metadataRequest != null) metadataRequest.cancel();
        if (pageRequest != null) pageRequest.cancel();
        viewport = null; sizes = Collections.emptyList(); pages.clear(); paths.clear(); errors.clear(); pdf.clear(); images.clear();
        retainedFrame = null; pdfReady = imagesReady = true; pdfFailed = imagesFailed = false;
    }
    void close() { hide(); pdf.close(); images.close(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
