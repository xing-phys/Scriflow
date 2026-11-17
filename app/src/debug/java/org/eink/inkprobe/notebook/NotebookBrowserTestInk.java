package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import java.util.List;

/** Debug emulator fixture for real note controllers; never substitutes for Huawei pen acceptance. */
final class NotebookBrowserTestInk implements NotebookInkBackend {
    private final View view;
    private final Listener listener;
    private NotebookPage page;
    private PaperRenderer paper;
    private boolean alive = true, wanted, ready, viewPaper, fingerStream;
    private int width, height;
    NotebookBrowserTestInk(Context context, NotebookPage initial, Listener listener) {
        this.listener = listener; page = initial;
        view = new View(context) {
            @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
                super.onSizeChanged(w,h,oldw,oldh); width=w; height=h;
                if (alive && w>0 && h>0) post(() -> { if(alive) { listener.onGeometryReady(width,height); publish(); } });
            }
            @Override protected void onDraw(Canvas canvas) {
                canvas.drawColor(Color.WHITE);
                if (paper != null) paper.draw(canvas,getWidth(),getHeight());
                Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
                for(NotebookPage.Stroke stroke:page.strokes) NotebookStrokeRenderer.draw(canvas,stroke,paint);
                // A visible fixture mark lets screenshots detect a lost note behind PROCESS_TEXT.
                paint.setColor(Color.DKGRAY);paint.setTextSize(24*getResources().getDisplayMetrics().scaledDensity);
                canvas.drawText("Note surface fixture",24,180,paint);
            }
            @Override public boolean onTouchEvent(MotionEvent event) {
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN)fingerStream=alive&&ready;
                if(alive&&fingerStream){
                    // Android's adb input swipe reports TOOL_TYPE_UNKNOWN; map that
                    // emulator-only injection to the finger tool used on a tablet.
                    MotionEvent delivered=event;
                    if(event.getToolType(0)==MotionEvent.TOOL_TYPE_UNKNOWN){
                        int count=event.getPointerCount();MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];
                        MotionEvent.PointerCoords[] coordinates=new MotionEvent.PointerCoords[count];
                        for(int i=0;i<count;i++){properties[i]=new MotionEvent.PointerProperties();event.getPointerProperties(i,properties[i]);
                            if(properties[i].toolType==MotionEvent.TOOL_TYPE_UNKNOWN)properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
                            coordinates[i]=new MotionEvent.PointerCoords();event.getPointerCoords(i,coordinates[i]);}
                        delivered=MotionEvent.obtain(event.getDownTime(),event.getEventTime(),event.getAction(),count,properties,coordinates,
                            event.getMetaState(),event.getButtonState(),event.getXPrecision(),event.getYPrecision(),event.getDeviceId(),event.getEdgeFlags(),event.getSource(),event.getFlags());
                    }
                    try{listener.onFingerEvent(delivered);}finally{if(delivered!=event)delivered.recycle();}
                }
                if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)fingerStream=false;
                return true;
            }
        };
    }
    private void publish() {
        if (!alive || !wanted || width<1 || height<1 || ready) return;
        ready=true; view.post(() -> { if(alive&&ready) listener.onReady(); });
    }
    public View view(){return view;}
    public void start(){wanted=true;publish();}
    public void stop(){wanted=false;ready=false;}
    public void release(){alive=false;stop();}
    public void pauseForEdit(){ready=false;wanted=false;}
    public boolean isReady(){return alive&&ready;}
    public boolean isPenDown(){return false;}
    public boolean hasPendingInput(){return false;}
    public void useViewPaper(){viewPaper=true;}
    public boolean usesViewPaper(){return viewPaper;}
    public boolean hasGeometry(int w,int h){return width==w&&height==h&&w>0&&h>0;}
    public void setTool(Tool value){}
    public void setSelectionMode(boolean value){}
    public void setPenWidth(float value){}
    public void setEraserWidth(float value){}
    public void setOverlayBounds(Rect... bounds){}
    public void redrawAfterOverlayChange(){view.invalidate();}
    public void setPage(NotebookPage value){page=value;view.invalidate();}
    public void setPaperRenderer(PaperRenderer renderer){paper=renderer;view.invalidate();}
    public boolean updateViewport(NotebookPage value,PaperRenderer renderer){setPage(value);setPaperRenderer(renderer);return true;}
    public boolean showScrollPreview(Bitmap preview){return true;}
    public List<NotebookPage.Stroke> snapshotNativeStrokes(){return page.strokes;}
}
