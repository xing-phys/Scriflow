package org.eink.inkprobe.notebook;

/** A link opens only after a short, unmoved, single-finger contact without pen activity. */
final class NotebookLinkTapGesture {
    private float x, y, slop;
    private long started;
    private int writingRevision;
    private boolean candidate;

    void begin(float x, float y, long time, float slop, int writingRevision) {
        this.x = x; this.y = y; this.started = time; this.slop = slop;
        this.writingRevision = writingRevision; candidate = true;
    }

    void move(float x, float y) {
        if (Math.hypot(x - this.x, y - this.y) > slop) candidate = false;
    }

    void cancel() { candidate = false; }

    boolean finish(float x, float y, long time, int writingRevision, boolean penDown) {
        move(x, y);
        boolean result = candidate && !penDown && this.writingRevision == writingRevision
            && time >= started && time - started <= 450;
        candidate = false;
        return result;
    }
}
