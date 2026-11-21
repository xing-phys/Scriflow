package org.eink.inkprobe.notebook;

/** Latest-only readback; dashed/eraser input temporarily owns the shared display hardware. */
final class NotebookCastPacer {
    static final int MAX_EDGE = 2048, JPEG_QUALITY = 94;
    static final int IDLE = 0, PEN = 1, PROTECTED = 2;
    static final long QUIET_MS = 300;
    private long next;
    private long protectedUntil;
    private long generation;
    private int contact;
    synchronized void contact(int value,long now) {
        if(value<IDLE||value>PROTECTED) value=IDLE;
        if(contact==value)return;
        if(value==PROTECTED){generation++;next=0;}
        if(contact==PROTECTED&&value!=PROTECTED)protectedUntil=now+QUIET_MS;
        contact=value;
    }
    synchronized boolean protectedInput(long now) { return contact==PROTECTED || now<protectedUntil; }
    synchronized long generation() { return generation; }
    synchronized long delay(long now) { return Math.max(0, next-now); }
    synchronized void completed(long start, long end) {
        completed(start,end,generation);
    }
    synchronized void completed(long start, long end,long startedGeneration) {
        if(startedGeneration!=generation || protectedInput(end)){next=0;return;}
        long cost = Math.max(1, end-start);
        long interval = Math.max(contact!=IDLE ? 167 : 125, cost * (contact!=IDLE ? 4 : 2));
        next = Math.max(end, start+interval);
    }
    synchronized void reset() { next = 0; }
}
