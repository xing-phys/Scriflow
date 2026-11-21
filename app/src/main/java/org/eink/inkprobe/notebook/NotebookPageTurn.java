package org.eink.inkprobe.notebook;

/** Page swipes only at a whole-page view; zoomed drags always remain panning gestures. */
final class NotebookPageTurn {
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
    private NotebookPageTurn(){}
}
