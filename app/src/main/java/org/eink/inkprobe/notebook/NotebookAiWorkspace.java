package org.eink.inkprobe.notebook;

import java.io.*;
import java.util.*;

/** Window identities and public destinations; transcripts and credentials are stored elsewhere. */
final class NotebookAiWorkspace {
    static final class Window {
        final NotebookWorkspace.Pane pane;
        String sessionId,provider,url,model,title="New chat";
        boolean rebind;
        Window(NotebookWorkspace.Pane pane,String sessionId,String provider,String url,String model,boolean rebind){this.pane=pane;this.sessionId=sessionId;this.provider=provider;this.url=url;this.model=model;this.rebind=rebind;}
    }
    final NotebookWorkspace geometry=new NotebookWorkspace();
    private final LinkedHashMap<Integer,Window> windows=new LinkedHashMap<>();
    private int next=2,active;
    NotebookAiWorkspace(){geometry.add(1,NotebookWorkspace.Position.FLOATING);}
    List<Window> windows(){return new ArrayList<>(windows.values());}
    Window find(int id){return windows.get(id);}
    Window active(){return windows.get(active);}
    Window session(String id){if(id!=null)for(Window window:windows.values())if(id.equals(window.sessionId))return window;return null;}
    Window add(String provider,String url,String model,String sessionId,boolean rebind){
        Window existing=session(sessionId);if(existing!=null){focus(existing);return existing;}
        if(windows.size()>=64)throw new IllegalArgumentException("Close a chat window before opening another");
        validate(provider,url,model,sessionId);
        if(next<2||next==Integer.MAX_VALUE){next=2;while(windows.containsKey(next))next++;}
        NotebookWorkspace.Pane pane=geometry.add(next++,NotebookWorkspace.Position.FLOATING);
        Window window=new Window(pane,sessionId,provider,url,model,rebind);windows.put(pane.notebookId,window);active=pane.notebookId;return window;
    }
    void focus(Window window){require(window);window.pane.collapsed=false;windows.remove(window.pane.notebookId);windows.put(window.pane.notebookId,window);active=window.pane.notebookId;}
    void minimize(Window window){require(window);geometry.minimize(window.pane);}
    void close(Window window){require(window);geometry.close(window.pane);windows.remove(window.pane.notebookId);if(active==window.pane.notebookId){active=0;for(Window other:windows.values())active=other.pane.notebookId;}}
    private void require(Window window){if(window==null||windows.get(window.pane.notebookId)!=window)throw new IllegalArgumentException("Unknown chat window");}
    private static void validate(String provider,String url,String model,String id){
        if(!Arrays.asList("OPENAI","ANTHROPIC","GEMINI","DEEPSEEK","CUSTOM").contains(provider)||url==null||url.length()>2048||model==null||model.isEmpty()||model.length()>200
                ||id==null||!UUID.fromString(id).toString().equals(id))throw new IllegalArgumentException("Invalid chat window");
        for(char c:(url+model).toCharArray())if(Character.isISOControl(c))throw new IllegalArgumentException("Invalid chat window");
    }
    String encode(){
        try{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
            out.writeInt(0x53415731);out.writeInt(next);out.writeInt(active);out.writeUTF(geometry.encode());out.writeInt(windows.size());
            for(Window window:windows.values()){out.writeInt(window.pane.notebookId);out.writeUTF(window.sessionId);out.writeUTF(window.provider);out.writeUTF(window.url);out.writeUTF(window.model);out.writeUTF(window.title);out.writeBoolean(window.rebind);}
            out.close();return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }catch(IOException error){throw new IllegalStateException(error);}
    }
    void restore(String encoded){
        windows.clear();geometry.restore(null);geometry.add(1,NotebookWorkspace.Position.FLOATING);active=0;next=2;
        if(encoded==null||encoded.length()>256*1024)return;
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))){
            if(in.readInt()!=0x53415731)return;int restoredNext=in.readInt(),restoredActive=in.readInt();
            NotebookWorkspace restored=new NotebookWorkspace();restored.restore(in.readUTF());int count=in.readInt();if(count<0||count>64)return;
            List<Window> valid=new ArrayList<>();Set<String> ids=new HashSet<>();Set<Integer> panes=new HashSet<>();
            for(int i=0;i<count;i++){
                int number=in.readInt();String id=in.readUTF(),provider=in.readUTF(),url=in.readUTF(),model=in.readUTF(),title=in.readUTF();boolean rebind=in.readBoolean();
                try{validate(provider,url,model,id);NotebookWorkspace.Pane pane=restored.find(number);
                    if(number<2||number==Integer.MAX_VALUE||pane==null||title.length()>200||!ids.add(id)||!panes.add(number))continue;Window window=new Window(pane,id,provider,url,model,rebind);window.title=title;valid.add(window);
                }catch(IllegalArgumentException ignored){}
            }
            if(in.read()!=-1)return;
            Map<Integer,Window> validById=new HashMap<>(),rebuilt=new HashMap<>();
            for(Window old:valid)validById.put(old.pane.notebookId,old);
            // Geometry has its own stable pane order; the window list records stacking order.
            for(NotebookWorkspace.Pane restoredPane:restored.panes()){
                Window old=validById.get(restoredPane.notebookId);if(old==null)continue;
                NotebookWorkspace.Pane pane=geometry.add(old.pane.notebookId,NotebookWorkspace.Position.FLOATING);
                pane.x=old.pane.x;pane.y=old.pane.y;pane.width=old.pane.width;pane.height=old.pane.height;pane.split=old.pane.split;pane.collapsed=old.pane.collapsed;
                geometry.place(pane,old.pane.position);Window window=new Window(pane,old.sessionId,old.provider,old.url,old.model,old.rebind);window.title=old.title;rebuilt.put(pane.notebookId,window);next=Math.max(next,pane.notebookId+1);}
            for(Window old:valid)windows.put(old.pane.notebookId,rebuilt.get(old.pane.notebookId));
            if(restoredNext>=next&&restoredNext<Integer.MAX_VALUE)next=restoredNext;
            for(Window window:windows.values())active=window.pane.notebookId;
            if(windows.containsKey(restoredActive))active=restoredActive;
        }catch(IOException|IllegalArgumentException ignored){windows.clear();geometry.restore(null);geometry.add(1,NotebookWorkspace.Position.FLOATING);active=0;next=2;}
    }
}
