package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure geometry for a continuous, zoomable stack of independently stored pages. */
public final class NotebookViewport {
    /** Percentages are relative to the width-fitted view, independent of display density. */
    public enum Fit { WIDTH, HEIGHT, PAGE, CUSTOM }
    public static final class Size {
        public final int width, height;
        public final java.util.UUID id;
        public final int pdfPage;
        public final NotebookCropBox crop;
        public Size(int width, int height) {
            this(width,height,null,0,NotebookCropBox.FULL);
        }
        public Size(int width,int height,java.util.UUID id,int pdfPage) {
            this(width,height,id,pdfPage,NotebookCropBox.FULL);
        }
        private Size(int width,int height,java.util.UUID id,int pdfPage,NotebookCropBox crop) {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Missing page dimensions");
            if(pdfPage<0||crop==null)throw new IllegalArgumentException("Invalid page view metadata");
            this.width = width; this.height = height;this.id=id;this.pdfPage=pdfPage;this.crop=crop;
        }
        public Size cropped(NotebookCropBox box){return crop.equals(box)?this:new Size(width,height,id,pdfPage,box);}
    }

    public static final class EraseGesture {
        public final int pageId;
        /** Separate paths must not be joined across an off-page excursion. */
        public final List<List<NotebookPage.Point>> paths;
        public final float radius;
        private EraseGesture(int pageId, List<List<NotebookPage.Point>> paths, float radius) {
            this.pageId = pageId; this.radius = radius;
            List<List<NotebookPage.Point>> copy = new ArrayList<>();
            for (List<NotebookPage.Point> path : paths) copy.add(Collections.unmodifiableList(new ArrayList<>(path)));
            this.paths = Collections.unmodifiableList(copy);
        }
    }

    public final int width, height, pageCount;
    public final float gap;
    public final float zoom, pan;
    /** Zero means continuous; otherwise the one-based first page of the paged view. */
    public final int singlePage;
    /** Two pages are laid out side by side, in both continuous and paged modes. */
    public final boolean spread;
    /** Continuous scrolling runs horizontally; pan carries the horizontal position. */
    public final boolean horizontal;
    private final List<Size> sizes;
    private final float[] tops, scales, heights;
    private final float totalHeight;

    public NotebookViewport(int pageCount, int logicalWidth, int logicalHeight,
                            int viewportWidth, int viewportHeight, float gap) {
        this(uniform(pageCount, logicalWidth, logicalHeight), viewportWidth, viewportHeight, gap);
    }

    public NotebookViewport(List<Size> sizes, int viewportWidth, int viewportHeight, float gap) {
        this(sizes, viewportWidth, viewportHeight, gap, 1, 0);
    }

    public NotebookViewport(int pageCount, int logicalWidth, int logicalHeight,
                            int viewportWidth, int viewportHeight, float gap, float zoom, float pan) {
        this(uniform(pageCount, logicalWidth, logicalHeight), viewportWidth, viewportHeight, gap, zoom, pan);
    }

    public NotebookViewport(List<Size> sizes, int viewportWidth, int viewportHeight, float gap, float zoom, float pan) {
        this(sizes, viewportWidth, viewportHeight, gap, zoom, pan, false,0,false,false);
    }

    private NotebookViewport(List<Size> sizes, int viewportWidth, int viewportHeight, float gap,
                             float zoom, float pan, boolean ownedSizes,int singlePage,boolean spread,boolean horizontal) {
        if (sizes == null || sizes.isEmpty() || sizes.size() > NotebookIndex.MAX_PAGES
                || viewportWidth <= 0 || viewportHeight <= 0 || !NotebookPage.finite(gap) || gap < 0
                || !NotebookPage.finite(zoom) || zoom <= 0 || !NotebookPage.finite(pan)) {
            throw new IllegalArgumentException("Invalid notebook viewport");
        }
        this.width = viewportWidth; this.height = viewportHeight; this.gap = gap;
        this.zoom = zoom;
        this.sizes = ownedSizes ? sizes : Collections.unmodifiableList(new ArrayList<>(sizes));
        pageCount = sizes.size();
        if(singlePage<0||singlePage>pageCount)throw new IllegalArgumentException("Invalid single page");
        this.singlePage=singlePage;
        this.spread=spread;
        this.horizontal=horizontal && singlePage==0;
        this.pan=Math.max(0,Math.min(horizontalMaxPan(),pan));
        tops = new float[pageCount]; scales = new float[pageCount]; heights = new float[pageCount];
        float top = 0;
        for (int i = 0; i < pageCount; i++) {
            Size size = this.sizes.get(i);
            if (size == null) throw new IllegalArgumentException("Missing page dimensions");
            scales[i] = pageSlotWidth() / (size.width*size.crop.width()) * zoom;
            heights[i] = size.height * size.crop.height() * scales[i];
            if (this.horizontal) {
                tops[i]=0;
                top=Math.max(top,heights[i]);
            } else if (!this.spread) {
                tops[i] = top;
                top += heights[i] + (i + 1 < pageCount ? gap : 0);
            } else if ((i & 1) == 1) {
                tops[i] = tops[i-1];
                top += Math.max(heights[i-1],heights[i]) + (i + 1 < pageCount ? gap : 0);
            } else {
                tops[i] = top;
                if (i + 1 == pageCount) top += heights[i];
            }
            if (!NotebookPage.finite(top)) throw new IllegalArgumentException("Notebook geometry is too large");
        }
        totalHeight = top;
    }

