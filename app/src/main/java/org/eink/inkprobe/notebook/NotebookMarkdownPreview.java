package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;
import android.webkit.*;
import android.widget.FrameLayout;
import org.json.*;

/** A non-interactive, temporary browser layout used only while a Markdown block is resized. */
final class NotebookMarkdownPreview {
    private static final String ORIGIN="https://appassets.androidplatform.net";
    final WebView view;
    private final Activity activity;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable changed,poll=this::poll;
    private final Runnable startAfterWriting=this::startPreview;
    private final NotebookWritingGate writingGate;
    private final NotebookMarkdown source;
    private NotebookFigure original,current;
    private NotebookViewport viewport;
    private int page,width=640,sentWidth=-1;
    private float scroll;
    private boolean closed,ready,requested,pageReady,started;
    private long deadline,pollingPausedAt=-1;

    NotebookMarkdownPreview(Activity activity,FrameLayout parent,NotebookMarkdown source,Runnable changed) {
        this.activity=activity;this.source=source;this.changed=changed;writingGate=NotebookWritingGate.from(activity);
        view=new WebView(activity);view.setVisibility(View.INVISIBLE);view.setBackgroundColor(Color.TRANSPARENT);
        view.setFocusable(false);view.setFocusableInTouchMode(false);view.setClickable(false);view.setLongClickable(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        view.setVerticalScrollBarEnabled(false);view.setHorizontalScrollBarEnabled(false);view.setOverScrollMode(View.OVER_SCROLL_NEVER);
        parent.addView(view,0,new FrameLayout.LayoutParams(1,1));
        WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);
        settings.setTextZoom(100);
        settings.setAllowContentAccess(false);settings.setBlockNetworkLoads(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setDomStorageEnabled(false);settings.setDatabaseEnabled(false);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);settings.setSupportMultipleWindows(false);
        view.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onJsAlert(WebView v,String url,String message,JsResult result){result.cancel();return true;}
            @Override public boolean onJsConfirm(WebView v,String url,String message,JsResult result){result.cancel();return true;}
            @Override public boolean onJsPrompt(WebView v,String url,String message,String value,JsPromptResult result){result.cancel();return true;}
        });
        view.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){return NotebookMarkdownRenderer.asset(activity,request.getUrl());}
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){return true;}
            @Override public void onPageFinished(WebView v,String url){
                if(closed||!url.equals(ORIGIN+"/markdown/preview.html"))return;
                pageReady=true;startPreview();
            }
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){close();changed.run();return true;}
        });
        view.loadUrl(ORIGIN+"/markdown/preview.html");
    }
    void update(NotebookFigure original,NotebookFigure current,NotebookViewport viewport,int page,float scroll) {
        if(closed)return;
        this.original=original;this.current=current;this.viewport=viewport;this.page=page;this.scroll=scroll;
        requested=true;width=NotebookFigureGeometry.markdownWidth(original,current);
        if(ready)place();else startPreview();
    }
    private void startPreview() {
        if(closed||!pageReady||started)return;
        if(!requested&&writingGate!=null&&!writingGate.isQuiet()){writingGate.defer(startAfterWriting);return;}
        if(writingGate!=null)writingGate.cancel(startAfterWriting);
        started=true;deadline=SystemClock.uptimeMillis()+60000;
        view.evaluateJavascript("window.ScriflowMarkdown&&window.ScriflowMarkdown.preview("+JSONObject.quote(source.source)+","+source.sourceMode+",640,"+NotebookSettings.markdownTextSize(activity)+");",null);
        main.postDelayed(poll,50);
    }
    private void poll() {
        if(closed||ready)return;
        if(!requested&&writingGate!=null&&!writingGate.isQuiet()){
            if(pollingPausedAt<0)pollingPausedAt=SystemClock.uptimeMillis();writingGate.defer(poll);return;
        }
        if(pollingPausedAt>=0){deadline+=SystemClock.uptimeMillis()-pollingPausedAt;pollingPausedAt=-1;}
        if(SystemClock.uptimeMillis()>deadline)return;
        view.evaluateJavascript("window.ScriflowMarkdown?JSON.stringify(window.ScriflowMarkdown.info()):null",value->{
            if(closed)return;
            try {
                Object decoded=new JSONTokener(value).nextValue();
                if(decoded instanceof String){String phase=new JSONObject((String)decoded).optString("phase");
                    if(phase.equals("ready")){ready=true;if(requested){place();changed.run();}return;}
                    if(phase.equals("error"))return;
                }
            } catch(JSONException|RuntimeException ignored) { }
            main.postDelayed(poll,60);
        });
    }
    private void place() {
        if(!requested||!ready||current==null||viewport==null)return;
        if(sentWidth!=width){sentWidth=width;view.evaluateJavascript("window.ScriflowMarkdown.previewWidth("+width+");",null);}
        float density=activity.getResources().getDisplayMetrics().density;
        float pageScale=viewport.scale(page),factor=current.width*pageScale/(width*density);
        int rawWidth=Math.max(1,Math.round(width*density));
        int rawHeight=Math.max(1,Math.round(Math.min(16384f,width*current.height/current.width)*density));
        FrameLayout.LayoutParams size=(FrameLayout.LayoutParams)view.getLayoutParams();
        if(size.width!=rawWidth||size.height!=rawHeight){size.width=rawWidth;size.height=rawHeight;view.setLayoutParams(size);}
        float centerX=viewport.pageLeft(page)+current.centerX()*pageScale;
        float centerY=viewport.pageTop(page,scroll)+current.centerY()*pageScale;
        view.setPivotX(rawWidth/2f);view.setPivotY(rawHeight/2f);
        view.setX(centerX-rawWidth/2f);view.setY(centerY-rawHeight/2f);
        view.setScaleX(factor);view.setScaleY(factor);view.setRotation(current.angle);
        view.setVisibility(View.VISIBLE);
    }
    boolean showing(){return requested&&ready&&!closed;}
    boolean matches(NotebookMarkdown value){return value!=null&&source.sourceMode==value.sourceMode&&source.source.equals(value.source);}
    void hide(){requested=false;if(!closed)view.setVisibility(View.INVISIBLE);}
    void close(){
        if(closed)return;closed=true;main.removeCallbacks(poll);
        if(writingGate!=null){writingGate.cancel(startAfterWriting);writingGate.cancel(poll);}
        view.stopLoading();
        if(view.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)view.getParent()).removeView(view);
        view.destroy();
    }
}
