package android.graphics;

/** Java2D path adapter for the Android graphics-state boundary in PDFBox's text interpreter. */
public class Path {
    public enum FillType{WINDING,EVEN_ODD,INVERSE_WINDING,INVERSE_EVEN_ODD}
    public enum Direction{CW,CCW}
    private java.awt.geom.Path2D.Float path=new java.awt.geom.Path2D.Float();
    public Path(){}public Path(Path other){path=(java.awt.geom.Path2D.Float)other.path.clone();}
    public void moveTo(float x,float y){path.moveTo(x,y);}public void lineTo(float x,float y){path.lineTo(x,y);}
    public void cubicTo(float a,float b,float c,float d,float e,float f){path.curveTo(a,b,c,d,e,f);}
    public void quadTo(float a,float b,float c,float d){path.quadTo(a,b,c,d);}public void close(){path.closePath();}
    public void reset(){path.reset();}public void rewind(){path.reset();}public boolean isEmpty(){return path.getCurrentPoint()==null;}
    public void addRect(float l,float t,float r,float b,Direction direction){path.append(new java.awt.geom.Rectangle2D.Float(l,t,r-l,b-t),false);}
    public void addPath(Path other){path.append(other.path,false);}
    public void computeBounds(RectF result,boolean exact){java.awt.geom.Rectangle2D bounds=path.getBounds2D();result.set((float)bounds.getMinX(),(float)bounds.getMinY(),(float)bounds.getMaxX(),(float)bounds.getMaxY());}
    public void setFillType(FillType type){path.setWindingRule(type==FillType.EVEN_ODD?java.awt.geom.Path2D.WIND_EVEN_ODD:java.awt.geom.Path2D.WIND_NON_ZERO);}
}
