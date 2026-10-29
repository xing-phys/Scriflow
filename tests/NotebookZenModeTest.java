package org.eink.inkprobe.notebook;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Execute production Zen transitions and seconds-clock callbacks without Android stub execution. */
public final class NotebookZenModeTest {
    public static void main(String[] args) throws Exception {
        clock(); toggle();
    }
    private static String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("app/src/main/java/org/eink/inkprobe/notebook/" + name + ".java")), StandardCharsets.UTF_8);
    }
    private static String method(String source, String signature) {
        return NotebookWorkspaceActivationTest.method(source, signature).replace("@Override ", "").replace("super.onDraw(canvas);", "");
    }
    private static void clock() throws Exception {
        String source = source("NotebookDeviceStatus");
        String fixture = String.join("\n",
            "package org.eink.inkprobe.notebook;import java.util.*;import java.util.function.*;public class ZenClockFixture {",
            "static class System {static long now=1234;static long currentTimeMillis(){return now;}}static class Color {static final int BLACK=0;}static class Paint {enum Style {FILL}float size=12;void setStyle(Style s){}void setColor(int c){}void setTextSize(float f){size=f;}float measureText(String s){return s.length()*5;}float ascent(){return -8;}float descent(){return 2;}}",
            "static class Canvas {final List<String> text=new ArrayList<>();int icons,rects;void drawText(String s,float x,float y,Paint p){text.add(s);}int save(){return 1;}void translate(float x,float y){}void scale(float x,float y){}void drawRect(float a,float b,float c,float d,Paint p){rects++;}void restoreToCount(int n){}}",
            "static class NotebookIconDrawable {enum Icon {WIFI,WIFI_OFF}}static class Icon {void setIcon(NotebookIconDrawable.Icon i){}void setBounds(int a,int b,int c,int d){}void draw(Canvas c){c.icons++;}}",
            "static class Handler {Runnable pending;long delay;void removeCallbacks(Runnable r){pending=null;}void postDelayed(Runnable r,long d){pending=r;delay=d;}}static class Context {int unregisters;void unregisterReceiver(Object receiver){unregisters++;}}static class Connectivity {void unregisterNetworkCallback(Object n){}}static class Resources {Metrics getDisplayMetrics(){return new Metrics();}}static class Metrics {float scaledDensity=1;}",
            "final Paint paint=new Paint();final Handler main=new Handler();final Context context=new Context();final Connectivity connectivity=new Connectivity();final Object receiver=new Object(),network=new Object();final Set<Object> wifiNetworks=new HashSet<>();final Icon wifi=new Icon(),battery=new Icon();boolean started,zenMode,networkRegistered,idle;int networkEpoch,clockWidth=80,percentWidth=30,invalidations,layouts,width,height;String description,clockValue=\"12:00\";final BooleanSupplier idleSupplier=()->idle;final BooleanSupplier idleGuard=idleSupplier;final NotebookDeviceStatusState state=new NotebookDeviceStatusState(clockValue);NotebookDeviceStatusState.Snapshot shown=state.publish(true);final Runnable secondTick=this::tickSeconds;",
            "String clock(){return clockValue;}int clockWidth(Context c){return zenMode?56:80;}Context getContext(){return context;}Resources getResources(){return new Resources();}void setContentDescription(String s){description=s;}void invalidate(){invalidations++;}void requestLayout(){layouts++;}int dp(int n){return n;}int resolveSize(int n,int spec){return n;}void setMeasuredDimension(int w,int h){width=w;height=h;}int getHeight(){return height;}",
            method(source, "private void tickSeconds("), method(source, "private void scheduleSeconds("),
            method(source, "void refresh(").replace("idle.getAsBoolean()", "idleGuard.getAsBoolean()"),
            method(source, "void setZenMode("), method(source, "void stop("),
            method(source, "private void updateDescription("), method(source, "protected void onMeasure("), method(source, "protected void onDraw("),
            "static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}public static void run(){",
            "ZenClockFixture f=new ZenClockFixture();f.scheduleSeconds();check(f.main.pending==null,\"hidden editor has no seconds timer\");f.started=true;f.scheduleSeconds();check(f.main.pending==null,\"normal status has no seconds timer\");f.clockValue=\"12:00:01\";f.setZenMode(true);check(f.main.pending==f.secondTick&&f.main.delay==766,\"Zen owns one callback aligned with the next second\");f.onMeasure(0,0);check(f.width==56&&f.height==18&&f.paint.size==10,\"Zen measures only the small clock\");Canvas zen=new Canvas();f.onDraw(zen);check(zen.text.equals(Collections.singletonList(\"12:00:01\"))&&zen.icons==0&&zen.rects==0,\"Zen draws seconds without battery or Wi-Fi\");",
            "int before=f.invalidations;f.state.battery(70,100);f.state.wifi(1);f.refresh();check(f.invalidations==before,\"hidden indicators do not repaint the clock\");f.clockValue=\"12:00:02\";System.now=2000;f.secondTick.run();check(f.shown.clock.equals(\"12:00:02\")&&f.invalidations==before+1&&f.main.delay==1000,\"seconds tick independently of writing-idle throttling\");f.secondTick.run();check(f.invalidations==before+1&&f.main.pending==f.secondTick,\"unchanged second does not redraw or accumulate callbacks\");",
            "f.clockValue=\"12:00\";f.setZenMode(false);f.onMeasure(0,0);check(f.main.pending==null&&f.width==162&&f.height==24&&f.paint.size==12,\"exit restores normal metrics and cancels seconds\");Canvas normal=new Canvas();f.onDraw(normal);check(normal.text.size()==2&&normal.icons==2&&normal.rects==1,\"normal battery and connectivity rendering return\");before=f.invalidations;f.state.clock(\"12:01\");f.refresh();check(f.invalidations==before,\"ordinary status still waits while writing\");f.idle=true;f.refresh();check(f.shown.clock.equals(\"12:01\"),\"idle refresh retains its previous normal behavior\");",
            "f.clockValue=\"12:01:03\";f.setZenMode(true);Runnable stale=f.main.pending;f.stop();before=f.invalidations;stale.run();check(f.main.pending==null&&f.invalidations==before&&f.context.unregisters==1,\"pause removes ticks and stale callbacks cannot restart them\");f.stop();check(f.context.unregisters==1,\"repeated pause is safe\");",
            "java.lang.System.out.println(\"NotebookZenClock: \"+checks+\" assertions passed (seconds, tiny rendering, normal restoration and lifecycle).\");}", "}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.ZenClockFixture", fixture);
    }
    private static void toggle() throws Exception {
        String source = source("NotebookActivity");
        String fixture = String.join("\n",
            "package org.eink.inkprobe.notebook;import java.util.*;import java.io.*;public class ZenToggleFixture {",
            "static class NotebookIconDrawable {enum Icon {FULLSCREEN,EXIT_FULLSCREEN}}static class NotebookIconButton {NotebookIconDrawable.Icon icon;String description;boolean selected;void setIcon(NotebookIconDrawable.Icon i){icon=i;}void setContentDescription(String s){description=s;}void setToolSelected(boolean b){selected=b;}}static class NotebookFloatingTools {enum Action {ZEN}final NotebookIconButton button=new NotebookIconButton();NotebookIconButton button(Action a){return button;}}",
            "class Ink {boolean ready=true,fail;void pauseForEdit(){events.add(\"pause\");ready=false;if(fail)shownError=new IOException(\"failed\");}void redrawAfterOverlayChange(){if(ready)throw new AssertionError(\"native writer must pause\");events.add(\"repair\");}void start(){events.add(\"resume\");ready=true;}}class Clock {boolean zen;void setZenMode(boolean b){zen=b;}}class Status {boolean zen,undo,redo;void setZenMode(boolean b){zen=b;}void history(boolean u,boolean r){undo=u;redo=r;}}static class Session {boolean canUndo(int page){return true;}boolean canRedo(int page){return true;}}",
            "final List<String> events=new ArrayList<>();final Ink ink=new Ink();final Clock deviceStatus=new Clock();final Status statusIslands=new Status();final Session session=new Session();final NotebookFloatingTools floatingTools=new NotebookFloatingTools();boolean zenMode,writingHistory=true;int activePage=7;IOException shownError;boolean canEdit(){return ink.ready&&shownError==null;}",
            method(source, "private void toggleZenMode("), method(source, "private void updateZenButton("),
            method(source, "private void leaveZenForError("),
            "static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}public static void run(){ZenToggleFixture f=new ZenToggleFixture();f.toggleZenMode();check(f.zenMode&&f.deviceStatus.zen&&f.statusIslands.zen&&!f.statusIslands.undo&&!f.statusIslands.redo,\"enter changes only status presentation and suppresses history\");check(f.writingHistory&&f.activePage==7&&f.floatingTools.button.selected&&f.floatingTools.button.icon==NotebookIconDrawable.Icon.EXIT_FULLSCREEN,\"mode entry preserves writing context and page\");check(f.events.equals(Arrays.asList(\"pause\",\"repair\",\"resume\")),\"toggle repairs existing ink without a viewport reinstall\");f.toggleZenMode();check(!f.zenMode&&!f.deviceStatus.zen&&!f.statusIslands.zen&&f.statusIslands.undo&&f.statusIslands.redo&&!f.floatingTools.button.selected,\"exit restores normal bars and eligible history\");f.writingHistory=false;f.toggleZenMode();f.toggleZenMode();check(!f.statusIslands.undo&&!f.statusIslands.redo,\"exit does not resurrect history dismissed by other interactions\");int size=f.events.size();f.ink.ready=false;f.toggleZenMode();check(!f.zenMode&&f.events.size()==size,\"busy native input rejects mode changes\");f.ink.ready=true;f.ink.fail=true;f.toggleZenMode();check(!f.zenMode&&!f.deviceStatus.zen,\"pause failure cannot partially enter Zen\");ZenToggleFixture recovery=new ZenToggleFixture();recovery.toggleZenMode();recovery.leaveZenForError();check(!recovery.zenMode&&!recovery.statusIslands.zen&&!recovery.deviceStatus.zen,\"failure recovery exposes the existing Retry bar\");java.lang.System.out.println(\"NotebookZenToggle: \"+checks+\" assertions passed (reversible presentation and native repair).\");}", "}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.ZenToggleFixture", fixture);
    }
}
