package org.eink.inkprobe.notebook;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;

/** Single-page Chromium WebView browser, with a separate, network-blocked archive reader. */
public final class NotebookBrowserActivity extends NotebookDisplayActivity {
    static final String SAVED_ID = "savedWebsiteId", INITIAL_URL = "websiteUrl";
    private FrameLayout content;
    private TextView title, status, hint;
    private View saveShield;
    private EditText address;
    private NotebookButton go, previous, next, reload, save;
    private WebView web;
    private NotebookWebsites.Saved savedPage;
    private NotebookWebsites.Pending pendingSave;
    private String archiveUrl, currentUrl;
    private boolean offline, loading, failed, pageReady, saving, resumed;
    private long navigation, saveSequence;
    private Runnable archiveTimeout;
    private int restoreX, restoreY;
    private AlertDialog shownDialog;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        offline = getIntent().hasExtra(SAVED_ID);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setWindowAnimations(0);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE); root.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(button("Bookshelf", NotebookIconDrawable.Icon.LIBRARY, this::bookshelf), new LinearLayout.LayoutParams(dp(112), dp(38)));
        title = label(offline ? "Saved website" : "Browser", 18); title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END); title.setPadding(dp(12), 0, dp(8), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
        header.addView(deviceSettingsIsland(), new LinearLayout.LayoutParams(-2, dp(32))); root.addView(header);

        LinearLayout addressRow = new LinearLayout(this); addressRow.setPadding(0, dp(6), 0, dp(6));
        address = new EditText(this); address.setTextColor(Color.BLACK); address.setTextSize(14);
        address.setSingleLine(true); address.setHint("Website address, e.g. en.wikipedia.org");
        address.setContentDescription("Website address"); address.setSelectAllOnFocus(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO); address.setEnabled(!offline);
        address.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO) { browse(); return true; } return false;
        });
        addressRow.addView(address, new LinearLayout.LayoutParams(0, dp(44), 1));
        go = button("Go", null, this::browse); go.setVisibility(offline ? View.GONE : View.VISIBLE);
        addressRow.addView(go, new LinearLayout.LayoutParams(dp(56), dp(44))); root.addView(addressRow);

        LinearLayout tools = new LinearLayout(this); tools.setGravity(Gravity.CENTER_VERTICAL);
        previous = button("", NotebookIconDrawable.Icon.PREVIOUS, () -> history(false)); previous.setContentDescription("Back");
        next = button("", NotebookIconDrawable.Icon.NEXT, () -> history(true)); next.setContentDescription("Forward");
        reload = button("", NotebookIconDrawable.Icon.RETRY, this::reload); reload.setContentDescription("Reload page");
        tools.addView(previous, new LinearLayout.LayoutParams(dp(42), dp(38)));
        addTool(tools, next, 42); addTool(tools, reload, 42);
        previous.setVisibility(offline ? View.GONE : View.VISIBLE); next.setVisibility(offline ? View.GONE : View.VISIBLE);
        save = button("Save", NotebookIconDrawable.Icon.SAVE, this::savePage);
        save.setContentDescription("Save page for offline reading"); save.setVisibility(offline ? View.GONE : View.VISIBLE);
        addTool(tools, save, 88);
        if (offline) addTool(tools, button("Open online", NotebookIconDrawable.Icon.BROWSER,
            () -> { if (savedPage != null) openOnline(savedPage.url); }), 136);
        addTool(tools, button("Saved websites", NotebookIconDrawable.Icon.FOLDER, this::savedWebsites), 158);
        root.addView(tools);
        status = label(offline ? "Opening offline copy…" : "Enter an address to browse. Save keeps this page for offline reading.", 12);
        status.setMaxLines(2); root.addView(status, new LinearLayout.LayoutParams(-1, dp(38)));
        content = new FrameLayout(this); content.setBackgroundColor(Color.WHITE);
        hint = label("Enter a website address above.\n\nTap Save after the page loads, then find it in Saved websites on the bookshelf.", 17);
        hint.setGravity(Gravity.CENTER); hint.setPadding(dp(22), dp(22), dp(22), dp(22));
        if (offline) hint.setText("Opening offline copy…");
        content.addView(hint, new FrameLayout.LayoutParams(-1, -1));
        saveShield = new View(this); saveShield.setClickable(true); saveShield.setVisibility(View.GONE);
        saveShield.setContentDescription("Saving page"); content.addView(saveShield, new FrameLayout.LayoutParams(-1, -1));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
        if (state != null) { restoreX = state.getInt("scrollX"); restoreY = state.getInt("scrollY"); }
        createWebView();
        if (offline) loadSavedPage(getIntent().getStringExtra(SAVED_ID));
        else if (web != null) {
            Bundle history = state == null ? null : state.getBundle("browserHistory");
            if (history != null && web.restoreState(history) != null) {
                currentUrl = web.getUrl(); address.setText(currentUrl); hint.setVisibility(View.GONE);
            } else {
                String url = state == null ? getIntent().getStringExtra(INITIAL_URL) : state.getString("currentUrl");
                if (NotebookBrowserAddress.isWebUrl(url)) navigate(url);
            }
        }
        updateControls();
    }

    @SuppressLint("SetJavaScriptEnabled") // Live websites need JavaScript; archives never execute it and have no bridge.
    private void createWebView() {
        try {
            web = new WebView(this); web.setBackgroundColor(Color.WHITE); web.setOverScrollMode(View.OVER_SCROLL_NEVER);
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(!offline); settings.setDomStorageEnabled(!offline);
            settings.setBlockNetworkLoads(offline); settings.setAllowFileAccess(offline);
            settings.setAllowContentAccess(false); settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            settings.setSafeBrowsingEnabled(true); settings.setGeolocationEnabled(false);
            settings.setJavaScriptCanOpenWindowsAutomatically(false); settings.setSupportMultipleWindows(false);
            settings.setMediaPlaybackRequiresUserGesture(true); settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false); settings.setUseWideViewPort(true); settings.setLoadWithOverviewMode(true);
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
            web.setWebChromeClient(new WebChromeClient() {
                @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); }
                @Override public void onReceivedTitle(WebView view, String value) {
                    if (!offline && pageReady && !saving && !loading) status.setText(value);
                }
            });
            web.setWebViewClient(new BrowserClient());
            web.setDownloadListener((url, agent, disposition, type, length) -> message("File download",
                "Use another browser to download this file. Save keeps the currently displayed web page for offline reading."));
            content.addView(web, 0, new FrameLayout.LayoutParams(-1, -1));
        } catch (RuntimeException error) {
            web = null; fail("The Android WebView browser is unavailable on this device.");
        }
    }

    private void loadSavedPage(String id) {
        NotebookWebsiteTasks.run(() -> {
            NotebookWebsites store = new NotebookWebsites(getFilesDir());
            NotebookWebsites.Saved entry = store.get(id);
            return new Object[]{entry, store.archive(entry)};
        }, (result, error) -> {
            if (isDestroyed() || isFinishing()) return;
            if (error != null) { fail(error.getMessage()); return; }
            savedPage = (NotebookWebsites.Saved) result[0];
            archiveUrl = Uri.fromFile((File) result[1]).toString();
            address.setText(savedPage.url); title.setText(savedPage.title);
            if (web != null) web.loadUrl(archiveUrl);
            updateControls();
        });
    }

    private void browse() {
        if (offline || saving) return;
        try { navigate(NotebookBrowserAddress.normalize(address.getText().toString())); }
        catch (IllegalArgumentException error) { address.setError(error.getMessage()); }
    }

    private void navigate(String url) {
        if (!NotebookBrowserAddress.isWebUrl(url) || saving) return;
        if (web == null) createWebView();
        if (web == null) return;
        currentUrl = url; address.setError(null); address.setText(url); address.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(address.getWindowToken(), 0);
        pageReady = false; failed = false; loading = true; hint.setVisibility(View.GONE);
        status.setText("Loading…"); web.loadUrl(url); updateControls();
    }

    private void history(boolean forward) {
        if (web == null || saving || offline) return;
        if (forward ? web.canGoForward() : web.canGoBack()) {
            pageReady = false; loading = true; failed = false;
            status.setText("Loading…");
            if (forward) web.goForward(); else web.goBack(); updateControls();
        }
    }

    private void reload() {
        if (saving) return;
        if (web == null) createWebView();
        if (web == null) return;
        if (offline && archiveUrl != null) web.loadUrl(archiveUrl);
        else if (!offline && NotebookBrowserAddress.isWebUrl(currentUrl)) {
            if (currentUrl.equals(web.getUrl())) {
                pageReady = false; failed = false; loading = true; status.setText("Loading…"); web.reload(); updateControls();
            } else navigate(currentUrl);
        }
    }

    private void savePage() {
        if (offline || web == null || !pageReady || loading || saving || !NotebookBrowserAddress.isWebUrl(web.getUrl())) return;
        final String url = web.getUrl(), pageTitle = web.getTitle();
        final long version = navigation;
        final long attempt = ++saveSequence;
        saving = true; updateControls(); status.setText("Saving offline copy…");
        archiveTimeout = () -> {
            if (attempt != saveSequence || !saving) return;
            NotebookWebsites.Pending pending = pendingSave;
            ++saveSequence;
            saveFinished(null, new IOException("The website took too long to save. Please retry after the page finishes loading."));
            if (pending != null) abandonQuietly(pending);
        };
        content.postDelayed(archiveTimeout, 90000);
        NotebookWebsiteTasks.run(() -> new NotebookWebsites(getFilesDir()).begin(), (pending, error) -> {
            if (attempt != saveSequence || isDestroyed()) { if (pending != null) abandonQuietly(pending); return; }
            if (error != null) { saveFinished(null, error); return; }
            pendingSave = pending;
            if (isDestroyed() || web == null || navigation != version || !url.equals(web.getUrl())) {
                discard(pending, "The page changed before it could be saved. Please retry.", attempt); return;
            }
            try {
                web.saveWebArchive(pending.archive.getAbsolutePath(), false, result -> {
                    if (attempt != saveSequence || isDestroyed()) { abandonQuietly(pending); return; }
                    if (isDestroyed() || web == null || navigation != version || !url.equals(web.getUrl())
                            || !pending.archive.getAbsolutePath().equals(result)) {
                        discard(pending, "The page could not be saved. Wait for it to finish loading and retry.", attempt); return;
                    }
                    cancelArchiveTimeout();
                    NotebookWebsiteTasks.run(() -> {
                        NotebookWebsites store = new NotebookWebsites(getFilesDir());
                        try { return store.finish(pending, pageTitle, url); }
                        catch (IOException failure) { try { store.abandon(pending); } catch (IOException ignored) { } throw failure; }
                    }, (entry, failure) -> { if (attempt == saveSequence) saveFinished(entry, failure); });
                });
            } catch (RuntimeException failure) { discard(pending, "The page could not be saved. Please retry.", attempt); }
        });
    }

    private void discard(NotebookWebsites.Pending pending, String message, long attempt) {
        NotebookWebsiteTasks.run(() -> { new NotebookWebsites(getFilesDir()).abandon(pending); return null; },
            (value, error) -> { if (attempt == saveSequence) saveFinished(null, new IOException(message)); });
    }

    private void abandonQuietly(NotebookWebsites.Pending pending) {
        NotebookWebsiteTasks.run(() -> { new NotebookWebsites(getFilesDir()).abandon(pending); return null; }, (value, error) -> { });
    }

    private void cancelArchiveTimeout() {
        if (archiveTimeout != null) { content.removeCallbacks(archiveTimeout); archiveTimeout = null; }
    }

    private void saveFinished(NotebookWebsites.Saved entry, IOException error) {
        cancelArchiveTimeout(); pendingSave = null; saving = false;
        if (isDestroyed()) return;
        updateControls();
        if (error != null) { status.setText("Page was not saved · tap Save to retry"); if (resumed) message("Couldn’t save website", error.getMessage()); }
        else { status.setText("Saved to Saved websites · available offline"); Toast.makeText(this, "Website saved for offline reading", Toast.LENGTH_SHORT).show(); }
    }

    private final class BrowserClient extends WebViewClient {
        @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            String scheme = request.getUrl().getScheme();
            // MHTML frame/content IDs and inline images are local resources. Network
            // fallback from an archived frame is still blocked by settings/interception.
            if (!request.isForMainFrame() && (NotebookBrowserAddress.isEmbeddedScheme(scheme)
                    || offline && ("https".equals(scheme) || "http".equals(scheme)))) return false;
            if (offline) {
                if (isArchiveUrl(url)) return false;
                if (savedPage != null && request.isForMainFrame() && request.getUrl().getFragment() != null
                        && NotebookBrowserAddress.withoutFragment(url).equals(NotebookBrowserAddress.withoutFragment(savedPage.url))) {
                    view.loadUrl(archiveUrl + "#" + request.getUrl().getEncodedFragment()); return true;
                }
                if (request.isForMainFrame() && request.hasGesture() && NotebookBrowserAddress.isWebUrl(url)) {
                    shownDialog = new AlertDialog.Builder(NotebookBrowserActivity.this).setTitle("Open link online?")
                        .setMessage("This link is outside the saved page and needs an internet connection.")
                        .setNegativeButton("Stay offline", null).setPositiveButton("Open online", (dialog, which) -> openOnline(url)).create();
                    NotebookUi.show(shownDialog);
                }
                return true;
            }
            if (saving && request.isForMainFrame()) return true;
            if (NotebookBrowserAddress.isWebUrl(url)) return false;
            if (request.isForMainFrame() && request.hasGesture()) Toast.makeText(NotebookBrowserActivity.this,
                "Only HTTPS websites can open here", Toast.LENGTH_SHORT).show();
            return true;
        }

        @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString(), scheme = request.getUrl().getScheme();
            if (NotebookBrowserAddress.isEmbeddedScheme(scheme)
                    || (offline ? isArchiveUrl(url) : NotebookBrowserAddress.isWebUrl(url))) return null;
            return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
        }

        @Override public void onPageStarted(WebView view, String url, Bitmap favicon) {
            ++navigation; loading = true; failed = false; pageReady = false;
            hint.setVisibility(View.GONE);
            if (!offline && NotebookBrowserAddress.isWebUrl(url)) { currentUrl = url; address.setText(url); }
            status.setText(offline ? "Opening offline copy…" : "Loading…"); updateControls();
        }

        @Override public void doUpdateVisitedHistory(WebView view, String url, boolean reload) {
            if (!offline && NotebookBrowserAddress.isWebUrl(url)) { currentUrl = url; address.setText(url); }
            updateControls();
        }

        @Override public void onPageFinished(WebView view, String url) {
            if (!NotebookBrowserAddress.withoutFragment(url).equals(NotebookBrowserAddress.withoutFragment(view.getUrl()))) return;
            loading = false; pageReady = !failed && (offline || NotebookBrowserAddress.isWebUrl(url));
            if (!failed && !saving) status.setText(offline && savedPage != null
                ? "Offline copy · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(savedPage.savedAt))
                : view.getTitle());
            if (restoreX != 0 || restoreY != 0) {
                final int x = restoreX, y = restoreY; restoreX = restoreY = 0;
                view.post(() -> { if (web == view && !isDestroyed()) view.scrollTo(x, y); });
            }
            updateControls();
        }

        @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) fail(offline ? "Couldn’t read this offline copy. Open the original online to save it again."
                : "Couldn’t load this website. Check the address and connection, then tap Reload.");
        }

        @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
            if (request.isForMainFrame()) { failed = true; pageReady = false; status.setText("Website returned HTTP " + response.getStatusCode() + " · tap Reload to retry"); updateControls(); }
        }

        @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            handler.cancel();
            if (error.getUrl().equals(view.getUrl())) fail("The website’s secure connection could not be verified. Try another address.");
        }

        @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            content.removeView(view); view.destroy();
            if (web == view) web = null;
            fail("The page renderer stopped. Tap Reload to reopen the page."); return true;
        }
    }

    private boolean isArchiveUrl(String url) {
        return archiveUrl != null && archiveUrl.equals(NotebookBrowserAddress.withoutFragment(url));
    }

    private void fail(String message) {
        failed = true; loading = false; pageReady = false;
        status.setText(message); hint.setText(message); hint.setVisibility(View.VISIBLE); updateControls();
    }

    private void updateControls() {
        if (go == null) return;
        go.setAvailable(!offline && !saving);
        previous.setAvailable(!saving && web != null && web.canGoBack()); next.setAvailable(!saving && web != null && web.canGoForward());
        reload.setAvailable(!saving && (offline ? archiveUrl != null : NotebookBrowserAddress.isWebUrl(currentUrl)));
        save.setAvailable(!offline && !saving && !loading && pageReady && web != null && NotebookBrowserAddress.isWebUrl(web.getUrl()));
        save.setText(saving ? "Saving…" : "Save"); address.setEnabled(!offline && !saving);
        saveShield.setVisibility(saving ? View.VISIBLE : View.GONE);
    }

    private void bookshelf() {
        if (waitForSave()) return;
        startActivity(new Intent(this, NotebookLibraryActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        overridePendingTransition(0, 0); finish();
    }

    private void savedWebsites() {
        if (waitForSave()) return;
        if (offline) finish();
        else startActivity(new Intent(this, NotebookWebsitesActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        overridePendingTransition(0, 0);
    }

    private void openOnline(String url) {
        if (!NotebookBrowserAddress.isWebUrl(url)) return;
        startActivity(new Intent(this, NotebookBrowserActivity.class).putExtra(INITIAL_URL, url)); overridePendingTransition(0, 0);
    }

    private boolean waitForSave() {
        if (saving) Toast.makeText(this, "Wait for the page to finish saving", Toast.LENGTH_SHORT).show();
        return saving;
    }

    private void message(String heading, String message) {
        if (isFinishing() || isDestroyed()) return;
        if (shownDialog != null) shownDialog.dismiss();
        shownDialog = new AlertDialog.Builder(this).setTitle(heading).setMessage(message).setPositiveButton("Done", null).create();
        NotebookUi.show(shownDialog);
    }

    @Override protected void onResume() { super.onResume(); resumed = true; if (web != null) web.onResume(); }
    @Override protected void onPause() {
        resumed = false;
        if (web != null) web.onPause();
        if (shownDialog != null) { shownDialog.dismiss(); shownDialog = null; }
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("currentUrl", currentUrl);
        if (web != null) {
            state.putInt("scrollX", web.getScrollX()); state.putInt("scrollY", web.getScrollY());
            if (!offline) { Bundle history = new Bundle(); web.saveState(history); state.putBundle("browserHistory", history); }
        }
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        ++saveSequence; cancelArchiveTimeout();
        if (pendingSave != null) { abandonQuietly(pendingSave); pendingSave = null; }
        if (web != null) { content.removeView(web); web.stopLoading(); web.destroy(); web = null; }
        // The writer's callback owns pending files. Incomplete directories are never listed
        // and old interrupted saves are reclaimed before a later save.
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (waitForSave()) return;
        if (!offline && web != null && web.canGoBack()) { history(false); return; }
        super.onBackPressed(); overridePendingTransition(0, 0);
    }

    private void addTool(LinearLayout row, NotebookButton button, int width) {
        LinearLayout.LayoutParams space = new LinearLayout.LayoutParams(dp(width), dp(38)); space.leftMargin = dp(6); row.addView(button, space);
    }
    private NotebookButton button(String text, NotebookIconDrawable.Icon icon, Runnable action) {
        NotebookButton button = new NotebookButton(this); button.setText(text); button.setTextSize(12);
        NotebookUi.action(button, icon); button.setOnClickListener(view -> action.run()); return button;
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(Color.BLACK);
        view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
