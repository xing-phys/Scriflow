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
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Date;

/** A maintained browser engine, floating controls and a separate offline reading policy. */
public class NotebookBrowserActivity extends NotebookActivity {
    @Override protected boolean isBrowserWorkspace() { return true; }

    static final String SAVED_ID = "savedWebsiteId", INITIAL_URL = "websiteUrl";
    private FrameLayout content;
    private TextView title, status, hint;
    private View saveShield;
    private EditText address;
    private NotebookButton go, previous, next, reload, save;
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
    private boolean home, startupHome;
    private final NotebookBrowserTabs tabs = new NotebookBrowserTabs();
    private final java.util.Map<String, NotebookBrowserEngine> engines = new java.util.HashMap<>();
    private NotebookBrowserIconButton tabButton;
    private static final class PageState { boolean loading, ready, failed; }
    private final java.util.Map<NotebookBrowserEngine, PageState> pageStates = new java.util.IdentityHashMap<>();
    private PageState pageState(NotebookBrowserEngine page) { PageState state = pageStates.get(page); if (state == null) { state = new PageState(); pageStates.put(page, state); } return state; }
    private static final int LINKS_RESULT = 6201;
    private final Runnable dataReset = this::forgetBrowserData;
    private Runnable previewUpdate;

    private void schedulePreview() {
        if (previewUpdate != null) content.removeCallbacks(previewUpdate);
        NotebookBrowserEngine page = web;
        previewUpdate = () -> { if (!offline && resumed && !home && web == page && page != null && !isDestroyed()) NotebookBrowserPreviews.remember(this, page); };
        content.postDelayed(previewUpdate, 700);
    }

