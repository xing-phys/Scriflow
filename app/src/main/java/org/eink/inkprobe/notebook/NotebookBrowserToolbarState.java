package org.eink.inkprobe.notebook;

/** Browser presentation and bounded overlay geometry, independent of the web page. */
final class NotebookBrowserToolbarState {
    private boolean collapsed, zen;
    private float horizontal, vertical;

    NotebookBrowserToolbarState(boolean collapsed, float horizontal, float vertical) {
        this.collapsed = collapsed; position(horizontal, vertical);
    }

    boolean collapsed() { return collapsed; }
    boolean zen() { return zen; }
    boolean controlsVisible() { return !collapsed && !zen; }
    boolean gripVisible() { return !zen; }
    float horizontal() { return horizontal; }
    float vertical() { return vertical; }
    void collapse(boolean value) { collapsed = value; }
    void zen(boolean value) { zen = value; }
    void position(float horizontal, float vertical) {
        this.horizontal = fraction(horizontal); this.vertical = fraction(vertical);
    }

    void moveTo(float left, float top, int width, int height, int grip, int margin) {
        Layout bounds = layout(width, height, grip, 1, 1, margin, 0);
        int across = Math.max(0, width - 2 * bounds.margin - bounds.gripSize);
        int down = Math.max(0, height - 2 * bounds.margin - bounds.gripSize);
        position(across == 0 ? 0 : (left - bounds.margin) / across,
            down == 0 ? 0 : (top - bounds.margin) / down);
    }

    Layout layout(int width, int height, int grip, int panelWidth, int panelHeight, int margin, int gap) {
        int w = Math.max(1, width), h = Math.max(1, height);
        int m = Math.min(Math.max(0, margin), (Math.min(w, h) - 1) / 2);
        int size = Math.min(Math.max(1, grip), Math.min(w, h) - 2 * m);
        int x = m + (int) Math.round((double) (w - 2 * m - size) * horizontal);
        int y = m + (int) Math.round((double) (h - 2 * m - size) * vertical);
        int separation = Math.max(0, gap);
        int below = Math.max(0, h - m - (y + size) - separation);
        int above = Math.max(0, y - m - separation);
        int pw = Math.min(Math.max(1, panelWidth), w - 2 * m);
        int ph = Math.min(Math.max(1, panelHeight), Math.max(1, Math.max(above, below)));
        int px = Math.max(m, Math.min(w - m - pw, x));
        int py = below >= above && below > 0 ? y + size + separation : above > 0 ? y - separation - ph : m;
        py = Math.max(m, Math.min(h - m - ph, py));
        return new Layout(m, x, y, size, px, py, pw, ph);
    }

    private static float fraction(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) ? 0 : Math.max(0, Math.min(1, value));
    }

    static final class Layout {
        final int margin, gripLeft, gripTop, gripSize, panelLeft, panelTop, panelWidth, panelHeight;
        Layout(int margin, int x, int y, int size, int px, int py, int pw, int ph) {
            this.margin = margin; gripLeft = x; gripTop = y; gripSize = size;
            panelLeft = px; panelTop = py; panelWidth = pw; panelHeight = ph;
        }
    }
}
