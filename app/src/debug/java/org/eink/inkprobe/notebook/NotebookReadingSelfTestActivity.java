package org.eink.inkprobe.notebook;

/** Emulator-only entry point for the real note reader with a software ink surface. */
public final class NotebookReadingSelfTestActivity extends NotebookActivity {
    @Override protected NotebookInkBackend createWritingSurface(NotebookPage initial, NotebookInkBackend.Listener listener) {
        return new NotebookBrowserTestInk(this, initial, listener);
    }
}
