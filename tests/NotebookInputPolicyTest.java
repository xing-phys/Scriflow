package org.eink.inkprobe.notebook;

/** Simulated pen/hover/palm streams and deliberate pinch activation without Android stubs. */
public final class NotebookInputPolicyTest {
    private static int assertions;
    public static void main(String[] args) {
        penProximityAndQuietPeriod();
        contactSizes();
        rejectedPalmsStayRejected();
        palmsCannotStealThePen();
        suspiciousAcceptedGesturesCancel();
        deliberatePinch();
        slowRestingContactsAndJitter();
        System.out.println("NotebookInputPolicy: " + assertions + " assertions passed (pen priority and pinch intent).");
    }

    private static void penProximityAndQuietPeriod() {
        NotebookPalmGuard guard = new NotebookPalmGuard();
        check(!guard.rejectFingers(0, false), "fresh canvas accepts navigation");
        guard.hover(true, 100);
        check(guard.rejectFingers(100000, false), "stationary nearby pen blocks touch even without more hover moves");
        guard.hover(false, 100000);
        check(guard.rejectFingers(100199, false), "quiet period after hover exit");
        check(!guard.rejectFingers(100200, false), "intentional navigation recovers after quiet period");
        check(!guard.rejectFingers(100201, false), "navigation remains allowed after 200 ms");
        guard.penContact(true, 101000);
        check(guard.rejectFingers(200000, false), "long pen contact never times out");
        guard.penContact(false, 200000);
        check(guard.rejectFingers(200199, false), "resting hand remains suppressed between strokes");
        guard.penSeen(200500);
        check(guard.rejectFingers(200699, false), "Android pen sample renews protection before native callback");
        check(!guard.rejectFingers(200700, false), "quiet period ends exactly once");
        check(guard.rejectFingers(201200, true), "broad palm is rejected without a pen");
    }

    private static void contactSizes() {
        check(!NotebookPalmGuard.broadContact(0, 8), "unreported contact width remains usable");
        check(!NotebookPalmGuard.broadContact(70, 8), "ordinary finger accepted");
        check(!NotebookPalmGuard.broadContact(191, 8), "below physical threshold accepted");
        check(NotebookPalmGuard.broadContact(192, 8), "24 mm contact classified as broad");
        check(NotebookPalmGuard.broadContact(300, 8), "large palm detected");
        check(!NotebookPalmGuard.broadContact(50, 0), "unknown display scale cannot manufacture a palm");
        check(!NotebookPalmGuard.broadContact(Float.NaN, 8), "invalid size cannot reject all fingers");
    }

    private static void rejectedPalmsStayRejected() {
        NotebookFingerStream stream = new NotebookFingerStream();
        dispatch(stream, 0, false, true, false, true, NotebookFingerStream.Dispatch.CONSUME);
        // Even when the pen moves away, the old resting contacts must lift before navigation.
        dispatch(stream, 5, false, true, false, false, NotebookFingerStream.Dispatch.CONSUME);
        dispatch(stream, 2, false, true, false, false, NotebookFingerStream.Dispatch.CONSUME);
        dispatch(stream, 6, false, true, false, false, NotebookFingerStream.Dispatch.CONSUME);
        dispatch(stream, 1, false, true, false, false, NotebookFingerStream.Dispatch.CONSUME);
        dispatch(stream, 0, false, true, false, false, NotebookFingerStream.Dispatch.FORWARD);
        dispatch(stream, 1, false, true, false, false, NotebookFingerStream.Dispatch.FORWARD);
    }

