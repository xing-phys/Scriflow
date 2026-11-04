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
        float w,h;
        if(start.markdown!=null) {w=Math.max(start.width*.02f,u);h=Math.max(start.height*.02f,v);}
        else {
            float factor = Math.max(.02f, (u * start.width + v * start.height)
                / (start.width * start.width + start.height * start.height));
            w=start.width*factor;h=start.height*factor;
        }
        float cx = ax + (w * start.cosine() - h * start.sine()) / 2;
        float cy = ay + (w * start.sine() + h * start.cosine()) / 2;
        return start.placed(cx - w / 2, cy - h / 2, w, h);
    }
    static int markdownWidth(NotebookFigure original,NotebookFigure resized) {
        return Math.max(160,Math.min(1600,Math.round(original.markdown.width()*resized.width/original.width)));
    }
    static int markdownHeight(NotebookFigure resized,int width) {
        return Math.max(0,Math.min(16384,Math.round(width*resized.height/resized.width)));
    }
    static NotebookFigure reflow(NotebookFigure resized,NotebookMarkdown markdown) {
        float ax=resized.cornerX(0),ay=resized.cornerY(0),w=resized.width,h=w*markdown.height()/markdown.width();
        float cx=ax+(w*resized.cosine()-h*resized.sine())/2,cy=ay+(w*resized.sine()+h*resized.cosine())/2;
        return resized.withMarkdown(markdown).placed(cx-w/2,cy-h/2,w,h);
    }
    static NotebookFigure rotate(NotebookFigure start, float downX, float downY, float x, float y) {
        double a = Math.atan2(downY - start.centerY(), downX - start.centerX());
        double b = Math.atan2(y - start.centerY(), x - start.centerX());
        return start.turned(start.angle + (float) Math.toDegrees(b - a));
    }
    private NotebookFigureGeometry() { }
}
