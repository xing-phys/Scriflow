package org.eink.inkprobe.notebook;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/** Real-socket coverage of transfer boundaries and a fixture for the bundled browser page. */
public final class NotebookShareServerTest {
    private static final String CODE = "ABCD2345";
    private static final String POST = "X-Scriflow-Share: 1\r\n";
    private static final String TEXT = POST + "Content-Type: text/plain; charset=utf-8\r\n";
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("scriflow-wifi-test-");
        Map<String, byte[]> assets = new HashMap<>();
        Path source = Paths.get("app/src/main/assets/share");
        for (String name : new String[]{"index.html", "share.js", "share.css"})
            assets.put(name, Files.readAllBytes(source.resolve(name)));
        AtomicInteger opened = new AtomicInteger();
        try (NotebookShareServer server = new NotebookShareServer(InetAddress.getByName("127.0.0.1"), CODE, assets, name -> {
            opened.incrementAndGet();
            return pending(root, name);
        })) {
            byte[] original = new byte[200_000];
            new java.util.Random(12).nextBytes(original);
            Path sample = Files.write(root.resolve("staged.bin"), original);
            server.offerFile("研究 notes #1.bin", sample.toFile(), original.length);
            server.offerLink("https://example.org/a%2Fb?q=1#tablet");
            if (args.length > 0 && args[0].equals("--browser")) {
                System.out.println(server.address());
                System.out.flush();
                try (BufferedReader commands = new BufferedReader(new InputStreamReader(System.in))) {
                    commands.readLine();
                }
                return;
            }
            Client browser = new Client(server.address());
            check(browser.get("/", null).status == 200, "Pairing page is public");
            check(browser.get("/items", null).status == 401, "Offered metadata requires pairing");
            check(browser.get("/download/1", null).status == 401, "Offered bytes require pairing");
            check(browser.get("/", "Origin: null\r\n").status == 403, "Opaque origins are denied");
            check(browser.exchange("GET / HTTP/1.1\r\nHost: other.test\r\n\r\n", null, false).status == 403, "Wrong Host is denied");
            check(browser.exchange("\r\n\r\n", null, false).status == 400, "Empty request returns an error");
            check(browser.post("/connect", null, "Content-Type: text/plain\r\n", bytes(CODE)).status == 403, "POST requests need the browser header");
            check(browser.post("/connect", null, TEXT, bytes("BADCODE2")).status == 401, "Incorrect code is rejected");
            String cookie = browser.pair(), second = browser.pair();
            check(!cookie.equals(second), "Browsers have independent cookies");
            String session = "Cookie: " + cookie + "\r\n";
            String items = browser.get("/items", session).text();
            check(items.contains("研究 notes #1.bin") && items.contains("https://example.org/a%2Fb?q=1#tablet")
                && !items.contains(sample.toString()), "Listings expose selected names and URLs without private paths");
            Response download = browser.get("/download/1", session);
            check(download.status == 200 && Arrays.equals(original, download.body), "Download streams the original bytes");
            check(download.headers.contains("Content-Type: application/octet-stream")
                && download.headers.contains("filename*=UTF-8''%E7%A0%94%E7%A9%B6%20notes%20%231.bin"), "Download is an attachment with a UTF-8 name");
            check(browser.get("/download/..%2Fstaged.bin", session).status == 404, "Only offered IDs resolve");
            check(browser.get("/items", session + "Sec-Fetch-Site: same-site\r\n").status == 403, "Requests from another browser origin are denied");

            String binary = POST + "Content-Type: application/octet-stream\r\nX-File-Name: ..%2F%E8%AE%B2%E4%B9%89%20%2B.pdf\r\n";
            String upload = "POST /upload HTTP/1.1\r\nHost: " + browser.authority + "\r\n" + binary;
            check(browser.exchange(upload + "Content-Length: 200000\r\n\r\n", null, false).status == 401 && opened.get() == 0,
                "Unauthorized upload is refused before reading its body");
            check(browser.exchange(upload + session + "Content-Length: 1073741825\r\n\r\n", null, false).status == 413
                && opened.get() == 0, "Oversized upload is refused before opening storage");
            check(browser.post("/upload", cookie, binary, original).status == 200, "Binary upload completes");
            check(browser.post("/upload", cookie, binary, new byte[0]).status == 200, "Zero-byte upload completes");
            check(server.received().size() == 2, "Receipts are created for completed uploads");
            NotebookShareServer.Item first = server.received().get(1), zero = server.received().get(0);
            check(first.name.equals("shared.._讲义 +.pdf") && !zero.name.equals(first.name)
                && Arrays.equals(original, Files.readAllBytes(Paths.get(first.receivedUri)))
                && Files.size(Paths.get(zero.receivedUri)) == 0, "Names are sanitized and both files survive a name collision");
            check(browser.exchange(upload + session + "Content-Length: 2000\r\n\r\n", bytes("truncated"), true).status == 500,
                "Interrupted upload does not succeed");
            try (Stream<Path> visible = Files.list(root)) {
                check(server.received().size() == 2 && visible.noneMatch(p -> p.getFileName().toString().endsWith(".part")),
                    "Interrupted uploads leave no receipt or partial file");
            }
            check(browser.post("/link", cookie, TEXT, bytes("HTTPS://example.org/中?q=a%2Fb#h")).status == 200
                && server.received().get(0).url.equals("https://example.org/%E4%B8%AD?q=a%2Fb#h"), "HTTP(S) links are stored for explicit opening");
            check(browser.post("/link", cookie, TEXT, bytes("javascript:alert(1)")).status == 400
                && browser.post("/link", cookie, TEXT, bytes("https://user:pass@example.org")).status == 400,
                "Other schemes and links with credentials are rejected");

            server.withdraw(server.offered().get(1));
            check(browser.get("/download/1", session).status == 404 && Files.exists(sample), "Withdrawal removes the route");
            check(browser.post("/disconnect", cookie, POST, new byte[0]).status == 204, "Sign-out succeeds");
            check(browser.get("/items", session).status == 401
                && browser.get("/items", "Cookie: " + second + "\r\n").status == 200, "Sign-out revokes only one browser");
            server.close();
            check(server.closed() && server.offered().isEmpty() && server.received().isEmpty(), "Stop clears in-memory items");
            try { browser.get("/items", session); throw new AssertionError("Listener still accepts connections"); }
            catch (IOException stopped) { checks++; }
        } finally {
            try (Stream<Path> files = Files.list(root)) {
                for (Path file : (Iterable<Path>) files::iterator) Files.delete(file);
            }
            Files.delete(root);
        }
        System.out.println("Wi-Fi sharing server: " + checks + " checks passed");
    }

    private static NotebookShareServer.PendingFile pending(Path root, String name) throws IOException {
        Path temporary = Files.createTempFile(root, "wifi-", ".part");
        return new NotebookShareServer.PendingFile() {
            @Override public OutputStream open() throws IOException { return Files.newOutputStream(temporary); }
            @Override public NotebookShareServer.SavedFile publish() throws IOException {
                for (int index = 0; ; index++) {
                    String saved = index == 0 ? name : index + "-" + name;
                    Path target = root.resolve(saved);
                    try {
                        Files.move(temporary, target);
                        return new NotebookShareServer.SavedFile(saved, target.toString());
                    } catch (FileAlreadyExistsException collision) { }
                }
            }
            @Override public void close() throws IOException { Files.deleteIfExists(temporary); }
        };
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private static final class Client {
        final String authority;
        final int port;
        Client(String address) {
            authority = address.substring("http://".length(), address.length() - 1);
            port = Integer.parseInt(authority.split(":")[1]);
        }
        String pair() throws IOException {
            Response response = post("/connect", null, TEXT, bytes(CODE.toLowerCase(java.util.Locale.ROOT)));
            check(response.status == 204 && response.headers.contains("; Path=/; HttpOnly; SameSite=Strict"), "Browser cookie is private");
            return response.headers.split("Set-Cookie: ")[1].split(";")[0];
        }
        Response get(String path, String headers) throws IOException {
            return exchange("GET " + path + " HTTP/1.1\r\nHost: " + authority + "\r\n" + (headers == null ? "" : headers) + "\r\n", null, false);
        }
        Response post(String path, String cookie, String headers, byte[] payload) throws IOException {
            return exchange("POST " + path + " HTTP/1.1\r\nHost: " + authority + "\r\n" + headers
                + (cookie == null ? "" : "Cookie: " + cookie + "\r\n") + "Content-Length: " + payload.length + "\r\n\r\n", payload, false);
        }
        Response exchange(String headers, byte[] payload, boolean halfClose) throws IOException {
            try (Socket socket = new Socket("127.0.0.1", port)) {
                socket.setSoTimeout(4000);
                OutputStream output = socket.getOutputStream();
                output.write(bytes(headers));
                if (payload != null) output.write(payload);
                if (halfClose) socket.shutdownOutput();
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                InputStream input = socket.getInputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) result.write(buffer, 0, count);
                return new Response(result.toByteArray());
            }
        }
    }

    private static final class Response {
        final String headers;
        final int status;
        final byte[] body;
        Response(byte[] value) {
            String text = new String(value, StandardCharsets.ISO_8859_1);
            int split = text.indexOf("\r\n\r\n");
            headers = text.substring(0, split);
            status = Integer.parseInt(headers.split(" ", 3)[1]);
            body = Arrays.copyOfRange(value, split + 4, value.length);
        }
        String text() { return new String(body, StandardCharsets.UTF_8); }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
}
