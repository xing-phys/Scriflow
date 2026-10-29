package org.eink.inkprobe.notebook;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Counter updates must not move the native ink boundary while page digits change. */
public final class NotebookStatusControlsTest {
    public static void main(String[] args) throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "app/src/main/java/org/eink/inkprobe/notebook/NotebookActivity.java")), StandardCharsets.UTF_8);
        String fixture = String.join("\n",
            "package org.eink.inkprobe.notebook;public class StatusControlsFixture {",
            "static class Paint {float measureText(String s){return s.length()*7f;}}",
            "static class Button {String text,description;int minimum;Button(String s){text=s;}String getText(){return text;}void setText(String s){text=s;}void setContentDescription(String s){description=s;}void setMinWidth(int n){minimum=n;}Paint getPaint(){return new Paint();}int width(){return Math.max(minimum,text.length()*7+8);}}",
            "static class Session {String name=\"Notebook\";int count=123;String notebookName(){return name;}int pageCount(){return count;}}",
            "static class Islands {int refreshes;void refresh(){refreshes++;}}",
            "final Session session=new Session();final Button noteNameButton=new Button(\"Notebook\"),pageLabel=new Button(\"1 / 1\"),zoomValue=new Button(\"100%\");Islands statusIslands=new Islands();int activePage=1,statusPageCount;String zoom=\"100%\";String zoomLabel(){return zoom;}int dp(int n){return n;}",
            NotebookWorkspaceActivationTest.method(source, "private void updatePageLabel("),
            "static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}public static void run(){",
            "StatusControlsFixture f=new StatusControlsFixture();f.updatePageLabel();int width=f.pageLabel.width();check(f.pageLabel.text.equals(\"1 / 123\")&&f.statusIslands.refreshes==1,\"initial counter installs compact reserved digits\");",
            "for(int page:new int[]{2,9,10,99,100,123}){f.activePage=page;f.updatePageLabel();check(f.pageLabel.width()==width,\"writing on a page with more digits retains the same native exclusion width\");}",
            "int refreshes=f.statusIslands.refreshes;f.updatePageLabel();check(f.statusIslands.refreshes==refreshes,\"idle status polling does not resize or refresh unchanged windows\");",
            "f.zoom=\"75%\";f.updatePageLabel();check(f.zoomValue.text.equals(\"75%\")&&f.pageLabel.width()==width&&f.zoomValue.description.contains(\"page fit\"),\"zoom has its own value and action description\");",
            "f.session.name=\"Another note\";f.session.count=1000;f.activePage=1;f.updatePageLabel();check(f.noteNameButton.text.equals(\"Another note\")&&f.noteNameButton.description.contains(\"bookshelf\")&&f.pageLabel.description.equals(\"Page 1 of 1000; manage pages\"),\"focus change updates the three independent controls\");check(f.pageLabel.width()>width,\"more notebook digits are reserved after a page-count change\");",
            "f.statusIslands=null;f.updatePageLabel();check(f.noteNameButton.text.equals(\"Another note\"),\"labels can initialize before a status host is attached\");",
            "System.out.println(\"NotebookStatusControls: \"+checks+\" assertions passed (stable counter bounds and independent status values).\");",
            "}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.StatusControlsFixture", fixture);
    }
}
