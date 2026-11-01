package org.eink.inkprobe.notebook;

import java.util.*;

/** Clip a closed selection to visible paper before applying the existing whole-stroke lasso rule. */
final class NotebookRegionEraser {
    private NotebookRegionEraser(){}
    static List<NotebookPage.Point> clip(List<NotebookPage.Point> path,float left,float top,float right,float bottom){
        if(!NotebookPage.finite(left)||!NotebookPage.finite(top)||!NotebookPage.finite(right)||!NotebookPage.finite(bottom))
            throw new IllegalArgumentException("Invalid region bounds");
        List<NotebookPage.Point> polygon=NotebookLasso.closedOutline(path);
        if(polygon.size()<3||right<=left||bottom<=top)return Collections.emptyList();
        for(int edge=0;edge<4&&!polygon.isEmpty();edge++){
            float boundary=edge==0?left:edge==1?right:edge==2?top:bottom;
            List<NotebookPage.Point> clipped=new ArrayList<>();
            NotebookPage.Point a=polygon.get(polygon.size()-1);boolean previous=inside(a,edge,boundary);
            for(NotebookPage.Point b:polygon){
                boolean current=inside(b,edge,boundary);
                if(previous!=current)clipped.add(intersection(a,b,edge,boundary));
                if(current)clipped.add(b);
                a=b;previous=current;
            }
            polygon=clipped;
        }
        return polygon.size()<3?Collections.emptyList():NotebookLasso.closedOutline(polygon);
    }
    private static boolean inside(NotebookPage.Point p,int edge,float boundary){
        return edge==0?p.x>=boundary:edge==1?p.x<=boundary:edge==2?p.y>=boundary:p.y<=boundary;
    }
    private static NotebookPage.Point intersection(NotebookPage.Point a,NotebookPage.Point b,int edge,float boundary){
        double first=edge<2?a.x:a.y,last=edge<2?b.x:b.y;
        double t=Math.max(0,Math.min(1,(boundary-first)/(last-first)));
        return new NotebookPage.Point(edge<2?boundary:(float)(a.x+(b.x-a.x)*t),
            edge>=2?boundary:(float)(a.y+(b.y-a.y)*t),
            Math.round(a.time*(1-t)+b.time*t),(float)(a.pressure+(b.pressure-a.pressure)*t));
    }
}
