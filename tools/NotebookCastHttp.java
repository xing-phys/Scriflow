package org.eink.inkprobe;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Emulator-only HTTP test client with complete socket reads through the final TCP EOF. */
public final class NotebookCastHttp {
    public static void main(String[] args) throws Exception {
        String host = args[0], port = args[1], method = args[2], path = args[3];
        String cookie = args[4], body = args[5];
        byte[] content = body.equals("-") ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        String headers = method + " " + path + " HTTP/1.1\r\nHost: " + host + ":" + port + "\r\nConnection: close\r\n"
            + (cookie.equals("-") ? "" : "Cookie: " + cookie + "\r\n")
            + (body.equals("-") ? "" : "Content-Type: application/x-www-form-urlencoded\r\nContent-Length: " + content.length + "\r\n") + "\r\n";
        try (Socket socket = new Socket(host, Integer.parseInt(port))) {
            socket.setSoTimeout(8000); OutputStream output = socket.getOutputStream();
            output.write(headers.getBytes(StandardCharsets.UTF_8)); output.write(content); output.flush();
            InputStream input = socket.getInputStream(); byte[] buffer = new byte[16384]; int count;
            while ((count = input.read(buffer)) != -1) System.out.write(buffer, 0, count);
            System.out.flush();
        }
    }
}
