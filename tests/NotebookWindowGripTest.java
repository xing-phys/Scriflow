package org.eink.inkprobe.notebook;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Production corner gestures: immediate spatial drag, tap separation and outline-only resizing. */
public final class NotebookWindowGripTest {
    public static void main(String[] args) throws Exception {
        String companion=new String(Files.readAllBytes(new File("app/src/main/java/org/eink/inkprobe/notebook/NotebookWindowControls.java").toPath()),StandardCharsets.UTF_8);
        String fixture=String.join("\n",
            "package org.eink.inkprobe.notebook;import java.util.*;public class WindowGripFixture {",
            "static class View {interface OnTouchListener {boolean onTouch(View v,MotionEvent e);}int clicks;boolean performClick(){clicks++;return true;}}",
            "static class MotionEvent {static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;int action;float x,y;MotionEvent(int a,float x,float y){action=a;this.x=x;this.y=y;}int getActionMasked(){return action;}float getRawX(){return x;}float getRawY(){return y;}}",
            "static class ViewConfiguration {static ViewConfiguration get(Object c){return new ViewConfiguration();}int getScaledTouchSlop(){return 10;}}",
            "static class Root {int getWidth(){return 1404;}int getHeight(){return 1872;}}",
            "class Listener {boolean allowed=true;int starts,ends,saves;boolean beginMove(boolean resize){starts++;if(allowed&&resize&&editor)editorPrepared=false;return allowed;}void endMove(){ends++;}void save(){saves++;}void position(){positions++;}}",
            "static class Outline {int updates;boolean showing;NotebookWorkspace.Box box;void show(Root r,NotebookWorkspace.Box b){updates++;showing=true;box=b;}void hide(){showing=false;}}",
            "final Object context=new Object();final Root root=new Root();final Listener listener=new Listener();final Outline outline=new Outline();final NotebookWorkspace workspace=new NotebookWorkspace();final NotebookWorkspace.Pane pane;int positions;boolean editor,editorPrepared=true;",
            "WindowGripFixture(){workspace.add(1,NotebookWorkspace.Position.FLOATING);pane=workspace.add(2,NotebookWorkspace.Position.FLOATING);}void position(){positions++;}NotebookWorkspace.Box box(){return workspace.box(pane,1404,1872);}",
            NotebookWorkspaceActivationTest.method(companion,"private final class Grip"),
            "static void event(Grip g,View v,int a,float x,float y){g.onTouch(v,new MotionEvent(a,x,y));}static void check(boolean b,String s){if(!b)throw new AssertionError(s);}static boolean same(NotebookWorkspace.Box a,NotebookWorkspace.Box b){return a.x==b.x&&a.y==b.y&&a.width==b.width&&a.height==b.height;}",
            "public static void run(){",
            "WindowGripFixture f=new WindowGripFixture();Grip menu=f.new Grip(false);View icon=new View();NotebookWorkspace.Box initial=f.box();",
            "event(menu,icon,0,100,100);event(menu,icon,1,100,100);check(icon.clicks==1&&f.listener.starts==0,\"tap opens options without pausing writing\");",
            "event(menu,icon,0,100,100);event(menu,icon,2,105,104);event(menu,icon,1,105,104);check(icon.clicks==2&&f.listener.starts==0,\"finger jitter remains a tap\");",
            "event(menu,icon,0,100,100);event(menu,icon,2,200,160);NotebookWorkspace.Box moved=f.box();check(f.listener.starts==1&&Math.abs(moved.x-initial.x-100)<=1&&Math.abs(moved.y-initial.y-60)<=1,\"first movement starts dragging immediately without a timer\");event(menu,icon,1,220,180);check(Math.abs(f.box().x-initial.x-120)<=1&&f.listener.ends==1&&f.listener.saves==1&&icon.clicks==2,\"release commits final coordinates once without a menu click\");",
            "Grip resize=f.new Grip(true);View minimize=new View();NotebookWorkspace.Box before=f.box();int layouts=f.positions;f.editor=true;event(resize,minimize,0,100,100);event(resize,minimize,2,250,200);NotebookWorkspace.Box larger=f.box();check(Math.abs(larger.x-before.x)<=1&&Math.abs(larger.y-before.y)<=1&&Math.abs(larger.width-before.width-150)<=1&&Math.abs(larger.height-before.height-100)<=1,\"immediate resize keeps its top-left anchor\");check(f.positions==layouts&&f.outline.showing&&f.outline.updates==1&&!f.editorPrepared,\"resize updates the outline without reflowing note content\");event(resize,minimize,2,270,220);check(f.positions==layouts&&f.outline.updates==2,\"every move updates the boundary independently of fit-width rendering\");event(resize,minimize,1,270,220);check(minimize.clicks==0&&f.listener.ends==2&&f.outline.showing,\"release starts one layout and keeps the outline until presentation\");",
            "NotebookWorkspace.Box retained=f.box();event(resize,minimize,0,100,100);event(resize,minimize,2,200,200);event(resize,minimize,3,200,200);check(same(retained,f.box())&&f.listener.ends==3&&!f.outline.showing,\"cancel restores bounds and hides the outline\");",
            "event(resize,minimize,0,100,100);event(resize,minimize,1,100,100);check(minimize.clicks==1,\"right tap still minimizes\");",
            "f.listener.allowed=false;int starts=f.listener.starts;event(menu,icon,0,100,100);event(menu,icon,2,200,200);event(menu,icon,2,250,250);event(menu,icon,1,250,250);check(same(retained,f.box())&&f.listener.ends==3&&f.listener.starts==starts+1&&icon.clicks==2,\"busy native input rejects the entire drag without repeated pause or false click\");",
            "f.listener.allowed=true;event(resize,minimize,0,100,100);event(resize,minimize,2,200,200);int ends=f.listener.ends;resize.cancel();check(same(retained,f.box())&&!f.outline.showing&&f.listener.ends==ends,\"hide retires a pending resize without resuming a paused Activity\");",
            "f.workspace.place(f.pane,NotebookWorkspace.Position.RIGHT);NotebookWorkspace.Box dock=f.box();event(menu,icon,0,100,100);event(menu,icon,2,200,200);event(menu,icon,1,200,200);check(same(dock,f.box())&&icon.clicks==2,\"split-pane corner movement does not accidentally open options\");",
            "for(float left:new float[]{0,.4f,1})for(float top:new float[]{0,.5f,1})for(int delta:new int[]{-10000,-100,0,100,10000}){WindowGripFixture edge=new WindowGripFixture();edge.pane.x=left;edge.pane.y=top;NotebookWorkspace.Box start=edge.box();Grip corner=edge.new Grip(true);View handle=new View();event(corner,handle,0,100,100);event(corner,handle,2,100+delta,100+delta);NotebookWorkspace.Box resized=edge.box();check(Math.abs(start.x-resized.x)<=1&&Math.abs(start.y-resized.y)<=1,\"note and chat resize keep their opposite corner anchored even at limits\");check(resized.x>=0&&resized.y>=0&&resized.x+resized.width<=1404&&resized.y+resized.height<=1872,\"right and bottom edges stay within the workspace at every size limit\");event(corner,handle,1,100+delta,100+delta);}",
            "}","}");
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.WindowGripFixture",fixture);
        System.out.println("NotebookWindowGrip: 103 assertions passed (shared note/chat drag, anchored bounded resize, outline updates, tap and cancel).");
    }
}
