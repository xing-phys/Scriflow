package org.eink.inkprobe.notebook;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Verify bookshelf launch uses the same main-window identity and contact ordering. */
public final class NotebookAiShelfLaunchTest {
    public static void main(String[] args)throws Exception{
        String source=new String(Files.readAllBytes(Paths.get("app/src/main/java/org/eink/inkprobe/notebook/NotebookAiWindows.java")),StandardCharsets.UTF_8);
        String fixture=String.join("\n",
            "package org.eink.inkprobe.notebook;import java.util.*;public class AiShelfLaunchFixture {",
            "static class NotebookAiConfig{}static class NotebookAiSettings{static NotebookAiConfig publicConfig(Object c){return new NotebookAiConfig();}}static class NotebookAiHistory{static String newId(){return UUID.randomUUID().toString();}}",
            "static class Listener{int starts,ends,changes,exits;boolean allowed(){return true;}boolean contactStart(){starts++;return true;}void contactEnd(){ends++;}void workspaceChanged(){changes++;}boolean mainDismissed(){exits++;return true;}}",
            "final Object activity=new Object();final Listener listener=new Listener();final NotebookAiWorkspace workspace=new NotebookAiWorkspace();boolean suspended,historyAsMain,shelfMainOpened,independent,rejectOpen;Object history;int saves,positions;",
            "void resume(){suspended=false;}void persist(){saves++;}void positionAll(){positions++;}void history(){history=new Object();}void open(NotebookAiConfig c,String id,boolean rebind){if(rejectOpen)return;NotebookAiWorkspace.Window existing=workspace.session(id);if(existing==null)workspace.add(\"OPENAI\",\"https://example.org/v1\",\"model\",id,rebind);else workspace.focus(existing);}",
            NotebookWorkspaceActivationTest.method(source,"void newMainFromShelf("),NotebookWorkspaceActivationTest.method(source,"void historyFromShelf("),
            NotebookWorkspaceActivationTest.method(source,"private void openHistorySelection("),NotebookWorkspaceActivationTest.method(source,"private void makeShelfMain("),
            NotebookWorkspaceActivationTest.method(source,"boolean consumeShelfMain("),NotebookWorkspaceActivationTest.method(source,"boolean retainsMainChat("),
            "static void check(boolean b,String s){if(!b)throw new AssertionError(s);}public static void run(){AiShelfLaunchFixture f=new AiShelfLaunchFixture();f.newMainFromShelf();check(f.workspace.windows().size()==1&&f.retainsMainChat(),\"new bookshelf chat becomes the main workspace\");check(f.listener.starts==1&&f.listener.ends==1&&f.listener.changes==1&&f.consumeShelfMain()&&!f.consumeShelfMain(),\"launch preserves the contact barrier and emits one hide-notes transition\");NotebookAiWorkspace.Window first=f.workspace.active();f.workspace.minimize(first);f.historyFromShelf();check(f.historyAsMain&&f.history!=null,\"history selection starts without creating a new conversation\");f.openHistorySelection(new NotebookAiConfig(),first.sessionId,false);check(f.workspace.active()==first&&!first.pane.collapsed&&f.retainsMainChat()&&f.consumeShelfMain(),\"choosing a saved chat restores its existing session as main\");AiShelfLaunchFixture failed=new AiShelfLaunchFixture();failed.independent=true;failed.rejectOpen=true;failed.newMainFromShelf();check(failed.listener.ends==1&&failed.listener.exits==1&&failed.workspace.windows().isEmpty(),\"failed launch cannot leave an empty AI screen behind Settings\");System.out.println(\"NotebookAiShelfLaunch: 5 assertions passed (new and saved main chats, failure and native contact ordering).\");}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.AiShelfLaunchFixture",fixture);
    }
}
