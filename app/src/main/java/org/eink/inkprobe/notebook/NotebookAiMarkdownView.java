package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.*;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import org.json.*;

/** Visible chat uses the same sanitized, offline Markdown/MathJax engine as note blocks. */
final class NotebookAiMarkdownView {
    private static final String ORIGIN="https://appassets.androidplatform.net";
    final WebView view;
    private final Activity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Consumer<String> failure;
    private final NotebookWritingGate writingGate;
    private final Runnable renderAfterWriting=this::render;
    private final Runnable pollAfterWriting=this::poll;
    private final Runnable infoAfterWriting=this::consumeInfo;
    private String pending="[]",shown;
    private String pendingInfo;
    private boolean ready,rendering,closed;
    private long deadline;
    private long pollingPausedAt=-1;
    private final Runnable poll=this::poll;
    NotebookAiMarkdownView(Activity activity,Consumer<String> failure) {
        this.activity=activity; this.failure=failure; view=new WebView(activity); view.setBackgroundColor(android.graphics.Color.WHITE);
        writingGate=NotebookWritingGate.from(activity);
        view.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
        view.setContentDescription("AI chat messages");
        WebSettings settings=view.getSettings(); settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setBlockNetworkLoads(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false); settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setJavaScriptCanOpenWindowsAutomatically(false); settings.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view,false);
        view.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsAlert(WebView v,String url,String text,JsResult result) { result.cancel(); return true; }
            @Override public boolean onJsConfirm(WebView v,String url,String text,JsResult result) { result.cancel(); return true; }
            @Override public boolean onJsPrompt(WebView v,String url,String text,String value,JsPromptResult result) { result.cancel(); return true; }
        });
        view.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request) { return asset(request.getUrl()); }
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request) { return true; }
            @Override public void onPageFinished(WebView v,String url) { if (!closed&&url.equals(ORIGIN+"/markdown/chat.html")) { ready=true; render(); } }
            @Override public void onReceivedError(WebView v,WebResourceRequest request,WebResourceError error) {
                if (request.isForMainFrame()) failure.accept("Cannot open the offline chat renderer. Close and reopen AI chat.");
            }
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail) {
                ready=false; main.removeCallbacks(poll); failure.accept("Chat display stopped. Your saved conversation is retained; close and reopen AI chat."); return true;
            }
        });
        view.loadUrl(ORIGIN+"/markdown/chat.html");
    }
    void messages(List<NotebookAiConversation.Message> messages) {
        JSONArray array=new JSONArray();
        try {
            for (NotebookAiConversation.Message message:messages) {
                JSONArray images=new JSONArray();
                for (NotebookAiAttachment image:message.images) images.put(new JSONObject().put("name",image.name)
                    .put("kind",image.isPdf()?"pdf":"image").put("data",image.isPdf()?"":image.dataUrl()));
                array.put(new JSONObject().put("role",message.role).put("content",message.content).put("images",images));
            }
        }
        catch (JSONException error) { throw new IllegalStateException(error); }
        pending=array.toString(); render();
    }
    private void render() {
        if (closed||!ready||rendering||pending.equals(shown)) return;
        if (writingGate!=null&&!writingGate.isQuiet()) { writingGate.defer(renderAfterWriting); return; }
        rendering=true; shown=pending; pollingPausedAt=-1;deadline=SystemClock.uptimeMillis()+60000;
        view.evaluateJavascript("window.ScriflowMarkdown&&window.ScriflowMarkdown.chat("+pending+");",null);
        main.postDelayed(poll,100);
    }
    private void poll() {
        if (closed||!rendering) return;
        if (writingGate!=null&&!writingGate.isQuiet()) {
            if(pollingPausedAt<0)pollingPausedAt=SystemClock.uptimeMillis();
            writingGate.defer(pollAfterWriting);return;
        }
        if(pollingPausedAt>=0){deadline+=SystemClock.uptimeMillis()-pollingPausedAt;pollingPausedAt=-1;}
        if (SystemClock.uptimeMillis()>deadline) { rendering=false; failure.accept("Chat formatting took too long. Copy reply retains the Markdown source."); return; }
        view.evaluateJavascript("window.ScriflowMarkdown?JSON.stringify(window.ScriflowMarkdown.info()):null",value->{
            if (closed) return;
            pendingInfo=value;
            if(writingGate!=null&&!writingGate.isQuiet()){
                if(pollingPausedAt<0)pollingPausedAt=SystemClock.uptimeMillis();
                writingGate.defer(infoAfterWriting);
            }else consumeInfo();
        });
    }
    private void consumeInfo(){
        if(closed||pendingInfo==null)return;
        String value=pendingInfo;pendingInfo=null;
        if(pollingPausedAt>=0){deadline+=SystemClock.uptimeMillis()-pollingPausedAt;pollingPausedAt=-1;}
        try {
            Object decoded=new JSONTokener(value).nextValue();
            JSONObject info=decoded instanceof String?new JSONObject((String)decoded):new JSONObject();
            String phase=info.optString("phase");
            if (phase.equals("ready")||phase.equals("error")) {
                rendering=false;
                if (phase.equals("error")) failure.accept("Could not format chat. Copy reply retains the Markdown source.");
                else if (!info.optString("warning").isEmpty()) failure.accept(info.optString("warning"));
                // Preserve the reader's position until an actual new turn arrives.
                view.evaluateJavascript("window.scrollTo(0,document.body.scrollHeight)",null); render();
            } else main.postDelayed(poll,100);
        } catch (JSONException error) { rendering=false; failure.accept("Could not read the chat display result"); }
    }
    private WebResourceResponse asset(Uri uri) {
        String path=uri.getPath();
        if ("https".equals(uri.getScheme())&&"appassets.androidplatform.net".equals(uri.getHost())&&path!=null
                &&path.startsWith("/markdown/")&&!path.contains("..")&&!path.contains("\\")&&uri.getQuery()==null) {
            try {
                String name=path.substring(1),mime=name.endsWith(".js")?"text/javascript":name.endsWith(".css")?"text/css"
                    :name.endsWith(".html")?"text/html":name.endsWith(".ttf")?"font/ttf":name.endsWith(".otf")?"font/otf":"application/octet-stream";
                WebResourceResponse response=new WebResourceResponse(mime,mime.startsWith("text/")?"UTF-8":null,activity.getAssets().open(name));
                response.setResponseHeaders(Collections.singletonMap("Cache-Control","no-store")); return response;
            } catch (IOException ignored) { }
        }
        return new WebResourceResponse("text/plain","UTF-8",404,"Not found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));
    }
    void close() {
        if (closed) return; closed=true; main.removeCallbacks(poll);
        if(writingGate!=null){writingGate.cancel(renderAfterWriting);writingGate.cancel(pollAfterWriting);writingGate.cancel(infoAfterWriting);}
        pendingInfo=null;view.stopLoading(); view.destroy();
    }
}
