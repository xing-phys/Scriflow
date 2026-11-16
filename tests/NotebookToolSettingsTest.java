package org.eink.inkprobe.notebook;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Exercise the production shared preference migration with independent Activity contexts. */
public final class NotebookToolSettingsTest {
    public static void main(String[] args) throws Exception {
        String code = new String(Files.readAllBytes(Paths.get("app/src/main/java/org/eink/inkprobe/notebook/NotebookToolSettings.java")), StandardCharsets.UTF_8);
        code = code.substring(code.indexOf("/**")).replace("final class NotebookToolSettings", "static final class NotebookToolSettings");
        String fixture = String.join("\n",
            "package org.eink.inkprobe.notebook;import java.util.*;public class ToolSettingsFixture {",
            "static class SharedPreferences {final Map<String,Object> values=new HashMap<>();boolean contains(String k){return values.containsKey(k);}boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);}float getFloat(String k,float d){return ((Number)values.getOrDefault(k,d)).floatValue();}Editor edit(){return new Editor();}class Editor{Editor putFloat(String k,float v){values.put(k,v);return this;}Editor putBoolean(String k,boolean v){values.put(k,v);return this;}void apply(){}}}",
            "static class Context {static final int MODE_PRIVATE=0;final Map<String,SharedPreferences> store;Context(){store=new HashMap<>();}Context(Context app){store=app.store;}Context getApplicationContext(){return this;}SharedPreferences getSharedPreferences(String n,int m){return store.computeIfAbsent(n,k->new SharedPreferences());}}",
            code,
            "static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}public static void run(){",
            "Context app=new Context(),note=new Context(app),ai=new Context(app),browser=new Context(app);",
            "app.getSharedPreferences(\"notebook.NotebookActivity\",0).edit().putFloat(\"penWidth\",4.7f).apply();app.getSharedPreferences(\"notebook.NotebookAiActivity\",0).edit().putFloat(\"eraserWidth\",58).apply();app.getSharedPreferences(\"notebook.NotebookBrowserActivity\",0).edit().putFloat(\"penWidth\",8).putBoolean(\"regionEraser\",true).apply();",
            "check(NotebookToolSettings.penWidth(browser)==4.7f,\"migration preserves the ordinary notebook's established pen setting\");check(NotebookToolSettings.eraserWidth(browser)==58,\"per-key migration falls back to AI settings\");check(NotebookToolSettings.regionEraser(browser),\"browser-only eraser mode is retained if other screens never chose one\");",
            "NotebookToolSettings.width(browser,false,6.2f);NotebookToolSettings.width(ai,true,72);NotebookToolSettings.regionEraser(note,false);check(NotebookToolSettings.penWidth(note)==6.2f&&NotebookToolSettings.penWidth(ai)==6.2f,\"browser pen changes reach both note contexts\");check(NotebookToolSettings.eraserWidth(browser)==72&&NotebookToolSettings.eraserWidth(note)==72,\"AI eraser changes reach browser and notes\");check(!NotebookToolSettings.regionEraser(ai)&&!NotebookToolSettings.regionEraser(browser),\"eraser mode is one app-wide choice\");",
            "app.getSharedPreferences(\"notebook.NotebookActivity\",0).edit().putFloat(\"penWidth\",9).apply();check(NotebookToolSettings.penWidth(new Context(app))==6.2f,\"reopening a screen never remigrates obsolete activity preferences\");NotebookToolSettings.width(note,false,Float.NaN);check(NotebookToolSettings.penWidth(browser)==NotebookPenConfig.DEFAULT_WIDTH,\"invalid persisted width restores a usable default\");",
            "Context onlyBrowser=new Context();onlyBrowser.getSharedPreferences(\"notebook.NotebookBrowserActivity\",0).edit().putFloat(\"penWidth\",3.3f).apply();check(NotebookToolSettings.penWidth(onlyBrowser)==3.3f,\"first-time browser-note users keep their existing width\");check(NotebookToolSettings.eraserWidth(new Context())==NotebookWidthRange.ERASER.defaultWidth,\"fresh installs use normal defaults\");",
            "System.out.println(\"NotebookToolSettings: \"+checks+\" checks passed (production migration, shared writes and reopening).\");}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.ToolSettingsFixture", fixture);
    }
}