    private int firstInGroup(int pageId){return spread?pageId-(pageId+1)%2:pageId;}
    private int groupSize(){return spread?2:1;}
    private int groupCount(){return (pageCount+groupSize()-1)/groupSize();}
    private float groupStart(int pageId){return ((firstInGroup(pageId)-1)/groupSize())*(width*zoom+gap);}
    private float groupHeight(int pageId){int first=firstInGroup(pageId);return spread&&first<pageCount?Math.max(heights[first-1],heights[first]):heights[first-1];}
    private int horizontalCenterPage(){
        float position=pan+width/2f-Math.max(0,(width-width*zoom)/2f);
        int group=Math.max(0,Math.min(groupCount()-1,(int)Math.floor(position/(width*zoom+gap))));
        return group*groupSize()+1;
    }
    private float horizontalMaxPan(){return horizontal?(groupCount()-1)*(width*zoom+gap)+Math.max(0,width*zoom-width):Math.max(0,width*zoom-width);}
    private float minScroll(){return singlePage==0?0:tops[singlePage-1];}
    private float spreadHeight(){return groupHeight(singlePage);}
    private float spreadGap(){return Math.min(gap,Math.max(0,width-2));}
    private float pageSlotWidth(){return spread?(width-spreadGap())/2:width;}
    public float maxScroll() {
        if(horizontal)return Math.max(0,groupHeight(horizontalCenterPage())-height);
        if(singlePage!=0)return minScroll()+Math.max(0,spreadHeight()-height);
        // A short final page can still be brought to the top when a reader taps Next.
        float maximum=Math.max(0,totalHeight-height);
        if(groupCount()>1&&groupHeight(pageCount)<height)maximum=Math.max(maximum,tops[firstInGroup(pageCount)-1]);
        return maximum;
    }
    public float clampScroll(float scroll) {
        if (!NotebookPage.finite(scroll)) throw new IllegalArgumentException("Invalid scroll position");
        return Math.max(minScroll(), Math.min(maxScroll(), scroll));
    }
    public float scale(int pageId) { return scales[index(pageId)]; }
    public float pageLeft(int pageId) { return viewLeft(pageId)-cropLeft(pageId)*scale(pageId); }
    public float pageRight(int pageId) { return pageLeft(pageId) + pageWidth(pageId) * scale(pageId); }
    public NotebookCropBox crop(int pageId){return sizes.get(index(pageId)).crop;}
    public float cropLeft(int pageId){return crop(pageId).left*pageWidth(pageId);}
    public float cropTop(int pageId){return crop(pageId).top*pageHeight(pageId);}
    public float viewLeft(int pageId){index(pageId);return (horizontal?Math.max(0,width*(1-zoom)/2)+groupStart(pageId)-pan:(zoom<=1?width*(1-zoom)/2:-pan))
        +(spread && pageId%2==0?(pageSlotWidth()+spreadGap())*zoom:0);}
    public float viewRight(int pageId){return viewLeft(pageId)+pageSlotWidth()*zoom;}
    private float verticalPadding(int pageId){return horizontal||singlePage==pageId||singlePage!=0&&spread&&singlePage+1==pageId?Math.max(0,(height-heights[index(pageId)])/2):0;}
    public float viewTop(int pageId,float scroll){return tops[index(pageId)]-clampScroll(scroll)+verticalPadding(pageId);}
    public float viewBottom(int pageId,float scroll){return viewTop(pageId,scroll)+heights[index(pageId)];}
    public NotebookFigure fitFigure(int pageId,NotebookFigure figure){
        float x=cropLeft(pageId),y=cropTop(pageId);
        NotebookFigure fitted=figure.placed(figure.x-x,figure.y-y,figure.width,figure.height)
            .fitted(pageWidth(pageId)*crop(pageId).width(),pageHeight(pageId)*crop(pageId).height());
        return fitted.placed(fitted.x+x,fitted.y+y,fitted.width,fitted.height);
    }
    public float maxPan() { return horizontalMaxPan(); }
    public float clampPan(float position) {
        if (!NotebookPage.finite(position)) throw new IllegalArgumentException("Invalid horizontal position");
        return Math.max(0, Math.min(maxPan(), position));
    }
    /** Pan changes share immutable geometry; a vertical drag at the same pan needs no allocation. */
    public NotebookViewport withView(float zoom, float pan) {
        if (!NotebookPage.finite(zoom) || zoom <= 0 || !NotebookPage.finite(pan))
            throw new IllegalArgumentException("Invalid notebook viewport");
        if (zoom != this.zoom) return new NotebookViewport(sizes, width, height, gap, zoom, pan, true,singlePage,spread,horizontal);
        float nextPan = clampPan(pan);
        return nextPan == this.pan ? this : new NotebookViewport(this, nextPan);
    }
    public NotebookViewport withSinglePage(int pageId){
        return pageId==singlePage&&!spread&&!horizontal?this:new NotebookViewport(sizes,width,height,gap,zoom,pan,true,pageId,false,false);
    }
    public NotebookViewport withSpreadPage(int pageId){
        index(pageId);int first=pageId-(pageId+1)%2;
        return spread&&first==singlePage&&!horizontal?this:new NotebookViewport(sizes,width,height,gap,zoom,pan,true,first,true,false);
    }
    public NotebookViewport withLayout(boolean paged,boolean doublePage,boolean horizontal,int pageId){
        index(pageId);int first=doublePage?firstInPair(pageId):pageId;
        int selected=paged?first:0;boolean sideways=!paged&&horizontal;
        return singlePage==selected&&spread==doublePage&&this.horizontal==sideways?this:
            new NotebookViewport(sizes,width,height,gap,zoom,pan,true,selected,doublePage,sideways);
    }
    private static int firstInPair(int pageId){return pageId-(pageId+1)%2;}
    public NotebookViewport forPage(int pageId){index(pageId);return singlePage==0?this:spread?withSpreadPage(pageId):withSinglePage(pageId);}

