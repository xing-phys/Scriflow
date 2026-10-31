package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.View;
import android.widget.FrameLayout;
import java.util.*;

/** Persistent ordinary View paper below the firmware's transparent live-ink surface. */
final class NotebookRestingPaper {
    interface Listener {
        boolean ready();void draw(Canvas canvas);List<Rect> exclusions();
        default void failed(Throwable error){}
    }
    private final FrameLayout anchor;
    private final View content;
    private final Listener listener;
    private List<Rect> lastExclusions=Collections.emptyList();
    private boolean closed,failed;
    NotebookRestingPaper(Context context,FrameLayout anchor,Listener listener){
        this.anchor=anchor;this.listener=listener;
        content=new View(context){@Override protected void onDraw(Canvas canvas){drawFrame(canvas);}};
        content.setClickable(false);content.setFocusable(false);content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // The firmware SurfaceView is RGBA8888 with ZOrderOnTop already enabled.
        // Its transparent color clear reveals this View; AHW remains above it.
        anchor.addView(content,0,new FrameLayout.LayoutParams(-1,-1));
    }
    private void drawFrame(Canvas canvas){
        if(closed||failed||!listener.ready())return;
        int save=canvas.save();int[] origin=new int[2];anchor.getLocationOnScreen(origin);
        try{
            for(Rect rect:listener.exclusions())if(!rect.isEmpty())
                canvas.clipOutRect(rect.left-origin[0],rect.top-origin[1],rect.right-origin[0],rect.bottom-origin[1]);
            listener.draw(canvas);
        }catch(RuntimeException|OutOfMemoryError error){
            failed=true;content.post(()->listener.failed(error));
        }finally{canvas.restoreToCount(save);}
    }
    void show(){
        if(closed||failed||!listener.ready())return;
        content.invalidate();
    }
    void refresh(){
        List<Rect> next=listener.exclusions();
        if(lastExclusions.equals(next))return;
        List<Rect> snapshot=new ArrayList<>();for(Rect rect:next)snapshot.add(new Rect(rect));lastExclusions=snapshot;
        show();
    }
    void close(){closed=true;anchor.removeView(content);}
}
