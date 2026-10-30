package org.eink.inkprobe.notebook;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/** Finger-only hold timer. It never intercepts stylus handwriting or a scrolling/pinch gesture. */
final class NotebookPdfPress {
    interface Listener {boolean eligible(float x,float y);int revision();boolean penDown();void held(float x,float y);}
    private final Listener listener;private final Handler main=new Handler(Looper.getMainLooper());
    private final NotebookPdfHold hold=new NotebookPdfHold();private final float slop,pixelsPerMm;
    private float x,y;
    private final Runnable fire=this::fire;
    private void fire(){
        if(listener.eligible(x,y)&&hold.fire(SystemClock.uptimeMillis(),ViewConfiguration.getLongPressTimeout(),listener.revision(),listener.penDown()))listener.held(x,y);
    }
    NotebookPdfPress(Listener listener,float slop,float pixelsPerMm){this.listener=listener;this.slop=slop;this.pixelsPerMm=pixelsPerMm;}
    boolean touch(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            cancel();x=event.getX();y=event.getY();
            if(event.getPointerCount()==1&&event.getToolType(0)==MotionEvent.TOOL_TYPE_FINGER
                    &&!NotebookPalmGuard.broadContact(event.getTouchMajor(0),pixelsPerMm)&&!listener.penDown()&&listener.eligible(x,y)){
                hold.begin(event.getEventTime(),x,y,slop,listener.revision());main.postDelayed(fire,ViewConfiguration.getLongPressTimeout());
            }
        }else if(event.getPointerCount()!=1||action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_CANCEL){cancel();return false;}
        else if(action==MotionEvent.ACTION_MOVE)hold.move(event.getX(),event.getY());
        boolean consumed=hold.consumed();
        if(action==MotionEvent.ACTION_UP){main.removeCallbacks(fire);hold.cancel();}
        return consumed;
    }
    void cancel(){main.removeCallbacks(fire);hold.cancel();}
}
