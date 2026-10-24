package org.eink.inkprobe;

/** Host-only checks: point timestamps describe sampling; arrival times describe delivery. */
public final class CallbackTimingTest {
    private static int assertions;

    public static void main(String[] args) {
        emptySnapshot();
        batchedDeliveryIsNotFastDelivery();
        nearestRankWithSmallSamples();
        invalidClocksAreFlagged();
        gapsExcludeInvalidRecords();
        duplicateAndOutOfOrderEvents();
        snapshotsAreIndependent();
        resetStartsNewMeasurement();
        boundedWindowDropsOldOutliers();
        System.out.println("CallbackTiming: " + assertions + " assertions passed (host JVM, no Android stubs).");
    }

    private static void emptySnapshot() {
        CallbackTiming.Snapshot s = new CallbackTiming().snapshot();
        equal(0, s.sampleCount, "empty calls");
        equal(0, s.validDelayCount, "empty valid delays");
        equal(0, s.windowSampleCount, "empty window");
        equal(-1, s.p50DelayMs, "empty delay median");
        equal(-1, s.p95DelayMs, "empty delay p95");
        equal(-1, s.maxDelayMs, "empty delay maximum");
        equal(-1, s.p50EventGapMs, "empty event gap median");
        equal(-1, s.p95EventGapMs, "empty event gap p95");
        equal(-1, s.maxEventGapMs, "empty event gap maximum");
    }

    private static void batchedDeliveryIsNotFastDelivery() {
        CallbackTiming timing = new CallbackTiming();
        // A firmware callback batch can contain closely sampled points that arrive late.
        timing.record(1000, 1300);
        timing.record(1010, 1300);
        timing.record(1020, 1300);
        timing.record(1030, 1300);
        CallbackTiming.Snapshot s = timing.snapshot();
        equal(4, s.sampleCount, "batch calls");
        equal(4, s.validDelayCount, "batch valid delays");
        equal(280, s.p50DelayMs, "batch delivery delay median");
        equal(300, s.p95DelayMs, "batch delivery delay p95");
        equal(300, s.maxDelayMs, "batch delivery delay maximum");
        equal(10, s.p50EventGapMs, "sampling interval survives delayed delivery");
        equal(10, s.maxEventGapMs, "sampling gap is not callback delay");
    }

    private static void nearestRankWithSmallSamples() {
        CallbackTiming timing = new CallbackTiming();
        timing.record(1000, 1009); // delay 9
        CallbackTiming.Snapshot one = timing.snapshot();
        equal(9, one.p50DelayMs, "one-sample median");
        equal(9, one.p95DelayMs, "one-sample p95");
        equal(-1, one.maxEventGapMs, "one point has no event gap");
        timing.record(1010, 1011); // sorted delays: 1, 9
        equal(1, timing.snapshot().p50DelayMs, "two-sample nearest-rank median");
        equal(9, timing.snapshot().p95DelayMs, "two-sample nearest-rank p95");
        timing.record(1020, 1024); // sorted delays: 1, 4, 9
        equal(4, timing.snapshot().p50DelayMs, "three-sample median");
        equal(9, timing.snapshot().p95DelayMs, "three-sample p95");
        timing.record(1030, 1036); // sorted delays: 1, 4, 6, 9
        equal(4, timing.snapshot().p50DelayMs, "even-sample nearest-rank median");
        equal(9, timing.snapshot().maxDelayMs, "maximum from unsorted delay samples");
    }

    private static void invalidClocksAreFlagged() {
        CallbackTiming timing = new CallbackTiming();
        timing.record(0, 500);
        timing.record(-10, -20); // invalid event time must not be counted twice
        timing.record(1000, 999); // event clock is ahead of arrival clock
        timing.record(1010, 1010); // zero delay is valid
        CallbackTiming.Snapshot s = timing.snapshot();
        equal(4, s.sampleCount, "invalid calls remain visible");
        equal(2, s.invalidEventTimeCount, "invalid event timestamps");
        equal(1, s.negativeDelayCount, "negative delay clock warning");
        equal(1, s.validDelayCount, "invalid delays excluded from statistics");
        equal(1, s.windowSampleCount, "invalid delays excluded from bounded window");
        equal(0, s.p50DelayMs, "valid zero delay");
        equal(0, s.maxDelayMs, "clock mismatch does not pollute delay maximum");
    }

