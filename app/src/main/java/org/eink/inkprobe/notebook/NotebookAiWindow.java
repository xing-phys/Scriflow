package org.eink.inkprobe.notebook;

/** Bottom-right resizing preserves the opposite corner and keeps the complete frame on screen. */
final class NotebookAiWindow {
    static int[] resize(int left,int top,int width,int height,float dx,float dy,int right,int bottom,int minimumWidth,int minimumHeight) {
        int availableWidth=Math.max(1,right-left),availableHeight=Math.max(1,bottom-top);
        return new int[]{left,top,Math.min(availableWidth,Math.max(minimumWidth,Math.round(width+dx))),
            Math.min(availableHeight,Math.max(minimumHeight,Math.round(height+dy)))};
    }
    private NotebookAiWindow() { }
}
