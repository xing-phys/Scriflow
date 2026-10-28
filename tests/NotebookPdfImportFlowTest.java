package org.eink.inkprobe.notebook;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Real picker/job phases and production Activity callbacks across result, resume, and recreation. */
public final class NotebookPdfImportFlowTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        phases(); activityCallbacks(); workerFailures(); publicationFailure();
        System.out.println("NotebookPdfImportFlow: " + assertions + " assertions passed (phase retention, production picker/result/open callbacks; not system-picker/device verification).");
    }
    private static void phases() {
        NotebookPdfImportFlow flow = new NotebookPdfImportFlow();
        check(flow.phase() == NotebookPdfImportFlow.Phase.CHOOSE, "New import automatically requests picker");
        check(flow.beginPicker() && !flow.beginPicker(), "Picker is launched once");
        NotebookPdfImportFlow restored = new NotebookPdfImportFlow(); restored.restore(flow.phase().name(), 0);
        check(restored.phase() == NotebookPdfImportFlow.Phase.PICKING && !restored.beginPicker(), "Outstanding picker survives recreation and process-style restore");
        check(flow.beginImport() && !flow.beginImport() && flow.busy(), "One picker result starts one import");
        check(!flow.beginPicker() && !flow.beginOpen(), "Busy job cannot launch a second picker or open early");
        restored.restore(flow.phase().name(), 0);
        check(restored.phase() == NotebookPdfImportFlow.Phase.ERROR && !restored.busy(), "Saved state alone cannot resume a dead import worker");
        flow.imported(17);
        check(flow.complete() && flow.notebookId() == 17 && !flow.busy(), "Committed imported notebook is ready to open");
        check(flow.beginOpen() && !flow.beginOpen(), "Completion navigates exactly once");
        restored.restore("READY", 17);
        check(restored.beginOpen() && restored.notebookId() == 17, "Ready publication survives recreation before handoff");
        restored.restore("READY", 0);
        check(restored.phase() == NotebookPdfImportFlow.Phase.ERROR && !restored.beginOpen(), "Invalid completion cannot open an unrelated note");
        restored.restore("PICKING", 0); restored.cancelPicker();
        check(restored.phase() == NotebookPdfImportFlow.Phase.CANCELLED && !restored.beginImport(), "Cancelled picker never imports");
        restored.restore("CHOOSE", 0); restored.beginPicker(); restored.failed();
        check(restored.phase() == NotebookPdfImportFlow.Phase.ERROR && restored.beginPicker(), "A picker/provider failure can be retried explicitly");
        restored.restore("unknown", 0);
        check(restored.phase() == NotebookPdfImportFlow.Phase.ERROR, "Damaged phase is recoverable without automatic repeat");
    }
    private static void activityCallbacks() throws Exception {
        String source = new String(Files.readAllBytes(new File(
            "app/src/main/java/org/eink/inkprobe/notebook/NotebookPdfImportActivity.java").toPath()), StandardCharsets.UTF_8);
        String fixture = String.join("\n",
            "package org.eink.inkprobe.notebook;import java.util.*;",
            "class ImportActivityBase {void onActivityResult(int request,int result,PdfImportActivityFixture.Intent data){}}",
            "public class PdfImportActivityFixture extends ImportActivityBase {",
            "static final int PICK_PDF=2402,RESULT_OK=1;",
            "static class Uri {} static class NotebookActivity {} static class PickerUnavailable extends RuntimeException {}",
            "static class Intent {static final String ACTION_OPEN_DOCUMENT=\"open\",CATEGORY_OPENABLE=\"openable\";String type;Uri uri;Intent(String action){}Intent(Object owner,Class<?> type){}Intent addCategory(String s){return this;}Intent setType(String s){type=s;return this;}Uri getData(){return uri;}}",
            "static class View {static final int VISIBLE=0,GONE=8;}",
            "static class Control {int visibility;String text;void setText(String text){this.text=text;}void setVisibility(int n){visibility=n;}}",
            "class Root {boolean attached=true;final ArrayDeque<Runnable> queue=new ArrayDeque<>();boolean isAttachedToWindow(){return attached;}void post(Runnable r){queue.add(r);}void removeCallbacks(Runnable r){queue.removeIf(value->value==r);}void drain(){for(int i=0;!queue.isEmpty();i++){if(i>20)throw new AssertionError(\"UI callback loop\");queue.remove().run();}}}",
            "static class Job {final NotebookPdfImportFlow flow=new NotebookPdfImportFlow();String message=\"Opening PDF picker…\";int imports;void importPdf(Uri uri){if(flow.beginImport())imports++;}}",
            "final Root root=new Root();final Control choose=new Control(),done=new Control(),status=new Control();Job job=new Job();boolean resumed=true,finishing,destroyed,noPicker;int picks,opens,finishes;Intent pickerIntent;final Runnable nextStep=this::advance;",
            "boolean isFinishing(){return finishing;}boolean isDestroyed(){return destroyed;}void startActivityForResult(Intent intent,int request){if(noPicker)throw new PickerUnavailable();picks++;pickerIntent=intent;}void startActivity(Intent intent){opens++;}void finish(){finishing=true;finishes++;}void overridePendingTransition(int a,int b){}",
            method(source,"private void advance("),method(source,"private void pick("),
            method(source,"protected void onActivityResult("),method(source,"private void render("),
            "static Intent selected(){Intent result=new Intent(\"result\");result.uri=new Uri();return result;}",
            "static void check(boolean b,String s){if(!b)throw new AssertionError(s);}",
            "public static void run(){",
            "PdfImportActivityFixture f=new PdfImportActivityFixture();f.render();f.root.drain();check(f.picks==1&&f.choose.visibility==View.GONE&&\"application/pdf\".equals(f.pickerIntent.type),\"resumed import opens files directly without a Choose PDF page\");f.advance();check(f.picks==1,\"multiple resume callbacks do not open duplicate pickers\");",
            "f.onActivityResult(PICK_PDF,RESULT_OK,selected());f.onActivityResult(PICK_PDF,RESULT_OK,selected());check(f.job.imports==1&&f.job.flow.busy(),\"duplicate picker results cannot publish twice\");f.job.flow.imported(23);f.render();f.root.drain();check(f.opens==1&&f.finishes==1,\"successful import opens PDF note and removes controller\");f.advance();check(f.opens==1,\"queued navigation cannot open again after finishing\");",
            "PdfImportActivityFixture rotating=new PdfImportActivityFixture();rotating.advance();rotating.resumed=false;PdfImportActivityFixture rotated=new PdfImportActivityFixture();rotated.job=rotating.job;rotated.advance();check(rotated.picks==0&&rotated.job.flow.phase()==NotebookPdfImportFlow.Phase.PICKING,\"landscape recreation keeps existing picker ownership\");rotated.resumed=false;rotated.onActivityResult(PICK_PDF,RESULT_OK,selected());rotated.job.flow.imported(24);rotated.render();rotated.root.drain();check(rotated.opens==0,\"background picker/import completion never launches a window\");rotated.resumed=true;rotated.advance();rotating.advance();check(rotated.opens==1&&rotating.opens==0,\"only resumed replacement opens the completed import\");",
            "PdfImportActivityFixture resultFirst=new PdfImportActivityFixture();resultFirst.advance();resultFirst.resumed=false;resultFirst.onActivityResult(PICK_PDF,RESULT_OK,selected());check(resultFirst.job.imports==1&&resultFirst.job.flow.busy(),\"PDF selected before onResume starts worker once\");PdfImportActivityFixture afterResultRotation=new PdfImportActivityFixture();afterResultRotation.job=resultFirst.job;afterResultRotation.resumed=false;resultFirst.destroyed=true;resultFirst.job.flow.imported(25);resultFirst.render();resultFirst.root.drain();afterResultRotation.render();afterResultRotation.root.drain();check(resultFirst.opens==0&&afterResultRotation.opens==0,\"completion during post-result configuration recreation never opens from inactive instances\");afterResultRotation.resumed=true;afterResultRotation.advance();check(afterResultRotation.opens==1&&afterResultRotation.picks==0&&afterResultRotation.job.imports==1,\"replacement instance opens selected PDF after recreation without reimporting\");",
            "PdfImportActivityFixture cancelled=new PdfImportActivityFixture();cancelled.advance();cancelled.onActivityResult(PICK_PDF,0,null);check(cancelled.finishing&&cancelled.job.imports==0,\"cancel returns to library without an intermediary screen\");",
            "PdfImportActivityFixture detached=new PdfImportActivityFixture();detached.root.attached=false;detached.advance();check(detached.picks==0,\"unattached Activity cannot launch picker\");detached.root.attached=true;detached.destroyed=true;detached.advance();check(detached.picks==0,\"destroyed Activity cannot launch picker\");",
            "PdfImportActivityFixture failed=new PdfImportActivityFixture();failed.noPicker=true;failed.advance();failed.root.drain();check(failed.job.flow.phase()==NotebookPdfImportFlow.Phase.ERROR&&failed.choose.visibility==View.VISIBLE&&failed.picks==0,\"missing file provider shows an explicit retry without an automatic loop\");",
            "}","}");
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.PdfImportActivityFixture", fixture);
        assertions += 15;
    }
    private static void workerFailures() throws Exception {
        String source = read("NotebookPdfImport");
        String fixture = String.join("\n",
            "package org.eink.inkprobe.notebook;import java.io.*;import java.nio.file.*;import java.util.*;",
            "public class PdfImportWorkerFixture {",
            "static class Uri {}",
            "static class Queue {final ArrayDeque<Runnable> queue=new ArrayDeque<>();boolean reject;void execute(Runnable r){if(reject)throw new OutOfMemoryError(\"dispatch allocation\");queue.add(r);}void post(Runnable r){queue.add(r);}void drain(){while(!queue.isEmpty())queue.remove().run();}}",
            "class Context {boolean fail;File getFilesDir(){if(fail)throw new OutOfMemoryError(\"stage setup\");return directory;}}",
            "final Context context=new Context();final Queue files=new Queue(),main=new Queue();final NotebookPdfImportFlow flow=new NotebookPdfImportFlow();final File directory;String message=\"\";Runnable listener;Throwable injected;int prepared;",
            "PdfImportWorkerFixture()throws Exception{directory=Files.createTempDirectory(\"pdf-worker-\").toFile();File book=new File(directory,\"notebook\");book.mkdir();Files.write(new File(book,\"page.nbk\").toPath(),new byte[]{1,2,3});flow.beginPicker();}",
            "void preparePdf(Uri uri,File staging)throws IOException{prepared++;staging.mkdir();Files.write(new File(staging,\"partial\").toPath(),new byte[]{9});if(injected instanceof OutOfMemoryError)throw (OutOfMemoryError)injected;if(injected instanceof LinkageError)throw (LinkageError)injected;if(injected instanceof RuntimeException)throw (RuntimeException)injected;if(injected instanceof IOException)throw (IOException)injected;}",
            method(source,"void importPdf("),method(source,"private void runImport("),method(source,"private void discardStaging("),method(source,"private void failed("),method(source,"private void changed("),method(source,"void observe("),method(source,"void detach("),
            "boolean originalSafe()throws Exception{return Arrays.equals(Files.readAllBytes(new File(directory,\"notebook/page.nbk\").toPath()),new byte[]{1,2,3});}int stages(){File[] stages=directory.listFiles((dir,name)->name.startsWith(\"pdf-stage-\"));return stages==null?0:stages.length;}void cleanup(){NotebookBackup.deleteTree(directory);}static void check(boolean b,String s){if(!b)throw new AssertionError(s);}",
            "public static void run()throws Exception{",
            "PdfImportWorkerFixture memory=new PdfImportWorkerFixture();try{memory.injected=new OutOfMemoryError(\"parser allocation\");int[] old={0},current={0};memory.observe(()->old[0]++);memory.importPdf(new Uri());memory.detach();memory.files.drain();memory.observe(()->current[0]++);memory.main.drain();check(memory.flow.phase()==NotebookPdfImportFlow.Phase.ERROR&&!memory.flow.busy()&&memory.message.contains(\"more memory\"),\"worker OOM becomes a recoverable main-thread import error\");check(memory.prepared==1&&memory.stages()==0&&memory.originalSafe(),\"failed private stage is removed without touching saved notebooks\");check(old[0]==1&&current[0]>1,\"failure after selection notifies only the replacement Activity observer\");}finally{memory.cleanup();}",
            "PdfImportWorkerFixture setup=new PdfImportWorkerFixture();try{setup.context.fail=true;setup.importPdf(new Uri());setup.files.drain();setup.main.drain();check(setup.prepared==0&&setup.flow.phase()==NotebookPdfImportFlow.Phase.ERROR&&setup.originalSafe(),\"stage allocation failure is inside worker guard\");}finally{setup.cleanup();}",
            "PdfImportWorkerFixture dispatch=new PdfImportWorkerFixture();try{dispatch.files.reject=true;dispatch.importPdf(new Uri());dispatch.main.drain();check(dispatch.flow.phase()==NotebookPdfImportFlow.Phase.ERROR&&dispatch.message.contains(\"more memory\")&&dispatch.prepared==0,\"executor startup allocation failure cannot escape onActivityResult\");}finally{dispatch.cleanup();}",
            "PdfImportWorkerFixture denied=new PdfImportWorkerFixture();try{denied.injected=new SecurityException(\"provider permission denied\");denied.importPdf(new Uri());denied.files.drain();denied.main.drain();check(denied.flow.phase()==NotebookPdfImportFlow.Phase.ERROR&&denied.message.contains(\"permission denied\")&&denied.stages()==0,\"URI/provider failure remains a recoverable error\");}finally{denied.cleanup();}",
            "PdfImportWorkerFixture fatal=new PdfImportWorkerFixture();try{fatal.injected=new LinkageError(\"unrelated VM failure\");fatal.importPdf(new Uri());boolean propagated=false;try{fatal.files.drain();}catch(LinkageError expected){propagated=true;}check(propagated,\"non-memory fatal VM errors are not swallowed\");}finally{fatal.cleanup();}",
            "}","}");
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.PdfImportWorkerFixture",fixture);
        assertions += 7;
    }
    private static void publicationFailure() throws Exception {
        String source = read("NotebookSession");
        String fixture=String.join("\n",
            "package org.eink.inkprobe.notebook;import java.io.*;import java.util.*;",
            "public class PdfImportPublicationFixture {",
            "interface Operation {void run()throws IOException;}interface Callback {void complete(Object page,IOException error);}interface TransferCallback {void complete(IOException error);}",
            "static class Document {boolean recovery=true;boolean fatal;void loadCatalog(){}void importNotebook(NotebookBackup.Restored restored){if(fatal)throw new LinkageError();throw new OutOfMemoryError(\"publication allocation\");}void load(){}}",
            "final Document document=new Document();List<Integer> windowIds=Arrays.asList(1);int navigationBook=1;",
            "void submit(Operation operation,Callback callback){try{operation.run();callback.complete(null,null);}catch(IOException error){callback.complete(null,error);}}",
            method(source,"void importBackup("),
            "public static void run(){PdfImportPublicationFixture f=new PdfImportPublicationFixture();IOException[] result={null};f.importBackup(null,error->result[0]=error);if(result[0]==null||!(result[0].getCause() instanceof OutOfMemoryError)||!f.document.recovery)throw new AssertionError(\"publication OOM must return error and preserve recovery ownership\");f.document.fatal=true;boolean propagated=false;try{f.importBackup(null,error->{});}catch(LinkageError expected){propagated=true;}if(!propagated)throw new AssertionError(\"non-memory fatal publication failure swallowed\");}","}");
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.PdfImportPublicationFixture",fixture);
        assertions += 2;
    }
    private static String read(String name)throws Exception{return new String(Files.readAllBytes(new File("app/src/main/java/org/eink/inkprobe/notebook/"+name+".java").toPath()),StandardCharsets.UTF_8);}
    private static String method(String source,String signature){return NotebookWorkspaceActivationTest.method(source,signature).replace("android.content.ActivityNotFoundException", "PickerUnavailable");}
    private static void check(boolean b,String message){assertions++;if(!b)throw new AssertionError(message);}
}
