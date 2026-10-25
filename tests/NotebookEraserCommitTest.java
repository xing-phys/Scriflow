package org.eink.inkprobe.notebook;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Exercises the actual native observer join against real document/history/storage. */
public final class NotebookEraserCommitTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        for (boolean trackFirst : new boolean[]{false, true}) {
            for (float width : new float[]{4, 36, 120, 240}) {
                for (boolean cancelled : new boolean[]{false, true})
                    observerOrder(trackFirst, width, cancelled);
            }
        }
        resetAndMismatch();
        durableEraseAfterBothObservers();
        System.out.println("NotebookEraserCommit: " + assertions + " assertions passed (native ordering, cancellation and files).");
    }

    private static void observerOrder(boolean trackFirst, float width, boolean cancelled) {
        NotebookEraserCommit gate = new NotebookEraserCommit();
        List<NotebookPage.Point> path = new ArrayList<>(Arrays.asList(point(-3, 20, 10), point(40, 40, 20)));
        gate.begin(width);
        check(gate.isActive() && !gate.isWaiting(), "DOWN captures a native eraser without a completed edit");
        check(gate.takeReady() == null, "DOWN cannot erase");
        if (trackFirst) gate.track(20); else gate.up(path, 20, cancelled);
        check(gate.takeReady() == null, "one observer alone cannot erase or clear the live native track");
        check(gate.isWaiting() != trackFirst, "UP blocks controls while waiting for native completion");
        if (trackFirst) gate.up(path, 20, cancelled); else gate.track(20);
        path.clear(); // The completion must own its path even after an input buffer is reused.
        NotebookEraserCommit.Result result = gate.takeReady();
        check(result != null && result.path.size() == 2, "both observers deliver one complete path");
        check(result.path.get(0).x == -3, "off-page centre survives for wide-brush gap/edge hits");
        check(result.radius == width / 2, "radius uses the diameter captured at DOWN");
        check(result.cancelled == cancelled, "cancellation never becomes an accepted erase");
        check(!gate.isActive() && !gate.isWaiting(), "gate clears before a reentrant pause/apply callback");
        check(gate.takeReady() == null, "a completed native gesture is delivered once");
        gate.begin(12);
        check(gate.isActive(), "next gesture starts after canonical edit");
        gate.reset();
        check(!gate.isActive(), "pause/tool change clears unfinished state");
    }

    private static void resetAndMismatch() {
        NotebookEraserCommit gate = new NotebookEraserCommit();
        gate.begin(36);
        gate.up(Arrays.asList(point(1, 2, 20)), 20, false);
        gate.reset();
        check(gate.takeReady() == null && !gate.isWaiting(), "pause discards a released but unsettled preview");
        gate.begin(4);
        gate.up(Arrays.asList(point(1, 2, 30)), 30, false);
        gate.track(31);
        boolean rejected = false;
        try { gate.takeReady(); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "mismatched native tracks cannot erase unrelated ink");
        gate.reset();
        gate.begin(240);
        rejected = false;
        try { gate.begin(4); } catch (IllegalStateException expected) { rejected = true; }
        check(rejected, "a pending native preview cannot be overwritten by another DOWN");
    }

    private static void durableEraseAfterBothObservers() throws Exception {
        File directory = Files.createTempDirectory("notebook-native-eraser-").toFile();
        try {
            NotebookDocument document = new NotebookDocument(directory);
            document.load(); document.initializeSize(400, 400);
            document.append(new NotebookPage.Stroke(2, 4, 0xff000000,
                Arrays.asList(point(80, 80, 1), point(120, 80, 2))));
            document.append(new NotebookPage.Stroke(2, 4, 0xff000000,
                Arrays.asList(point(250, 250, 3), point(300, 250, 4))));
            NotebookEraserCommit gate = new NotebookEraserCommit();
            gate.begin(36);
            List<NotebookPage.Point> path = Arrays.asList(point(100, 50, 100), point(100, 120, 200));
            gate.up(path, 200, false);
            check(gate.takeReady() == null, "an UP does not submit before the native track finishes");
            check(new NotebookStore(directory).load().strokes.size() == 2, "preview and UP never alter saved ink");
            gate.track(200);
            NotebookEraserCommit.Result ready = gate.takeReady();
            check(document.erase(ready.path, ready.radius), "settled preview drives the existing whole-stroke edit");
            NotebookPage saved = new NotebookStore(directory).load();
            check(saved.strokes.size() == 1 && saved.strokes.get(0).color == 0xff000000,
                "only unaffected real ink is saved; grey preview is absent");
            check(document.undo(), "one native gesture creates one undo command");
            check(new NotebookStore(directory).load().strokes.size() == 2, "undo restores the original ink without grey");
            check(document.redo(), "native erasure remains redoable");
            check(new NotebookStore(directory).load().strokes.size() == 1, "redo persists only original remaining ink");
        } finally { deleteTree(directory); }
    }

    private static NotebookPage.Point point(float x, float y, long time) {
        return new NotebookPage.Point(x, y, time, .5f);
    }
    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) throw new AssertionError("Cannot remove native eraser fixture " + file);
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
