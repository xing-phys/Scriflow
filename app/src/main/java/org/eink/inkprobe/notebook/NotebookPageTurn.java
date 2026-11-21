package org.eink.inkprobe.notebook;

/** Page swipes only at a whole-page view; zoomed drags always remain panning gestures. */
final class NotebookPageTurn {
    static final class ScrollMove {
        final NotebookViewport view;
        final float scroll;
        ScrollMove(NotebookViewport view,float scroll){this.view=view;this.scroll=scroll;}
    }
    static int target(NotebookViewport view,float dx,float dy,float threshold){
        if(view==null||view.singlePage==0||!Float.isFinite(dx)||!Float.isFinite(dy)||!Float.isFinite(threshold)||threshold<=0)return 0;
        if(view.zoom>view.fittedZoom(NotebookViewport.Fit.PAGE,view.singlePage)+.0001f||Math.abs(dx)<threshold||Math.abs(dx)<=Math.abs(dy)*1.25f)return 0;
        return view.adjacentPage(view.singlePage,dx<0?1:-1);
    }
    /** The upper-left quarter goes back; the rest advances, in any layout or orientation. */
    static int tapTarget(NotebookViewport view,float scroll,float x,float y){
        if(view==null||!Float.isFinite(x)||!Float.isFinite(y)||x<0||y<0||x>view.width||y>view.height)return 0;
        int current=view.navigationPage(scroll);
        int direction=x>=view.width/2f||y>=view.height/2f?1:-1;
        return view.adjacentPage(current,direction);
    }
    /** A continuous tap advances through the visible area, retaining the requested overlap. */
    static ScrollMove scrollTap(NotebookViewport view,float scroll,float x,float y,int overlap){
        if(view==null||view.singlePage!=0||!Float.isFinite(scroll)||!Float.isFinite(x)||!Float.isFinite(y)
                ||x<0||y<0||x>view.width||y>view.height)return null;
        if(overlap<0||overlap>NotebookReadingSettings.MAX_TAP_OVERLAP)throw new IllegalArgumentException("Invalid tap overlap");
        int direction=x>=view.width/2f||y>=view.height/2f?1:-1;
        float current=view.clampScroll(scroll),step=view.height*(100-overlap)/100f;
        float next=view.clampScroll(current+direction*step);
        if(Math.abs(next-current)>.001f)return new ScrollMove(view,next);
        if(!view.horizontal)return null;
        // Read the visible vertical slice first, then move along the horizontal
        // strip. The overlap also applies when the reader is zoomed in.
        float pan=view.clampPan(view.pan+direction*view.width*(100-overlap)/100f);
        if(Math.abs(pan-view.pan)<.001f)return null;
        NotebookViewport moved=view.withView(view.zoom,pan);
        return new ScrollMove(moved,direction>0?0:moved.maxScroll());
    }
    private NotebookPageTurn(){}
}
