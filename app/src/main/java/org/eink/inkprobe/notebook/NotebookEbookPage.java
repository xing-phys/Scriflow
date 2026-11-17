package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Base64;
import android.widget.FrameLayout;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.json.JSONObject;
import org.mozilla.geckoview.*;

/** A trusted offline extension UI; book content receives no native port or filesystem capability. */
final class NotebookEbookPage extends FrameLayout {
    private static final String ID = "ebooks@scriflow.local", CHANNEL = "scriflowEbooks";
    private static GeckoResult<WebExtension> installed;
    private static final Map<GeckoSession, Consumer<WebExtension.Port>> OWNERS = new IdentityHashMap<>();
    private static final WebExtension.MessageDelegate ROUTER = new WebExtension.MessageDelegate() {
        @Override public void onConnect(WebExtension.Port sender) {
            Consumer<WebExtension.Port> owner=OWNERS.get(sender.sender.session);
            if(owner==null)sender.disconnect();else owner.accept(sender);
        }
    };
    private final GeckoSession session;
    private final GeckoRuntime runtime;
    private final GeckoView surface;
    private final NotebookEbookStore store;
    private final String book;
    private final Consumer<JSONObject> listener;
    private final boolean debug;
    private final NotebookBrowserPrompts prompts;
    private WebExtension.Port port;
    private String page, origin;
    private boolean closed;
    private JSONObject pendingConfig;
    private final Runnable saveState = this::writeState;
    private String pendingState;
    private long latestModified;

