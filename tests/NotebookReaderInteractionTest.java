package org.eink.inkprobe.notebook;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Production reader callbacks with synthetic touches, asynchronous pages and drawing adapters. */
public final class NotebookReaderInteractionTest {
    public static void main(String[] args) throws Exception {
        String reader = new String(Files.readAllBytes(new File(
            "app/src/main/java/org/eink/inkprobe/notebook/NotebookReader.java").toPath()), StandardCharsets.UTF_8);
        String fixture = String.join("\n",
            "package org.eink.inkprobe.notebook;import java.io.*;import java.util.*;import org.eink.inkprobe.notebook.*;",
            "public class ReaderInteractionFixture {",
            "static class Paint {static class Style {static final int FILL=0,STROKE=1;}void setStyle(int n){}void setColor(int n){}void setTextSize(float n){}void setStrokeWidth(float n){}}",
            "static class Color {static final int WHITE=0xffffffff,DKGRAY=0xff555555;}",
            "static class Path {}",
            "static class Bitmap {} static class RectF {RectF(float a,float b,float c,float d){}}",
            "static class Canvas {int paths,scales,images,clears;void drawBitmap(Bitmap b,Object src,RectF dest,Paint paint){images++;}int save(){return 1;}void translate(float x,float y){}void scale(float x,float y){scales++;}void clipRect(float a,float b,float c,float d){}void drawColor(int n){clears++;}void drawText(String s,float x,float y,Paint p){}void drawLine(float a,float b,float c,float d,Paint p){}void drawPath(Path path,Paint paint){paths++;}void restoreToCount(int n){}}",
            "static class NotebookStrokeRenderer {static int outlines;static class Cache extends IdentityHashMap<NotebookPage.Stroke,Path>{Cache(int n){}void prepare(Collection<NotebookPage> pages){Set<NotebookPage.Stroke> keep=new HashSet<>();for(NotebookPage p:pages)keep.addAll(p.strokes);keySet().retainAll(keep);}Path path(NotebookPage.Stroke s,java.util.function.BooleanSupplier c){Path p=get(s);if(p==null){outlines++;p=new Path();put(s,p);}return p;}}}",
            "static class NotebookImages {static void draw(Canvas c,NotebookPage p){}}",
            "static class MotionEvent {static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_UP=6,TOOL_TYPE_STYLUS=2,TOOL_TYPE_ERASER=4;int action,type=1;float x,y;MotionEvent(int a,float x,float y){action=a;this.x=x;this.y=y;}int getActionMasked(){return action;}int getToolType(int p){return type;}float getX(){return x;}float getY(){return y;}float getX(int i){return x;}float getY(int i){return y;}int getPointerCount(){return 1;}int getActionIndex(){return 0;}}",
            "static class ScaleGestureDetector {boolean progress;float factor=2;float getFocusX(){return 50;}float getFocusY(){return 100;}float getScaleFactor(){return factor;}boolean isInProgress(){return progress;}void onTouchEvent(MotionEvent e){}}",
            "static class NotebookSession {static class PreviewRequest {boolean cancelled;void cancel(){cancelled=true;}}interface Callback {void done(NotebookPage p,IOException e);}final ArrayDeque<Runnable> queue=new ArrayDeque<>();final List<Integer> reads=new ArrayList<>();PreviewRequest companionPage(int book,int n,Callback callback){PreviewRequest r=new PreviewRequest();reads.add(n);queue.add(()->{if(!r.cancelled)callback.done(page(n),null);});return r;}static NotebookPage page(int n){return NotebookPage.empty().withSize(200,300).withPdfPage(n).withStroke(new NotebookPage.Stroke(1,2,0xff000000,Collections.singletonList(new NotebookPage.Point(20,30,1,1))));}}",
            "static class Pdf {boolean available=true;List<NotebookPdfTiles.Tile> tiles=new ArrayList<>();boolean prepare(File f,List<NotebookPdfTiles.Tile> t,Set<Integer> p){tiles=t;return available;}boolean draw(Canvas c,int p,int w,int h,Paint paint){return true;}void clear(){tiles.clear();}}",
            "static class Listener {int activations,saves;boolean allowed=true;boolean allowInput(){return allowed;}void activate(){activations++;}void save(){saves++;}}",
            "final NotebookSession session=new NotebookSession();final NotebookWorkspace workspace=new NotebookWorkspace();final NotebookWorkspace.Pane pane=workspace.add(1,NotebookWorkspace.Position.FLOATING);final Listener listener=new Listener();final Pdf pdf=new Pdf();final ScaleGestureDetector scale=new ScaleGestureDetector();final Paint paint=new Paint();",
            "final Map<Integer,NotebookPage> pages=new LinkedHashMap<>();final NotebookStrokeRenderer.Cache paths=new NotebookStrokeRenderer.Cache(32768);final Set<Integer> errors=new HashSet<>();List<NotebookViewport.Size> sizes=Arrays.asList(new NotebookViewport.Size(200,300),new NotebookViewport.Size(200,300),new NotebookViewport.Size(200,300),new NotebookViewport.Size(200,300),new NotebookViewport.Size(200,300));",
            "NotebookSession.PreviewRequest metadataRequest,pageRequest;NotebookViewport viewport;File source;Bitmap retainedFrame;boolean pdfReady=true,pdfFailed;float scrollY,lastX,lastY;boolean opened=true,loadingPage,stylusContact,contactAccepted;int epoch,width=100,height=200;String message=\"Loading…\";",
            "int getWidth(){return width;}int getHeight(){return height;}int dp(int n){return n;}void invalidate(){}boolean performClick(){return true;}",
            method(reader, "private void configure("), method(reader, "void remember("), method(reader, "private void changed("),
            method(reader, "private void requestPages("), method(reader, "private void preparePdf("), method(reader, "private boolean frameReady("), method(reader, "void retainFrame("),
            method(reader, "public boolean onScale("), method(reader, "public boolean onTouchEvent("),
            method(reader, "protected void onDraw("), method(reader, "private void label("),
            method(reader, "protected void onSizeChanged("), method(reader, "void hide("),
            "void drain(){for(int n=0;!session.queue.isEmpty();n++){if(n>30)throw new AssertionError(\"load loop\");session.queue.remove().run();}}",
            "void touch(int action,float x,float y){onTouchEvent(new MotionEvent(action,x,y));}",
            "static void check(boolean b,String s){if(!b)throw new AssertionError(s);}",
            "public static void run(){",
            "ReaderInteractionFixture f=new ReaderInteractionFixture();f.configure();check(f.viewport.zoom==1&&f.scrollY==0,\"new note starts fit width at page top\");f.drain();check(f.pages.containsKey(1)&&f.pages.containsKey(2)&&f.session.reads.size()==2,\"viewport loads all visible pages without page buttons\");",
            "f.touch(0,50,150);f.touch(2,50,-200);f.touch(1,50,-200);f.drain();check(f.scrollY==350&&f.pane.page==3&&!f.pages.containsKey(1),\"finger scroll crosses page boundaries and trims offscreen pages\");check(f.listener.activations==0,\"finger navigation never captures editing focus\");",
            "int page=f.viewport.centeredPage(f.scrollY);float logical=(100-f.viewport.pageTop(page,f.scrollY))/f.viewport.scale(page);f.onScale(f.scale);f.drain();check(f.viewport.zoom==2&&Math.abs((100-f.viewport.pageTop(page,f.scrollY))/f.viewport.scale(page)-logical)<.01f,\"pinch preserves its logical page anchor\");",
            "check(!f.pdf.tiles.isEmpty()&&f.pdf.tiles.get(0).fullWidth==200,\"PDF requests sharpen to zoomed screen resolution\");",
            "Canvas a=new Canvas();f.onDraw(a);int outlines=NotebookStrokeRenderer.outlines;check(a.paths>0&&a.scales>0,\"saved ink draws as vectors in page coordinates\");Canvas b=new Canvas();f.onDraw(b);check(NotebookStrokeRenderer.outlines==outlines&&b.paths==a.paths,\"repaint reuses vector paths rather than flattened previews\");",
            "float z=f.pane.zoom,x=f.pane.centerX,y=f.pane.centerY;int p=f.pane.page;f.width=150;f.height=250;f.onSizeChanged(150,250,100,200);check(f.viewport.zoom==z&&f.pane.page==p&&Math.abs(f.pane.centerX-x)<.01&&Math.abs(f.pane.centerY-y)<.01,\"resizing keeps independent logical reading center and width-relative zoom\");",
            "MotionEvent down=new MotionEvent(0,40,60);down.type=2;MotionEvent up=new MotionEvent(1,40,60);up.type=2;f.onTouchEvent(down);check(f.listener.activations==0,\"stylus tap consumes contact before handoff\");f.onTouchEvent(up);check(f.listener.activations==1,\"stylus tap inside note activates on release\");",
            "f.listener.allowed=false;float blocked=f.scrollY;f.touch(0,50,100);f.touch(2,50,0);f.touch(1,50,0);check(f.scrollY==blocked,\"finger contact during a native edit is ignored for its entire stream\");f.listener.allowed=true;",
            "List<NotebookViewport.Size> metadata=f.sizes;f.hide();check(f.pages.isEmpty()&&f.paths.isEmpty()&&f.viewport==null,\"minimized note releases display caches\");f.opened=true;f.sizes=metadata;f.configure();f.drain();check(f.viewport.zoom==z&&f.pane.page==p,\"restoring a minimized note retains its own page and zoom\");",
            "ReaderInteractionFixture held=new ReaderInteractionFixture();held.retainFrame(new Bitmap());held.configure();Canvas loading=new Canvas();held.onDraw(loading);check(loading.images==1&&loading.clears==0,\"handoff retains complete contents instead of painting a white loading frame\");held.pdf.available=false;held.drain();Canvas waitingPdf=new Canvas();held.onDraw(waitingPdf);check(waitingPdf.images==1&&waitingPdf.clears==0,\"retained frame stays until every visible PDF tile is ready\");held.pdf.available=true;held.preparePdf();Canvas ready=new Canvas();held.onDraw(ready);check(ready.images==0&&ready.paths>0&&held.retainedFrame==null,\"complete vector/PDF frame replaces retention without a blank post\");",
            "ReaderInteractionFixture stale=new ReaderInteractionFixture();stale.configure();stale.hide();stale.drain();check(stale.pages.isEmpty()&&stale.viewport==null,\"late page reads cannot resurrect a minimized reader\");",
            "}", "}");
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.ReaderInteractionFixture", fixture);
        System.out.println("NotebookReaderInteraction: 18 assertions passed (production touch, geometry, drawing and queued reads; not native pixel/device verification).");
    }
    private static String method(String source, String signature) { return NotebookWorkspaceActivationTest.method(source, signature); }
}
