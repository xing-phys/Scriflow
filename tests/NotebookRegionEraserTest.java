package org.eink.inkprobe.notebook;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

/** Real geometry, durable deletion and undo for the existing whole-stroke lasso rule. */
public final class NotebookRegionEraserTest {
    private static int checks;
    public static void main(String[] args)throws Exception{
        clipping();viewport();durability();
        System.out.println("NotebookRegionEraser: "+checks+" assertions passed (visible-region geometry, whole-stroke selection, figures, files and undo).");
    }
    private static NotebookPage.Point p(float x,float y){return new NotebookPage.Point(x,y,100,.5f);}
    private static List<NotebookPage.Point> rectangle(float l,float t,float r,float b){return Arrays.asList(p(l,t),p(r,t),p(r,b),p(l,b));}
    private static NotebookPage.Stroke stroke(float... xy){
        List<NotebookPage.Point> points=new ArrayList<>();for(int i=0;i<xy.length;i+=2)points.add(new NotebookPage.Point(xy[i],xy[i+1],100+i,.2f+i*.1f));
        return new NotebookPage.Stroke(2,3.2f,0xff000000,points,1.5f,4.8f);
    }
    private static void clipping(){
        List<NotebookPage.Point> box=NotebookRegionEraser.clip(rectangle(-20,-30,120,130),0,0,100,100);
        check(NotebookLasso.contains(box,50,50)&&!NotebookLasso.contains(box,-1,50),"a loop enclosing a whole page clips to the page, even when all original vertices are outside");
        for(NotebookPage.Point q:box)check(q.x>=0&&q.x<=100&&q.y>=0&&q.y<=100,"all clipped vertices are visible");
        check(NotebookRegionEraser.clip(rectangle(200,200,300,300),0,0,100,100).isEmpty(),"disjoint loop is a no-op");
        check(NotebookRegionEraser.clip(Collections.emptyList(),0,0,100,100).isEmpty(),"cancelled loop is empty");
        List<NotebookPage.Point> concave=Arrays.asList(p(10,10),p(90,10),p(90,30),p(30,30),p(30,90),p(10,90));
        List<NotebookPage.Point> clipped=NotebookRegionEraser.clip(concave,20,20,80,80);
        check(NotebookLasso.contains(clipped,25,60)&&NotebookLasso.contains(clipped,60,25)&&!NotebookLasso.contains(clipped,60,60),"concave regions keep their excluded notch after clipping");
        check(clipped.get(0).x==clipped.get(clipped.size()-1).x&&clipped.get(0).y==clipped.get(clipped.size()-1).y,"gesture closes automatically");
        NotebookPage page=NotebookPage.empty().withSize(100,100).withStrokes(Arrays.asList(stroke(50,50),stroke(0,60,100,60),stroke(90,90)));
        check(NotebookLasso.select(page,rectangle(40,40,70,70)).strokes.size()==2,"inside ink and sparse crossing segments select whole strokes");
        check(NotebookLasso.select(page,Arrays.asList(p(10,10),p(90,90))).isEmpty(),"a line without an enclosed area cannot erase");
        Random random=new Random(917);
        for(int i=0;i<160;i++){
            float l=random.nextFloat()*100-25,t=random.nextFloat()*100-25,r=l+5+random.nextFloat()*60,b=t+5+random.nextFloat()*60;
            List<NotebookPage.Point> polygon=NotebookRegionEraser.clip(rectangle(l,t,r,b),0,0,100,100);
            for(int x=2;x<100;x+=7)for(int y=3;y<100;y+=11)
                check(NotebookLasso.contains(polygon,x,y)==(x>=l&&x<=r&&y>=t&&y<=b),"clipped rectangle membership agrees with independent rectangle intersection");
        }
    }
    private static void viewport(){
        NotebookViewport view=new NotebookViewport(2,100,100,100,180,24);
        Map<Integer,List<NotebookPage.Point>> split=view.splitRegion(rectangle(20,80,80,150),0);
        check(split.size()==2,"one loop can erase visible regions of both pages");
        NotebookPage first=NotebookPage.empty().withSize(100,100).withStrokes(Arrays.asList(stroke(50,90),stroke(50,70)));
        NotebookPage second=NotebookPage.empty().withSize(100,100).withStrokes(Arrays.asList(stroke(50,10),stroke(50,40)));
        check(NotebookLasso.select(first,split.get(1)).strokes.size()==1&&NotebookLasso.select(second,split.get(2)).strokes.size()==1,"page offsets and gutter are not joined into false selections");
        check(view.splitRegion(rectangle(20,105,80,120),0).isEmpty(),"a region wholly in the gutter touches no page");
        NotebookViewport zoomed=new NotebookViewport(1,100,100,200,200,24).withView(2,30);
        float scale=zoomed.scale(1),left=zoomed.pageLeft(1),top=zoomed.pageTop(1,20);
        Map<Integer,List<NotebookPage.Point>> mapped=zoomed.splitRegion(rectangle(left+15*scale,top+15*scale,left+30*scale,top+30*scale),20);
        check(NotebookLasso.contains(mapped.get(1),20,20)&&!NotebookLasso.contains(mapped.get(1),40,40),"zoom, pan and scroll map the loop back to stored coordinates");
        List<NotebookViewport.Size> sizes=Collections.singletonList(new NotebookViewport.Size(100,100).cropped(new NotebookCropBox(.2f,.2f,.8f,.8f)));
        NotebookViewport cropped=new NotebookViewport(sizes,200,240,12,1,0);
        Map<Integer,List<NotebookPage.Point>> crop=cropped.splitRegion(rectangle(-100,-100,400,400),0);
        NotebookPage hidden=NotebookPage.empty().withSize(100,100).withStrokes(Arrays.asList(stroke(50,50),stroke(10,10),stroke(90,90)));
        check(NotebookLasso.select(hidden,crop.get(1)).strokes.size()==1,"hidden crop-margin strokes are protected");
        check(view.withSinglePage(2).splitRegion(rectangle(-10,-10,200,300),0).keySet().equals(Collections.singleton(2)),"single-page mode never targets another page");
    }
    private static void durability()throws Exception{
        File root=Files.createTempDirectory("region-erase-").toFile();
        try{
            NotebookLibrary book=new NotebookLibrary(root);book.load();book.initializeSize(100,100);
            NotebookPage.Stroke inside=stroke(30,30,40,40),crossing=stroke(0,45,100,45),outside=stroke(90,90);
            book.append(inside);book.append(crossing);book.append(outside);
            NotebookFigure figure=new NotebookFigure(UUID.randomUUID(),20,20,40,30,NotebookFigureFixture.png(true));
            book.setFigures(1,Collections.singletonList(figure));
            check(book.eraseRegionOnPage(1,rectangle(20,20,60,60)),"a completed loop commits a region erase");
            check(book.current().strokes.equals(Collections.singletonList(outside))&&book.current().figures.equals(Collections.singletonList(figure)),"whole selected strokes erase while images and other ink remain intact");
            check(book.undoOnPage(1)&&book.current().strokes.equals(Arrays.asList(inside,crossing,outside)),"one undo restores all region-erased strokes, IDs and pressure data");
            check(book.redoOnPage(1)&&book.current().strokes.equals(Collections.singletonList(outside)),"one redo repeats the region erase");
            check(!book.eraseRegionOnPage(1,rectangle(1,1,2,2))&&book.undoOnPage(1)&&book.current().strokes.size()==3,"a no-hit loop creates no extra undo step");
            check(book.redoOnPage(1),"redo remains available after a no-op region");
            NotebookLibrary reopened=new NotebookLibrary(root);reopened.load();
            check(reopened.current().strokes.size()==1&&reopened.current().strokes.get(0).id.equals(outside.id)&&reopened.current().figures.size()==1,"erased content and figures survive reopening through the existing durable format");
        }finally{delete(root);}
    }
    private static void delete(File f)throws Exception{File[] children=f.listFiles();if(children!=null)for(File child:children)delete(child);Files.deleteIfExists(f.toPath());}
    private static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
}
