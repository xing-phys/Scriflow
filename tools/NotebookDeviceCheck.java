package org.eink.inkprobe.notebook;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Signed, external instrumentation. Creates only uniquely named disposable QA notebooks. */
public final class NotebookDeviceCheck extends Instrumentation {
    private NotebookSession session; private NotebookActivity activity;
    private int checks; private File output; private Bundle args;
    private interface Checked { void run() throws Exception; }
    private interface Operation { void run(NotebookSession.Callback callback); }
    @Override public void onCreate(Bundle args) { super.onCreate(args); this.args=args; start(); }
    private void report(String s) { Bundle b=new Bundle();b.putString("stream",s+"\n");sendStatus(0,b); }
    private void main(Checked action) throws Exception {
        AtomicReference<Throwable> error=new AtomicReference<>();
        runOnMainSync(()->{try{action.run();}catch(Throwable t){error.set(t);}});
        if(error.get()!=null)throw new Exception("Main callback failed",error.get());
    }
    private Object field(Object o,String name)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private Object call(Object o,String name,Class<?>[] types,Object... values)throws Exception {Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(o,values);}
    private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;report("PASS "+message);}
    private void operation(Operation op)throws Exception {
        CountDownLatch done=new CountDownLatch(1);AtomicReference<IOException> error=new AtomicReference<>();
        main(()->op.run((p,e)->{error.set(e);done.countDown();}));
        if(!done.await(30,TimeUnit.SECONDS))throw new AssertionError("writer timeout");
        if(error.get()!=null)throw error.get();
    }
    private void ready()throws Exception {
        long until=SystemClock.uptimeMillis()+15000;AtomicBoolean ok=new AtomicBoolean();
        while(SystemClock.uptimeMillis()<until){main(()->{
            IOException e=(IOException)field(activity,"shownError");if(e!=null)throw e;
            ok.set((Boolean)call(activity,"canEdit",new Class<?>[0]));
        });if(ok.get())return;SystemClock.sleep(40);}
        throw new AssertionError("editor readiness timeout");
    }
    private void screenshot(String name)throws Exception {Bitmap b=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(output,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private void appendVisible(boolean left)throws Exception {
        ready();AtomicReference<NotebookPage.Stroke> stroke=new AtomicReference<>();
        main(()->{
            NotebookViewport v=(NotebookViewport)field(activity,"viewport");float scroll=(Float)field(activity,"scrollY");int p=v.centeredPage(scroll);
            float sx=left?150:v.width/2f,sy=left?300:v.height/2f;
            float x=(sx-v.pageLeft(p))/v.scale(p),y=(sy-v.pageTop(p,scroll))/v.scale(p);
            float d=50/v.scale(p);stroke.set(new NotebookPage.Stroke(2,8/v.scale(p),0xff000000,Arrays.asList(new NotebookPage.Point(x-d,y,1,.7f),new NotebookPage.Point(x,y,2,1),new NotebookPage.Point(x+d,y,3,.7f))));
            call(activity,"pauseForEdit",new Class<?>[0]);
        });
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Throwable> error=new AtomicReference<>();
        main(()->session.appendViewport(Collections.singletonMap(1,Collections.singletonList(stroke.get())),(p,e)->{try{call(activity,"finishEdit",new Class<?>[]{int.class,NotebookPage.class,IOException.class},field(activity,"generation"),p,e);}catch(Throwable t){error.set(t);}done.countDown();}));
        if(!done.await(30,TimeUnit.SECONDS))throw new AssertionError("append timeout");if(error.get()!=null)throw new Exception(error.get());ready();
    }
    @Override public void onStart(){try{if(args!=null&&"true".equals(args.getString("gestureOnly"))){captureOnly();}else runChecks();Bundle b=new Bundle();b.putString("stream","QA COMPLETE checks="+checks+" output="+output);finish(0,b);}catch(Throwable t){StringWriter s=new StringWriter();t.printStackTrace(new PrintWriter(s));Bundle b=new Bundle();b.putString("stream",s.toString());finish(-1,b);}}
    private void captureOnly()throws Exception {
        Activity owner=startActivitySync(new Intent().setClassName(getTargetContext(),"org.eink.inkprobe.notebook.NotebookLibraryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        AtomicInteger events=new AtomicInteger();BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){events.incrementAndGet();report("STYLUS_BODY_EVENT "+i.getAction());}};
        main(()->owner.registerReceiver(receiver,new IntentFilter("com.huawei.stylus.action.BUTTON_DOUBLE_PRESSED")));
        File stop=new File(getTargetContext().getExternalFilesDir(null),"qa-capture-stop");stop.delete();
        report("GESTURE_CAPTURE_READY");
        long deadline=SystemClock.uptimeMillis()+600000;
        while(!stop.exists()&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(250);
        stop.delete();main(()->owner.unregisterReceiver(receiver));report("GESTURE_CAPTURE_FINISHED events="+events.get());
    }
    private NotebookInk.Tool selected() throws Exception {
        AtomicReference<NotebookInk.Tool> value=new AtomicReference<>();
        main(()->value.set((NotebookInk.Tool)field(activity,"tool")));return value.get();
    }
    private void select(NotebookInk.Tool value)throws Exception {
        ready();if(selected()!=value)main(()->call(activity,"selectTool",new Class<?>[]{NotebookInk.Tool.class},value));
    }
    private void bodyGesture(NotebookInk.Tool expected)throws Exception {
        SystemClock.sleep(300);
        // Firmware protects this broadcast from application senders. Exercise the real
        // receiver directly here; the interactive check separately verifies hardware delivery.
        main(()->((BroadcastReceiver)field(field(activity,"stylusGesture"),"receiver")).onReceive(activity,new Intent(NotebookStylusGesture.ACTION)));
        long until=SystemClock.uptimeMillis()+3000;
        while(selected()!=expected&&SystemClock.uptimeMillis()<until)SystemClock.sleep(15);
        check(selected()==expected&&((NotebookInk)field(activity,"ink")).getTool()==expected,"body gesture selects "+expected+" in UI and native backend");
    }
    private void checkStylus()throws Exception {
        Object backend=field(activity,"ink");int strokes=session.current().strokes.size();
        select(NotebookInk.Tool.PEN);bodyGesture(NotebookInk.Tool.ERASER);
        Object hint=field(activity,"toolHint");PopupWindow popup=(PopupWindow)field(hint,"popup");
        check(popup.isShowing()&&!popup.isTouchable()&&!popup.isFocusable(),"brief tool icon is visible and does not intercept input");
        WindowManager.LayoutParams lp=(WindowManager.LayoutParams)popup.getContentView().getRootView().getLayoutParams();
        View root=(View)field(activity,"workspaceRoot");
        check(Math.abs(lp.x*2+lp.width-root.getWidth())<=2,"tool icon is centered at top of screen");
        screenshot("eraser-hint");SystemClock.sleep(800);
        check(!popup.isShowing(),"tool icon disappears automatically");
        bodyGesture(NotebookInk.Tool.PEN);select(NotebookInk.Tool.LASSO);bodyGesture(NotebookInk.Tool.PEN);
        check(field(activity,"ink")==backend&&session.current().strokes.size()==strokes,"tool gestures retain native widget and saved ink");
    }
    private void interactiveStylus()throws Exception {
        select(NotebookInk.Tool.PEN);SystemClock.sleep(800);
        File stop=new File(getTargetContext().getExternalFilesDir(null),"qa-capture-stop");stop.delete();
        AtomicInteger events=new AtomicInteger();
        BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){events.incrementAndGet();report("PHYSICAL_BODY_EVENT "+events.get());}};
        main(()->activity.registerReceiver(receiver,new IntentFilter(NotebookStylusGesture.ACTION)));
        report("FEATURE_READY: QA note open, PEN selected; waiting for physical body double tap");
        long deadline=SystemClock.uptimeMillis()+600000;NotebookInk.Tool previous=selected();int transitions=0;
        while(!stop.exists()&&SystemClock.uptimeMillis()<deadline){
            NotebookInk.Tool current=selected();
            if(current!=previous){transitions++;report("PHYSICAL_TOOL_CHANGE "+previous+" -> "+current);screenshot("physical-tool-"+transitions);previous=current;}
            SystemClock.sleep(40);
        }
        stop.delete();main(()->activity.unregisterReceiver(receiver));
        check(events.get()>0&&transitions>0,"physical M-Pencil body gesture changes tool inside open note");
        report("PHYSICAL_RESULT events="+events.get()+" toolTransitions="+transitions);
    }
    private void runChecks()throws Exception {
        output=new File(getTargetContext().getExternalFilesDir(null),"qa-"+System.currentTimeMillis());output.mkdirs();
        Activity library=startActivitySync(new Intent().setClassName(getTargetContext(),"org.eink.inkprobe.notebook.NotebookLibraryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main(()->session=NotebookSession.get(getTargetContext()));operation(cb->session.load(cb));
        String prefix="QA-USB-"+SystemClock.uptimeMillis();operation(cb->session.createNotebook(prefix+"-A",NotebookPaperSize.A5,cb));int first=session.notebookId();
        operation(cb->session.createNotebook(prefix+"-B",NotebookPaperSize.A5,cb));int second=session.notebookId();operation(cb->session.openNotebook(first,cb));
        activity=(NotebookActivity)startActivitySync(new Intent().setClassName(getTargetContext(),"org.eink.inkprobe.notebook.NotebookActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ready();
        Object backend=field(activity,"ink");appendVisible(true);screenshot("main-seeded");
        check(session.current().strokes.size()==1,"native replay contains persisted QA ink");
        NotebookWorkspace workspace=(NotebookWorkspace)field(activity,"workspace");
        main(()->call(activity,"changeWorkspace",new Class<?>[]{Runnable.class},(Runnable)()->workspace.add(second,NotebookWorkspace.Position.FLOATING)));ready();
        check(field(activity,"ink")==backend,"opening floating note retains native editor");
        NotebookWorkspace.Pane pane=workspace.find(second);
        main(()->call(activity,"changeWorkspace",new Class<?>[]{Runnable.class},(Runnable)()->{pane.x=.7f;pane.y=.2f;}));ready();
        main(()->call(activity,"changeWorkspace",new Class<?>[]{Runnable.class},(Runnable)()->workspace.resize(pane,.1f,false)));ready();
        check(field(activity,"ink")==backend,"inactive float move/resize retains main surface");
        main(()->call(activity,"activatePane",new Class<?>[]{NotebookWorkspace.Pane.class,Runnable.class},pane,null));ready();appendVisible(false);
        check(field(activity,"ink")==backend,"floating focus handoff reuses native widget");
        main(()->call(activity,"changeWorkspace",new Class<?>[]{Runnable.class},(Runnable)()->workspace.resize(pane,-.1f,false)));ready();
        check(field(activity,"ink")==backend,"active floating resize recomposes retained widget");
        main(()->{
            NotebookViewport v=(NotebookViewport)field(activity,"viewport");float x=v.width/2f,y=v.height/2f;
            List<NotebookPage.Point> path=Arrays.asList(new NotebookPage.Point(x-100,y-80,10,1),new NotebookPage.Point(x+100,y-80,20,1),new NotebookPage.Point(x+100,y+80,30,1),new NotebookPage.Point(x-100,y+80,40,1),new NotebookPage.Point(x-100,y-80,50,1));
            call(activity,"lasso",new Class<?>[]{List.class},path);
        });ready();
        PopupWindow selection=(PopupWindow)field(field(activity,"selectionTools"),"popup");
        check(selection.isShowing(),"floating lasso opens real action panel without bad token");
        check(field(activity,"selection")!=null,"lasso selects persisted QA stroke");
        View root=(View)field(activity,"workspaceRoot"),canvas=(View)field(activity,"canvas");
        WindowManager.LayoutParams layout=(WindowManager.LayoutParams)selection.getContentView().getRootView().getLayoutParams();
        check(layout.token==root.getWindowToken()&&canvas.getWindowToken()!=root.getWindowToken(),"floating lasso panel is parented by Activity token");
        PopupWindow tools=(PopupWindow)field(field(activity,"floatingTools"),"popup");
        check(tools.getWindowLayoutType()==WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL&&selection.getWindowLayoutType()==WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,"toolbox uses higher application layer than lasso/notes");
        screenshot("floating-lasso");
        main(()->call(activity,"performSelectionAction",new Class<?>[]{NotebookSelectionTools.Action.class},NotebookSelectionTools.Action.DONE));ready();
        main(()->call(activity,"showZoomBadge",new Class<?>[0]));
        check(((PopupWindow)field(activity,"zoomBadge")).isShowing(),"floating zoom badge accepts Activity token");
        main(()->((PopupWindow)field(activity,"zoomBadge")).dismiss());
        main(()->call(activity,"chooseWidth",new Class<?>[]{NotebookInk.Tool.class},NotebookInk.Tool.PEN));SystemClock.sleep(250);
        Object width=field(activity,"widthPopup");check(width!=null&&((PopupWindow)field(width,"popup")).isShowing(),"floating width control opens with valid token");
        main(()->call(width,"cancel",new Class<?>[0]));ready();
        main(()->call(activity,"collapsePane",new Class<?>[]{NotebookWorkspace.Pane.class,boolean.class},pane,true));ready();
        long closedBy=SystemClock.uptimeMillis()+4000;while(workspace.find(second)!=null&&SystemClock.uptimeMillis()<closedBy)SystemClock.sleep(30);ready();
        report("close state id="+session.notebookId()+" expected="+first+" pane="+(workspace.find(second)!=null)+" sameEditor="+(field(activity,"ink")==backend));
        check(session.notebookId()==first&&workspace.find(second)==null&&field(activity,"ink")==backend,"closing active float returns to main without replacing editor");
        check(session.current().strokes.size()==1,"main QA ink survives float close and handoff");screenshot("main-after-float");
        try(FileWriter out=new FileWriter(new File(output,"result.json"))){out.write("{\"checks\":"+checks+",\"firstId\":"+first+",\"secondId\":"+second+",\"prefix\":\""+prefix+"\"}");}
        if(args!=null&&"true".equals(args.getString("testStylus")))checkStylus();
        if(args!=null&&"true".equals(args.getString("interactiveStylus")))interactiveStylus();
    }
}