    private NotebookViewport(NotebookViewport layout, float pan) {
        width = layout.width; height = layout.height; pageCount = layout.pageCount;
        gap = layout.gap; zoom = layout.zoom; this.pan = pan;
        singlePage=layout.singlePage;
        spread=layout.spread; horizontal=layout.horizontal;
        sizes = layout.sizes; tops = layout.tops; scales = layout.scales; heights = layout.heights;
        totalHeight = layout.totalHeight;
    }

    /** First page whose bottom reaches the position (or strictly passes it for visible area). */
    private int firstEnding(float position, boolean strict) {
        int low=0,high=groupCount();
        while(low<high){int middle=(low+high)>>>1,first=middle*groupSize();
            float end=tops[first]+groupHeight(first+1);
            if(strict?end<=position:end<position)low=middle+1;else high=middle;}
        return low*groupSize();
    }

    public float fittedZoom(Fit fit, int pageId) {
        float heightFit = (float)height * pageWidth(pageId)*crop(pageId).width() / (pageSlotWidth() * (float)pageHeight(pageId)*crop(pageId).height());
        if(spread){int first=pageId-(pageId+1)%2,second=first+1;
            float firstFit=(float)height*pageWidth(first)*crop(first).width()/(pageSlotWidth()*pageHeight(first)*crop(first).height());
            heightFit=firstFit;
            if(second<=pageCount)heightFit=Math.min(heightFit,(float)height*pageWidth(second)*crop(second).width()/(pageSlotWidth()*pageHeight(second)*crop(second).height()));
        }
        if (fit == Fit.WIDTH) return 1;
        if (fit == Fit.HEIGHT) return heightFit;
        if (fit == Fit.PAGE) return Math.min(1, heightFit);
        throw new IllegalArgumentException("Custom zoom has no fitted value");
    }

