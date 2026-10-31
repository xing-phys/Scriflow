package org.eink.inkprobe.notebook;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** A quiet word highlight and two nonmodal actions; tapping the page dismisses it. */
final class NotebookPdfTextTools {
    interface Lookup{void open(String text);}
    private final View anchor;private final Lookup lookup;private final PopupWindow popup;private final Runnable boundsChanged;
    private final Rect bounds=new Rect();
    private final RectF area=new RectF();private String text;
    private final Drawable highlight=new Drawable(){
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        public void draw(Canvas canvas){paint.setColor(0x30606060);paint.setStyle(Paint.Style.FILL);canvas.drawRect(area,paint);
            paint.setColor(Color.BLACK);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));canvas.drawRect(area,paint);}
        public void setAlpha(int value){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    };
    NotebookPdfTextTools(View anchor,Lookup lookup,Runnable boundsChanged){
        this.anchor=anchor;this.lookup=lookup;this.boundsChanged=boundsChanged;Context context=anchor.getContext();
        LinearLayout bar=new LinearLayout(context);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setPadding(dp(3),dp(2),dp(3),dp(2));
        GradientDrawable background=new GradientDrawable();background.setColor(Color.WHITE);background.setStroke(dp(1),Color.BLACK);background.setCornerRadius(dp(5));bar.setBackground(background);
        NotebookIconButton copy=new NotebookIconButton(context,NotebookIconDrawable.Icon.COPY,"Copy PDF text");
        copy.setOnClickListener(v->{((ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("PDF text",text));Toast.makeText(context,"Text copied",Toast.LENGTH_SHORT).show();});
        NotebookIconButton dictionary=new NotebookIconButton(context,NotebookIconDrawable.Icon.DICTIONARY,"Look up in Eudic");
        dictionary.setOnClickListener(v->{if(text!=null)lookup.open(text);});
        bar.addView(copy,new LinearLayout.LayoutParams(dp(32),dp(32)));bar.addView(dictionary,new LinearLayout.LayoutParams(dp(32),dp(32)));
        popup=new PopupWindow(bar,dp(70),dp(36),false);popup.setBackgroundDrawable(null);popup.setTouchable(true);popup.setOutsideTouchable(false);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);popup.setClippingEnabled(true);popup.setIsLaidOutInScreen(true);
        popup.setAnimationStyle(0);popup.setElevation(0);popup.setEnterTransition(null);popup.setExitTransition(null);
    }
    void show(NotebookPdfText.Word word,RectF rect){
        hide();text=word.text;area.set(rect);area.intersect(0,0,anchor.getWidth(),anchor.getHeight());
        highlight.setBounds(0,0,anchor.getWidth(),anchor.getHeight());anchor.getOverlay().add(highlight);
        int[] point=new int[2];anchor.getLocationOnScreen(point);
        int x=Math.max(dp(4),Math.min(anchor.getWidth()-dp(74),Math.round(area.centerX()-dp(35))));
        int y=area.top>dp(44)?Math.round(area.top)-dp(40):Math.min(anchor.getHeight()-dp(40),Math.round(area.bottom)+dp(4));
        int top=point[1]+Math.max(0,y),left=point[0]+x;
        if(NotebookPopupHost.show(popup,anchor,Gravity.TOP|Gravity.LEFT,left,top)){bounds.set(left,top,left+dp(70),top+dp(36));boundsChanged.run();}
    }
    Rect selectionBounds(){
        if(text==null)return new Rect();int[] origin=new int[2];anchor.getLocationOnScreen(origin);
        return new Rect(origin[0]+(int)Math.floor(area.left),origin[1]+(int)Math.floor(area.top),
            origin[0]+(int)Math.ceil(area.right),origin[1]+(int)Math.ceil(area.bottom));
    }
    Rect bounds(){return new Rect(bounds);}
    void hide(){anchor.getOverlay().remove(highlight);if(popup.isShowing())popup.dismiss();text=null;if(!bounds.isEmpty()){bounds.setEmpty();boundsChanged.run();}}
    private int dp(int value){return Math.round(value*anchor.getResources().getDisplayMetrics().density);}
}
