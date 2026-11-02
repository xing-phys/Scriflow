package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

/** The popup viewport owns its frame, independently of its content's measured or scrolling size. */
class NotebookAiFrame extends FrameLayout {
    private final Paint border=new Paint();
    private final int thickness;

    NotebookAiFrame(Context context,int thickness) {
        super(context);this.thickness=Math.max(1,thickness);
        border.setColor(Color.BLACK);border.setStyle(Paint.Style.FILL);
        setBackgroundColor(Color.WHITE);setPadding(this.thickness,this.thickness,this.thickness,this.thickness);
        setClipChildren(true);setClipToPadding(true);
    }
    static void configure(PopupWindow popup) {
        // FLAG_LAYOUT_NO_LIMITS lets content/frame extend beyond the window manager's usable area.
        popup.setClippingEnabled(true);popup.setIsClippedToScreen(false);popup.setIsLaidOutInScreen(true);
        popup.setBackgroundDrawable(null);
    }
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight) {
        super.onSizeChanged(width,height,oldWidth,oldHeight);
        // Invalidate at the NEW size, after WindowManager has applied a resize, not only at the old grip bounds.
        invalidate();
    }
    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        int width=getWidth(),height=getHeight();
        int x=Math.min(thickness,width),y=Math.min(thickness,height);
        // Filled, integer-aligned strips stay entirely inside the actual viewport and draw after children.
        canvas.drawRect(0,0,width,y,border);
        canvas.drawRect(0,height-y,width,height,border);
        canvas.drawRect(0,0,x,height,border);
        canvas.drawRect(width-x,0,width,height,border);
    }
}