    /** Keep a logical page point under the same screen position during pinch or resize. */
    public float scrollForPoint(int pageId, float logicalY, float screenY) {
        if (!NotebookPage.finite(logicalY) || !NotebookPage.finite(screenY)) throw new IllegalArgumentException("Invalid zoom anchor");
        return clampScroll(tops[index(pageId)] + (logicalY-cropTop(pageId)) * scale(pageId) + verticalPadding(pageId) - screenY);
    }
    public float panForPoint(int pageId, float logicalX, float screenX) {
        if (!NotebookPage.finite(logicalX) || !NotebookPage.finite(screenX)) throw new IllegalArgumentException("Invalid zoom anchor");
        return clampPan((logicalX-cropLeft(pageId)) * scale(pageId)
            +(spread&&pageId%2==0?(pageSlotWidth()+spreadGap())*zoom:0)
            +(horizontal?Math.max(0,width*(1-zoom)/2)+groupStart(pageId):0)-screenX);
    }
    public int pageWidth(int pageId) { return sizes.get(index(pageId)).width; }
    public int pageHeight(int pageId) { return sizes.get(index(pageId)).height; }
    public float pageTop(int pageId, float scroll) { return viewTop(pageId,scroll)-cropTop(pageId)*scale(pageId); }
    public float pageScroll(int pageId) { return clampScroll(tops[index(pageId)]); }
    public float navigationScroll(int pageId){return pageScroll(pageId);}
    /** Continuous page turns follow the leading page, even when several fit on screen. */
    public int navigationPage(float scroll){return singlePage!=0?singlePage:anchorPage(scroll);}
    /** Horizontal continuous layouts store their page position in pan. */
    public float panForPage(int pageId){index(pageId);return horizontal?clampPan(groupStart(pageId)):0;}
    public float panForAnchor(int pageId,double fraction){
        index(pageId);if(Double.isNaN(fraction)||Double.isInfinite(fraction)||fraction<0||fraction>1)throw new IllegalArgumentException("Invalid scroll anchor");
        return horizontal?clampPan((float)(groupStart(pageId)+fraction*(width*zoom+gap))):pan;
    }

    /** Anchor belongs to the page above a gap, preserving an arbitrary continuous offset. */
    public int anchorPage(float scroll) {
        if(singlePage!=0)return singlePage;
        if(horizontal){int group=Math.min(groupCount()-1,(int)(pan/(width*zoom+gap)));return group*groupSize()+1;}
        float offset=clampScroll(scroll);int low=0,high=groupCount();
        while(low<high){int middle=(low+high)>>>1;if(tops[middle*groupSize()]<=offset)low=middle+1;else high=middle;}
        return Math.max(0,low-1)*groupSize()+1;
    }

    public double anchorFraction(float scroll) {
        int page=anchorPage(scroll);
        if(horizontal)return Math.max(0,Math.min(Math.nextDown(1.0),(pan-groupStart(page))/(width*zoom+gap)));
        float offset=clampScroll(scroll);int i=page-1;
        float span=groupHeight(page)+(i+groupSize()<pageCount?gap:0);
        return Math.max(0,Math.min(Math.nextDown(1.0),((double)offset-tops[i])/span));
    }

    public float scrollForAnchor(int pageId, double fraction) {
        int i=index(pageId);
        if(Double.isNaN(fraction)||Double.isInfinite(fraction)||fraction<0||fraction>1)throw new IllegalArgumentException("Invalid scroll anchor");
        if(horizontal)return clampScroll(0);
        int first=firstInGroup(pageId);i=first-1;
        double span=groupHeight(first)+(i+groupSize()<pageCount?gap:0);
        return clampScroll((float)(tops[i]+fraction*span));
    }

    public List<Integer> visiblePages(float scroll) {
        if(singlePage!=0){if(spread&&singlePage<pageCount)return java.util.Arrays.asList(singlePage,singlePage+1);return Collections.singletonList(singlePage);}
        List<Integer> visible=new ArrayList<>();
        if(horizontal){
            float start=Math.max(0,pan-Math.max(0,width*(1-zoom)/2));
            int group=Math.max(0,Math.min(groupCount()-1,(int)(start/(width*zoom+gap))));
            for(int i=group*groupSize();i<pageCount;i+=groupSize()){
                if(viewLeft(i+1)>=width)break;
                for(int j=i;j<Math.min(pageCount,i+groupSize());j++)
                    if(viewRight(j+1)>0&&viewLeft(j+1)<width)visible.add(j+1);
            }
        }else{
            float offset=clampScroll(scroll);
            for(int i=firstEnding(offset,true);i<pageCount&&tops[i]<offset+height;i+=groupSize())
                for(int j=i;j<Math.min(pageCount,i+groupSize());j++)
                    if(tops[j]+heights[j]>offset)visible.add(j+1);
        }
        return Collections.unmodifiableList(visible);
    }

