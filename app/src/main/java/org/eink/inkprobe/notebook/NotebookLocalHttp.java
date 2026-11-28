package org.eink.inkprobe.notebook;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** HTTP framing and browser isolation for the app's Wi-Fi listeners. */
final class NotebookLocalHttp {
    private static final int MAX_HEADERS = 8192;
    private static final long HEADER_TIMEOUT = TimeUnit.SECONDS.toNanos(8);

    private NotebookLocalHttp() { }

    static final class Request {
        final String method, path;
        final Map<String, String> headers;
        final long length;
        final InputStream input;

        Request(String method, String path, Map<String, String> headers, long length, InputStream input) {
            this.method = method;
            this.path = path;
            this.headers = headers;
            this.length = length;
            this.input = input;
        }

        String rejection(String authority) {
            if (!authority.equalsIgnoreCase(headers.get("host"))) return "Use the address shown on the tablet.";
            String origin = headers.get("origin");
            if (origin != null && !("http://" + authority).equalsIgnoreCase(origin)) return "Origin denied";
            String site = headers.get("sec-fetch-site");
            return site != null && !site.equals("same-origin") && !site.equals("none") ? "Origin denied" : null;
        }

        byte[] body(int limit) throws IOException {
            if (length > limit) throw new IllegalArgumentException("Request body is too large");
            byte[] body = new byte[(int) length];
            int offset = 0;
            while (offset < body.length) {
                int count = input.read(body, offset, body.length - offset);
                if (count < 0) throw new EOFException("Request ended early");
                offset += count;
            }
            return body;
        }
    }

    static Request read(Socket socket) throws IOException {
        InputStream input = new BufferedInputStream(socket.getInputStream());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        long start = System.nanoTime();
        int match = 0;
        while (match < 4) {
            int item = input.read();
            if (item < 0) throw new EOFException("Disconnected");
            if (System.nanoTime() - start > HEADER_TIMEOUT || buffer.size() == MAX_HEADERS
                    || item < 32 && item != '\r' && item != '\n' && item != '\t' || item == 127)
                throw new IllegalArgumentException("Invalid request headers");
            buffer.write(item);
            int expected = match % 2 == 0 ? '\r' : '\n';
            match = item == expected ? match + 1 : item == '\r' ? 1 : 0;
        }
        String[] lines = new String(buffer.toByteArray(), StandardCharsets.ISO_8859_1).split("\r\n", -1);
        String[] first = lines[0].split(" ", -1);
        if (first.length != 3 || !first[0].matches("[A-Z]{1,12}") || !first[2].equals("HTTP/1.1")
                || !first[1].startsWith("/") || hasLineBreak(first[1])) throw new IllegalArgumentException("Invalid request line");
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.length - 2; i++) {
            int colon = lines[i].indexOf(':');
            if (colon < 1 || hasLineBreak(lines[i])) throw new IllegalArgumentException("Invalid header");
            String name = lines[i].substring(0, colon).toLowerCase(Locale.ROOT);
            if (!name.matches("[a-z0-9-]+") || headers.put(name, lines[i].substring(colon + 1).trim()) != null)
                throw new IllegalArgumentException("Invalid or duplicate header");
        }
        if (headers.containsKey("transfer-encoding")) throw new IllegalArgumentException("Unsupported framing");
        String length = headers.getOrDefault("content-length", "0");
        if (!length.matches("[0-9]{1,19}")) throw new IllegalArgumentException("Invalid body length");
        return new Request(first[0], first[1], headers, Long.parseLong(length), input);
    }

    private static boolean hasLineBreak(String value) { return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0; }

    static void copy(InputStream input, OutputStream output, long length) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        while (length > 0) {
            int count = input.read(buffer, 0, (int) Math.min(length, buffer.length));
            if (count < 0) throw new EOFException("File ended early");
            output.write(buffer, 0, count);
            length -= count;
        }
        output.flush();
    }

    static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    static void respond(Socket socket, int status, String type, byte[] body, String extra) throws IOException {
        OutputStream output = response(socket, status, type, body == null ? 0 : body.length, extra);
        if (body != null) output.write(body);
        output.flush();
    }

    static OutputStream response(Socket socket, int status, String type, long length, String extra) throws IOException {
        OutputStream output = socket.getOutputStream();
        String contentLength = status == 204 || status == 304 ? "" : "Content-Length: " + length + "\r\n";
        output.write(bytes("HTTP/1.1 " + status + " " + reason(status) + "\r\nConnection: close\r\n"
            + "Cache-Control: no-store, private\r\nX-Content-Type-Options: nosniff\r\nX-Frame-Options: DENY\r\n"
            + "Referrer-Policy: no-referrer\r\nContent-Security-Policy: default-src 'none'; script-src 'self'; style-src 'self'; "
            + "connect-src 'self'; img-src 'self' blob:; base-uri 'none'; frame-ancestors 'none'; form-action 'self'\r\n"
            + "Content-Type: " + type + "\r\n" + contentLength
            + (extra == null ? "" : extra) + "\r\n"));
        return output;
    }

    private static String reason(int status) {
        switch (status) {
            case 200: return "OK";
            case 204: return "No Content";
            case 304: return "Not Modified";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            case 413: return "Content Too Large";
            case 429: return "Too Many Requests";
            case 500: return "Internal Server Error";
            default: return "Service Unavailable";
        }
    }
}
