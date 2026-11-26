package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.widget.FrameLayout;
import android.widget.PopupWindow;

/** The rounded popup viewport owns its frame independently of scrolling content. */
class NotebookAiFrame extends FrameLayout {
    private final Paint border=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip=new Path();
    private final int thickness;
    private final float cornerRadius;

    NotebookAiFrame(Context context,int thickness) {
        super(context);this.thickness=Math.max(1,thickness);
        cornerRadius=14*context.getResources().getDisplayMetrics().density;
        border.setColor(Color.BLACK);border.setStyle(Paint.Style.STROKE);border.setStrokeWidth(this.thickness);
        setBackgroundColor(Color.TRANSPARENT);setPadding(this.thickness,this.thickness,this.thickness,this.thickness);
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
        int width=getWidth(),height=getHeight();
        float radius=Math.min(cornerRadius,Math.min(width,height)/2f);
        clip.reset();clip.addRoundRect(0,0,width,height,radius,radius,Path.Direction.CW);
        int saved=canvas.save();canvas.clipPath(clip);canvas.drawColor(Color.WHITE);
        super.dispatchDraw(canvas);canvas.restoreToCount(saved);
        float inset=thickness/2f;
        canvas.drawRoundRect(inset,inset,width-inset,height-inset,Math.max(0,radius-inset),Math.max(0,radius-inset),border);
    }
}
