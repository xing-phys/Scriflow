package org.eink.inkprobe.notebook;

/** Cross-page drop fitting and an unconstrained, pointer-anchored floating preview. */
final class NotebookSelectionMove {
    private NotebookSelectionMove() { }

    static final class Destination {
        final int page;
        final boolean fits;
        final float centerX, centerY, scale, screenLeft, screenTop;

        Destination(int page, boolean fits, float centerX, float centerY,
                    float scale, float screenLeft, float screenTop) {
            this.page = page; this.fits = fits; this.centerX = centerX; this.centerY = centerY;
            this.scale = scale; this.screenLeft = screenLeft; this.screenTop = screenTop;
        }
    }

    /** Grab offsets are logical distances from the selected bounds' center. */
    static Destination place(NotebookViewport viewport, int sourcePage, NotebookLasso.Bounds bounds,
                             float grabX, float grabY, float screenX, float screenY, float scroll) {
        if (viewport == null || bounds == null || !finite(grabX, grabY, screenX, screenY, scroll,
                bounds.left, bounds.top, bounds.right, bounds.bottom)
                || bounds.left > bounds.right || bounds.top > bounds.bottom)
            throw new IllegalArgumentException("Invalid selection movement");
        viewport.pageWidth(sourcePage); // Reject a stale source page before producing a drop target.
        int page = viewport.nearestPage(screenY, scroll);
        float scale = viewport.scale(page);
        float width = viewport.pageWidth(page)*viewport.crop(page).width(), height = viewport.pageHeight(page)*viewport.crop(page).height();
        float minX=viewport.cropLeft(page),minY=viewport.cropTop(page);
        float boundsX = bounds.left / 2 + bounds.right / 2;
        float boundsY = bounds.top / 2 + bounds.bottom / 2;
        float centerX = (screenX - viewport.pageLeft(page)) / scale - grabX;
        float centerY = (screenY - viewport.pageTop(page, scroll)) / scale - grabY;
        boolean fits = bounds.right - bounds.left <= width && bounds.bottom - bounds.top <= height;
        if (fits) {
            centerX = boundsX + clamp(centerX - boundsX, minX-bounds.left, minX+width - bounds.right);
            centerY = boundsY + clamp(centerY - boundsY, minY-bounds.top, minY+height - bounds.bottom);
        } else {
            // A complete-bounds fit does not exist. Keep a neutral logical center;
            // callers must reject the drop, while the floating preview stays anchored.
            centerX = minX+width / 2; centerY = minY+height / 2;
        }
        float left = screenX - (boundsX + grabX) * scale;
        float top = screenY - (boundsY + grabY) * scale;
        if (!finite(centerX, centerY, left, top)) throw new IllegalArgumentException("Selection movement is too large");
        return new Destination(page, fits, centerX, centerY, scale, left, top);
    }

    /** Signed pixels per UI tick. Ramp smoothly at each edge and cap outside the viewport. */
    static float edgeScroll(float y, int height, float edge, float step) {
        if (!finite(y, edge, step) || height <= 0 || edge < 0 || step < 0)
            throw new IllegalArgumentException("Invalid selection edge scrolling");
        if (edge == 0 || step == 0) return 0;
        float band = Math.min(edge, height / 2f);
        if (y < band) return -step * Math.min(1, (band - y) / band);
        if (y > height - band) return step * Math.min(1, (y - (height - band)) / band);
        return 0;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean finite(float... values) {
        for (float value : values) if (!NotebookPage.finite(value)) return false;
        return true;
    }
}
