package org.eink.inkprobe.notebook;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Filename handling for untrusted HTTP headers and URLs. */
final class NotebookDownloadName {
    private static final Pattern PARAMETER = Pattern.compile("(?i)(?:^|;)\\s*(filename\\*?)\\s*=\\s*(?:\"((?:\\\\.|[^\"])*)\"|([^;]*))");
    private NotebookDownloadName() { }

    static String choose(String url, String disposition) {
        String extended = null, basic = null;
        if (disposition != null && disposition.length() <= 16384) {
            Matcher matcher = PARAMETER.matcher(disposition);
            while (matcher.find()) {
                String value = matcher.group(2) != null ? matcher.group(2).replaceAll("\\\\(.)", "$1") : matcher.group(3).trim();
                if (matcher.group(1).endsWith("*")) {
                    int marker = value.indexOf("''");
                    if (marker >= 0 && value.substring(0, marker).equalsIgnoreCase("UTF-8")) extended = decode(value.substring(marker + 2));
                } else if (basic == null) basic = value;
            }
        }
        String value = extended != null && !extended.isEmpty() ? extended : basic;
        if (value == null || value.trim().isEmpty()) {
            try {
                String path = new URI(url).getRawPath();
                if (path != null && !path.isEmpty() && !path.endsWith("/")) value = decode(path.substring(path.lastIndexOf('/') + 1));
            } catch (Exception ignored) { }
        }
        return clean(value);
    }

    static String clean(String value) {
        if (value == null) value = "";
        value = Normalizer.normalize(value, Normalizer.Form.NFC).trim();
        StringBuilder result = new StringBuilder();
        for (int at = 0; at < value.length();) {
            int code = value.codePointAt(at); at += Character.charCount(code);
            if (Character.isISOControl(code) || code == 0x2028 || code == 0x2029 || code >= 0x202a && code <= 0x202e
                    || code >= 0x2066 && code <= 0x2069) continue;
            result.appendCodePoint("/\\:*?\"<>|".indexOf(code) >= 0 ? '_' : code);
        }
        String name = result.toString().trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) name = "download";
        String suffix = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && name.length() - dot <= 24) { suffix = name.substring(dot); name = name.substring(0, dot); }
        while (!name.isEmpty() && (name + suffix).getBytes(StandardCharsets.UTF_8).length > 200)
            name = name.substring(0, name.offsetByCodePoints(name.length(), -1));
        return (name.isEmpty() ? "download" : name) + suffix;
    }

    static String mime(String value) {
        if (value == null) return "application/octet-stream";
        String type = value.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return type.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+") ? type : "application/octet-stream";
    }

    static long length(String value, String encoding) {
        if (encoding != null && !encoding.isEmpty() && !encoding.equalsIgnoreCase("identity")) return -1;
        try { long bytes = Long.parseLong(value); return bytes >= 0 ? bytes : -1; }
        catch (RuntimeException invalid) { return -1; }
    }

    private static String decode(String value) {
        try { return URLDecoder.decode(value.replace("+", "%2B"), "UTF-8"); }
        catch (Exception invalid) { return value; }
    }
}
