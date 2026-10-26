package org.eink.inkprobe.notebook;

import java.util.List;

/** Either pointer observer may finish first; native edit/track notifications are unnecessary. */
final class NotebookLassoCompletion {
    private List<NotebookPage.Point> path;
    private boolean cancelled;
    boolean isPending() { return path != null; }
    boolean offer(List<NotebookPage.Point> candidate, boolean nativeSamples) {
        if (candidate == null) return false;
        boolean first = path == null;
        if (first || !cancelled && (nativeSamples || candidate.isEmpty())) path = candidate;
        cancelled |= candidate.isEmpty();
        return first;
    }
    List<NotebookPage.Point> take() { List<NotebookPage.Point> result = path; reset(); return result; }
    void reset() { path = null; cancelled = false; }
}
