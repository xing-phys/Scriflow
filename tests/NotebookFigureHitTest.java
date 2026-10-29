package org.eink.inkprobe.notebook;

import java.util.*;

public final class NotebookFigureHitTest {
    private static int checks;
    public static void main(String[] args)throws Exception {
        byte[] png=NotebookFigureFixture.png(true);
        NotebookFigure base=new NotebookFigure(UUID.randomUUID(),20,30,100,120,png);
        NotebookFigure top=new NotebookFigure(UUID.randomUUID(),40,50,30,40,png);
        NotebookFigure second=new NotebookFigure(UUID.randomUUID(),40,60,120,180,png);
        NotebookPage a=NotebookPage.empty().withSize(200,300).withFigures(Arrays.asList(base,top));
        NotebookPage b=NotebookPage.empty().withSize(400,500).withFigures(Collections.singletonList(second));
        Map<Integer,NotebookPage> pages=new LinkedHashMap<>();pages.put(1,a);pages.put(2,b);
        List<NotebookViewport.Size> sizes=Arrays.asList(new NotebookViewport.Size(200,300),new NotebookViewport.Size(400,500));
        Random random=new Random(20261029);
        for(int i=0;i<500;i++) {
            int number=i%2+1;float logicalX=number==1?55:100,logicalY=number==1?60:100;
            NotebookViewport v=new NotebookViewport(sizes,300,240,24,.15f+random.nextFloat()*7,0);
            v=v.withView(v.zoom,v.panForPoint(number,logicalX,150));float scroll=v.scrollForPoint(number,logicalY,120);
            float x=v.pageLeft(number)+logicalX*v.scale(number),y=v.pageTop(number,scroll)+logicalY*v.scale(number);
            NotebookFigureHit.Hit hit=NotebookFigureHit.find(v,pages,scroll,x,y);
            check(hit!=null&&hit.page==number&&hit.pageId.equals(pages.get(number).id),"hit follows page, zoom, pan and scroll");
            check(hit.figureId.equals(number==1?top.id:second.id),"topmost drawn figure wins overlapping bounds");
        }
        NotebookViewport v=new NotebookViewport(sizes,300,240,24);
        check(NotebookFigureHit.find(v,pages,342,150,120)==null,"page gap cannot select an image");
        check(NotebookFigureHit.find(v,pages,0,-1,50)==null,"outside viewport cannot select an image");
        check(NotebookFigureHit.find(v,pages,0,Float.NaN,50)==null,"invalid coordinates cannot select an image");
        check(NotebookFigureHit.find(v,Collections.emptyMap(),0,50,50)==null,"unloaded page is ignored");
        check(NotebookFigureHit.find(null,pages,0,50,50)==null,"unconfigured view is ignored");
        System.out.println("NotebookFigureHit: "+checks+" assertions passed (transforms, page identity, overlap and gaps).");
    }
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
}
