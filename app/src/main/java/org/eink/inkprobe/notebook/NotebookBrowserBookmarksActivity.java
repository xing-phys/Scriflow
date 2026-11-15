package org.eink.inkprobe.notebook;

import android.app.*;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import static org.eink.inkprobe.notebook.NotebookBrowserUi.*;

/** Bookmark collections and pin management; all addresses remain online links. */
public final class NotebookBrowserBookmarksActivity extends NotebookDisplayActivity {
    private NotebookBrowserLibrary.Snapshot snapshot;
    private GridView list;
    private boolean thumbnails;
    private NotebookBrowserIconButton presentation;
    private final List<NotebookBrowserLibrary.Link> matches=new ArrayList<>();
    private final BaseAdapter adapter=new BaseAdapter(){public int getCount(){return matches.size();}public Object getItem(int at){return matches.get(at);}public long getItemId(int at){return at;}public View getView(int at,View recycled,ViewGroup parent){NotebookBrowserLibrary.Link link=matches.get(at);return thumbnails?new NotebookBrowserCard(NotebookBrowserBookmarksActivity.this,link.title,link.url,"Open bookmark "+link.title,false,false,()->open(link.url),NotebookIconDrawable.Icon.MORE,"Manage bookmark "+link.title,()->actions(link)):linkRow(link);}};
    private Spinner folders;
    private EditText search;
    private TextView status;
    private AlertDialog dialog;
    private boolean busy,active,offered;
    private String selected="*";
    private int generation;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().setWindowAnimations(0);
        if(saved!=null){selected=saved.getString("folder","*");offered=saved.getBoolean("offered");}
        thumbnails=getSharedPreferences("browser-ui",MODE_PRIVATE).getBoolean("bookmarksThumbnails",false);
        LinearLayout root=column(this);root.setPadding(dp(this,18),dp(this,12),dp(this,18),dp(this,12));header(this,root,"Bookmarks",this::finish,deviceSettingsIsland());
        LinearLayout tools=row(this);
        folders=new Spinner(this);folders.setContentDescription("Bookmark collection");
        if(compact(this)){root.addView(folders,new LinearLayout.LayoutParams(-1,dp(this,48)));space(root,dp(this,8));}else tools.addView(folders,new LinearLayout.LayoutParams(0,dp(this,48),1));
        tools.addView(icon(this,NotebookIconDrawable.Icon.BOOKMARK,"Add bookmark",()->edit(null,getIntent().getStringExtra("addUrl"),false)),compact(this)?new LinearLayout.LayoutParams(dp(this,48),dp(this,48)):beside(this,48,48));
        tools.addView(icon(this,NotebookIconDrawable.Icon.NEW_FOLDER,"New collection",()->collection(null)),beside(this,48,48));
        tools.addView(icon(this,NotebookIconDrawable.Icon.MORE,"Manage collection",this::collectionActions),beside(this,48,48));root.addView(tools);space(root,dp(this,12));
        presentation=icon(this,NotebookIconDrawable.Icon.OVERVIEW,"Show bookmark thumbnails",()->{thumbnails=!thumbnails;getSharedPreferences("browser-ui",MODE_PRIVATE).edit().putBoolean("bookmarksThumbnails",thumbnails).apply();presentation();adapter.notifyDataSetChanged();});tools.addView(presentation,beside(this,48,48));
        folders.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int at,long id){if(snapshot!=null){selected=at==0?"*":at==1?"":snapshot.folders.get(at-2).id;render();}}});
        search=input(this,"Find bookmarks",saved==null?"":saved.getString("query",""));field(search);search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int b,int c){render();}public void afterTextChanged(Editable e){}});root.addView(search,new LinearLayout.LayoutParams(-1,dp(this,48)));
        status=label(this,"Opening bookmarks…",12);status.setPadding(0,dp(this,10),0,dp(this,10));root.addView(status);list=new GridView(this);list.setId(android.R.id.list);list.setOverScrollMode(View.OVER_SCROLL_NEVER);list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));list.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);presentation();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
    private void presentation(){if(list==null)return;list.setNumColumns(thumbnails?Math.max(1,Math.min(4,(getResources().getConfiguration().screenWidthDp-24)/212)):1);list.setHorizontalSpacing(dp(this,12));list.setVerticalSpacing(dp(this,12));presentation.setSymbol(thumbnails?NotebookIconDrawable.Icon.OUTLINE:NotebookIconDrawable.Icon.OVERVIEW,thumbnails?"Show bookmarks as list":"Show bookmark thumbnails");}
    @Override protected void onResume(){super.onResume();active=true;refresh();}
    @Override protected void onPause(){active=false;++generation;if(dialog!=null)dialog.dismiss();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("folder",selected);state.putString("query",search.getText().toString());state.putBoolean("offered",offered);super.onSaveInstanceState(state);}
    private void refresh(){
        busy=true;int request=++generation;NotebookWebsiteTasks.run(()->new NotebookBrowserLibrary(getFilesDir()).initialize(),(value,error)->{
            if(!active||request!=generation)return;busy=false;if(error!=null){problem(error.getMessage());return;}snapshot=value;
            List<String> names=new ArrayList<>(Arrays.asList("All bookmarks","Unsorted"));int at=selected.equals("")?1:0;
            for(NotebookBrowserLibrary.Folder folder:value.folders){if(folder.id.equals(selected))at=names.size();names.add(folder.title);}
            folders.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));folders.setSelection(at);render();
            if(!offered&&getIntent().getBooleanExtra("edit",false)){offered=true;String url=getIntent().getStringExtra("addUrl");edit(value.find(url),url,getIntent().getBooleanExtra("pin",false));}
        });
    }
    private void render(){
        if(list==null||snapshot==null)return;matches.clear();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        for(NotebookBrowserLibrary.Link link:snapshot.links)if((selected.equals("*")||selected.equals(link.folder))&&(link.title+" "+link.url).toLowerCase(Locale.ROOT).contains(query))matches.add(link);
        adapter.notifyDataSetChanged();status.setText(matches.isEmpty()?"No bookmarks here. Add a website or choose another collection.":matches.size()+" bookmarks · Tap to open online");
    }
    private View linkRow(NotebookBrowserLibrary.Link link){
        LinearLayout row=row(this);row.setBackground(NotebookUi.rounded(getResources().getDisplayMetrics().density,7));row.setPadding(dp(this,12),dp(this,10),dp(this,10),dp(this,10));
        TextView info=label(this,link.title+(link.pinned?" · Pinned":"")+"\n"+link.url+"\n"+snapshot.folderTitle(link.folder),15);info.setMaxLines(4);info.setEllipsize(TextUtils.TruncateAt.END);info.setMinimumHeight(dp(this,64));info.setContentDescription("Open bookmark "+link.title);info.setOnClickListener(v->open(link.url));row.addView(info,new LinearLayout.LayoutParams(0,-2,1));row.addView(icon(this,NotebookIconDrawable.Icon.MORE,"Manage bookmark "+link.title,()->actions(link)),beside(this,48,48));return row;
    }
    private void open(String url){if(busy)return;setResult(RESULT_OK,new Intent().putExtra(NotebookBrowserActivity.INITIAL_URL,url));finish();}
    private void actions(NotebookBrowserLibrary.Link link){if(busy)return;dialog=new AlertDialog.Builder(this).setTitle(link.title).setItems(new String[]{"Open website","Edit / move to collection",link.pinned?"Unpin from start page":"Pin to start page","Move shortcut earlier","Move shortcut later","Delete bookmark"},(d,at)->{
        if(at==0)open(link.url);else if(at==1)edit(link,link.url,link.pinned);else if(at==2)mutate(()->{new NotebookBrowserLibrary(getFilesDir()).pin(link.id,!link.pinned);return null;});else if(at==3||at==4)mutate(()->{new NotebookBrowserLibrary(getFilesDir()).movePin(link.id,at==3?-1:1);return null;});else confirm("Delete bookmark?","Remove this bookmark and its pinned shortcut? Offline copies stay available.",()->{new NotebookBrowserLibrary(getFilesDir()).remove(link.id);return null;});
    }).create();NotebookUi.show(dialog);}
    private void edit(NotebookBrowserLibrary.Link link,String url,boolean pinned){
        if(busy||snapshot==null)return;LinearLayout form=column(this);form.setPadding(dp(this,16),dp(this,6),dp(this,16),dp(this,6));
        EditText name=input(this,"Bookmark name",link==null?getIntent().getStringExtra("addTitle"):link.title),address=input(this,"HTTPS website address",link==null?url:link.url);form.addView(name);form.addView(address);
        Spinner folder=new Spinner(this);List<String> names=new ArrayList<>();names.add("Unsorted");int selectedFolder=0;for(NotebookBrowserLibrary.Folder item:snapshot.folders){if(link!=null&&item.id.equals(link.folder))selectedFolder=names.size();names.add(item.title);}folder.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));folder.setSelection(selectedFolder);form.addView(folder);
        CheckBox pin=new CheckBox(this);pin.setText("Pin on browser start page");pin.setTextColor(Color.BLACK);pin.setChecked(link==null?pinned:link.pinned);form.addView(pin);
        dialog=new AlertDialog.Builder(this).setTitle(link==null?"Add bookmark":"Edit bookmark").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();NotebookUi.show(dialog);
        AlertDialog owner=dialog;owner.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{String target=NotebookBrowserAddress.normalize(address.getText().toString());int at=folder.getSelectedItemPosition();String collection=at<=0?"":snapshot.folders.get(at-1).id;owner.dismiss();mutate(()->{new NotebookBrowserLibrary(getFilesDir()).put(link==null?null:link.id,name.getText().toString(),target,collection,pin.isChecked());return null;});}catch(IllegalArgumentException error){address.setError(error.getMessage());}});
    }
    private void collection(NotebookBrowserLibrary.Folder existing){if(busy)return;EditText name=input(this,"Collection name",existing==null?"":existing.title);dialog=new AlertDialog.Builder(this).setTitle(existing==null?"New collection":"Rename collection").setView(name).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->mutate(()->{new NotebookBrowserLibrary(getFilesDir()).folder(existing==null?null:existing.id,name.getText().toString());return null;})).create();NotebookUi.show(dialog);}
    private void collectionActions(){if(busy||snapshot==null)return;NotebookBrowserLibrary.Folder value=null;for(NotebookBrowserLibrary.Folder item:snapshot.folders)if(item.id.equals(selected))value=item;if(value==null){problem("Choose a named collection first.");return;}NotebookBrowserLibrary.Folder target=value;dialog=new AlertDialog.Builder(this).setTitle(value.title).setItems(new String[]{"Rename collection","Remove collection"},(d,at)->{if(at==0)collection(target);else confirm("Remove collection?","Bookmarks will move to Unsorted. Pinned shortcuts remain on the start page.",()->{new NotebookBrowserLibrary(getFilesDir()).removeFolder(target.id);return null;});}).create();NotebookUi.show(dialog);}
    private void confirm(String title,String message,java.util.concurrent.Callable<Void> task){dialog=new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("Cancel",null).setPositiveButton("Remove",(d,w)->mutate(task)).create();NotebookUi.show(dialog);}
    private void mutate(java.util.concurrent.Callable<Void> task){if(busy)return;busy=true;int request=++generation;NotebookWebsiteTasks.run(task,(value,error)->{if(!active||request!=generation)return;busy=false;if(error!=null)problem(error.getMessage());else refresh();});}
    private void problem(String message){status.setText(message);dialog=new AlertDialog.Builder(this).setTitle("Bookmarks").setMessage(message).setPositiveButton("Done",null).create();NotebookUi.show(dialog);}
}
