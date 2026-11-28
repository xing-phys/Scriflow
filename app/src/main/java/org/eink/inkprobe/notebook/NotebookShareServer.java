package org.eink.inkprobe.notebook;

import static org.eink.inkprobe.notebook.NotebookLocalHttp.bytes;
import static org.eink.inkprobe.notebook.NotebookLocalHttp.respond;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.eink.inkprobe.notebook.NotebookLocalHttp.Request;

/** One explicitly started sharing session. No routes map browser input to a filesystem path. */
final class NotebookShareServer implements Closeable {
    static final long MAX_FILE_BYTES = 1024L * 1024 * 1024;
    static final int MAX_OFFERED = 32;
    private static final long MAX_STAGED_BYTES = 2 * MAX_FILE_BYTES;
    private static final int MAX_RECEIVED = 40;
    private static final String BINARY = "application/octet-stream", LOGIN = "Enter the pairing code.";

    interface Inbox { PendingFile create(String name) throws IOException; }
    interface PendingFile extends Closeable {
        OutputStream open() throws IOException;
        SavedFile publish() throws IOException;
    }
    static final class SavedFile {
        final String name, uri;
        SavedFile(String name, String uri) { this.name = name; this.uri = uri; }
    }
    static final class Item {
        final int id;
        final String name, url, receivedUri;
        final File stagedFile;
        final long size;

        private Item(int id, String name, String url, String receivedUri, File stagedFile, long size) {
            this.id = id;
            this.name = name;
            this.url = url;
            this.receivedUri = receivedUri;
            this.stagedFile = stagedFile;
            this.size = size;
        }
    }

