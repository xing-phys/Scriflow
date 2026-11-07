package org.eink.inkprobe.notebook;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Owns independent floating chat sessions, the shared-style tray and window restoration. */
final class NotebookAiWindows {
    interface Listener {
        boolean allowed();boolean contactStart();void contactEnd();void boundsChanged();void workspaceChanged();
        void attachNotebook(NotebookAiChat chat);void chatRaised();
    }
    private final Activity activity;
    private final FrameLayout root;
    private final Listener listener;
    final NotebookAiWorkspace workspace=new NotebookAiWorkspace();
    private final Map<Integer,NotebookAiChat> windows=new LinkedHashMap<>();
    private final Map<Integer,Rect> bounds=new LinkedHashMap<>();
    private final NotebookNoteTray tray;
    private final android.content.SharedPreferences prefs;
    private boolean suspended=true, historyAsMain, shelfMainOpened;
    private int frontId;
    private String saved;
    private AlertDialog menu;
    private NotebookAiChat menuOwner;
    private NotebookAiHistoryDialog history;

    NotebookAiWindows(Activity activity,FrameLayout root,Listener listener,String restored){
        this.activity=activity;this.root=root;this.listener=listener;prefs=activity.getPreferences(Activity.MODE_PRIVATE);
        saved=restored==null?prefs.getString("aiWindows",null):restored;workspace.restore(saved);
        // Discard invalid destinations from damaged local preferences, never guessed credentials.
        for(NotebookAiWorkspace.Window window:workspace.windows())try{
            new NotebookAiConfig(NotebookAiConfig.Provider.valueOf(window.provider),window.providerId,window.url,window.model,"local-validation",null).validate();
            if(NotebookAiHistory.deleted(activity.getFilesDir(),window.sessionId))workspace.close(window);
        }
        catch(IllegalArgumentException error){workspace.close(window);}
        catch(java.io.IOException ignored){/* Retain a history window when storage is temporarily unavailable. */}
        tray=new NotebookNoteTray(activity,new NotebookNoteTray.Listener(){
            public void add(){trayAction(()->open(NotebookAiSettings.publicConfig(activity),null,false));}
            public void reopen(NotebookWorkspace.Pane pane){trayAction(()->restore(workspace.find(pane.notebookId)));}
            public void boundsChanged(){listener.boundsChanged();}
            public String name(int id){return windowName(workspace.find(id));}
        });
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(!suspended&&(r-l!=or-ol||b-t!=ob-ot)){positionAll();listener.workspaceChanged();}});
    }
    void migrate(String id,String profile){
        if(!workspace.windows().isEmpty())return;NotebookAiConfig config=NotebookAiSettings.publicConfig(activity);
        if(config.model.isEmpty())return;String restored=NotebookAiHistory.restoreId(id,profile,config.profile());
        workspace.add(config.provider.name(),config.providerId,config.baseUrl,config.model,restored==null?NotebookAiHistory.newId():restored,false);persist();
    }
    void resume(){
        if(!suspended||!listener.allowed()||!root.isAttachedToWindow())return;suspended=false;
        NotebookAiWorkspace.Window active=workspace.active();
        for(NotebookAiWorkspace.Window window:workspace.windows())if(window!=active&&!window.pane.collapsed)show(window,false);
        if(active!=null&&!active.pane.collapsed)show(active,false);
        listener.workspaceChanged();
    }
    boolean visible(){for(NotebookAiWorkspace.Window window:workspace.windows())if(!window.pane.collapsed)return !suspended;return false;}
    boolean trayShowing(){return tray.showing();}
    void hideTray(){tray.hide();}
    List<Rect> exclusions(){List<Rect> result=new ArrayList<>();for(Map.Entry<Integer,Rect> item:bounds.entrySet())
        if(!(workspace.mainChat()&&item.getKey()==workspace.geometry.base().notebookId)&&!item.getValue().isEmpty())result.add(new Rect(item.getValue()));
        Rect strip=tray.bounds();if(!strip.isEmpty())result.add(strip);return result;}
    void toggle(View button,Rect tools,boolean vertical){
        if(!listener.allowed())return;resume();
        if(!listener.contactStart())return;
        if(tray.showing())tray.hide();else tray.show(root,button,tools,vertical,workspace.geometry.panes(),"Open a new floating chat",NotebookIconDrawable.Icon.AI_CHAT,this::history);
        listener.contactEnd();
    }
    private void history(){
        if(history!=null||!listener.allowed()||!listener.contactStart())return;tray.hide();
        for(NotebookAiChat chat:windows.values())chat.saveDraft();
        List<NotebookAiConfig> configs=NotebookAiSettings.publicConfigs(activity);
        for(NotebookAiWorkspace.Window state:workspace.windows())configs.add(new NotebookAiConfig(NotebookAiConfig.Provider.valueOf(state.provider),state.providerId,state.url,state.model,"",null));
        history=new NotebookAiHistoryDialog(activity,root,configs,new NotebookAiHistoryDialog.Listener(){
            public void open(NotebookAiConfig config,String id,boolean rebind){NotebookAiWindows.this.openHistorySelection(config,id,rebind);}
            public void deleted(String id){NotebookAiWindows.this.deleted(id);}
            public void closed(){history=null;historyAsMain=false;listener.contactEnd();}
        });history.show();
    }
    private void openHistorySelection(NotebookAiConfig config,String id,boolean rebind){
        open(config,id,rebind);
        if(historyAsMain){NotebookAiWorkspace.Window state=workspace.session(id);if(state!=null)makeShelfMain(state);}
    }
    void historyFromShelf(){if(suspended)resume();if(suspended)return;historyAsMain=true;history();if(history==null)historyAsMain=false;}
    void newMainFromShelf(){
        if(suspended)resume();if(suspended||!listener.allowed()||!listener.contactStart())return;
        String id=NotebookAiHistory.newId();open(NotebookAiSettings.publicConfig(activity),id,false);
        NotebookAiWorkspace.Window state=workspace.session(id);if(state!=null)makeShelfMain(state);
        listener.contactEnd();
    }
    private void makeShelfMain(NotebookAiWorkspace.Window state){
        workspace.makeMain(state);shelfMainOpened=true;persist();positionAll();listener.workspaceChanged();
    }
    boolean consumeShelfMain(){boolean value=shelfMainOpened;shelfMainOpened=false;return value;}
    boolean retainsMainChat(){return workspace.mainChat();}
    private void deleted(String sessionId){
        if(activity.isFinishing()||activity.isDestroyed())return;
        NotebookAiWorkspace.Window deleted=workspace.session(sessionId);if(deleted==null)return;
        NotebookAiChat view=windows.get(deleted.pane.notebookId);if(view!=null)view.close();else{workspace.close(deleted);persist();listener.workspaceChanged();}
    }
    private void trayAction(Runnable action){if(!listener.allowed()||!listener.contactStart())return;for(NotebookAiChat chat:windows.values())chat.saveDraft();tray.hide();action.run();listener.contactEnd();}
    private void open(NotebookAiConfig config,String sessionId,boolean rebind){
        if(suspended)return;
        if(config.model.isEmpty()){
            Toast.makeText(activity,"Fetch and select models in Settings → AI chat first",Toast.LENGTH_LONG).show();activity.startActivity(new Intent(activity,NotebookSettingsActivity.class));return;
        }
        try{
            new NotebookAiConfig(config.provider,config.baseUrl,config.model,"local-validation").validate();
            NotebookAiWorkspace.Window existing=workspace.session(sessionId);
            if(existing!=null){restore(existing);return;}
            NotebookAiWorkspace.Window window=workspace.add(config.provider.name(),config.providerId,config.baseUrl,config.model,sessionId==null?NotebookAiHistory.newId():sessionId,rebind);
            show(window,false);persist();listener.workspaceChanged();
        }catch(IllegalArgumentException error){Toast.makeText(activity,error.getMessage(),Toast.LENGTH_LONG).show();}
    }
    private NotebookAiChat create(NotebookAiWorkspace.Window state){
        final int id=state.pane.notebookId;final NotebookAiChat[] owner={null};
        NotebookAiChat chat=new NotebookAiChat(activity,root,workspace,state,new NotebookAiChat.Listener(){
            public boolean contactStart(){return !suspended&&listener.contactStart();}
            public void contactEnd(){listener.contactEnd();}
            public void boundsChanged(Rect rect){bounds.put(id,new Rect(rect));listener.boundsChanged();}
            public boolean activated(){
                if(workspace.find(id)==null)return false;
                boolean raise=frontId!=id;frontId=id;listener.chatRaised();workspace.focus(state);persist();return raise;
            }
            public boolean mayRaise(){return frontId==id;}
            public void attachNotebook(NotebookAiChat chat){listener.attachNotebook(chat);}
            public void minimized(){if(workspace.find(id)!=null){workspace.minimize(state);persist();positionAll();listener.workspaceChanged();}}
            public void options(){showMenu(state,owner[0]);}
            public void open(NotebookAiConfig config,String sessionId,boolean rebind){NotebookAiWindows.this.open(config,sessionId,rebind);}
            public void changed(){sync(state,owner[0]);persist();}
            public void closed(){
                bounds.remove(id);windows.remove(id);if(menuOwner==owner[0]&&menu!=null)menu.dismiss();
                if(!suspended&&workspace.find(id)!=null){workspace.close(state);persist();positionAll();listener.workspaceChanged();}
                listener.boundsChanged();
            }
        });owner[0]=chat;windows.put(id,chat);return chat;
    }
    private void sync(NotebookAiWorkspace.Window state,NotebookAiChat chat){
        if(chat==null||workspace.find(state.pane.notebookId)==null)return;
        state.sessionId=chat.sessionId();NotebookAiConfig config=chat.configuration();state.provider=config.provider.name();state.providerId=config.providerId;state.url=config.baseUrl;state.model=config.model;state.title=chat.name();
    }
    private void show(NotebookAiWorkspace.Window state,boolean raise){
        if(state==null||suspended)return;NotebookAiChat chat=windows.get(state.pane.notebookId);
        if(chat==null){chat=create(state);chat.show();}else if(raise)chat.raise();else chat.show();
        frontId=state.pane.notebookId;listener.chatRaised();
    }
    void noteRaised(){frontId=0;}
    void raiseFloatingAboveTools(){
        if(suspended)return;
        NotebookAiWorkspace.Window active=workspace.active();
        for(NotebookAiWorkspace.Window state:workspace.windows())if(state!=active){
            NotebookAiChat chat=windows.get(state.pane.notebookId);if(chat!=null)chat.raiseAboveTools();
        }
        if(active!=null){NotebookAiChat chat=windows.get(active.pane.notebookId);if(chat!=null)chat.raiseAboveTools();}
    }
    private void restore(NotebookAiWorkspace.Window state){if(state==null)return;workspace.focus(state);show(state,true);persist();positionAll();listener.workspaceChanged();}
    private String windowName(NotebookAiWorkspace.Window state){if(state==null)return "Chat";String name=state.title;if(name.equals("New chat"))name+=" "+(state.pane.notebookId-1);return name+" · "+state.model;}
    private void positionAll(){if(suspended)return;for(NotebookAiChat chat:new ArrayList<>(windows.values()))chat.position();listener.boundsChanged();}
    boolean mainChat(){return !suspended&&workspace.mainChat();}
    NotebookWorkspace.Box background(){return suspended||mainChat()?new NotebookWorkspace.Box(0,0,root.getWidth(),root.getHeight())
        :workspace.geometry.box(workspace.geometry.find(1),root.getWidth(),root.getHeight());}
    void persist(){
        for(NotebookAiWorkspace.Window state:workspace.windows())sync(state,windows.get(state.pane.notebookId));
        String encoded=workspace.encode();if(!encoded.equals(saved)){saved=encoded;prefs.edit().putString("aiWindows",encoded).apply();}
    }
    String snapshot(){persist();return workspace.encode();}
    void suspend(){
        if(suspended)return;persist();suspended=true;frontId=0;tray.hide();if(menu!=null)menu.dismiss();if(history!=null)history.close();
        for(NotebookAiChat chat:new ArrayList<>(windows.values()))chat.close();windows.clear();bounds.clear();listener.boundsChanged();
    }
    void append(String source,NotebookAiAttachment image){
        if(suspended)resume();if(suspended)return;NotebookAiWorkspace.Window state=workspace.active();
        if(state==null){open(NotebookAiSettings.publicConfig(activity),null,false);state=workspace.active();}
        if(state==null)return;restore(state);NotebookAiChat chat=windows.get(state.pane.notebookId);if(chat!=null)chat.append(source,image);
    }
    boolean back(){
        if(tray.showing()){tray.hide();return true;}
        NotebookAiWorkspace.Window state=workspace.active();if(state!=null&&!state.pane.collapsed){NotebookAiChat chat=windows.get(state.pane.notebookId);if(chat!=null){chat.close();return true;}}return false;
    }
    private void showMenu(NotebookAiWorkspace.Window state,NotebookAiChat chat){
        if(menu!=null){chat.optionsFinished();return;}final int[] choice={-1};menuOwner=chat;
        String[] standard=NotebookWindowControls.options("chat"),items=Arrays.copyOf(standard,standard.length+1);
        items[standard.length]="Copy last AI reply";
        menu=new AlertDialog.Builder(activity).setTitle(windowName(state)).setItems(items,(d,index)->choice[0]=index).setNegativeButton("Cancel",null).create();
        menu.setOnDismissListener(d->{menu=null;menuOwner=null;
            if(!suspended&&workspace.find(state.pane.notebookId)!=null){
                if(choice[0]==0){showPosition(state,chat);return;}
                if(choice[0]==1)chat.minimize();else if(choice[0]==2)chat.close();else if(choice[0]==standard.length)chat.copyLastReply();
                else if(choice[0]>=3&&choice[0]<standard.length){workspace.geometry.resize(state.pane,choice[0]==3?-.1f:.1f,choice[0]==4);persist();positionAll();listener.workspaceChanged();}
            }chat.optionsFinished();
        });NotebookUi.show(menu);
    }
    private void showPosition(NotebookAiWorkspace.Window state,NotebookAiChat chat){
        final int[] choice={-1};menuOwner=chat;
        String[] positions=Arrays.copyOf(NotebookWindowControls.POSITIONS,NotebookWindowControls.POSITIONS.length+1);
        positions[positions.length-1]="Main window";
        menu=new AlertDialog.Builder(activity).setTitle("Position chat").setItems(positions,(d,index)->choice[0]=index).setNegativeButton("Cancel",null).create();
        menu.setOnDismissListener(d->{menu=null;menuOwner=null;
            if(!suspended&&choice[0]>=0&&workspace.find(state.pane.notebookId)!=null){
                if(choice[0]==NotebookWindowControls.POSITIONS.length)workspace.makeMain(state);
                else workspace.place(state,NotebookWorkspace.Position.values()[choice[0]]);
                persist();positionAll();listener.workspaceChanged();}chat.optionsFinished();
        });NotebookUi.show(menu);
    }
}
