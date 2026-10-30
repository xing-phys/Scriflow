package org.eink.inkprobe.notebook;

import java.util.*;

/** Bounded content detection on a preview raster, never on zoomed full-page pixels. */
final class NotebookPdfCrop {
    static NotebookCropBox detect(int[] pixels,int width,int height,NotebookCropBox area) {
        if(pixels==null||width<1||height<1||(long)width*height>2000000||pixels.length!=width*height||area==null)
            throw new IllegalArgumentException("Invalid crop preview");
        int x0=Math.max(0,(int)Math.floor(area.left*width)),y0=Math.max(0,(int)Math.floor(area.top*height));
        int x1=Math.min(width,(int)Math.ceil(area.right*width)),y1=Math.min(height,(int)Math.ceil(area.bottom*height));
        int[] histogram=new int[256];int samples=0;
        for(int y=y0;y<y1;y+=Math.max(1,(y1-y0)/200))for(int x=x0;x<x1;x+=Math.max(1,(x1-x0)/200))
            if(x-x0<4||x1-x<=4||y-y0<4||y1-y<=4){histogram[gray(pixels[y*width+x])]++;samples++;}
        int background=255,total=0;
        for(int n=0;n<256;n++){total+=histogram[n];if(total>=Math.max(1,samples*.9)){background=n;break;}}
        int threshold=Math.min(249,background-6);
        BitSet seen=new BitSet(pixels.length);int[] queue=new int[pixels.length];
        int left=x1,top=y1,right=x0,bottom=y0;
        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++) {
            int start=y*width+x;if(seen.get(start)||gray(pixels[start])>=threshold)continue;
            int head=0,tail=0,minX=x,maxX=x,minY=y,maxY=y;queue[tail++]=start;seen.set(start);
            while(head<tail){
                int at=queue[head++],cx=at%width,cy=at/width;
                minX=Math.min(minX,cx);maxX=Math.max(maxX,cx);minY=Math.min(minY,cy);maxY=Math.max(maxY,cy);
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                    int nx=cx+dx,ny=cy+dy;if(nx<x0||nx>=x1||ny<y0||ny>=y1)continue;
                    int next=ny*width+nx;if(!seen.get(next)&&gray(pixels[next])<threshold){seen.set(next);queue[tail++]=next;}
                }
            }
            boolean frame=minX<=x0+1&&minY<=y0+1&&maxX>=x1-2&&maxY>=y1-2
                &&tail<(x1-x0)*(long)(y1-y0)*.035;
            if(tail>=4&&!frame){left=Math.min(left,minX);top=Math.min(top,minY);right=Math.max(right,maxX+1);bottom=Math.max(bottom,maxY+1);}
        }
        if(right<=left||bottom<=top||(right-left)<width*.1&&(bottom-top)<height*.1)return area;
        return new NotebookCropBox(Math.max(area.left,left/(float)width-.01f),Math.max(area.top,top/(float)height-.01f),
            Math.min(area.right,right/(float)width+.01f),Math.min(area.bottom,bottom/(float)height+.01f));
    }
    private static int gray(int argb){return (((argb>>>16)&255)*77+((argb>>>8)&255)*150+(argb&255)*29)>>>8;}
}
