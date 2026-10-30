package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** Original-page preview with immediate corner/edge dragging and independent odd/even areas. */
final class NotebookCropEditor extends android.widget.FrameLayout {
    interface Listener{void complete(NotebookCropBox box,NotebookReadingSettings.Scope scope);}
    private final PopupWindow popup;private final View anchor;private final Listener listener;
    private final Bitmap image;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF paper=new RectF();private final NotebookButton scopeButton;
    private NotebookCropBox box,start;private NotebookReadingSettings.Scope scope;
    private float downX,downY,cornerX=Float.NaN,cornerY;private int mode;private boolean retired,gestureMoved;
    NotebookCropEditor(Activity activity,View anchor,Bitmap image,NotebookCropBox initial,int page,Listener listener){
        super(activity);this.anchor=anchor;this.image=image;this.box=initial;this.listener=listener;
        scope=page%2==1?NotebookReadingSettings.Scope.ODD:NotebookReadingSettings.Scope.EVEN;
        setBackgroundColor(Color.WHITE);setWillNotDraw(false);
        LinearLayout controls=new LinearLayout(activity);controls.setGravity(Gravity.CENTER_VERTICAL);
        scopeButton=new NotebookButton(activity);scopeButton.setTextSize(12);scopeButton.setOnClickListener(v->{scope=NotebookReadingSettings.Scope.values()[(scope.ordinal()+1)%4];scopeLabel();});
        controls.addView(scopeButton,new LinearLayout.LayoutParams(0,dp(32),1));scopeLabel();
        icon(controls,NotebookIconDrawable.Icon.PAPER_BLANK,"Reset crop area",()->{box=NotebookCropBox.FULL;cornerX=Float.NaN;invalidate();});
        icon(controls,NotebookIconDrawable.Icon.CLOSE,"Cancel cropping",()->finish(null));
        icon(controls,NotebookIconDrawable.Icon.CHECK,"Apply crop area",()->finish(box));
        FrameLayout.LayoutParams row=new FrameLayout.LayoutParams(-1,dp(36),Gravity.BOTTOM);row.setMargins(dp(6),0,dp(6),dp(5));addView(controls,row);
        popup=new PopupWindow(this,anchor.getWidth(),anchor.getHeight(),false);popup.setOutsideTouchable(false);popup.setClippingEnabled(false);
        popup.setIsLaidOutInScreen(true);popup.setAnimationStyle(0);popup.setElevation(0);popup.setEnterTransition(null);popup.setExitTransition(null);
        setContentDescription("Crop the original page; tap opposite corners or drag corners and edges. Apply to the current, all, odd or even pages.");
    }
    private void scopeLabel(){scopeButton.setText("Apply: "+(scope==NotebookReadingSettings.Scope.PAGE?"this page":scope==NotebookReadingSettings.Scope.ALL?"all pages":scope==NotebookReadingSettings.Scope.ODD?"odd pages":"even pages")+" ↻");}
    private void icon(LinearLayout row,NotebookIconDrawable.Icon icon,String name,Runnable action){NotebookIconButton b=new NotebookIconButton(getContext(),icon,name);b.setTooltipText(name);b.setOnClickListener(v->action.run());row.addView(b,new LinearLayout.LayoutParams(dp(32),dp(32)));}
    void show(){int[] xy=new int[2];anchor.getLocationOnScreen(xy);if(!NotebookPopupHost.show(popup,anchor,Gravity.TOP|Gravity.LEFT,xy[0],xy[1]))finish(null);}
    private void finish(NotebookCropBox result){if(retired)return;retired=true;popup.dismiss();listener.complete(result,scope);}
    void retire(){retired=true;popup.dismiss();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        float scale=Math.min(Math.max(1,w-dp(16))/(float)image.getWidth(),Math.max(1,h-dp(100))/(float)image.getHeight());
        float pw=image.getWidth()*scale,ph=image.getHeight()*scale;
        paper.set((w-pw)/2,dp(52)+(Math.max(1,h-dp(100))-ph)/2,(w+pw)/2,dp(52)+(Math.max(1,h-dp(100))+ph)/2);
    }
    @Override protected void onDraw(Canvas canvas){
        if(retired)return;
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.BLACK);paint.setTextSize(dp(13));
        canvas.drawText("Tap two opposite corners",dp(12),dp(21),paint);
        canvas.drawText("or drag edges / inside to adjust",dp(12),dp(40),paint);
        canvas.drawBitmap(image,null,paper,paint);
        float l=paper.left+box.left*paper.width(),t=paper.top+box.top*paper.height(),r=paper.left+box.right*paper.width(),b=paper.top+box.bottom*paper.height();
        paint.setStyle(Paint.Style.FILL);paint.setColor(0x88777777);
        canvas.drawRect(paper.left,paper.top,paper.right,t,paint);canvas.drawRect(paper.left,b,paper.right,paper.bottom,paint);
        canvas.drawRect(paper.left,t,l,b,paint);canvas.drawRect(r,t,paper.right,b,paint);
        paint.setColor(Color.BLACK);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));canvas.drawRect(l,t,r,b,paint);
        for(int i=0;i<4;i++){float x=(i&1)==0?l:r,y=(i&2)==0?t:b;paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);canvas.drawCircle(x,y,dp(6),paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(Color.BLACK);canvas.drawCircle(x,y,dp(6),paint);}
        if(!Float.isNaN(cornerX)){
            float cx=paper.left+cornerX*paper.width(),cy=paper.top+cornerY*paper.height();
            paint.setStrokeWidth(dp(2));canvas.drawLine(cx-dp(10),cy,cx+dp(10),cy,paint);canvas.drawLine(cx,cy-dp(10),cx,cy+dp(10),paint);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(retired)return true;int action=event.getActionMasked();
        float x=(event.getX()-paper.left)/paper.width(),y=(event.getY()-paper.top)/paper.height();
        if(action==MotionEvent.ACTION_DOWN){
            if(x<0||x>1||y<0||y>1){start=null;return false;}
            start=box;downX=x;downY=y;mode=0;gestureMoved=false;float ex=dp(20)/paper.width(),ey=dp(20)/paper.height();
            if(Math.abs(x-box.left)<ex)mode|=1;else if(Math.abs(x-box.right)<ex)mode|=2;
            if(Math.abs(y-box.top)<ey)mode|=4;else if(Math.abs(y-box.bottom)<ey)mode|=8;
            if(mode==0&&box.contains(x,y))mode=16;
            return true;
        }
        if(action==MotionEvent.ACTION_CANCEL||event.getPointerCount()>1){if(start!=null)box=start;mode=0;cornerX=Float.NaN;invalidate();return true;}
        if(start==null)return false;
        if(action==MotionEvent.ACTION_MOVE&&Math.hypot((x-downX)*paper.width(),(y-downY)*paper.height())>=dp(4)){
            gestureMoved=true;cornerX=Float.NaN;
        }
        if(action==MotionEvent.ACTION_UP&&!gestureMoved){
            if(x>=0&&x<=1&&y>=0&&y<=1){
                if(Float.isNaN(cornerX)){cornerX=x;cornerY=y;}
                else{if(Math.abs(x-cornerX)>=.01f&&Math.abs(y-cornerY)>=.01f)
                    box=new NotebookCropBox(Math.min(x,cornerX),Math.min(y,cornerY),Math.max(x,cornerX),Math.max(y,cornerY));cornerX=Float.NaN;}
            }
            mode=0;invalidate();performClick();return true;
        }
        if(gestureMoved&&(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP)&&mode!=0){
            float l=start.left,t=start.top,r=start.right,b=start.bottom,min=.01f;
            if(mode==16){float dx=Math.max(-l,Math.min(1-r,x-downX)),dy=Math.max(-t,Math.min(1-b,y-downY));l+=dx;r+=dx;t+=dy;b+=dy;}
            else{if((mode&1)!=0)l=Math.max(0,Math.min(r-min,x));if((mode&2)!=0)r=Math.max(l+min,Math.min(1,x));
                if((mode&4)!=0)t=Math.max(0,Math.min(b-min,y));if((mode&8)!=0)b=Math.max(t+min,Math.min(1,y));}
            box=new NotebookCropBox(l,t,r,b);invalidate();if(action==MotionEvent.ACTION_UP)mode=0;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
