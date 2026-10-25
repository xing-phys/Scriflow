package org.eink.inkprobe.notebook;

/** Deliberately simple host-only outlines for PDF transport tests, not a brush approximation. */
final class NotebookOutlineFixture {
    static NotebookStrokeOutline outline(NotebookPage.Stroke stroke) {
        int count = stroke.points.size();
        float[] samples = new float[(count + 2) * 3];
        for (int i = 0; i < count; i++) {
            NotebookPage.Point point = stroke.points.get(i);
            set(samples, i, i / (float) (count + 1), point.x, point.y);
        }
        NotebookPage.Point first = stroke.points.get(0), last = stroke.points.get(count - 1);
        set(samples, count, count / (float) (count + 1), last.x + stroke.width, last.y);
        set(samples, count + 1, 1, first.x, first.y + stroke.width);
        return new NotebookStrokeOutline(samples, false);
    }

    private static void set(float[] samples, int index, float fraction, float x, float y) {
        samples[index * 3] = fraction;
        samples[index * 3 + 1] = x;
        samples[index * 3 + 2] = y;
    }
}
