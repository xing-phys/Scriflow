package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import static org.eink.inkprobe.notebook.NotebookBrowserUi.*;

/** Native start page: shortcuts never request a website or favicon before the user opens it. */
final class NotebookBrowserHome extends ScrollView {
    interface Actions { void open(String url);void add();void manage(NotebookBrowserLibrary.Link link);void bookmarks();void history();void settings();void tabs(); }
    private final Actions actions;
    private final LinearLayout body,grid;
    private NotebookBrowserLibrary.Snapshot snapshot;
    private boolean thumbnails;
    private final NotebookBrowserIconButton presentation;
    NotebookBrowserHome(Context context,Actions actions) {
        super(context);this.actions=actions;setBackgroundColor(android.graphics.Color.WHITE);setOverScrollMode(OVER_SCROLL_NEVER);
        FrameLayout center=new FrameLayout(context);addView(center,new LayoutParams(-1,-2));
        body=column(context);body.setPadding(dp(context,24),dp(context,32),dp(context,24),dp(context,28));center.addView(body,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL));
        TextView title=label(context,"Browser",24);title.setTypeface(null,android.graphics.Typeface.BOLD);body.addView(title);
        space(body,dp(context,24));
        LinearLayout search=row(context);EditText query=input(context,"Search or enter a website","");field(query);query.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        Runnable go=()->{if(!query.getText().toString().trim().isEmpty())actions.open(query.getText().toString());};
        query.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_GO){go.run();return true;}return false;});
        search.addView(query,new LinearLayout.LayoutParams(0,dp(context,48),1));search.addView(icon(context,NotebookIconDrawable.Icon.SEARCH,"Search or open website",go),beside(context,48,48));body.addView(search);
        LinearLayout shortcuts=row(context);shortcuts.setGravity(Gravity.CENTER);
        NotebookIconDrawable.Icon[] symbols={NotebookIconDrawable.Icon.BOOKMARK,NotebookIconDrawable.Icon.HISTORY,NotebookIconDrawable.Icon.TABS,NotebookIconDrawable.Icon.SETTINGS};
        String[] names={"Bookmarks","History","Tabs","Browser data"};Runnable[] clicks={actions::bookmarks,actions::history,actions::tabs,actions::settings};
        for(int i=0;i<symbols.length;i++){FrameLayout slot=new FrameLayout(context);slot.addView(icon(context,symbols[i],names[i],clicks[i]),new FrameLayout.LayoutParams(dp(context,48),dp(context,48),Gravity.CENTER));shortcuts.addView(slot,new LinearLayout.LayoutParams(0,dp(context,48),1));}
        LinearLayout.LayoutParams shortcutSize=new LinearLayout.LayoutParams(-1,-2);shortcutSize.setMargins(0,dp(context,20),0,dp(context,28));body.addView(shortcuts,shortcutSize);
        thumbnails=context.getSharedPreferences("browser-ui",Context.MODE_PRIVATE).getBoolean("pinsThumbnails",true);
        LinearLayout pinHeading=row(context);TextView pins=label(context,"Pinned websites",18);pins.setTypeface(null,Typeface.BOLD);pinHeading.addView(pins,new LinearLayout.LayoutParams(0,dp(context,48),1));
        presentation=icon(context,NotebookIconDrawable.Icon.OVERVIEW,"Show thumbnails",()->{thumbnails=!thumbnails;context.getSharedPreferences("browser-ui",Context.MODE_PRIVATE).edit().putBoolean("pinsThumbnails",thumbnails).apply();render();});pinHeading.addView(presentation,beside(context,48,48));body.addView(pinHeading);
        TextView help=label(context,"Hold a shortcut to edit, unpin or reorder it.",12);help.setPadding(0,dp(context,6),0,dp(context,4));body.addView(help);grid=column(context);body.addView(grid);
        space(body,dp(context,24));body.addView(label(context,"Save keeps a page for offline reading. Bookmarks open the live website.",13));
    }
    void show(NotebookBrowserLibrary.Snapshot value){snapshot=value;render();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(w!=oldw){FrameLayout.LayoutParams size=(FrameLayout.LayoutParams)body.getLayoutParams();size.width=Math.min(w,dp(getContext(),880));body.setLayoutParams(size);post(this::render);}}
    private void render(){
        grid.removeAllViews();presentation.setSymbol(thumbnails?NotebookIconDrawable.Icon.OUTLINE:NotebookIconDrawable.Icon.OVERVIEW,thumbnails?"Show pinned websites as list":"Show pinned website thumbnails");if(snapshot==null)return;
        Context context=getContext();int width=Math.min(getWidth(),dp(context,880))-body.getPaddingLeft()-body.getPaddingRight();
        int columns=thumbnails?Math.max(1,Math.min(4,(width+dp(context,12))/dp(context,212))):1,count=0;LinearLayout row=null;
        java.util.List<NotebookBrowserLibrary.Link> pins=new java.util.ArrayList<>();for(NotebookBrowserLibrary.Link link:snapshot.links)if(link.pinned)pins.add(link);
        for(int i=0;i<=pins.size();i++){
            if(count%columns==0){row=row(context);row.setGravity(Gravity.TOP);grid.addView(row);}
            NotebookBrowserLibrary.Link link=i<pins.size()?pins.get(i):null;
            View card;
            if(thumbnails)card=new NotebookBrowserCard(context,link==null?"Add shortcut":link.title,link==null?null:link.url,link==null?"Add shortcut":"Open pinned website "+link.title,false,link==null,link==null?actions::add:()->actions.open(link.url),NotebookIconDrawable.Icon.MORE,link==null?null:"Manage pinned website "+link.title,link==null?null:()->actions.manage(link));
            else{NotebookButton item=button(context,link==null?"+ Add shortcut":link.title+"\n"+NotebookBrowserLibrary.host(link.url),link==null?actions::add:()->actions.open(link.url));item.setMaxLines(2);item.setEllipsize(TextUtils.TruncateAt.END);item.setContentDescription(link==null?"Add shortcut":"Open pinned website "+link.title);card=item;}
            if(link!=null)card.setOnLongClickListener(v->{actions.manage(link);return true;});
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,thumbnails?-2:dp(context,64),1);size.topMargin=dp(context,12);if(count%columns!=0)size.setMarginStart(dp(context,12));row.addView(card,size);count++;
        }
        if(row!=null&&count%columns!=0)for(int i=count%columns;i<columns;i++){LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,1,1);size.setMarginStart(dp(context,12));row.addView(new View(context),size);}
    }
}
