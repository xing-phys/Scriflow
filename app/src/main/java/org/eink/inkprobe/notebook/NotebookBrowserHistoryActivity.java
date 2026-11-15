package org.eink.inkprobe.notebook;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;
import static org.eink.inkprobe.notebook.NotebookBrowserUi.*;

public final class NotebookBrowserHistoryActivity extends NotebookDisplayActivity {
    private ListView list;private final List<NotebookBrowserLibrary.Visit> matches=new ArrayList<>();private final BaseAdapter adapter=new BaseAdapter(){public int getCount(){return matches.size();}public Object getItem(int at){return matches.get(at);}public long getItemId(int at){return at;}public View getView(int at,View recycled,ViewGroup parent){return visitRow(matches.get(at));}};private TextView status;private EditText query;private NotebookBrowserLibrary.Snapshot snapshot;private AlertDialog dialog;private boolean active;private int generation;
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().setWindowAnimations(0);
        LinearLayout root=column(this);root.setPadding(dp(this,18),dp(this,12),dp(this,18),dp(this,12));header(this,root,"Browsing history",this::finish,deviceSettingsIsland());
        LinearLayout tools=row(this);query=input(this,"Find in history",state==null?"":state.getString("query",""));field(query);query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int a,int b,int c){render();}});tools.addView(query,new LinearLayout.LayoutParams(0,dp(this,48),1));tools.addView(icon(this,NotebookIconDrawable.Icon.DELETE,"Clear browsing history",this::clear),beside(this,48,48));root.addView(tools);status=label(this,"Opening history…",12);status.setPadding(0,dp(this,10),0,dp(this,10));root.addView(status);list=new ListView(this);list.setId(android.R.id.list);NotebookBrowserUi.list(list);list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);}
    @Override protected void onResume(){super.onResume();active=true;refresh();}
    @Override protected void onPause(){active=false;++generation;if(dialog!=null)dialog.dismiss();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("query",query.getText().toString());super.onSaveInstanceState(state);}
    private void refresh(){int request=++generation;NotebookWebsiteTasks.run(()->new NotebookBrowserLibrary(getFilesDir()).read(),(value,error)->{if(!active||request!=generation)return;if(error!=null){status.setText(error.getMessage());return;}snapshot=value;render();});}
    private void render(){if(snapshot==null||list==null)return;matches.clear();String filter=query.getText().toString().toLowerCase(Locale.ROOT);for(NotebookBrowserLibrary.Visit visit:snapshot.visits)if((visit.title+" "+visit.url).toLowerCase(Locale.ROOT).contains(filter))matches.add(visit);adapter.notifyDataSetChanged();status.setText(matches.size()+" recent pages · Latest visit per address · Up to 1,000 pages");}
    private View visitRow(NotebookBrowserLibrary.Visit visit){
        LinearLayout line=row(this);line.setBackground(NotebookUi.rounded(getResources().getDisplayMetrics().density,7));line.setPadding(dp(this,12),dp(this,10),dp(this,10),dp(this,10));TextView info=label(this,visit.title+"\n"+visit.url+"\n"+DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(new Date(visit.time)),15);info.setMaxLines(4);info.setEllipsize(TextUtils.TruncateAt.END);info.setMinimumHeight(dp(this,64));info.setContentDescription("Open history website "+visit.title);info.setOnClickListener(v->{setResult(RESULT_OK,new Intent().putExtra(NotebookBrowserActivity.INITIAL_URL,visit.url));finish();});line.addView(info,new LinearLayout.LayoutParams(0,-2,1));NotebookButton remove=icon(this,NotebookIconDrawable.Icon.CLOSE,"Remove history entry "+visit.title,()->{NotebookWebsiteTasks.run(()->{new NotebookBrowserLibrary(getFilesDir()).removeVisit(visit.url);return null;},(value,error)->{if(active){if(error!=null)status.setText(error.getMessage());else refresh();}});});line.addView(remove,beside(this,48,48));return line;
    }
    private void clear(){if(NotebookBrowserData.clearing())return;dialog=new AlertDialog.Builder(this).setTitle("Clear browsing history?").setMessage("This closes open web pages and removes recent addresses. Cookies, bookmarks, offline copies and notebooks are kept.").setNegativeButton("Cancel",null).setPositiveButton("Clear history",(d,w)->NotebookBrowserData.clear(this,null,false,false,true,(value,error)->{if(active){if(error!=null)status.setText(error);else refresh();}})).create();NotebookUi.show(dialog);}
}
