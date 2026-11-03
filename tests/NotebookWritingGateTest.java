package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Fake-clock races at the boundary between native input and optional presentation. */
public final class NotebookWritingGateTest {
    private static int assertions;

    private static final class Fixture implements NotebookWritingGate.Scheduler {
        private static final class Entry {
            final Runnable task;
            final long time;
            Entry(Runnable task, long time) { this.task = task; this.time = time; }
        }
        long now = 1000;
        boolean nativePending;
        final List<Entry> queue = new ArrayList<>();
        final NotebookWritingGate gate = new NotebookWritingGate(() -> now, this, () -> nativePending);
        @Override public void postDelayed(Runnable task, long delay) {
            check(delay >= 0, "no negative callback delay");
            queue.add(new Entry(task, now + delay));
            check(queue.size() <= 1, "only one gate callback is outstanding");
        }
        @Override public void removeCallbacks(Runnable task) { queue.removeIf(entry -> entry.task == task); }
        void advance(long millis) {
            long end = now + millis;
            for (int count = 0; ; count++) {
                check(count < 10000, "no busy loop");
                Entry next = null;
                for (Entry entry : queue) if (entry.time <= end && (next == null || entry.time < next.time)) next = entry;
                if (next == null) break;
                queue.remove(next); now = next.time; next.task.run();
            }
            now = end;
        }
    }

    public static void main(String[] args) {
        burst(); pendingCommit(); queuedContact(); interleavedJobs(); lifecycle(); cancellation();
        System.out.println("NotebookWritingGate: " + assertions
            + " assertions passed (rapid strokes, pending commits, coalescing, rechecks and lifecycle).");
    }

    private static void burst() {
        Fixture f = new Fixture();
        final int[] visible = {-1}, latest = {0};
        Runnable paper = () -> visible[0] = latest[0];
        for (int stroke = 0; stroke < 500; stroke++) {
            f.gate.contact(true);
            latest[0] = stroke;
            f.gate.defer(paper);
            f.advance(20);
            check(f.queue.isEmpty() && visible[0] == -1, "long input has no polling or page redraw");
            f.gate.contact(false);
            f.gate.defer(paper);
            f.advance(80);
            check(visible[0] == -1, "a gap between characters does not release presentation");
        }
        f.advance(219);
        check(visible[0] == -1, "quiet interval is measured from the latest UP");
        f.advance(1);
        check(visible[0] == 499, "one update publishes the latest complete state");
        check(Arrays.equals(f.gate.statistics(), new long[]{1000, 999, 1, 0}), "notifications coalesce with bounded bookkeeping");
    }

    private static void pendingCommit() {
        Fixture f = new Fixture();
        final int[] displayed = {0};
        f.gate.contact(true); f.nativePending = true;
        f.gate.defer(() -> displayed[0]++);
        f.gate.contact(false);
        f.advance(1000);
        check(displayed[0] == 0 && !f.gate.isQuiet(), "pen UP and elapsed time cannot bypass an unmatched native commit");
        f.nativePending = false;
        f.advance(25);
        check(displayed[0] == 1 && f.gate.isQuiet(), "settled input releases the queued UI without another stroke");
    }

    private static void queuedContact() {
        Fixture f = new Fixture();
        final int[] displayed = {0};
        f.gate.defer(() -> displayed[0]++);
        check(f.queue.size() == 1, "quiet work is posted, never run inside an input callback");
        f.gate.contact(true);
        f.advance(1000);
        check(displayed[0] == 0, "a pen contact after enqueueing cancels publication");
        f.gate.contact(false); f.advance(300);
        check(displayed[0] == 1, "work resumes after the new burst");
        f.gate.defer(() -> displayed[0]++);
        f.nativePending = true;
        f.advance(100);
        check(displayed[0] == 1, "dispatch rechecks native input even without a contact notification");
        f.nativePending = false; f.advance(25);
        check(displayed[0] == 2, "late completion releases the second update");
    }

    private static void interleavedJobs() {
        Fixture f = new Fixture();
        List<String> work = new ArrayList<>();
        f.gate.defer(() -> { work.add("history"); f.gate.contact(true); });
        f.gate.defer(() -> work.add("chat"));
        f.advance(0);
        check(work.equals(Arrays.asList("history")), "a new contact between jobs protects the remaining feature");
        f.gate.contact(false); f.advance(300);
        check(work.equals(Arrays.asList("history", "chat")), "jobs retain order across writing");
        final Runnable[] more = new Runnable[1];
        more[0] = () -> { work.add("paper"); f.gate.cancel(more[0]); };
        f.gate.defer(more[0]); f.advance(0);
        check(work.size() == 3 && f.queue.isEmpty(), "callbacks can retire their own owner safely");
    }

    private static void lifecycle() {
        Fixture f = new Fixture();
        final int[] displayed = {0};
        f.gate.contact(true); f.gate.defer(() -> displayed[0]++);
        f.gate.suspend(); f.gate.contact(false); f.advance(1000);
        check(displayed[0] == 0 && f.queue.isEmpty(), "covered activity does not publish optional UI");
        f.gate.resume(); f.advance(0);
        check(displayed[0] == 1, "a retained owner can publish its latest state after resume");
        f.gate.defer(() -> displayed[0]++);
        Runnable stale = f.queue.get(0).task;
        f.gate.close(); stale.run(); f.gate.resume(); f.advance(1000);
        check(displayed[0] == 1 && f.queue.isEmpty(), "retired callbacks cannot resurrect a destroyed owner");
    }

    private static void cancellation() {
        Fixture f = new Fixture();
        final int[] displayed = {0};
        Runnable closedChat = () -> displayed[0]++;
        f.gate.contact(true); f.gate.defer(closedChat); f.gate.cancel(closedChat);
        f.gate.contact(false); f.advance(1000);
        check(displayed[0] == 0 && f.queue.isEmpty(), "closing a waiting chat releases its queued work");
        f.gate.defer(closedChat); f.gate.reset(); f.advance(1000);
        check(displayed[0] == 0 && f.queue.isEmpty(), "canvas replacement drops old callbacks");
        check(NotebookWritingGate.from(new Object()) == null, "non-writing screens do not acquire a gate");
        NotebookWritingGate.Owner owner = () -> f.gate;
        check(NotebookWritingGate.from(owner) == f.gate, "all features share the activity's coordinator");
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
