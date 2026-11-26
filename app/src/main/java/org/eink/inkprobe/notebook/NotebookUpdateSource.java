package org.eink.inkprobe.notebook;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Restrict update metadata to this project's release assets and HTTPS CDN redirects. */
final class NotebookUpdateSource {
    private static final Pattern CHECKSUM = Pattern.compile("(?i)^([0-9a-f]{64})[ \\t]+\\*?([^\\r\\n]+)[\\r\\n]*$");
    private NotebookUpdateSource() { }
    static String apkName(String version) { return "scriflow-" + version + ".apk"; }
    static boolean releaseAsset(String url, String version, boolean checksum) {
        if (NotebookReleaseVersion.parse(version)==null) return false;
        String expected="/xing-phys/Scriflow/releases/download/v"+version+"/"+apkName(version)+(checksum?".sha256":"");
        try {
            URI uri=new URI(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && "github.com".equalsIgnoreCase(uri.getHost())
                && (uri.getPort()==-1||uri.getPort()==443) && uri.getUserInfo()==null && uri.getQuery()==null
                && uri.getFragment()==null && expected.equals(uri.getRawPath());
        } catch(Exception invalid){return false;}
    }
    static boolean trustedHttps(String url) {
        try {
            URI uri=new URI(url); String host=uri.getHost(); if(host==null)return false;
            host=host.toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(uri.getScheme()) && (uri.getPort()==-1||uri.getPort()==443)
                && uri.getUserInfo()==null && uri.getFragment()==null
                && (host.equals("api.github.com")||host.equals("github.com")||host.equals("release-assets.githubusercontent.com")||host.equals("objects.githubusercontent.com"));
        } catch(Exception invalid){return false;}
    }
    static String checksum(String text,String version) {
        if(text==null||text.length()>1024||NotebookReleaseVersion.parse(version)==null)return null;
        Matcher match=CHECKSUM.matcher(text);
        return match.matches() && match.group(2).equals(apkName(version)) ? match.group(1).toLowerCase(Locale.ROOT) : null;
    }
}
