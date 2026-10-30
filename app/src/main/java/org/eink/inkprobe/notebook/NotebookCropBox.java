package org.eink.inkprobe.notebook;

/** Immutable displayed-page fractions; never modifies the original PDF or notebook coordinates. */
public final class NotebookCropBox {
    public static final NotebookCropBox FULL = new NotebookCropBox(0,0,1,1);
    public final float left, top, right, bottom;
    public NotebookCropBox(float left,float top,float right,float bottom) {
        if(!Float.isFinite(left)||!Float.isFinite(top)||!Float.isFinite(right)||!Float.isFinite(bottom)
                ||left<0||top<0||right>1||bottom>1||right-left<.005f||bottom-top<.005f)
            throw new IllegalArgumentException("Invalid page crop");
        this.left=left;this.top=top;this.right=right;this.bottom=bottom;
    }
    public float width(){return right-left;}
    public float height(){return bottom-top;}
    public boolean contains(float x,float y){return x>=left&&x<=right&&y>=top&&y<=bottom;}
    public NotebookCropBox padded(float fraction,NotebookCropBox limit) {
        if(!Float.isFinite(fraction)||fraction<0)throw new IllegalArgumentException("Invalid crop margin");
        return new NotebookCropBox(Math.max(limit.left,left-fraction),Math.max(limit.top,top-fraction),
            Math.min(limit.right,right+fraction),Math.min(limit.bottom,bottom+fraction));
    }
    @Override public boolean equals(Object o){if(!(o instanceof NotebookCropBox))return false;NotebookCropBox b=(NotebookCropBox)o;return left==b.left&&top==b.top&&right==b.right&&bottom==b.bottom;}
    @Override public int hashCode(){return java.util.Objects.hash(left,top,right,bottom);}
}
