package org.eink.inkprobe.notebook;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Execute the production native input barriers rather than assuming PopupWindow consumes native ink. */
public final class NotebookAiWindowTest {
    public static void main(String[] args) throws Exception {
        String activity=read("NotebookActivity"),toggle=method(activity,"private void toggleAiChat(");
        String fixture=String.join("\n",
            "package org.eink.inkprobe.notebook;import java.io.*;import java.util.*;public class AiWindowFixture {",
            "static class Rect {int id;Rect(){}Rect(int id){this.id=id;}Rect(Rect r){id=r.id;}void set(Rect r){id=r.id;}public boolean equals(Object r){return r instanceof Rect&&id==((Rect)r).id;}}",
            "static class SystemClock {static long uptimeMillis(){return 12;}}static class NotebookPage{}",
            "static class NotebookFloatingTools {enum Action{AI_CHAT}final Button b=new Button();Button button(Action a){return b;}}static class Button {boolean selected=true;void setToolSelected(boolean b){selected=b;}}",
            "final List<String> events=new ArrayList<>();class Ink {void pauseForEdit(){events.add(\"pause\");}void redrawAfterOverlayChange(){events.add(\"redraw\");}void start(){events.add(\"start\");}}",
            "interface Result{void ready(NotebookPage p,IOException e);}class Session{Result pending;void load(Result r){pending=r;}void finish(IOException e){Result r=pending;pending=null;r.ready(new NotebookPage(),e);}}",
            "boolean active=true,editing,aiContact,aiMoved,restoreAiChat=true,pauseFails;int generation=1,merges,restores;long lastInputAt;IOException shownError;Object aiChat=new Object();Ink ink=new Ink();final Session session=new Session();final Rect aiChatBounds=new Rect();final NotebookFloatingTools floatingTools=new NotebookFloatingTools();",
            "boolean canEdit(){return active&&!editing&&shownError==null&&ink!=null;}void pauseForEdit(){editing=true;ink.pauseForEdit();if(pauseFails)shownError=new IOException(\"native pause failed\");}void updateOverlayBounds(){events.add(\"bounds\");}void mergeWindow(){merges++;}void installViewport(){restores++;events.add(\"restore\");}void showError(IOException e){shownError=e;}",
            method(activity,"private void finishAiContact("),
            "class Listener {",method(toggle,"public boolean contactStart("),method(toggle,"public void contactEnd("),method(toggle,"public void boundsChanged(Rect"),method(toggle,"public void closed("),"}",
            "static int assertions;static void check(boolean b,String s){assertions++;if(!b)throw new AssertionError(s);}public static void run()throws Exception{",
            "AiWindowFixture tap=new AiWindowFixture();Listener listener=tap.new Listener();check(listener.contactStart()&&tap.aiContact&&tap.editing&&tap.events.equals(Arrays.asList(\"pause\")),\"chat DOWN pauses native input synchronously before child dispatch\");listener.contactEnd();check(!tap.aiContact&&tap.editing&&tap.session.pending!=null&&!tap.events.contains(\"start\"),\"UP waits for writer barrier\");tap.session.finish(null);check(!tap.editing&&tap.merges==1&&tap.restores==0&&tap.events.equals(Arrays.asList(\"pause\",\"start\")),\"ordinary tap resumes without rebuilding paper\");",
            "AiWindowFixture move=new AiWindowFixture();Listener drag=move.new Listener();drag.contactStart();drag.boundsChanged(new Rect(5));check(move.aiMoved&&move.aiChatBounds.id==5&&move.events.equals(Arrays.asList(\"pause\",\"bounds\")),\"drag updates native exclusion while handwriting remains paused\");drag.contactEnd();move.session.finish(null);check(move.restores==1&&move.events.get(2).equals(\"restore\"),\"drag restores the newly uncovered paper once after writer barrier\");",
            "AiWindowFixture reflow=new AiWindowFixture();Listener layout=reflow.new Listener();layout.boundsChanged(new Rect(8));check(reflow.events.equals(Arrays.asList(\"pause\",\"bounds\",\"redraw\",\"start\")),\"keyboard/layout reflow pauses before changing exclusions and restores afterward\");int size=reflow.events.size();layout.boundsChanged(new Rect(8));check(reflow.events.size()==size,\"unchanged layout does not flash paper\");",
            "AiWindowFixture stale=new AiWindowFixture();Listener retired=stale.new Listener();retired.contactStart();retired.contactEnd();stale.active=false;stale.session.finish(null);check(stale.merges==0&&stale.events.equals(Arrays.asList(\"pause\")),\"late writer callback cannot resume a paused Activity\");",
            "AiWindowFixture failed=new AiWindowFixture();failed.pauseFails=true;check(!failed.new Listener().contactStart()&&!failed.aiContact&&failed.shownError!=null,\"native pause failure blocks chat contacts\");",
            "AiWindowFixture writer=new AiWindowFixture();Listener broken=writer.new Listener();broken.contactStart();broken.contactEnd();writer.session.finish(new IOException(\"disk full\"));check(writer.shownError!=null&&!writer.events.contains(\"start\"),\"writer failure cannot restart native input\");",
            "layout.closed();check(reflow.aiChat==null&&!reflow.restoreAiChat&&!reflow.floatingTools.b.selected,\"user close clears tool selection and automatic reopen\");",
            "AiWindowFixture background=new AiWindowFixture();background.active=false;background.new Listener().closed();check(background.restoreAiChat,\"Activity pause preserves reopen intent\");",
            "System.out.println(\"NotebookAiWindow: \"+assertions+\" assertions passed (production contact, writer and native clip callbacks).\");}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.AiWindowFixture",fixture);
        String chat=read("NotebookAiChat");
        String modal=String.join("\n",
            "package org.eink.inkprobe.notebook;public class AiModalFixture {boolean contact=true;Dialog clearDialog;final Listener listener=new Listener();static class Dialog{boolean showing=true;boolean isShowing(){return showing;}}static class Listener{int ends;void contactEnd(){ends++;}}",
            method(chat,"private void finishContact("),
            "public static void run(){AiModalFixture f=new AiModalFixture();f.clearDialog=new Dialog();f.finishContact();if(!f.contact||f.listener.ends!=0)throw new AssertionError(\"confirmation retains the native input barrier\");f.clearDialog=null;f.finishContact();f.finishContact();if(f.contact||f.listener.ends!=1)throw new AssertionError(\"dismiss resumes the writer exactly once\");System.out.println(\"NotebookAiModal: 2 assertions passed (production confirmation contact barrier).\");}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.AiModalFixture",modal);
    }
    private static String read(String name) throws Exception { return new String(Files.readAllBytes(Paths.get("app/src/main/java/org/eink/inkprobe/notebook/"+name+".java")),StandardCharsets.UTF_8); }
    private static String method(String source,String signature) { return NotebookWorkspaceActivationTest.method(source,signature); }
}