    /** Zero means the point is outside the viewport or in a page gap. */
    public int pageAt(float x, float y, float scroll) {
        if (!NotebookPage.finite(x) || !NotebookPage.finite(y) || x < 0 || x > width || y < 0 || y > height) return 0;
        if(singlePage!=0||horizontal){for(int id:visiblePages(scroll))if(x>=viewLeft(id)&&x<=viewRight(id)&&y>=viewTop(id,scroll)&&y<=viewBottom(id,scroll))return id;return 0;}
        float position = y + clampScroll(scroll);
        for (int i = firstEnding(position, false); i < pageCount && tops[i] <= position; i++) {
            if (position <= tops[i]+heights[i] && x >= viewLeft(i + 1) && x <= viewRight(i + 1)) return i + 1;
        }
        return 0;
    }

    /** Chooses the nearest page when the viewport center falls inside a gap. */
    public int centeredPage(float scroll) {
        return nearestPage(height / 2f, scroll);
    }

    public int nearestPage(float y, float scroll) {
        if(!NotebookPage.finite(y))throw new IllegalArgumentException("Invalid page position");
        if(singlePage!=0)return singlePage;
        if(horizontal)return horizontalCenterPage();
        float center=clampScroll(scroll)+y;if(!NotebookPage.finite(center))return 1;
        int next=firstEnding(center,false)/groupSize(),closest=0;float distance=Float.MAX_VALUE;
        for(int group=Math.max(0,next-1);group<=Math.min(groupCount()-1,next+1);group++){
            int first=group*groupSize();float delta=Math.max(tops[first]-center,Math.max(0,center-tops[first]-groupHeight(first+1)));
            if(delta<distance){distance=delta;closest=group;}
        }
        int low=0,high=closest;
        while(low<high){int mid=(low+high)>>>1,first=mid*groupSize();
            float delta=Math.max(tops[first]-center,Math.max(0,center-tops[first]-groupHeight(first+1)));
            if(delta<=distance)high=mid;else low=mid+1;}
        return low*groupSize()+1;
    }

    /** Builds only visible tracks; paper/background composition is handled by the native adapter. */
    public NotebookPage compose(Map<Integer, NotebookPage> pages, float scroll) {
        if (pages == null) throw new IllegalArgumentException("Missing loaded pages");
        List<NotebookPage.Stroke> result = new ArrayList<>();
        int count = 0;
        for (int id : visiblePages(scroll)) {
            NotebookPage page = pages.get(id);
            if (page == null) continue;
            if (page.width != pageWidth(id) || page.height != pageHeight(id)) {
                throw new IllegalArgumentException("Loaded page dimensions differ from viewport layout");
            }
            float scale = scale(id), top = pageTop(id, scroll), left = pageLeft(id);
            for (NotebookPage.Stroke stroke : page.strokes) {
                float clipLeft = Math.max(0, viewLeft(id)), clipTop = Math.max(0, viewTop(id,scroll));
                float clipRight = Math.min(width, viewRight(id));
                float clipBottom = Math.min(height, viewBottom(id,scroll));
                if (!intersects(stroke, scale, left, top, clipLeft, clipTop, clipRight, clipBottom)) continue;
                List<NotebookPage.Point> screen = transform(stroke.points, scale, left, top);
                result.add(stroke.inView(stroke.width * scale, screen, stroke.captureScale / scale,
                    clipLeft, clipTop, clipRight, clipBottom));
                count = Math.addExact(count, screen.size());
            }
        }
        return NotebookPage.restored(width, height, NotebookPage.Template.BLANK, result, count);
    }

