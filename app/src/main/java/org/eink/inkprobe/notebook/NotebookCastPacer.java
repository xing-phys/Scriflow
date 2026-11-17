package org.eink.inkprobe.notebook;

/** Latest-only readback with a bounded CPU duty cycle; pen contact gets the larger reserve. */
final class NotebookCastPacer {
    static final int MAX_EDGE = 2048, JPEG_QUALITY = 94;
    private long next;
    private boolean contact;
    synchronized void contact(boolean down) { contact = down; }
    synchronized long delay(long now) { return Math.max(0, next-now); }
    synchronized void completed(long start, long end) {
        long cost = Math.max(1, end-start);
        long interval = Math.max(contact ? 167 : 125, cost * (contact ? 4 : 2));
        next = Math.max(end, start+interval);
    }
    synchronized void reset() { next = 0; }
}
