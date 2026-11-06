package org.eink.inkprobe.notebook;

import java.io.*;
import java.util.*;

/** Chat identities use the real note geometry; windows restore without sharing conversations. */
public final class NotebookAiWorkspaceTest {
    private static int assertions;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private static NotebookAiWorkspace.Window add(NotebookAiWorkspace w){return w.add("OPENAI","https://api.openai.com/v1","model",UUID.randomUUID().toString(),false);}
    private static boolean same(NotebookWorkspace.Box a,NotebookWorkspace.Box b){return a.x==b.x&&a.y==b.y&&a.width==b.width&&a.height==b.height;}
    private static void invalid(Runnable run){try{run.run();}catch(IllegalArgumentException expected){assertions++;return;}throw new AssertionError("Invalid window was accepted");}
    public static void main(String[] args)throws Exception{
        windows();docking();mainChat();restore();damaged();
        System.out.println("NotebookAiWorkspace: "+assertions+" assertions passed (independent windows, note geometry, dock/minimize/restore, destinations and bounded recovery).");
    }
    private static void mainChat(){
        NotebookAiWorkspace workspace=new NotebookAiWorkspace();NotebookAiWorkspace.Window main=add(workspace),other=add(workspace);
        workspace.place(other,NotebookWorkspace.Position.LEFT);
        workspace.makeMain(main);
        check(workspace.mainChat()&&workspace.geometry.base()==main.pane,"selected chat becomes the main workspace");
        check(workspace.geometry.floating(workspace.geometry.find(1))&&workspace.geometry.floating(other.pane),"note placeholder and other chats float above the main chat");
        NotebookWorkspace.Box bounds=workspace.geometry.box(main.pane,1200,1000);
        check(bounds.x==0&&bounds.y==0&&bounds.width==1200&&bounds.height==1000,"main chat uses the full available area");
        NotebookAiWorkspace restored=new NotebookAiWorkspace();restored.restore(workspace.encode());
        check(restored.mainChat()&&restored.geometry.base().notebookId==main.pane.notebookId,"main chat survives workspace restoration");
        restored.minimize(restored.session(main.sessionId));
        check(!restored.mainChat()&&restored.geometry.base().notebookId==1,"minimizing the main chat returns the note workspace");
        workspace.makeMain(other);
        check(workspace.mainChat()&&workspace.geometry.base()==other.pane&&workspace.geometry.floating(main.pane),"switching main chats floats the former main");
        workspace.place(other,NotebookWorkspace.Position.FLOATING);
        check(!workspace.mainChat()&&workspace.geometry.base().notebookId==1,"floating the new main returns to the note even after switching main chats");
        workspace.makeMain(main);workspace.place(other,NotebookWorkspace.Position.RIGHT);
        check(!workspace.mainChat()&&workspace.geometry.base().notebookId==1&&other.pane.position==NotebookWorkspace.Position.RIGHT,"docking another chat restores the note workspace and its complementary split");
        workspace.makeMain(main);
        workspace.close(main);check(!workspace.mainChat()&&workspace.geometry.base().notebookId==1,"closing main chat returns to the note workspace");
        NotebookWorkspace notes=new NotebookWorkspace();NotebookWorkspace.Pane note=notes.add(42,NotebookWorkspace.Position.FLOATING);
        check(!notes.floating(note),"ordinary base note stays full size");notes.setFloatingBase(true);
        check(notes.floating(note)&&notes.box(note,1200,1000).width<1200,"main-chat mode presents the same base note as a floating pane");
    }
    private static void windows(){
        NotebookAiWorkspace w=new NotebookAiWorkspace();NotebookAiWorkspace.Window first=add(w),second=add(w);
        check(first!=second&&!first.sessionId.equals(second.sessionId)&&w.active()==second,"New windows have independent sessions and activation");
        w.focus(first);check(w.windows().get(1)==first,"focusing a chat records it at the front of the stack");
        NotebookAiWorkspace stack=new NotebookAiWorkspace();stack.restore(w.encode());
        check(stack.windows().get(1).sessionId.equals(first.sessionId)&&stack.active().sessionId.equals(first.sessionId),"chat stacking order survives restoration");
        w.focus(second);
        NotebookWorkspace.Box before=w.geometry.box(second.pane,1404,1872);
        first.pane.x=.1f;first.pane.y=.8f;w.geometry.resize(first.pane,.1f,false);
        check(same(before,w.geometry.box(second.pane,1404,1872)),"Resizing and moving one chat preserves the other");
        w.minimize(first);check(first.pane.collapsed&&!second.pane.collapsed,"Only the selected chat is minimized");
        check(w.add("OPENAI","https://api.openai.com/v1","model",first.sessionId,false)==first&&w.windows().size()==2&&!first.pane.collapsed&&w.active()==first,"Opening history focuses its single existing owner and restores it");
        w.close(first);check(w.active()==second&&w.find(first.pane.notebookId)==null,"Closing one window preserves and activates the other");
        List<NotebookAiWorkspace.Window> copy=w.windows();copy.clear();check(w.windows().size()==1,"A caller cannot remove window ownership through a list snapshot");
        invalid(()->w.focus(first));invalid(()->w.minimize(first));invalid(()->w.add("unknown","https://host","id",UUID.randomUUID().toString(),false));
        invalid(()->w.add("OPENAI","https://host","id","../../private",false));invalid(()->w.add("OPENAI","https://host","bad\nmodel",UUID.randomUUID().toString(),false));
        for(int i=1;i<64;i++)add(w);invalid(()->add(w));check(w.windows().size()==64,"Window count is bounded without evicting another conversation");
        w.close(second);check(add(w)!=null&&w.windows().size()==64,"Closing frees a slot for another chat");
    }
    private static void docking(){
        NotebookAiWorkspace w=new NotebookAiWorkspace();NotebookAiWorkspace.Window a=add(w),b=add(w);
        for(NotebookWorkspace.Position position:NotebookWorkspace.Position.values()){
            w.geometry.place(a.pane,position);NotebookWorkspace.Box chat=w.geometry.box(a.pane,1200,1000),paper=w.geometry.box(w.geometry.base(),1200,1000);
            if(position==NotebookWorkspace.Position.FLOATING)check(paper.width==1200&&paper.height==1000,"Floating chats leave the full note workspace");
            else{
                boolean horizontal=position==NotebookWorkspace.Position.LEFT||position==NotebookWorkspace.Position.RIGHT;
                check(horizontal?chat.width+paper.width==1200&&chat.height==1000&&paper.height==1000:chat.height+paper.height==1000&&chat.width==1200&&paper.width==1200,"Chat docking reserves the complementary note area");
                check(horizontal?chat.x+chat.width<=paper.x||paper.x+paper.width<=chat.x:chat.y+chat.height<=paper.y||paper.y+paper.height<=chat.y,"Docked chat and note content do not overlap");
                w.minimize(a);check(w.geometry.box(w.geometry.base(),1200,1000).width==1200&&w.geometry.box(w.geometry.base(),1200,1000).height==1000,"Minimizing a dock returns its space to the note");w.focus(a);
                check(same(chat,w.geometry.box(a.pane,1200,1000)),"Restoring retains the dock position and split");
            }
        }
        w.geometry.place(a.pane,NotebookWorkspace.Position.LEFT);w.geometry.place(b.pane,NotebookWorkspace.Position.BOTTOM);
        check(a.pane.position==NotebookWorkspace.Position.FLOATING&&b.pane.position==NotebookWorkspace.Position.BOTTOM,"A new dock floats the old dock exactly as notes do");
        w.geometry.resize(b.pane,.1f,false);check(w.geometry.box(b.pane,1200,1000).height==600,"Dock sizing uses the same split policy as notes");
        w.close(b);check(w.geometry.box(w.geometry.base(),1200,1000).height==1000,"Closing a dock restores the full note area");
    }
    private static void restore(){
        NotebookAiWorkspace w=new NotebookAiWorkspace();NotebookAiWorkspace.Window a=add(w),b=w.add("CUSTOM","https://local.example/v1","local-model",UUID.randomUUID().toString(),true);
        a.title="A saved question";b.title="Unsent draft";a.pane.x=.91f;a.pane.y=.77f;a.pane.width=.31f;a.pane.height=.79f;
        w.geometry.place(b.pane,NotebookWorkspace.Position.TOP);b.pane.split=.65f;w.focus(a);w.minimize(b);
        String encoded=w.encode();NotebookAiWorkspace restored=new NotebookAiWorkspace();restored.restore(encoded);
        check(restored.windows().size()==2&&restored.active().sessionId.equals(a.sessionId),"Restore retains all windows and the active destination for appended selections");
        NotebookAiWorkspace.Window ra=restored.session(a.sessionId),rb=restored.session(b.sessionId);
        check(ra.title.equals(a.title)&&rb.provider.equals("CUSTOM")&&rb.url.equals(b.url)&&rb.model.equals(b.model)&&rb.rebind,"Original public destination and history binding survive Settings changes");
        check(rb.pane.collapsed&&rb.pane.position==NotebookWorkspace.Position.TOP&&rb.pane.split==.65f,"Minimized state and original dock restore together");
        check(same(w.geometry.box(a.pane,1404,1872),restored.geometry.box(ra.pane,1404,1872)),"Floating geometry survives restoration exactly");
        check(restored.encode().equals(encoded),"A restored workspace serializes consistently");
        String providerId="p-"+UUID.randomUUID();
        NotebookAiWorkspace named=new NotebookAiWorkspace();NotebookAiWorkspace.Window profile=named.add("CUSTOM",providerId,"https://local.example/v1","model",UUID.randomUUID().toString(),false);
        NotebookAiWorkspace namedRestored=new NotebookAiWorkspace();namedRestored.restore(named.encode());
        check(namedRestored.session(profile.sessionId).providerId.equals(providerId),"provider identity survives workspace recreation");
        restored.focus(rb);check(restored.geometry.box(restored.geometry.base(),1404,1872).height<1872,"Restoring a minimized dock reapplies its split");
        check(add(restored).pane.notebookId>b.pane.notebookId,"Next window identity does not reuse a live identity");
        restored.close(ra);restored.close(rb);check(restored.windows().size()==1,"Restored windows can be independently closed");
    }
    private static String record(int next,int active,int number,String id,String provider)throws IOException{
        NotebookWorkspace geometry=new NotebookWorkspace();geometry.add(1,NotebookWorkspace.Position.FLOATING);geometry.add(number,NotebookWorkspace.Position.FLOATING);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(bytes)){
            out.writeInt(0x53415731);out.writeInt(next);out.writeInt(active);out.writeUTF(geometry.encode());out.writeInt(1);
            out.writeInt(number);out.writeUTF(id);out.writeUTF(provider);out.writeUTF("https://host/v1");out.writeUTF("model");out.writeUTF("Saved");out.writeBoolean(false);
        }return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    private static void damaged()throws Exception{
        NotebookAiWorkspace w=new NotebookAiWorkspace();add(w);String valid=w.encode();
        for(String bad:new String[]{null,"!",valid.substring(0,valid.length()/2),Base64.getEncoder().encodeToString(new byte[]{0,0,0,0}),valid+"AAAA",String.join("",Collections.nCopies(256*1024+1,"a"))}){
            w.restore(bad);check(w.windows().isEmpty()&&w.geometry.panes().size()==1&&w.active()==null,"Damaged preferences leave a usable blank workspace");
        }
        w.restore(record(3,2,2,"not-a-uuid","OPENAI"));check(w.windows().isEmpty(),"Unsafe session identifiers cannot enter the window graph");
        w.restore(record(3,2,2,UUID.randomUUID().toString(),"UNKNOWN"));check(w.windows().isEmpty(),"Unknown providers are discarded without guessed credentials");
        w.restore(record(3,999,2,UUID.randomUUID().toString(),"OPENAI"));check(w.active()!=null,"Damaged active identity falls back to a retained window");
        w.restore(record(Integer.MAX_VALUE,2,Integer.MAX_VALUE-1,UUID.randomUUID().toString(),"OPENAI"));check(add(w).pane.notebookId==2,"Next identity safely wraps instead of overflowing");
    }
}
