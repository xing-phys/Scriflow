package org.eink.inkprobe.notebook;

import java.util.Locale;

/** Short, stable labels for monochrome file-type badges. */
final class NotebookFileTypes {
    static String label(String name) {
        if (name == null) return "FILE";
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".fb2.zip")) return "FB2Z";
        int dot = lower.lastIndexOf('.');
        if (dot <= 0 || dot == lower.length() - 1) return "FILE";
        String extension = lower.substring(dot + 1);
        if (extension.equals("jpeg")) return "JPG";
        if (extension.equals("markdown")) return "MD";
        if (extension.length() > 5 || !extension.matches("[a-z0-9]+")) return "FILE";
        return extension.toUpperCase(Locale.ROOT);
    }

    private NotebookFileTypes() { }
}
