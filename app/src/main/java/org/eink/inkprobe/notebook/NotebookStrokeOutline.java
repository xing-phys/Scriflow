package org.eink.inkprobe.notebook;

import java.io.IOException;

/** Filled native brush geometry, flattened in source-page units, independent of Android. */
public final class NotebookStrokeOutline {
    public interface Sink {
        void moveTo(float x, float y) throws IOException;
        void lineTo(float x, float y) throws IOException;
        void close() throws IOException;
    }

    private final float[] samples; // Path.approximate triples: cumulative fraction, x, y.
    public final boolean evenOdd;

    public NotebookStrokeOutline(float[] samples, boolean evenOdd) {
        this(samples, evenOdd, true);
    }

    /** Takes a fresh native result without doubling a potentially large stroke allocation. */
    static NotebookStrokeOutline take(float[] samples, boolean evenOdd) {
        return new NotebookStrokeOutline(samples, evenOdd, false);
    }

    private NotebookStrokeOutline(float[] samples, boolean evenOdd, boolean copy) {
        if (samples == null || samples.length < 3 || samples.length % 3 != 0)
            throw new IllegalArgumentException("Invalid stroke outline");
        float previous = -1;
        for (int i = 0; i < samples.length; i += 3) {
            float fraction = samples[i];
            if (!NotebookPage.finite(fraction) || fraction < previous || fraction < 0 || fraction > 1
                    || !NotebookPage.finite(samples[i + 1]) || !NotebookPage.finite(samples[i + 2]))
                throw new IllegalArgumentException("Invalid stroke outline sample");
            previous = fraction;
        }
        this.samples = copy ? samples.clone() : samples;
        this.evenOdd = evenOdd;
    }

    public int vertexCount() { return samples.length / 3; }

    /** One fill for all contours preserves winding, holes, and translucent overlap behavior. */
    public void writeTo(Sink sink) throws IOException {
        float fraction = -1, x = 0, y = 0;
        boolean started = false;
        for (int i = 0; i < samples.length; i += 3) {
            float nextFraction = samples[i], nextX = samples[i + 1], nextY = samples[i + 2];
            if (!started || nextFraction == fraction && (nextX != x || nextY != y)) {
                if (started) sink.close();
                sink.moveTo(nextX, nextY);
                started = true;
            } else if (nextX != x || nextY != y) sink.lineTo(nextX, nextY);
            fraction = nextFraction; x = nextX; y = nextY;
        }
        if (started) sink.close();
    }
}