    @Override public void onCreate(Bundle state) {
        boolean clearedRestore = state != null && state.getLong("browserDataRevision", 0) != NotebookBrowserData.revision(this);
        if (clearedRestore) state = null;
        super.onCreate(state);
        offline = getIntent().hasExtra(SAVED_ID);
        startupHome = state == null ? !getIntent().hasExtra(INITIAL_URL) : state.getBoolean("startupHome", false);
        NotebookBrowserData.owner(dataReset);
        if (!offline) { if (state != null) restoreTabs(state.getBundle("tabs")); else tabs.restore(getSharedPreferences("browser-tabs", MODE_PRIVATE).getString("tabs", null)); }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setWindowAnimations(0);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        FrameLayout root = new FrameLayout(this); root.setBackgroundColor(Color.WHITE);
        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(toolIcon(NotebookIconDrawable.Icon.LIBRARY, "Bookshelf", this::bookshelf), new LinearLayout.LayoutParams(dp(32), dp(32)));
        title = label(offline ? "Saved website" : "Browser", 18); title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END); title.setPadding(dp(12), 0, dp(12), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(32), 1));
        header.addView(workspaceToolButton(NotebookFloatingTools.Action.ZEN), new LinearLayout.LayoutParams(dp(32), dp(32)));
        header.addView(toolIcon(NotebookIconDrawable.Icon.MORE, "Browser menu", this::browserMenu), NotebookBrowserUi.beside(this, 32, 32));
        controls.addView(header);

        LinearLayout addressRow = new LinearLayout(this); addressRow.setGravity(Gravity.CENTER_VERTICAL);
        addressRow.setPadding(0, dp(12), 0, dp(12));
        address = new EditText(this) {
            @Override public void onWindowFocusChanged(boolean focused) {
                super.onWindowFocusChanged(focused);
                if (focused && hasFocus()) browserToolTextInput(this, true);
            }
        }; address.setTextColor(Color.BLACK); address.setTextSize(14);
        address.setSingleLine(true); address.setHint("Search or enter a website");
        address.setContentDescription("Website address"); address.setSelectAllOnFocus(true);
        address.setOnFocusChangeListener((view, focused) -> browserToolTextInput(view, focused));
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO); address.setEnabled(!offline);
        address.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO) { browse(); return true; } return false;
        });
        NotebookBrowserUi.field(address);
        addressRow.addView(address, new LinearLayout.LayoutParams(0, dp(48), 1));
        go = toolIcon(NotebookIconDrawable.Icon.NEXT, "Browse website address", this::browse); go.setVisibility(offline ? View.GONE : View.VISIBLE);
        addressRow.addView(go, NotebookBrowserUi.beside(this, 32, 32)); controls.addView(addressRow);

        NotebookBrowserActions tools = new NotebookBrowserActions(this);
        previous = toolIcon(NotebookIconDrawable.Icon.PREVIOUS, "Back", () -> history(false));
        next = toolIcon(NotebookIconDrawable.Icon.NEXT, "Forward", () -> history(true));
        reload = toolIcon(NotebookIconDrawable.Icon.RETRY, "Reload page", this::reload);
        tools.addView(previous); tools.addView(next); tools.addView(reload);
        if (!offline) {
            tools.addView(toolIcon(NotebookIconDrawable.Icon.HOME, "Home", () -> { if (!waitForSave()) showHome(true); }));
            tabButton = toolIcon(NotebookIconDrawable.Icon.TABS, "Browser tabs", this::showTabs); tools.addView(tabButton);
            tools.addView(toolIcon(NotebookIconDrawable.Icon.BOOKMARK, "Bookmark this page", () -> bookmarks(true, false)));
        }
        save = toolIcon(NotebookIconDrawable.Icon.SAVE, "Save page for offline reading", this::savePage);
        save.setVisibility(offline ? View.GONE : View.VISIBLE); tools.addView(save);
        if (offline) tools.addView(toolIcon(NotebookIconDrawable.Icon.BROWSER, "Open online",
            () -> { if (savedPage != null) openOnline(savedPage.url); }));
        tools.addView(toolIcon(NotebookIconDrawable.Icon.FOLDER, "Saved websites", this::savedWebsites));
        NotebookIconButton notes = workspaceToolButton(NotebookFloatingTools.Action.NOTES);
        notes.setContentDescription("Floating or split notes"); notes.setTooltipText("Floating or split notes"); tools.addView(notes);
        controls.addView(tools, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(this); divider.setBackgroundColor(0xffbbbbbb);
        LinearLayout.LayoutParams dividerSize = new LinearLayout.LayoutParams(-1, dp(1));
        dividerSize.setMargins(0, dp(12), 0, dp(8)); controls.addView(divider, dividerSize);
        LinearLayout details = new LinearLayout(this); details.setGravity(Gravity.CENTER_VERTICAL);
        status = label(offline ? "Opening offline copy…" : "Enter an address to browse. Save keeps this page for offline reading.", 12);
        status.setMaxLines(2); status.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(status, new LinearLayout.LayoutParams(0, dp(40), 1));
        controls.addView(details);
        LinearLayout writing = new LinearLayout(this); writing.setOrientation(LinearLayout.VERTICAL);
        writing.setVisibility(View.GONE);
        View writingDivider = new View(this); writingDivider.setBackgroundColor(0xffbbbbbb);
        LinearLayout.LayoutParams writingDividerSize = new LinearLayout.LayoutParams(-1, dp(1));
        writingDividerSize.setMargins(0, dp(8), 0, dp(10)); writing.addView(writingDivider, writingDividerSize);
        NotebookBrowserActions writingActions = new NotebookBrowserActions(this);
        for (NotebookFloatingTools.Action action : new NotebookFloatingTools.Action[] {
                NotebookFloatingTools.Action.PEN, NotebookFloatingTools.Action.ERASER, NotebookFloatingTools.Action.LASSO,
                NotebookFloatingTools.Action.ADD_PAGE, NotebookFloatingTools.Action.OUTLINE, NotebookFloatingTools.Action.AI_CHAT }) {
            NotebookIconButton button = workspaceToolButton(action);
            button.setTooltipText(button.getContentDescription()); writingActions.addView(button);
        }
        writing.addView(writingActions, new LinearLayout.LayoutParams(-1, -2)); controls.addView(writing);
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
        attachWorkspaceBackground(root); attachBrowserTools(controls, header, writing);
        if (state != null) { restoreX = state.getInt("scrollX"); restoreY = state.getInt("scrollY"); }
        if (offline) loadSavedPage(getIntent().getStringExtra(SAVED_ID), state == null ? null : state.getBundle("browserHistory"));
        else {
            String url = state == null ? (clearedRestore ? null : getIntent().getStringExtra(INITIAL_URL)) : state.getString("currentUrl");
            if (state != null && !NotebookBrowserAddress.isWebUrl(url) && !tabs.current().home) url = tabs.current().url;
            if (NotebookBrowserAddress.isWebUrl(url) && (state == null || !state.getBoolean("home", false))) {
                home = false; tabs.current().home = false; tabs.current().url = url; createWebView();
                Bundle history = state == null ? null : state.getBundle("browserHistory");
                if (web != null && history != null && web.restoreState(history) != null) {
                    currentUrl = web.getUrl(); address.setText(currentUrl); hint.setVisibility(View.GONE); startPage.setVisibility(View.GONE);
                } else navigate(url);
            } else {
                if (state == null && !tabs.current().home) tabs.startHome();
                showHome(state == null);
            }
        }
        updateTabButton(); updateControls();
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
        startupHome = false;
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
        if (offline || home || web == null || !pageReady || saving || !NotebookBrowserAddress.isWebUrl(web.getUrl())) return;
        final String url = web.getUrl(), pageTitle = web.getTitle();
        final long version = navigation;
        final long attempt = ++saveSequence;
        saving = true; updateControls(); status.setText("Saving offline copy…");
        archiveTimeout = () -> {
            if (attempt != saveSequence || !saving) return;
            NotebookWebsites.Pending pending = pendingSave;
            ++saveSequence;
            if (web != null) web.cancelCapture();
            saveFinished(null, new IOException("The offline capture timed out. Please retry, or reload the page if it keeps failing."));
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
                        discard(pending, navigation != version || web == null || !url.equals(web.getUrl())
                            ? "The page changed during saving. Please retry on the page you want to keep."
                            : web.captureError(), attempt); return;
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
            if (pageReady) { schedulePreview(); onBrowserDocumentReady(); }
        }

        @Override public void onDocumentReady(NotebookBrowserEngine view, String url) {
            PageState state = pageState(view); state.ready = !state.failed;
            if (view != web || home || !url.equals(view.getUrl()) || state.failed) return;
            pageReady = true; hint.setVisibility(View.GONE);
            if (!saving) status.setText(offline ? "Offline copy" : view.getTitle());
            updateControls(); schedulePreview(); onBrowserDocumentReady();
        }

        @Override public void onTitle(NotebookBrowserEngine view, String value) {
            remember(view, view.getUrl()); if (view != web || home) return;
            if (!offline && pageReady && !saving) status.setText(value);
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
                    .put("nativeLoading", loading).put("saveAvailable", save.isAvailable())
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
        revealWorkspaceTools(); updateControls();
    }

    private void updateControls() {
        if (go == null) return;
        go.setAvailable(!offline && !saving);
        previous.setAvailable(!saving && canHistory(false)); next.setAvailable(!saving && canHistory(true));
        reload.setAvailable(!saving && !home && (offline ? archiveUrl != null : NotebookBrowserAddress.isWebUrl(currentUrl)));
        save.setAvailable(!offline && !home && !saving && pageReady && web != null && NotebookBrowserAddress.isWebUrl(web.getUrl()));
        address.setEnabled(!offline && !saving);
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
        super.onResume(); resumed = true;
        if (offline && web == null && archiveUrl != null && !NotebookBrowserData.clearing()) { createWebView(); if (web != null) web.loadUrl(archiveUrl); }
        if (web != null && !home) web.onResume();
        if (!offline && home) showHome(false);
    }
    @Override protected void onPause() {
        if (!offline && !home && web != null && pageReady) NotebookBrowserPreviews.remember(this, web);
        resumed = false;
        // A dictionary bubble pauses this Activity while the page is still visible.
        // Gecko's active flag represents visibility; deactivate only onStop or tab hiding.
        if (web != null && !home) web.onWindowPause();
        if (shownDialog != null) { shownDialog.dismiss(); shownDialog = null; }
        if (!offline) rememberTabs();
        super.onPause();
    }

    @Override protected void onStop() {
        if (offline && web != null) web.onPause();
        for (NotebookBrowserEngine page : engines.values()) page.onPause();
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("browserDataRevision", NotebookBrowserData.revision(this));
        state.putString("currentUrl", currentUrl); state.putBoolean("home", home);
        state.putBoolean("startupHome", startupHome);
        if (!offline) { state.putBundle("tabs", saveTabs()); rememberTabs(); }
        if (web != null) {
            state.putInt("scrollX", web.pageScrollX()); state.putInt("scrollY", web.pageScrollY());
            Bundle history = new Bundle(); web.saveState(history); state.putBundle("browserHistory", history);
        }
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (content != null && previewUpdate != null) content.removeCallbacks(previewUpdate);
        NotebookBrowserData.removeOwner(dataReset);
        ++saveSequence; cancelArchiveTimeout();
        if (pendingSave != null) { abandonQuietly(pendingSave); pendingSave = null; }
        if (offline && web != null) { content.removeView(web); web.stopLoading(); web.destroy(); }
        for (NotebookBrowserEngine page : new java.util.ArrayList<>(engines.values())) { content.removeView(page); page.stopLoading(); page.destroy(); }
        engines.clear(); pageStates.clear(); web = null;
        // The writer's callback owns pending files. Incomplete directories are never listed
        // and old interrupted saves are reclaimed before a later save.
        super.onDestroy();
    }

    @Override public void onBackPressed() {
        if (workspaceZen()) { setWorkspaceZen(false); return; }
        if (!workspaceToolsCollapsed()) { collapseWorkspaceTools(true); return; }
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
        if (web != null && !home) NotebookBrowserPreviews.remember(this, web);
        home = true; if (!startupHome) tabs.current().home = true; if (web != null) { web.onPause(); web.setVisibility(View.GONE); }
        hint.setVisibility(View.GONE); startPage.setVisibility(View.VISIBLE); address.setText(""); title.setText("Browser"); status.setText("Start page · " + tabs.items.size() + " tabs");
        loading = false; pageReady = false; if (collapse) collapseWorkspaceTools(true); refreshHome(); updateControls(); updateTabButton();
    }
    private void showCurrentPage() {
        if (web == null) { navigate(tabs.current().url); return; }
        home = false; tabs.current().home = false; startPage.setVisibility(View.GONE); hint.setVisibility(View.GONE); web.setVisibility(View.VISIBLE);
        currentUrl = web.getUrl(); address.setText(currentUrl); PageState state = pageState(web); pageReady = state.ready; loading = state.loading; failed = state.failed; status.setText(loading ? "Loading…" : failed ? "Couldn’t load this page. Tap Reload to retry." : web.getTitle()); if (resumed) web.onResume(); updateControls(); updateTabButton();
    }
    private void updateTabButton() { if (tabButton != null) tabButton.setCount(tabs.items.size()); if (!offline) rememberTabs(); }
    private void activateTab(String id) {
        if (waitForSave()) return;
        startupHome = false;
        if (web != null && !home) NotebookBrowserPreviews.remember(this, web);
        if (web != null) { web.onPause(); web.setVisibility(View.GONE); }
        tabs.select(id); NotebookBrowserTabs.Tab tab = tabs.current(); web = engines.get(id); currentUrl = tab.url; home = tab.home;
        if (home || !NotebookBrowserAddress.isWebUrl(tab.url)) showHome(true);
        else if (web == null) navigate(tab.url); else showCurrentPage();
    }
    private void showTabs() {
        if (offline || waitForSave()) return;
        if (web != null && !home) NotebookBrowserPreviews.remember(this, web);
        boolean thumbnails = getSharedPreferences("browser-ui", MODE_PRIVATE).getBoolean("tabsThumbnails", false);
        int dialogWidth = Math.min(getResources().getConfiguration().screenWidthDp - 32, 760);
        int columns = Math.max(1, Math.min(3, (dialogWidth - 32) / 224)), at = 0;
        LinearLayout thumbnailRow = null;
        LinearLayout rows = NotebookBrowserUi.column(this);
        rows.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView count = label(tabs.items.size() + " of " + NotebookBrowserTabs.MAX + " open", 14);
        heading.addView(count, new LinearLayout.LayoutParams(0, dp(48), 1));
        heading.addView(icon(thumbnails ? NotebookIconDrawable.Icon.OUTLINE : NotebookIconDrawable.Icon.OVERVIEW,
            thumbnails ? "Show tabs as list" : "Show tab thumbnails", () -> {
                getSharedPreferences("browser-ui", MODE_PRIVATE).edit().putBoolean("tabsThumbnails", !thumbnails).apply();
                shownDialog.dismiss(); showTabs();
            }), NotebookBrowserUi.beside(this, 48, 48));
        NotebookBrowserIconButton add = icon(NotebookIconDrawable.Icon.PLUS, "New tab", () -> { if (shownDialog != null) shownDialog.dismiss(); try { NotebookBrowserTabs.Tab tab = tabs.add(); activateTab(tab.id); } catch (IllegalStateException error) { message("Tabs", error.getMessage()); } });
        add.setAvailable(tabs.items.size() < NotebookBrowserTabs.MAX);
        heading.addView(add, NotebookBrowserUi.beside(this, 48, 48)); rows.addView(heading);
        for (NotebookBrowserTabs.Tab tab : tabs.items) {
            if (thumbnails) {
                if (at % columns == 0) { thumbnailRow = new LinearLayout(this); thumbnailRow.setGravity(Gravity.TOP); rows.addView(thumbnailRow); }
                String name = tab.home ? "Start page" : tab.title;
                NotebookBrowserCard card = new NotebookBrowserCard(this, name, tab.home ? null : tab.url,
                    "Open tab " + name + (tab == tabs.current() ? ", current" : ""), tab == tabs.current(), false,
                    () -> { shownDialog.dismiss(); activateTab(tab.id); }, NotebookIconDrawable.Icon.CLOSE, "Close tab " + name, () -> closeTab(tab.id));
                LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -2, 1); size.topMargin = dp(10); if (at % columns != 0) size.setMarginStart(dp(10));
                thumbnailRow.addView(card, size); at++; continue;
            }
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            boolean selected = tab == tabs.current();
            android.graphics.drawable.GradientDrawable outline = NotebookUi.rounded(getResources().getDisplayMetrics().density, 7);
            if (selected) outline.setStroke(dp(2), Color.BLACK);
            row.setBackground(outline); row.setPadding(dp(12), dp(8), dp(8), dp(8));
            String name = tab.home ? "Start page" : tab.title;
            LinearLayout open = NotebookBrowserUi.column(this); open.setGravity(Gravity.CENTER_VERTICAL); open.setMinimumHeight(dp(56));
            TextView nameView = label(name, 16); nameView.setSingleLine(true); nameView.setEllipsize(TextUtils.TruncateAt.END);
            if (selected) nameView.setTypeface(null, android.graphics.Typeface.BOLD);
            NotebookIconDrawable marker = new NotebookIconDrawable(selected ? NotebookIconDrawable.Icon.CHECK : tab.home ? NotebookIconDrawable.Icon.HOME : NotebookIconDrawable.Icon.BROWSER);
            marker.setBounds(0, 0, dp(20), dp(20)); nameView.setCompoundDrawablesRelative(marker, null, null, null); nameView.setCompoundDrawablePadding(dp(8));
            open.addView(nameView, new LinearLayout.LayoutParams(-1, -2));
            TextView url = label(tab.home ? "Pinned websites and search" : tab.url, 12); url.setSingleLine(true); url.setEllipsize(TextUtils.TruncateAt.END); url.setPadding(0, dp(4), 0, 0); open.addView(url, new LinearLayout.LayoutParams(-1, -2));
            open.setContentDescription("Open tab " + name + (selected ? ", current" : "")); open.setFocusable(true);
            open.setOnClickListener(view -> { shownDialog.dismiss(); activateTab(tab.id); });
            row.addView(open, new LinearLayout.LayoutParams(0, -2, 1));
            NotebookBrowserIconButton close = icon(NotebookIconDrawable.Icon.CLOSE, "Close tab " + name, () -> closeTab(tab.id));
            row.addView(close, NotebookBrowserUi.beside(this, 48, 48));
            LinearLayout.LayoutParams rowSize = new LinearLayout.LayoutParams(-1, -2); rowSize.topMargin = dp(10); rows.addView(row, rowSize);
        }
        if (thumbnails && thumbnailRow != null && at % columns != 0) for (int i = at % columns; i < columns; i++) {
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, 1, 1); size.setMarginStart(dp(10)); thumbnailRow.addView(new View(this), size);
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this); scroll.setOverScrollMode(View.OVER_SCROLL_NEVER); scroll.addView(rows); shownDialog = new AlertDialog.Builder(this).setTitle("Tabs").setView(scroll).setNegativeButton("Done", null).create(); NotebookUi.show(shownDialog);
        shownDialog.getWindow().setLayout(dp(dialogWidth), -2);
    }

    private void closeTab(String id) {
        if (shownDialog != null) shownDialog.dismiss();
        NotebookBrowserEngine page = engines.remove(id);
        if (page != null) { content.removeView(page); page.destroy(); pageStates.remove(page); if (web == page) web = null; }
        tabs.close(id); activateTab(tabs.current().id);
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

    private NotebookBrowserIconButton toolIcon(NotebookIconDrawable.Icon symbol, String name, Runnable action) {
        return new NotebookBrowserIconButton(this, symbol, name, action, true);
    }

    private NotebookBrowserIconButton icon(NotebookIconDrawable.Icon symbol, String name, Runnable action) {
        return NotebookBrowserUi.icon(this, symbol, name, action);
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(Color.BLACK);
        view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
