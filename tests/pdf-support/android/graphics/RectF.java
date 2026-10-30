package android.graphics;

/** Host-only geometry contract for the real PDFBox text interpreter, not device verification. */
public class RectF {
    public float left,top,right,bottom;
    public RectF(){}
    public RectF(float l,float t,float r,float b){set(l,t,r,b);}
    public RectF(RectF r){set(r.left,r.top,r.right,r.bottom);}
    public void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}
    public float width(){return right-left;}public float height(){return bottom-top;}
    public boolean isEmpty(){return left>=right||top>=bottom;}
    public void union(float l,float t,float r,float b){if(isEmpty())set(l,t,r,b);else set(Math.min(left,l),Math.min(top,t),Math.max(right,r),Math.max(bottom,b));}
}
