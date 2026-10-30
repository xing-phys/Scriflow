package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Anchored reading preferences; the note and its native writing surface stay in place. */
final class NotebookReadingMenu {
    interface Listener{void close(NotebookReadingSettings settings,boolean cropArea);}
    private final Activity activity;private final View anchor;private final PopupWindow popup;private final Listener listener;
    private NotebookReadingSettings settings;private boolean retired,cropArea;
    NotebookReadingMenu(Activity activity,View anchor,NotebookReadingSettings initial,boolean pdf,Listener listener){
        this.activity=activity;this.anchor=anchor;this.settings=initial;this.listener=listener;
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(12),dp(8),dp(12),dp(10));
        GradientDrawable background=new GradientDrawable();background.setColor(Color.WHITE);background.setStroke(dp(1),Color.BLACK);background.setCornerRadius(dp(6));content.setBackground(background);
        LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("Reading view",15);title.setTypeface(null,Typeface.BOLD);heading.addView(title,new LinearLayout.LayoutParams(0,dp(30),1));
        NotebookIconButton close=new NotebookIconButton(activity,NotebookIconDrawable.Icon.CLOSE,"Close reading settings");heading.addView(close,new LinearLayout.LayoutParams(dp(28),dp(28)));content.addView(heading);
        LinearLayout flow=new LinearLayout(activity);
        add(flow,(initial.singlePage?"":"✓ ")+"Continuous",()->chooseFlow(false));
        add(flow,(initial.singlePage?"✓ ":"")+"Single page",()->chooseFlow(true));content.addView(flow);
        if(pdf){
            content.addView(label("Page crop",13));
            LinearLayout first=new LinearLayout(activity),second=new LinearLayout(activity);
            cropButton(first,"Off",NotebookReadingSettings.Crop.NONE,false);cropButton(first,"Auto",NotebookReadingSettings.Crop.AUTO,false);
            cropButton(second,"Semi-auto…",NotebookReadingSettings.Crop.SEMI_AUTO,true);cropButton(second,"Manual…",NotebookReadingSettings.Crop.MANUAL,true);
            content.addView(first);content.addView(second);
            TextView value=label(contrastLabel(initial.contrast),13);content.addView(value);
            SeekBar slider=new SeekBar(activity);slider.setMax(56);slider.setProgress(Math.round((initial.contrast-.2f)*20));
            slider.setThumbTintList(ColorStateList.valueOf(Color.BLACK));slider.setProgressTintList(ColorStateList.valueOf(Color.BLACK));
            slider.setProgressBackgroundTintList(ColorStateList.valueOf(0xffbbbbbb));slider.setStateListAnimator(null);
            slider.setContentDescription("PDF contrast; larger values darken text and images");
            slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
                public void onProgressChanged(SeekBar s,int n,boolean user){float factor=(n+4)/20f;settings=settings.contrast(factor);value.setText(contrastLabel(factor));}
            });
            content.addView(slider,new LinearLayout.LayoutParams(-1,dp(36)));
            LinearLayout reset=new LinearLayout(activity);add(reset,"Reset contrast",()->slider.setProgress(16));content.addView(reset);
            content.addView(label("Applies to PDF text and images. Ink is unchanged.",11));
        }
        popup=new PopupWindow(content,1,1,true);popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);popup.setClippingEnabled(false);popup.setIsLaidOutInScreen(true);popup.setAnimationStyle(0);popup.setElevation(0);
        popup.setEnterTransition(null);popup.setExitTransition(null);popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setOnDismissListener(()->{if(!retired){retired=true;listener.close(settings,cropArea);}});close.setOnClickListener(v->popup.dismiss());
    }
    private void cropButton(LinearLayout row,String text,NotebookReadingSettings.Crop mode,boolean area){
        add(row,(settings.crop==mode?"✓ ":"")+text,()->{settings=settings.crop(mode);cropArea=area;popup.dismiss();});
    }
    private void chooseFlow(boolean single){settings=settings.single(single);popup.dismiss();}
    private void add(LinearLayout row,String text,Runnable action){NotebookButton button=new NotebookButton(activity);button.setText(text);button.setTextSize(13);button.setOnClickListener(v->action.run());row.addView(button,new LinearLayout.LayoutParams(0,dp(36),1));}
    private TextView label(String text,int size){TextView label=new TextView(activity);label.setText(text);label.setTextColor(Color.BLACK);label.setTextSize(size);label.setPadding(0,dp(3),0,dp(3));return label;}
    private static String contrastLabel(float value){return String.format(Locale.ROOT,"PDF contrast · %.2f×",value);}
    void show(Rect status){
        Rect visible=new Rect();anchor.getWindowVisibleDisplayFrame(visible);int[] origin=new int[2];anchor.getLocationOnScreen(origin);
        int width=Math.max(1,Math.min(dp(360),visible.width()-dp(16)));
        View content=popup.getContentView();content.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        int height=Math.min(content.getMeasuredHeight(),Math.max(1,visible.height()-dp(16)));
        int x=Math.max(visible.left+dp(8),Math.min(visible.right-width-dp(8),status.left));
        int y=Math.max(visible.top+dp(8),Math.min(visible.bottom-height-dp(8),status.isEmpty()?origin[1]+dp(48):status.bottom+dp(4)));
        popup.setWidth(width);popup.setHeight(height);
        if(!NotebookPopupHost.show(popup,anchor,Gravity.TOP|Gravity.LEFT,x,y)){retired=true;listener.close(settings,false);}
    }
    void retire(){retired=true;popup.dismiss();}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
