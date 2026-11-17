package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** The same corner islands and movable icon toolbox used by native notebooks. */
final class NotebookEbookChrome {
    private final Activity activity;
    private final FrameLayout root;
    private final Consumer<String> command;
    private final SharedPreferences settings;
    private final NotebookDeviceStatus device;
    private final NotebookStatusIslands islands;
    private final NotebookFloatingTools tools;
    private final NotebookButton title,position;
    private final Rect left=new Rect(),right=new Rect();
    private boolean active,zen,panel,selection,restoreCollapsed,initial=true;
    private float x,y;
    private static final NotebookFloatingTools.Action[] SELECTION={NotebookFloatingTools.Action.HIGHLIGHT,NotebookFloatingTools.Action.TEXT_NOTE,NotebookFloatingTools.Action.COPY_TEXT,NotebookFloatingTools.Action.LOOKUP,NotebookFloatingTools.Action.CLEAR_SELECTION};

    NotebookEbookChrome(Activity activity,FrameLayout root,BooleanSupplier interactive,Consumer<String> command){
        this.activity=activity;this.root=root;this.command=command;
        settings=activity.getSharedPreferences("ebook-tools",Activity.MODE_PRIVATE);
        x=settings.getFloat("x",.5f);y=settings.getFloat("y",0);
        title=text("Opening…","Return to bookshelf",()->command.accept("close"));title.setMaxWidth(dp(160));title.setEllipsize(TextUtils.TruncateAt.END);
        position=text("0%","Reading position",()->command.accept("position"));position.setFontFeatureSettings("tnum");
        NotebookButton zoom=text("","Text and layout",()->command.accept("type"));zoom.setVisibility(View.GONE);
        NotebookButton more=text("⋮","More reading tools",()->command.accept("more"));
        TextView status=new TextView(activity);status.setVisibility(View.GONE);
        device=new NotebookDeviceStatus(activity,interactive);device.setZenExit(()->command.accept("focus"));
        NotebookIconButton back=icon(NotebookIconDrawable.Icon.VIEW_BACK,"Return from link","history-back");
        NotebookIconButton forward=icon(NotebookIconDrawable.Icon.VIEW_FORWARD,"Forward to link","history-forward");
        islands=new NotebookStatusIslands(activity,title,icon(NotebookIconDrawable.Icon.PREVIOUS,"Previous page","previous"),position,
            icon(NotebookIconDrawable.Icon.NEXT,"Next page","next"),zoom,status,more,device,back,forward,a->{},(a,b)->{left.set(a);right.set(b);});
        tools=NotebookFloatingTools.reading(activity,new NotebookFloatingTools.Listener(){
            public void onAction(NotebookFloatingTools.Action action){
                switch(action){
                    case COLLAPSE:tools.setCollapsed(!tools.isCollapsed());settings.edit().putBoolean("collapsed",tools.isCollapsed()).apply();break;
                    case ORIENTATION:tools.setVertical(!tools.isVertical());settings.edit().putBoolean("vertical",tools.isVertical()).apply();break;
                    case TEXT_STYLE:command.accept("type");break;
                    case OUTLINE:command.accept("contents");break;
                    case SEARCH_BOOK:command.accept("search");break;
                    case BOOKMARK:command.accept("bookmark");break;
                    case NOTES:command.accept("notes");break;
                    case HANDWRITE:command.accept("handwrite");break;
                    case ZEN:command.accept("focus");break;
                    case HIGHLIGHT:command.accept("highlight");break;
                    case TEXT_NOTE:command.accept("text-note");break;
                    case COPY_TEXT:command.accept("copy");break;
                    case LOOKUP:command.accept("lookup");break;
                    case CLEAR_SELECTION:command.accept("clear-selection");break;
                    default:break;
                }
            }
            public boolean onContactStart(){return interactive.getAsBoolean();}
            public void onContactEnd(){}
            public boolean onMoveStart(){return interactive.getAsBoolean();}
            public void onMoveEnd(){}
            public void onBoundsChanged(Rect bounds){}
            public void onPositionChanged(float horizontal,float vertical){x=horizontal;y=vertical;settings.edit().putFloat("x",x).putFloat("y",y).apply();}
        });
        for(NotebookFloatingTools.Action action:SELECTION)tools.button(action).setVisibility(View.GONE);
        tools.setCollapsed(settings.getBoolean("collapsed",true));tools.setVertical(settings.getBoolean("vertical",false));
    }
    private NotebookIconButton icon(NotebookIconDrawable.Icon icon,String label,String action){NotebookIconButton b=new NotebookIconButton(activity,icon,label);b.setOnClickListener(v->command.accept(action));return b;}
    private NotebookButton text(String value,String label,Runnable action){
        NotebookButton b=new NotebookButton(activity);b.setText(value);b.setTextSize(12);b.setSingleLine(true);b.setIncludeFontPadding(false);
        b.setPadding(dp(4),0,dp(4),0);b.setGravity(Gravity.CENTER);b.setContentDescription(label);b.setTooltipText(label);b.setOnClickListener(v->action.run());return b;
    }
    void title(String value){title.setText(value);}
    void progress(int value){position.setText(value+"%");islands.refresh();}
    void history(boolean back,boolean forward){if(!islands.historyMatches(back,forward))islands.history(back,forward);}
    void bookmark(boolean value){tools.button(NotebookFloatingTools.Action.BOOKMARK).setToolSelected(value);}
    void selection(boolean value){
        if(selection==value)return;selection=value;
        if(value){restoreCollapsed=tools.isCollapsed();tools.setCollapsed(false);}else if(restoreCollapsed)tools.setCollapsed(true);
        for(NotebookFloatingTools.Action action:SELECTION)tools.button(action).setVisibility(value?View.VISIBLE:View.GONE);
        show();
    }
    void panel(boolean value){panel=value;show();}
    void zen(boolean value){zen=value;device.setZenMode(value);islands.setZenMode(value);show();}
    void active(boolean value){active=value;if(value){device.start();root.post(this::show);}else{device.stop();islands.hide();tools.hide();}}
    private void show(){
        if(!active||!root.isAttachedToWindow())return;
        if(panel){islands.hide();tools.hide();return;}
        islands.show(root);if(zen){tools.hide();return;}
        tools.show(root,x,y);if(initial){tools.avoidStatusIslands(left,right);initial=false;}
    }
    void close(){active(false);tools.dispose();}
    org.json.JSONObject debug()throws org.json.JSONException{
        org.json.JSONObject value=new org.json.JSONObject().put("width",root.getWidth()).put("height",root.getHeight()).put("panel",panel).put("zen",zen);
        value.put("left",new org.json.JSONArray(new int[]{left.left,left.top,left.right,left.bottom}));
        value.put("right",new org.json.JSONArray(new int[]{right.left,right.top,right.right,right.bottom}));
        int[] xy=new int[2];title.getLocationOnScreen(xy);
        value.put("home",new org.json.JSONArray(new int[]{xy[0],xy[1],title.getWidth(),title.getHeight()}));
        View content=root.getChildAt(0);value.put("fullScreenContent",content.getWidth()==root.getWidth()&&content.getHeight()==root.getHeight());
        tools.appendDebug(value);return value;
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}
