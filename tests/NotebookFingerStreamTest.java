package org.eink.inkprobe.notebook;

/** Finger/pen ownership regressions independent of Android event delivery. */
public final class NotebookFingerStreamTest {
    private static int assertions;

    public static void main(String[] args) {
        pureFingerDrag();
        mixedFingerLiftsBeforePen();
        mixedPenLiftsBeforeFinger();
        mixedCancellation();
        palmsAndUnownedEvents();
        multipleFingers();
        resetAbandonsOwnership();
        System.out.println("NotebookFingerStream: " + assertions + " assertions passed (gesture ownership).");
    }

    private static void pureFingerDrag() {
        NotebookFingerStream stream = new NotebookFingerStream();
        expect(stream, NotebookFingerStream.DOWN, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        expect(stream, NotebookFingerStream.MOVE, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        expect(stream, NotebookFingerStream.UP, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        // Terminal delivery clears ownership before a callback could re-enter the helper.
        expect(stream, NotebookFingerStream.MOVE, false, true, false, NotebookFingerStream.Dispatch.CONSUME);
        expect(stream, NotebookFingerStream.DOWN, true, false, false, NotebookFingerStream.Dispatch.UNHANDLED);
        expect(stream, NotebookFingerStream.DOWN, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        expect(stream, NotebookFingerStream.CANCEL, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
    }

    private static void mixedFingerLiftsBeforePen() {
        NotebookFingerStream stream = new NotebookFingerStream();
        expect(stream, NotebookFingerStream.DOWN, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        expect(stream, NotebookFingerStream.POINTER_DOWN, true, false, false, NotebookFingerStream.Dispatch.CONSUME);
        expect(stream, NotebookFingerStream.MOVE, true, true, false, NotebookFingerStream.Dispatch.CONSUME);
        expect(stream, NotebookFingerStream.POINTER_UP, true, true, false, NotebookFingerStream.Dispatch.CONSUME);
        // Regression: final UP belongs to the stylus, but the Activity must still end its drag.
        expect(stream, NotebookFingerStream.UP, true, false, false, NotebookFingerStream.Dispatch.CANCEL);
        expect(stream, NotebookFingerStream.DOWN, true, false, false, NotebookFingerStream.Dispatch.UNHANDLED);
        expect(stream, NotebookFingerStream.DOWN, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
    }

    private static void mixedPenLiftsBeforeFinger() {
        NotebookFingerStream stream = new NotebookFingerStream();
        expect(stream, NotebookFingerStream.DOWN, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        expect(stream, NotebookFingerStream.POINTER_DOWN, true, false, false, NotebookFingerStream.Dispatch.CONSUME);
        expect(stream, NotebookFingerStream.POINTER_UP, true, false, false, NotebookFingerStream.Dispatch.CONSUME);
        // Latch mixed ownership: a remaining finger may not continue scrolling after pen UP.
        expect(stream, NotebookFingerStream.MOVE, false, true, false, NotebookFingerStream.Dispatch.CONSUME);
        expect(stream, NotebookFingerStream.UP, false, true, false, NotebookFingerStream.Dispatch.CANCEL);
        expect(stream, NotebookFingerStream.UP, false, true, false, NotebookFingerStream.Dispatch.CONSUME);
    }

    private static void mixedCancellation() {
        NotebookFingerStream stream = new NotebookFingerStream();
        expect(stream, NotebookFingerStream.DOWN, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        expect(stream, NotebookFingerStream.POINTER_DOWN, true, false, false, NotebookFingerStream.Dispatch.CONSUME);
        // CANCEL may report no pointers and no changed finger; ownership still delivers it.
        expect(stream, NotebookFingerStream.CANCEL, false, false, false, NotebookFingerStream.Dispatch.CANCEL);
        expect(stream, NotebookFingerStream.DOWN, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        // A late native pen-down observation also protects against simultaneous pen input.
        expect(stream, NotebookFingerStream.MOVE, false, true, true, NotebookFingerStream.Dispatch.CONSUME);
        expect(stream, NotebookFingerStream.CANCEL, true, false, false, NotebookFingerStream.Dispatch.CANCEL);
    }

    private static void palmsAndUnownedEvents() {
        NotebookFingerStream stream = new NotebookFingerStream();
        expect(stream, NotebookFingerStream.DOWN, true, false, false, NotebookFingerStream.Dispatch.UNHANDLED);
        expect(stream, NotebookFingerStream.POINTER_DOWN, true, true, true, NotebookFingerStream.Dispatch.UNHANDLED);
        expect(stream, NotebookFingerStream.POINTER_UP, true, false, true, NotebookFingerStream.Dispatch.UNHANDLED);
        expect(stream, NotebookFingerStream.MOVE, true, true, true, NotebookFingerStream.Dispatch.UNHANDLED);
        expect(stream, NotebookFingerStream.CANCEL, true, true, true, NotebookFingerStream.Dispatch.UNHANDLED);
        expect(stream, NotebookFingerStream.MOVE, false, true, false, NotebookFingerStream.Dispatch.CONSUME);
        expect(stream, NotebookFingerStream.UP, false, true, false, NotebookFingerStream.Dispatch.CONSUME);
        expect(stream, NotebookFingerStream.DOWN, false, true, true, NotebookFingerStream.Dispatch.UNHANDLED);
        expect(stream, NotebookFingerStream.MOVE, false, true, false, NotebookFingerStream.Dispatch.CONSUME);
        expect(stream, NotebookFingerStream.CANCEL, false, false, false, NotebookFingerStream.Dispatch.UNHANDLED);
        expect(stream, NotebookFingerStream.CANCEL, false, true, false, NotebookFingerStream.Dispatch.UNHANDLED);
        expect(stream, NotebookFingerStream.DOWN, false, false, false, NotebookFingerStream.Dispatch.UNHANDLED);
        expect(stream, NotebookFingerStream.DOWN, true, true, false, NotebookFingerStream.Dispatch.CONSUME);
    }

    private static void multipleFingers() {
        NotebookFingerStream stream = new NotebookFingerStream();
        expect(stream, NotebookFingerStream.DOWN, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        expect(stream, NotebookFingerStream.POINTER_DOWN, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        expect(stream, NotebookFingerStream.POINTER_UP, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        // Pointer replacement belongs to the Activity; the stream remains owned until final UP.
        expect(stream, NotebookFingerStream.MOVE, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        expect(stream, NotebookFingerStream.POINTER_DOWN, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        expect(stream, NotebookFingerStream.POINTER_UP, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        expect(stream, NotebookFingerStream.UP, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        expect(stream, NotebookFingerStream.MOVE, false, true, false, NotebookFingerStream.Dispatch.CONSUME);
    }

    private static void resetAbandonsOwnership() {
        NotebookFingerStream stream = new NotebookFingerStream();
        expect(stream, NotebookFingerStream.DOWN, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        expect(stream, NotebookFingerStream.POINTER_DOWN, true, false, false, NotebookFingerStream.Dispatch.CONSUME);
        stream.reset();
        expect(stream, NotebookFingerStream.UP, true, false, false, NotebookFingerStream.Dispatch.UNHANDLED);
        expect(stream, NotebookFingerStream.DOWN, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        stream.reset();
        expect(stream, NotebookFingerStream.MOVE, false, true, false, NotebookFingerStream.Dispatch.CONSUME);
        expect(stream, NotebookFingerStream.DOWN, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
        expect(stream, NotebookFingerStream.UP, false, true, false, NotebookFingerStream.Dispatch.FORWARD);
    }

    private static void expect(NotebookFingerStream stream, int action, boolean hasPen,
                               boolean changedFinger, boolean penDown, NotebookFingerStream.Dispatch expected) {
        NotebookFingerStream.Dispatch actual = stream.accept(action, hasPen, changedFinger, penDown);
        assertions++;
        if (actual != expected) throw new AssertionError("Action " + action + ": expected " + expected + ", got " + actual);
    }
}
