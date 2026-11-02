package org.eink.inkprobe.notebook;

import android.app.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.List;

/** Toolbox history is independent of any visible chat and never starts a provider request. */
final class NotebookAiHistoryDialog {
    interface Listener {
        void open(NotebookAiConfig config,String id,boolean rebind);void deleted(String id);void closed();
    }
    private final Activity activity;
    private final View anchor;
    private final Listener listener;
    private final NotebookAiSession worker;
    private final List<NotebookAiConfig> configs;
    private AlertDialog dialog,confirmation;
    private boolean closed,deleting;

    NotebookAiHistoryDialog(Activity activity,View anchor,List<NotebookAiConfig> configs,Listener listener){
        this.activity=activity;this.anchor=anchor;this.configs=configs;this.listener=listener;
        // Use only the existing serialized archive operations: no load, save, WebView or HTTP.
        worker=new NotebookAiSession(activity.getFilesDir(),NotebookAiSettings.publicConfig(activity),new Handler(Looper.getMainLooper())::post,()->{});
    }
    void show(){
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(8),0,dp(8),0);
        TextView description=label("Loading saved chats…",12);body.addView(description);
        ListView list=new ListView(activity);list.setBackgroundColor(Color.WHITE);list.setCacheColorHint(Color.WHITE);
        list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        body.addView(list,new LinearLayout.LayoutParams(-1,Math.min(dp(420),Math.max(dp(160),anchor.getHeight()-dp(160)))));
        dialog=new AlertDialog.Builder(activity).setTitle("Chat history").setView(body).setNegativeButton("Close",null).create();
        dialog.setOnDismissListener(d->close());NotebookUi.show(dialog);load(list,description);
    }
    private void load(ListView list,TextView description){
        worker.history(configs,(entries,error)->{
            if(closed)return;if(error!=null){description.setText(error);return;}
            description.setText(entries.isEmpty()?"No saved chats yet. Messages and drafts are saved automatically.":"Saved on this device · newest first");
            list.setAdapter(new BaseAdapter(){
                public int getCount(){return entries.size();}public Object getItem(int position){return entries.get(position);}public long getItemId(int position){return position;}
                public View getView(int position,View convert,ViewGroup parent){
                    NotebookAiHistory.Entry entry=entries.get(position);LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
                    String destination=entry.config==null?"Earlier chat":entry.config.provider.label+" · "+entry.config.model;
                    String date=java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(new java.util.Date(entry.updated));
                    LinearLayout open=new LinearLayout(activity);open.setOrientation(LinearLayout.VERTICAL);open.setGravity(Gravity.CENTER_VERTICAL);open.setPadding(dp(4),0,dp(4),0);
                    open.setOnClickListener(v->{if(entry.error.isEmpty())open(entry);});
                    for(String[] line:new String[][]{{entry.title,"14"},{destination,"12"},{date+" · "+entry.messages+" messages"+(entry.draft?" · draft":""),"11"}}){
                        TextView text=label(line[0],Integer.parseInt(line[1]));text.setSingleLine(true);text.setEllipsize(android.text.TextUtils.TruncateAt.END);open.addView(text);
                    }
                    open.setContentDescription("Open saved chat: "+entry.title+"; "+destination+"; "+date);open.setEnabled(entry.error.isEmpty());row.addView(open,new LinearLayout.LayoutParams(0,dp(76),1));
                    NotebookIconButton remove=new NotebookIconButton(activity,NotebookIconDrawable.Icon.DELETE,"Delete saved chat: "+entry.title);remove.setTooltipText("Delete saved chat: "+entry.title);
                    remove.setOnClickListener(v->delete(entry,list,description));row.addView(remove,new LinearLayout.LayoutParams(dp(36),dp(40)));return row;
                }
            });
        });
    }
    private void open(NotebookAiHistory.Entry entry){
        if(closed||deleting||confirmation!=null)return;
        if(entry.config!=null){open(entry,entry.config,false);return;}
        NotebookAiConfig current=NotebookAiSettings.publicConfig(activity);
        confirmation=new AlertDialog.Builder(activity).setTitle("Open earlier chat?")
            .setMessage("This older chat has no saved provider details. Open it with "+current.provider.label+" / "+current.model+"? Nothing is sent until you press the send arrow.")
            .setNegativeButton("Cancel",null).setPositiveButton("Open",(d,w)->open(entry,current,true)).create();
        confirmation.setOnDismissListener(d->confirmation=null);NotebookUi.show(confirmation);
    }
    private void open(NotebookAiHistory.Entry entry,NotebookAiConfig config,boolean rebind){
        if(closed)return;listener.open(config,entry.id,rebind);close();
    }
    private void delete(NotebookAiHistory.Entry entry,ListView list,TextView description){
        if(closed||deleting||confirmation!=null)return;
        confirmation=new AlertDialog.Builder(activity).setTitle("Delete saved chat?")
            .setMessage(entry.title+"\n\nDelete its messages, images and draft from this device? This cannot be undone.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(d,w)->{
                deleting=true;description.setText("Deleting saved chat…");worker.delete(entry.id,error->deleted(entry.id,error,list,description));
            }).create();
        confirmation.setOnDismissListener(d->confirmation=null);NotebookUi.show(confirmation);
    }
    private void deleted(String id,String error,ListView list,TextView description){
        deleting=false;if(error==null)listener.deleted(id);else if(!closed)Toast.makeText(activity,error,Toast.LENGTH_LONG).show();
        if(closed)worker.close();else load(list,description);
    }
    void close(){
        if(closed)return;closed=true;if(confirmation!=null)confirmation.dismiss();if(dialog!=null)dialog.dismiss();
        // An already-confirmed deletion must deliver its ownership callback even after dismissal.
        if(!deleting)worker.close();listener.closed();
    }
    private TextView label(String value,int size){TextView text=new TextView(activity);text.setText(value);text.setTextSize(size);text.setTextColor(Color.BLACK);return text;}
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}
