package org.eink.inkprobe.notebook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Real TCP requests against the production server; fake monotonic time for expiry and throttling. */
public final class NotebookCastServerTest {
    private static int checks;
    private static final String PASSWORD = "Quantum 中文 + & = password";
    private static final byte[] JPEG = {(byte)255, (byte)216, 1, 2, 3, (byte)255, (byte)217};

    public static void main(String[] args) throws Exception {
        Map<String, byte[]> assets = new HashMap<>();
        for (String name : new String[]{"index.html", "viewer.css", "viewer.js"})
            assets.put(name, Files.readAllBytes(Paths.get("app/src/main/assets/cast", name)));
        if (args.length > 0 && args[0].equals("--viewer")) {
            NotebookCastServer server = new NotebookCastServer(InetAddress.getLoopbackAddress(), 0, PASSWORD, assets);
            server.frames.sharing(true, 0);
            server.frames.publish(server.frames.ticket(System.nanoTime()), Files.readAllBytes(Paths.get(args[1])));
            System.out.println(server.address()); System.out.flush();
            java.util.Scanner commands = new java.util.Scanner(System.in, "UTF-8");
            while (commands.hasNextLine()) {
                String command = commands.nextLine();
                if (command.equals("stop")) break;
                server.frames.sharing(!command.equals("pause"), 0);
                if (!command.equals("pause")) server.frames.publish(server.frames.ticket(System.nanoTime()), Files.readAllBytes(Paths.get(args[1])));
            }
            server.close(); return;
        }
        frameOwnership();
        AtomicLong clock = new AtomicLong(1_000_000);
        NotebookCastServer server = new NotebookCastServer(InetAddress.getByName("127.0.0.1"), 0, PASSWORD, assets, clock::get);
        int port = Integer.parseInt(server.address().split(":")[2].replace("/", ""));
        try {
            Response home = get(server, "/", null);
            check(home.status == 200 && home.text().contains("Cast password"), "Public page presents the password gate");
            check(home.header("cache-control").contains("no-store") && home.header("content-security-policy").contains("default-src 'none'"), "Viewer has no cache and a restrictive CSP");
            check(get(server, "/frame", null).status == 401, "JPEG endpoint requires authentication");
            check(get(server, "/session", null).status == 401, "Session status requires authentication");
            check(get(server, "/viewer.js", null).status == 200 && get(server, "/viewer.css", null).status == 200, "Viewer assets are bundled and available without Internet");
            check(login(server, "wrong password", null).status == 401, "Wrong password cannot authorize a viewer");
            Response login = login(server, PASSWORD, null);
            check(login.status == 200, "Unicode and form punctuation survive password submission");
            String first = session(login);
            check(first.matches("scriflow_cast=[0-9a-f]{64}"), "Session tokens have 256 random bits");
            check(login.header("set-cookie").contains("HttpOnly; SameSite=Strict"), "Session cookie cannot be read by scripts or sent cross-site");
            check(get(server, "/frame", first).status == 204, "Private or background app serves no image");
            server.frames.sharing(true, 0); long ticket = server.frames.ticket(500_000_000);
            check(server.frames.publish(ticket, JPEG), "Visible-app encoder can publish a frame");
            Response frame = get(server, "/frame", first);
            check(frame.status == 200 && Arrays.equals(frame.body, JPEG), "An authenticated viewer receives the current JPEG");
            check(frame.header("content-type").equals("image/jpeg") && frame.header("x-cast-state").equals("live"), "Frames carry state and image type");
            check(server.hasRecentViewer(), "Only authenticated polling marks a recent viewer");
            Response same = request(server, "GET /frame HTTP/1.1\r\nCookie: " + first + "\r\nIf-None-Match: " + frame.header("etag") + "\r\n", null);
            check(same.status == 304 && same.body.length == 0, "Unchanged screens avoid resending image bytes");
            check(get(server, "/frame", "scriflow_cast=" + repeat('a', 64)).status == 401, "A fabricated token is rejected even while live");
            check(request(server, "GET /frame HTTP/1.1\r\nCookie: " + first + "\r\nOrigin: https://evil.example\r\n", null).status == 403, "Foreign origins cannot read authenticated frames");
            check(request(server, "GET /frame HTTP/1.1\r\nCookie: " + first + "\r\nSec-Fetch-Site: cross-site\r\n", null).status == 403, "Cross-site browser requests are rejected");
            check(raw(port, "GET /frame HTTP/1.1\r\nHost: evil.example\r\nCookie: " + first + "\r\n\r\n").status == 403, "DNS rebinding cannot use a foreign Host");
            check(request(server, "POST /login HTTP/1.1\r\nOrigin: null\r\n", null).status == 403, "Opaque origins cannot log in");
            for (String path : new String[]{"/../files/ai-settings", "/frame?password=" + PASSWORD.replace(" ", "%20"), "/%2e%2e/private", "/stop"})
                check(get(server, path, first).status == 404, "Only explicit viewer endpoints are exposed");
            check(request(server, "POST /frame HTTP/1.1\r\nCookie: " + first + "\r\n", null).status == 405, "Frame endpoint is read-only");
            check(request(server, "GET /frame HTTP/1.1\r\nCookie: " + first + "\r\nCookie: duplicate\r\n", null).status == 400, "Ambiguous duplicate headers are rejected");
            check(request(server, "POST /login HTTP/1.1\r\nTransfer-Encoding: chunked\r\n", null).status == 400, "Unsupported transfer framing is rejected");
            check(request(server, "POST /login HTTP/1.1\r\nContent-Length: 9999999\r\n", null).status == 400, "Large request bodies are rejected before allocation");
            check(request(server, "GET / HTTP/1.1\r\nX-Padding: " + repeat('z', 9000) + "\r\n", null).status == 400, "Header memory is bounded");
            String second = session(login(server, PASSWORD, null));
            check(!first.equals(second) && get(server, "/frame", second).status == 200, "Multiple viewers have independent sessions");
            check(request(server, "POST /logout HTTP/1.1\r\nCookie: " + first + "\r\n", null).status == 200, "Disconnect revokes the current session");
            check(get(server, "/frame", first).status == 401 && get(server, "/frame", second).status == 200, "Disconnect leaves other viewers authorized");
            server.frames.sharing(false, 1_000_000_000);
            Response paused = get(server, "/frame", second);
            check(paused.status == 204 && paused.body.length == 0 && paused.header("x-cast-state").equals("paused"), "Pause removes the cached image from HTTP responses");
            check(!server.frames.publish(ticket, JPEG), "A late encoder cannot restore a paused frame");
            clock.addAndGet(30 * 60_000L + 1);
            check(get(server, "/frame", second).status == 401, "Idle sessions expire using monotonic time");
            check(!server.hasRecentViewer(), "Expired viewers stop counting as recent viewers");
            String old = session(login(server, PASSWORD, null));
            String fresh = session(login(server, PASSWORD, old));
            check(!old.equals(fresh) && get(server, "/frame", old).status == 401, "Login rotates an existing browser session");
        } finally { server.close(); }
        try (Socket ignored = new Socket("127.0.0.1", port)) { throw new AssertionError("Stop left the listener open"); }
        catch (IOException expected) { checks++; }
        try (NotebookCastServer limited = new NotebookCastServer(InetAddress.getByName("127.0.0.1"), 0, PASSWORD, assets, clock::get)) {
            for (int i = 0; i < 8; i++) check(login(limited, "bad password", null).status == 401, "Bounded attempts remain generic");
            Response limit = login(limited, PASSWORD, null);
            check(limit.status == 429 && limit.header("retry-after").equals("60"), "Repeated guessing is throttled even with a correct next guess");
            clock.addAndGet(60_001);
            check(login(limited, PASSWORD, null).status == 200, "Throttle recovers after its time window");
        }
        try (NotebookCastServer restarted = new NotebookCastServer(InetAddress.getByName("127.0.0.1"), port, "New password", assets)) {
            check(get(restarted, "/frame", null).status == 401 && login(restarted, PASSWORD, null).status == 401, "A new cast starts with fresh authorization and password");
        }
        try (NotebookCastServer capped = new NotebookCastServer(InetAddress.getByName("127.0.0.1"), 0, PASSWORD, assets, clock::get)) {
            String existing = null;
            for (int i = 0; i < 16; i++) {
                if (i % 7 == 0) clock.addAndGet(60_001);
                String token = session(login(capped, PASSWORD, null));
                if (existing == null) existing = token;
            }
            check(login(capped, PASSWORD, null).status == 503, "Authenticated session count is bounded");
            String replacement = session(login(capped, PASSWORD, existing));
            check(get(capped, "/session", existing).status == 401 && get(capped, "/session", replacement).status == 200,
                "A browser can rotate its session even when the viewer limit is reached");
        }
        System.out.println("Screen cast: " + checks + " checks passed");
    }

