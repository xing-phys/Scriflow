package org.eink.inkprobe.notebook;

/** Own the width result across window removal and its synchronous input callbacks. */
final class NotebookPenSliderClose {
    interface Listener { void onClosed(Float chosenWidth); }

    private final Runnable dismissWindow;
    private final Listener listener;
    private Float chosenWidth;
    private boolean closing, delivered;

    NotebookPenSliderClose(Runnable dismissWindow, Listener listener) {
        this.dismissWindow = dismissWindow; this.listener = listener;
    }

    void finish(Float width) {
        if (closing) return;
        // Removing a touched popup can synchronously send CANCEL before onDismiss.
        // Lock the release/cancel decision before entering that removal path.
        closing = true;
        chosenWidth = width;
        dismissWindow.run();
    }

    void dismissed() {
        if (delivered) return;
        closing = true;
        delivered = true;
        listener.onClosed(chosenWidth);
    }
}
