package org.eink.inkprobe.notebook;

/** Portable workspace geometry. Fractions survive orientation and display-size changes. */
public final class NotebookWorkspace {
    public enum Position { LEFT, RIGHT, TOP, BOTTOM, FLOATING }
    public Position position = Position.RIGHT;
    public int notebookId, page = 1;
    public boolean collapsed, secondaryActive;
    public float split = .5f, x = .42f, y = .18f, width = .55f, height = .58f;
    public static final class Box {
        public final int x, y, width, height;
        Box(int x, int y, int width, int height) { this.x = x; this.y = y; this.width = width; this.height = height; }
    }
    public int secondaryNotebook(int activeId) { return secondaryActive ? activeId : notebookId; }
    public int primaryNotebook(int activeId) { return secondaryActive ? notebookId : activeId; }
    /** Called only after the writer has successfully opened the previously inactive note. */
    public void activatedOther(int previousId, int previousPage) {
        if (notebookId <= 0 || previousId <= 0 || previousId == notebookId || previousPage < 1)
            throw new IllegalArgumentException("Invalid workspace activation");
        notebookId = previousId; page = previousPage; secondaryActive = !secondaryActive; collapsed = false;
    }
    public Box editor(int w, int h) {
        return secondaryActive && notebookId > 0 && !collapsed ? secondary(w, h) : primary(w, h);
    }
    public Box companion(int w, int h) { return secondaryActive ? primary(w, h) : secondary(w, h); }
    public Box primary(int w, int h) {
        if (notebookId <= 0 || collapsed || position == Position.FLOATING) return new Box(0, 0, w, h);
        int side = Math.round((position == Position.LEFT || position == Position.RIGHT ? w : h) * clamp(split, .25f, .75f));
        switch (position) {
            case LEFT: return new Box(side, 0, w - side, h);
            case RIGHT: return new Box(0, 0, w - side, h);
            case TOP: return new Box(0, side, w, h - side);
            default: return new Box(0, 0, w, h - side);
        }
    }
    public Box secondary(int w, int h) {
        if (position == Position.FLOATING) {
            int fw = Math.max(1, Math.round(w * clamp(width, .3f, .95f)));
            int fh = Math.max(1, Math.round(h * clamp(height, .3f, .95f)));
            return new Box(Math.round(clamp(x, 0, 1) * (w - fw)), Math.round(clamp(y, 0, 1) * (h - fh)), fw, fh);
        }
        Box editor = primary(w, h);
        switch (position) {
            case LEFT: return new Box(0, 0, editor.x, h);
            case RIGHT: return new Box(editor.width, 0, w - editor.width, h);
            case TOP: return new Box(0, 0, w, editor.y);
            default: return new Box(0, editor.height, w, h - editor.height);
        }
    }
    static float clamp(float value, float low, float high) { return NotebookPage.finite(value) ? Math.max(low, Math.min(high, value)) : low; }
}
