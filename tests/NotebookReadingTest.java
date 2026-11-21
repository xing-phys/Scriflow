package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Source-coordinate invariants, paged panning, crop detection and real preference publication. */
public final class NotebookReadingTest {
    private static int checks;
    private interface Action{void run()throws Exception;}
    public static void main(String[] args)throws Exception{geometry();paging();spread();coverSpreads();continuousLayouts();screenfulTaps();activeTap();settings();detection();contrast();words();System.out.println("NotebookReading: "+checks+" assertions passed (crop/ink geometry, page views, settings recovery, content detection and contrast).");}
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
    private static void spread(){
        List<NotebookViewport.Size> sizes=Arrays.asList(new NotebookViewport.Size(600,900),new NotebookViewport.Size(900,600),new NotebookViewport.Size(300,1200));
        NotebookViewport view=new NotebookViewport(sizes,1000,700,20).withSpreadPage(2);
        view=view.withView(view.fittedZoom(NotebookViewport.Fit.PAGE,1),0);float scroll=view.pageScroll(1);
        check(view.singlePage==1&&view.visiblePages(scroll).equals(Arrays.asList(1,2)),"spread shows the destination and its facing page");
        check(view.viewRight(1)<view.viewLeft(2),"facing pages have a nonwritable gutter");
        float leftX=(view.viewLeft(1)+view.viewRight(1))/2,rightX=(view.viewLeft(2)+view.viewRight(2))/2;
        check(view.pageAt(leftX,350,scroll)==1&&view.pageAt(rightX,350,scroll)==2&&view.pageAt(500,350,scroll)==0,"spread hit testing distinguishes both pages and the gutter");
        NotebookPage.Stroke line=new NotebookPage.Stroke(1,3,0xff000000,Arrays.asList(new NotebookPage.Point(leftX,350,1,1),new NotebookPage.Point(rightX,350,2,1)));
        Map<Integer,List<NotebookPage.Stroke>> pieces=view.splitStroke(line,scroll);
        check(pieces.size()==2&&pieces.containsKey(1)&&pieces.containsKey(2),"cross-gutter writing is saved separately to both source pages");
        NotebookPage first=NotebookPage.empty().withSize(600,900).withStroke(pieces.get(1).get(0));
        NotebookPage second=NotebookPage.empty().withSize(900,600).withStroke(pieces.get(2).get(0));
        check(view.compose(new java.util.HashMap<Integer,NotebookPage>(){{put(1,first);put(2,second);}},scroll).strokes.size()==2,"both pages replay their own ink");
        check(NotebookPageTurn.target(view,-100,0,48)==3&&NotebookPageTurn.target(view,100,0,48)==0,"swipes advance by a spread and respect the first page");
        NotebookViewport last=view.forPage(3);last=last.withView(last.fittedZoom(NotebookViewport.Fit.PAGE,3),0);check(last.visiblePages(last.pageScroll(3)).equals(Collections.singletonList(3)),"odd last page remains reachable on its own");
        check(NotebookPageTurn.target(last,100,0,48)==1&&NotebookPageTurn.target(last,-100,0,48)==0,"last spread navigates back without passing the end");
        NotebookViewport zoomed=view.withView(2,0);check(NotebookPageTurn.target(zoomed,-100,0,48)==0,"zoomed spread drags remain panning gestures");
        NotebookViewport tallRight=new NotebookViewport(Arrays.asList(new NotebookViewport.Size(600,300),new NotebookViewport.Size(300,1800)),1000,700,20).withSpreadPage(1).withView(2,0);
        float bottom=tallRight.maxScroll();double fraction=tallRight.anchorFraction(bottom);
        check(fraction>=0&&fraction<=1,"spread persistence uses the taller page when saving a zoomed scroll offset");
        near(tallRight.scrollForAnchor(1,fraction),bottom,"zoomed spread scroll offsets restore without losing the taller page");
        float pan=zoomed.panForPoint(2,450,550);zoomed=zoomed.withView(2,pan);
        near(zoomed.pageLeft(2)+450*zoomed.scale(2),550,"right-page pinch anchor accounts for its horizontal slot");
    }
    private static void continuousLayouts(){
        List<NotebookViewport.Size> sizes=Arrays.asList(new NotebookViewport.Size(600,900),new NotebookViewport.Size(900,600),new NotebookViewport.Size(300,1200),new NotebookViewport.Size(600,900),new NotebookViewport.Size(600,900));
        for(int[] screen:new int[][]{{700,1000},{1000,700}})for(boolean doublePage:new boolean[]{false,true})for(boolean horizontal:new boolean[]{false,true}){
            NotebookViewport view=new NotebookViewport(sizes,screen[0],screen[1],20).withLayout(false,doublePage,horizontal,1);
            int step=doublePage?2:1;
            check(view.singlePage==0&&view.spread==doublePage&&view.horizontal==horizontal,"continuous preferences are independent");
            check(NotebookPageTurn.tapTarget(view,0,screen[0]*.75f,screen[1]*.25f)==1+step,"right tap advances in continuous layout");
            check(NotebookPageTurn.tapTarget(view,0,screen[0]*.25f,screen[1]*.75f)==1+step,"bottom tap advances in continuous layout");
            check(NotebookPageTurn.tapTarget(view,0,screen[0]*.25f,screen[1]*.25f)==0,"top left tap respects the beginning");
            view=view.withView(1,view.panForPage(1+step));float scroll=view.navigationScroll(1+step);
            check(view.navigationPage(scroll)==1+step,"continuous page navigation reaches the target: "+Arrays.toString(screen)+" double="+doublePage+" horizontal="+horizontal+" got="+view.navigationPage(scroll)+" scroll="+scroll+" pan="+view.pan);
            check(NotebookPageTurn.tapTarget(view,scroll,screen[0]*.25f,screen[1]*.25f)==1,"top left tap returns to the previous page or spread");
            float x=(view.viewLeft(1+step)+view.viewRight(1+step))/2;
            float y=(Math.max(0,view.viewTop(1+step,scroll))+Math.min(screen[1],view.viewBottom(1+step,scroll)))/2;
            check(view.pageAt(x,y,scroll)==1+step,"continuous page hit testing finds the source page");
            NotebookPage.Stroke line=new NotebookPage.Stroke(1,3,0xff000000,Arrays.asList(new NotebookPage.Point(x,y,1,1),new NotebookPage.Point(x+2,y+2,2,1)));
            check(view.splitStroke(line,scroll).containsKey(1+step),"continuous page writing returns to the correct page");
            if(horizontal){
                view=view.withView(1,view.panForAnchor(1+step,.2));int anchor=view.anchorPage(0);double fraction=view.anchorFraction(0);
                near(view.panForAnchor(anchor,fraction),view.pan,"horizontal position persists as an anchor and fraction");
            }else{
                float position=view.clampScroll(scroll+20);int anchor=view.anchorPage(position);double fraction=view.anchorFraction(position);
                near(view.scrollForAnchor(anchor,fraction),position,"vertical position persists as an anchor and fraction");
            }
            if(doublePage){
                view=view.withView(1,view.panForPage(1));scroll=view.pageScroll(1);
                float right=(view.viewLeft(2)+view.viewRight(2))/2;
                float rightY=(Math.max(0,view.viewTop(2,scroll))+Math.min(screen[1],view.viewBottom(2,scroll)))/2;
                check(view.pageAt(right,rightY,scroll)==2,"continuous facing page has its own hit target");
            }
        }
        NotebookViewport shortLast=new NotebookViewport(3,1404,1688,1396,1864,24).withLayout(false,true,false,1);
        float last=shortLast.navigationScroll(3);
        check(shortLast.navigationPage(last)==3&&shortLast.anchorFraction(last)==0,"short final spread reaches its exact anchor despite float rounding");
        check(NotebookPageTurn.tapTarget(shortLast,last,100,200)==1,"a tap can return from a short final spread");
        for(boolean doublePage:new boolean[]{false,true})for(int[] screen:new int[][]{{700,1000},{1000,700}}){
            NotebookViewport view=new NotebookViewport(sizes,screen[0],screen[1],20).withLayout(true,doublePage,false,3);
            int first=doublePage?3:3,step=doublePage?2:1;
            check(NotebookPageTurn.tapTarget(view,view.pageScroll(first),screen[0]*.25f,screen[1]*.25f)==first-step,"paged top-left tap goes back in either orientation");
            check(NotebookPageTurn.tapTarget(view,view.pageScroll(first),screen[0]*.75f,screen[1]*.25f)==first+step,"paged right tap advances in either orientation");
            view=view.withView(3,0);
            check(NotebookPageTurn.tapTarget(view,view.pageScroll(first),screen[0]*.25f,screen[1]*.75f)==first+step,"a tap still turns a zoomed page");
        }
    }
    private static void coverSpreads(){
        for(int count=1;count<=8;count++)for(int[] screen:new int[][]{{700,1000},{1000,700}})
            for(boolean paged:new boolean[]{false,true})for(boolean horizontal:new boolean[]{false,true})for(boolean cover:new boolean[]{false,true}){
                NotebookViewport base=new NotebookViewport(count,600,900,screen[0],screen[1],20);
                for(int page=1;page<=count;page++){
                    int first=cover?(page==1?1:page-page%2):page-(page+1)%2;
                    int last=Math.min(count,first+(cover&&first==1?0:1));
                    NotebookViewport view=base.withLayout(paged,true,horizontal,page,cover);
                    if(paged)view=view.withView(view.fittedZoom(NotebookViewport.Fit.PAGE,page),0);
                    else view=view.withView(1,view.panForPage(page));
                    float scroll=view.pageScroll(page);
                    check(view.navigationPage(scroll)==first,"cover alignment preserves the selected group");
                    check(view.visiblePages(scroll).contains(page),"every page stays reachable with either spread alignment");
                    if(paged)check(view.visiblePages(scroll).equals(first==last?Collections.singletonList(first):Arrays.asList(first,last)),"paged spread contains exactly its own pages");
                    int expectedPrevious=first==1?0:cover&&first==2?1:first-2;
                    int expectedNext=last==count?0:last+1;
                    check(view.adjacentPage(page,-1)==expectedPrevious&&view.adjacentPage(page,1)==expectedNext,"spread navigation includes cover and short final page");
                    boolean right=cover?(page%2==1):(page%2==0);
                    check(right?view.viewLeft(page)>view.viewLeft(first)+(page==first?0:1)||page==first&&cover&&page==1:view.viewLeft(page)<=view.viewLeft(first),"page remains on its intended side of the spread");
                    check(NotebookPageTurn.tapTarget(view,scroll,screen[0]*.75f,screen[1]*.25f)==expectedNext,"forward taps respect cover alignment");
                    check(NotebookPageTurn.tapTarget(view,scroll,screen[0]*.25f,screen[1]*.25f)==expectedPrevious,"back taps respect cover alignment");
                }
            }
        NotebookViewport cover=new NotebookViewport(4,600,900,1000,700,20).withLayout(true,true,false,1,true);
        cover=cover.withView(cover.fittedZoom(NotebookViewport.Fit.PAGE,1),0);
        check(cover.visiblePages(0).equals(Collections.singletonList(1))&&cover.viewLeft(1)>500,"a cover is alone in the right-hand slot");
        NotebookViewport pair=cover.forPage(3);check(pair.singlePage==2&&pair.visiblePages(pair.pageScroll(2)).equals(Arrays.asList(2,3)),"pages 2 and 3 share the next spread");
        check(NotebookPageTurn.target(cover,-100,0,48)==2&&NotebookPageTurn.target(pair,100,0,48)==1,"swipes cross the cover boundary correctly");
        check(cover.pageAt(100,350,cover.pageScroll(1))==0&&cover.pageAt(750,350,cover.pageScroll(1))==1,"the empty cover slot never accepts page input");
        float scroll=pair.pageScroll(2);check(pair.pageAt(250,350,scroll)==2&&pair.pageAt(750,350,scroll)==3,"each side of a shifted spread targets its own page");
        NotebookPage.Stroke stroke=new NotebookPage.Stroke(1,3,0xff000000,Arrays.asList(new NotebookPage.Point(750,350,1,1),new NotebookPage.Point(760,360,2,1)));
        check(pair.splitStroke(stroke,scroll).containsKey(3),"ink remains attached to the correct page in a shifted spread");
        check(pair.withView(2,0).cover&&pair.forPage(4).cover,"zooming and changing pages retain spread alignment");
    }
    private static void screenfulTaps(){
        for(int[] screen:new int[][]{{600,900},{900,600}})for(boolean spread:new boolean[]{false,true}){
            NotebookViewport view=new NotebookViewport(5,600,2400,screen[0],screen[1],20).withLayout(false,spread,false,1,true);
            for(int overlap:new int[]{0,10,25,80}){
                NotebookPageTurn.ScrollMove next=NotebookPageTurn.scrollTap(view,0,screen[0]*.8f,screen[1]*.4f,overlap);
                check(next!=null&&next.view==view,"continuous tap retains the same layout and zoom");
                near(next.scroll,screen[1]*(100-overlap)/100f,"continuous tap moves one screen minus its overlap");
                NotebookPageTurn.ScrollMove back=NotebookPageTurn.scrollTap(view,next.scroll,screen[0]*.2f,screen[1]*.2f,overlap);
                check(back!=null&&back.scroll==0,"a backward tap returns to the same continuous position");
            }
            check(NotebookPageTurn.scrollTap(view,0,screen[0]*.2f,screen[1]*.2f,10)==null,"backward tap at the start does nothing");
            check(NotebookPageTurn.scrollTap(view,view.maxScroll(),screen[0]*.8f,screen[1]*.4f,10)==null,"forward tap at the end does nothing");
        }
        NotebookViewport tall=new NotebookViewport(3,600,1800,600,900,20).withLayout(false,false,false,1);
        NotebookPageTurn.ScrollMove lower=NotebookPageTurn.scrollTap(tall,0,500,400,10);
        near(lower.scroll,810,"the first tap reaches the lower part of a tall page instead of jumping to the next page");
        check(lower.view.navigationPage(lower.scroll)==1,"the first screenful remains on the current tall page");
        NotebookViewport horizontal=tall.withLayout(false,false,true,1);
        NotebookPageTurn.ScrollMove first=NotebookPageTurn.scrollTap(horizontal,0,500,400,10);
        near(first.scroll,810,"horizontal continuous reading shows the lower part before moving sideways");
        NotebookPageTurn.ScrollMove bottom=NotebookPageTurn.scrollTap(first.view,first.scroll,500,400,10);
        near(bottom.scroll,horizontal.maxScroll(),"the last vertical step stops at the page bottom");
        NotebookPageTurn.ScrollMove side=NotebookPageTurn.scrollTap(bottom.view,bottom.scroll,500,400,10);
        near(side.view.pan,540,"horizontal movement retains the configured screen overlap");near(side.scroll,0,"moving to the next horizontal slice starts at its top");
        NotebookPageTurn.ScrollMove previous=NotebookPageTurn.scrollTap(side.view,side.scroll,50,100,10);
        near(previous.view.pan,0,"backward horizontal tap returns to the previous slice");near(previous.scroll,previous.view.maxScroll(),"backward horizontal movement resumes at the previous bottom");
        check(NotebookPageTurn.scrollTap(tall,0,-1,100,10)==null,"taps outside the reader do not navigate");
        check(NotebookPageTurn.scrollTap(tall.withLayout(true,false,false,1),0,500,400,10)==null,"discrete page turns do not use continuous screenful movement");
    }
    private static byte[] legacyView(byte[] frame,int version,int flags)throws IOException{
        int length=java.nio.ByteBuffer.wrap(frame,8,4).getInt();byte[] payload=new byte[length-1];
        System.arraycopy(frame,16,payload,0,2);System.arraycopy(frame,19,payload,2,length-3);payload[1]=(byte)flags;
        java.util.zip.CRC32 crc=new java.util.zip.CRC32();crc.update(payload);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeInt(0x4e525631);out.writeInt(version);out.writeInt(payload.length);out.writeInt(~payload.length);out.write(payload);out.writeInt((int)crc.getValue());out.flush();return bytes.toByteArray();
    }
    private static void activeTap()throws Exception{
        String source=new String(Files.readAllBytes(Paths.get("app/src/main/java/org/eink/inkprobe/notebook/NotebookActivity.java")),java.nio.charset.StandardCharsets.UTF_8);
        String fixture=String.join("\n",
            "package org.eink.inkprobe.notebook;import java.io.*;public class ActiveContinuousTapFixture {",
            "static class Session{NotebookReadingSettings settings=NotebookReadingSettings.DEFAULT.tapOverlap(25);int notebookId(){return 1;}NotebookReadingSettings readingSettings(int n){return settings;}}static class Tap{int cancelled;void cancel(){cancelled++;}}",
            "final Session session=new Session();final Tap linkTap=new Tap();NotebookViewport viewport=new NotebookViewport(3,600,1800,600,900,20).withLayout(false,false,false,1);float scrollY;int activePage=1,viewportRevision,pauses,clears,requests;boolean editing,scrolling,savePosition,settleRead,fail;IOException shownError;void pauseForEdit(){pauses++;editing=true;if(fail)shownError=new IOException(\"failed\");}void clearSelection(){clears++;}void clearPdfText(){}void requestWindow(){requests++;}",
            NotebookWorkspaceActivationTest.method(source,"private boolean moveContinuousTap("),
            "static void check(boolean b,String s){if(!b)throw new AssertionError(s);}public static void run(){",
            "ActiveContinuousTapFixture f=new ActiveContinuousTapFixture();check(f.moveContinuousTap(500,400),\"forward tap handled\");check(Math.abs(f.scrollY-675)<.01&&f.activePage==1,\"tap shows the lower part with 25 percent overlap\");check(f.pauses==1&&f.clears==1&&f.requests==1&&f.scrolling&&f.savePosition&&f.settleRead&&!f.editing&&f.viewportRevision==1,\"tap pauses native input, restores through the window barrier and saves position\");check(f.moveContinuousTap(50,100)&&f.scrollY==0,\"backward tap returns to the previous screen\");int pauses=f.pauses;check(!f.moveContinuousTap(50,100)&&f.pauses==pauses,\"a boundary tap does not pause the native writer\");ActiveContinuousTapFixture failure=new ActiveContinuousTapFixture();failure.fail=true;check(failure.moveContinuousTap(500,400)&&failure.scrollY==0&&failure.requests==0,\"a native pause failure does not navigate\");",
            "System.out.println(\"ActiveContinuousTap: 6 assertions passed (production pause, scroll, boundary and failure handling).\");}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.ActiveContinuousTapFixture",fixture);
    }
    private static void settings()throws Exception{
        File root=Files.createTempDirectory("reading-prefs-").toFile();
        try{
            UUID id=UUID.randomUUID();NotebookCropBox box=new NotebookCropBox(.1f,.1f,.9f,.9f);
            NotebookReadingSettings state=NotebookReadingSettings.DEFAULT.crop(NotebookReadingSettings.Crop.SEMI_AUTO).paged(true).contrast(1.5f).area(NotebookReadingSettings.Scope.ODD,id,box);
            NotebookReadingStore store=new NotebookReadingStore(root,1);check(store.read().equals(NotebookReadingSettings.DEFAULT),"new notes retain uncropped continuous identity defaults");store.write(state);
            check(new NotebookReadingStore(root,1).read().equals(state),"crop scopes, page mode and contrast persist together");
            byte[] legacy=Files.readAllBytes(new File(root,"reading/1.view").toPath());Files.write(new File(root,"reading/1.view").toPath(),legacyView(legacy,1,1));
            check(new NotebookReadingStore(root,1).read().equals(state),"version-one reading settings still open");
            NotebookReadingSettings spread=state.doublePage(true);store.write(spread);check(store.read().equals(spread)&&spread.contrast(2).doublePage,"spread preference persists and survives other preference changes");
            NotebookReadingSettings prior=spread.horizontal(true);store.write(prior);
            byte[] v3=Files.readAllBytes(new File(root,"reading/1.view").toPath());Files.write(new File(root,"reading/1.view").toPath(),legacyView(v3,3,7));
            check(store.read().equals(prior)&&!store.read().cover,"version-three settings retain all choices and default to no cover");
            NotebookReadingSettings cover=spread.cover(true);store.write(cover);
            check(store.read().equals(cover)&&cover.contrast(2).cover&&cover.area(NotebookReadingSettings.Scope.ALL,id,box).cover,"cover alignment persists across crop and contrast changes");
            byte[] v2=Files.readAllBytes(new File(root,"reading/1.view").toPath());Files.write(new File(root,"reading/1.view").toPath(),legacyView(v2,2,2));
            check(store.read().equals(spread),"version-two spread settings migrate to page turns and double page");
            store.write(cover);byte[] v4=Files.readAllBytes(new File(root,"reading/1.view").toPath());
            Files.write(new File(root,"reading/1.view").toPath(),legacyView(v4,4,11));
            check(store.read().equals(cover)&&store.read().tapOverlap==10,"version-four cover settings preserve alignment and receive the default overlap");
            for(int overlap:new int[]{0,10,33,80}){NotebookReadingSettings option=cover.tapOverlap(overlap);store.write(option);
                check(store.read().equals(option)&&option.crop(NotebookReadingSettings.Crop.AUTO).tapOverlap==overlap,"tap overlap persists and survives other settings changes");}
            for(boolean paged:new boolean[]{false,true})for(boolean two:new boolean[]{false,true})for(boolean sideways:new boolean[]{false,true}){
                NotebookReadingSettings option=state.paged(paged).doublePage(two).horizontal(sideways);store.write(option);
                check(store.read().equals(option),"independent reading choices persist in every combination");
                check(option.contrast(2).paged==paged&&option.crop(NotebookReadingSettings.Crop.AUTO).doublePage==two&&option.paged(!paged).horizontal==sideways,"changing one preference preserves the others");
            }
            store.write(state);
            check(state.manual(1,id).equals(box)&&state.manual(2,id).equals(NotebookCropBox.FULL),"odd/even areas remain independent");
            NotebookReadingSettings changed=state.area(NotebookReadingSettings.Scope.PAGE,id,new NotebookCropBox(.2f,.2f,.8f,.8f));store.write(changed);
            Files.write(new File(root,"reading/1.view").toPath(),new byte[]{1});check(new NotebookReadingStore(root,1).read().equals(state),"corrupt preferences recover the exact predecessor without changing notes");
            List<NotebookViewport.Size> pages=Arrays.asList(new NotebookViewport.Size(100,200,id,1),new NotebookViewport.Size(100,200,UUID.randomUUID(),0));
            List<NotebookViewport.Size> layout=state.layout(pages,(p,area)->new NotebookCropBox(.15f,.15f,.85f,.85f));
            check(layout.get(0).crop.left==.15f&&layout.get(1).crop.equals(NotebookCropBox.FULL),"semi-auto detection applies to PDF pages only");
            check(changed.crop(NotebookReadingSettings.Crop.MANUAL).layout(pages,(p,area)->{throw new AssertionError("manual crop must not rasterize");}).get(0).crop.left==.2f,"manual crop uses the exact chosen box");
            reject(()->state.contrast(Float.NaN));reject(()->state.tapOverlap(-1));reject(()->state.tapOverlap(81));reject(()->state.pages.clear());
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
