package org.eink.inkprobe.notebook;

/** In-memory latest frame, with revocable ownership for asynchronous encoders. */
final class NotebookCastFrames {
    static final class Frame {
        final long sequence;
        final byte[] jpeg;
        final String state;
        Frame(long sequence, byte[] jpeg, String state) {
            this.sequence = sequence; this.jpeg = jpeg; this.state = state;
        }
    }
    private long generation, sequence, notBefore;
    private boolean sharing, holding, suspended;
    private byte[] jpeg;

    synchronized void sharing(boolean enabled, long nowNanos) {
        if (sharing == enabled && !holding) return;
        sharing = enabled; holding = false; suspended = false; generation++; sequence++; jpeg = null;
        // Discard queued compositor frames from before the app became visible.
        notBefore = nowNanos;
        notifyAll();
    }

    synchronized void hold() {
        if (holding) return;
        sharing = false; holding = true; suspended = false; generation++; sequence++; notifyAll();
    }

    /** Freeze the last safe frame and reject encodes while a latency-sensitive gesture runs. */
    synchronized void suspendCapture(boolean value, long nowNanos) {
        if (suspended == value) return;
        suspended = value; generation++;
        if (!value) notBefore = nowNanos;
    }

    synchronized long ticket(long imageNanos) {
        return sharing && !suspended && imageNanos >= notBefore ? generation : -1;
    }

    synchronized boolean publish(long ticket, byte[] bytes) {
        if (!sharing || suspended || ticket < 0 || ticket != generation) return false;
        if (java.util.Arrays.equals(jpeg, bytes)) return true;
        jpeg = bytes; sequence++; notifyAll(); return true;
    }

    synchronized Frame latest() {
        return new Frame(sequence, jpeg, holding ? "holding" : !sharing ? "paused" : jpeg == null ? "waiting" : "live");
    }

    synchronized Frame await(String etag, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (sharing && ("\"" + sequence + "\"").equals(etag)) {
            long remaining = (deadline-System.nanoTime()) / 1_000_000L;
            if (remaining <= 0) break;
            wait(remaining);
        }
        return latest();
    }
}
