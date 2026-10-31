package org.eink.inkprobe.notebook;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Production raster/assembly callbacks with an adapted native boundary; not device raster verification. */
public final class NotebookMarkdownRasterizerTest {
    public static void main(String[] args)throws Exception{
        String source=new String(Files.readAllBytes(Paths.get("app/src/main/java/org/eink/inkprobe/notebook/NotebookMarkdownRasterizer.java")),StandardCharsets.UTF_8);
        String fixture=String.join("\n",
            "package org.eink.inkprobe.notebook;import java.util.*;import java.io.*;public class MarkdownRasterizerFixture {",
            "static class Color {static final int TRANSPARENT=0;}static class Matrix {float[] values;void setValues(float[] v){values=v;}}",
            "static class Bitmap {static final int DENSITY_NONE=0;enum Config {ARGB_8888}static final List<Bitmap> allocations=new ArrayList<>();final int width,height;final int[] pixels;int density=320;boolean recycled;Bitmap(int w,int h){width=w;height=h;pixels=new int[w*h];allocations.add(this);}static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(w,h);}int getWidth(){return width;}int getHeight(){return height;}void setDensity(int d){density=d;}void eraseColor(int c){Arrays.fill(pixels,c);}void recycle(){if(recycled)throw new AssertionError(\"double recycle\");recycled=true;}}",
            "static class Canvas {final Bitmap target;Canvas(Bitmap b){target=b;}void drawBitmap(Bitmap source,float x,float y,Object paint){if(source.density!=target.density)throw new AssertionError(\"density must not resample tiled text\");for(int row=0;row<source.height;row++)System.arraycopy(source.pixels,row*source.width,target.pixels,((int)y+row)*target.width+(int)x,source.width);}}",
            "static class PdfRenderer {static class Page {static final int RENDER_MODE_FOR_DISPLAY=1,RENDER_MODE_FOR_PRINT=2;int calls;boolean fail;int getWidth(){return 480;}int getHeight(){return 240;}void render(Bitmap bitmap,Object clip,Matrix matrix,int mode){if(mode!=RENDER_MODE_FOR_PRINT)throw new AssertionError(\"LCD text must not enter the normal writing surface\");if(bitmap.density!=0)throw new AssertionError(\"pixel geometry must be density-independent\");calls++;if(fail)throw new IllegalStateException(\"injected native failure\");int left=Math.round(-matrix.values[2]),top=Math.round(-matrix.values[5]);for(int y=0;y<bitmap.height;y++)for(int x=0;x<bitmap.width;x++){int n=((x+left)*7+(y+top)*11)%256;bitmap.pixels[y*bitmap.width+x]=(n<<24)|((255-n)<<16)|(n<<8)|17;}}}}",
            "static class Raster {final PdfRenderer.Page page=new PdfRenderer.Page();",
            NotebookWorkspaceActivationTest.method(source,"Bitmap render(NotebookPdfTiles.Tile"),"}",
            "static Raster raster;static Bitmap render(NotebookFigure figure,NotebookPdfTiles.Tile tile)throws IOException{return raster.render(tile);}",
            NotebookWorkspaceActivationTest.method(source,"static Bitmap bitmap("),
            "static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}public static void run()throws Exception{",
            "for(int[] size:new int[][]{{320,180},{1100,700},{257,1097}}){raster=new Raster();int w=size[0],h=size[1];Bitmap batch=bitmap(null,w,h);check(batch.density==0&&raster.page.calls==NotebookPdfTiles.visible(1,w,h,0,0,w,h).size(),\"whole preview and normal tiles use the same grayscale text mode and exact grid\");for(NotebookPdfTiles.Tile tile:NotebookPdfTiles.visible(1,w,h,0,0,w,h)){Bitmap normal=raster.render(tile);for(int y=0;y<tile.height;y++)for(int x=0;x<tile.width;x++)if(normal.pixels[y*tile.width+x]!=batch.pixels[(tile.top+y)*w+tile.left+x])throw new AssertionError(\"resting and preview pixels differ\");check(normal.density==0,\"partial edge tiles retain exact pixel scale\");normal.recycle();}check(!batch.recycled,\"successful preview owns its completed pixels\");batch.recycle();}",
            "int before=Bitmap.allocations.size();raster=new Raster();raster.page.fail=true;try{bitmap(null,700,500);throw new AssertionError(\"expected native failure\");}catch(IllegalStateException expected){check(expected.getMessage().equals(\"injected native failure\"),\"real render failures propagate\");}for(Bitmap b:Bitmap.allocations.subList(before,Bitmap.allocations.size()))check(b.recycled,\"failed native tile and assembly release their allocations\");",
            "System.out.println(\"NotebookMarkdownRasterizer: \"+checks+\" assertions passed (production grayscale mode, normal/preview parity, density and failure cleanup; not native device pixels).\");}}"
        );
        NotebookWorkspaceActivationTest.runFixture("org.eink.inkprobe.notebook.MarkdownRasterizerFixture",fixture);
    }
}
