package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Anchored reading preferences; the note and its native writing surface stay in place. */
final class NotebookReadingMenu {
    interface Listener{
        void changed(NotebookReadingSettings settings);
        void close(NotebookReadingSettings settings,NotebookReadingSettings cropCancellation);
    }
    private final Activity activity;private final View anchor;private final PopupWindow popup;private final Listener listener;
    private NotebookReadingSettings settings,cropCancellation;private boolean retired;
    private final java.util.List<Runnable> choiceUpdates=new java.util.ArrayList<>();
    NotebookReadingMenu(Activity activity,View anchor,NotebookReadingSettings initial,boolean pdf,Listener listener){
        this.activity=activity;this.anchor=anchor;this.settings=initial;this.listener=listener;
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(12),dp(8),dp(12),dp(10));
        LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("Reading view",18);title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.CENTER_VERTICAL);heading.addView(title,new LinearLayout.LayoutParams(0,dp(40),1));
        content.addView(heading);
        content.addView(label("Navigation",13));
        LinearLayout flow=new LinearLayout(activity);
        choice(flow,"Continuous",()->!settings.paged,()->chooseFlow(false));
        choice(flow,"Page turns",()->settings.paged,()->chooseFlow(true));content.addView(flow);
        content.addView(label("Pages on screen",13));
        LinearLayout pages=new LinearLayout(activity);
        choice(pages,"Single page",()->!settings.doublePage,()->choosePages(false));
        choice(pages,"Double page",()->settings.doublePage,()->choosePages(true));content.addView(pages);
        content.addView(label("Spread starts",13));
        LinearLayout cover=new LinearLayout(activity);
        choice(cover,"Pages 1–2",()->!settings.cover,()->chooseCover(false));
        choice(cover,"Cover, then 2–3",()->settings.cover,()->chooseCover(true));content.addView(cover);
        content.addView(label("Continuous direction",13));
        LinearLayout direction=new LinearLayout(activity);
        choice(direction,"Vertical",()->!settings.horizontal,()->chooseDirection(false));
        choice(direction,"Horizontal",()->settings.horizontal,()->chooseDirection(true));content.addView(direction);
        TextView overlapValue=label("Tap overlap · "+initial.tapOverlap+"%",13);content.addView(overlapValue);
        SeekBar overlap=new SeekBar(activity);overlap.setMax(NotebookReadingSettings.MAX_TAP_OVERLAP);overlap.setProgress(initial.tapOverlap);
        overlap.setThumbTintList(ColorStateList.valueOf(Color.BLACK));overlap.setProgressTintList(ColorStateList.valueOf(Color.BLACK));
        overlap.setProgressBackgroundTintList(ColorStateList.valueOf(0xffbbbbbb));overlap.setStateListAnimator(null);
        overlap.setContentDescription("Continuous reading tap overlap, percentage of the visible screen");
        overlap.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
            public void onProgressChanged(SeekBar s,int value,boolean user){overlapValue.setText("Tap overlap · "+value+"%");change(settings.tapOverlap(value));}
        });
        content.addView(overlap,new LinearLayout.LayoutParams(-1,dp(36)));
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
                public void onProgressChanged(SeekBar s,int n,boolean user){float factor=(n+4)/20f;value.setText(contrastLabel(factor));change(settings.contrast(factor));}
            });
            content.addView(slider,new LinearLayout.LayoutParams(-1,dp(36)));
            LinearLayout reset=new LinearLayout(activity);add(reset,"Reset contrast",()->slider.setProgress(16));content.addView(reset);
            content.addView(label("Applies to PDF text and images. Ink is unchanged.",11));
        }
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.addView(content);
        FrameLayout frame=new FrameLayout(activity);frame.setBackground(NotebookUi.rounded(activity.getResources().getDisplayMetrics().density,14));
        frame.addView(scroll,new FrameLayout.LayoutParams(-1,-2));
        popup=new PopupWindow(frame,1,1,true);popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setWindowLayoutType(WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL);
        popup.setOutsideTouchable(true);popup.setClippingEnabled(false);popup.setIsLaidOutInScreen(true);popup.setAnimationStyle(0);popup.setElevation(0);
        popup.setEnterTransition(null);popup.setExitTransition(null);popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setOnDismissListener(()->{if(!retired){retired=true;listener.close(settings,cropCancellation);}});
    }
    private void cropButton(LinearLayout row,String text,NotebookReadingSettings.Crop mode,boolean area){
        choice(row,text,()->settings.crop==mode,()->{
            if(area){cropCancellation=settings;settings=settings.crop(mode);popup.dismiss();}
            else change(settings.crop(mode));
        });
    }
    private void chooseFlow(boolean paged){change(settings.paged(paged));}
    private void choosePages(boolean doublePage){change(settings.doublePage(doublePage));}
    private void chooseCover(boolean value){change(settings.cover(value));}
    private void chooseDirection(boolean horizontal){change(settings.horizontal(horizontal));}
    private void change(NotebookReadingSettings value){
        if(settings.equals(value))return;
        settings=value;refreshChoices();listener.changed(settings);
    }
    private void choice(LinearLayout row,String text,java.util.function.BooleanSupplier selected,Runnable action){
        NotebookButton button=add(row,"",action);
        Runnable update=()->button.setText((selected.getAsBoolean()?"✓ ":"")+text);
        choiceUpdates.add(update);update.run();
    }
    private void refreshChoices(){for(Runnable update:choiceUpdates)update.run();}
    private NotebookButton add(LinearLayout row,String text,Runnable action){NotebookButton button=new NotebookButton(activity);button.setText(text);button.setTextSize(13);button.setOnClickListener(v->action.run());row.addView(button,new LinearLayout.LayoutParams(0,dp(36),1));return button;}
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
        if(!NotebookPopupHost.show(popup,anchor,Gravity.TOP|Gravity.LEFT,x,y)){retired=true;listener.close(settings,null);}
    }
    NotebookReadingSettings selectedSettings(){return settings;}
    void retire(){retired=true;popup.dismiss();}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
