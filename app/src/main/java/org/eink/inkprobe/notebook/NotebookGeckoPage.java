package org.eink.inkprobe.notebook;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.WebResponse;

/** Current Firefox engine plus an isolated, app-owned capture extension. */
final class NotebookGeckoPage extends NotebookBrowserEngine {
    private final GeckoRuntime runtime;
    private final GeckoSession session;
    private final GeckoView surface;
    private final NotebookBrowserPrompts prompts;
    private final boolean offline;
    private final String archive;
    private WebExtension extension;
    private WebExtension.Port pagePort;
    private String bootstrap, url, title = "", pendingUrl;
    private GeckoSession.SessionState sessionState, pendingState;
    private History history = new History(new ArrayList<>(), -1);
    private boolean closed, ready, back, forward, fresh = true;
    private int warnings, scrollX, scrollY;
    private Capture capture;
    private final Map<String, Consumer<JSONObject>> probes = new HashMap<>();

    NotebookGeckoPage(Context context, boolean offline, String archive, Listener listener) {
        super(context, listener); this.offline = offline; this.archive = archive == null ? "" : NotebookBrowserAddress.withoutFragment(archive);
        runtime = NotebookBrowserRuntime.get(context);
        session = new GeckoSession(new GeckoSessionSettings.Builder().allowJavascript(true).suspendMediaWhenInactive(true).build());
        prompts = new NotebookBrowserPrompts(context, () -> url);
        if (!offline) session.setPromptDelegate(prompts);
        surface = new GeckoView(context); addView(surface, new LayoutParams(-1, -1));
        session.setScrollDelegate(new GeckoSession.ScrollDelegate() {
            @Override public void onScrollChanged(GeckoSession owner, int x, int y) { scrollX = x; scrollY = y; }
        });
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession owner, LoadRequest request) {
                return navigation(request, true);
            }
            @Override public GeckoResult<AllowOrDeny> onSubframeLoadRequest(GeckoSession owner, LoadRequest request) {
                return navigation(request, false);
            }
            @Override public void onLocationChange(GeckoSession owner, String value,
                    List<GeckoSession.PermissionDelegate.ContentPermission> permissions, Boolean gesture) {
                if (closed || internal(value)) return;
                url = value; listener.onHistoryChanged(NotebookGeckoPage.this, value);
                // Session-store history normally arrives on a delayed timer. Request
                // its current index after each location change, including hash jumps.
                post(() -> { if (!closed) session.flushSessionState(); });
            }
            @Override public void onCanGoBack(GeckoSession owner, boolean value) { back = value; changed(); }
            @Override public void onCanGoForward(GeckoSession owner, boolean value) { forward = value; changed(); }
            @Override public GeckoResult<String> onLoadError(GeckoSession owner, String value, WebRequestError error) {
                if (!closed && !internal(value)) listener.onError(NotebookGeckoPage.this,
                    error.category == WebRequestError.ERROR_CATEGORY_SECURITY ? "The website’s secure connection could not be verified."
                        : "Couldn’t load this page. Check the address and connection, then tap Reload.");
                return null; // Never install a certificate exception or retry insecurely.
            }
        });
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStart(GeckoSession owner, String value) {
                if (closed || internal(value)) return;
                prompts.close(); cancelCapture(); url = value; listener.onPageStarted(NotebookGeckoPage.this, value);
            }
            @Override public void onPageStop(GeckoSession owner, boolean success) {
                if (closed || url == null || internal(url)) return;
                if (fresh) { fresh = false; session.purgeHistory(); back = forward = false; }
                if (success) listener.onPageFinished(NotebookGeckoPage.this, url);
                else listener.onError(NotebookGeckoPage.this, "Couldn’t finish loading this page. Tap Reload to retry.");
            }
            @Override public void onSessionStateChange(GeckoSession owner, GeckoSession.SessionState value) { sessionState = value; }
        });
        session.setHistoryDelegate(new GeckoSession.HistoryDelegate() {
            @Override public void onHistoryStateChange(GeckoSession owner, HistoryList values) {
                ArrayList<String> urls = new ArrayList<>();
                for (HistoryItem value : values) urls.add(value.getUri());
                history = new History(urls, values.getCurrentIndex()); changed();
            }
        });
        session.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override public void onTitleChange(GeckoSession owner, String value) {
                if (closed || url == null || internal(url)) return;
                title = value == null ? "" : value; listener.onTitle(NotebookGeckoPage.this, title);
            }
            @Override public void onCrash(GeckoSession owner) { if (!closed) listener.onRendererGone(NotebookGeckoPage.this); }
            @Override public void onKill(GeckoSession owner) { if (!closed) listener.onRendererGone(NotebookGeckoPage.this); }
            @Override public void onExternalResponse(GeckoSession owner, WebResponse response) {
                if (response.body != null) try { response.body.close(); } catch (IOException ignored) { }
                if (!closed) listener.onDownload(NotebookGeckoPage.this);
            }
        });
        session.setPermissionDelegate(new GeckoSession.PermissionDelegate() {
            @Override public void onAndroidPermissionsRequest(GeckoSession owner, String[] permissions, Callback callback) { callback.reject(); }
            @Override public GeckoResult<Integer> onContentPermissionRequest(GeckoSession owner, ContentPermission permission) {
                return GeckoResult.fromValue(ContentPermission.VALUE_DENY);
            }
        });
        session.open(runtime); surface.setSession(session);
        NotebookBrowserRuntime.extension(context).accept(value -> {
            if (closed) return;
            extension = value; bootstrap = value.metaData.optionsPageUrl;
            NotebookBrowserRuntime.bind(value, session, this::connect);
            session.getWebExtensionController().setMessageDelegate(value, new WebExtension.MessageDelegate() {
                @Override public void onConnect(WebExtension.Port port) { connect(port); }
            }, "scriflow");
            session.loadUri(bootstrap);
        }, error -> { if (!closed) listener.onError(this, "Could not start the browser’s archive and offline protection component."); });
    }

    private boolean internal(String value) { return value == null || value.equals("about:blank") || value.equals(bootstrap); }
    private boolean allowed(String value, boolean main) {
        Uri uri = Uri.parse(value); String scheme = uri.getScheme();
        if (offline) return archive.equals(NotebookBrowserAddress.withoutFragment(value))
            || !main && NotebookBrowserAddress.isEmbeddedScheme(scheme);
        return "https".equals(scheme) || !main && NotebookBrowserAddress.isEmbeddedScheme(scheme);
    }
    private GeckoResult<AllowOrDeny> navigation(GeckoSession.NavigationDelegate.LoadRequest request, boolean main) {
        if (closed) return GeckoResult.deny();
        if (!ready && request.uri.equals(bootstrap)) return GeckoResult.allow();
        if (!ready || !allowed(request.uri, main)) {
            if (ready && main) listener.overrideNavigation(this, request.uri, true, request.hasUserGesture);
            return GeckoResult.deny();
        }
        if (listener.overrideNavigation(this, request.uri, main, request.hasUserGesture)) return GeckoResult.deny();
        if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
            if (request.hasUserGesture) post(() -> { if (!closed) loadUrl(request.uri); });
            return GeckoResult.deny();
        }
        return GeckoResult.allow();
    }
    private void changed() { if (!closed && url != null) listener.onHistoryChanged(this, url); }

    private void connect(WebExtension.Port port) {
        WebExtension.MessageSender sender = port.sender;
        if (closed || sender.session != session || !sender.isTopLevel()
                || !NotebookBrowserRuntime.EXTENSION_ID.equals(sender.webExtension.id)) { port.disconnect(); return; }
        boolean boot = sender.environmentType == WebExtension.MessageSender.ENV_TYPE_EXTENSION && sender.url.equals(bootstrap);
        if (!boot && (!ready || sender.environmentType != WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT || !allowed(sender.url, true))) {
            port.disconnect(); return;
        }
        port.setDelegate(new WebExtension.PortDelegate() {
            @Override public void onPortMessage(Object value, WebExtension.Port owner) {
                if (closed || !(value instanceof JSONObject)) return;
                JSONObject message = (JSONObject) value;
                if (boot) {
                    if (message.optString("type").equals("configured")) {
                        ready = true; session.getSettings().setAllowJavascript(!offline);
                        if (pendingState != null) { fresh = false; session.restoreState(pendingState); pendingState = null; }
                        else if (pendingUrl != null) { String target = pendingUrl; pendingUrl = null; session.loadUri(target); }
                    } else if (message.optString("type").equals("error")) listener.onError(NotebookGeckoPage.this, "Could not configure offline protection.");
                } else message(owner, message);
            }
            @Override public void onDisconnect(WebExtension.Port owner) {
                if (pagePort == owner) {
                    pagePort = null; cancelCapture();
                    for (Consumer<JSONObject> callback : probes.values()) callback.accept(null);
                    probes.clear();
                }
            }
        });
        if (boot) port.postMessage(json("type", "configure", "offline", offline, "archive", archive));
        else pagePort = port;
    }

    private void message(WebExtension.Port port, JSONObject message) {
        if (port != pagePort) return;
        String type = message.optString("type"), id = message.optString("id");
        if (type.equals("stats")) { Consumer<JSONObject> callback = probes.remove(id); if (callback != null) callback.accept(message); return; }
        Capture job = capture;
        if (job == null || !job.id.equals(id)) return;
        if (type.equals("chunk")) {
            String text = message.optString("text"); int sequence = message.optInt("sequence", -1);
            if (sequence != job.sequence || text.length() > 65536 || text.isEmpty()) { cancelCapture(); return; }
            job.sequence++;
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8); job.bytes += bytes.length;
            if (job.bytes > 64L * 1024 * 1024) { cancelCapture(); return; }
            NotebookWebsiteTasks.run(() -> { if (job.output == null) throw new IOException("Capture writer is closed"); job.output.write(bytes); return null; }, (value, error) -> {
                if (job != capture || closed) return;
                if (error != null) cancelCapture(); else port.postMessage(json("type", "ack", "sequence", sequence));
            });
        } else if (type.equals("complete")) {
            if (message.optInt("chunks", -1) != job.sequence || !job.url.equals(message.optString("url")) || !job.url.equals(url)) { cancelCapture(); return; }
            warnings = Math.max(0, message.optInt("missing"));
            NotebookWebsiteTasks.run(() -> { job.output.getFD().sync(); job.output.close(); job.output = null; return job.path; }, (path, error) -> {
                if (job != capture) return;
                capture = null; job.callback.accept(error == null && !closed ? path : null);
            });
        } else if (type.equals("error")) cancelCapture();
    }

    @Override void saveWebArchive(String path, boolean automatic, Consumer<String> callback) {
        cancelCapture(); warnings = 0;
        if (offline || closed || pagePort == null || url == null) { callback.accept(null); return; }
        Capture job = new Capture(path, url, callback); capture = job;
        NotebookWebsiteTasks.run(() -> {
            File destination = new File(path).getAbsoluteFile();
            File root = new File(getContext().getFilesDir(), "websites").getCanonicalFile();
            File parent = destination.getParentFile();
            if (!destination.equals(destination.getCanonicalFile()) || parent == null || !root.equals(parent.getParentFile())
                    || !parent.getName().matches("\\.saving-[a-f0-9-]{36}") || !destination.getName().equals("page.html"))
                throw new IOException("Invalid capture destination");
            job.output = new FileOutputStream(destination); return null;
        }, (value, error) -> {
            if (capture != job || closed) { close(job); return; }
            if (error != null || pagePort == null) cancelCapture();
            else pagePort.postMessage(json("type", "capture", "id", job.id));
        });
    }
    private void close(Capture job) {
        NotebookWebsiteTasks.run(() -> { if (job.output != null) { job.output.close(); job.output = null; } return null; }, (value, error) -> { });
    }
    @Override void cancelCapture() {
        Capture job = capture; capture = null;
        if (job == null) return;
        if (pagePort != null) pagePort.postMessage(json("type", "cancel", "id", job.id));
        close(job); job.callback.accept(null);
    }

    /** Used only by the debug-only on-device fixture; no page receives an app API. */
    void probe(Consumer<JSONObject> callback) {
        if ((getContext().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) == 0 || pagePort == null || closed) { callback.accept(null); return; }
        String id = UUID.randomUUID().toString(); probes.put(id, callback); pagePort.postMessage(json("type", "stats", "id", id));
        postDelayed(() -> { Consumer<JSONObject> waiting = probes.remove(id); if (waiting != null) waiting.accept(null); }, 10000);
    }
    @Override String getUrl() { return url; }
    @Override String getTitle() { return title; }
    @Override void loadUrl(String value) { if (closed) return; if (ready) session.loadUri(value); else pendingUrl = value; }
    @Override void reload() { if (!closed && ready) session.reload(); }
    @Override boolean canGoBack() { return ready && !fresh && back; }
    @Override boolean canGoForward() { return ready && !fresh && forward; }
    @Override void goBack() { if (!closed && canGoBack()) session.goBack(); }
    @Override void goForward() { if (!closed && canGoForward()) session.goForward(); }
    @Override void clearHistory() { session.purgeHistory(); back = forward = false; }
    @Override History copyBackForwardList() { return history; }
    @Override History restoreState(Bundle saved) {
        if (!"gecko".equals(saved.getString("engine"))) return null;
        GeckoSession.SessionState restored = GeckoSession.SessionState.fromString(saved.getString("state"));
        History values = History.restore(saved);
        if (restored == null || values == null || values.getSize() == 0 || values.getCurrentIndex() < 0
                || values.getCurrentIndex() >= values.getSize()) return null;
        for (int i = 0; i < values.getSize(); i++) if (!allowed(values.getItemAtIndex(i).getUrl(), true)) return null;
        history = values; fresh = false; url = values.getItemAtIndex(values.getCurrentIndex()).getUrl();
        if (ready) session.restoreState(restored); else pendingState = restored;
        return values;
    }
    @Override void saveState(Bundle saved) {
        if (sessionState == null) return;
        String state = sessionState.toString(); if (state == null || state.length() > 256 * 1024) return;
        saved.putString("engine", "gecko"); saved.putString("state", state); history.save(saved);
    }
    @Override void stopLoading() { if (!closed) session.stop(); }
    @Override void onResume() { if (!closed) { session.setActive(true); runtime.getWebExtensionController().setTabActive(session, true); } }
    @Override void onPause() { if (!closed) { prompts.close(); session.flushSessionState(); session.setActive(false); runtime.getWebExtensionController().setTabActive(session, false); } }
    @Override void destroy() {
        if (closed) return; prompts.close(); cancelCapture(); closed = true;
        for (Consumer<JSONObject> callback : probes.values()) callback.accept(null); probes.clear();
        NotebookBrowserRuntime.unbind(session); surface.releaseSession(); session.close(); removeView(surface);
    }
    @Override int captureWarnings() { return warnings; }
    @Override int pageScrollX() { return scrollX; }
    @Override int pageScrollY() { return scrollY; }
    private static JSONObject json(Object... pairs) {
        JSONObject result = new JSONObject();
        try { for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]); }
        catch (JSONException error) { throw new IllegalArgumentException(error); }
        return result;
    }
    private static final class Capture {
        final String id = UUID.randomUUID().toString(), path, url;
        final Consumer<String> callback;
        FileOutputStream output;
        int sequence;
        long bytes;
        Capture(String path, String url, Consumer<String> callback) { this.path = path; this.url = url; this.callback = callback; }
    }
}
