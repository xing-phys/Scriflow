package org.eink.inkprobe.notebook;

/** Own each slider gesture; completed choices become the baseline for the next adjustment. */
final class NotebookPenSliderState {
    private float committed;
    private final NotebookWidthRange range;
    private float width;
    private int pointer = -1;
    private boolean active, accepted;

    NotebookPenSliderState(float original) { this(NotebookWidthRange.PEN, original); }
    NotebookPenSliderState(NotebookWidthRange range, float original) {
        this.range = range; this.committed = width = range.restore(original);
    }
    float width() { return width; }
    int pointer() { return pointer; }
    boolean active() { return active; }

    void begin(int id, boolean inside, float fraction) {
        active = true; accepted = inside; pointer = inside ? id : -1;
        width = committed;
        move(id, fraction);
    }
    void move(int id, float fraction) {
        if (active && accepted && id == pointer && pointer >= 0)
            width = range.atFraction(fraction);
    }
    void lift(int id, float fraction) {
        move(id, fraction);
        if (id == pointer) pointer = -1;
    }
    void cancel() { accepted = false; pointer = -1; }
    void choose(float chosenWidth) {
        if (active) throw new IllegalStateException("Finish the slider contact before applying an accessibility choice");
        committed = width = range.atFraction((chosenWidth - range.minimum) / (range.maximum - range.minimum));
    }
    Float end(int id, float fraction, boolean cancelled) {
        if (!active) return null;
        move(id, fraction);
        Float choice = accepted && !cancelled ? width : null;
        if (choice != null) committed = width;
        else width = committed;
        active = false; accepted = false; pointer = -1;
        return choice;
    }
}