    private static void frameOwnership() {
        NotebookCastFrames frames = new NotebookCastFrames();
        check(frames.ticket(Long.MAX_VALUE) == -1, "Frames start paused");
        frames.sharing(true, 1_000_000_000);
        check(frames.ticket(1_100_000_000) == -1, "Queued frames before foreground settling are discarded");
        long old = frames.ticket(1_500_000_000);
        check(frames.publish(old, JPEG), "Foreground ticket owns the current capture");
        frames.sharing(false, 1_600_000_000); frames.sharing(true, 2_000_000_000);
        check(!frames.publish(old, JPEG) && frames.latest().jpeg == null, "Resume cannot revive a frame from a previous visibility interval");
        long current = frames.ticket(2_500_000_000L);
        check(frames.publish(current, JPEG), "New visibility interval accepts fresh frames");
        frames.sharing(true, 3_000_000_000L);
        check(frames.latest().jpeg == JPEG, "Repeated visibility checks do not invalidate live content");
    }
    private static Response get(NotebookCastServer server, String path, String cookie) throws Exception {
        return request(server, "GET " + path + " HTTP/1.1\r\n" + (cookie == null ? "" : "Cookie: " + cookie + "\r\n"), null);
    }
    private static Response login(NotebookCastServer server, String password, String cookie) throws Exception {
        return request(server, "POST /login HTTP/1.1\r\nContent-Type: application/x-www-form-urlencoded\r\n"
            + (cookie == null ? "" : "Cookie: " + cookie + "\r\n"), "password=" + URLEncoder.encode(password, "UTF-8"));
    }
    private static String session(Response response) {
        check(response.status == 200, "Login succeeds"); return response.header("set-cookie").split(";")[0];
    }
    private static Response request(NotebookCastServer server, String headers, String body) throws Exception {
        String authority = server.address().substring(7).replace("/", "");
        int port = Integer.parseInt(authority.split(":")[1]);
        return raw(port, headers + "Host: " + authority + "\r\n"
            + (body == null ? "" : "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n") + "\r\n" + (body == null ? "" : body));
    }
    private static Response raw(int port, String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5000); socket.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
            try { while ((count = socket.getInputStream().read(buffer)) != -1) output.write(buffer, 0, count); }
            catch (java.net.SocketException reset) { if (output.size() == 0) throw reset; }
            return new Response(output.toByteArray());
        }
    }
    private static final class Response {
        final int status; final Map<String, String> headers = new LinkedHashMap<>(); final byte[] body;
        Response(byte[] response) {
            String text = new String(response, StandardCharsets.ISO_8859_1); int split = text.indexOf("\r\n\r\n");
            String[] lines = text.substring(0, split).split("\r\n"); status = Integer.parseInt(lines[0].split(" ")[1]);
            for (int i = 1; i < lines.length; i++) { int colon = lines[i].indexOf(':'); headers.put(lines[i].substring(0, colon).toLowerCase(java.util.Locale.US), lines[i].substring(colon + 1).trim()); }
            body = Arrays.copyOfRange(response, split + 4, response.length);
        }
        String header(String name) { return headers.getOrDefault(name, ""); }
        String text() { return new String(body, StandardCharsets.UTF_8); }
    }
    private static String repeat(char value, int count) { char[] result = new char[count]; Arrays.fill(result, value); return new String(result); }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
}
