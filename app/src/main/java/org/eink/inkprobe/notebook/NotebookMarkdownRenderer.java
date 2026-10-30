package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.*;
import android.widget.FrameLayout;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** A short-lived, offscreen compiler. Native ink never becomes a WebView. */
final class NotebookMarkdownRenderer {
    interface Callback { void complete(NotebookMarkdown block, IOException error); }
    private static final String ORIGIN = "https://appassets.androidplatform.net";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "markdown-result"));
    private final Activity activity;
    private final FrameLayout parent;
    private final WebView view;
    private final Callback callback;
    private final String source;
    private final boolean sourceMode;
    private final int width;
    private final Runnable poll = this::poll;
    private boolean closed, started;
    private long deadline;
    private JSONObject info;
    private String warning = "";

    NotebookMarkdownRenderer(Activity activity, FrameLayout parent, String source, boolean sourceMode, int width, Callback callback) {
        this.activity = activity; this.parent = parent; this.source = source; this.sourceMode = sourceMode;
        this.width = width; this.callback = callback;
        view = new WebView(activity); view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        view.setFocusable(false); view.setFocusableInTouchMode(false); view.setLongClickable(false);
        // Outside the hit-testable page, but attached so browser layout/fonts remain available.
        int pixels = Math.max(1, activity.getResources().getDisplayMetrics().widthPixels);
        view.setTranslationX(-pixels - 32); view.setAlpha(0);
        parent.addView(view, new FrameLayout.LayoutParams(pixels, 1024));
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true); settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setDomStorageEnabled(false); settings.setDatabaseEnabled(false); settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setJavaScriptCanOpenWindowsAutomatically(false); settings.setSupportMultipleWindows(false);
        view.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onJsAlert(WebView v, String url, String message, JsResult result) { result.cancel(); return true; }
            @Override public boolean onJsConfirm(WebView v, String url, String message, JsResult result) { result.cancel(); return true; }
            @Override public boolean onJsPrompt(WebView v, String url, String message, String value, JsPromptResult result) { result.cancel(); return true; }
        });
        view.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
                return asset(request.getUrl());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) { return true; }
            @Override public void onPageFinished(WebView v, String url) {
                if (closed || started || !url.equals(ORIGIN + "/markdown/index.html")) return;
                started = true; deadline = SystemClock.uptimeMillis() + 60000;
                view.evaluateJavascript("window.ScriflowMarkdown&&window.ScriflowMarkdown.render("+JSONObject.quote(source)+","+width+");", null);
                main.removeCallbacks(poll); main.post(poll);
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) fail("Cannot open the offline Markdown renderer");
            }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail detail) {
                fail("Markdown rendering stopped. Your source draft is retained."); return true;
            }
        });
        deadline = SystemClock.uptimeMillis() + 60000;
        view.loadUrl(ORIGIN + "/markdown/index.html");
        main.postDelayed(poll, 100);
    }

    private WebResourceResponse asset(Uri uri) {
        String path = uri.getPath();
        if (!"https".equals(uri.getScheme()) || !"appassets.androidplatform.net".equals(uri.getHost())
                || path == null || !path.startsWith("/markdown/") || path.contains("..") || path.contains("\\")
                || uri.getQuery() != null) return missing();
        try {
            String name = path.substring(1), mime = name.endsWith(".js") ? "text/javascript" : name.endsWith(".css") ? "text/css"
                : name.endsWith(".html") ? "text/html" : name.endsWith(".ttf") ? "font/ttf" : name.endsWith(".otf") ? "font/otf" : "application/octet-stream";
            WebResourceResponse response = new WebResourceResponse(mime, mime.startsWith("text/") ? "UTF-8" : null, activity.getAssets().open(name));
            response.setResponseHeaders(Collections.singletonMap("Cache-Control", "no-store")); return response;
        } catch (IOException error) { return missing(); }
    }
    private WebResourceResponse missing() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not found", Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
    }
    private void poll() {
        if (closed) return;
        if (SystemClock.uptimeMillis() > deadline) { fail("Markdown rendering took too long. Your source draft is retained."); return; }
        if (!started) { main.postDelayed(poll, 100); return; }
        view.evaluateJavascript("window.ScriflowMarkdown?JSON.stringify(window.ScriflowMarkdown.info()):null", value -> {
            if (closed) return;
            try {
                Object decoded = new JSONTokener(value).nextValue();
                if (!(decoded instanceof String)) { main.postDelayed(poll, 100); return; }
                info = new JSONObject((String) decoded); String phase = info.optString("phase");
                if (phase.equals("error")) { fail(info.optString("message", "Cannot render Markdown")); return; }
                if (phase.equals("ready")) {
                    warning = info.optString("warning", "");
                    int renderedLength = info.getInt("renderedLength"), sourceLength = info.getInt("sourceLength");
                    int limit = (NotebookMarkdown.MAX_PDF_BYTES + 2) / 3 * 4;
                    if (renderedLength < 1 || sourceLength < 1 || renderedLength > limit || sourceLength > limit)
                        throw new IOException("Markdown appearance exceeds its size limit");
                    read(false, renderedLength, new StringBuilder(renderedLength), rendered ->
                        read(true, sourceLength, new StringBuilder(sourceLength), code -> decode(rendered, code)));
                } else main.postDelayed(poll, 60);
            } catch (IOException | JSONException | RuntimeException error) { fail("Cannot read Markdown rendering result"); }
        });
    }
    private interface TextCallback { void ready(String text); }
    private void read(boolean code, int length, StringBuilder text, TextCallback complete) {
        if (closed) return;
        if (SystemClock.uptimeMillis() > deadline) { fail("Markdown rendering took too long. Your source draft is retained."); return; }
        int count = Math.min(65536, length - text.length());
        view.evaluateJavascript("window.ScriflowMarkdown.chunk("+code+","+text.length()+","+count+")", value -> {
            if (closed) return;
            try {
                Object decoded = new JSONTokener(value).nextValue();
                if (!(decoded instanceof String) || ((String)decoded).length() != count) throw new JSONException("Invalid Markdown result chunk");
                text.append((String)decoded);
                if (text.length() == length) complete.ready(text.toString()); else read(code, length, text, complete);
            } catch (JSONException | RuntimeException error) { fail("Cannot read Markdown vector data"); }
        });
    }
    private void decode(String rendered, String code) {
        final JSONObject dimensions = info;
        worker.execute(() -> {
            NotebookMarkdown block = null; IOException problem = null;
            try {
                block = new NotebookMarkdown(source, sourceMode, (float)dimensions.getDouble("rw"), (float)dimensions.getDouble("rh"),
                    Base64.decode(rendered, Base64.NO_WRAP), (float)dimensions.getDouble("sw"), (float)dimensions.getDouble("sh"), Base64.decode(code, Base64.NO_WRAP));
                NotebookMarkdownRasterizer.validate(block);
            } catch (JSONException | IOException | RuntimeException | OutOfMemoryError error) { problem = new IOException("Cannot prepare Markdown vectors", error); }
            final NotebookMarkdown result = block; final IOException error = problem;
            main.post(() -> { if (!closed) { close(); callback.complete(error == null ? result : null, error); } });
        });
    }
    private void fail(String message) {
        if (Looper.myLooper() != Looper.getMainLooper()) { main.post(() -> fail(message)); return; }
        if (closed) return; close(); callback.complete(null, new IOException(message));
    }
    void close() {
        if (closed) return; closed = true; main.removeCallbacks(poll);
        view.stopLoading(); parent.removeView(view); view.destroy(); worker.shutdownNow();
    }
    String warning() { return warning; }
}
