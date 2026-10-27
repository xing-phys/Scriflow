package org.eink.inkprobe.notebook;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.tools.ToolProvider;

/** Exercise the production Activity completion/read callbacks with a queued writer/native boundary. */
public final class NotebookEditRestoreTest {
    public static void main(String[] args) throws Exception {
        String activity = new String(Files.readAllBytes(new File(
            "app/src/main/java/org/eink/inkprobe/notebook/NotebookActivity.java").toPath()), StandardCharsets.UTF_8);
        String fixture = String.join("\n",
            "import java.io.*; import java.util.*;",
            "public class EditRestoreFixture {",
            "static class NotebookPage {}",
            "static class Viewport {}",
            "static class Drag { boolean isActive(){return false;} }",
            "interface Callback { void done(NotebookPage page, IOException error); }",
            "final ArrayDeque<Runnable> queue=new ArrayDeque<>();",
            "final Map<Integer,NotebookPage> pages=new HashMap<>();",
            "final Drag selectionDrag=new Drag(); final Viewport viewport=new Viewport();",
            "final Session session=new Session(); int generation=1, reads, installs; long viewportRevision=10;",
            "boolean active=true, editing=true, scrolling, dragging, fetching, settleRead=true, selectionBackgroundDirty;",
            "boolean inkReady, pdfReady=true; IOException shownError;",
            "class Session { void window(List<Integer> wanted, Callback callback) { reads++; queue.add(()->callback.done(new NotebookPage(),null)); } }",
            "List<Integer> wantedPages(){return Arrays.asList(1,2);}",
            "void mergeWindow(){for(int id:wantedPages())pages.put(id,new NotebookPage());}",
            "void showError(IOException error){shownError=error;}",
            "void scheduleSelectionFrame(){} void schedulePreview(){}",
            "void installViewport(){if(editing||dragging)return; if(settleRead){requestWindow();return;} if(!pdfReady)return; installs++; inkReady=true; scrolling=false;}",
            method(activity,"private void requestWindow("),
            method(activity,"private void finishEdit("),
            "void drain(){for(int i=0;!queue.isEmpty();i++){if(i>10)throw new AssertionError(\"read loop\");queue.remove().run();}}",
            "static void check(boolean value,String message){if(!value)throw new AssertionError(message);}",
            "public static void run(){",
            "EditRestoreFixture cross=new EditRestoreFixture(); cross.finishEdit(1,new NotebookPage(),null);",
            "check(!cross.inkReady&&cross.fetching,\"cross-page edit waits for its asynchronous window\"); cross.drain();",
            "check(cross.inkReady&&cross.installs==1&&!cross.editing&&!cross.scrolling&&!cross.fetching,\"page-window completion must resume native input\");",
            "EditRestoreFixture same=new EditRestoreFixture(); same.settleRead=false; same.finishEdit(1,new NotebookPage(),null);",
            "check(same.inkReady&&same.installs==1&&same.reads==0,\"same-page edit restores immediately\");",
            "EditRestoreFixture raced=new EditRestoreFixture(); raced.requestWindow(); raced.viewportRevision++; raced.finishEdit(1,new NotebookPage(),null); raced.drain();",
            "check(raced.inkReady&&raced.installs==1&&raced.reads==2,\"an older in-flight window is followed by the current one and one restore\");",
            "EditRestoreFixture pdf=new EditRestoreFixture(); pdf.pdfReady=false; pdf.finishEdit(1,new NotebookPage(),null); pdf.drain();",
            "check(!pdf.inkReady&&pdf.scrolling,\"PDF preparation keeps restoration pending\"); pdf.pdfReady=true; pdf.installViewport();",
            "check(pdf.inkReady&&pdf.installs==1,\"PDF-ready callback can resume the edited viewport\");",
            "EditRestoreFixture stale=new EditRestoreFixture(); stale.finishEdit(0,new NotebookPage(),null);",
            "check(stale.editing&&stale.reads==0&&stale.installs==0,\"stale completion cannot resume input\");",
            "EditRestoreFixture paused=new EditRestoreFixture(); paused.active=false; paused.finishEdit(1,new NotebookPage(),null);",
            "check(paused.editing&&paused.reads==0&&paused.installs==0,\"background completion cannot resume input\");",
            "EditRestoreFixture failed=new EditRestoreFixture(); failed.finishEdit(1,new NotebookPage(),new IOException(\"save failed\"));",
            "check(failed.shownError!=null&&!failed.inkReady&&failed.editing&&failed.reads==0,\"save failure preserves the paused editor\");",
            "}", "}");
        File directory = Files.createTempDirectory("edit-restore-contract-").toFile();
        try {
            File source = new File(directory,"EditRestoreFixture.java");
            Files.write(source.toPath(), fixture.getBytes(StandardCharsets.UTF_8));
            int compiled = ToolProvider.getSystemJavaCompiler().run(null,null,null,"-source","8","-target","8",
                "-d",directory.getAbsolutePath(),source.getAbsolutePath());
            if(compiled!=0)throw new AssertionError("Could not compile production Activity callback fixture");
            try(URLClassLoader loader=new URLClassLoader(new java.net.URL[]{directory.toURI().toURL()})) {
                try { loader.loadClass("EditRestoreFixture").getMethod("run").invoke(null); }
                catch(InvocationTargetException error){throw new AssertionError("Production edit-restoration regression",error.getCause());}
            }
            System.out.println("NotebookEditRestore: 9 assertions passed (production Activity callbacks, queued read/native boundary).");
        } finally { NotebookBackup.deleteTree(directory); }
    }
    private static String method(String source,String signature) {
        int start=source.indexOf(signature), open=source.indexOf('{',start), depth=0;
        if(start<0||open<0)throw new AssertionError("Missing production callback: "+signature);
        for(int i=open;i<source.length();i++) {
            if(source.charAt(i)=='{')depth++;
            else if(source.charAt(i)=='}'&&--depth==0)return source.substring(start,i+1);
        }
        throw new AssertionError("Incomplete production callback: "+signature);
    }
}
