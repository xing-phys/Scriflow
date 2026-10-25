package org.eink.inkprobe.notebook;

/** Coalesces system events without publishing display changes while the pen is busy. */
final class NotebookDeviceStatusState {
    static final class Snapshot {
        final String clock;
        final int battery, wifi;
        Snapshot(String clock, int battery, int wifi) {
            this.clock = clock; this.battery = battery; this.wifi = wifi;
        }
    }
    private String clock;
    private int battery = -1, wifi = -1;
    private Snapshot published;

    NotebookDeviceStatusState(String clock) { clock(clock); }
    void clock(String value) { clock = value == null ? "" : value; }
    void battery(int level, int scale) {
        battery = level < 0 || scale <= 0 || level > scale ? -1
            : (int) ((100L * level + scale / 2L) / scale);
    }
    void wifi(int value) { wifi = value < 0 ? -1 : value == 0 ? 0 : 1; }

    /** Null means that no view should be invalidated. The latest pending state is retained. */
    Snapshot publish(boolean idle) {
        if (!idle) return null;
        if (published != null && published.clock.equals(clock)
                && published.battery == battery && published.wifi == wifi) return null;
        published = new Snapshot(clock, battery, wifi);
        return published;
    }
}
