package org.eink.inkprobe.notebook;

import static org.eink.inkprobe.notebook.NotebookLocalHttp.bytes;
import static org.eink.inkprobe.notebook.NotebookLocalHttp.respond;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.eink.inkprobe.notebook.NotebookLocalHttp.Request;

/** Small, bounded, Wi-Fi-bound HTTP viewer. No file access or remote-control endpoints. */
final class NotebookCastServer implements Closeable {
    private static final int MAX_BODY = 2048;
    private final ServerSocket listener;
    private final ThreadPoolExecutor workers;
    private final Set<Socket> sockets = Collections.newSetFromMap(new ConcurrentHashMap<Socket, Boolean>());
    private final Map<String, byte[]> assets;
    private final NotebookLocalSessions sessions;
    private final LongSupplier clock;
    final NotebookCastFrames frames = new NotebookCastFrames();
    private final String authority;
    private volatile boolean closed;
    private volatile long lastViewer = Long.MIN_VALUE;

    NotebookCastServer(InetAddress address, int port, String password, Map<String, byte[]> assets) throws IOException {
        this(address, port, password, assets, () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));
    }

    NotebookCastServer(InetAddress address, int port, String password, Map<String, byte[]> assets,
                       LongSupplier clock) throws IOException {
        if (password == null || password.codePointCount(0, password.length()) < 8 || password.length() > 128)
            throw new IllegalArgumentException("Use a password with 8–128 characters.");
        this.assets = new HashMap<>(assets); this.clock = clock;
        sessions = new NotebookLocalSessions("scriflow_cast", password, 16, clock);
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
            Request request; byte[] payload;
            try { request = NotebookLocalHttp.read(client); payload = request.body(MAX_BODY); }
            catch (IllegalArgumentException error) { respond(client, 400, "text/plain", bytes("Invalid request"), null); return; }
            String rejection = request.rejection(authority);
            if (rejection != null) { respond(client, 403, "text/plain", bytes(rejection), null); return; }
            String path = request.path;
            if (request.method.equals("GET") && (path.equals("/") || path.equals("/viewer.js") || path.equals("/viewer.css"))) {
                String file = path.equals("/") ? "index.html" : path.substring(1);
                byte[] body = assets.get(file);
                respond(client, body == null ? 404 : 200, file.endsWith(".js") ? "text/javascript; charset=utf-8"
                    : file.endsWith(".css") ? "text/css; charset=utf-8" : "text/html; charset=utf-8", body, null); return;
            }
            if (path.equals("/login") && request.method.equals("POST")) { login(client, request, payload); return; }
            if (!path.equals("/frame") && !path.equals("/session") && !path.equals("/logout")) {
                respond(client, 404, "text/plain", bytes("Not found"), null); return;
            }
            if (!(path.equals("/logout") ? request.method.equals("POST") : request.method.equals("GET"))) {
                respond(client, 405, "text/plain", bytes("Method not allowed"), null); return;
            }
            String token = sessions.readCookie(request.headers.get("cookie"));
            if (!sessions.authenticated(token)) { respond(client, 401, "text/plain", bytes("Enter the cast password."), null); return; }
            if (path.equals("/logout")) {
                sessions.signOut(token);
                respond(client, 200, "text/plain", bytes("Signed out"), sessions.cookieHeader(null)); return;
            }
            lastViewer = clock.getAsLong();
            NotebookCastFrames.Frame frame = frames.latest();
            if (path.equals("/frame") && "wait=1".equals(request.headers.get("prefer"))) {
                frame = frames.await(request.headers.get("if-none-match"), 1000);
                if (!sessions.authenticated(token)) { respond(client, 401, "text/plain", bytes("Enter the cast password."), null); return; }
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

    private void login(Socket socket, Request request, byte[] payload) throws IOException {
        if (!"application/x-www-form-urlencoded".equalsIgnoreCase(request.headers.get("content-type"))) {
            respond(socket, 400, "text/plain", bytes("Invalid login"), null); return;
        }
        String peer = socket.getInetAddress().getHostAddress();
        if (!sessions.allowAttempt(peer)) {
            respond(socket, 429, "text/plain", bytes("Too many attempts. Wait one minute."), "Retry-After: 60\r\n"); return;
        }
        String supplied = "";
        try {
            String body = new String(payload, StandardCharsets.UTF_8);
            if (!body.startsWith("password=") || body.indexOf('&') >= 0) throw new IllegalArgumentException();
            supplied = URLDecoder.decode(body.substring(9), "UTF-8");
        } catch (IllegalArgumentException error) {
            respond(socket, 400, "text/plain", bytes("Invalid login"), null); return;
        }
        if (!sessions.matches(supplied)) {
            respond(socket, 401, "text/plain", bytes("Incorrect password."), null); return;
        }
        String token = sessions.signIn(sessions.readCookie(request.headers.get("cookie")));
        if (token == null) {
            respond(socket, 503, "text/plain", bytes("Viewer limit reached. Sign out an existing viewer."), null); return;
        }
        respond(socket, 200, "text/plain", bytes("Connected"), sessions.cookieHeader(token));
    }

    @Override public void close() {
        closed = true; frames.sharing(false, System.nanoTime());
        try { listener.close(); } catch (IOException ignored) { }
        for (Socket socket : sockets) try { socket.close(); } catch (SocketException ignored) { } catch (IOException ignored) { }
        workers.shutdownNow(); sessions.close();
    }
}
