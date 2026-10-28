package org.eink.inkprobe.notebook;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Production corner-grip callbacks with a deterministic long-press clock. */
public final class NotebookWindowGripTest {
    public static void main(String[] args) throws Exception {
        String companion = new String(Files.readAllBytes(new File(
            "app/src/main/java/org/eink/inkprobe/notebook/NotebookCompanion.java").toPath()), StandardCharsets.UTF_8);
        String fixture = String.join("\n",
            "package org.eink.inkprobe.notebook;import java.util.*;",
            "public class WindowGripFixture {",
            "static class View {interface OnTouchListener {boolean onTouch(View v,MotionEvent e);}int clicks;boolean performClick(){clicks++;return true;}}",
            "static class MotionEvent {static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;int action;float x,y;MotionEvent(int a,float x,float y){action=a;this.x=x;this.y=y;}int getActionMasked(){return action;}float getRawX(){return x;}float getRawY(){return y;}}",
            "static class ViewConfiguration {static ViewConfiguration get(Object context){return new ViewConfiguration();}static int getLongPressTimeout(){return 500;}int getScaledTouchSlop(){return 10;}}",
            "static class Handler {Runnable pending;void postDelayed(Runnable r,long t){pending=r;}void removeCallbacks(Runnable r){if(pending==r)pending=null;}void fire(){if(pending!=null){Runnable r=pending;pending=null;r.run();}}}",
            "static class Root {int getWidth(){return 1404;}int getHeight(){return 1872;}}",
            "static class Listener {boolean allowed=true;int starts,ends,saves;boolean beginMove(){starts++;return allowed;}void endMove(){ends++;}void save(){saves++;}}",
            "final Object context=new Object();final Handler main=new Handler();final Root root=new Root();final Listener listener=new Listener();final NotebookWorkspace workspace=new NotebookWorkspace();final NotebookWorkspace.Pane pane;int positions;",
            "WindowGripFixture(){workspace.add(1,NotebookWorkspace.Position.FLOATING);pane=workspace.add(2,NotebookWorkspace.Position.FLOATING);}",
            "void position(){positions++;}NotebookWorkspace.Box box(){return workspace.box(pane,1404,1872);}",
            NotebookWorkspaceActivationTest.method(companion, "private final class Grip"),
            "static void event(Grip grip,View view,int action,float x,float y){grip.onTouch(view,new MotionEvent(action,x,y));}",
            "static void check(boolean b,String s){if(!b)throw new AssertionError(s);}static boolean same(NotebookWorkspace.Box a,NotebookWorkspace.Box b){return a.x==b.x&&a.y==b.y&&a.width==b.width&&a.height==b.height;}",
            "public static void run(){",
            "WindowGripFixture f=new WindowGripFixture();Grip menu=f.new Grip(false);View icon=new View();NotebookWorkspace.Box initial=f.box();",
            "event(menu,icon,0,100,100);event(menu,icon,1,100,100);f.main.fire();check(icon.clicks==1&&f.listener.starts==0,\"tap opens options without starting window move\");",
            "event(menu,icon,0,100,100);event(menu,icon,2,150,100);f.main.fire();event(menu,icon,1,150,100);check(same(initial,f.box())&&icon.clicks==1&&f.listener.starts==0,\"drag before hold threshold neither moves nor clicks\");",
            "event(menu,icon,0,100,100);f.main.fire();check(f.listener.starts==1,\"hold pauses native writing before movement\");event(menu,icon,2,200,160);event(menu,icon,1,200,160);NotebookWorkspace.Box moved=f.box();check(Math.abs(moved.x-initial.x-100)<=1&&Math.abs(moved.y-initial.y-60)<=1,\"held left grip moves window by screen delta\");check(f.listener.ends==1&&f.listener.saves==1&&icon.clicks==1,\"move persists and resumes once without opening menu\");",
            "Grip resize=f.new Grip(true);View minimize=new View();NotebookWorkspace.Box before=f.box();event(resize,minimize,0,100,100);f.main.fire();event(resize,minimize,2,250,200);NotebookWorkspace.Box larger=f.box();check(Math.abs(larger.x-before.x)<=1&&Math.abs(larger.y-before.y)<=1&&Math.abs(larger.width-before.width-150)<=1&&Math.abs(larger.height-before.height-100)<=1,\"held right grip resizes lower-right corner with top-left fixed\");event(resize,minimize,1,250,200);check(minimize.clicks==0&&f.listener.ends==2,\"resize release never minimizes\");",
            "NotebookWorkspace.Box retained=f.box();event(resize,minimize,0,100,100);f.main.fire();event(resize,minimize,2,200,200);event(resize,minimize,3,200,200);check(same(retained,f.box())&&f.listener.ends==3,\"cancel rolls back bounds and releases native pause\");",
            "event(resize,minimize,0,100,100);event(resize,minimize,1,100,100);check(minimize.clicks==1,\"short right tap minimizes\");",
            "f.listener.allowed=false;event(menu,icon,0,100,100);f.main.fire();event(menu,icon,2,200,200);event(menu,icon,1,200,200);check(same(retained,f.box())&&f.listener.ends==3,\"busy native writer rejects movement without a false resume\");",
            "event(menu,icon,0,100,100);menu.cancel();int starts=f.listener.starts;f.main.fire();check(f.listener.starts==starts,\"retiring window cancels pending hold callbacks\");",
            "}", "}");
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.WindowGripFixture", fixture);
        System.out.println("NotebookWindowGrip: 11 assertions passed (production hold, tap, move, resize and cancel; not device verification).");
    }
}
