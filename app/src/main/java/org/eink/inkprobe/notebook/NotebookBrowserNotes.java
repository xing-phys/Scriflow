package org.eink.inkprobe.notebook;

/** One browser note layout. Retired screens cannot overwrite the foreground owner's changes. */
final class NotebookBrowserNotes {
    static final class Snapshot {
        final String layout;
        final int editor;
        final long revision;
        Snapshot(String layout, int editor, long revision) {
            this.layout = layout; this.editor = Math.max(0, editor); this.revision = Math.max(0, revision);
        }
    }
    private Snapshot current;
    private Object owner;
    NotebookBrowserNotes(String layout, int editor, long revision) { current = new Snapshot(layout, editor, revision); }
    Snapshot current() { return current; }
    Snapshot claim(Object token) { owner = token; return current; }
    boolean owns(Object token) { return owner == token; }
    void release(Object token) { if (owns(token)) owner = null; }
    Snapshot save(Object token, String layout, int editor) {
        if (owns(token) && (!java.util.Objects.equals(layout, current.layout) || editor != current.editor))
            current = new Snapshot(layout, editor, current.revision + 1);
        return current;
    }
}
