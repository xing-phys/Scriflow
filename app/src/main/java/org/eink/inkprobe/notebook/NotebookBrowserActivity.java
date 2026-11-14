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

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        offline = getIntent().hasExtra(SAVED_ID);
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
        header.addView(deviceSettingsIsland(), new LinearLayout.LayoutParams(-2, dp(32))); controls.addView(header);

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
        addressRow.addView(go, new LinearLayout.LayoutParams(dp(56), dp(44))); controls.addView(addressRow);

        LinearLayout tools = new LinearLayout(this); tools.setGravity(Gravity.CENTER_VERTICAL);
        previous = button("", NotebookIconDrawable.Icon.PREVIOUS, () -> history(false)); previous.setContentDescription("Back");
        next = button("", NotebookIconDrawable.Icon.NEXT, () -> history(true)); next.setContentDescription("Forward");
        reload = button("", NotebookIconDrawable.Icon.RETRY, this::reload); reload.setContentDescription("Reload page");
        tools.addView(previous, new LinearLayout.LayoutParams(dp(42), dp(38)));
        addTool(tools, next, 42); addTool(tools, reload, 42);
        save = button("Save", NotebookIconDrawable.Icon.SAVE, this::savePage);
        save.setContentDescription("Save page for offline reading"); save.setVisibility(offline ? View.GONE : View.VISIBLE);
        addTool(tools, save, 88);
        if (offline) addTool(tools, button("Open online", NotebookIconDrawable.Icon.BROWSER,
            () -> { if (savedPage != null) openOnline(savedPage.url); }), 136);
        addTool(tools, button("Saved websites", NotebookIconDrawable.Icon.FOLDER, this::savedWebsites), 158);
        NotebookButton zen = button("", NotebookIconDrawable.Icon.FULLSCREEN, () -> browserTools.zen(true));
        zen.setContentDescription("Enter Zen mode"); addTool(tools, zen, 42);
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
        saveShield = new View(this); saveShield.setClickable(true); saveShield.setVisibility(View.GONE);
        saveShield.setContentDescription("Saving page"); content.addView(saveShield, new FrameLayout.LayoutParams(-1, -1));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        browserTools = new NotebookBrowserTools(this, controls, deviceSettingsButton(), offline,
            state == null ? null : state.getBundle("browserTools"));
        root.addView(browserTools, new FrameLayout.LayoutParams(-1, -1)); setContentView(root);
        if (state != null) { restoreX = state.getInt("scrollX"); restoreY = state.getInt("scrollY"); }
        if (!offline) createWebView();
        if (offline) loadSavedPage(getIntent().getStringExtra(SAVED_ID), state == null ? null : state.getBundle("browserHistory"));
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

    private void createWebView() {
        try {
            BrowserClient client = new BrowserClient();
            web = offline && savedPage != null && savedPage.format == NotebookWebsites.Format.MHTML
                ? new NotebookLegacyWebPage(this, archiveUrl, client) : new NotebookGeckoPage(this, offline, archiveUrl, client);
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
        if (saving || !canHistory(forward)) return;
        // Hash jumps do not start a new document load. Keep readiness/status intact;
        // onPageStarted owns those changes when a full navigation actually occurs.
        if (forward) web.goForward(); else web.goBack(); updateControls();
    }

    private boolean canHistory(boolean forward) {
        if (web == null) return false;
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
        if (offline || web == null || !pageReady || loading || saving || !NotebookBrowserAddress.isWebUrl(web.getUrl())) return;
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
            ++navigation; loading = true; failed = false; pageReady = false;
            hint.setVisibility(View.GONE);
            if (!offline && NotebookBrowserAddress.isWebUrl(url)) { currentUrl = url; address.setText(url); }
            status.setText(offline ? "Opening offline copy…" : "Loading…"); updateControls();
        }

        @Override public void onHistoryChanged(NotebookBrowserEngine view, String url) {
            if (!offline && NotebookBrowserAddress.isWebUrl(url)) { currentUrl = url; address.setText(url); }
            updateControls();
        }

        @Override public void onPageFinished(NotebookBrowserEngine view, String url) {
            if (!NotebookBrowserAddress.withoutFragment(url).equals(NotebookBrowserAddress.withoutFragment(view.getUrl()))) return;
            if (clearHistoryOnFinish && isArchiveUrl(url)) { clearHistoryOnFinish = false; view.clearHistory(); }
            loading = false; pageReady = !failed && (offline || NotebookBrowserAddress.isWebUrl(url));
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
            if (!offline && pageReady && !saving && !loading) status.setText(value);
        }
        @Override public void onError(NotebookBrowserEngine view, String message) { fail(message); }
        @Override public void onDownload(NotebookBrowserEngine view) {
            message("File download", "Use another browser to download this file. Save keeps the currently displayed web page for offline reading.");
        }
        @Override public void onRendererGone(NotebookBrowserEngine view) {
            content.removeView(view); view.destroy();
            if (web == view) web = null;
            fail("The page renderer stopped. Tap Reload to reopen the page.");
        }
    }

    protected void onBrowserDocumentReady() { }
    protected final void probeBrowser(java.util.function.Consumer<org.json.JSONObject> callback) {
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0) { callback.accept(null); return; }
        java.util.function.Consumer<org.json.JSONObject> result = stats -> {
            try {
                org.json.JSONObject value = stats == null ? new org.json.JSONObject() : stats;
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

    @Override protected void onResume() {
        super.onResume(); resumed = true; browserTools.resumed(true); if (web != null) web.onResume();
    }
    @Override protected void onPause() {
        resumed = false;
        browserTools.resumed(false);
        if (web != null) web.onPause();
        if (shownDialog != null) { shownDialog.dismiss(); shownDialog = null; }
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("currentUrl", currentUrl);
        state.putBundle("browserTools", browserTools.save());
        if (web != null) {
            state.putInt("scrollX", web.pageScrollX()); state.putInt("scrollY", web.pageScrollY());
            Bundle history = new Bundle(); web.saveState(history); state.putBundle("browserHistory", history);
        }
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        ++saveSequence; cancelArchiveTimeout();
        if (browserTools != null) browserTools.dispose();
        if (pendingSave != null) { abandonQuietly(pendingSave); pendingSave = null; }
        if (web != null) { content.removeView(web); web.stopLoading(); web.destroy(); web = null; }
        // The writer's callback owns pending files. Incomplete directories are never listed
        // and old interrupted saves are reclaimed before a later save.
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (browserTools.isZen()) { browserTools.zen(false); return; }
        if (!browserTools.isCollapsed()) { browserTools.collapse(true); return; }
        if (waitForSave()) return;
        if (canHistory(false)) { history(false); return; }
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
