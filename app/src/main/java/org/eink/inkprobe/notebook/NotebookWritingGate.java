package org.eink.inkprobe.notebook;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Main-thread scheduling for optional presentation work. Never schedules input or durable saves. */
final class NotebookWritingGate {
    static final long QUIET_MILLIS = 300;
    private static final long COMMIT_RECHECK_MILLIS = 25;

    interface Owner { NotebookWritingGate writingGate(); }
    interface Scheduler {
        void postDelayed(Runnable task, long delayMillis);
        void removeCallbacks(Runnable task);
    }

    static NotebookWritingGate from(Object owner) {
        return owner instanceof Owner ? ((Owner) owner).writingGate() : null;
    }

    private final LongSupplier clock;
    private final Scheduler scheduler;
    private final BooleanSupplier pendingInput;
    private final Set<Runnable> pending = new LinkedHashSet<>();
    private final Runnable dispatch = this::dispatch;
    private long lastContact = -1;
    private boolean down, scheduled, dispatching, suspended, closed;
    private long requests, coalesced, executed;

    NotebookWritingGate(LongSupplier clock, Scheduler scheduler, BooleanSupplier pendingInput) {
        this.clock = clock;
        this.scheduler = scheduler;
        this.pendingInput = pendingInput;
    }

    void contact(boolean down) {
        if (closed) return;
        this.down = down;
        lastContact = clock.getAsLong();
        cancelDispatch();
        schedule();
    }

    boolean isQuiet() {
        return !closed && !suspended && !down && !pendingInput.getAsBoolean() && remainingQuiet() == 0;
    }

    /** Use a retained Runnable per owner: repeated notifications keep only its latest state. */
    void defer(Runnable task) {
        if (closed) return;
        requests++;
        if (!pending.add(task)) coalesced++;
        schedule();
    }

    void cancel(Runnable task) {
        pending.remove(task);
        if (pending.isEmpty()) cancelDispatch();
    }

    void suspend() {
        suspended = true;
        cancelDispatch();
    }

    void resume() {
        if (closed) return;
        suspended = false;
        down = false;
        lastContact = -1;
        schedule();
    }

    /** Retire callbacks belonging to a replaced canvas, after its completed UPs have been saved. */
    void reset() {
        cancelDispatch();
        pending.clear();
        down = false;
        lastContact = -1;
    }

    void close() {
        reset();
        closed = true;
    }

    private long remainingQuiet() {
        if (lastContact < 0) return 0;
        long elapsed = Math.max(0, clock.getAsLong() - lastContact);
        return Math.max(0, QUIET_MILLIS - elapsed);
    }

    private void schedule() {
        if (closed || suspended || down || scheduled || dispatching || pending.isEmpty()) return;
        long delay = remainingQuiet();
        if (pendingInput.getAsBoolean()) delay = Math.max(delay, COMMIT_RECHECK_MILLIS);
        scheduled = true;
        scheduler.postDelayed(dispatch, delay);
    }

    private void cancelDispatch() {
        if (scheduled) scheduler.removeCallbacks(dispatch);
        scheduled = false;
    }

    private void dispatch() {
        scheduled = false;
        // Recheck at execution, including late native commits and pen contacts after enqueueing.
        if (!isQuiet()) { schedule(); return; }
        Iterator<Runnable> iterator = pending.iterator();
        if (!iterator.hasNext()) return;
        Runnable task = iterator.next();
        iterator.remove();
        dispatching = true;
        try { executed++; task.run(); }
        finally { dispatching = false; schedule(); }
    }

    /** Bounded counters for diagnostics; no point logging or allocations in live MOVE callbacks. */
    long[] statistics() { return new long[]{requests, coalesced, executed, pending.size()}; }
}
