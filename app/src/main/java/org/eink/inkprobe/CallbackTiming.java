package org.eink.inkprobe;

import java.util.Arrays;

/** Bounded software delivery statistics. These do not measure panel/optical latency. */
final class CallbackTiming {
    private static final int CAPACITY = 8192;
    private final long[] delays = new long[CAPACITY];
    private final long[] gaps = new long[CAPACITY];
    private int delaySize, gapSize, delayNext, gapNext;
    private long samples, valid, negative, invalid, previousEvent = -1;

    synchronized void record(long eventTimeMs, long arrivalUptimeMs) {
        samples++;
        if (eventTimeMs <= 0) { invalid++; return; }
        if (arrivalUptimeMs < eventTimeMs) { negative++; return; }
        delays[delayNext] = arrivalUptimeMs - eventTimeMs;
        delayNext = (delayNext + 1) % CAPACITY;
        delaySize = Math.min(CAPACITY, delaySize + 1);
        valid++;
        if (previousEvent > 0 && eventTimeMs >= previousEvent) {
            gaps[gapNext] = eventTimeMs - previousEvent;
            gapNext = (gapNext + 1) % CAPACITY;
            gapSize = Math.min(CAPACITY, gapSize + 1);
        }
        previousEvent = eventTimeMs;
    }
    synchronized void reset() {
        delaySize = gapSize = delayNext = gapNext = 0;
        samples = valid = negative = invalid = 0;
        previousEvent = -1;
    }
    synchronized Snapshot snapshot() {
        long[] d = Arrays.copyOf(delays, delaySize);
        long[] g = Arrays.copyOf(gaps, gapSize);
        Arrays.sort(d); Arrays.sort(g);
        return new Snapshot(samples, valid, negative, invalid, delaySize,
            percentile(d, 50), percentile(d, 95), percentile(d, 100),
            percentile(g, 50), percentile(g, 95), percentile(g, 100));
    }
    private static long percentile(long[] sorted, int percent) {
        if (sorted.length == 0) return -1;
        return sorted[(int) ((percent * (long) sorted.length + 99) / 100) - 1];
    }
    static final class Snapshot {
        final long sampleCount, validDelayCount, negativeDelayCount, invalidEventTimeCount;
        final int windowSampleCount;
        final long p50DelayMs, p95DelayMs, maxDelayMs;
        final long p50EventGapMs, p95EventGapMs, maxEventGapMs;
        Snapshot(long samples, long valid, long negative, long invalid, int window,
                 long p50, long p95, long max, long gap50, long gap95, long gapMax) {
            sampleCount = samples; validDelayCount = valid; negativeDelayCount = negative;
            invalidEventTimeCount = invalid; windowSampleCount = window;
            p50DelayMs = p50; p95DelayMs = p95; maxDelayMs = max;
            p50EventGapMs = gap50; p95EventGapMs = gap95; maxEventGapMs = gapMax;
        }
    }
}