    private static void gapsExcludeInvalidRecords() {
        CallbackTiming timing = new CallbackTiming();
        timing.record(1000, 1005);
        timing.record(0, 1006);
        timing.record(2000, 1007); // negative delay; do not move the reference timestamp
        timing.record(1010, 1015);
        CallbackTiming.Snapshot s = timing.snapshot();
        equal(10, s.p50EventGapMs, "invalid samples do not advance the event-gap reference");
        equal(10, s.maxEventGapMs, "invalid event clocks cannot create a huge gap");
    }

    private static void duplicateAndOutOfOrderEvents() {
        CallbackTiming timing = new CallbackTiming();
        timing.record(1000, 1005);
        timing.record(1000, 1005); // legitimate duplicate timestamp: zero gap
        timing.record(1010, 1015); // gap 10
        timing.record(1005, 1010); // negative gap must not be retained
        CallbackTiming.Snapshot s = timing.snapshot();
        equal(4, s.validDelayCount, "out-of-order sampling still has a valid delivery delay");
        equal(0, s.p50EventGapMs, "zero event gaps retained");
        equal(10, s.p95EventGapMs, "negative event gap excluded");
        equal(10, s.maxEventGapMs, "out-of-order event does not inflate maximum");
    }

    private static void snapshotsAreIndependent() {
        CallbackTiming timing = new CallbackTiming();
        timing.record(1000, 1001);
        CallbackTiming.Snapshot before = timing.snapshot();
        timing.record(1010, 1090);
        CallbackTiming.Snapshot after = timing.snapshot();
        equal(1, before.sampleCount, "snapshot count remains fixed");
        equal(1, before.maxDelayMs, "snapshot statistics remain fixed");
        equal(2, after.sampleCount, "subsequent snapshot sees new records");
        equal(80, after.maxDelayMs, "new outlier retained");
    }

    private static void resetStartsNewMeasurement() {
        CallbackTiming timing = new CallbackTiming();
        timing.record(1000, 1100);
        timing.record(0, 1101);
        timing.record(1200, 1102);
        timing.reset();
        CallbackTiming.Snapshot reset = timing.snapshot();
        equal(0, reset.sampleCount, "reset total calls");
        equal(0, reset.validDelayCount, "reset valid delays");
        equal(0, reset.invalidEventTimeCount, "reset event-time warnings");
        equal(0, reset.negativeDelayCount, "reset clock warnings");
        equal(0, reset.windowSampleCount, "reset retained window");
        equal(-1, reset.maxDelayMs, "reset delay maximum");
        equal(-1, reset.maxEventGapMs, "reset event gap maximum");
        timing.record(5000, 5002);
        equal(2, timing.snapshot().maxDelayMs, "new measurement after reset");
        equal(-1, timing.snapshot().maxEventGapMs, "reset forgets previous event reference");
    }

    private static void boundedWindowDropsOldOutliers() {
        CallbackTiming timing = new CallbackTiming();
        timing.record(1000, 11000); // old delay outlier
        timing.record(11000, 11001); // old gap outlier
        for (int i = 1; i <= 8192; i++) timing.record(11000 + i, 11001 + i);
        CallbackTiming.Snapshot s = timing.snapshot();
        equal(8194, s.sampleCount, "lifetime call count exceeds window");
        equal(8194, s.validDelayCount, "lifetime valid count exceeds window");
        equal(8192, s.windowSampleCount, "bounded retained delay window");
        equal(1, s.p50DelayMs, "window delay median");
        equal(1, s.p95DelayMs, "window delay p95");
        equal(1, s.maxDelayMs, "evicted delay outlier absent from maximum");
        equal(1, s.p50EventGapMs, "window event-gap median");
        equal(1, s.p95EventGapMs, "window event-gap p95");
        equal(1, s.maxEventGapMs, "evicted event-gap outlier absent from maximum");
    }

    private static void equal(long expected, long actual, String description) {
        assertions++;
        if (expected != actual) {
            throw new AssertionError(description + ": expected " + expected + ", got " + actual);
        }
    }
}
