package org.eink.inkprobe.notebook;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Execute the actual outline implementation with small window adapters. */
public final class NotebookResizeOutlineTest {
    public static void main(String[] args)throws Exception {
        String source=new String(Files.readAllBytes(Paths.get("app/src/main/java/org/eink/inkprobe/notebook/NotebookResizeOutline.java")),StandardCharsets.UTF_8)
            .replace("package org.eink.inkprobe.notebook;", "").replaceAll("(?m)^import .*;\\s*$", "");
        String fixture=String.join("\n",
            "package org.eink.inkprobe.notebook;public class ResizeOutlineFixture {",
            "static class Context {Resources getResources(){return new Resources();}}static class Resources {Metrics getDisplayMetrics(){return new Metrics();}}static class Metrics {float density=1;}",
            "static class Rect {int left,top,right,bottom;void set(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}void setEmpty(){set(0,0,0,0);}}",
            "static class Color {static final int BLACK=0xff000000;}static class ColorDrawable {ColorDrawable(int color){}}static class Gravity {static final int TOP=48,LEFT=3;}static class WindowManager {static class LayoutParams {static final int TYPE_APPLICATION_PANEL=1000;}}",
            "static class View {boolean attached=true;View(Context c){}void setBackgroundColor(int c){}boolean isAttachedToWindow(){return attached;}void getLocationOnScreen(int[] p){p[0]=10;p[1]=20;}}",
            "static class PopupWindow {int x,y,w,h,type,updates;boolean showing,touchable=true,focusable;PopupWindow(View v,int w,int h,boolean focus){this.w=w;this.h=h;focusable=focus;}void setBackgroundDrawable(ColorDrawable d){}void setWindowLayoutType(int t){type=t;}void setTouchable(boolean b){touchable=b;}void setOutsideTouchable(boolean b){}void setClippingEnabled(boolean b){}void setIsLaidOutInScreen(boolean b){}void setAnimationStyle(int n){}void setElevation(int n){}void setEnterTransition(Object o){}void setExitTransition(Object o){}boolean isShowing(){return showing;}void update(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;updates++;}void setWidth(int n){w=n;}void setHeight(int n){h=n;}void dismiss(){showing=false;}}",
            "static class NotebookPopupHost {static int shows,failAt;static boolean show(PopupWindow p,View root,int gravity,int x,int y){shows++;if(shows==failAt)return false;p.showing=true;p.x=x;p.y=y;return true;}}",
            source,
            "static void check(boolean b,String s){if(!b)throw new AssertionError(s);}public static void run(){",
            "ResizeOutlineFixture owner=new ResizeOutlineFixture();View root=new View(new Context());NotebookResizeOutline guide=owner.new NotebookResizeOutline(new Context());guide.show(root,new NotebookWorkspace.Box(100,200,600,700));check(guide.showing()&&NotebookPopupHost.shows==4,\"resize creates four tiny edge panels\");check(guide.bounds.left==110&&guide.bounds.top==220&&guide.bounds.right==710&&guide.bounds.bottom==920,\"guide follows pane bounds in screen coordinates\");",
            "int pixels=0;for(PopupWindow edge:guide.edges){check(!edge.touchable&&!edge.focusable&&edge.type==1000,\"outline cannot intercept dragging or cover the higher toolbox layer\");check(edge.w<=2||edge.h<=2,\"edge surfaces never allocate a page-sized buffer\");pixels+=edge.w*edge.h;check(!(400>=edge.x&&400<edge.x+edge.w&&500>=edge.y&&500<edge.y+edge.h),\"outline leaves note center unobscured\");}check(pixels<600*700/10,\"outline has a small perimeter-only footprint\");",
            "guide.show(root,new NotebookWorkspace.Box(110,210,630,740));check(NotebookPopupHost.shows==4&&guide.edges[0].updates==1&&guide.bounds.right==750&&guide.bounds.bottom==970,\"pointer updates reuse edges without hide/show or page reflow\");root.attached=false;guide.show(root,new NotebookWorkspace.Box(0,0,300,400));for(PopupWindow p:guide.edges)check(!p.showing,\"detachment clears every edge\");check(guide.bounds.right==0,\"detachment clears stale bounds\");",
            "root.attached=true;NotebookPopupHost.failAt=NotebookPopupHost.shows+3;guide.show(root,new NotebookWorkspace.Box(0,0,300,400));for(PopupWindow p:guide.edges)check(!p.showing,\"partial token failure removes already shown edges\");",
            "}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.ResizeOutlineFixture",fixture);
        System.out.println("NotebookResizeOutline: 25 assertions passed (thin edges, live updates, no touch capture and teardown).");
    }
}
