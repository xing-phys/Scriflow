package org.eink.inkprobe.notebook;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Production history visibility callbacks, including native pause/repair and delayed completions. */
public final class NotebookWritingHistoryTest {
    public static void main(String[] args) throws Exception {
        String activity = new String(Files.readAllBytes(Paths.get(
            "app/src/main/java/org/eink/inkprobe/notebook/NotebookActivity.java")), StandardCharsets.UTF_8);
        String fixture = String.join("\n",
            "package org.eink.inkprobe.notebook;import java.util.*;import java.io.*;public class WritingHistoryFixture {",
            "static class NotebookInk {enum Tool {PEN,ERASER,LASSO}}static class SystemClock {static long uptimeMillis(){return 10;}}",
            "class Session {boolean undo=true,redo;IOException error;IOException error(){return error;}boolean canUndo(int p){return undo;}boolean canRedo(int p){return redo;}}",
            "class Islands {boolean undo,redo;boolean historyMatches(boolean u,boolean r){return undo==u&&redo==r;}void history(boolean u,boolean r){undo=u;redo=r;events.add(\"history\");}}",
            "class Ink {boolean down,ready=true,failPause;boolean isPenDown(){return down;}void setSelectionMode(boolean b){}void pauseForEdit(){events.add(\"pause\");ready=false;if(failPause){shownError=new IOException(\"native failed\");updateControls();}}void redrawAfterOverlayChange(){if(ready||down)throw new AssertionError(\"repair requires a paused native writer\");events.add(\"repair\");}void start(){events.add(\"start\");main.post(()->{ready=true;updateControls();});}}",
            "class Main {final ArrayDeque<Runnable> queue=new ArrayDeque<>();void post(Runnable r){queue.add(r);}void removeCallbacks(Runnable r){}void drain(){for(int i=0;!queue.isEmpty();i++){if(i>50)throw new AssertionError(\"reentrant enable loop\");queue.remove().run();}}}",
            "static class Popup {void dismiss(){}void hide(){}}static class Tap {void cancel(){}}static class Shortcut {boolean hasPending(){return false;}}",
            "final List<String> events=new ArrayList<>();final Session session=new Session();final Islands statusIslands=new Islands();final Main main=new Main();final Ink ink=new Ink();final Tap linkTap=new Tap();final Shortcut stylusShortcut=new Shortcut();final Runnable updateSaveState=()->{},applyStylusShortcut=()->{};Popup widthPopup,selectionTools;Object figureEditor,selectionOutline;void dismissFigureEditor(){figureEditor=null;}boolean writingHistory,updatingHistory,zenMode,active=true,editing,dragging,scrolling,fetching,changingWorkspace,penDown,widthContact;int activePage=1,surfaceOwner=1,inkGeneration=1,writingRevision;long lastInputAt;IOException shownError;NotebookInk.Tool tool=NotebookInk.Tool.PEN;",
            "boolean canEdit(){return active&&!editing&&!dragging&&!scrolling&&!fetching&&!changingWorkspace&&shownError==null&&session.error()==null&&ink.ready&&!ink.down;}void updateControls(){updateHistoryControls();}void setWritingContact(boolean down){}void scheduleSaveState(){}void pen(boolean down){ink.down=down;onPenStateChanged(down);}",
            NotebookWorkspaceActivationTest.method(activity, "private void updateHistoryControls("),
            NotebookWorkspaceActivationTest.method(activity, "private void hideHistoryControls("),
            NotebookWorkspaceActivationTest.method(activity, "private void pauseForEdit()"),
            NotebookWorkspaceActivationTest.method(activity, "private void pauseForEdit(boolean"),
            NotebookWorkspaceActivationTest.method(activity, "public void onPenStateChanged("),
            "static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}public static void run(){",
            "WritingHistoryFixture f=new WritingHistoryFixture();f.updateControls();check(!f.statusIslands.undo&&!f.statusIslands.redo&&f.events.isEmpty(),\"opening an existing undoable note does not show history\");f.pen(true);f.updateControls();check(f.writingHistory&&f.events.isEmpty(),\"pen DOWN arms history without changing clipping mid-stroke\");f.pen(false);f.main.drain();check(f.statusIslands.undo&&!f.statusIslands.redo,\"writing reveals only the available undo action\");check(f.events.equals(Arrays.asList(\"pause\",\"history\",\"repair\",\"start\")),\"visibility updates pause, change bounds, repair exposed ink and resume once\");",
            "int size=f.events.size();f.updateControls();check(f.events.size()==size,\"unchanged availability does not touch the native writer\");f.pauseForEdit(true);f.session.undo=false;f.session.redo=true;f.editing=false;f.ink.ready=true;f.updateControls();f.main.drain();check(!f.statusIslands.undo&&f.statusIslands.redo&&f.writingHistory,\"Undo can reveal Redo without requiring another stroke\");",
            "f.pauseForEdit();check(!f.writingHistory&&!f.statusIslands.undo&&!f.statusIslands.redo,\"navigation or resize hides history while the writer is paused\");f.editing=false;f.ink.ready=true;f.session.undo=true;f.updateControls();f.main.drain();check(!f.statusIslands.undo&&!f.statusIslands.redo,\"late writer and layout completions cannot reopen dismissed history\");",
            "f.pen(true);f.pen(false);f.main.drain();check(f.statusIslands.undo&&f.statusIslands.redo,\"a new writing contact restores each available action\");f.pen(true);size=f.events.size();f.hideHistoryControls();check(!f.writingHistory&&f.events.size()==size,\"a hide request never interrupts an active native stroke\");f.pen(false);f.main.drain();check(!f.statusIslands.undo&&!f.statusIslands.redo,\"deferred removal happens safely after pen UP\");",
            "f.tool=NotebookInk.Tool.LASSO;f.pen(true);f.pen(false);f.main.drain();check(!f.writingHistory&&!f.statusIslands.undo&&!f.statusIslands.redo,\"lasso does not count as resumed writing\");f.tool=NotebookInk.Tool.ERASER;f.pen(true);f.pen(false);f.main.drain();check(!f.writingHistory,\"erasing does not reopen writing history\");",
            "WritingHistoryFixture empty=new WritingHistoryFixture();empty.session.undo=empty.session.redo=false;empty.pen(true);empty.pen(false);empty.main.drain();check(empty.events.isEmpty(),\"no available history creates no icon or blank space\");empty.session.undo=true;empty.updateControls();empty.main.drain();check(empty.statusIslands.undo,\"asynchronous stroke save publishes newly available undo\");empty.hideHistoryControls();empty.main.drain();empty.updateControls();check(!empty.statusIslands.undo,\"a reader interaction hides history until writing resumes\");",
            "WritingHistoryFixture failure=new WritingHistoryFixture();failure.writingHistory=true;failure.ink.failPause=true;failure.updateControls();check(!failure.writingHistory&&!failure.statusIslands.undo&&!failure.statusIslands.redo&&!failure.events.contains(\"repair\"),\"pause failure clears controls without repair or reentrant enable\");",
            "System.out.println(\"NotebookWritingHistory: \"+checks+\" assertions passed (writing-only history, deferred clipping, repair and stale callbacks).\");",
            "}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.WritingHistoryFixture", fixture);
    }
}
