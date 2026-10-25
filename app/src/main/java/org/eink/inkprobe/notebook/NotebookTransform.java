package org.eink.inkprobe.notebook;

/** Explicit affine page-to-view transform. Samples retain pressure and pen-event timestamps. */
public final class NotebookTransform {
    public final float a, b, c, d, x, y;
    public NotebookTransform(float a, float b, float c, float d, float x, float y) {
        float[] values = {a,b,c,d,x,y};
        for (float value : values) if (!NotebookPage.finite(value)) throw new IllegalArgumentException("Non-finite transform");
        this.a=a; this.b=b; this.c=c; this.d=d; this.x=x; this.y=y;
    }
    public static NotebookTransform fit(float scale, float x, float y) {
        if (scale <= 0) throw new IllegalArgumentException("Invalid page scale");
        return new NotebookTransform(scale,0,0,scale,x,y);
    }
    public NotebookPage.Point map(NotebookPage.Point point) {
        return new NotebookPage.Point(a*point.x + c*point.y + x,
            b*point.x + d*point.y + y, point.time, point.pressure);
    }
    public NotebookTransform inverse() {
        double determinant = (double)a*d - (double)b*c;
        if (determinant == 0) throw new IllegalArgumentException("Singular transform");
        return new NotebookTransform((float)(d/determinant), (float)(-b/determinant),
            (float)(-c/determinant), (float)(a/determinant),
            (float)(((double)c*y - (double)d*x)/determinant),
            (float)(((double)b*x - (double)a*y)/determinant));
    }
}
