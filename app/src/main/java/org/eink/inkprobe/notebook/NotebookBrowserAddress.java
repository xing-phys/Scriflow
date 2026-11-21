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
                throw new IllegalArgumentException("Enter a valid website address.");
        if (value.startsWith("//")) value = "https:" + value;
        else if (!value.contains("://")) value = "https://" + value;
        try {
            URI uri = new URI(value.replace(" ", "%20"));
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("https") && !scheme.equals("http")) throw new IllegalArgumentException();
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
            throw new IllegalArgumentException("Enter a valid HTTP or HTTPS address (without a username or password).");
        }
    }

    static String destination(String input, int provider) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Enter a search or website address.");
        if (value.contains("://") || value.startsWith("//") || value.startsWith("[") || !value.matches(".*\\s.*") && (value.contains(".") || value.contains(":") || value.equalsIgnoreCase("localhost"))) return normalize(value);
        if (value.length() > 2048) throw new IllegalArgumentException("This search is too long.");
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) throw new IllegalArgumentException("Enter a single-line search.");
        try { return (provider == 1 ? "https://www.google.com/search?q=" : provider == 2 ? "https://www.bing.com/search?q=" : "https://duckduckgo.com/?q=") + java.net.URLEncoder.encode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException error) { throw new IllegalStateException(error); }
    }

    static boolean isWebUrl(String url) {
        if (url == null || !(url.regionMatches(true, 0, "https://", 0, 8)
                || url.regionMatches(true, 0, "http://", 0, 7))) return false;
        try { normalize(url); return true; }
        catch (IllegalArgumentException error) { return false; }
    }

    static String origin(String url) {
        if (!isWebUrl(url)) return "";
        URI uri = URI.create(normalize(url)); int port = uri.getPort();
        String scheme = uri.getScheme();
        int defaultPort = scheme.equals("https") ? 443 : 80;
        return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (port < 0 || port == defaultPort ? "" : ":" + port);
    }

    static String withoutFragment(String url) {
        if (url == null) return "";
        int fragment = url.indexOf('#');
        return fragment < 0 ? url : url.substring(0, fragment);
    }

    static boolean isEmbeddedScheme(String scheme) {
        return "data".equals(scheme) || "blob".equals(scheme) || "cid".equals(scheme) || "about".equals(scheme);
    }

    /** Only same-page anchors may be rewritten from the original URL into an offline copy. */
    static String offlineTarget(String archive, String source, String target) {
        if (archive == null || !archive.startsWith("file:///") || target == null) return null;
        String base = withoutFragment(archive);
        if (base.equals(withoutFragment(target))) return target;
        int fragment = target.indexOf('#');
        return fragment >= 0 && isWebUrl(source) && withoutFragment(source).equals(withoutFragment(target))
            ? base + target.substring(fragment) : null;
    }
}
