package org.eink.inkprobe.notebook;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;

/** A maintained browser engine, floating controls and a separate offline reading policy. */
public class NotebookBrowserActivity extends NotebookDisplayActivity {
    static final String SAVED_ID = "savedWebsiteId", INITIAL_URL = "websiteUrl";
    private FrameLayout content;
    private TextView title, status, hint;
    private View saveShield;
    private EditText address;
    private NotebookButton go, previous, next, reload, save;
    private NotebookBrowserTools browserTools;
    private NotebookBrowserEngine web;
    private NotebookWebsites.Saved savedPage;
    private NotebookWebsites.Pending pendingSave;
    private String archiveUrl, currentUrl;
    private boolean offline, loading, failed, pageReady, saving, resumed, clearHistoryOnFinish;
    private long navigation, saveSequence;
    private Runnable archiveTimeout;
    private int restoreX, restoreY;
    private AlertDialog shownDialog;
    private NotebookBrowserHome startPage;
    private boolean home;
    private final NotebookBrowserTabs tabs = new NotebookBrowserTabs();
    private final java.util.Map<String, NotebookBrowserEngine> engines = new java.util.HashMap<>();
    private NotebookButton tabButton;
    private static final class PageState { boolean loading, ready, failed; }
    private final java.util.Map<NotebookBrowserEngine, PageState> pageStates = new java.util.IdentityHashMap<>();
    private PageState pageState(NotebookBrowserEngine page) { PageState state = pageStates.get(page); if (state == null) { state = new PageState(); pageStates.put(page, state); } return state; }
    private static final int LINKS_RESULT = 6201;
    private final Runnable dataReset = this::forgetBrowserData;

