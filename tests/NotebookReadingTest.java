package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Source-coordinate invariants, paged panning, crop detection and real preference publication. */
public final class NotebookReadingTest {
    private static int checks;
    private interface Action{void run()throws Exception;}
    public static void main(String[] args)throws Exception{geometry();paging();settings();detection();contrast();words();System.out.println("NotebookReading: "+checks+" assertions passed (crop/ink geometry, single-page zoom, settings recovery, content detection and contrast).");}
    private static void geometry()throws Exception{
        UUID id=UUID.randomUUID();NotebookCropBox crop=new NotebookCropBox(.2f,.1f,.9f,.8f);
        NotebookViewport.Size size=new NotebookViewport.Size(1000,1200,id,1).cropped(crop);
        NotebookViewport view=new NotebookViewport(Collections.singletonList(size),700,700,12);
        near(view.scale(1),1,"fit width uses the visible content width");near(view.pageLeft(1),-200,"logical origin retains hidden left margin");near(view.pageTop(1,0),-120,"logical origin retains hidden top margin");
        check(view.pageAt(5,5,0)==1&&view.crop(1).equals(crop),"crop hit testing uses the visible region");
        NotebookPage.Point a=new NotebookPage.Point(50,60,1,1),b=new NotebookPage.Point(100,120,2,1);
        NotebookPage.Stroke screen=new NotebookPage.Stroke(1,3,0xff000000,Arrays.asList(a,b));
        NotebookPage.Stroke stored=view.splitStroke(screen,0).get(1).get(0);
        near(stored.points.get(0).x,250,"new writing maps back into original PDF x coordinates");near(stored.points.get(0).y,180,"new writing maps back into original PDF y coordinates");
        NotebookPage page=NotebookPage.empty().withSize(1000,1200).withStroke(stored);
        NotebookPage shown=view.compose(Collections.singletonMap(1,page),0);
        near(shown.strokes.get(0).points.get(0).x,50,"saved writing replays at the same cropped x");near(shown.strokes.get(0).points.get(0).y,60,"saved writing replays at the same cropped y");
        NotebookFigure picture=new NotebookFigure(UUID.randomUUID(),0,0,1000,800,37,NotebookFigureFixture.png(true));
        NotebookFigure fitted=view.fitFigure(1,picture);
        check(fitted.left()>=199.99f&&fitted.right()<=900.01f&&fitted.top()>=119.99f&&fitted.bottom()<=960.01f,"rotated figures fit inside the displayed crop without altering their source pixels");
        NotebookLasso.Bounds bounds=new NotebookLasso.Bounds(Collections.singletonList(stored));
        NotebookSelectionMove.Destination drop=NotebookSelectionMove.place(view,1,bounds,0,0,-1000,-1000,0);
        check(drop.fits&&drop.centerX>200&&drop.centerY>120,"lasso drops stay reachable inside cropped margins");
        NotebookPage.Stroke replay=shown.strokes.get(0);
        check(replay.clip.left==0&&replay.clip.top==0&&replay.clip.right==700&&replay.clip.bottom==700,"native replay clip is the visible crop while saved points retain original coordinates");
        Random random=new Random(73);
        for(int n=0;n<500;n++){
            float zoom=.5f+random.nextFloat()*6;NotebookViewport z=view.withView(zoom,random.nextFloat()*700);
            float x=200+random.nextFloat()*700,y=120+random.nextFloat()*840;
            float scroll=z.scrollForPoint(1,y,350),pan=z.panForPoint(1,x,350);z=z.withView(zoom,pan);
            float sx=z.pageLeft(1)+x*z.scale(1),sy=z.pageTop(1,scroll)+y*z.scale(1);
            near((sx-z.pageLeft(1))/z.scale(1),x,"crop inverse x round trip");near((sy-z.pageTop(1,scroll))/z.scale(1),y,"crop inverse y round trip");
        }
        reject(()->new NotebookCropBox(0,0,0,1));reject(()->new NotebookCropBox(Float.NaN,0,1,1));
    }
    private static void paging(){
        List<NotebookViewport.Size> sizes=Arrays.asList(new NotebookViewport.Size(600,900),new NotebookViewport.Size(900,600),new NotebookViewport.Size(300,1200));
        NotebookViewport view=new NotebookViewport(sizes,600,700,12).withSinglePage(2);
        view=view.withView(view.fittedZoom(NotebookViewport.Fit.PAGE,2),0);float scroll=view.pageScroll(2);
        check(view.visiblePages(scroll).equals(Collections.singletonList(2)),"single mode never exposes adjacent pages");
        near(view.viewTop(2,scroll),150,"landscape page is vertically centered");check(view.pageAt(20,10,scroll)==0,"letterbox is not writable paper");
        check(NotebookPageTurn.target(view,-80,5,48)==3&&NotebookPageTurn.target(view,80,5,48)==1,"whole-page horizontal swipes turn exactly one page");
        NotebookViewport zoomed=view.withView(3,200);float mid=zoomed.scrollForPoint(2,300,350);
        check(zoomed.visiblePages(mid).equals(Collections.singletonList(2)),"zooming remains within the selected page");
        check(NotebookPageTurn.target(zoomed,-300,0,48)==0,"zoomed drags cannot accidentally turn a page");
        check(zoomed.clampScroll(-1)==zoomed.pageScroll(2)&&zoomed.clampScroll(Float.MAX_VALUE)==zoomed.maxScroll(),"zoomed pan is bounded by the single page");
        NotebookViewport next=view.forPage(3);check(next.centeredPage(next.pageScroll(3))==3,"explicit navigation selects the destination before clamping");
        check(next.withSinglePage(0).pageCount==3,"continuous mode restores the original full page stack");
    }
    private static void settings()throws Exception{
        File root=Files.createTempDirectory("reading-prefs-").toFile();
        try{
            UUID id=UUID.randomUUID();NotebookCropBox box=new NotebookCropBox(.1f,.1f,.9f,.9f);
            NotebookReadingSettings state=NotebookReadingSettings.DEFAULT.crop(NotebookReadingSettings.Crop.SEMI_AUTO).single(true).contrast(1.5f).area(NotebookReadingSettings.Scope.ODD,id,box);
            NotebookReadingStore store=new NotebookReadingStore(root,1);check(store.read().equals(NotebookReadingSettings.DEFAULT),"new notes retain uncropped continuous identity defaults");store.write(state);
            check(new NotebookReadingStore(root,1).read().equals(state),"crop scopes, page mode and contrast persist together");
            check(state.manual(1,id).equals(box)&&state.manual(2,id).equals(NotebookCropBox.FULL),"odd/even areas remain independent");
            NotebookReadingSettings changed=state.area(NotebookReadingSettings.Scope.PAGE,id,new NotebookCropBox(.2f,.2f,.8f,.8f));store.write(changed);
            Files.write(new File(root,"reading/1.view").toPath(),new byte[]{1});check(new NotebookReadingStore(root,1).read().equals(state),"corrupt preferences recover the exact predecessor without changing notes");
            List<NotebookViewport.Size> pages=Arrays.asList(new NotebookViewport.Size(100,200,id,1),new NotebookViewport.Size(100,200,UUID.randomUUID(),0));
            List<NotebookViewport.Size> layout=state.layout(pages,(p,area)->new NotebookCropBox(.15f,.15f,.85f,.85f));
            check(layout.get(0).crop.left==.15f&&layout.get(1).crop.equals(NotebookCropBox.FULL),"semi-auto detection applies to PDF pages only");
            check(changed.crop(NotebookReadingSettings.Crop.MANUAL).layout(pages,(p,area)->{throw new AssertionError("manual crop must not rasterize");}).get(0).crop.left==.2f,"manual crop uses the exact chosen box");
            reject(()->state.contrast(Float.NaN));reject(()->state.pages.clear());
        }finally{NotebookBackup.deleteTree(root);}
    }
    private static void detection(){
        int width=300,height=400;int[] pixels=new int[width*height];Arrays.fill(pixels,0xffffffff);
        check(NotebookPdfCrop.detect(pixels,width,height,NotebookCropBox.FULL).equals(NotebookCropBox.FULL),"blank pages retain their complete area");
        for(int y=80;y<320;y++)for(int x=60;x<240;x++)if(y%16<3)pixels[y*width+x]=0xff222222;
        pixels[5]=0xff000000;NotebookCropBox found=NotebookPdfCrop.detect(pixels,width,height,NotebookCropBox.FULL);
        check(found.left>.18f&&found.right<.82f&&found.top>.18f&&found.bottom<.82f,"automatic crop finds text and ignores isolated border speckles");
        for(int y=10;y<30;y++)for(int x=10;x<100;x++)pixels[y*width+x]=0xff000000;
        NotebookCropBox roi=new NotebookCropBox(.15f,.15f,.85f,.85f),semi=NotebookPdfCrop.detect(pixels,width,height,roi);
        check(semi.left>=roi.left&&semi.top>.18f&&semi.right<=roi.right,"semi-auto detects inside the manual area and excludes a dirty header");
        Arrays.fill(pixels,0xffeeeeee);for(int y=80;y<320;y++)for(int x=60;x<240;x++)if(y%16<3)pixels[y*width+x]=0xff444444;
        check(NotebookPdfCrop.detect(pixels,width,height,NotebookCropBox.FULL).left>.18f,"uniform gray scan backgrounds are not mistaken for content");
    }
    private static void contrast(){
        int[] identity=NotebookPdfContrast.table(1),dark=NotebookPdfContrast.table(2),light=NotebookPdfContrast.table(.5f);
        for(int n=0;n<256;n++){check(identity[n]==n,"identity contrast is exact");check(dark[n]<=n&&light[n]>=n,"contrast follows the intended darkness direction");if(n>0)check(dark[n]>=dark[n-1],"gamma preserves tone ordering");}
        int[] pixels={0xff000000,0xffffffff,0x80808080};NotebookPdfContrast.apply(pixels,dark);
        check(pixels[0]==0xff000000&&pixels[1]==0xffffffff&&(pixels[2]>>>24)==128,"contrast retains black, white and alpha");
    }
    private static void words(){
        List<NotebookPdfText.Word> result=new ArrayList<>();
        NotebookPdfText.append(result,Arrays.asList(new NotebookPdfText.Glyph("ﬁnite",.1f,.2f,.3f,.25f),
            new NotebookPdfText.Glyph(" ",.3f,.2f,.35f,.25f),new NotebookPdfText.Glyph("ψ",.35f,.2f,.4f,.25f),new NotebookPdfText.Glyph("!",.4f,.2f,.42f,.25f)));
        NotebookPdfText text=new NotebookPdfText(result);
        check(text.words.size()==2&&text.words.get(0).text.equals("finite"),"compatibility ligatures normalize to searchable words");
        check(text.at(.36f,.22f).text.equals("ψ"),"Unicode scientific words retain searchable text and correct bounds");
        check(text.at(.41f,.22f)==null&&text.at(.7f,.8f)==null,"punctuation and blank space do not select a remote word");
    }
    private static void near(float actual,float expected,String message){check(Math.abs(actual-expected)<.002f,message);}
    private static void check(boolean yes,String message){checks++;if(!yes)throw new AssertionError(message);}
    private static void reject(Action action)throws Exception{try{action.run();}catch(IOException|IllegalArgumentException|UnsupportedOperationException expected){checks++;return;}throw new AssertionError("Expected rejected input");}
}
