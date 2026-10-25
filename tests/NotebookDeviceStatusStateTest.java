package org.eink.inkprobe.notebook;

/** Status changes coalesce while pen input owns the display. */
public final class NotebookDeviceStatusStateTest {
    private static int assertions;
    public static void main(String[] args) {
        NotebookDeviceStatusState state = new NotebookDeviceStatusState("12:59");
        check(state.publish(false) == null, "no initial invalidation while busy");
        NotebookDeviceStatusState.Snapshot initial = state.publish(true);
        check(initial.clock.equals("12:59") && initial.battery == -1 && initial.wifi == -1, "unknown state is explicit");
        check(state.publish(true) == null, "unchanged status never redraws");
        state.battery(78, 100); state.wifi(1);
        check(state.publish(false) == null, "battery and Wi-Fi wait during writing");
        state.clock("13:00"); state.battery(77, 100); state.wifi(0);
        check(state.publish(false) == null, "minute change does not disturb a long stroke");
        state.clock("13:01"); state.wifi(1);
        NotebookDeviceStatusState.Snapshot latest = state.publish(true);
        check(latest.clock.equals("13:01") && latest.battery == 77 && latest.wifi == 1, "pen idle publishes latest state once");
        check(initial.clock.equals("12:59") && initial.battery == -1, "previous displayed snapshot stays immutable");
        check(state.publish(true) == null, "queued intermediate events are not replayed");
        for (int i = 0; i < 100; i++) {
            state.battery(154, 200); state.wifi(1); state.clock("13:01");
            check(state.publish(true) == null, "irrelevant system broadcasts cannot cause repeated redraws");
        }
        state.battery(1, 2);
        check(state.publish(true).battery == 50, "battery scale normalized");
        state.battery(2, 3);
        check(state.publish(true).battery == 67, "fractional percent rounds");
        state.battery(Integer.MAX_VALUE, Integer.MAX_VALUE);
        check(state.publish(true).battery == 100, "large scale does not overflow");
        state.battery(0, 100);
        check(state.publish(true).battery == 0, "empty battery remains distinct from unknown");
        for (int[] invalid : new int[][]{{-1, 100}, {10, 0}, {10, -1}, {101, 100}}) {
            state.battery(50, 100); state.publish(true);
            state.battery(invalid[0], invalid[1]);
            check(state.publish(true).battery == -1, "invalid battery data is unavailable");
        }
        state.wifi(-1);
        check(state.publish(true).wifi == -1, "unavailable connectivity is distinct from disconnected");
        state.wifi(0);
        check(state.publish(true).wifi == 0, "disconnected indicator updates");
        state.clock("1:02 PM");
        check(state.publish(true).clock.equals("1:02 PM"), "device-formatted twelve-hour time retained");
        state.clock("09:02");
        check(state.publish(true).clock.equals("09:02"), "time-zone/manual time correction publishes");
        System.out.println("NotebookDeviceStatusState: " + assertions + " assertions passed (idle-only system status).");
    }
    private static void check(boolean condition, String detail) { assertions++; if (!condition) throw new AssertionError(detail); }
}
