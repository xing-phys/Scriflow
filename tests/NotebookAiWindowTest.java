package org.eink.inkprobe.notebook;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Execute the production native input barriers rather than assuming PopupWindow consumes native ink. */
public final class NotebookAiWindowTest {
    public static void main(String[] args) throws Exception {
        String activity=read("NotebookActivity"),toggle=method(activity,"private void toggleAiChat(");
        String fixture=String.join("\n",
            "package org.eink.inkprobe.notebook;import java.io.*;import java.util.*;public class AiWindowFixture {",
            "static class Rect {int id;Rect(){}Rect(int id){this.id=id;}Rect(Rect r){id=r.id;}void set(Rect r){id=r.id;}public boolean equals(Object r){return r instanceof Rect&&id==((Rect)r).id;}}",
            "static class SystemClock {static long uptimeMillis(){return 12;}}static class NotebookPage{}",
            "static class NotebookFloatingTools {enum Action{AI_CHAT}final Button b=new Button();Button button(Action a){return b;}}static class Button {boolean selected=true;void setToolSelected(boolean b){selected=b;}}",
            "final List<String> events=new ArrayList<>();class Ink {void pauseForEdit(){events.add(\"pause\");}void redrawAfterOverlayChange(){events.add(\"redraw\");}void start(){events.add(\"start\");}}",
            "interface Result{void ready(NotebookPage p,IOException e);}class Session{Result pending;void load(Result r){pending=r;}void finish(IOException e){Result r=pending;pending=null;r.ready(new NotebookPage(),e);}}",
            "String restoredAiId,restoredAiProfile;boolean active=true,editing,aiContact,aiMoved,restoreAiChat=true,pauseFails;int generation=1,merges,restores;long lastInputAt;IOException shownError;Object aiChat=new Object();Ink ink=new Ink();final Session session=new Session();final Rect aiChatBounds=new Rect();final NotebookFloatingTools floatingTools=new NotebookFloatingTools();",
            "boolean canEdit(){return active&&!editing&&shownError==null&&ink!=null;}void pauseForEdit(){editing=true;ink.pauseForEdit();if(pauseFails)shownError=new IOException(\"native pause failed\");}void updateOverlayBounds(){events.add(\"bounds\");}void mergeWindow(){merges++;}void installViewport(){restores++;events.add(\"restore\");}void showError(IOException e){shownError=e;}",
            method(activity,"private void finishAiContact("),
            "class Listener {",method(toggle,"public boolean contactStart("),method(toggle,"public void contactEnd("),method(toggle,"public void boundsChanged(Rect"),method(toggle,"public void closed("),"}",
            "static int assertions;static void check(boolean b,String s){assertions++;if(!b)throw new AssertionError(s);}public static void run()throws Exception{",
            "AiWindowFixture tap=new AiWindowFixture();Listener listener=tap.new Listener();check(listener.contactStart()&&tap.aiContact&&tap.editing&&tap.events.equals(Arrays.asList(\"pause\")),\"chat DOWN pauses native input synchronously before child dispatch\");listener.contactEnd();check(!tap.aiContact&&tap.editing&&tap.session.pending!=null&&!tap.events.contains(\"start\"),\"UP waits for writer barrier\");tap.session.finish(null);check(!tap.editing&&tap.merges==1&&tap.restores==0&&tap.events.equals(Arrays.asList(\"pause\",\"start\")),\"ordinary tap resumes without rebuilding paper\");",
            "AiWindowFixture move=new AiWindowFixture();Listener drag=move.new Listener();drag.contactStart();drag.boundsChanged(new Rect(5));check(move.aiMoved&&move.aiChatBounds.id==5&&move.events.equals(Arrays.asList(\"pause\",\"bounds\")),\"drag updates native exclusion while handwriting remains paused\");drag.contactEnd();move.session.finish(null);check(move.restores==1&&move.events.get(2).equals(\"restore\"),\"drag restores the newly uncovered paper once after writer barrier\");",
            "AiWindowFixture reflow=new AiWindowFixture();Listener layout=reflow.new Listener();layout.boundsChanged(new Rect(8));check(reflow.events.equals(Arrays.asList(\"pause\",\"bounds\",\"redraw\",\"start\")),\"keyboard/layout reflow pauses before changing exclusions and restores afterward\");int size=reflow.events.size();layout.boundsChanged(new Rect(8));check(reflow.events.size()==size,\"unchanged layout does not flash paper\");",
            "AiWindowFixture stale=new AiWindowFixture();Listener retired=stale.new Listener();retired.contactStart();retired.contactEnd();stale.active=false;stale.session.finish(null);check(stale.merges==0&&stale.events.equals(Arrays.asList(\"pause\")),\"late writer callback cannot resume a paused Activity\");",
            "AiWindowFixture failed=new AiWindowFixture();failed.pauseFails=true;check(!failed.new Listener().contactStart()&&!failed.aiContact&&failed.shownError!=null,\"native pause failure blocks chat contacts\");",
            "AiWindowFixture writer=new AiWindowFixture();Listener broken=writer.new Listener();broken.contactStart();broken.contactEnd();writer.session.finish(new IOException(\"disk full\"));check(writer.shownError!=null&&!writer.events.contains(\"start\"),\"writer failure cannot restart native input\");",
            "layout.closed();check(reflow.aiChat==null&&!reflow.restoreAiChat&&!reflow.floatingTools.b.selected,\"user close clears tool selection and automatic reopen\");",
            "AiWindowFixture background=new AiWindowFixture();background.active=false;background.new Listener().closed();check(background.restoreAiChat,\"Activity pause preserves reopen intent\");",
            "System.out.println(\"NotebookAiWindow: \"+assertions+\" assertions passed (production contact, writer and native clip callbacks).\");}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.AiWindowFixture",fixture);
        String chat=read("NotebookAiChat");
        String modal=String.join("\n",
            "package org.eink.inkprobe.notebook;public class AiModalFixture {boolean contact=true;Dialog clearDialog,historyDialog;final Listener listener=new Listener();static class Dialog{boolean showing=true;boolean isShowing(){return showing;}}static class Listener{int ends;void contactEnd(){ends++;}}",
            method(chat,"private void finishContact("),
            "public static void run(){AiModalFixture f=new AiModalFixture();f.clearDialog=new Dialog();f.finishContact();if(!f.contact||f.listener.ends!=0)throw new AssertionError(\"confirmation retains the native input barrier\");f.clearDialog=null;f.historyDialog=new Dialog();f.finishContact();if(!f.contact||f.listener.ends!=0)throw new AssertionError(\"history also retains the native input barrier\");f.historyDialog=null;f.finishContact();f.finishContact();if(f.contact||f.listener.ends!=1)throw new AssertionError(\"dismiss resumes the writer exactly once\");System.out.println(\"NotebookAiModal: 3 assertions passed (production confirmation contact barrier).\");}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.AiModalFixture",modal);
        resizeControls();
        sendDrafts();
        historyLifecycle();
        settingsRows();
    }
    private static void settingsRows()throws Exception{
        String fixture=String.join("\n",
            "package org.eink.inkprobe.notebook;import java.util.*;import java.util.function.*;public class AiSettingsRowsFixture {",
            "static class View{}static class ViewGroup extends View{}static class Gravity{static int CENTER_VERTICAL;}static class Color{static int BLACK;}static class TextUtils{static class TruncateAt{static Object END;}}static class android{static class content{static class res{static class ColorStateList{static Object valueOf(int n){return null;}}}}}",
            "static class LinearLayout extends View{List<View> children=new ArrayList<>();LinearLayout(Object a){}void setGravity(int n){}void addView(View v,Object p){children.add(v);}static class LayoutParams{LayoutParams(int a,int b){}LayoutParams(int a,int b,int c){}}}",
            "interface Changed{void change(CheckBox button,boolean on);}static class CheckBox extends View{String text;boolean checked,enabled;Changed listener;CheckBox(Object a){}void setText(String s){text=s;}void setTextSize(int n){}void setTextColor(int n){}void setSingleLine(boolean b){}void setEllipsize(Object o){}void setChecked(boolean b){if(checked==b)return;checked=b;if(listener!=null)listener.change(this,b);}void setEnabled(boolean b){enabled=b;}void setButtonTintList(Object o){}void setContentDescription(String s){}void setOnCheckedChangeListener(Changed l){listener=l;}}",
            "static class NotebookIconDrawable{enum Icon{SETTINGS}}static class NotebookIconButton extends View{NotebookIconDrawable.Icon icon;boolean available;Consumer<View> click;NotebookIconButton(Object a,NotebookIconDrawable.Icon i,String s){icon=i;}void setTooltipText(String s){}void setAvailable(boolean b){available=b;}void setOnClickListener(Consumer<View> c){click=c;}void press(){if(available)click.accept(this);}}",
            "static class NotebookAiModel{String id,name;NotebookAiModel(String i,String n){id=i;name=n;}}static class NotebookAiModelLibrary{static int MAX_MODELS=2;}static class Toast{static int LENGTH_SHORT;static Toast makeText(Object a,String s,int n){return new Toast();}void show(){}}static class Status{void setText(String s){}}",
            "Object activity=new Object();boolean working;final Status status=new Status();Set<String> checked=new HashSet<>();List<NotebookAiModel> available=Arrays.asList(new NotebookAiModel(\"hidden-id\",\"Friendly model\"),new NotebookAiModel(\"second\",\"Second\"),new NotebookAiModel(\"third\",\"Third\"));NotebookAiModel edited;int dp(int n){return n;}void editModel(NotebookAiModel m){edited=m;}",
            method(read("NotebookAiSettingsSection"),"public View getView(int position"),
            "public static void run(){AiSettingsRowsFixture f=new AiSettingsRowsFixture();LinearLayout row=(LinearLayout)f.getView(0,null,null);CheckBox name=(CheckBox)row.children.get(0);NotebookIconButton gear=(NotebookIconButton)row.children.get(1);if(row.children.size()!=2||!name.text.equals(\"Friendly model\")||gear.icon!=NotebookIconDrawable.Icon.SETTINGS)throw new AssertionError(\"A model row contains only its selection/name and settings gear\");",
            "name.setChecked(true);if(!f.checked.contains(\"hidden-id\"))throw new AssertionError(\"Inline checking selects the actual model ID\");gear.press();if(f.edited!=f.available.get(0))throw new AssertionError(\"The gear edits its own model\");f.checked.add(\"second\");CheckBox third=(CheckBox)((LinearLayout)f.getView(2,null,null)).children.get(0);third.setChecked(true);if(third.checked||f.checked.size()!=2)throw new AssertionError(\"Inline selection enforces the enabled model bound\");",
            "f.working=true;LinearLayout busy=(LinearLayout)f.getView(2,null,null);if(((CheckBox)busy.children.get(0)).enabled||((NotebookIconButton)busy.children.get(1)).available)throw new AssertionError(\"Fetch/save prevents edits to the in-flight selection\");System.out.println(\"NotebookAiSettingsRows: 5 assertions passed (production inline name-only rows, selection, model gear and worker guards).\");}}"
        );NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.AiSettingsRowsFixture",fixture);
    }
    private static void historyLifecycle() throws Exception {
        String fixture=String.join("\n",
            "package org.eink.inkprobe.notebook;public class AiHistoryUiFixture {",
            "static class Session{boolean loaded=true,switching;String draft=\"\";int newChats;void clear(){newChats++;}}static class Handler{int removed;void removeCallbacks(Object r){removed++;}}final Handler main=new Handler();final Object saveDraft=new Object();Session session=new Session();String text=\"selected Markdown\",displayProblem=\"old error\";void saveDraft(){session.draft=text;}",
            method(read("NotebookAiChat"),"private void newChat("),
            "public static void run(){AiHistoryUiFixture f=new AiHistoryUiFixture();f.newChat();if(f.session.newChats!=1||!f.session.draft.equals(f.text)||!f.displayProblem.isEmpty())throw new AssertionError(\"New preserves the source draft before starting a separate session\");f.session.switching=true;f.newChat();if(f.session.newChats!=1)throw new AssertionError(\"double New waits for the first archive barrier\");System.out.println(\"NotebookAiHistoryUi: 2 assertions passed (production non-destructive New and switching guard).\");}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.AiHistoryUiFixture",fixture);
        String lifecycle=String.join("\n",
            "package org.eink.inkprobe.notebook;public class AiHistoryLifecycleFixture {",
            "String restoredAiId,restoredAiProfile;boolean restoreAiChat;class Chat{String sessionId(){return \"session-id\";}String profile(){return \"settings-fingerprint\";}void close(){restoredAiId=null;restoredAiProfile=null;restoreAiChat=false;aiChat=null;}}Chat aiChat=new Chat();",
            method(read("NotebookActivity"),"private void pauseAiChat("),
            "public static void run(){AiHistoryLifecycleFixture f=new AiHistoryLifecycleFixture();f.pauseAiChat();if(f.aiChat!=null||!f.restoreAiChat||!\"session-id\".equals(f.restoredAiId)||!\"settings-fingerprint\".equals(f.restoredAiProfile))throw new AssertionError(\"pause keeps selected session identity despite the close callback\");f.pauseAiChat();if(!f.restoreAiChat)throw new AssertionError(\"repeated canvas teardown keeps reopen state\");System.out.println(\"NotebookAiHistoryLifecycle: 2 assertions passed (production pause/restore identity capture).\");}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.AiHistoryLifecycleFixture",lifecycle);
    }
    private static void sendDrafts() throws Exception {
        String fixture=String.join("\n",
            "package org.eink.inkprobe.notebook;public class AiSendDraftFixture {",
            "static class Chat{boolean awaiting;boolean awaitingReply(){return awaiting;}}static class Session{boolean busy,saveFailed;int sends,saves;Chat chat=new Chat();void stop(){busy=false;}void retrySave(){saves++;}void send(String text){sends++;busy=true;}}",
            "static class Editor{String text=\"selected Markdown\";Object getText(){return text;}}static class Handler{void removeCallbacks(Object r){}}final Handler main=new Handler();final Object saveDraft=new Object();final Editor composer=new Editor();Session session=new Session();String displayProblem=\"\";void setText(String s){composer.text=s;}void hideKeyboard(){}",
            method(read("NotebookAiChat"),"private void send("),
            "public static void run(){int checks=0;AiSendDraftFixture fresh=new AiSendDraftFixture();fresh.send();if(fresh.session.sends!=1||!fresh.composer.text.isEmpty())throw new AssertionError(\"new Send consumes its draft\");checks++;",
            "AiSendDraftFixture retry=new AiSendDraftFixture();retry.session.chat.awaiting=true;retry.send();if(retry.session.sends!=1||!retry.composer.text.equals(\"selected Markdown\"))throw new AssertionError(\"Retry must preserve selections appended for the next question\");checks++;",
            "AiSendDraftFixture stop=new AiSendDraftFixture();stop.session.busy=true;stop.send();if(stop.session.busy||!stop.composer.text.equals(\"selected Markdown\"))throw new AssertionError(\"Stop must preserve the next draft\");checks++;",
            "AiSendDraftFixture save=new AiSendDraftFixture();save.session.saveFailed=true;save.send();if(save.session.saves!=1||save.session.sends!=0||!save.composer.text.equals(\"selected Markdown\"))throw new AssertionError(\"Retry save must preserve unsent selection text\");checks++;System.out.println(\"NotebookAiSendDraft: \"+checks+\" assertions passed (production Send, Retry, Stop and save draft handling).\");}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.AiSendDraftFixture",fixture);
    }
    private static void resizeControls() throws Exception {
        String chat=read("NotebookAiChat");
        String fixture=String.join("\n",
            "package org.eink.inkprobe.notebook;public class AiResizeFixture {",
            "static class Rect {int left,top,right,bottom;Rect(){}Rect(int l,int t,int r,int b){set(l,t,r,b);}Rect(Rect r){set(r);}void set(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}void set(Rect r){set(r.left,r.top,r.right,r.bottom);}int width(){return right-left;}int height(){return bottom-top;}boolean isEmpty(){return width()<=0||height()<=0;}}",
            "static class View {interface OnTouchListener{boolean onTouch(View v,MotionEvent e);}int redraws;void getWindowVisibleDisplayFrame(Rect r){r.set(100,200,1300,1200);}void getLocationOnScreen(int[] p){p[0]=100;p[1]=200;}int getWidth(){return 1200;}int getHeight(){return 1000;}boolean performClick(){return true;}void invalidate(){redraws++;}}",
            "static class MotionEvent {static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;int action;float x,y;MotionEvent(int a,float x,float y){action=a;this.x=x;this.y=y;}int getActionMasked(){return action;}int getPointerCount(){return 1;}float getRawX(){return x;}float getRawY(){return y;}}static class ViewConfiguration {static ViewConfiguration get(Object a){return new ViewConfiguration();}int getScaledTouchSlop(){return 3;}}",
            "static class Popup {Rect placed;boolean isShowing(){return true;}void update(int x,int y,int w,int h){placed=new Rect(x,y,x+w,y+h);}void setWidth(int w){}void setHeight(int h){}}static class Prefs {Prefs edit(){return this;}Prefs putFloat(String n,float v){return this;}void apply(){}}",
            "final Object activity=new Object();final View anchor=new View(),content=new View();final Popup popup=new Popup();final Prefs prefs=new Prefs();final Rect area=new Rect(),bounds=new Rect();Rect resizeFrame;boolean closed;float xFraction=.95f,yFraction=.12f,widthFraction=.68f,heightFraction=.68f;int dp(int n){return n;}void publish(Rect r){bounds.set(r);}",
            method(chat,"private void position("),method(chat,"private static float clamp("),method(chat,"private final class Grip"),
            "static int assertions;static void check(boolean b,String s){assertions++;if(!b)throw new AssertionError(s);}public static void run(){",
            "AiResizeFixture f=new AiResizeFixture();f.position();int left=f.bounds.left,top=f.bounds.top;Grip grip=f.new Grip(true);View target=new View();grip.onTouch(target,new MotionEvent(0,0,0));",
            "for(int delta:new int[]{80,2000,-100,-2000,40}){grip.onTouch(target,new MotionEvent(2,delta,delta));check(f.bounds.left==left&&f.bounds.top==top,\"production resize grip keeps both opposite edges fixed\");check(f.bounds.right<=1292&&f.bounds.bottom<=1192,\"production popup keeps right and bottom borders inside visible screen\");check(f.popup.placed.right==f.bounds.right&&f.popup.placed.bottom==f.bounds.bottom,\"input exclusion matches the actual resized frame\");}",
            "grip.onTouch(target,new MotionEvent(1,40,40));f.position();check(Math.abs(f.bounds.left-left)<=1&&Math.abs(f.bounds.top-top)<=1&&f.resizeFrame==null,\"release persists the anchored position without a corner jump\");check(f.content.redraws==5,\"every resize invalidates the complete foreground border\");System.out.println(\"NotebookAiResize: \"+assertions+\" assertions passed (production grip, placement, border redraw and persistence).\");}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.AiResizeFixture",fixture);
    }
    private static String read(String name) throws Exception { return new String(Files.readAllBytes(Paths.get("app/src/main/java/org/eink/inkprobe/notebook/"+name+".java")),StandardCharsets.UTF_8); }
    private static String method(String source,String signature) { return NotebookWorkspaceActivationTest.method(source,signature); }
}
