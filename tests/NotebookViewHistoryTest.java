package org.eink.inkprobe.notebook;

import java.util.UUID;

/** The return arrows follow PDF links and outline destinations; ordinary reading never grows or branches history. */
public final class NotebookViewHistoryTest {
    private static int assertions;
    public static void main(String[] args) {
        ordinaryReadingActionsNeverRecord(); linkAndOutlineRoundTrips(); ordinaryReadingPreservesForward();
        System.out.println("NotebookViewHistory: " + assertions + " assertions passed (link and outline navigation policy).");
    }

    private static void ordinaryReadingActionsNeverRecord() {
        for (NotebookViewHistory.Navigation action : NotebookViewHistory.Navigation.values()) {
            if (action == NotebookViewHistory.Navigation.PDF_LINK || action == NotebookViewHistory.Navigation.OUTLINE) continue;
            NotebookViewHistory history = new NotebookViewHistory();
            history.record(view(1, 20, 1), action);
            history.record(view(10, 900, 4), action);
            check(!history.canBack() && !history.canForward(), action + " never creates a return destination");
        }
    }

    private static void linkAndOutlineRoundTrips() {
        NotebookViewHistory history = new NotebookViewHistory();
        NotebookViewHistory.State origin = view(1, 20, 1), link = view(7, 80, 2), outline = view(22, 140, .5f);
        history.record(origin, NotebookViewHistory.Navigation.PDF_LINK);
        history.record(link, NotebookViewHistory.Navigation.OUTLINE);
        NotebookViewHistory.State read = view(23, 400, 3);
        history.record(outline, NotebookViewHistory.Navigation.SCROLL);
        history.record(read, NotebookViewHistory.Navigation.PAGE_BUTTON);
        check(history.back(read).equals(link), "Back skips scrolling/page changes and returns to outline origin");
        check(history.back(link).equals(origin) && !history.canBack(), "next Back returns to PDF link origin");
        check(history.forward(origin).equals(link), "Forward restores exact link origin including zoom");
        check(history.forward(link).equals(read) && !history.canForward(), "Forward restores departed reading position");
    }

    private static void ordinaryReadingPreservesForward() {
        NotebookViewHistory history = new NotebookViewHistory();
        NotebookViewHistory.State origin = view(1, 0, 1), target = view(8, 60, 2);
        history.record(origin, NotebookViewHistory.Navigation.PDF_LINK);
        history.back(target);
        for (NotebookViewHistory.Navigation action : NotebookViewHistory.Navigation.values()) {
            if (action == NotebookViewHistory.Navigation.PDF_LINK || action == NotebookViewHistory.Navigation.OUTLINE) continue;
            history.record(view(3, 400, 4), action);
            check(history.canForward() && !history.canBack(), action + " preserves forward return destination");
        }
        NotebookViewHistory.State moved = view(3, 400, 4);
        check(history.forward(moved).equals(target), "Forward still restores the link destination after ordinary movement");
        history.back(target);
        history.record(moved, NotebookViewHistory.Navigation.OUTLINE);
        check(!history.canForward() && history.back(target).equals(moved), "new outline jump branches from current reading view");
    }

    private static NotebookViewHistory.State view(int page, float y, float zoom) {
        return new NotebookViewHistory.State(new UUID(0, page), page, y, zoom, "CUSTOM");
    }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