    @Override public void onCreate(Bundle state) {
        boolean clearedRestore = state != null && state.getLong("browserDataRevision", 0) != NotebookBrowserData.revision(this);
        if (clearedRestore) state = null;
        super.onCreate(state);
        offline = getIntent().hasExtra(SAVED_ID);
        NotebookBrowserData.owner(dataReset);
        if (!offline) { if (state != null) restoreTabs(state.getBundle("tabs")); else tabs.restore(getSharedPreferences("browser-tabs", MODE_PRIVATE).getString("tabs", null)); }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setWindowAnimations(0);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.WHITE);
        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), dp(8), dp(12), dp(8));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(button("Bookshelf", NotebookIconDrawable.Icon.LIBRARY, this::bookshelf), new LinearLayout.LayoutParams(dp(112), dp(38)));
        title = label(offline ? "Saved website" : "Browser", 18); title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END); title.setPadding(dp(12), 0, dp(8), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));
        header.addView(button("", NotebookIconDrawable.Icon.MORE, this::browserMenu), new LinearLayout.LayoutParams(dp(38), dp(38)));
        header.addView(deviceSettingsIsland(), new LinearLayout.LayoutParams(-2, dp(32))); controls.addView(header);

        LinearLayout addressRow = new LinearLayout(this); addressRow.setPadding(0, dp(6), 0, dp(6));
        address = new EditText(this); address.setTextColor(Color.BLACK); address.setTextSize(14);
        address.setSingleLine(true); address.setHint("Search or enter a website");
        address.setContentDescription("Website address"); address.setSelectAllOnFocus(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO); address.setEnabled(!offline);
        address.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO) { browse(); return true; } return false;
        });
        addressRow.addView(address, new LinearLayout.LayoutParams(0, dp(44), 1));
        go = button("Go", null, this::browse); go.setContentDescription("Browse website address"); go.setVisibility(offline ? View.GONE : View.VISIBLE);
        addressRow.addView(go, new LinearLayout.LayoutParams(dp(56), dp(44))); controls.addView(addressRow);

        LinearLayout tools = new LinearLayout(this); tools.setGravity(Gravity.CENTER_VERTICAL);
        previous = button("", NotebookIconDrawable.Icon.PREVIOUS, () -> history(false)); previous.setContentDescription("Back");
        next = button("", NotebookIconDrawable.Icon.NEXT, () -> history(true)); next.setContentDescription("Forward");
        reload = button("", NotebookIconDrawable.Icon.RETRY, this::reload); reload.setContentDescription("Reload page");
        tools.addView(previous, new LinearLayout.LayoutParams(dp(42), dp(38)));
        addTool(tools, next, 42); addTool(tools, reload, 42);
        if (!offline) {
            addTool(tools, button("Home", null, () -> { if (!waitForSave()) showHome(true); }), 64);
            tabButton = button("Tabs", null, this::showTabs); tabButton.setContentDescription("Browser tabs"); addTool(tools, tabButton, 64);
        }
        save = button("Save", NotebookIconDrawable.Icon.SAVE, this::savePage);
        save.setContentDescription("Save page for offline reading"); save.setVisibility(offline ? View.GONE : View.VISIBLE);
        addTool(tools, save, 88);
        if (!offline) addTool(tools, button("Bookmark", null, () -> bookmarks(true, false)), 100);
        if (offline) addTool(tools, button("Open online", NotebookIconDrawable.Icon.BROWSER,
            () -> { if (savedPage != null) openOnline(savedPage.url); }), 136);
        addTool(tools, button("Saved websites", NotebookIconDrawable.Icon.FOLDER, this::savedWebsites), 158);
        NotebookButton zen = button("", NotebookIconDrawable.Icon.FULLSCREEN, () -> browserTools.zen(true));
        zen.setContentDescription("Enter Zen mode"); header.addView(zen, new LinearLayout.LayoutParams(dp(38), dp(38)));
        HorizontalScrollView strip = new HorizontalScrollView(this); strip.setHorizontalScrollBarEnabled(false);
        strip.setOverScrollMode(View.OVER_SCROLL_NEVER); strip.setSmoothScrollingEnabled(false);
        strip.addView(tools, new HorizontalScrollView.LayoutParams(-2, -2));
        controls.addView(strip, new LinearLayout.LayoutParams(-1, -2));
        status = label(offline ? "Opening offline copy…" : "Enter an address to browse. Save keeps this page for offline reading.", 12);
        status.setMaxLines(2); controls.addView(status, new LinearLayout.LayoutParams(-1, dp(38)));
        content = new FrameLayout(this); content.setBackgroundColor(Color.WHITE);
        hint = label("Tap the dotted button for browser controls.\n\nEnter a website address, then tap Save after the page loads. Find your offline copy in Saved websites on the bookshelf.", 17);
        hint.setGravity(Gravity.CENTER); hint.setPadding(dp(22), dp(22), dp(22), dp(22));
        if (offline) hint.setText("Opening offline copy…");
        content.addView(hint, new FrameLayout.LayoutParams(-1, -1));
        if (!offline) {
            startPage = new NotebookBrowserHome(this, new NotebookBrowserHome.Actions() {
                public void open(String value) { try { navigate(NotebookBrowserAddress.destination(value, searchEngine())); } catch (IllegalArgumentException error) { message("Website address", error.getMessage()); } }
                public void add() { NotebookBrowserActivity.this.bookmarks(false, true); }
                public void manage(NotebookBrowserLibrary.Link link) { pinnedActions(link); }
                public void bookmarks() { NotebookBrowserActivity.this.bookmarks(false, false); }
                public void history() { openHistory(); }
                public void settings() { openBrowserData(); }
                public void tabs() { showTabs(); }
            });
            content.addView(startPage, new FrameLayout.LayoutParams(-1, -1));
        }
        saveShield = new View(this); saveShield.setClickable(true); saveShield.setVisibility(View.GONE);
        saveShield.setContentDescription("Saving page"); content.addView(saveShield, new FrameLayout.LayoutParams(-1, -1));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        browserTools = new NotebookBrowserTools(this, controls, deviceSettingsButton(), offline,
            state == null ? null : state.getBundle("browserTools"));
        root.addView(browserTools, new FrameLayout.LayoutParams(-1, -1)); setContentView(root);
        if (state != null) { restoreX = state.getInt("scrollX"); restoreY = state.getInt("scrollY"); }
        if (offline) loadSavedPage(getIntent().getStringExtra(SAVED_ID), state == null ? null : state.getBundle("browserHistory"));
        else {
            String url = state == null ? (clearedRestore ? null : getIntent().getStringExtra(INITIAL_URL)) : state.getString("currentUrl");
            if (!NotebookBrowserAddress.isWebUrl(url) && !tabs.current().home) url = tabs.current().url;
            if (NotebookBrowserAddress.isWebUrl(url) && (state == null || !state.getBoolean("home", false))) {
                home = false; tabs.current().home = false; tabs.current().url = url; createWebView();
                Bundle history = state == null ? null : state.getBundle("browserHistory");
                if (web != null && history != null && web.restoreState(history) != null) {
                    currentUrl = web.getUrl(); address.setText(currentUrl); hint.setVisibility(View.GONE); startPage.setVisibility(View.GONE);
                } else navigate(url);
            } else showHome(state == null);
        }
        updateControls();
    }

    private void createWebView() {
        try {
            if (NotebookBrowserData.clearing()) { Toast.makeText(this, "Wait for browser data clearing to finish", Toast.LENGTH_SHORT).show(); return; }
            BrowserClient client = new BrowserClient(offline ? "offline" : tabs.current().id);
            web = offline && savedPage != null && savedPage.format == NotebookWebsites.Format.MHTML
                ? new NotebookLegacyWebPage(this, archiveUrl, client) : new NotebookGeckoPage(this, offline, archiveUrl, client);
            if (!offline) engines.put(tabs.current().id, web);
            content.addView(web, 0, new FrameLayout.LayoutParams(-1, -1));
            if (resumed) web.onResume();
        } catch (RuntimeException error) {
            web = null; fail("Could not start the browser engine. Please reopen Browser or restart the app.");
        }
    }

    private void loadSavedPage(String id, Bundle history) {
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
            if (web == null) createWebView();
            if (web != null && !restoreOfflineHistory(history)) web.loadUrl(archiveUrl);
            updateControls();
        });
    }

    private boolean restoreOfflineHistory(Bundle history) {
        if (web == null || history == null) return false;
        NotebookBrowserEngine.History restored = web.restoreState(history);
        if (restored == null || restored.getSize() == 0 || restored.getCurrentIndex() < 0
                || restored.getCurrentIndex() >= restored.getSize()) return false;
        for (int i = 0; i < restored.getSize(); i++) {
            if (!isArchiveUrl(restored.getItemAtIndex(i).getUrl())) {
                web.stopLoading(); clearHistoryOnFinish = true; return false;
            }
        }
        hint.setVisibility(View.GONE); return true;
    }

    private void browse() {
        if (offline || saving) return;
        try { navigate(NotebookBrowserAddress.destination(address.getText().toString(), searchEngine())); }
        catch (IllegalArgumentException error) { address.setError(error.getMessage()); }
    }

    private void navigate(String url) {
        if (!NotebookBrowserAddress.isWebUrl(url) || saving) return;
        if (web == null) createWebView();
        if (web == null) return;
        home = false; tabs.current().home = false; tabs.current().url = url;
        if (startPage != null) startPage.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE); if (resumed) web.onResume();
        currentUrl = url; address.setError(null); address.setText(url); address.clearFocus();
        InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(address.getWindowToken(), 0);
        pageReady = false; failed = false; loading = true; hint.setVisibility(View.GONE);
        status.setText("Loading…"); web.loadUrl(url); updateControls();
    }

    private void history(boolean forward) {
        if (saving) return;
        if (home && !forward && web != null) { showCurrentPage(); return; }
        if (!canHistory(forward)) return;
        // Hash jumps do not start a new document load. Keep readiness/status intact;
        // onPageStarted owns those changes when a full navigation actually occurs.
        if (forward) web.goForward(); else web.goBack(); updateControls();
    }

    private boolean canHistory(boolean forward) {
        if (web == null) return false;
        if (home) return !forward && NotebookBrowserAddress.isWebUrl(web.getUrl());
        if (!(forward ? web.canGoForward() : web.canGoBack())) return false;
        if (!offline) return forward ? web.canGoForward() : web.canGoBack();
        NotebookBrowserEngine.History history = web.copyBackForwardList();
        int target = history.getCurrentIndex() + (forward ? 1 : -1);
        return target >= 0 && target < history.getSize() && isArchiveUrl(history.getItemAtIndex(target).getUrl());
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
        if (offline || home || web == null || !pageReady || loading || saving || !NotebookBrowserAddress.isWebUrl(web.getUrl())) return;
        final String url = web.getUrl(), pageTitle = web.getTitle();
        final long version = navigation;
        final long attempt = ++saveSequence;
        saving = true; updateControls(); status.setText("Saving offline copy…");
        archiveTimeout = () -> {
            if (attempt != saveSequence || !saving) return;
            NotebookWebsites.Pending pending = pendingSave;
            ++saveSequence;
            if (web != null) web.cancelCapture();
            saveFinished(null, new IOException("The website took too long to save. Please retry after the page finishes loading."));
            if (pending != null) abandonQuietly(pending);
        };
        content.postDelayed(archiveTimeout, 90000);
        NotebookWebsiteTasks.run(() -> new NotebookWebsites(getFilesDir()).begin(NotebookWebsites.Format.HTML), (pending, error) -> {
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
                    final int missing = web.captureWarnings();
                    NotebookWebsiteTasks.run(() -> {
                        NotebookWebsites store = new NotebookWebsites(getFilesDir());
                        try { return store.finish(pending, pageTitle, url, missing); }
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
        else {
            status.setText("Saved to Saved websites · available offline" + (entry.missing > 0 ? " · " + entry.missing + " resources unavailable" : ""));
            Toast.makeText(this, "Website saved for offline reading", Toast.LENGTH_SHORT).show();
        }
    }

    private final class BrowserClient implements NotebookBrowserEngine.Listener {
        private final String owner;
        BrowserClient(String owner) { this.owner = owner; }
        private void remember(NotebookBrowserEngine view, String url) {
            if (!offline) { NotebookBrowserTabs.Tab tab = tabs.find(owner); if (tab != null && NotebookBrowserAddress.isWebUrl(url)) { tab.url = url; if (view.getTitle() != null && !view.getTitle().isEmpty()) tab.title = view.getTitle(); rememberTabs(); } }
        }
        @Override public boolean overrideNavigation(NotebookBrowserEngine view, String url, boolean mainFrame, boolean gesture) {
            String scheme = Uri.parse(url).getScheme();
            // MHTML frame/content IDs and inline images are local resources. Network
            // fallback from an archived frame is still blocked by settings/interception.
            if (!mainFrame && (NotebookBrowserAddress.isEmbeddedScheme(scheme)
                    || offline && ("https".equals(scheme) || "http".equals(scheme)))) return false;
            if (offline) {
                if (isArchiveUrl(url)) return false;
                String target = savedPage == null ? null : NotebookBrowserAddress.offlineTarget(archiveUrl, savedPage.url, url);
                if (mainFrame && target != null) {
                    view.loadUrl(target); return true;
                }
                if (mainFrame && gesture && NotebookBrowserAddress.isWebUrl(url)) {
                    shownDialog = new AlertDialog.Builder(NotebookBrowserActivity.this).setTitle("Open link online?")
                        .setMessage("This link is outside the saved page and needs an internet connection.")
                        .setNegativeButton("Stay offline", null).setPositiveButton("Open online", (dialog, which) -> openOnline(url)).create();
                    NotebookUi.show(shownDialog);
                }
                return true;
            }
            if (saving && mainFrame) return true;
            if ("https".equals(scheme)) return false;
            if (mainFrame && gesture) Toast.makeText(NotebookBrowserActivity.this,
                "Only HTTPS websites can open here", Toast.LENGTH_SHORT).show();
            return true;
        }

        @Override public void onPageStarted(NotebookBrowserEngine view, String url) {
            remember(view, url); PageState state = pageState(view); state.loading = true; state.ready = false; state.failed = false;
            if (view != web || home) return;
            ++navigation; loading = true; failed = false; pageReady = false;
            hint.setVisibility(View.GONE);
            if (!offline && NotebookBrowserAddress.isWebUrl(url)) { currentUrl = url; address.setText(url); }
            status.setText(offline ? "Opening offline copy…" : "Loading…"); updateControls();
        }

        @Override public void onHistoryChanged(NotebookBrowserEngine view, String url) {
            remember(view, url); if (view != web || home) return;
            if (!offline && NotebookBrowserAddress.isWebUrl(url)) { currentUrl = url; address.setText(url); }
            updateControls();
        }

        @Override public void onPageFinished(NotebookBrowserEngine view, String url) {
            remember(view, url); PageState state = pageState(view); state.loading = false; state.ready = !state.failed && (offline || NotebookBrowserAddress.isWebUrl(url));
            if (!offline && NotebookBrowserAddress.isWebUrl(url)) NotebookWebsiteTasks.run(() -> { new NotebookBrowserLibrary(getFilesDir()).visit(view.getTitle(), url); return null; }, (value, error) -> { });
            if (view != web || home) return;
            if (!NotebookBrowserAddress.withoutFragment(url).equals(NotebookBrowserAddress.withoutFragment(view.getUrl()))) return;
            if (clearHistoryOnFinish && isArchiveUrl(url)) { clearHistoryOnFinish = false; view.clearHistory(); }
            failed = state.failed; loading = false; pageReady = !failed && (offline || NotebookBrowserAddress.isWebUrl(url));
            if (!failed && !saving) status.setText(offline && savedPage != null
                ? "Offline copy · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(savedPage.savedAt))
                    + (savedPage.missing > 0 ? " · " + savedPage.missing + " resources unavailable" : "")
                : view.getTitle());
            if (restoreX != 0 || restoreY != 0) {
                final int x = restoreX, y = restoreY; restoreX = restoreY = 0;
                view.post(() -> { if (web == view && !isDestroyed()) view.scrollPageTo(x, y); });
            }
            updateControls();
            if (pageReady) onBrowserDocumentReady();
        }

        @Override public void onTitle(NotebookBrowserEngine view, String value) {
            remember(view, view.getUrl()); if (view != web || home) return;
            if (!offline && pageReady && !saving && !loading) status.setText(value);
        }
        @Override public void onError(NotebookBrowserEngine view, String message) { PageState state = pageState(view); state.loading = false; state.ready = false; state.failed = true; if (view == web && !home) fail(message); }
        @Override public void onDownload(NotebookBrowserEngine view) {
            message("File download", "Use another browser to download this file. Save keeps the currently displayed web page for offline reading.");
        }
        @Override public void onNewTab(NotebookBrowserEngine view, String url) {
            if (offline || view != web || saving || !NotebookBrowserAddress.isWebUrl(url)) return;
            try { NotebookBrowserTabs.Tab tab = tabs.add(); activateTab(tab.id); navigate(url); }
            catch (IllegalStateException error) { message("Tabs", error.getMessage()); }
        }
        @Override public void onDataCleared(NotebookBrowserEngine view) {
            content.removeView(view); engines.remove(owner); pageStates.remove(view); if (web == view) web = null;
            if (!offline) { tabs.clear(); home = true; currentUrl = null; pageReady = false; loading = false; if (startPage != null) startPage.setVisibility(View.VISIBLE); }
        }
        @Override public void onRendererGone(NotebookBrowserEngine view) {
            content.removeView(view); view.destroy(); engines.remove(owner); pageStates.remove(view);
            if (web == view) { web = null; fail("The page renderer stopped. Tap Reload to reopen the page."); }
        }
    }

    protected final View browserDebugRoot() { return shownDialog != null && shownDialog.isShowing() ? shownDialog.getWindow().getDecorView() : getWindow().getDecorView(); }
    protected void onBrowserDocumentReady() { }
    protected final void probeBrowser(java.util.function.Consumer<org.json.JSONObject> callback) {
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) { callback.accept(null); return; }
        java.util.function.Consumer<org.json.JSONObject> result = stats -> {
            try {
                org.json.JSONObject value = stats == null ? new org.json.JSONObject() : stats;
                value.put("home", home).put("tabCount", tabs.items.size()).put("selectedTab", tabs.current().id);
                org.json.JSONArray tabValues = new org.json.JSONArray(); for (NotebookBrowserTabs.Tab tab : tabs.items) tabValues.put(new org.json.JSONObject().put("id", tab.id).put("url", tab.url).put("title", tab.title).put("home", tab.home)); value.put("tabs", tabValues);
                value.put("nativeUrl", web == null ? "" : web.getUrl()).put("offline", offline).put("pageReady", pageReady)
                    .put("canGoBack", canHistory(false)).put("canGoForward", canHistory(true));
                if (web != null) value.put("nativeScrollY", web.pageScrollY()).put("pageWidth", web.getWidth()).put("pageHeight", web.getHeight());
                org.json.JSONArray urls = new org.json.JSONArray();
                if (web != null) {
                    NotebookBrowserEngine.History history = web.copyBackForwardList();
                    for (int i = 0; i < history.getSize(); i++) urls.put(history.getItemAtIndex(i).getUrl());
                    value.put("historyIndex", history.getCurrentIndex());
                }
                callback.accept(value.put("history", urls));
            } catch (org.json.JSONException error) { callback.accept(null); }
        };
        if (web instanceof NotebookGeckoPage) ((NotebookGeckoPage) web).probe(result); else result.accept(null);
    }

    private boolean isArchiveUrl(String url) {
        return archiveUrl != null && archiveUrl.equals(NotebookBrowserAddress.withoutFragment(url));
    }

    private void fail(String message) {
        failed = true; loading = false; pageReady = false;
        status.setText(message); hint.setText(message); hint.setVisibility(View.VISIBLE);
        if (browserTools != null) browserTools.reveal(); updateControls();
    }

    private void updateControls() {
        if (go == null) return;
        go.setAvailable(!offline && !saving);
        previous.setAvailable(!saving && canHistory(false)); next.setAvailable(!saving && canHistory(true));
        reload.setAvailable(!saving && !home && (offline ? archiveUrl != null : NotebookBrowserAddress.isWebUrl(currentUrl)));
        save.setAvailable(!offline && !home && !saving && !loading && pageReady && web != null && NotebookBrowserAddress.isWebUrl(web.getUrl()));
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

    @Override protected void onResume() {
        super.onResume(); resumed = true; browserTools.resumed(true);
        if (offline && web == null && archiveUrl != null && !NotebookBrowserData.clearing()) { createWebView(); if (web != null) web.loadUrl(archiveUrl); }
        if (web != null && !home) web.onResume();
        if (!offline && home) showHome(false);
    }
    @Override protected void onPause() {
        resumed = false;
        browserTools.resumed(false);
        if (offline && web != null) web.onPause();
        for (NotebookBrowserEngine page : engines.values()) page.onPause();
        if (shownDialog != null) { shownDialog.dismiss(); shownDialog = null; }
        if (!offline) rememberTabs();
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("browserDataRevision", NotebookBrowserData.revision(this));
        state.putString("currentUrl", currentUrl); state.putBoolean("home", home);
        if (!offline) { state.putBundle("tabs", saveTabs()); rememberTabs(); }
        state.putBundle("browserTools", browserTools.save());
        if (web != null) {
            state.putInt("scrollX", web.pageScrollX()); state.putInt("scrollY", web.pageScrollY());
            Bundle history = new Bundle(); web.saveState(history); state.putBundle("browserHistory", history);
        }
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        NotebookBrowserData.removeOwner(dataReset);
        ++saveSequence; cancelArchiveTimeout();
        if (browserTools != null) browserTools.dispose();
        if (pendingSave != null) { abandonQuietly(pendingSave); pendingSave = null; }
        if (offline && web != null) { content.removeView(web); web.stopLoading(); web.destroy(); }
        for (NotebookBrowserEngine page : new java.util.ArrayList<>(engines.values())) { content.removeView(page); page.stopLoading(); page.destroy(); }
        engines.clear(); pageStates.clear(); web = null;
        // The writer's callback owns pending files. Incomplete directories are never listed
        // and old interrupted saves are reclaimed before a later save.
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (browserTools.isZen()) { browserTools.zen(false); return; }
        if (!browserTools.isCollapsed()) { browserTools.collapse(true); return; }
        if (waitForSave()) return;
        if (home && web != null && NotebookBrowserAddress.isWebUrl(web.getUrl())) { showCurrentPage(); return; }
        if (canHistory(false)) { history(false); return; }
        super.onBackPressed(); overridePendingTransition(0, 0);
    }

    private void forgetBrowserData() {
        ++saveSequence; ++navigation; cancelArchiveTimeout(); saving = false;
        if (pendingSave != null) { abandonQuietly(pendingSave); pendingSave = null; }
        if (!offline) { tabs.clear(); home = true; currentUrl = null; pageReady = false; loading = false; address.setText(""); rememberTabs(); }
    }
    private void rememberTabs() { getSharedPreferences("browser-tabs", MODE_PRIVATE).edit().putString("tabs", tabs.encode()).apply(); }
    private int searchEngine() { return getSharedPreferences("browser-data", MODE_PRIVATE).getInt("search", 0); }
    private void refreshHome() {
        NotebookWebsiteTasks.run(() -> new NotebookBrowserLibrary(getFilesDir()).initialize(), (value, error) -> {
            if (isDestroyed()) return; if (error != null) { message("Browser start page", error.getMessage()); return; } startPage.show(value);
        });
    }
    private void showHome(boolean collapse) {
        if (offline || saving) return;
        home = true; tabs.current().home = true; if (web != null) { web.onPause(); web.setVisibility(View.GONE); }
        hint.setVisibility(View.GONE); startPage.setVisibility(View.VISIBLE); address.setText(""); title.setText("Browser"); status.setText("Start page · " + tabs.items.size() + " tabs");
        loading = false; pageReady = false; if (collapse) browserTools.collapse(true); refreshHome(); updateControls(); updateTabButton();
    }
    private void showCurrentPage() {
        if (web == null) { navigate(tabs.current().url); return; }
        home = false; tabs.current().home = false; startPage.setVisibility(View.GONE); hint.setVisibility(View.GONE); web.setVisibility(View.VISIBLE);
        currentUrl = web.getUrl(); address.setText(currentUrl); PageState state = pageState(web); pageReady = state.ready; loading = state.loading; failed = state.failed; status.setText(loading ? "Loading…" : failed ? "Couldn’t load this page. Tap Reload to retry." : web.getTitle()); if (resumed) web.onResume(); updateControls(); updateTabButton();
    }
    private void updateTabButton() { if (tabButton != null) tabButton.setText(tabs.items.size() + (tabs.items.size() == 1 ? " tab" : " tabs")); if (!offline) rememberTabs(); }
    private void activateTab(String id) {
        if (waitForSave()) return;
        if (web != null) { web.onPause(); web.setVisibility(View.GONE); }
        tabs.select(id); NotebookBrowserTabs.Tab tab = tabs.current(); web = engines.get(id); currentUrl = tab.url; home = tab.home;
        if (home || !NotebookBrowserAddress.isWebUrl(tab.url)) showHome(true);
        else if (web == null) navigate(tab.url); else showCurrentPage();
    }
    private void showTabs() {
        if (offline || waitForSave()) return;
        LinearLayout rows = NotebookBrowserUi.column(this);
        rows.addView(NotebookBrowserUi.button(this, "+ New tab", () -> { if (shownDialog != null) shownDialog.dismiss(); try { NotebookBrowserTabs.Tab tab = tabs.add(); activateTab(tab.id); } catch (IllegalStateException error) { message("Tabs", error.getMessage()); } }));
        for (NotebookBrowserTabs.Tab tab : tabs.items) {
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            NotebookButton open = button((tab == tabs.current() ? "✓ " : "") + (tab.home ? "Start page" : tab.title) + "\n" + tab.url, null, () -> { shownDialog.dismiss(); activateTab(tab.id); }); open.setMaxLines(3); row.addView(open, new LinearLayout.LayoutParams(0, dp(74), 1));
            NotebookButton close = button("×", null, () -> { shownDialog.dismiss(); NotebookBrowserEngine page = engines.remove(tab.id); if (page != null) { content.removeView(page); page.destroy(); pageStates.remove(page); if (web == page) web = null; } tabs.close(tab.id); activateTab(tabs.current().id); }); close.setContentDescription("Close tab " + tab.title); row.addView(close, new LinearLayout.LayoutParams(dp(46), dp(44))); rows.addView(row);
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.addView(rows); shownDialog = new AlertDialog.Builder(this).setTitle("Tabs · " + tabs.items.size() + " of " + NotebookBrowserTabs.MAX).setView(scroll).setNegativeButton("Done", null).create(); NotebookUi.show(shownDialog);
    }
    private Bundle saveTabs() {
        Bundle value = new Bundle(); java.util.ArrayList<String> ids = new java.util.ArrayList<>(), urls = new java.util.ArrayList<>(), titles = new java.util.ArrayList<>(); boolean[] starts = new boolean[tabs.items.size()];
        for (int i = 0; i < tabs.items.size(); i++) { NotebookBrowserTabs.Tab tab = tabs.items.get(i); ids.add(tab.id); urls.add(tab.url); titles.add(tab.title); starts[i] = tab.home; }
        value.putStringArrayList("ids", ids); value.putStringArrayList("urls", urls); value.putStringArrayList("titles", titles); value.putBooleanArray("homes", starts); value.putString("selected", tabs.current().id); return value;
    }
    private void restoreTabs(Bundle value) {
        if (value == null) return;
        java.util.ArrayList<String> ids = value.getStringArrayList("ids"), urls = value.getStringArrayList("urls"), titles = value.getStringArrayList("titles"); boolean[] homes = value.getBooleanArray("homes");
        if (ids == null || urls == null || titles == null || homes == null || ids.size() == 0 || ids.size() > NotebookBrowserTabs.MAX || urls.size() != ids.size() || titles.size() != ids.size() || homes.length != ids.size()) return;
        java.util.List<NotebookBrowserTabs.Tab> restored = new java.util.ArrayList<>(); java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < ids.size(); i++) { String id = ids.get(i), url = urls.get(i); if (id == null || !id.matches("[a-f0-9-]{36}") || titles.get(i) == null || titles.get(i).length() > 1024 || !seen.add(id) || url == null || !url.isEmpty() && !NotebookBrowserAddress.isWebUrl(url)) return; NotebookBrowserTabs.Tab tab = new NotebookBrowserTabs.Tab(id); tab.url = url; tab.title = titles.get(i); tab.home = homes[i]; restored.add(tab); }
        String selected = value.getString("selected"); if (!seen.contains(selected)) return; tabs.items.clear(); tabs.items.addAll(restored); tabs.select(selected);
    }
    private void bookmarks(boolean current, boolean pin) {
        if (waitForSave()) return;
        String url = offline && savedPage != null ? savedPage.url : !home && web != null ? web.getUrl() : null;
        if (current && !NotebookBrowserAddress.isWebUrl(url)) { Toast.makeText(this, "Open a website to bookmark it", Toast.LENGTH_SHORT).show(); return; }
        Intent intent = new Intent(this, NotebookBrowserBookmarksActivity.class);
        if (current || pin) { intent.putExtra("edit", true).putExtra("pin", pin).putExtra("addUrl", current ? url : "").putExtra("addTitle", current && web != null ? web.getTitle() : ""); }
        startActivityForResult(intent, LINKS_RESULT); overridePendingTransition(0, 0);
    }
    private void pinnedActions(NotebookBrowserLibrary.Link link) {
        shownDialog = new AlertDialog.Builder(this).setTitle(link.title).setItems(new String[]{"Open website", "Edit bookmark", "Move earlier", "Move later", "Unpin"}, (dialog, which) -> {
            if (which == 0) navigate(link.url);
            else if (which == 1) startActivityForResult(new Intent(this, NotebookBrowserBookmarksActivity.class).putExtra("edit", true).putExtra("addUrl", link.url), LINKS_RESULT);
            else NotebookWebsiteTasks.run(() -> { NotebookBrowserLibrary library = new NotebookBrowserLibrary(getFilesDir()); if (which == 4) library.pin(link.id, false); else library.movePin(link.id, which == 2 ? -1 : 1); return null; }, (value, error) -> { if (isDestroyed()) return; if (error != null) message("Pinned websites", error.getMessage()); else refreshHome(); });
        }).create(); NotebookUi.show(shownDialog);
    }
    private void openHistory() { if (!waitForSave()) startActivityForResult(new Intent(this, NotebookBrowserHistoryActivity.class), LINKS_RESULT); }
    private void openBrowserData() { if (!waitForSave()) startActivity(new Intent(this, NotebookBrowserDataActivity.class).putExtra("host", currentUrl)); }
    private void browserMenu() {
        if (waitForSave()) return;
        shownDialog = new AlertDialog.Builder(this).setTitle("Browser").setItems(new String[]{"Bookmarks", "Bookmark this page", "Pin this page", "Browsing history", "Browser data settings", "Copy page address", "Share page address", "Open in another browser"}, (dialog, at) -> {
            if (at == 0) bookmarks(false, false); else if (at == 1) bookmarks(true, false); else if (at == 2) bookmarks(true, true); else if (at == 3) openHistory(); else if (at == 4) openBrowserData(); else {
                String url = offline && savedPage != null ? savedPage.url : !home && web != null ? web.getUrl() : null; if (!NotebookBrowserAddress.isWebUrl(url)) return;
                if (at == 5) { ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("Website address", url)); Toast.makeText(this, "Website address copied", Toast.LENGTH_SHORT).show(); }
                else if (at == 6) startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url), "Share website"));
                else try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (android.content.ActivityNotFoundException error) { message("Open website", "No other browser is installed."); }
            }
        }).create(); NotebookUi.show(shownDialog);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); if (request == LINKS_RESULT && result == RESULT_OK && data != null) { String url = data.getStringExtra(INITIAL_URL); if (NotebookBrowserAddress.isWebUrl(url)) { if (offline) openOnline(url); else navigate(url); } }
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