    private final Map<String, byte[]> assets;
    private final Inbox inbox;
    private final NotebookLocalSessions sessions;
    private final ServerSocket listener;
    private final String authority;
    private final Set<Socket> sockets = Collections.newSetFromMap(new ConcurrentHashMap<Socket, Boolean>());
    private final Semaphore transfers = new Semaphore(4);
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, 12, 20, TimeUnit.SECONDS,
        new SynchronousQueue<>(), task -> daemon(task, "wifi-share-client"));
    private volatile List<Item> offered = Collections.emptyList(), received = Collections.emptyList();
    private volatile boolean closed;
    private int nextId;
    private long stagedBytes;

    NotebookShareServer(InetAddress address, String code, Map<String, byte[]> assets, Inbox inbox) throws IOException {
        this.assets = assets;
        this.inbox = inbox;
        sessions = new NotebookLocalSessions("scriflow_share", code, 8, () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
        listener = new ServerSocket();
        try { listener.bind(new InetSocketAddress(address, 0), 16); }
        catch (IOException error) { listener.close(); throw error; }
        authority = address.getHostAddress() + ":" + listener.getLocalPort();
        daemon(this::accept, "wifi-share-listener").start();
    }

    String address() { return "http://" + authority + "/"; }
    boolean closed() { return closed; }
    List<Item> offered() { return offered; }
    List<Item> received() { return received; }

    synchronized long stagingLimit() throws IOException {
        checkOfferRoom();
        return Math.min(MAX_FILE_BYTES, MAX_STAGED_BYTES - stagedBytes);
    }

    synchronized void offerFile(String name, File file, long size) throws IOException {
        if (size < 0 || size > stagingLimit()) throw new IOException("Remove shared files to make room for this file.");
        offered = append(offered, new Item(++nextId, NotebookDownloadName.clean(name), null, null, file, size));
        stagedBytes += size;
    }

    synchronized void offerLink(String raw) throws IOException {
        String url = webLink(raw);
        checkOfferRoom();
        offered = append(offered, new Item(++nextId, url, url, null, null, 0));
    }

    synchronized File withdraw(Item item) {
        List<Item> remaining = new ArrayList<>(offered);
        if (!remaining.remove(item)) return null;
        offered = Collections.unmodifiableList(remaining);
        stagedBytes -= item.size;
        return item.stagedFile;
    }

    private void checkOfferRoom() throws IOException {
        if (closed) throw new IOException("Wi-Fi sharing has stopped.");
        if (offered.size() >= MAX_OFFERED) throw new IOException("Remove some shared items first. Up to 32 can be shared at once.");
    }

    private synchronized void remember(String name, String url, String uri, long size) {
        if (closed) return;
        List<Item> recent = received.size() == MAX_RECEIVED ? received.subList(0, MAX_RECEIVED - 1) : received;
        received = append(recent, new Item(++nextId, name, url, uri, null, size));
    }

    private static List<Item> append(List<Item> current, Item item) {
        List<Item> updated = new ArrayList<>(current.size() + 1);
        updated.add(item);
        updated.addAll(current);
        return Collections.unmodifiableList(updated);
    }

    private static Thread daemon(Runnable action, String name) {
        Thread thread = new Thread(action, name);
        thread.setDaemon(true);
        return thread;
    }

    private void accept() {
        while (!closed) {
            Socket client = null;
            try {
                client = listener.accept();
                sockets.add(client);
                if (closed) { retire(client); return; }
                client.setSoTimeout(4000);
                Socket socket = client;
                workers.execute(() -> serve(socket));
            } catch (IOException | RejectedExecutionException stopped) {
                if (client != null) retire(client);
                if (listener.isClosed()) return;
            }
        }
    }

    private void serve(Socket client) {
        try {
            Request request = NotebookLocalHttp.read(client);
            String rejection = request.rejection(authority);
            if (rejection != null) { message(client, 403, rejection); return; }
            String path = request.path;
            if ("GET".equals(request.method) && path.equals("/")) {
                asset(client, "index.html", "text/html"); return;
            }
            if ("GET".equals(request.method) && path.equals("/share.css")) {
                asset(client, "share.css", "text/css"); return;
            }
            if ("GET".equals(request.method) && path.equals("/share.js")) {
                asset(client, "share.js", "text/javascript"); return;
            }
            boolean post = "POST".equals(request.method);
            if (post && !"1".equals(request.headers.get("x-scriflow-share"))) {
                message(client, 403, "Open the sharing page to send items."); return;
            }
            String token = sessions.readCookie(request.headers.get("cookie"));
            if (post && path.equals("/connect")) { login(client, request, token); return; }
            // Check authorization before reading an upload body or opening a destination.
            if (!sessions.authenticated(token)) { message(client, 401, LOGIN); return; }
            if (post && path.equals("/disconnect")) {
                sessions.signOut(token);
                respond(client, 204, "text/plain", null, sessions.cookieHeader(null));
            } else if (post && path.equals("/upload")) {
                upload(client, request, token);
            } else if (post && path.equals("/link")) {
                String url = webLink(readText(request, 32 * 1024));
                if (!sessions.authenticated(token) || closed) { message(client, 401, LOGIN); return; }
                remember(url, url, null, 0);
                message(client, 200, "Link sent to the tablet.");
            } else if ("GET".equals(request.method) && path.equals("/items")) {
                respond(client, 200, "application/json; charset=utf-8", bytes(itemsJson()), null);
            } else if ("GET".equals(request.method) && path.startsWith("/download/")) {
                download(client, path.substring("/download/".length()));
            } else message(client, 404, "Not found");
        } catch (IllegalArgumentException invalid) {
            String detail = invalid.getMessage() == null ? "Invalid request." : invalid.getMessage();
            try { message(client, 400, detail); } catch (IOException disconnected) { }
        } catch (IOException disconnected) {
            // Closing a browser tab or stopping the session closes its streams.
        } finally { retire(client); }
    }

    private void asset(Socket client, String name, String type) throws IOException {
        byte[] body = assets.get(name);
        respond(client, body == null ? 404 : 200, type + "; charset=utf-8", body, null);
    }

    private void login(Socket client, Request request, String previous) throws IOException {
        if (!sessions.allowAttempt(client.getInetAddress().getHostAddress())) {
            respond(client, 429, "text/plain; charset=utf-8", bytes("Too many attempts. Wait a minute."), "Retry-After: 60\r\n");
            return;
        }
        String code = readText(request, 64).trim().toUpperCase(Locale.ROOT);
        if (!sessions.matches(code)) { message(client, 401, "Incorrect pairing code."); return; }
        String token = sessions.signIn(previous);
        if (token == null) { message(client, 503, "Too many browsers are connected. Disconnect one and try again."); return; }
        respond(client, 204, "text/plain", null, sessions.cookieHeader(token));
    }

    private void upload(Socket client, Request request, String token) throws IOException {
        if (request.length > MAX_FILE_BYTES) { message(client, 413, "Choose a file of 1 GB or less."); return; }
        if (!BINARY.equalsIgnoreCase(request.headers.get("content-type")) || !request.headers.containsKey("content-length")
                || request.headers.containsKey("content-encoding")) {
            message(client, 400, "Send the file using the sharing page."); return;
        }
        String encoded = request.headers.get("x-file-name");
        if (encoded == null || encoded.length() > 3072) { message(client, 400, "This file name is missing or too long."); return; }
        String name = NotebookDownloadName.clean(URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8"));
        if (name.startsWith(".")) name = "shared" + name;
        if (!transfers.tryAcquire()) { message(client, 503, "Other files are transferring. Try again in a moment."); return; }
        SavedFile saved;
        try {
            client.setSoTimeout(20_000);
            try (PendingFile pending = inbox.create(name)) {
                try (OutputStream output = pending.open()) { NotebookLocalHttp.copy(request.input, output, request.length); }
                if (!sessions.authenticated(token) || closed) { message(client, 401, LOGIN); return; }
                saved = pending.publish();
            }
        } catch (IOException | RuntimeException failure) {
            message(client, 500, "The tablet could not save the complete file. Check available space and try again.");
            return;
        } finally { transfers.release(); }
        remember(saved.name, null, saved.uri, request.length);
        message(client, 200, "Saved to tablet Downloads as " + saved.name);
    }

    private void download(Socket client, String id) throws IOException {
        Item selected = null;
        for (Item item : offered) if (item.stagedFile != null && Integer.toString(item.id).equals(id)) { selected = item; break; }
        if (selected == null) { message(client, 404, "This file is no longer shared."); return; }
        if (!transfers.tryAcquire()) { message(client, 503, "Other files are transferring. Try again in a moment."); return; }
        try (FileInputStream input = new FileInputStream(selected.stagedFile)) {
            String ascii = selected.name.replaceAll("[^A-Za-z0-9._ -]", "_");
            String utf8 = URLEncoder.encode(selected.name, "UTF-8").replace("+", "%20").replace("*", "%2A");
            OutputStream output = NotebookLocalHttp.response(client, 200, BINARY, selected.size,
                "Content-Disposition: attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" + utf8 + "\r\n");
            NotebookLocalHttp.copy(input, output, selected.size);
        } catch (FileNotFoundException gone) {
            message(client, 404, "This file is no longer shared.");
        } finally { transfers.release(); }
    }

    private String itemsJson() {
        StringBuilder json = new StringBuilder("{\"maxFileBytes\":").append(MAX_FILE_BYTES).append(",\"items\":[");
        String separator = "";
        for (Item item : offered) {
            json.append(separator).append("{\"id\":").append(item.id).append(",\"name\":").append(quote(item.name));
            if (item.url != null) json.append(",\"url\":").append(quote(item.url));
            else json.append(",\"size\":").append(item.size);
            json.append('}');
            separator = ",";
        }
        return json.append("]}").toString();
    }

    private static String quote(String value) {
        // Stored links are normalized URI strings and staged names have had control characters removed.
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String readText(Request request, int limit) throws IOException {
        String type = request.headers.getOrDefault("content-type", "").split(";", 2)[0].trim();
        if (!"text/plain".equalsIgnoreCase(type)) throw new IllegalArgumentException("Send text using the sharing page.");
        return new String(request.body(limit), StandardCharsets.UTF_8);
    }

    private static String webLink(String raw) {
        String value = raw.trim();
        if (!value.regionMatches(true, 0, "http://", 0, 7) && !value.regionMatches(true, 0, "https://", 0, 8))
            throw new IllegalArgumentException("Enter a full HTTP or HTTPS address.");
        String url = NotebookBrowserAddress.normalize(value);
        if (url.length() > 8192) throw new IllegalArgumentException("This website address is too long.");
        return url;
    }

    private static void message(Socket client, int status, String message) throws IOException {
        respond(client, status, "text/plain; charset=utf-8", bytes(message), null);
    }

    private void retire(Socket client) {
        try { client.close(); } catch (IOException ignored) { }
        sockets.remove(client);
    }

    @Override public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
            offered = Collections.emptyList();
            received = Collections.emptyList();
        }
        sessions.close();
        try { listener.close(); } catch (IOException ignored) { }
        for (Socket client : sockets) retire(client);
        workers.shutdownNow();
    }
}