    private static boolean intersects(NotebookPage.Stroke stroke, float scale, float x, float y,
                                      float left, float top, float right, float bottom) {
        // Conservative native brush padding also retains thick edges whose
        // centreline is outside the viewport, without replaying distant ink.
        float padding = 2 * Math.max(stroke.width * stroke.maxPressure, 1 / stroke.captureScale) * scale;
        return stroke.maxX * scale + x + padding >= left && stroke.minX * scale + x - padding <= right
            && stroke.maxY * scale + y + padding >= top && stroke.minY * scale + y - padding <= bottom;
    }

    /** Clips a completed screen stroke into independently stored page-local pieces. */
    public Map<Integer, List<NotebookPage.Stroke>> splitStroke(NotebookPage.Stroke stroke, float scroll) {
        if (stroke == null) throw new IllegalArgumentException("Missing viewport stroke");
        Map<Integer, List<NotebookPage.Stroke>> result = new LinkedHashMap<>();
        for (int id : visiblePages(scroll)) {
            float top = pageTop(id, scroll), scale = scale(id), left = pageLeft(id);
            List<List<NotebookPage.Point>> pieces = clip(stroke.points, Math.max(0, viewLeft(id)), Math.max(0, viewTop(id,scroll)), Math.min(width, viewRight(id)),
                    Math.min(height, viewBottom(id,scroll)));
            if (pieces.isEmpty()) continue;
            List<NotebookPage.Stroke> local = new ArrayList<>();
            for (List<NotebookPage.Point> piece : pieces) {
                local.add(new NotebookPage.Stroke(stroke.penType, stroke.width / scale, stroke.color,
                        transformClipped(piece, 1 / scale, -left / scale, -top / scale, pageWidth(id), pageHeight(id)),
                        stroke.captureScale * scale, stroke.captureWidth));
            }
            result.put(id, Collections.unmodifiableList(local));
        }
        return Collections.unmodifiableMap(result);
    }

    /** Map the whole brush footprint, including centres just outside a page's visible edge. */
    public List<EraseGesture> splitErase(List<NotebookPage.Point> path, float radius, float scroll) {
        checkPath(path);
        if (!NotebookPage.finite(radius) || radius < 0) throw new IllegalArgumentException("Invalid eraser radius");
        List<EraseGesture> result = new ArrayList<>();
        for (int id : visiblePages(scroll)) {
            float top = pageTop(id, scroll), scale = scale(id), left = pageLeft(id);
            List<List<NotebookPage.Point>> pieces = clip(path, Math.max(0, viewLeft(id)) - radius, Math.max(0, viewTop(id,scroll)) - radius, Math.min(width, viewRight(id)) + radius,
                    Math.min(height, viewBottom(id,scroll)) + radius);
            if (pieces.isEmpty()) continue;
            List<List<NotebookPage.Point>> local = new ArrayList<>();
            // Eraser paths are transient and may lie outside page bounds. Clamping the centres
            // would distort the grey footprint and turn a near miss into a deletion.
            for (List<NotebookPage.Point> piece : pieces) local.add(transform(piece, 1 / scale, -left / scale, -top / scale));
            result.add(new EraseGesture(id, local, radius / scale));
        }
        return Collections.unmodifiableList(result);
    }

    /** Region selection cannot reach invisible crop margins, offscreen pages or page gutters. */
    public Map<Integer,List<NotebookPage.Point>> splitRegion(List<NotebookPage.Point> path,float scroll){
        checkPath(path);
        Map<Integer,List<NotebookPage.Point>> result=new LinkedHashMap<>();
        for(int id:visiblePages(scroll)){
            List<NotebookPage.Point> clipped=NotebookRegionEraser.clip(path,
                Math.max(0,viewLeft(id)),Math.max(0,viewTop(id,scroll)),
                Math.min(width,viewRight(id)),Math.min(height,viewBottom(id,scroll)));
            if(clipped.isEmpty())continue;
            float scale=scale(id);
            result.put(id,Collections.unmodifiableList(transformClipped(clipped,1/scale,
                -pageLeft(id)/scale,-pageTop(id,scroll)/scale,pageWidth(id),pageHeight(id))));
        }
        return Collections.unmodifiableMap(result);
    }

    private int index(int pageId) {
        if (pageId < 1 || pageId > pageCount) throw new IllegalArgumentException("Page is out of range");
        return pageId - 1;
    }

    private static List<Size> uniform(int count, int width, int height) {
        if (count < 1 || count > NotebookIndex.MAX_PAGES) throw new IllegalArgumentException("Invalid page count");
        List<Size> sizes = new ArrayList<>();
        Size size = new Size(width, height);
        for (int i = 0; i < count; i++) sizes.add(size);
        return sizes;
    }

