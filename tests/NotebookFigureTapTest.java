package org.eink.inkprobe.notebook;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Actual Activity selection callbacks with an editable-note boundary and a fake editor. */
public final class NotebookFigureTapTest {
    public static void main(String[] args)throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("app/src/main/java/org/eink/inkprobe/notebook/NotebookActivity.java")),StandardCharsets.UTF_8);
        String fixture=String.join("\n",
            "package org.eink.inkprobe.notebook;import java.util.*;import java.io.*;public class FigureTapFixture {",
            "final Map<Integer,NotebookPage> pages=new LinkedHashMap<>();NotebookViewport viewport=new NotebookViewport(2,200,300,100,180,12);float scrollY;int activePage=2,pauses,selectedPage,selectedIndex=-1;boolean ready=true;Object figureEditor;IOException shownError;",
            "FigureTapFixture()throws Exception{byte[] png=NotebookFigureFixture.png(true);NotebookFigure a=new NotebookFigure(UUID.randomUUID(),20,20,100,100,png),b=new NotebookFigure(UUID.randomUUID(),40,40,40,40,png);pages.put(1,NotebookPage.empty().withSize(200,300).withFigures(Arrays.asList(a,b)));pages.put(2,NotebookPage.empty().withSize(200,300));}",
            "boolean canEdit(){return ready;}void pauseForEdit(boolean keepHistory,boolean keepPaper){if(keepHistory||!keepPaper)throw new AssertionError(\"selection must retain the clear paper\");pauses++;ready=false;}void updatePageLabel(){}void editFigureOnPage(NotebookPage p,int index,int number){selectedPage=number;selectedIndex=index;}",
            NotebookWorkspaceActivationTest.method(source,"private boolean selectFigureAt("),
            NotebookWorkspaceActivationTest.method(source,"private void editTappedFigure("),
            "static void check(boolean b,String s){if(!b)throw new AssertionError(s);}public static void run()throws Exception{",
            "FigureTapFixture f=new FigureTapFixture();check(f.selectFigureAt(30,30)&&f.pauses==1&&f.selectedIndex==1,\"tap chooses the topmost image directly\");check(f.selectedPage==1&&f.activePage==1&&f.pages.get(1).figures.size()==2,\"partial-page image editing uses the tapped page rather than the centered page\");",
            "FigureTapFixture blank=new FigureTapFixture();check(!blank.selectFigureAt(90,90)&&blank.pauses==0,\"blank tap leaves writing ready\");blank.ready=false;check(!blank.selectFigureAt(30,30)&&blank.pauses==0,\"busy native input rejects selection\");",
            "FigureTapFixture stale=new FigureTapFixture();stale.editTappedFigure(new NotebookFigureHit.Hit(1,UUID.randomUUID(),stale.pages.get(1).figures.get(0).id));check(stale.pauses==0&&stale.selectedIndex==-1,\"stale page identity cannot edit the wrong page\");stale.editTappedFigure(new NotebookFigureHit.Hit(1,stale.pages.get(1).id,UUID.randomUUID()));check(stale.pauses==0,\"removed figure identity cannot edit another image\");stale.figureEditor=new Object();check(stale.selectFigureAt(30,30)&&stale.pauses==0,\"a pending editor does not start another edit\");",
            "}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.FigureTapFixture",fixture);
        System.out.println("NotebookFigureTap: 7 assertions passed (direct selection, correct page, busy and stale identities).");
    }
}
