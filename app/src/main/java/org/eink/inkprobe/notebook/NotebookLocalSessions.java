package org.eink.inkprobe.notebook;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** In-memory credentials, browser sessions and login throttling for one local listener. */
final class NotebookLocalSessions {
    private static final long SESSION_MS = 30 * 60_000L, ATTEMPT_MS = 60_000L;
    private static final String HEX = "0123456789abcdef";
    private final String cookieName;
    private final int limit;
    private final LongSupplier clock;
    private final Map<String, Long> sessions = new HashMap<>();
    private final Map<String, long[]> attempts = new LinkedHashMap<>();
    private final byte[] salt = new byte[32], passwordDigest;
    private final SecureRandom random = new SecureRandom();
    private long globalWindow;
    private int globalAttempts;
    private boolean closed;

    NotebookLocalSessions(String cookieName, String password, int limit, LongSupplier clock) {
        this.cookieName = cookieName;
        this.limit = limit;
        this.clock = clock;
        globalWindow = clock.getAsLong();
        random.nextBytes(salt);
        passwordDigest = digest(password);
    }

    synchronized boolean allowAttempt(String peer) {
        if (closed) return false;
        long now = clock.getAsLong();
        if (now - globalWindow >= ATTEMPT_MS) { globalWindow = now; globalAttempts = 0; }
        long[] window = attempts.get(peer);
        if (window == null || now - window[0] >= ATTEMPT_MS) {
            if (attempts.size() >= 64) attempts.remove(attempts.keySet().iterator().next());
            window = new long[]{now, 0};
            attempts.put(peer, window);
        }
        if (window[1] >= 8 || globalAttempts >= 64) return false;
        window[1]++;
        globalAttempts++;
        return true;
    }

    boolean matches(String supplied) { return MessageDigest.isEqual(passwordDigest, digest(supplied)); }

    synchronized String signIn(String previous) {
        if (closed) return null;
        purgeSessions();
        sessions.remove(previous);
        if (sessions.size() >= limit) return null;
        byte[] value = new byte[32];
        random.nextBytes(value);
        StringBuilder token = new StringBuilder(64);
        for (byte item : value) token.append(HEX.charAt(item >>> 4 & 15)).append(HEX.charAt(item & 15));
        String key = token.toString();
        sessions.put(key, clock.getAsLong());
        return key;
    }

    synchronized boolean authenticated(String token) {
        purgeSessions();
        if (closed || token == null || !sessions.containsKey(token)) return false;
        sessions.put(token, clock.getAsLong());
        return true;
    }

    synchronized void signOut(String token) { sessions.remove(token); }

    String readCookie(String header) {
        if (header == null) return null;
        String prefix = cookieName + "=";
        for (String part : header.split(";")) {
            String value = part.trim();
            if (value.startsWith(prefix) && value.length() == prefix.length() + 64) {
                String token = value.substring(prefix.length());
                if (token.matches("[0-9a-f]{64}")) return token;
            }
        }
        return null;
    }

    String cookieHeader(String token) {
        return "Set-Cookie: " + cookieName + "=" + (token == null ? "" : token)
            + "; Path=/; HttpOnly; SameSite=Strict" + (token == null ? "; Max-Age=0" : "") + "\r\n";
    }

    synchronized void close() {
        closed = true;
        sessions.clear();
        attempts.clear();
    }

    private void purgeSessions() {
        long now = clock.getAsLong();
        sessions.values().removeIf(at -> now - at >= SESSION_MS);
    }

    private byte[] digest(String value) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(salt);
            return sha.digest(NotebookLocalHttp.bytes(value));
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}
