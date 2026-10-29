package org.eink.inkprobe.notebook;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Production import continuation/result callbacks with synthetic Activity/provider boundaries. */
public final class NotebookFigureImportFlowTest {
    public static void main(String[] args) throws Exception {
        String source = new String(Files.readAllBytes(Paths.get("app/src/main/java/org/eink/inkprobe/notebook/NotebookMediaActivity.java")), StandardCharsets.UTF_8);
        String fixture = String.join("\n",
            "package org.eink.inkprobe.notebook;public class FigureImportFixture {",
            "static final int PICK=2510,RESULT_OK=1;static class Root{boolean attached=true;boolean isAttachedToWindow(){return attached;}}static class Intent{Object uri;Intent(Object u){uri=u;}Object getData(){return uri;}}",
            "static class Job{boolean importing=true,complete,busy,pickerStarted,failed;int inserts,saves;void insert(Object uri){inserts++;busy=true;}void save(Object uri){saves++;}}",
            "final Root root=new Root();final Job job=new Job();boolean resumed,awaiting,finishing,destroyed;int launches,finishes;boolean isFinishing(){return finishing;}boolean isDestroyed(){return destroyed;}void finish(){finishing=true;finishes++;}void overridePendingTransition(int a,int b){}void pick(boolean importing){launches++;awaiting=true;job.pickerStarted=true;}",
            NotebookWorkspaceActivationTest.method(source,"private void advance("),
            NotebookWorkspaceActivationTest.method(source,"protected void onActivityResult(").replace("super.onActivityResult(request, result, data);", ""),
            "static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}public static void run(){",
            "FigureImportFixture f=new FigureImportFixture();f.advance();check(f.launches==0,\"picker waits for resumed activity\");f.resumed=true;f.root.attached=false;f.advance();check(f.launches==0,\"picker waits for attached window\");f.root.attached=true;f.advance();f.advance();check(f.launches==1&&f.awaiting,\"initial entry opens files once without a Choose image click\");",
            "f.onActivityResult(99,RESULT_OK,new Intent(new Object()));check(f.job.inserts==0&&f.awaiting,\"unrelated results cannot start insertion\");f.onActivityResult(PICK,RESULT_OK,new Intent(new Object()));f.advance();check(f.job.inserts==1&&f.job.busy&&f.finishes==0,\"successful selection waits for insertion\");f.job.busy=false;f.job.complete=true;f.advance();f.advance();check(f.finishes==1,\"successful insertion returns directly to the note once\");",
            "FigureImportFixture restored=new FigureImportFixture();restored.resumed=true;restored.awaiting=true;restored.job.pickerStarted=true;restored.advance();check(restored.launches==0,\"recreated picker is not launched twice\");restored.job.complete=true;restored.awaiting=false;restored.advance();check(restored.finishes==1,\"retained completed import returns without a second picker\");",
            "FigureImportFixture cancel=new FigureImportFixture();cancel.onActivityResult(PICK,0,null);check(cancel.finishes==1&&cancel.job.inserts==0,\"cancel returns directly without an intermediate page\");FigureImportFixture failed=new FigureImportFixture();failed.resumed=true;failed.job.failed=true;failed.advance();check(failed.launches==0&&failed.finishes==0,\"failure remains available for explicit retry\");FigureImportFixture export=new FigureImportFixture();export.resumed=true;export.job.importing=false;export.advance();check(export.launches==0&&export.finishes==0,\"ink export retains its explicit format workflow\");export.onActivityResult(PICK,RESULT_OK,new Intent(new Object()));check(export.job.saves==1,\"export result still writes its chosen destination\");",
            "System.out.println(\"NotebookFigureImportFlow: \"+checks+\" assertions passed (direct picker, return, recreation and cancellation).\");}", "}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.FigureImportFixture", fixture);
    }
}
