package org.eink.inkprobe.notebook;

import android.content.Context;
import android.view.*;
import android.widget.*;
import static org.eink.inkprobe.notebook.NotebookBrowserUi.*;

/** Native start page: shortcuts never request a website or favicon before the user opens it. */
final class NotebookBrowserHome extends ScrollView {
    interface Actions { void open(String url);void add();void manage(NotebookBrowserLibrary.Link link);void bookmarks();void history();void settings();void tabs(); }
    private final Actions actions;
    private final LinearLayout body,grid;
    private NotebookBrowserLibrary.Snapshot snapshot;
    NotebookBrowserHome(Context context,Actions actions) {
        super(context);this.actions=actions;setBackgroundColor(android.graphics.Color.WHITE);setOverScrollMode(OVER_SCROLL_NEVER);
        body=column(context);body.setPadding(dp(context,24),dp(context,52),dp(context,24),dp(context,24));addView(body,new LayoutParams(-1,-2));
        TextView title=label(context,"Browser",28);title.setTypeface(null,android.graphics.Typeface.BOLD);body.addView(title);
        space(body,dp(context,18));
        LinearLayout search=row(context);EditText query=input(context,"Search or enter a website","");query.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_GO);
        Runnable go=()->{if(!query.getText().toString().trim().isEmpty())actions.open(query.getText().toString());};
        query.setOnEditorActionListener((v,action,event)->{if(action==android.view.inputmethod.EditorInfo.IME_ACTION_GO){go.run();return true;}return false;});
        search.addView(query,new LinearLayout.LayoutParams(0,dp(context,48),1));search.addView(button(context,"Go",go),new LinearLayout.LayoutParams(dp(context,64),dp(context,42)));body.addView(search);
        space(body,dp(context,22));body.addView(label(context,"Pinned websites",19));
        body.addView(label(context,"Hold a shortcut to edit, unpin or reorder it.",12));grid=column(context);body.addView(grid);
        String[][] labels={{"Bookmarks","History"},{"Browser data","Tabs"}};Runnable[][] clicks={{actions::bookmarks,actions::history},{actions::settings,actions::tabs}};
        for(int r=0;r<labels.length;r++){LinearLayout row=row(context);for(int c=0;c<2;c++){LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(context,44),1);size.setMargins(dp(context,3),dp(context,6),dp(context,3),0);row.addView(button(context,labels[r][c],clicks[r][c]),size);}body.addView(row);}
        space(body,dp(context,18));TextView hint=label(context,"Save keeps a page for offline reading. Bookmarks open the live website.",13);body.addView(hint);
    }
    void show(NotebookBrowserLibrary.Snapshot value){snapshot=value;render();}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(w!=oldw)post(this::render);}
    private void render(){
        grid.removeAllViews();if(snapshot==null)return;
        int columns=getWidth()>=dp(getContext(),760)?4:2,count=0;LinearLayout row=null;
        for(NotebookBrowserLibrary.Link link:snapshot.links)if(link.pinned){
            if(count++%columns==0){row=row(getContext());grid.addView(row);}
            NotebookButton card=button(getContext(),link.title+"\n"+NotebookBrowserLibrary.host(link.url),()->actions.open(link.url));card.setMaxLines(3);card.setContentDescription("Open pinned website "+link.title);card.setOnLongClickListener(v->{actions.manage(link);return true;});
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(getContext(),92),1);size.setMargins(dp(getContext(),3),dp(getContext(),8),dp(getContext(),3),0);row.addView(card,size);
        }
        if(row!=null && count%columns!=0)for(int i=count%columns;i<columns;i++)row.addView(new android.view.View(getContext()),new LinearLayout.LayoutParams(0,1,1));
        NotebookButton add=button(getContext(),"+ Add shortcut",actions::add);grid.addView(add,new LinearLayout.LayoutParams(-1,dp(getContext(),42)));
    }
}
