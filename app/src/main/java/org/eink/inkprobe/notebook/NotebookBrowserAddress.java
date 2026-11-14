package org.eink.inkprobe.notebook;

import java.net.IDN;
import java.net.URI;
import java.net.URL;
import java.util.Locale;

/** Address-bar parsing and the shared boundary for all browser navigation. */
final class NotebookBrowserAddress {
    private NotebookBrowserAddress() { }

    static String normalize(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Enter a website address.");
        if (value.length() > 8192) throw new IllegalArgumentException("This website address is too long.");
        for (int i = 0; i < value.length(); i++)
            if (Character.isISOControl(value.charAt(i)) || value.charAt(i) == '\\')
                throw new IllegalArgumentException("Enter a valid HTTPS address.");
        if (value.startsWith("//")) value = "https:" + value;
        else if (!value.contains("://")) value = "https://" + value;
        if (value.regionMatches(true, 0, "http://", 0, 7))
            throw new IllegalArgumentException("This browser supports HTTPS websites. Try an https:// address.");
        try {
            URI uri = new URI(value.replace(" ", "%20"));
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("https")) throw new IllegalArgumentException();
            URL parsed = uri.toURL();
            String host = parsed.getHost();
            if (host.isEmpty() || parsed.getUserInfo() != null || host.contains("%")) throw new IllegalArgumentException();
            if (!host.startsWith("[")) host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
            int port = parsed.getPort();
            if (port < -1 || port == 0 || port > 65535 || parsed.getAuthority().endsWith(":")) throw new IllegalArgumentException();
            String result = scheme + "://" + host + (port < 0 ? "" : ":" + port)
                + (uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery())
                + (uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment());
            // Keep existing escapes intact, including escaped slashes in paths.
            return new URI(result).toASCIIString();
        } catch (Exception error) {
            throw new IllegalArgumentException("Enter a valid HTTPS address (without a username or password).");
        }
    }

    static boolean isWebUrl(String url) {
        if (url == null || !url.regionMatches(true, 0, "https://", 0, 8)) return false;
        try { normalize(url); return true; }
        catch (IllegalArgumentException error) { return false; }
    }

    static String withoutFragment(String url) {
        if (url == null) return "";
        int fragment = url.indexOf('#');
        return fragment < 0 ? url : url.substring(0, fragment);
    }

    static boolean isEmbeddedScheme(String scheme) {
        return "data".equals(scheme) || "blob".equals(scheme) || "cid".equals(scheme) || "about".equals(scheme);
    }
}
