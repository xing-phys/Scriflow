package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.*;
import android.widget.PopupWindow;
import java.util.*;

/** The same View/PopupWindow composition used by the clear figure preview, without selection controls. */
final class NotebookRestingPaper {
    interface Listener {boolean ready();void draw(Canvas canvas);List<Rect> exclusions();}
    private final View anchor,content;
    private final PopupWindow popup;
    private final Listener listener;
    private List<Rect> lastExclusions=Collections.emptyList();
    private boolean closed,failed;
    NotebookRestingPaper(Context context,View anchor,Listener listener){
        this.anchor=anchor;this.listener=listener;
        content=new View(context){@Override protected void onDraw(Canvas canvas){drawFrame(canvas);}};
        content.setClickable(false);content.setFocusable(false);content.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        popup=new PopupWindow(content,1,1,false);
        // No AHW exclusion: this window has no touch region and hides before pen DOWN.
        popup.setTouchable(false);popup.setFocusable(false);popup.setOutsideTouchable(false);
        popup.setClippingEnabled(false);popup.setIsLaidOutInScreen(true);popup.setAnimationStyle(0);popup.setElevation(0);
        popup.setEnterTransition(null);popup.setExitTransition(null);
    }
    private void drawFrame(Canvas canvas){
        if(closed||failed||!listener.ready())return;
        int save=canvas.save();int[] origin=new int[2];anchor.getLocationOnScreen(origin);
        try{
            for(Rect rect:listener.exclusions())if(!rect.isEmpty())
                canvas.clipOutRect(rect.left-origin[0],rect.top-origin[1],rect.right-origin[0],rect.bottom-origin[1]);
            listener.draw(canvas);
        }catch(RuntimeException|OutOfMemoryError error){
            // The complete native frame remains underneath. Retire only this
            // optional presentation layer if the view renderer cannot draw it.
            failed=true;content.post(this::hide);
        }finally{canvas.restoreToCount(save);}
    }
    void show(){
        if(closed||failed||!listener.ready()||!anchor.isAttachedToWindow()||anchor.getWidth()<1||anchor.getHeight()<1){hide();return;}
        int[] origin=new int[2];anchor.getLocationOnScreen(origin);
        if(popup.isShowing())popup.update(origin[0],origin[1],anchor.getWidth(),anchor.getHeight());
        else{popup.setWidth(anchor.getWidth());popup.setHeight(anchor.getHeight());
            NotebookPopupHost.show(popup,anchor,Gravity.TOP|Gravity.LEFT,origin[0],origin[1]);}
        content.setVisibility(View.VISIBLE);content.invalidate();
    }
    void refresh(){
        List<Rect> next=listener.exclusions();
        if(lastExclusions.equals(next))return;
        List<Rect> snapshot=new ArrayList<>();for(Rect rect:next)snapshot.add(new Rect(rect));lastExclusions=snapshot;
        if(popup.isShowing()&&content.getVisibility()==View.VISIBLE)content.invalidate();
    }
    // Keep the window's place below subsequently opened figure controls. Hiding a
    // View also avoids window add/remove work on the live pen-DOWN path.
    void hide(){content.setVisibility(View.INVISIBLE);}
    void close(){closed=true;if(popup.isShowing())popup.dismiss();}
}
