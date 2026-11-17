package org.eink.inkprobe.notebook;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Small, bounded, Wi-Fi-bound HTTP viewer. No file access or remote-control endpoints. */
final class NotebookCastServer implements Closeable {
    private static final long SESSION_MS = 30 * 60_000L, ATTEMPT_MS = 60_000L;
    private static final int MAX_HEADERS = 8192, MAX_BODY = 2048;
    private final ServerSocket listener;
    private final ThreadPoolExecutor workers;
    private final Set<Socket> sockets = Collections.newSetFromMap(new ConcurrentHashMap<Socket, Boolean>());
    private final Map<String, byte[]> assets;
    private final Map<String, Long> sessions = new HashMap<>();
    private final Map<String, long[]> attempts = new LinkedHashMap<>();
    private final byte[] salt = new byte[32], passwordDigest;
    private final SecureRandom random = new SecureRandom();
    private final LongSupplier clock;
    final NotebookCastFrames frames = new NotebookCastFrames();
    private final String authority;
    private volatile boolean closed;
    private volatile long lastViewer = Long.MIN_VALUE;
    private long globalWindow;
    private int globalAttempts;

    NotebookCastServer(InetAddress address, int port, String password, Map<String, byte[]> assets) throws IOException {
        this(address, port, password, assets, () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    NotebookCastServer(InetAddress address, int port, String password, Map<String, byte[]> assets,
                       LongSupplier clock) throws IOException {
        if (password == null || password.codePointCount(0, password.length()) < 8 || password.length() > 128)
            throw new IllegalArgumentException("Use a password with 8–128 characters.");
        this.assets = new HashMap<>(assets); this.clock = clock; globalWindow = clock.getAsLong();
        random.nextBytes(salt); passwordDigest = digest(password);
        listener = new ServerSocket();
        try { listener.bind(new InetSocketAddress(address, port), 16); }
        catch (IOException error) { listener.close(); throw error; }
        authority = address.getHostAddress() + ":" + listener.getLocalPort();
        // Sixteen sleeping frame waits still leave capacity for login, status and disconnect.
        workers = new ThreadPoolExecutor(0, 20, 20, TimeUnit.SECONDS, new SynchronousQueue<>(), task -> {
            Thread thread = new Thread(task, "screen-cast-client"); thread.setDaemon(true); return thread;
        });
        Thread accept = new Thread(this::accept, "screen-cast-listener"); accept.setDaemon(true); accept.start();
    }

    String address() { return "http://" + authority + "/"; }
    boolean hasRecentViewer() {
        long last = lastViewer; return last != Long.MIN_VALUE && clock.getAsLong() - last < 3000;
    }

    private void accept() {
        while (!closed) {
            Socket socket = null;
            try {
                socket = listener.accept(); socket.setSoTimeout(4000); socket.setTcpNoDelay(true);
                if (closed) { socket.close(); return; }
                sockets.add(socket);
                final Socket client = socket;
                workers.execute(() -> serve(client));
            } catch (IOException | java.util.concurrent.RejectedExecutionException error) {
                if (socket != null) { sockets.remove(socket); try { socket.close(); } catch (IOException ignored) { } }
                if (listener.isClosed()) return;
            }
        }
    }

    private void serve(Socket socket) {
        try (Socket client = socket) {
            Request request;
            try { request = read(client.getInputStream()); }
            catch (IllegalArgumentException error) { respond(client, 400, "text/plain", bytes("Invalid request"), null); return; }
            if (!authority.equalsIgnoreCase(request.headers.get("host"))) {
                respond(client, 403, "text/plain", bytes("Use the address shown on the tablet."), null); return;
            }
            String origin = request.headers.get("origin");
            if (origin != null && !("http://" + authority).equalsIgnoreCase(origin)) {
                respond(client, 403, "text/plain", bytes("Origin denied"), null); return;
            }
            String fetchSite = request.headers.get("sec-fetch-site");
            if (fetchSite != null && !fetchSite.equals("same-origin") && !fetchSite.equals("none")) {
                respond(client, 403, "text/plain", bytes("Origin denied"), null); return;
            }
            String path = request.path;
            if (request.method.equals("GET") && (path.equals("/") || path.equals("/viewer.js") || path.equals("/viewer.css"))) {
                String file = path.equals("/") ? "index.html" : path.substring(1);
                byte[] body = assets.get(file);
                respond(client, body == null ? 404 : 200, file.endsWith(".js") ? "text/javascript; charset=utf-8"
                    : file.endsWith(".css") ? "text/css; charset=utf-8" : "text/html; charset=utf-8", body, null); return;
            }
            if (path.equals("/login") && request.method.equals("POST")) { login(client, request); return; }
            if (!path.equals("/frame") && !path.equals("/session") && !path.equals("/logout")) {
                respond(client, 404, "text/plain", bytes("Not found"), null); return;
            }
            if (!(path.equals("/logout") ? request.method.equals("POST") : request.method.equals("GET"))) {
                respond(client, 405, "text/plain", bytes("Method not allowed"), null); return;
            }
            String token = cookie(request.headers.get("cookie"));
            if (!authenticated(token)) { respond(client, 401, "text/plain", bytes("Enter the cast password."), null); return; }
            if (path.equals("/logout")) {
                synchronized (this) { sessions.remove(token); }
                respond(client, 200, "text/plain", bytes("Signed out"), "Set-Cookie: scriflow_cast=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0\r\n"); return;
            }
            lastViewer = clock.getAsLong();
            NotebookCastFrames.Frame frame = frames.latest();
            if (path.equals("/frame") && "wait=1".equals(request.headers.get("prefer"))) {
                frame = frames.await(request.headers.get("if-none-match"), 1000);
                if (!authenticated(token)) { respond(client, 401, "text/plain", bytes("Enter the cast password."), null); return; }
            }
            String headers = "X-Cast-State: " + frame.state + "\r\nETag: \"" + frame.sequence + "\"\r\n";
            if (path.equals("/session")) {
                respond(client, 200, "application/json", bytes("{\"state\":\"" + frame.state + "\"}"), headers);
            } else if (frame.jpeg == null || frame.state.equals("holding")) respond(client, 204, "image/jpeg", null, headers);
            else if (("\"" + frame.sequence + "\"").equals(request.headers.get("if-none-match")))
                respond(client, 304, "image/jpeg", null, headers);
            else respond(client, 200, "image/jpeg", frame.jpeg, headers);
        } catch (IOException ignored) {
            // A viewer leaving, a read timeout or Stop must not affect other viewers.
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        } finally { sockets.remove(socket); }
    }

    private void login(Socket socket, Request request) throws IOException {
        if (!"application/x-www-form-urlencoded".equalsIgnoreCase(request.headers.get("content-type"))) {
            respond(socket, 400, "text/plain", bytes("Invalid login"), null); return;
        }
        String peer = socket.getInetAddress().getHostAddress();
        if (!allowAttempt(peer)) {
            respond(socket, 429, "text/plain", bytes("Too many attempts. Wait one minute."), "Retry-After: 60\r\n"); return;
        }
        String supplied = "";
        try {
            String body = new String(request.body, StandardCharsets.UTF_8);
            if (!body.startsWith("password=") || body.indexOf('&') >= 0) throw new IllegalArgumentException();
            supplied = URLDecoder.decode(body.substring(9), "UTF-8");
        } catch (IllegalArgumentException error) {
            respond(socket, 400, "text/plain", bytes("Invalid login"), null); return;
        }
        if (!MessageDigest.isEqual(passwordDigest, digest(supplied))) {
            respond(socket, 401, "text/plain", bytes("Incorrect password."), null); return;
        }
        String token;
        synchronized (this) {
            purgeSessions();
            sessions.remove(cookie(request.headers.get("cookie")));
            if (sessions.size() >= 16) {
                respond(socket, 503, "text/plain", bytes("Viewer limit reached. Sign out an existing viewer."), null); return;
            }
            token = token(); sessions.put(token, clock.getAsLong());
        }
        respond(socket, 200, "text/plain", bytes("Connected"), "Set-Cookie: scriflow_cast=" + token
            + "; Path=/; HttpOnly; SameSite=Strict\r\n");
    }

    private synchronized boolean allowAttempt(String peer) {
        long now = clock.getAsLong();
        if (now - globalWindow >= ATTEMPT_MS) { globalWindow = now; globalAttempts = 0; }
        long[] window = attempts.get(peer);
        if (window == null || now - window[0] >= ATTEMPT_MS) {
            if (attempts.size() >= 64) attempts.remove(attempts.keySet().iterator().next());
            window = new long[]{now, 0}; attempts.put(peer, window);
        }
        if (window[1] >= 8 || globalAttempts >= 64) return false;
        window[1]++; globalAttempts++; return true;
    }

    private synchronized boolean authenticated(String token) {
        purgeSessions(); if (token == null || !sessions.containsKey(token)) return false;
        sessions.put(token, clock.getAsLong()); return true;
    }
    private void purgeSessions() {
        long now = clock.getAsLong();
        Iterator<Long> iterator = sessions.values().iterator();
        while (iterator.hasNext()) if (now - iterator.next() >= SESSION_MS) iterator.remove();
    }
    private String token() {
        byte[] value = new byte[32]; random.nextBytes(value); StringBuilder hex = new StringBuilder(64);
        for (byte item : value) hex.append(String.format(Locale.US, "%02x", item & 255));
        return hex.toString();
    }
    private byte[] digest(String password) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256"); sha.update(salt);
            return sha.digest(bytes(password));
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static String cookie(String header) {
        if (header != null) for (String part : header.split(";")) {
            String value = part.trim();
            if (value.matches("scriflow_cast=[0-9a-f]{64}")) return value.substring(14);
        }
        return null;
    }

    private static final class Request {
        String method, path;
        final Map<String, String> headers = new HashMap<>();
        byte[] body;
    }
    private static Request read(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(); int match = 0;
        while (match < 4) {
            int item = input.read(); if (item < 0) throw new IOException("Disconnected");
            buffer.write(item); if (buffer.size() > MAX_HEADERS) throw new IllegalArgumentException();
            int expected = match % 2 == 0 ? '\r' : '\n';
            match = item == expected ? match + 1 : item == '\r' ? 1 : 0;
        }
        String[] lines = new String(buffer.toByteArray(), StandardCharsets.US_ASCII).split("\r\n");
        String[] first = lines[0].split(" ");
        if (first.length != 3 || !first[2].equals("HTTP/1.1") || !first[1].startsWith("/")) throw new IllegalArgumentException();
        Request request = new Request(); request.method = first[0]; request.path = first[1];
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':'); if (colon < 1) throw new IllegalArgumentException();
            String name = lines[i].substring(0, colon).toLowerCase(Locale.US);
            if (!name.matches("[a-z0-9-]+") || request.headers.put(name, lines[i].substring(colon + 1).trim()) != null)
                throw new IllegalArgumentException();
        }
        if (request.headers.containsKey("transfer-encoding")) throw new IllegalArgumentException();
        int length;
        try { length = Integer.parseInt(request.headers.getOrDefault("content-length", "0")); }
        catch (NumberFormatException error) { throw new IllegalArgumentException(); }
        if (length < 0 || length > MAX_BODY) throw new IllegalArgumentException();
        request.body = new byte[length]; int offset = 0;
        while (offset < length) {
            int count = input.read(request.body, offset, length - offset);
            if (count < 0) throw new IOException("Disconnected"); offset += count;
        }
        return request;
    }

    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static void respond(Socket socket, int status, String type, byte[] body, String extra) throws IOException {
        String reason = status == 200 ? "OK" : status == 204 ? "No Content" : status == 304 ? "Not Modified"
            : status == 400 ? "Bad Request" : status == 401 ? "Unauthorized" : status == 403 ? "Forbidden"
            : status == 404 ? "Not Found" : status == 405 ? "Method Not Allowed" : status == 429 ? "Too Many Requests" : "Service Unavailable";
        byte[] headers = bytes("HTTP/1.1 " + status + " " + reason + "\r\nConnection: close\r\n"
            + "Cache-Control: no-store, private\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\n"
            + "Referrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; "
            + "connect-src 'self'; img-src 'self' blob:; base-uri 'none'; frame-ancestors 'none'; form-action 'self'\r\n"
            + "Content-Type: " + type + "\r\nContent-Length: " + (body == null ? 0 : body.length) + "\r\n"
            + (extra == null ? "" : extra) + "\r\n");
        OutputStream output = socket.getOutputStream(); output.write(headers);
        if (body != null) output.write(body); output.flush();
    }

    @Override public void close() {
        closed = true; frames.sharing(false, System.nanoTime());
        try { listener.close(); } catch (IOException ignored) { }
        for (Socket socket : sockets) try { socket.close(); } catch (SocketException ignored) { } catch (IOException ignored) { }
        workers.shutdownNow(); synchronized (this) { sessions.clear(); attempts.clear(); }
    }
}
