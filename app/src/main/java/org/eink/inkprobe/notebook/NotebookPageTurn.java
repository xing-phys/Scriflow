package org.eink.inkprobe.notebook;

/** Page swipes only at a whole-page view; zoomed drags always remain panning gestures. */
final class NotebookPageTurn {
    static int target(NotebookViewport view,float dx,float dy,float threshold){
        if(view==null||view.singlePage==0||!Float.isFinite(dx)||!Float.isFinite(dy)||!Float.isFinite(threshold)||threshold<=0)return 0;
        if(view.zoom>view.fittedZoom(NotebookViewport.Fit.PAGE,view.singlePage)+.0001f||Math.abs(dx)<threshold||Math.abs(dx)<=Math.abs(dy)*1.25f)return 0;
        int target=view.singlePage+(dx<0?1:-1);return target<1||target>view.pageCount?0:target;
    }
    private NotebookPageTurn(){}
}
