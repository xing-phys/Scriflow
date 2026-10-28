package org.eink.inkprobe.notebook;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Hardware shortcut policy, foreground receiver, real Activity switching and timed static hint. */
public final class NotebookStylusShortcutTest {
    private static int assertions;
    public static void main(String[] args)throws Exception {
        policy(); activity(); receiver(); hint();
        System.out.println("NotebookStylusShortcut: "+assertions+" assertions passed (production policy/receiver/tool/hint callbacks; hardware delivery checked separately).");
    }
    private static void policy(){
        NotebookStylusShortcut s=new NotebookStylusShortcut();
        check(s.request(1000,true)&&s.take(1000,true)==NotebookStylusShortcut.Target.ERASER,"Pen switches to eraser");
        check(!s.request(1100,false)&&s.take(1100,true)==null,"Duplicate hardware delivery is ignored");
        check(s.request(1300,false)&&s.take(1300,true)==NotebookStylusShortcut.Target.PEN,"Eraser/lasso switches to pen");
        check(s.request(1600,true)&&s.take(1600,false)==null,"Busy native stroke defers switching");
        check(s.take(1900,true)==NotebookStylusShortcut.Target.ERASER,"Deferred gesture applies after native input settles");
        check(s.request(2200,true)&&s.request(2500,true)&&s.take(2500,true)==NotebookStylusShortcut.Target.PEN,"Several deferred gestures preserve toggle parity");
        s.request(3000,true);check(s.take(5001,true)==null,"A stale delayed gesture expires");
        s.request(6000,false);check(s.request(9000,false)&&s.take(9000,true)==NotebookStylusShortcut.Target.PEN,"New gesture uses current tool after old request expires");
        s.request(10000,true);s.discard();check(s.take(10000,true)==null,"Explicit tool selection cancels a queued gesture");
        s.request(11000,true);s.reset();check(s.take(11000,true)==null&&s.request(11001,false),"Pause clears pending gesture and duplicate history");
        s.reset();check(!s.request(-1,true)&&s.take(0,true)==null,"Invalid gesture clock never switches tools");
    }
    private static void activity()throws Exception{
        String a=read("NotebookActivity");
        String f=String.join("\n",
            "package org.eink.inkprobe.notebook;public class StylusActivityFixture {",
            "static class NotebookInk {enum Tool {PEN,ERASER,LASSO}}static class Clock {static long now=1000;static long uptimeMillis(){return now;}}",
            "static class Session {Exception problem;Exception error(){return problem;}}static class Hint {int shown;boolean pen;void show(Object root,boolean p){shown++;pen=p;}}static class Width {int dismissed;void dismiss(){dismissed++;}}",
            "final NotebookStylusShortcut stylusShortcut=new NotebookStylusShortcut();final Session session=new Session();final Hint toolHint=new Hint();final Object workspaceRoot=new Object();Object ink=new Object(),overview,viewMenu,lassoMenu,outlineMenu,outlineChild,externalLinkMenu,figureEditor;Width widthPopup;Object shownError;boolean active=true,finishing,destroyed,changingWorkspace,toolsContact,widthContact,ready=true;int switches;NotebookInk.Tool tool=NotebookInk.Tool.PEN;boolean isFinishing(){return finishing;}boolean isDestroyed(){return destroyed;}boolean canEdit(){return ready;}void selectTool(NotebookInk.Tool selected){if(!ready)throw new AssertionError(\"busy native input\");tool=selected;switches++;}",
            method(a,"private boolean acceptsStylusShortcut("),method(a,"private void stylusDoubleTap("),method(a,"private void applyStylusShortcut("),
            "static void check(boolean b,String s){if(!b)throw new AssertionError(s);}public static void run(){",
            "StylusActivityFixture f=new StylusActivityFixture();f.stylusDoubleTap();check(f.tool==NotebookInk.Tool.ERASER&&f.toolHint.shown==1&&!f.toolHint.pen,\"body gesture selects eraser and shows its icon\");Clock.now+=300;f.stylusDoubleTap();check(f.tool==NotebookInk.Tool.PEN&&f.toolHint.pen&&f.switches==2,\"next body gesture selects pen\");Clock.now+=300;f.tool=NotebookInk.Tool.LASSO;f.stylusDoubleTap();check(f.tool==NotebookInk.Tool.PEN,\"lasso goes directly to pen\");",
            "Clock.now+=300;f.ready=false;f.stylusDoubleTap();check(f.tool==NotebookInk.Tool.PEN&&f.toolHint.shown==3,\"unfinished native stroke keeps old tool and no premature hint\");f.ready=true;f.applyStylusShortcut();check(f.tool==NotebookInk.Tool.ERASER&&f.toolHint.shown==4,\"settled native input applies deferred selection\");",
            "Clock.now+=300;f.active=false;f.stylusDoubleTap();f.active=true;f.applyStylusShortcut();check(f.switches==4,\"background gesture cannot queue an edit\");f.viewMenu=new Object();f.stylusDoubleTap();f.viewMenu=null;f.applyStylusShortcut();check(f.switches==4,\"modal screens cannot queue a delayed tool change\");",
            "Clock.now+=300;f.widthPopup=new Width();f.stylusDoubleTap();check(f.widthPopup.dismissed==1&&f.tool==NotebookInk.Tool.PEN,\"hardware switch closes the old width control\");",
            "}","}");
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.StylusActivityFixture",f.replace("SystemClock.","Clock."));assertions+=8;
    }
    private static void receiver()throws Exception{
        String source=read("NotebookStylusGesture").replaceAll("(?m)^package .*;\\s*","").replaceAll("(?m)^import .*;\\s*","").replace("final class NotebookStylusGesture","static final class NotebookStylusGesture");
        String f=String.join("\n","package org.eink.inkprobe.notebook;public class StylusReceiverFixture {",
            "static class Intent {String action;Intent(String a){action=a;}String getAction(){return action;}}static class IntentFilter {String action;IntentFilter(String a){action=a;}}abstract static class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}static class Context {int starts,stops;BroadcastReceiver receiver;IntentFilter filter;Intent registerReceiver(BroadcastReceiver r,IntentFilter f){starts++;receiver=r;filter=f;return null;}void unregisterReceiver(BroadcastReceiver r){stops++;}}",
            source,"public static void run(){Context c=new Context();int[] events={0};NotebookStylusGesture g=new NotebookStylusGesture(c,()->events[0]++);g.start();g.start();if(c.starts!=1||!NotebookStylusGesture.ACTION.equals(c.filter.action))throw new AssertionError(\"one foreground registration\");c.receiver.onReceive(c,new Intent(NotebookStylusGesture.ACTION));c.receiver.onReceive(c,new Intent(\"unrelated\"));c.receiver.onReceive(c,null);if(events[0]!=1)throw new AssertionError(\"only exact vendor event accepted\");BroadcastReceiver retained=c.receiver;g.stop();g.stop();retained.onReceive(c,new Intent(NotebookStylusGesture.ACTION));if(events[0]!=1||c.stops!=1)throw new AssertionError(\"stale receiver ignored after pause\");g.start();c.receiver.onReceive(c,new Intent(NotebookStylusGesture.ACTION));if(events[0]!=2||c.starts!=2)throw new AssertionError(\"foreground resumes receiving\");}","}");
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.StylusReceiverFixture",f);assertions+=4;
    }
    private static void hint()throws Exception{
        String s=read("NotebookToolHint");
        String f=String.join("\n","package org.eink.inkprobe.notebook;public class ToolHintFixture {",
            "static final long DURATION_MS=650;static class Rect {int l,t,r,b;Rect(){}Rect(int l,int t,int r,int b){this.l=l;this.t=t;this.r=r;this.b=b;}}static class View {boolean attached=true;boolean isAttachedToWindow(){return attached;}int getWidth(){return 1000;}void getLocationOnScreen(int[] p){p[0]=100;p[1]=200;}}static class Gravity {static final int TOP=48,LEFT=3;}static class NotebookIconDrawable {enum Icon {PEN,ERASER}}static class Icon {NotebookIconDrawable.Icon icon;String description;void setIcon(NotebookIconDrawable.Icon i){icon=i;}void setContentDescription(String s){description=s;}}",
            "class Listener {Rect bounds;void boundsChanged(Rect b){bounds=b;}}class Popup {boolean showing;int x,y;boolean isShowing(){return showing;}void update(int x,int y,int w,int h){this.x=x;this.y=y;}void dismiss(){showing=false;listener.boundsChanged(new Rect());}}static class NotebookPopupHost {static boolean available=true;static boolean show(Popup p,View v,int g,int x,int y){p.showing=available;p.x=x;p.y=y;return available;}}static class Handler {Runnable pending;long delay;void removeCallbacks(Runnable r){pending=null;}void postDelayed(Runnable r,long d){pending=r;delay=d;}}",
            "final Listener listener=new Listener();final Popup popup=new Popup();final Icon icon=new Icon();final Handler main=new Handler();final Runnable hide=this::hide;int dp(int i){return i;}",
            method(s,"void show("),method(s,"void hide("),
            "public static void run(){ToolHintFixture f=new ToolHintFixture();View root=new View();f.show(root,true);if(!f.popup.showing||f.popup.x!=578||f.popup.y!=208||f.main.delay!=650||f.icon.icon!=NotebookIconDrawable.Icon.PEN)throw new AssertionError(\"top-center short pen icon\");f.show(root,false);if(f.icon.icon!=NotebookIconDrawable.Icon.ERASER||!\"Eraser\".equals(f.icon.description))throw new AssertionError(\"repeated gesture updates icon and restarts expiry\");f.main.pending.run();if(f.popup.showing||f.listener.bounds.r!=0||f.main.pending!=null)throw new AssertionError(\"expires without lingering native exclusion\");NotebookPopupHost.available=false;f.show(root,true);if(f.listener.bounds.r!=0||f.main.pending!=null)throw new AssertionError(\"invalid host never leaves a hint or timer\");}","}");
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.ToolHintFixture",f);assertions+=4;
    }
    private static String read(String name)throws Exception{return new String(Files.readAllBytes(new File("app/src/main/java/org/eink/inkprobe/notebook/"+name+".java").toPath()),StandardCharsets.UTF_8);}
    private static String method(String source,String signature){return NotebookWorkspaceActivationTest.method(source,signature);}
    private static void check(boolean b,String message){assertions++;if(!b)throw new AssertionError(message);}
}