    private static List<NotebookPage.Point> transform(List<NotebookPage.Point> points, float scale, float x, float y) {
        List<NotebookPage.Point> result = new ArrayList<>();
        NotebookTransform transform = NotebookTransform.fit(scale, x, y);
        for (NotebookPage.Point point : points) result.add(transform.map(point));
        return result;
    }

    /** Only use after geometric clipping: the inverse float transform may round past an edge. */
    private static List<NotebookPage.Point> transformClipped(List<NotebookPage.Point> points, float scale,
                                                             float x, float y, float width, float height) {
        List<NotebookPage.Point> result = new ArrayList<>();
        NotebookTransform transform = NotebookTransform.fit(scale, x, y);
        for (NotebookPage.Point point : points) result.add(clamp(transform.map(point), 0, 0, width, height));
        return result;
    }

    private static void checkPath(List<NotebookPage.Point> path) {
        if (path == null || path.isEmpty()) throw new IllegalArgumentException("Missing pen path");
        for (NotebookPage.Point point : path) if (point == null) throw new IllegalArgumentException("Missing pen point");
    }

    /** Liang-Barsky segment clipping. Rejected excursions end a piece instead of drawing a chord. */
    private static List<List<NotebookPage.Point>> clip(List<NotebookPage.Point> path,
                                                       float left, float top, float right, float bottom) {
        checkPath(path);
        List<List<NotebookPage.Point>> pieces = new ArrayList<>();
        if (right < left || bottom < top) return pieces;
        if (path.size() == 1) {
            NotebookPage.Point point = path.get(0);
            if (inside(point, left, top, right, bottom)) pieces.add(Collections.singletonList(point));
            return pieces;
        }
        List<NotebookPage.Point> current = null;
        for (int i = 1; i < path.size(); i++) {
            NotebookPage.Point a = path.get(i - 1), b = path.get(i);
            double dx = (double) b.x - a.x, dy = (double) b.y - a.y;
            double[] range = {0, 1};
            if (!bound(-dx, a.x - left, range) || !bound(dx, right - a.x, range)
                    || !bound(-dy, a.y - top, range) || !bound(dy, bottom - a.y, range)) {
                current = null;
                continue;
            }
            // Interpolation can round a boundary crossing a fraction of a pixel outside the
            // rectangle. Clamp the accepted endpoints, never the original off-page path.
            NotebookPage.Point start = clamp(interpolate(a, b, range[0]), left, top, right, bottom);
            NotebookPage.Point end = clamp(interpolate(a, b, range[1]), left, top, right, bottom);
            if (current == null || range[0] > 0) {
                current = new ArrayList<>(); pieces.add(current); current.add(start);
            }
            if (!same(current.get(current.size() - 1), end)) current.add(end);
            if (range[1] < 1) current = null;
        }
        return pieces;
    }

    private static boolean inside(NotebookPage.Point p, float left, float top, float right, float bottom) {
        return p.x >= left && p.x <= right && p.y >= top && p.y <= bottom;
    }
    private static NotebookPage.Point clamp(NotebookPage.Point p, float left, float top, float right, float bottom) {
        float x = Math.max(left, Math.min(right, p.x)), y = Math.max(top, Math.min(bottom, p.y));
        return x == p.x && y == p.y ? p : new NotebookPage.Point(x, y, p.time, p.pressure);
    }
    private static boolean bound(double p, double q, double[] range) {
        if (p == 0) return q >= 0;
        double t = q / p;
        if (p < 0) { if (t > range[1]) return false; range[0] = Math.max(range[0], t); }
        else { if (t < range[0]) return false; range[1] = Math.min(range[1], t); }
        return true;
    }
    private static NotebookPage.Point interpolate(NotebookPage.Point a, NotebookPage.Point b, double t) {
        if (t == 0) return a;
        if (t == 1) return b;
        return new NotebookPage.Point((float) (a.x + ((double) b.x - a.x) * t),
                (float) (a.y + ((double) b.y - a.y) * t),
                Math.max(0, Math.round(a.time + ((double) b.time - a.time) * t)),
                (float) (a.pressure + ((double) b.pressure - a.pressure) * t));
    }
    private static boolean same(NotebookPage.Point a, NotebookPage.Point b) {
        return a.x == b.x && a.y == b.y && a.time == b.time && a.pressure == b.pressure;
    }
}
