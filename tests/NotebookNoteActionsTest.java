package org.eink.inkprobe.notebook;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Production action expansion and dispatch, with a queued native-ready boundary. */
public final class NotebookNoteActionsTest {
    public static void main(String[] args) throws Exception {
        String status = source("NotebookStatusIslands"), activity = source("NotebookActivity");
        String fixture = String.join("\n",
            "package org.eink.inkprobe.notebook;import java.util.*;public class NoteActionsFixture {",
            "static class View {static final int VISIBLE=0,GONE=8;}static class Node {int visibility;String description,tooltip;int getVisibility(){return visibility;}void setVisibility(int n){visibility=n;}void setContentDescription(String s){description=s;}void setTooltipText(String s){tooltip=s;}}",
            "static class NotebookIconDrawable {enum Icon {PAPER_BLANK,PAPER_LINED}}static class NotebookIconButton extends Node {NotebookIconDrawable.Icon icon;void setIcon(NotebookIconDrawable.Icon i){icon=i;}}",
            "static class NotebookStatusIslands {",
            method(status,"enum Action"),
            "final List<String> events;boolean zenMode;final Node actions=new Node(),more=new Node();final EnumMap<Action,NotebookIconButton> actionButtons=new EnumMap<>(Action.class);NotebookStatusIslands(List<String> e){events=e;actions.visibility=View.GONE;for(Action a:Action.values())actionButtons.put(a,new NotebookIconButton());}void position(){events.add(\"bounds\");}",
            method(status,"void showActions("),method(status,"boolean actionsShowing("),
            method(status,"boolean collapseActions("),method(status,"private int visibleActionCount("), "}",
            "static class NotebookPage {enum Template {BLANK,LINED}int pdfPage;Template template=Template.BLANK;}",
            "class Session {Object clipboard;Object figureClipboard(){return clipboard;}}",
            "class Ink {boolean ready=true,down,fail;void pauseForEdit(){events.add(\"pause\");ready=false;if(fail)shownError=new Object();}void redrawAfterOverlayChange(){if(ready||down)throw new AssertionError(\"bounds repair requires a paused writer\");events.add(\"repair\");}void start(){events.add(\"start\");ready=true;}}",
            "final List<String> events=new ArrayList<>();final Map<Integer,NotebookPage> pages=new HashMap<>();final Session session=new Session();final Ink ink=new Ink();NotebookStatusIslands statusIslands=new NotebookStatusIslands(events);int activePage=1;boolean active=true,editing;Object shownError;Runnable pending;NoteActionsFixture(){pages.put(1,new NotebookPage());}boolean canEdit(){return active&&!editing&&shownError==null&&ink.ready&&!ink.down;}void pauseForEdit(){editing=true;ink.pauseForEdit();if(statusIslands.collapseActions()&&shownError==null)ink.redrawAfterOverlayChange();}void resumeThen(Runnable r){pending=r;}void ready(){editing=false;ink.ready=true;Runnable r=pending;pending=null;if(r!=null)r.run();}void insertFigure(){events.add(\"insert\");}void insertMarkdown(){events.add(\"markdown\");}void readingOptions(){events.add(\"reading\");}void chooseFigure(){events.add(\"figures\");}void pageInformation(){events.add(\"information\");}void choosePaper(){events.add(\"background\");}void pasteFigure(){events.add(\"paste\");}",
            method(activity,"private void noteActions("),method(activity,"private void performNoteAction("),
            method(activity,"private boolean collapseNoteActions("),
            "static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}public static void run(){",
            "NoteActionsFixture f=new NoteActionsFixture();f.noteActions();check(f.statusIslands.actionsShowing()&&f.statusIslands.more.visibility==View.GONE,\"function icons replace the three dots in the same status host\");check(f.events.equals(Arrays.asList(\"pause\",\"bounds\",\"repair\",\"start\"))&&!f.editing&&f.pending==null,\"expansion repairs native ink without a modal dialog or viewport change\");check(f.statusIslands.visibleActionCount()==6,\"ordinary pages offer all actions except unavailable paste\");",
            "NotebookIconButton background=f.statusIslands.actionButtons.get(NotebookStatusIslands.Action.BACKGROUND);check(background.icon==NotebookIconDrawable.Icon.PAPER_LINED&&background.tooltip.equals(\"Use lined background\"),\"background icon identifies the action and retains its text alternative\");check(f.collapseNoteActions()&&!f.statusIslands.actionsShowing()&&f.statusIslands.more.visibility==View.VISIBLE,\"outside interaction restores the three dots\");int count=f.events.size();check(!f.collapseNoteActions()&&count==f.events.size(),\"already collapsed bars do not interrupt native writing\");",
            "f.pages.get(1).template=NotebookPage.Template.LINED;f.session.clipboard=new Object();f.noteActions();check(f.statusIslands.visibleActionCount()==7&&background.icon==NotebookIconDrawable.Icon.PAPER_BLANK&&background.description.equals(\"Use blank background\"),\"lined pages and copied figures update their icons on expansion\");f.collapseNoteActions();f.pages.get(1).pdfPage=1;f.noteActions();check(f.statusIslands.visibleActionCount()==6&&background.visibility==View.GONE,\"PDF pages never offer a replacement background\");",
            "for(NotebookStatusIslands.Action a:NotebookStatusIslands.Action.values()){NoteActionsFixture n=new NoteActionsFixture();n.session.clipboard=new Object();n.noteActions();n.performNoteAction(a);check(!n.statusIslands.actionsShowing()&&n.pending!=null&&n.events.get(n.events.size()-1).equals(\"repair\"),\"action collapses the bar before waiting for native readiness\");n.ready();String expected=new String[]{\"insert\",\"markdown\",\"reading\",\"figures\",\"information\",\"background\",\"paste\"}[a.ordinal()];check(n.events.get(n.events.size()-1).equals(expected),\"each icon dispatches its named function independent of conditional icons\");}",
            "NoteActionsFixture busy=new NoteActionsFixture();busy.ink.down=true;busy.noteActions();check(busy.events.isEmpty()&&!busy.statusIslands.actionsShowing(),\"a live stroke cannot expand or reclip the bar\");busy.ink.down=false;busy.noteActions();busy.ink.down=true;int size=busy.events.size();check(!busy.collapseNoteActions()&&busy.events.size()==size,\"collapse waits until writing finishes\");busy.ink.down=false;busy.active=false;check(!busy.collapseNoteActions(),\"retired activity callbacks cannot change the native clip\");NoteActionsFixture fail=new NoteActionsFixture();fail.ink.fail=true;fail.noteActions();check(!fail.statusIslands.actionsShowing()&&!fail.events.contains(\"start\"),\"native pause failure cannot partly open the bar\");NoteActionsFixture zen=new NoteActionsFixture();zen.statusIslands.zenMode=true;zen.statusIslands.showActions(true,false,true);check(!zen.statusIslands.actionsShowing(),\"Zen cannot reopen hidden note controls\");",
            "System.out.println(\"NotebookNoteActions: \"+checks+\" assertions passed (inline expansion, contextual icons, native repair and dispatch).\");}", "}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.NoteActionsFixture",fixture);
    }
    private static String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("app/src/main/java/org/eink/inkprobe/notebook/"+name+".java")),StandardCharsets.UTF_8);
    }
    private static String method(String source,String signature) { return NotebookWorkspaceActivationTest.method(source,signature); }
}