    private static void palmsCannotStealThePen() {
        NotebookFingerStream stream = new NotebookFingerStream();
        dispatch(stream, 0, false, true, false, true, NotebookFingerStream.Dispatch.CONSUME);
        dispatch(stream, 5, true, false, true, true, NotebookFingerStream.Dispatch.UNHANDLED);
        dispatch(stream, 2, true, false, true, true, NotebookFingerStream.Dispatch.UNHANDLED);
        dispatch(stream, 6, true, false, false, true, NotebookFingerStream.Dispatch.UNHANDLED);
        dispatch(stream, 2, false, true, false, true, NotebookFingerStream.Dispatch.CONSUME);
        dispatch(stream, 1, false, true, false, false, NotebookFingerStream.Dispatch.CONSUME);
        dispatch(stream, 0, false, true, false, false, NotebookFingerStream.Dispatch.FORWARD);
        dispatch(stream, 3, false, false, false, false, NotebookFingerStream.Dispatch.FORWARD);
    }

    private static void suspiciousAcceptedGesturesCancel() {
        NotebookFingerStream stream = new NotebookFingerStream();
        dispatch(stream, 0, false, true, false, false, NotebookFingerStream.Dispatch.FORWARD);
        dispatch(stream, 5, false, true, false, false, NotebookFingerStream.Dispatch.FORWARD);
        dispatch(stream, 2, false, true, false, true, NotebookFingerStream.Dispatch.CONSUME);
        dispatch(stream, 2, false, true, false, false, NotebookFingerStream.Dispatch.CONSUME);
        dispatch(stream, 1, false, true, false, false, NotebookFingerStream.Dispatch.CANCEL);
        dispatch(stream, 0, false, true, false, false, NotebookFingerStream.Dispatch.FORWARD);
        dispatch(stream, 5, true, false, true, true, NotebookFingerStream.Dispatch.CONSUME);
        dispatch(stream, 3, false, false, false, false, NotebookFingerStream.Dispatch.CANCEL);
    }

    private static void deliberatePinch() {
        NotebookPinchGesture pinch = new NotebookPinchGesture();
        pinch.begin(100, 200, 200, 12);
        check(!pinch.update(220, 250), "fast contact placement is not immediately zoom");
        check(!pinch.update(270, 205), "span jitter ignored");
        check(pinch.update(280, 220), "deliberate expansion begins zoom");
        check(pinch.update(290, 218), "confirmed pinch follows small continuous movements");
        pinch.begin(1000, 1000, 200, 12);
        check(pinch.update(1100, 180), "deliberate contraction begins zoom");
        pinch.begin(2000, 2350, 100, 12);
        check(pinch.update(2450, 125), "join boundary remains inclusive");
    }

    private static void slowRestingContactsAndJitter() {
        NotebookPinchGesture pinch = new NotebookPinchGesture();
        pinch.begin(0, 1000, 200, 12);
        check(!pinch.update(1100, 300), "late extra contact on a resting hand cannot start zoom");
        pinch.begin(0, 20, 20, 12);
        check(!pinch.update(200, 100), "tiny initial split is not two deliberate fingers");
        pinch.begin(1000, 1100, 500, 12);
        for (int i = 0; i < 100; i++)
            check(!pinch.update(1200 + i * 10, 500 + (i % 19 - 9)), "jitter does not accumulate into zoom");
        check(!pinch.update(2500, Float.NaN), "invalid span ignored");
        check(!pinch.update(2500, Float.POSITIVE_INFINITY), "unbounded span ignored");
        check(!pinch.update(2500, 0), "zero span ignored");
        pinch.begin(2500, 2510, Float.POSITIVE_INFINITY, 12);
        check(!pinch.update(2700, 200), "invalid initial span cannot become a pinch");
        pinch.begin(3000, 3010, 500, 12);
        check(pinch.update(3100, 521), "fresh deliberate gesture recovers after rejected gesture");
    }

    private static void dispatch(NotebookFingerStream stream, int action, boolean pen, boolean finger,
            boolean down, boolean rejected, NotebookFingerStream.Dispatch expected) {
        check(stream.accept(action, pen, finger, down, rejected) == expected, "stream action " + action + " expected " + expected);
    }
    private static void check(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError(message);
    }
}
