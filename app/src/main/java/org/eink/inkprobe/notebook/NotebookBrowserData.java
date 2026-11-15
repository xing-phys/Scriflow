package org.eink.inkprobe.notebook;

import android.content.Context;
import android.os.*;
import android.os.HandlerThread;
import java.net.URI;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;
import org.mozilla.geckoview.*;

/** Explicit browser-data operations; document files and bookmark storage are never deletion targets. */
final class NotebookBrowserData {
    interface Result<T> { void complete(T result,String error); }
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final Set<NotebookGeckoPage> pages=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<String,Result<Map<String,Integer>>> waiting=new LinkedHashMap<>();
    private static WebExtension.Port port;
    private static Handler worker;
    private static boolean clearing;
    private static final Set<Runnable> owners=Collections.newSetFromMap(new IdentityHashMap<>());
    static void owner(Runnable reset){owners.add(reset);}
    static void removeOwner(Runnable reset){owners.remove(reset);}
    static boolean clearing(){return clearing;}
    static long revision(Context context){return context.getSharedPreferences("browser-data",Context.MODE_PRIVATE).getLong("clearRevision",0);}
    static int openPages(){return pages.size();}
    static void register(NotebookGeckoPage page){pages.add(page);}
    static void unregister(NotebookGeckoPage page){pages.remove(page);}
    static int cookieMode(Context context){return Math.max(0,Math.min(2,context.getSharedPreferences("browser-data",Context.MODE_PRIVATE).getInt("cookies",0)));}
    static void cookies(Context context,int choice){context.getSharedPreferences("browser-data",Context.MODE_PRIVATE).edit().putInt("cookies",choice).apply();applyCookies(context,NotebookBrowserRuntime.get(context));}
    static void applyCookies(Context context,GeckoRuntime runtime){int value=cookieMode(context);runtime.getSettings().getContentBlocking().setCookieBehavior(value==1?ContentBlocking.CookieBehavior.ACCEPT_ALL:value==2?ContentBlocking.CookieBehavior.ACCEPT_NONE:ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY);}
    static void attach(WebExtension extension){
        extension.setMessageDelegate(new WebExtension.MessageDelegate(){@Override public void onConnect(WebExtension.Port connected){
            WebExtension.MessageSender sender=connected.sender;

            if(sender.session!=null||sender.environmentType!=WebExtension.MessageSender.ENV_TYPE_EXTENSION||!NotebookBrowserRuntime.EXTENSION_ID.equals(sender.webExtension.id)){connected.disconnect();return;}
            port=connected;connected.setDelegate(new WebExtension.PortDelegate(){
                @Override public void onPortMessage(Object data,WebExtension.Port owner){
                    if(owner!=port||!(data instanceof JSONObject))return;JSONObject message=(JSONObject)data;Result<Map<String,Integer>> callback=waiting.remove(message.optString("id"));if(callback==null)return;
                    if(!message.optString("type").equals("sites")){callback.complete(null,"Could not read browser cookies.");return;}
                    Map<String,Integer> sites=new TreeMap<>();JSONArray values=message.optJSONArray("sites");if(values!=null)for(int i=0;i<Math.min(2000,values.length());i++){JSONObject value=values.optJSONObject(i);if(value==null)continue;try{String host=host(value.optString("host"));sites.put(host,Math.max(0,value.optInt("count")));}catch(IllegalArgumentException ignored){}}
                    callback.complete(sites,null);
                }
                @Override public void onDisconnect(WebExtension.Port owner){if(port==owner){port=null;for(Result<Map<String,Integer>> callback:new ArrayList<>(waiting.values()))callback.complete(null,"Browser data connection closed. Tap Refresh to retry.");waiting.clear();}}
            });
            for(String id:waiting.keySet())request(id);
        }},"scriflow_data");
    }
    static void sites(Context context,Result<Map<String,Integer>> callback){
        String id=UUID.randomUUID().toString();waiting.put(id,callback);
        NotebookBrowserRuntime.extension(context).accept(extension->{if(waiting.containsKey(id)&&port!=null)request(id);},error->{Result<Map<String,Integer>> pending=waiting.remove(id);if(pending!=null)pending.complete(null,"Could not start browser data settings.");});
        MAIN.postDelayed(()->{Result<Map<String,Integer>> pending=waiting.remove(id);if(pending!=null)pending.complete(null,"Cookie list timed out. Tap Refresh to retry.");},15000);
    }
    private static void request(String id){try{port.postMessage(new JSONObject().put("type","sites").put("id",id));}catch(JSONException error){throw new IllegalStateException(error);}}
    static String host(String input){String value=input==null?"":input.trim();if(value.startsWith("."))value=value.substring(1);String host=URI.create(NotebookBrowserAddress.normalize(value)).getHost();if(host==null||host.isEmpty())throw new IllegalArgumentException("Enter a website address or host.");return host;}
    static void clear(Context context,String host,boolean cookies,boolean cache,boolean history,Result<Void> callback){
        if(clearing){callback.complete(null,"Browser data is already being cleared.");return;}
        final String selected;try{selected=host==null?null:host(host);}catch(IllegalArgumentException error){callback.complete(null,error.getMessage());return;}
        if(!cookies&&!cache&&!history){callback.complete(null,"Choose data to clear.");return;}
        GeckoRuntime runtime=NotebookBrowserRuntime.get(context);StorageController storage=runtime.getStorageController();clearing=true;NotebookBrowserPreviews.invalidate();
        // Close every live engine before clearing; paused pages can also hold script timers.
        for(Runnable reset:new ArrayList<>(owners))reset.run();
        for(NotebookGeckoPage page:new ArrayList<>(pages))page.retireForDataClear();
        long flags=(cookies?StorageController.ClearFlags.COOKIES|StorageController.ClearFlags.DOM_STORAGES|StorageController.ClearFlags.AUTH_SESSIONS:0)|(cache?StorageController.ClearFlags.ALL_CACHES:0);
        NotebookWebsiteTasks.run(()->{NotebookBrowserPreviews.clear(context);if(!context.getSharedPreferences("browser-data",Context.MODE_PRIVATE).edit().putLong("clearRevision",revision(context)+1).commit())throw new java.io.IOException("Could not invalidate old browser sessions.");if(!context.getSharedPreferences("browser-tabs",Context.MODE_PRIVATE).edit().remove("tabs").commit())throw new java.io.IOException("Could not clear restorable browser tabs.");if(history)new NotebookBrowserLibrary(context.getFilesDir()).clearHistory();return null;},(value,error)->{
            if(error!=null){clearing=false;callback.complete(null,error.getMessage());return;}
            if(flags==0){clearing=false;callback.complete(null,null);return;}
            if(worker==null){HandlerThread thread=new HandlerThread("browser-data");thread.start();worker=new Handler(thread.getLooper());}
            worker.post(()->{
                try { GeckoResult<Void> result=selected==null?storage.clearData(flags):storage.clearDataFromHost(selected,flags);
                result.withHandler(MAIN).accept(done->{clearing=false;callback.complete(null,null);},failure->{clearing=false;callback.complete(null,"Could not finish clearing browser data. Please retry.");}); }
                catch(RuntimeException failure){MAIN.post(()->{clearing=false;callback.complete(null,"Could not clear browser data. Please retry.");});}
            });
        });
    }
    private NotebookBrowserData(){}
}
