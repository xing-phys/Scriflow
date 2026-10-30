package android.graphics;
/** Host-only point contract used by PDFBox matrix arithmetic. */
public class PointF {
    public float x,y;
    public PointF(){}public PointF(float x,float y){this.x=x;this.y=y;}
    public void set(float x,float y){this.x=x;this.y=y;}
}
