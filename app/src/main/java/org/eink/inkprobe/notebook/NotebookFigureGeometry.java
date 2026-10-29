package org.eink.inkprobe.notebook;

/** Pointer geometry shared by in-note figure gestures and host checks. */
final class NotebookFigureGeometry {
    static NotebookFigure move(NotebookViewport viewport, NotebookFigure figure, int target,
            float grabX, float grabY, float screenX, float screenY, float scroll) {
        float scale = viewport.scale(target);
        float cx = (screenX - viewport.pageLeft(target)) / scale - grabX;
        float cy = (screenY - viewport.pageTop(target, scroll)) / scale - grabY;
        return figure.placed(cx - figure.width / 2, cy - figure.height / 2, figure.width, figure.height);
    }
    static NotebookFigure resize(NotebookFigure start, float pointerX, float pointerY) {
        float ax = start.cornerX(0), ay = start.cornerY(0), dx = pointerX - ax, dy = pointerY - ay;
        float u = dx * start.cosine() + dy * start.sine(), v = -dx * start.sine() + dy * start.cosine();
        float factor = Math.max(.02f, (u * start.width + v * start.height)
            / (start.width * start.width + start.height * start.height));
        float w = start.width * factor, h = start.height * factor;
        float cx = ax + (w * start.cosine() - h * start.sine()) / 2;
        float cy = ay + (w * start.sine() + h * start.cosine()) / 2;
        return start.placed(cx - w / 2, cy - h / 2, w, h);
    }
    static NotebookFigure rotate(NotebookFigure start, float downX, float downY, float x, float y) {
        double a = Math.atan2(downY - start.centerY(), downX - start.centerX());
        double b = Math.atan2(y - start.centerY(), x - start.centerX());
        return start.turned(start.angle + (float) Math.toDegrees(b - a));
    }
    private NotebookFigureGeometry() { }
}
