package org.eink.inkprobe.notebook;

import java.util.Map;
import java.util.UUID;

/** Figure hit testing follows the visible page transform and reverse drawing order. */
final class NotebookFigureHit {
    static final class Hit {
        final int page;
        final UUID pageId, figureId;
        Hit(int page, UUID pageId, UUID figureId) { this.page = page; this.pageId = pageId; this.figureId = figureId; }
    }
    static Hit find(NotebookViewport viewport, Map<Integer, NotebookPage> pages, float scroll, float x, float y) {
        if (viewport == null) return null;
        int number = viewport.pageAt(x, y, scroll); if (number == 0) return null;
        NotebookPage page = pages.get(number); if (page == null) return null;
        float logicalX = (x - viewport.pageLeft(number)) / viewport.scale(number);
        float logicalY = (y - viewport.pageTop(number, scroll)) / viewport.scale(number);
        for (int i = page.figures.size() - 1; i >= 0; i--) {
            NotebookFigure figure = page.figures.get(i);
            if (figure.contains(logicalX, logicalY))
                return new Hit(number, page.id, figure.id);
        }
        return null;
    }
    private NotebookFigureHit() { }
}
