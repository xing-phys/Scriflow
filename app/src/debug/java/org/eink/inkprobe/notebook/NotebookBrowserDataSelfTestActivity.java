package org.eink.inkprobe.notebook;

import android.os.Bundle;
import android.widget.TextView;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.json.*;
import org.mozilla.geckoview.*;

/** Isolated emulator fixture: synthetic cookies, real engine deletion and document preservation. */
public final class NotebookBrowserDataSelfTestActivity extends NotebookDisplayActivity {
    private final List<String> checks=new ArrayList<>();
    private TextView label;
    private byte[] libraryBytes;
    private NotebookWebsites.Saved saved;
    private int initialPages;
    @Override public void onCreate(Bundle state){super.onCreate(state);label=new TextView(this);label.setText("Checking browser data…");setContentView(label);initialPages=NotebookBrowserData.openPages();new File(getFilesDir(),"browser-data-selftest.json").delete();
        try{
            NotebookBrowserLibrary library=new NotebookBrowserLibrary(getFilesDir());library.initialize();library.put(null,"Data fixture bookmark","https://first.scriflow-test.example/","",true);
            libraryBytes=Files.readAllBytes(new File(getFilesDir(),"browser-library.nbr").toPath());
            NotebookWebsites store=new NotebookWebsites(getFilesDir());NotebookWebsites.Pending pending=store.begin(NotebookWebsites.Format.HTML);Files.write(pending.archive.toPath(),"<!DOCTYPE html>\n<!-- Saved by Scriflow -->\n<p>Keep this offline copy</p>".getBytes(StandardCharsets.UTF_8));saved=store.finish(pending,"Data fixture offline","https://first.scriflow-test.example/");
        }catch(Throwable error){fail(error.toString());return;}
        NotebookBrowserRuntime.extension(this).accept(main->NotebookBrowserRuntime.get(this).getWebExtensionController().ensureBuiltIn("resource://android/assets/browser-data-fixture/","browser-data-test@scriflow.local").accept(extension->{
            extension.setMessageDelegate(new WebExtension.MessageDelegate(){@Override public void onConnect(WebExtension.Port port){
                android.util.Log.d("BrowserDataTest","Fixture port connected");
                if(port.sender.session!=null||!port.sender.webExtension.id.equals("browser-data-test@scriflow.local")){port.disconnect();return;}
                port.setDelegate(new WebExtension.PortDelegate(){@Override public void onPortMessage(Object data,WebExtension.Port owner){if(!(data instanceof JSONObject)||!((JSONObject)data).optString("type").equals("ready")){fail(String.valueOf(data));return;}afterSeed();}});
                try{port.postMessage(new JSONObject().put("type",getIntent().getBooleanExtra("inspectOnly",false)?"inspect":"seed"));}catch(Throwable error){fail(error.toString());}
            }},"scriflow_test");
        },error->fail(error.toString())),error->fail(error.toString()));
    }
    private void afterSeed(){android.util.Log.d("BrowserDataTest","Fixture cookies ready");NotebookBrowserData.sites(this,(sites,error)->{
        if(error!=null){fail(error);return;}try{check(sites.getOrDefault("first.scriflow-test.example",0)==1&&sites.getOrDefault("second.scriflow-test.example",0)==1,"real engine cookies appear in the app data list");
            if(getIntent().getBooleanExtra("seedOnly",false)||getIntent().getBooleanExtra("inspectOnly",false)){finishResult("passed");return;}
            NotebookBrowserData.clear(this,null,false,true,false,(value,failure)->{if(failure!=null){fail(failure);return;}afterCache();});
        }catch(Throwable failure){fail(failure.toString());}
    });}
    private void afterCache(){NotebookBrowserData.sites(this,(sites,error)->{try{
        if(error!=null)throw new IOException(error);check(NotebookBrowserData.openPages()==0,"data clearing retires foreground and inactive browser pages");check(sites.getOrDefault("first.scriflow-test.example",0)==1&&sites.getOrDefault("second.scriflow-test.example",0)==1,"cache-only deletion keeps persistent login cookies");check(Arrays.equals(libraryBytes,Files.readAllBytes(new File(getFilesDir(),"browser-library.nbr").toPath())),"cache-only deletion preserves bookmark and history bytes");
        NotebookBrowserData.clear(this,"first.scriflow-test.example",true,true,false,(value,failure)->{if(failure!=null){fail(failure);return;}afterHost();});
    }catch(Throwable failure){fail(failure.toString());}});}
    private void afterHost(){NotebookBrowserData.sites(this,(sites,error)->{try{
        if(error!=null)throw new IOException(error);check(!sites.containsKey("first.scriflow-test.example")&&sites.getOrDefault("second.scriflow-test.example",0)==1,"host deletion signs out only the selected host");
        NotebookBrowserData.clear(this,null,true,false,false,(value,failure)->{if(failure!=null){fail(failure);return;}afterAll();});
    }catch(Throwable failure){fail(failure.toString());}});}
    private void afterAll(){NotebookBrowserData.sites(this,(sites,error)->{try{
        if(error!=null)throw new IOException(error);check(!sites.containsKey("first.scriflow-test.example")&&!sites.containsKey("second.scriflow-test.example"),"global cookie deletion removes both synthetic login cookies");
        int original=NotebookBrowserData.cookieMode(this);for(int i=0;i<3;i++){NotebookBrowserData.cookies(this,i);check(NotebookBrowserData.cookieMode(this)==i,"cookie preference "+i+" is saved");}NotebookBrowserData.cookies(this,original);
        NotebookWebsites store=new NotebookWebsites(getFilesDir());check(store.get(saved.id).bytes==saved.bytes,"offline snapshot remains readable after all engine data operations");check(Arrays.equals(libraryBytes,Files.readAllBytes(new File(getFilesDir(),"browser-library.nbr").toPath())),"cookie clearing preserves bookmark and history bytes");finishResult("passed");
    }catch(Throwable failure){fail(failure.toString());}});}
    private void check(boolean condition,String description){if(!condition)throw new AssertionError(description);checks.add(description);}
    private void fail(String error){finishResult("failed: "+error);}
    private void finishResult(String outcome){try{JSONObject report=new JSONObject().put("result",outcome).put("checks",new JSONArray(checks)).put("initialOpenPages",initialPages);try(FileOutputStream output=new FileOutputStream(new File(getFilesDir(),"browser-data-selftest.json"))){output.write(report.toString(2).getBytes(StandardCharsets.UTF_8));}label.setText(report.toString(2));}catch(Throwable error){android.util.Log.e("BrowserDataTest","Could not write result",error);}}
}
