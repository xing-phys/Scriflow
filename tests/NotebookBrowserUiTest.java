package org.eink.inkprobe.notebook;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

/** Geometry, real presentation callbacks and native-history dispatch without Android stub execution. */
public final class NotebookBrowserUiTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        geometry(); anchors(); presentation(); history();
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
            "final NotebookBrowserToolbarState state=new NotebookBrowserToolbarState(false,.25f,.75f);final View panel=new View(),grip=new View(),clockTarget=new View();final Clock normalStatus=new Clock(),zenClock=new Clock();final NotebookGripGesture gesture=new NotebookGripGesture(8);final Runnable moveFrame=()->{};boolean resumed,dragging;int pointer=-1,remembers,keyboards,placements;void removeCallbacks(Runnable r){}View getParent(){return new View();}void place(){placements++;}void remember(){remembers++;}void hideKeyboard(){keyboards++;}",
            method(source, "void collapse("), method(source, "void zen("), method(source, "void reveal("), method(source, "void resumed("),
            method(source, "Bundle save("), method(source, "void dispose("), method(source, "private void present("), method(source, "private void updateClocks("), method(source, "private void stopDrag("),
            "static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}public static void run(){BrowserToolsFixture f=new BrowserToolsFixture();f.resumed(true);f.present();check(f.normalStatus.started&&!f.zenClock.started,\"expanded browser uses normal status without a seconds timer\");check(f.panel.visibility==VISIBLE&&f.grip.visibility==VISIBLE&&f.clockTarget.visibility==GONE,\"normal controls have no separate clock overlay\");f.collapse(true);check(f.panel.visibility==GONE&&f.grip.visibility==VISIBLE&&f.clockTarget.visibility==GONE,\"collapse leaves only the small button\");check(!f.normalStatus.started&&!f.zenClock.started&&f.keyboards==1&&f.remembers==1,\"collapse stops hidden clocks, dismisses keyboard and remembers position\");f.zen(true);check(f.panel.visibility==GONE&&f.grip.visibility==GONE&&f.clockTarget.visibility==VISIBLE&&f.zenClock.started,\"Zen leaves only the clock visible\");check(!f.normalStatus.started,\"Zen never starts the hidden status view\");Bundle saved=f.save();check(Boolean.TRUE.equals(saved.values.get(\"zen\"))&&Boolean.TRUE.equals(saved.values.get(\"collapsed\"))&&saved.values.get(\"horizontal\").equals(.25f)&&saved.values.get(\"vertical\").equals(.75f),\"recreation state retains Zen, collapse and placement\");f.zen(false);check(f.panel.visibility==GONE&&f.grip.visibility==VISIBLE&&f.clockTarget.visibility==GONE&&!f.zenClock.started,\"clock exit restores the previous collapsed mode\");f.collapse(false);f.zen(true);f.zen(false);check(f.panel.visibility==VISIBLE&&f.normalStatus.started,\"clock exit also restores previously expanded controls\");f.zen(true);f.resumed(false);check(!f.zenClock.started&&!f.normalStatus.started&&f.state.zen(),\"pause stops clocks without losing Zen state\");f.resumed(true);check(f.zenClock.started&&!f.normalStatus.started,\"resume starts only the visible Zen clock\");f.reveal();check(!f.state.zen()&&f.state.controlsVisible()&&f.panel.visibility==VISIBLE&&f.normalStatus.started&&!f.zenClock.started,\"errors expose the recovery controls\");f.dispose();f.present();check(!f.normalStatus.started&&!f.zenClock.started&&!f.dragging,\"retired controls cannot restart timers\");System.out.println(\"NotebookBrowserTools: \"+checks+\" assertions passed (production collapse/Zen/clock, restoration and lifecycle callbacks).\");}}"
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
            "Web web=new Web();String archiveUrl=\"file:///private/page.mht\";boolean offline=true,saving,loading,failed,clearHistoryOnFinish,pageReady=true;String currentUrl=\"https://example.org/\";final Button go=new Button(),previous=new Button(),next=new Button(),reload=new Button(),save=new Button();final Address address=new Address();final View saveShield=new View(),hint=new View();",
            method(source, "private void history("), method(source, "private boolean canHistory("), method(source, "private boolean isArchiveUrl("),
            method(source, "private void updateControls("), method(source, "private boolean restoreOfflineHistory("),
            "static int checks;static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}public static void run(){BrowserHistoryFixture f=new BrowserHistoryFixture();f.updateControls();check(f.previous.available&&!f.next.available,\"offline anchors expose previous navigation\");f.history(false);check(f.web.history.index==1&&f.previous.available&&f.next.available,\"Back traverses an offline section jump\");check(f.pageReady&&!f.loading&&!f.failed,\"same-document navigation cannot get stuck waiting for a load callback\");f.history(false);check(f.web.history.index==0&&!f.previous.available&&f.next.available,\"Back reaches the original saved-page position\");f.history(true);check(f.web.history.index==1&&f.previous.available&&f.next.available,\"Forward replays a section jump\");f.saving=true;int moves=f.web.moves;f.history(true);check(f.web.moves==moves,\"save ownership still gates history changes\");f.saving=false;f.web.history=new WebBackForwardList(1,\"file:///private/other.mht\",f.archiveUrl,\"https://example.org/online\");f.updateControls();check(!f.previous.available&&!f.next.available,\"offline history cannot leave its selected archive\");f.history(false);f.history(true);check(f.web.moves==moves,\"forbidden entries are never dispatched to the browser engine\");check(!f.restoreOfflineHistory(new Bundle())&&f.clearHistoryOnFinish&&f.web.stops==1,\"unsafe restored history falls back to a fresh local load\");f.web.history=new WebBackForwardList(1,f.archiveUrl,f.archiveUrl+\"#one\");check(f.restoreOfflineHistory(new Bundle())&&f.hint.visibility==View.GONE,\"valid local history restores across rotation\");check(!f.restoreOfflineHistory(null),\"missing state uses a normal archive open\");f.web.history=new WebBackForwardList(0);check(!f.restoreOfflineHistory(new Bundle()),\"empty history cannot leave a blank saved-page reader\");f.offline=false;f.web.history=new WebBackForwardList(1,\"https://example.org/\",\"https://example.org/#one\");f.history(false);check(f.web.history.index==0&&f.pageReady&&!f.loading,\"online section history also retains page readiness\");System.out.println(\"NotebookBrowserHistory: \"+checks+\" assertions passed (production offline Back/Forward, anchor readiness, confinement and restoration).\");}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.BrowserHistoryFixture", fixture.replace("NotebookBrowserEngine.History", "WebBackForwardList"));
    }

    private static String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("app/src/main/java/org/eink/inkprobe/notebook/" + name + ".java")), StandardCharsets.UTF_8);
    }
    private static String method(String source, String signature) { return NotebookWorkspaceActivationTest.method(source, signature); }
    private static void equal(String expected, String value, String detail) { check(expected.equals(value), detail); }
    private static void check(boolean condition, String detail) { assertions++; if (!condition) throw new AssertionError(detail); }
}
