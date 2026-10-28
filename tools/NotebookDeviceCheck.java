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
    private NotebookSession session; private volatile NotebookActivity activity;
    private int checks; private File output; private Bundle args;
    private final List<Integer> extraIds=new ArrayList<>();
    private interface Checked { void run() throws Exception; }
    private interface Operation { void run(NotebookSession.Callback callback); }
    @Override public void onCreate(Bundle args) { super.onCreate(args); this.args=args; start(); }
    @Override public void callActivityOnResume(Activity resumed) {
        super.callActivityOnResume(resumed);
        // The system picker/lock screen can rotate and recreate a landscape editor.
        // Follow the resumed instance rather than inspecting the destroyed predecessor.
        if(resumed instanceof NotebookActivity)activity=(NotebookActivity)resumed;
    }
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
    private void metric(String phase)throws Exception {
        String[] fds=new File("/proc/self/fd").list(),threads=new File("/proc/self/task").list();
        report("RESOURCE "+phase+" pssKB="+Debug.getPss()+" nativeKB="+Debug.getNativeHeapAllocatedSize()/1024
            +" javaKB="+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1024
            +" fds="+(fds==null?-1:fds.length)+" threads="+(threads==null?-1:threads.length));
    }
    private void changed(Runnable mutation)throws Exception {
        main(()->call(activity,"changeWorkspace",new Class<?>[]{Runnable.class},mutation));ready();
    }
    private void touchReader(NotebookReader reader,int action,int type,float[] xs,float[] ys,long down,long time)throws Exception {
        MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[xs.length];
        MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[xs.length];
        for(int i=0;i<xs.length;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=i;props[i].toolType=type;
            coords[i]=new MotionEvent.PointerCoords();coords[i].x=xs[i];coords[i].y=ys[i];coords[i].pressure=.5f;coords[i].size=.1f;}
        main(()->{MotionEvent event=MotionEvent.obtain(down,time,action,xs.length,props,coords,0,0,1,1,0,0,
            type==MotionEvent.TOOL_TYPE_STYLUS?InputDevice.SOURCE_STYLUS:InputDevice.SOURCE_TOUCHSCREEN,0);
            try{reader.onTouchEvent(event);}finally{event.recycle();}});
    }
    private void auditReaders(int second)throws Exception {
        NotebookWorkspace workspace=(NotebookWorkspace)field(activity,"workspace");int first=session.notebookId();
        changed(()->workspace.add(second,NotebookWorkspace.Position.FLOATING));
        for(int id:extraIds)changed(()->workspace.add(id,NotebookWorkspace.Position.FLOATING));
        check(workspace.panes().size()==4,"three floating companions open simultaneously");
        NotebookWorkspace.Pane pane=workspace.find(second);
        Map<?,?> windows=(Map<?,?>)field(activity,"noteWindows");NotebookReader reader=(NotebookReader)field(windows.get(pane),"reader");
        long until=SystemClock.uptimeMillis()+10000;while(field(reader,"viewport")==null&&SystemClock.uptimeMillis()<until)SystemClock.sleep(30);
        NotebookViewport before=(NotebookViewport)field(reader,"viewport");
        check(before!=null&&before.zoom==1&&((List<?>)field(reader,"sizes")).size()==5,"inactive note defaults to fit width with all five pages in continuous viewport");
        float oldScroll=(Float)field(reader,"scrollY"),x=reader.getWidth()/2f,h=reader.getHeight();long down=SystemClock.uptimeMillis();
        touchReader(reader,MotionEvent.ACTION_DOWN,MotionEvent.TOOL_TYPE_FINGER,new float[]{x},new float[]{h*.8f},down,down);
        for(int i=1;i<=5;i++)touchReader(reader,MotionEvent.ACTION_MOVE,MotionEvent.TOOL_TYPE_FINGER,new float[]{x},new float[]{h*(.8f-.13f*i)},down,down+i*16);
        touchReader(reader,MotionEvent.ACTION_UP,MotionEvent.TOOL_TYPE_FINGER,new float[]{x},new float[]{h*.15f},down,down+100);
        check((Float)field(reader,"scrollY")>oldScroll+100&&session.notebookId()==first,"finger drag scrolls inactive continuous note without taking writing focus");
        down=SystemClock.uptimeMillis();float y=h/2f,start=reader.getWidth()*.15f,end=reader.getWidth()*.45f;
        touchReader(reader,MotionEvent.ACTION_DOWN,MotionEvent.TOOL_TYPE_FINGER,new float[]{x-start},new float[]{y},down,down);
        touchReader(reader,MotionEvent.ACTION_POINTER_DOWN|(1<<8),MotionEvent.TOOL_TYPE_FINGER,new float[]{x-start,x+start},new float[]{y,y},down,down+16);
        for(int i=1;i<=10;i++){float span=start+(end-start)*i/10;
            touchReader(reader,MotionEvent.ACTION_MOVE,MotionEvent.TOOL_TYPE_FINGER,new float[]{x-span,x+span},new float[]{y,y},down,down+16+i*16);}
        touchReader(reader,MotionEvent.ACTION_POINTER_UP|(1<<8),MotionEvent.TOOL_TYPE_FINGER,new float[]{x-end,x+end},new float[]{y,y},down,down+200);
        touchReader(reader,MotionEvent.ACTION_UP,MotionEvent.TOOL_TYPE_FINGER,new float[]{x-end},new float[]{y},down,down+216);
        report("INACTIVE_PINCH width="+reader.getWidth()+" zoom="+((NotebookViewport)field(reader,"viewport")).zoom);
        check(((NotebookViewport)field(reader,"viewport")).zoom>1.2f&&session.notebookId()==first,"two-finger pinch zooms inactive note while retaining main editing focus");
        down=SystemClock.uptimeMillis();touchReader(reader,MotionEvent.ACTION_DOWN,MotionEvent.TOOL_TYPE_STYLUS,new float[]{x},new float[]{y},down,down);
        touchReader(reader,MotionEvent.ACTION_UP,MotionEvent.TOOL_TYPE_STYLUS,new float[]{x},new float[]{y},down,down+20);ready();
        check(session.notebookId()==second,"stylus tap inside inactive note activates editing without an Active button");
        main(()->call(activity,"activatePane",new Class<?>[]{NotebookWorkspace.Pane.class,Runnable.class},workspace.find(first),null));ready();
        changed(()->workspace.place(pane,NotebookWorkspace.Position.LEFT));
        changed(()->workspace.place(workspace.find(extraIds.get(0)),NotebookWorkspace.Position.RIGHT));
        int split=0;for(NotebookWorkspace.Pane p:workspace.panes())if(!p.collapsed&&!workspace.floating(p))split++;
        check(split==2,"placing another split note keeps at most two split panes");screenshot("multiple-notes");
        for(int id:extraIds)changed(()->workspace.close(workspace.find(id)));
        changed(()->workspace.close(pane));
    }
    private void auditWorkspace(int second)throws Exception {
        NotebookWorkspace workspace=(NotebookWorkspace)field(activity,"workspace");
        int first=session.notebookId();Object backend=field(activity,"ink");metric("workspace-start");
        long begin=SystemClock.uptimeMillis();
        for(int i=0;i<20;i++){
            changed(()->workspace.add(second,NotebookWorkspace.Position.FLOATING));
            NotebookWorkspace.Pane pane=workspace.find(second);
            changed(()->{pane.x=.65f;pane.y=.3f;workspace.resize(pane,-.1f,false);});
            changed(()->workspace.minimize(pane));changed(()->pane.collapsed=false);
            main(()->call(activity,"activatePane",new Class<?>[]{NotebookWorkspace.Pane.class,Runnable.class},pane,null));ready();
            NotebookWorkspace.Pane mainPane=workspace.find(first);
            main(()->call(activity,"activatePane",new Class<?>[]{NotebookWorkspace.Pane.class,Runnable.class},mainPane,null));ready();
            changed(()->workspace.close(pane));
            if((i+1)%5==0)metric("workspace-cycle-"+(i+1));
        }
        check(field(activity,"ink")==backend&&session.current().strokes.size()==1,"20 floating move/resize/minimize/restore/focus/close cycles preserve editor and QA ink");
        report("WORKSPACE_STRESS totalMs="+(SystemClock.uptimeMillis()-begin));
        long cpu=android.os.Process.getElapsedCpuTime(),time=SystemClock.uptimeMillis();SystemClock.sleep(10000);
        report("IDLE foreground wallMs="+(SystemClock.uptimeMillis()-time)+" cpuMs="+(android.os.Process.getElapsedCpuTime()-cpu));
        metric("workspace-idle");
    }
    private void shell(String command)throws Exception {
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);
                InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){
            byte[] bytes=new byte[1024];while(in.read(bytes)!=-1){}
        }
    }
    private void waitActive(boolean expected)throws Exception {
        long until=SystemClock.uptimeMillis()+10000;
        while(SystemClock.uptimeMillis()<until){AtomicBoolean value=new AtomicBoolean();main(()->value.set((Boolean)field(activity,"active")));
            if(value.get()==expected)return;SystemClock.sleep(40);}
        throw new AssertionError("Activity foreground state did not become "+expected);
    }
    private void auditLifecycle()throws Exception {
        int id=session.notebookId();int count=session.current().strokes.size();
        shell("input keyevent 223");SystemClock.sleep(1500);
        android.os.PowerManager power=(android.os.PowerManager)getTargetContext().getSystemService(Context.POWER_SERVICE);
        check(!power.isInteractive(),"explicit sleep works with Keep screen on enabled");waitActive(false);
        check(field(activity,"ink")==null&&!(Boolean)field(field(activity,"stylusGesture"),"registered"),"sleep releases native editor and foreground stylus receiver");
        long cpu=android.os.Process.getElapsedCpuTime();SystemClock.sleep(5000);
        report("IDLE screen-off wallMs=5000 cpuMs="+(android.os.Process.getElapsedCpuTime()-cpu));
        shell("input keyevent 224");SystemClock.sleep(1000);
        KeyguardManager keyguard=(KeyguardManager)getTargetContext().getSystemService(Context.KEYGUARD_SERVICE);
        report("WAKE keyguardLocked="+keyguard.isKeyguardLocked()+" deviceSecure="+keyguard.isDeviceSecure());
        screenshot("wake-before-resume");waitActive(true);ready();
        check(session.notebookId()==id&&session.current().strokes.size()==count,"wake restores QA note and persisted ink");
        check((Boolean)field(field(activity,"stylusGesture"),"registered"),"wake registers stylus shortcut again");
        shell("input keyevent 3");waitActive(false);cpu=android.os.Process.getElapsedCpuTime();SystemClock.sleep(5000);
        report("IDLE background wallMs=5000 cpuMs="+(android.os.Process.getElapsedCpuTime()-cpu));
        main(()->getTargetContext().startActivity(new Intent(getTargetContext(),NotebookActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));
        waitActive(true);ready();
        check(session.notebookId()==id&&session.current().strokes.size()==count,"Home and return restore same QA note without lost ink");
        metric("lifecycle-returned");
    }
    private void pdfIdle(NotebookPdfBackgrounds pdf)throws Exception {
        long until=SystemClock.uptimeMillis()+15000;AtomicBoolean idle=new AtomicBoolean();
        while(SystemClock.uptimeMillis()<until){main(()->idle.set(!(Boolean)field(pdf,"working")));if(idle.get())return;SystemClock.sleep(20);}
        throw new AssertionError("PDF tile timeout");
    }
    private void auditPdf()throws Exception {
        File source=new File(getTargetContext().getExternalFilesDir(null),"qa-fixtures/large-source.pdf");
        check(source.isFile(),"synthetic 600-page PDF fixture available");
        AtomicReference<IOException> failure=new AtomicReference<>();
        NotebookPdfBackgrounds pdf=new NotebookPdfBackgrounds(getTargetContext(),new NotebookPdfBackgrounds.Listener(){public void changed(){}public void failed(IOException e){failure.set(e);}},false);
        metric("pdf-start");long begin=SystemClock.uptimeMillis();
        for(int zoom=1;zoom<=8;zoom++){
            final int z=zoom;
            main(()->pdf.prepare(source,NotebookPdfTiles.visible(300,600d*z,900d*z,0,0,600,700),Collections.singleton(300)));
            pdfIdle(pdf);if(failure.get()!=null)throw failure.get();
        }
        AtomicLong pixels=new AtomicLong();
        main(()->{Map<?,?> cache=(Map<?,?>)field(pdf,"cache");for(Object key:cache.keySet())pixels.addAndGet(((NotebookPdfTiles.Tile)key).pixels());});
        report("PDF_CACHE zooms=8 pixels="+pixels.get()+" totalMs="+(SystemClock.uptimeMillis()-begin));metric("pdf-before-clear");
        if(args!=null&&"true".equals(args.getString("expectReleased")))check(pixels.get()<=2097152,"small PDF pane keeps at most two visible tile sets after eight zoom levels");
        main(pdf::clear);SystemClock.sleep(150);
        ((ExecutorService)field(pdf,"worker")).submit(()->{}).get(5,TimeUnit.SECONDS);
        boolean retained=field(pdf,"renderer")!=null;report("PDF_CLEAR nativeRendererRetained="+retained);metric("pdf-after-clear");
        if(args!=null&&"true".equals(args.getString("expectReleased")))check(!retained,"clearing hidden PDF releases native renderer on its worker");
        // Clear with an in-flight request, then replace it: stale pixels must never publish.
        main(()->{pdf.prepare(source,NotebookPdfTiles.visible(301,3000,4500,0,0,600,700),Collections.singleton(301));pdf.clear();pdf.prepare(source,NotebookPdfTiles.visible(302,600,900,0,0,600,700),Collections.singleton(302));});
        pdfIdle(pdf);if(failure.get()!=null)throw failure.get();
        AtomicBoolean exact=new AtomicBoolean();main(()->{Map<?,?> cache=(Map<?,?>)field(pdf,"cache");exact.set(!cache.isEmpty());for(Object key:cache.keySet())if(((NotebookPdfTiles.Tile)key).page!=302)exact.set(false);});
        check(exact.get(),"PDF clear during rendering drops stale tiles and renders replacement source page");
        main(pdf::close);((ExecutorService)field(pdf,"worker")).awaitTermination(5,TimeUnit.SECONDS);metric("pdf-closed");
    }
    private void runChecks()throws Exception {
        output=new File(getTargetContext().getExternalFilesDir(null),"qa-"+System.currentTimeMillis());output.mkdirs();
        Activity library=startActivitySync(new Intent().setClassName(getTargetContext(),"org.eink.inkprobe.notebook.NotebookLibraryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        main(()->session=NotebookSession.get(getTargetContext()));operation(cb->session.load(cb));
        String prefix="QA-USB-"+SystemClock.uptimeMillis();operation(cb->session.createNotebook(prefix+"-A",NotebookPaperSize.A5,cb));int first=session.notebookId();
        operation(cb->session.createNotebook(prefix+"-B",NotebookPaperSize.A5,cb));int second=session.notebookId();
        if(args!=null&&"true".equals(args.getString("audit"))){
            for(int i=0;i<4;i++)operation(session::addPage);
            for(int i=0;i<2;i++){final int n=i;operation(cb->session.createNotebook(prefix+"-extra-"+n,NotebookPaperSize.A5,cb));extraIds.add(session.notebookId());}
        }
        operation(cb->session.openNotebook(first,cb));
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
        if(args!=null&&"true".equals(args.getString("audit"))){auditReaders(second);auditWorkspace(second);auditPdf();}
        if(args!=null&&"true".equals(args.getString("lifecycle")))auditLifecycle();
        if(args!=null&&"true".equals(args.getString("interactiveStylus")))interactiveStylus();
    }
}
