package org.eink.inkprobe.notebook;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.tools.ToolProvider;

/** Executes production focus-switch callbacks against queued writer and surface lifecycles. */
public final class NotebookWorkspaceActivationTest {
    public static void main(String[] args) throws Exception {
        String activity = new String(Files.readAllBytes(new File(
            "app/src/main/java/org/eink/inkprobe/notebook/NotebookActivity.java").toPath()), StandardCharsets.UTF_8);
        String fixture = String.join("\n",
            "import java.io.*; import java.util.*; import org.eink.inkprobe.notebook.NotebookWorkspace;",
            "public class WorkspaceActivationFixture {",
            "static class NotebookPage {}",
            "static class NotebookViewport { enum Fit { WIDTH, PAGE, CUSTOM } float fittedZoom(Fit fit,int n){return 1;} int pageWidth(int n){return 200;} int pageHeight(int n){return 300;} }",
            "static class NotebookViewHistory { static class State {float x=100,y=150;} byte[] save(int id){return new byte[]{(byte)id};} void clear(){} void restore(int id,byte[] b){} }",
            "static class NotebookScrollStore { static class State {State(int page,double fraction){}} }",
            "static class NotebookCompanion { static class ReadingView {float zoom,centerX,centerY; ReadingView(float z,float x,float y){zoom=z;centerX=x;centerY=y;} } ReadingView readingView(){return new ReadingView(2,.3f,.4f);} void remember(int b,int p,ReadingView v){} void hide(){} }",
            "interface Callback {void complete(NotebookPage page,IOException error);}",
            "final ArrayDeque<Runnable> queue=new ArrayDeque<>(); final List<String> events=new ArrayList<>();",
            "final NotebookWorkspace workspace=new NotebookWorkspace(); final Session session=new Session();",
            "final NotebookCompanion companion=new NotebookCompanion(),activeWindow=new NotebookCompanion();",
            "final NotebookViewHistory viewHistory=new NotebookViewHistory(); final Map<Integer,byte[]> workspaceHistories=new HashMap<>();",
            "final Area workspaceArea=new Area(); NotebookViewport viewport=new NotebookViewport();",
            "NotebookViewport.Fit zoomFit=NotebookViewport.Fit.WIDTH; float zoom=1; boolean restoreCenter;",
            "NotebookCompanion.ReadingView pendingReadingView; NotebookScrollStore.State reopenPosition; Runnable whenReady;",
            "int activePage=3,generation=1,viewNotebookId=1,pendingReadingPage,editable=1,statusNote=1,openCount;",
            "boolean active=true,editing,changingWorkspace,ink=true; IOException shownError,failure;",
            "WorkspaceActivationFixture(){workspace.notebookId=2;workspace.page=7;}",
            "class Session {int id=1; int notebookId(){return id;} int pageCount(){return 10;} void openNotebook(int target,Callback callback){events.add(\"open\");queue.add(()->{if(failure==null)id=target;callback.complete(new NotebookPage(),failure);});}}",
            "class Area {void post(Runnable r){queue.add(r);}}",
            "boolean canEdit(){return active&&!editing&&ink&&shownError==null;}",
            "void pauseForEdit(){editing=true;events.add(\"pause\");} void persistPosition(){events.add(\"flush\");}",
            "NotebookViewHistory.State currentView(){return new NotebookViewHistory.State();}",
            "void disposeCanvas(){ink=false;editable=0;events.add(\"retire\");} void saveWorkspace(){events.add(\"persist\");}",
            "void showError(IOException error){shownError=error;} void updatePageLabel(){statusNote=session.id;} void layoutWorkspace(){events.add(\"layout\");}",
            "void openCanvas(NotebookPage page){openCount++;ink=true;editing=false;editable=session.id;viewport=new NotebookViewport();if(whenReady!=null){Runnable next=whenReady;whenReady=null;queue.add(next);}}",
            "void changeWorkspace(Runnable mutation){if(canEdit())mutation.run();}",
            method(activity, "private void activateCompanion("),
            method(activity, "private void collapseSecondary("),
            "void drain(){for(int i=0;!queue.isEmpty();i++){if(i>20)throw new AssertionError(\"callback loop\");queue.remove().run();}}",
            "static void check(boolean value,String message){if(!value)throw new AssertionError(message);}",
            "public static void run(){",
            "WorkspaceActivationFixture f=new WorkspaceActivationFixture(); f.activateCompanion(null);",
            "check(f.editable==0&&f.session.id==1&&!f.workspace.secondaryActive,\"old ink retires before writer handoff\");",
            "check(f.events.equals(Arrays.asList(\"pause\",\"flush\",\"retire\",\"open\")),\"completed ink is flushed before writer focus changes\");",
            "f.queue.remove().run(); check(f.session.id==2&&f.editable==0&&f.workspace.secondaryActive&&f.statusNote==2,\"status and placement change only after writer success, before enabling ink\");",
            "check(f.pendingReadingPage==7&&f.pendingReadingView.zoom==2&&f.pendingReadingView.centerX==.3f,\"activation carries independent reading navigation\");",
            "f.drain();check(f.editable==2&&f.openCount==1&&f.workspace.primaryNotebook(2)==1,\"exactly one editor opens at the same note position\");",
            "f.activateCompanion(null);f.drain();check(f.editable==1&&!f.workspace.secondaryActive&&f.workspace.secondaryNotebook(1)==2,\"activation returns to the first note without exchanging panes\");",
            "WorkspaceActivationFixture failed=new WorkspaceActivationFixture();failed.failure=new IOException(\"save failed\");failed.activateCompanion(null);failed.drain();",
            "check(failed.shownError!=null&&failed.editable==0&&failed.session.id==1&&!failed.workspace.secondaryActive,\"failed save cannot activate another note\");",
            "WorkspaceActivationFixture paused=new WorkspaceActivationFixture();paused.activateCompanion(null);paused.active=false;paused.generation++;paused.drain();",
            "check(paused.openCount==0&&paused.editable==0&&paused.workspace.secondaryActive&&paused.workspace.notebookId==1,\"background completion persists identity but never starts ink\");",
            "WorkspaceActivationFixture duringLayout=new WorkspaceActivationFixture();duringLayout.activateCompanion(null);duringLayout.queue.remove().run();duringLayout.generation++;duringLayout.drain();",
            "check(duringLayout.openCount==0,\"stale layout continuation cannot resurrect an old editor\");",
            "WorkspaceActivationFixture collapsed=new WorkspaceActivationFixture();collapsed.activateCompanion(null);collapsed.drain();collapsed.collapseSecondary(false);collapsed.drain();",
            "check(collapsed.session.id==1&&collapsed.workspace.collapsed&&!collapsed.workspace.secondaryActive&&collapsed.editable==1,\"collapsing the active second pane first activates the primary note\");",
            "WorkspaceActivationFixture closed=new WorkspaceActivationFixture();closed.activateCompanion(null);closed.drain();closed.collapseSecondary(true);closed.drain();",
            "check(closed.session.id==1&&closed.workspace.notebookId==0&&closed.editable==1,\"closing the active second pane keeps the primary editable\");",
            "WorkspaceActivationFixture busy=new WorkspaceActivationFixture();busy.editing=true;busy.activateCompanion(null);check(busy.queue.isEmpty(),\"an active edit prevents a competing focus switch\");",
            "}", "}");
        File directory = Files.createTempDirectory("workspace-activation-").toFile();
        try {
            File source = new File(directory, "WorkspaceActivationFixture.java");
            Files.write(source.toPath(), fixture.getBytes(StandardCharsets.UTF_8));
            int compiled = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-source", "8", "-target", "8",
                "-classpath", System.getProperty("java.class.path"), "-d", directory.getAbsolutePath(), source.getAbsolutePath());
            if (compiled != 0) throw new AssertionError("Could not compile production activation callback fixture");
            try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{directory.toURI().toURL()})) {
                try { loader.loadClass("WorkspaceActivationFixture").getMethod("run").invoke(null); }
                catch (InvocationTargetException error) { throw new AssertionError("Production activation regression", error.getCause()); }
            }
            System.out.println("NotebookWorkspaceActivation: 12 assertions passed (production callbacks, queued writer and surface lifecycle; not device verification).");
        } finally { NotebookBackup.deleteTree(directory); }
    }
    private static String method(String source, String signature) {
        int start = source.indexOf(signature), open = source.indexOf('{', start), depth = 0;
        if (start < 0 || open < 0) throw new AssertionError("Missing method: " + signature);
        for (int i = open; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            else if (source.charAt(i) == '}' && --depth == 0) return source.substring(start, i + 1);
        }
        throw new AssertionError("Incomplete method: " + signature);
    }
}
