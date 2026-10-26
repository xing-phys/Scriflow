package org.eink.inkprobe.notebook;

/** Require two fresh fingers and deliberate span change before zoom can start. */
final class NotebookPinchGesture {
    static final long JOIN_MS = 350, INTENT_MS = 60;
    private float initialSpan, threshold;
    private long joinedAt;
    private boolean eligible, confirmed;

    void begin(long firstDown, long now, float span, float slop) {
        initialSpan = span; threshold = Math.max(slop, span * .04f); joinedAt = now;
        eligible = NotebookPage.finite(span) && NotebookPage.finite(slop) && slop > 0
            && now >= firstDown && now - firstDown <= JOIN_MS && span > slop * 2;
        confirmed = false;
    }

    boolean update(long now, float span) {
        if (!eligible || !NotebookPage.finite(span) || span <= 0) return false;
        if (!confirmed && now - joinedAt >= INTENT_MS && Math.abs(span - initialSpan) >= threshold) confirmed = true;
        return confirmed;
    }
}
