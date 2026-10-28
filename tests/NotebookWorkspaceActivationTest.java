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
            "static class NotebookViewHistory { byte[] save(int id){return new byte[]{(byte)id};} void clear(){} void restore(int id,byte[] b){} }",
            "static class NotebookScrollStore { static class State {State(int page,double fraction){}} }",
            "static class NotebookViewport {enum Fit {WIDTH}}",
            "static class NotebookCompanion { static class ReadingView {float zoom,centerX,centerY; ReadingView(float z,float x,float y){zoom=z;centerX=x;centerY=y;} } final NotebookWorkspace.Pane pane; NotebookCompanion(NotebookWorkspace.Pane p){pane=p;} ReadingView readingView(){return pane.hasView?new ReadingView(pane.zoom,pane.centerX,pane.centerY):null;} void hide(){} }",
            "interface Callback {void complete(NotebookPage page,IOException error);}",
            "final ArrayDeque<Runnable> queue=new ArrayDeque<>(); final List<String> events=new ArrayList<>();",
            "final NotebookWorkspace workspace=new NotebookWorkspace(); final Session session=new Session();",
            "final Map<NotebookWorkspace.Pane,NotebookCompanion> noteWindows=new LinkedHashMap<>();",
            "final NotebookViewHistory viewHistory=new NotebookViewHistory(); final Map<Integer,byte[]> workspaceHistories=new HashMap<>();",
            "final Area workspaceArea=new Area(); NotebookViewport viewport=new NotebookViewport();",
            "NotebookViewport.Fit zoomFit=NotebookViewport.Fit.WIDTH; float zoom=1; boolean restoreCenter;",
            "NotebookCompanion.ReadingView pendingReadingView; NotebookScrollStore.State reopenPosition; Runnable whenReady;",
            "int activePage=3,generation=1,viewNotebookId=1,pendingReadingPage,editable=1,statusNote=1,openCount;",
            "boolean active=true,editing,changingWorkspace,ink=true; IOException shownError,failure;",
            "WorkspaceActivationFixture(){workspace.add(1,NotebookWorkspace.Position.FLOATING); NotebookWorkspace.Pane p=workspace.add(2,NotebookWorkspace.Position.RIGHT);p.page=7;p.hasView=true;p.zoom=2;p.centerX=.3f;p.centerY=.4f;workspace.add(3,NotebookWorkspace.Position.FLOATING);workspace.add(4,NotebookWorkspace.Position.FLOATING);for(NotebookWorkspace.Pane pane:workspace.panes())noteWindows.put(pane,new NotebookCompanion(pane));}",
            "class Session {int id=1; int notebookId(){return id;} int pageCount(){return 10;} void openNotebook(int target,Callback callback){events.add(\"open\");queue.add(()->{if(failure==null)id=target;callback.complete(new NotebookPage(),failure);});}}",
            "class Area {void post(Runnable r){queue.add(r);}}",
            "boolean canEdit(){return active&&!editing&&ink&&shownError==null;}",
            "void pauseForEdit(){editing=true;events.add(\"pause\");} void persistPosition(){events.add(\"flush\");}",
            "void rememberEditor(){NotebookWorkspace.Pane p=workspace.find(session.id);p.page=activePage;p.hasView=true;p.zoom=1.5f;p.centerX=.6f;p.centerY=.7f;events.add(\"remember\");}",
            "void disposeCanvas(){ink=false;editable=0;events.add(\"retire\");} void saveWorkspace(){events.add(\"persist\");}",
            "void showError(IOException error){shownError=error;} void updatePageLabel(){statusNote=session.id;} void layoutWorkspace(){events.add(\"layout\");}",
            "void openCanvas(NotebookPage page){openCount++;ink=true;editing=false;editable=session.id;viewport=new NotebookViewport();if(whenReady!=null){Runnable next=whenReady;whenReady=null;queue.add(next);}}",
            "void changeWorkspace(Runnable mutation){if(canEdit())mutation.run();}",
            method(activity, "private void activatePane("), method(activity, "private void collapsePane("),
            "void focus(int id){activatePane(workspace.find(id),null);}",
            "void drain(){for(int i=0;!queue.isEmpty();i++){if(i>20)throw new AssertionError(\"callback loop\");queue.remove().run();}}",
            "static void check(boolean value,String message){if(!value)throw new AssertionError(message);}",
            "public static void run(){",
            "WorkspaceActivationFixture f=new WorkspaceActivationFixture(); NotebookWorkspace.Box before=f.workspace.box(f.workspace.find(2),1404,1872);f.focus(2);",
            "check(f.editable==0&&f.session.id==1,\"old ink retires before writer handoff\");",
            "check(f.events.equals(Arrays.asList(\"pause\",\"flush\",\"remember\",\"retire\",\"open\")),\"completed ink and view are saved before writer focus changes\");",
            "f.queue.remove().run(); check(f.session.id==2&&f.editable==0&&f.statusNote==2,\"status changes only after writer success and before enabling ink\");",
            "check(f.pendingReadingPage==7&&f.pendingReadingView.zoom==2&&f.pendingReadingView.centerX==.3f,\"activation carries independent continuous reading position\");",
            "f.drain();check(f.editable==2&&f.openCount==1&&f.workspace.base().notebookId==1,\"exactly one editor opens without exchanging identities\");",
            "NotebookWorkspace.Box after=f.workspace.box(f.workspace.find(2),1404,1872);check(before.x==after.x&&before.width==after.width,\"focus keeps pane placement\");",
            "f.focus(3);f.drain();f.focus(4);f.drain();check(f.editable==4&&f.workspace.panes().size()==4,\"focus switches among several floating notes\");",
            "f.focus(1);f.drain();check(f.editable==1&&f.pendingReadingView.zoom==1.5f&&f.workspace.find(2).page==7,\"returning focus preserves each note's own page and zoom\");",
            "WorkspaceActivationFixture failed=new WorkspaceActivationFixture();failed.failure=new IOException(\"save failed\");failed.focus(2);failed.drain();",
            "check(failed.shownError!=null&&failed.editable==0&&failed.session.id==1&&failed.workspace.base().notebookId==1,\"failed save cannot activate another note\");",
            "WorkspaceActivationFixture paused=new WorkspaceActivationFixture();paused.focus(2);paused.active=false;paused.generation++;paused.drain();",
            "check(paused.openCount==0&&paused.editable==0&&paused.session.id==2,\"background completion never starts ink\");",
            "WorkspaceActivationFixture duringLayout=new WorkspaceActivationFixture();duringLayout.focus(2);duringLayout.queue.remove().run();duringLayout.generation++;duringLayout.drain();",
            "check(duringLayout.openCount==0,\"stale layout continuation cannot resurrect an old editor\");",
            "WorkspaceActivationFixture collapsed=new WorkspaceActivationFixture();collapsed.focus(3);collapsed.drain();collapsed.collapsePane(collapsed.workspace.find(3),false);collapsed.drain();",
            "check(collapsed.session.id==1&&collapsed.workspace.find(3).collapsed&&collapsed.editable==1,\"minimizing active floating note hands off to visible base first\");",
            "WorkspaceActivationFixture closed=new WorkspaceActivationFixture();closed.focus(2);closed.drain();closed.collapsePane(closed.workspace.find(2),true);closed.drain();",
            "check(closed.session.id==1&&closed.workspace.find(2)==null&&closed.workspace.find(3)!=null&&closed.editable==1,\"closing active split keeps other floating notes and editor\");",
            "WorkspaceActivationFixture busy=new WorkspaceActivationFixture();busy.editing=true;busy.focus(2);check(busy.queue.isEmpty(),\"an active edit prevents a competing focus switch\");",
            "WorkspaceActivationFixture hidden=new WorkspaceActivationFixture();hidden.workspace.minimize(hidden.workspace.find(2));hidden.focus(2);check(hidden.queue.isEmpty(),\"hidden pane cannot capture editing focus\");",
            "WorkspaceActivationFixture inactive=new WorkspaceActivationFixture();inactive.collapsePane(inactive.workspace.find(3),false);check(inactive.editable==1&&inactive.workspace.find(3).collapsed,\"minimizing inactive note leaves writer ownership unchanged\");",
            "}", "}");
        runFixture("WorkspaceActivationFixture", fixture);
        System.out.println("NotebookWorkspaceActivation: 16 assertions passed (production callbacks, queued writer and surface lifecycle; not device verification).");
    }
    static void runFixture(String name, String fixture) throws Exception {
        File directory = Files.createTempDirectory("workspace-regression-").toFile();
        try {
            File source = new File(directory, name.substring(name.lastIndexOf('.') + 1) + ".java"); Files.write(source.toPath(), fixture.getBytes(StandardCharsets.UTF_8));
            int compiled = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-source", "8", "-target", "8",
                "-classpath", System.getProperty("java.class.path"), "-d", directory.getAbsolutePath(), source.getAbsolutePath());
            if (compiled != 0) throw new AssertionError("Could not compile production callback fixture");
            java.util.List<java.net.URL> urls = new java.util.ArrayList<>(); urls.add(directory.toURI().toURL());
            for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) urls.add(new File(entry).toURI().toURL());
            // Production model classes and fixture share a loader, preserving package-private access.
            try (URLClassLoader loader = new URLClassLoader(urls.toArray(new java.net.URL[0]), ClassLoader.getSystemClassLoader().getParent())) {
                try { loader.loadClass(name).getMethod("run").invoke(null); }
                catch (InvocationTargetException error) { throw new AssertionError("Production regression", error.getCause()); }
            }
        } finally { NotebookBackup.deleteTree(directory); }
    }
    static String method(String source, String signature) {
        int start = source.indexOf(signature), open = source.indexOf('{', start), depth = 0;
        if (start < 0 || open < 0) throw new AssertionError("Missing method: " + signature);
        for (int i = open; i < source.length(); i++) {
            if (source.charAt(i) == '{') depth++;
            else if (source.charAt(i) == '}' && --depth == 0) return source.substring(start, i + 1).replace("@Override ", "");
        }
        throw new AssertionError("Incomplete method: " + signature);
    }
}
