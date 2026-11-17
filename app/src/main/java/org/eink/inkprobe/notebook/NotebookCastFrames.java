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
    private boolean sharing;
    private byte[] jpeg;

    synchronized void sharing(boolean enabled, long nowNanos) {
        if (sharing == enabled) return;
        sharing = enabled; generation++; sequence++; jpeg = null;
        // Discard queued compositor frames from before the app became visible.
        notBefore = nowNanos + 350_000_000L;
    }

    synchronized long ticket(long imageNanos) {
        return sharing && imageNanos >= notBefore ? generation : -1;
    }

    synchronized boolean publish(long ticket, byte[] bytes) {
        if (!sharing || ticket < 0 || ticket != generation) return false;
        jpeg = bytes; sequence++; return true;
    }

    synchronized Frame latest() {
        return new Frame(sequence, jpeg, !sharing ? "paused" : jpeg == null ? "waiting" : "live");
    }
}
