package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;

/** Read-only compatibility for MHTML copies saved before the bundled engine. Never browses online. */
final class NotebookLegacyWebPage extends NotebookBrowserEngine {
    private final WebView view;
    private final String archive;
    NotebookLegacyWebPage(Context context, String archive, Listener listener) {
        super(context, listener); this.archive = NotebookBrowserAddress.withoutFragment(archive);
        view = new WebView(context); view.setBackgroundColor(Color.WHITE); view.setOverScrollMode(OVER_SCROLL_NEVER);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(false); settings.setDomStorageEnabled(false); settings.setBlockNetworkLoads(true);
        settings.setAllowFileAccess(true); settings.setAllowContentAccess(false); settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(true); settings.setDisplayZoomControls(false); settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true); settings.setTextZoom(100); settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        view.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView v, String title) { listener.onTitle(NotebookLegacyWebPage.this, title); }
        });
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                return listener.overrideNavigation(NotebookLegacyWebPage.this, request.getUrl().toString(), request.isForMainFrame(), request.hasGesture());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (NotebookBrowserAddress.isEmbeddedScheme(request.getUrl().getScheme()) || allowed(url)) return null;
                return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageStarted(WebView v, String url, Bitmap icon) { listener.onPageStarted(NotebookLegacyWebPage.this, url); }
            @Override public void onPageFinished(WebView v, String url) { listener.onPageFinished(NotebookLegacyWebPage.this, url); }
            @Override public void doUpdateVisitedHistory(WebView v, String url, boolean reload) { listener.onHistoryChanged(NotebookLegacyWebPage.this, url); }
            @Override public void onReceivedError(WebView v, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) listener.onError(NotebookLegacyWebPage.this, "Couldn’t read this legacy offline copy. Open the original online to save it again.");
            }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail detail) {
                listener.onRendererGone(NotebookLegacyWebPage.this); return true;
            }
        });
        addView(view, new LayoutParams(-1, -1));
    }
    private boolean allowed(String url) { return archive.equals(NotebookBrowserAddress.withoutFragment(url)); }
    private History history(WebBackForwardList list) {
        if (list == null) return null;
        ArrayList<String> urls = new ArrayList<>();
        for (int i = 0; i < list.getSize(); i++) urls.add(list.getItemAtIndex(i).getUrl());
        return new History(urls, list.getCurrentIndex());
    }
    @Override String getUrl() { return view.getUrl(); }
    @Override String getTitle() { return view.getTitle(); }
    @Override void loadUrl(String url) { if (allowed(url)) view.loadUrl(url); }
    @Override void reload() { view.reload(); }
    @Override boolean canGoBack() { return view.canGoBack(); }
    @Override boolean canGoForward() { return view.canGoForward(); }
    @Override void goBack() { view.goBack(); }
    @Override void goForward() { view.goForward(); }
    @Override void clearHistory() { view.clearHistory(); }
    @Override History copyBackForwardList() { return history(view.copyBackForwardList()); }
    @Override History restoreState(Bundle saved) { return "gecko".equals(saved.getString("engine")) ? null : history(view.restoreState(saved)); }
    @Override void saveState(Bundle saved) { view.saveState(saved); saved.putString("engine", "legacy"); }
    @Override void saveWebArchive(String path, boolean automatic, Consumer<String> callback) { callback.accept(null); }
    @Override void cancelCapture() { }
    @Override void stopLoading() { view.stopLoading(); }
    @Override void onResume() { view.onResume(); }
    @Override void onPause() { view.onPause(); }
    @Override void destroy() { removeView(view); view.stopLoading(); view.destroy(); }
    @Override boolean htmlCapture() { return false; }
    @Override int pageScrollX() { return view.getScrollX(); }
    @Override int pageScrollY() { return view.getScrollY(); }
    @Override void scrollPageTo(int x, int y) { view.scrollTo(x, y); }
}