    NotebookEbookPage(Activity context, NotebookEbookStore store, String book, Consumer<JSONObject> listener) {
        super(context); this.store = store; this.book = book; this.listener = listener;
        debug=(context.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0;
        session = new GeckoSession(new GeckoSessionSettings.Builder().allowJavascript(true).suspendMediaWhenInactive(true).build());
        prompts=new NotebookBrowserPrompts(context,()->null,"Reading options");session.setPromptDelegate(prompts);
        surface = new GeckoView(context); addView(surface, new LayoutParams(-1, -1));
        session.setContentDelegate(new GeckoSession.ContentDelegate() { });
        session.setPermissionDelegate(new GeckoSession.PermissionDelegate() { });
        session.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override public void onPageStop(GeckoSession owner, boolean success) {
                if (!success && !closed) fail("The offline reader could not start. Close this book and retry.");
            }
        });
        session.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession owner, LoadRequest request) {
                return allowed(request.uri, true) ? GeckoResult.allow() : GeckoResult.deny();
            }
            @Override public GeckoResult<AllowOrDeny> onSubframeLoadRequest(GeckoSession owner, LoadRequest request) {
                return allowed(request.uri, false) ? GeckoResult.allow() : GeckoResult.deny();
            }
        });
        runtime = NotebookBrowserRuntime.get(context); session.open(runtime); surface.setSession(session);
        if (installed == null) {
            GeckoResult<WebExtension> result=runtime.getWebExtensionController().ensureBuiltIn("resource://android/assets/ebook-extension/", ID);
            // Local rebuilds can share a versionCode; refresh only this debug-only
            // built-in extension, without clearing any browser or book data.
            if(debug)result=result.then(value->runtime.getWebExtensionController().uninstall(value)
                .then(ignored->runtime.getWebExtensionController().ensureBuiltIn("resource://android/assets/ebook-extension/",ID)));
            installed=result;
        }
        OWNERS.put(session, this::connect);
        installed.accept(extension -> {
            if (closed) return;
            page = extension.metaData.optionsPageUrl; origin = Uri.parse(page).buildUpon().path("/").build().toString();
            extension.setMessageDelegate(ROUTER, CHANNEL);
            session.getWebExtensionController().setMessageDelegate(extension, new WebExtension.MessageDelegate() {
                @Override public void onConnect(WebExtension.Port sender) { connect(sender); }
            }, CHANNEL);
            session.loadUri(page);
        }, error -> fail("Could not start the offline ebook engine."));
    }
    private boolean allowed(String uri, boolean main) {
        if (closed) return false;
        if (main) return page != null && (uri.equals(page) || uri.startsWith(page + "#"));
        return origin != null && uri.startsWith("blob:"+origin) || uri.startsWith("data:") || uri.equals("about:blank") || uri.equals("about:srcdoc")
            || origin != null && uri.startsWith(origin);
    }
    private void connect(WebExtension.Port sender) {
        if (closed || sender.sender.session != session || !sender.sender.isTopLevel()
                || !ID.equals(sender.sender.webExtension.id)
                || sender.sender.environmentType != WebExtension.MessageSender.ENV_TYPE_EXTENSION || !page.equals(sender.sender.url)) {
            sender.disconnect(); return;
        }
        port = sender;
        sender.setDelegate(new WebExtension.PortDelegate() {
            @Override public void onPortMessage(Object value, WebExtension.Port owner) {
                if (closed || port != owner || !(value instanceof JSONObject)) return;
                JSONObject message = (JSONObject) value; String type = message.optString("type");
                if (type.equals("hello")) configure();
                else if (type.equals("read")) read(owner, message);
                else if (type.equals("state")) {
                    JSONObject state = message.optJSONObject("state");
                    if (state != null && state.optInt("version") == 1) {
                        if (state.optLong("modified") < latestModified) return;
                        latestModified = state.optLong("modified");
                        pendingState = state.toString(); removeCallbacks(saveState); postDelayed(saveState, 400);
                    }
                } else listener.accept(message);
            }
            @Override public void onDisconnect(WebExtension.Port owner) { if (port == owner) port = null; }
        });
    }
    private void configure() {
        if (pendingConfig != null) { send(pendingConfig); return; }
        NotebookEbookTasks.run(() -> {
            NotebookEbookStore.Book entry = store.book(book);
            return new JSONObject().put("type", "open").put("id", book).put("name", entry.documentTitle + "." + entry.format)
                .put("displayTitle",entry.label==null?"":entry.title)
                .put("format", entry.format).put("size", entry.bytes).put("fontSize", store.font(book).isFile() ? store.font(book).length() : 0)
                .put("debug",debug).put("state", new JSONObject(store.state(book)));
        }, (value, error) -> { if (!closed) { if (error != null) fail(error.getMessage()); else {
            latestModified=value.optJSONObject("state").optLong("modified");pendingConfig = value; send(value);
        } } });
    }
    private void read(WebExtension.Port owner, JSONObject request) {
        long offset = request.optLong("offset", -1); int length = request.optInt("length", -1), id = request.optInt("id", -1);
        if (id < 0 || offset < 0 || length < 0 || length > 262144) { fail("Invalid book read request."); return; }
        NotebookEbookTasks.run(() -> {
            String channel = request.optString("channel", "book");
            if (!channel.equals("book") && !channel.equals("font")) throw new IOException("Invalid reading resource.");
            File source = channel.equals("font") ? store.font(book) : store.source(book);
            if (offset > source.length() || length > source.length() - offset) throw new IOException("The book file is truncated.");
            byte[] bytes = new byte[length];
            try (RandomAccessFile file = new RandomAccessFile(source, "r")) { file.seek(offset); file.readFully(bytes); }
            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        }, (data, error) -> {
            if (closed || port != owner) return;
            try { owner.postMessage(new JSONObject().put("type", "read-result").put("id", id)
                .put("data", data).put("error", error == null ? JSONObject.NULL : error.getMessage())); }
            catch (org.json.JSONException impossible) { fail("Could not read the book."); }
        });
    }
    private void writeState() {
        String state = pendingState; pendingState = null; if (state == null) return;
        NotebookEbookTasks.run(() -> { store.state(book, state); return null; }, (value, error) -> { if (error != null && !closed) fail("Couldn’t save book annotations. " + error.getMessage()); });
    }
    void send(JSONObject message) { if (!closed && port != null) port.postMessage(message); }
    void command(String type) { try { send(new JSONObject().put("type", type)); } catch (org.json.JSONException impossible) { throw new AssertionError(impossible); } }
    void replaceState(JSONObject state, Consumer<Exception> completed) {
        try {
            latestModified=Math.max(System.currentTimeMillis(),latestModified+1);state.put("modified",latestModified);
            pendingState=null;removeCallbacks(saveState);String json=state.toString();
            NotebookEbookTasks.run(()->{store.state(book,json);return null;},(value,error)->{if(error!=null)fail(error.getMessage());completed.accept(error);});
            if(pendingConfig!=null)pendingConfig.put("state",state);
            send(new JSONObject().put("type", "state-updated").put("state", state));
        }
        catch (org.json.JSONException impossible) { throw new AssertionError(impossible); }
    }
    void active(boolean value) {
        if (closed) return;
        if (!value) { prompts.close();command("flush"); removeCallbacks(saveState); writeState(); }
        session.setActive(value);
        runtime.getWebExtensionController().setTabActive(session,value);
    }
    void snapshot(Consumer<Bitmap> callback) { if (closed) callback.accept(null); else surface.capturePixels().accept(callback::accept, error -> callback.accept(null)); }
    void close() {
        if (closed) return; prompts.close();removeCallbacks(saveState); writeState(); closed = true;
        OWNERS.remove(session); if (port != null) port.disconnect(); port = null; surface.releaseSession(); session.close();
    }
    private void fail(String message) {
        if (closed) return;
        try { listener.accept(new JSONObject().put("type", "error").put("message", message == null ? "Could not open this book." : message)); }
        catch (org.json.JSONException impossible) { throw new AssertionError(impossible); }
    }
}
