package org.eink.inkprobe.notebook;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

/** Geometry, real presentation callbacks and native-history dispatch without Android stub execution. */
public final class NotebookBrowserUiTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        geometry(); externalNotes(); sharedNotes(); startup(); anchors(); presentation(); history(); backNavigation(); visibility(); captureBridgeRetry();
        System.out.println("NotebookBrowserUi: " + assertions + " assertions passed (bounded toolbox, state restoration and offline anchor mapping).");
    }

    private static void geometry() {
        NotebookBrowserToolbarState state = new NotebookBrowserToolbarState(false, .25f, .75f);
        check(state.controlsVisible() && state.gripVisible() && !state.zen(), "expanded controls start with the grip");
        state.collapse(true); state.zen(true);
        check(!state.controlsVisible() && !state.gripVisible() && state.zen(), "Zen hides both controls and grip");
        state.zen(false);
        check(state.collapsed() && !state.controlsVisible() && state.gripVisible(), "leaving Zen restores the collapsed state");
        state.collapse(false); state.zen(true); state.zen(false);
        check(state.controlsVisible() && state.horizontal() == .25f && state.vertical() == .75f, "Zen preserves expansion and position");
        for (int width : new int[]{1, 36, 200, 320, 702, 936}) {
            for (int height : new int[]{1, 36, 100, 300, 702, 936}) {
                for (float x : new float[]{0, .5f, 1}) for (float y : new float[]{0, .5f, 1}) {
                    state.position(x, y);
                    NotebookBrowserToolbarState.Layout layout = state.layout(width, height, 36, 560, 220, 8, 6);
                    check(layout.gripSize > 0 && layout.panelWidth > 0 && layout.panelHeight > 0, "all overlay dimensions stay positive");
                    check(layout.gripLeft >= 0 && layout.gripTop >= 0 && layout.gripLeft + layout.gripSize <= width
                        && layout.gripTop + layout.gripSize <= height, "grip stays on screen across rotation and keyboard sizes");
                    check(layout.panelLeft >= 0 && layout.panelTop >= 0 && layout.panelLeft + layout.panelWidth <= width
                        && layout.panelTop + layout.panelHeight <= height, "expanded controls stay inside the viewport");
                    if (height >= 100) check(layout.panelTop + layout.panelHeight <= layout.gripTop
                        || layout.panelTop >= layout.gripTop + layout.gripSize, "scrollable panel leaves the grip exposed");
                }
            }
        }
        state.moveTo(-10000, 10000, 702, 936, 36, 8);
        check(state.horizontal() == 0 && state.vertical() == 1, "drag clamps at the available edges");
        state.position(Float.NaN, Float.POSITIVE_INFINITY);
        check(state.horizontal() == 0 && state.vertical() == 0, "invalid saved coordinates recover to a reachable corner");
        state.position(.5f, .5f);
        NotebookBrowserToolbarState.Layout portrait = state.layout(702, 936, 36, 560, 220, 8, 6);
        NotebookBrowserToolbarState.Layout landscape = state.layout(936, 702, 36, 560, 220, 8, 6);
        check(portrait.gripLeft == landscape.gripTop && portrait.gripTop == landscape.gripLeft, "normalized placement follows rotation");
    }


    private static void externalNotes() {
        for (int[] size : new int[][]{{702,936},{936,702},{1404,1872},{1872,1404}}) {
            NotebookWorkspace model = new NotebookWorkspace(); model.setExternalBase(true); model.setFloatingBase(true);
            NotebookWorkspace.Pane note = model.add(1, NotebookWorkspace.Position.FLOATING);
            check(model.backgroundBox(size[0], size[1]).width == size[0], "floating notes leave the browser full width");
            for (NotebookWorkspace.Position side : NotebookWorkspace.Position.values()) if (side != NotebookWorkspace.Position.FLOATING) {
                model.place(note, side);
                NotebookWorkspace.Box box = model.box(note,size[0],size[1]), page = model.backgroundBox(size[0],size[1]);
                check(!model.floating(note), "the first browser note supports every split position");
                check(box.width*box.height+page.width*page.height==size[0]*size[1], "split note and browser cover the full 4:3 viewport");
                check(Math.max(box.x,page.x)>=Math.min(box.x+box.width,page.x+page.width)
                    || Math.max(box.y,page.y)>=Math.min(box.y+box.height,page.y+page.height), "browser reflows beside docked notes without overlap");
                model.resize(note,.1f,false); check(note.split>.5f,"enlarging a docked first note grows its split"); model.resize(note,0,true);
                note.collapsed=true; check(model.backgroundBox(size[0],size[1]).width==size[0]&&model.backgroundBox(size[0],size[1]).height==size[1],"minimizing restores the browser viewport"); note.collapsed=false;
            }
            NotebookWorkspace.Pane second=model.add(2,NotebookWorkspace.Position.RIGHT);
            check(note.position==NotebookWorkspace.Position.FLOATING&&!model.floating(second),"docking another note floats the previous split");
            NotebookWorkspace restored=new NotebookWorkspace();restored.setExternalBase(true);restored.restore(model.encode());
            check(restored.find(2).position==NotebookWorkspace.Position.RIGHT,"split geometry survives recreation");
        }
    }
    private static void sharedNotes() {
        NotebookWorkspace online=new NotebookWorkspace();online.setExternalBase(true);online.add(8,NotebookWorkspace.Position.RIGHT);
        Object live=new Object(),offline=new Object();NotebookBrowserNotes notes=new NotebookBrowserNotes(null,0,0);
        notes.claim(live);NotebookBrowserNotes.Snapshot first=notes.save(live,online.encode(),8);
        check(first.revision==1&&first.editor==8,"opening an online note publishes its selected editor and layout");
        NotebookBrowserNotes.Snapshot saved=notes.claim(offline);NotebookWorkspace local=new NotebookWorkspace();local.setExternalBase(true);local.restore(saved.layout);
        check(local.find(8).position==NotebookWorkspace.Position.RIGHT&&!local.find(8).collapsed,"saved pages acquire the same split note");
        local.minimize(local.find(8));NotebookBrowserNotes.Snapshot minimized=notes.save(offline,local.encode(),0);
        check(minimized.revision==2&&minimized.editor==0,"offline minimization publishes one shared change");
        check(notes.save(live,online.encode(),8)==minimized,"a covered online screen cannot resurrect an older note layout");
        notes.release(live);check(notes.owns(offline),"destroying an old screen cannot release the active owner");
        NotebookBrowserNotes.Snapshot resumed=notes.claim(live);online.restore(resumed.layout);
        check(online.find(8).collapsed&&resumed.editor==0,"returning online respects offline minimization");
        online.close(online.find(8));NotebookBrowserNotes.Snapshot closed=notes.save(live,online.encode(),0);
        NotebookBrowserNotes restarted=new NotebookBrowserNotes(closed.layout,closed.editor,closed.revision);
        check(restarted.claim(new Object()).layout.equals("1\n")&&restarted.current().editor==0,"a persisted closed workspace stays closed after process restart");
        check(notes.save(live,closed.layout,closed.editor)==closed,"unchanged metadata does not churn the shared revision");
        notes.release(live);check(notes.save(live,first.layout,first.editor)==closed,"a destroyed owner cannot overwrite saved state");
    }

    private static void startup() {
        NotebookBrowserTabs tabs = new NotebookBrowserTabs();
        tabs.current().home=false; tabs.current().url="https://example.org/retained";String old=tabs.current().id;
        tabs.startHome();check(tabs.current().home&&tabs.find(old).url.endsWith("retained")&&!tabs.find(old).home,"fresh entry opens Home and retains the previous website");
        int count=tabs.items.size();tabs.startHome();check(tabs.items.size()==count,"repeated entry reuses an existing Home tab");
        for(NotebookBrowserTabs.Tab t:tabs.items)t.home=false;
        while(tabs.items.size()<NotebookBrowserTabs.MAX){tabs.add().home=false;}
        tabs.startHome();check(tabs.items.size()==NotebookBrowserTabs.MAX&&tabs.find(old)!=null,"Home at the tab limit never deletes a website");
    }

    private static void anchors() {
        String archive = "file:///private/websites/page.mht", source = "https://example.org/article?q=%23value#old";
        equal(archive + "#section%20one", NotebookBrowserAddress.offlineTarget(archive, source, "https://example.org/article?q=%23value#section%20one"), "source anchors map to the selected archive");
        equal(archive + "#", NotebookBrowserAddress.offlineTarget(archive, source, "https://example.org/article?q=%23value#"), "empty fragment can return to the top");
        equal(archive + "#section-two", NotebookBrowserAddress.offlineTarget(archive, source, archive + "#section-two"), "local anchor history keeps its URL");
        for (String target : Arrays.asList("https://other.example/article#one", "https://example.org/other#one",
                "https://example.org/article?q=different#one", "https://example.org/article?q=%23value", "file:///private/other.mht#one",
                "content://private#one", "javascript:alert(1)#one", null))
            check(NotebookBrowserAddress.offlineTarget(archive, source, target) == null, "external pages and other local files cannot become offline destinations");
        check(NotebookBrowserAddress.offlineTarget("https://example.org", source, source) == null, "an archive destination must be local");
    }

    private static void presentation() throws Exception {
        String source = source("NotebookBrowserTools");
        String fixture = String.join("\n",
            "package org.eink.inkprobe.notebook; public class BrowserToolsFixture {",
            "static final int VISIBLE=0,GONE=8;static class View {int visibility;String description;void setVisibility(int v){visibility=v;}void setContentDescription(String s){description=s;}void requestDisallowInterceptTouchEvent(boolean b){}}",
            "static class Clock {boolean started;void start(){started=true;}void stop(){started=false;}}static class Bundle {java.util.Map<String,Object> values=new java.util.HashMap<>();void putBoolean(String k,boolean v){values.put(k,v);}void putFloat(String k,float v){values.put(k,v);}}",
            "final NotebookBrowserToolbarState state=new NotebookBrowserToolbarState(false,.25f,.75f);final View panel=new View(),grip=new View();final java.util.function.Consumer<Boolean> zenChanged=b->sharedZen=b;static boolean sharedZen;final NotebookGripGesture gesture=new NotebookGripGesture(8);final Runnable moveFrame=()->{};boolean resumed,dragging;int pointer=-1,remembers,keyboards,placements;void removeCallbacks(Runnable r){}View getParent(){return new View();}void place(){placements++;}void remember(){remembers++;}void hideKeyboard(){keyboards++;}",
            method(source, "void collapse("), method(source, "void zen("), method(source, "void reveal("), method(source, "void resumed("),
            method(source, "Bundle save("), method(source, "void dispose("), method(source, "private void present("), method(source, "private void stopDrag("),
            "static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}public static void run(){BrowserToolsFixture f=new BrowserToolsFixture();f.resumed(true);f.present();check(f.panel.visibility==VISIBLE&&f.grip.visibility==VISIBLE&&!sharedZen,\"normal toolbox leaves the shared device island in normal mode\");f.collapse(true);check(f.panel.visibility==GONE&&f.grip.visibility==VISIBLE&&!sharedZen,\"collapse preserves normal device status\");check(f.keyboards==1&&f.remembers==1,\"collapse dismisses keyboard and remembers position\");f.zen(true);check(f.panel.visibility==GONE&&f.grip.visibility==GONE&&sharedZen,\"Zen hides the toolbox and requests the shared note clock\");Bundle saved=f.save();check(Boolean.TRUE.equals(saved.values.get(\"zen\"))&&Boolean.TRUE.equals(saved.values.get(\"collapsed\"))&&saved.values.get(\"horizontal\").equals(.25f)&&saved.values.get(\"vertical\").equals(.75f),\"recreation retains Zen, collapse and placement\");f.zen(false);check(!sharedZen&&f.panel.visibility==GONE&&f.grip.visibility==VISIBLE,\"clock exit restores the previous collapsed mode\");f.collapse(false);f.zen(true);f.zen(false);check(f.panel.visibility==VISIBLE&&!sharedZen,\"clock exit restores expanded controls\");f.zen(true);f.resumed(false);check(f.state.zen()&&!f.dragging,\"pause preserves mode and stops dragging\");f.reveal();check(!sharedZen&&f.state.controlsVisible()&&f.panel.visibility==VISIBLE,\"errors expose recovery controls\");f.dispose();check(!f.resumed&&!f.dragging,\"retired controls stop their contact state\");System.out.println(\"NotebookBrowserTools: \"+checks+\" assertions passed (shared status, collapse, Zen and restoration).\");}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.BrowserToolsFixture", fixture);
    }

    private static void history() throws Exception {
        String source = source("NotebookBrowserActivity");
        String fixture = String.join("\n",
            "package org.eink.inkprobe.notebook;public class BrowserHistoryFixture {",
            "static class View {static final int VISIBLE=0,GONE=8;int visibility;void setVisibility(int v){visibility=v;}}static class Button {boolean available;void setAvailable(boolean b){available=b;}void setText(String s){}}static class Address {void setEnabled(boolean b){}}static class Bundle {}",
            "static class Item {final String url;Item(String s){url=s;}String getUrl(){return url;}}static class WebBackForwardList {final java.util.List<Item> entries=new java.util.ArrayList<>();int index;WebBackForwardList(int i,String...urls){index=i;for(String s:urls)entries.add(new Item(s));}int getCurrentIndex(){return index;}int getSize(){return entries.size();}Item getItemAtIndex(int i){return entries.get(i);}}",
            "static class Web {WebBackForwardList history=new WebBackForwardList(2,\"file:///private/page.mht\",\"file:///private/page.mht#one\",\"file:///private/page.mht#two\");int moves,stops;boolean canGoBack(){return history.index>0;}boolean canGoForward(){return history.index+1<history.getSize();}WebBackForwardList copyBackForwardList(){return history;}void goBack(){history.index--;moves++;}void goForward(){history.index++;moves++;}String getUrl(){return history.getSize()==0?null:history.getItemAtIndex(history.index).getUrl();}WebBackForwardList restoreState(Bundle b){return history;}void stopLoading(){stops++;}}",
            "Web web=new Web();String archiveUrl=\"file:///private/page.mht\";boolean home,offline=true,saving,loading,failed,clearHistoryOnFinish,pageReady=true;String currentUrl=\"https://example.org/\";final Button go=new Button(),previous=new Button(),next=new Button(),reload=new Button(),save=new Button();final Address address=new Address();final View saveShield=new View(),hint=new View();void showCurrentPage(){}",
            method(source, "private void history("), method(source, "private boolean canHistory("), method(source, "private boolean isArchiveUrl("),
            method(source, "private void updateControls("), method(source, "private boolean restoreOfflineHistory("),
            "static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}public static void run(){BrowserHistoryFixture f=new BrowserHistoryFixture();f.updateControls();check(f.previous.available&&!f.next.available,\"offline anchors expose previous navigation\");f.history(false);check(f.web.history.index==1&&f.previous.available&&f.next.available,\"Back traverses an offline section jump\");check(f.pageReady&&!f.loading&&!f.failed,\"same-document navigation cannot get stuck waiting for a load callback\");f.history(false);check(f.web.history.index==0&&!f.previous.available&&f.next.available,\"Back reaches the original saved-page position\");f.history(true);check(f.web.history.index==1&&f.previous.available&&f.next.available,\"Forward replays a section jump\");f.saving=true;int moves=f.web.moves;f.history(true);check(f.web.moves==moves,\"save ownership still gates history changes\");f.saving=false;f.web.history=new WebBackForwardList(1,\"file:///private/other.mht\",f.archiveUrl,\"https://example.org/online\");f.updateControls();check(!f.previous.available&&!f.next.available,\"offline history cannot leave its selected archive\");f.history(false);f.history(true);check(f.web.moves==moves,\"forbidden entries are never dispatched to the browser engine\");check(!f.restoreOfflineHistory(new Bundle())&&f.clearHistoryOnFinish&&f.web.stops==1,\"unsafe restored history falls back to a fresh local load\");f.web.history=new WebBackForwardList(1,f.archiveUrl,f.archiveUrl+\"#one\");check(f.restoreOfflineHistory(new Bundle())&&f.hint.visibility==View.GONE,\"valid local history restores across rotation\");check(!f.restoreOfflineHistory(null),\"missing state uses a normal archive open\");f.web.history=new WebBackForwardList(0);check(!f.restoreOfflineHistory(new Bundle()),\"empty history cannot leave a blank saved-page reader\");f.offline=false;f.web.history=new WebBackForwardList(1,\"https://example.org/\",\"https://example.org/#one\");f.history(false);check(f.web.history.index==0&&f.pageReady&&!f.loading,\"online section history also retains page readiness\");System.out.println(\"NotebookBrowserHistory: \"+checks+\" assertions passed (production offline Back/Forward, anchor readiness, confinement and restoration).\");}}"
        );
        fixture = fixture.replace("System.out.println(\"NotebookBrowserHistory:", "f.loading=true;f.updateControls();check(f.save.available,\"readable pages can save with pending network requests\");f.pageReady=false;f.updateControls();check(!f.save.available,\"an unreadable page still cannot save\");System.out.println(\"NotebookBrowserHistory:");
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.BrowserHistoryFixture", fixture.replace("NotebookBrowserEngine.History", "WebBackForwardList"));
    }

    private static void backNavigation() throws Exception {
        String code=source("NotebookBrowserActivity");
        String fixture=String.join("\n",
            "package org.eink.inkprobe.notebook;class BackBase {int exits;public void onBackPressed(){exits++;}void overridePendingTransition(int a,int b){}}public class BrowserBackFixture extends BackBase {",
            "static class Tools {boolean zen,collapsed=true;boolean isZen(){return zen;}boolean isCollapsed(){return collapsed;}void zen(boolean b){zen=b;}void collapse(boolean b){collapsed=b;}}static class Web {String getUrl(){return \"https://example.org/\";}}Tools browserTools=new Tools();Web web=new Web();boolean home,offline,waiting,back;int restored,moves;boolean waitForSave(){return waiting;}boolean canHistory(boolean forward){return back;}void history(boolean forward){moves++;}void showCurrentPage(){restored++;home=false;}",
            method(code,"@Override public void onBackPressed("),
            "static void check(boolean b){if(!b)throw new AssertionError(\"Back navigation regression\");}public static void run(){BrowserBackFixture f=new BrowserBackFixture();f.onBackPressed();check(f.exits==1);f.home=true;f.onBackPressed();check(f.restored==1&&f.exits==1);f.home=true;f.web=null;f.onBackPressed();check(f.exits==2);f.browserTools.zen=true;f.onBackPressed();check(!f.browserTools.zen&&f.exits==2);f.browserTools.collapsed=false;f.onBackPressed();check(f.browserTools.collapsed&&f.exits==2);f.waiting=true;f.onBackPressed();check(f.exits==2);f.waiting=false;f.home=false;f.back=true;f.onBackPressed();check(f.moves==1&&f.exits==2);System.out.println(\"NotebookBrowserBack: 7 assertions passed (production Home return, root exit, Zen/panel priority, saving and history).\");}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.BrowserBackFixture",fixture);
    }

    private static void visibility() throws Exception {
        String activity=source("NotebookBrowserActivity"),page=source("NotebookGeckoPage");
        String fixture=String.join("\n",
            "package org.eink.inkprobe.notebook;class VisibilityBase{protected void onPause(){}protected void onStop(){}}public class BrowserVisibilityFixture extends VisibilityBase{",
            "static class Session{boolean active=true;void setActive(boolean b){active=b;}void flushSessionState(){}}static class Prompts{void close(){}}static class Runtime{Runtime getWebExtensionController(){return this;}void setTabActive(Session s,boolean b){}}static class Page{boolean closed;Session session=new Session();Prompts prompts=new Prompts();Runtime runtime=new Runtime();",
            method(page,"@Override void onResume(").replace("@Override ",""),method(page,"@Override void onWindowPause(").replace("@Override ",""),method(page,"@Override void onPause(").replace("@Override ",""),"}",
            "static class Tools{void resumed(boolean b){}}static class Dialog{void dismiss(){}}static class NotebookBrowserPreviews{static void remember(Object a,Page b){}}boolean offline,home,pageReady=true,resumed=true;Page web=new Page();Tools browserTools=new Tools();Dialog shownDialog;java.util.Map<String,Page> engines=new java.util.HashMap<>();void rememberTabs(){}",
            method(activity,"@Override protected void onPause("),method(activity,"@Override protected void onStop("),
            "static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError(\"Visible page lifecycle regression\");}public static void run(){BrowserVisibilityFixture f=new BrowserVisibilityFixture();Page background=new Page();background.onPause();f.engines.put(\"current\",f.web);f.engines.put(\"background\",background);f.onPause();check(f.web.session.active);check(!background.session.active);check(!f.resumed);f.onStop();check(!f.web.session.active&&!background.session.active);f.web.onResume();check(f.web.session.active);f.web.onWindowPause();check(f.web.session.active);f.web.closed=true;f.web.onPause();check(f.web.session.active);BrowserVisibilityFixture offline=new BrowserVisibilityFixture();offline.offline=true;offline.onPause();check(offline.web.session.active);offline.onStop();check(!offline.web.session.active);System.out.println(\"NotebookBrowserVisibility: \"+checks+\" assertions passed (production Gecko visibility, floating overlays, hidden tabs and full background).\");}}"
        ).replace("NotebookBrowserEngine page : engines.values()","Page page : engines.values()");
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.BrowserVisibilityFixture",fixture);
    }

    private static void captureBridgeRetry() throws Exception {
        String fixture=String.join("\n",
            "package org.eink.inkprobe.notebook;public class BrowserCaptureRetryFixture{static class Capture{String id;Capture(String s){id=s;}}static class Port{int sent;String id;void postMessage(Object message){sent++;id=message.toString();}}Capture capture=new Capture(\"current\");boolean closed;Port pagePort;int canceled;java.util.ArrayDeque<Runnable> queue=new java.util.ArrayDeque<>();void postDelayed(Runnable r,int ms){queue.add(r);}void cancelCapture(){canceled++;capture=null;}Object json(Object...args){return args[3];}",
            method(source("NotebookGeckoPage"),"private void beginCapture("),
            "static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError(\"Capture bridge wait regression\");}public static void run(){BrowserCaptureRetryFixture f=new BrowserCaptureRetryFixture();f.beginCapture(f.capture,50);check(f.canceled==0&&!f.queue.isEmpty());for(int i=0;i<5;i++)f.queue.remove().run();f.pagePort=new Port();f.queue.remove().run();check(f.pagePort.sent==1&&f.pagePort.id.equals(\"current\"));BrowserCaptureRetryFixture unavailable=new BrowserCaptureRetryFixture();unavailable.beginCapture(unavailable.capture,50);int steps=0;while(!unavailable.queue.isEmpty()&&steps++<100)unavailable.queue.remove().run();check(unavailable.canceled==1&&unavailable.capture==null&&unavailable.queue.isEmpty());BrowserCaptureRetryFixture replaced=new BrowserCaptureRetryFixture();replaced.beginCapture(replaced.capture,50);replaced.capture=new Capture(\"new\");replaced.pagePort=new Port();replaced.queue.remove().run();check(replaced.pagePort.sent==0&&replaced.canceled==0);replaced.closed=true;replaced.beginCapture(replaced.capture,50);check(replaced.pagePort.sent==0&&replaced.queue.isEmpty());System.out.println(\"NotebookBrowserCaptureRetry: \"+checks+\" assertions passed (production connection grace period, timeout, retired capture and closed page).\");}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.BrowserCaptureRetryFixture",fixture);
    }

    private static String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("app/src/main/java/org/eink/inkprobe/notebook/" + name + ".java")), StandardCharsets.UTF_8);
    }
    private static String method(String source, String signature) { return NotebookWorkspaceActivationTest.method(source, signature); }
    private static void equal(String expected, String value, String detail) { check(expected.equals(value), detail); }
    private static void check(boolean condition, String detail) { assertions++; if (!condition) throw new AssertionError(detail); }
}
