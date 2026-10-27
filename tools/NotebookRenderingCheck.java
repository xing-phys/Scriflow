package org.eink.inkprobe.notebook;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.json.JSONObject;
import org.json.JSONArray;

/** Headless firmware regression: synthetic geometry only; never opens a notebook or a display surface. */
public final class NotebookRenderingCheck {
    private static final int WIDTH = 1404, HEIGHT = 1760;

    public static void main(String[] args) throws Exception {
        File directory = new File(args[0]);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException("No output directory");
        NotebookPage page = fixture(NotebookPage.Template.BLANK);
        Bitmap nativeReplay = paper(page), shared = paper(page);
        reference(new Canvas(nativeReplay), page);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (NotebookPage.Stroke stroke : page.strokes)
            NotebookStrokeRenderer.draw(new Canvas(shared), stroke, paint);
        png(directory, "native-replay.png", nativeReplay);
        png(directory, "shared-canvas.png", shared);
        Bitmap thumbnail = NotebookPageThumbnail.render(page, 320, 384);
        png(directory, "thumbnail.png", thumbnail);
        thumbnail.recycle();

        File pdf = new File(directory, "pressure-export.pdf");
        try (FileOutputStream output = new FileOutputStream(pdf)) {
            NotebookPdf.write(output, "Native pressure regression", 2,
                number -> number == 1 ? page : fixture(NotebookPage.Template.LINED), null,
                NotebookStrokeRenderer::outline);
        }
        Bitmap pdfBitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
                PdfRenderer renderer = new PdfRenderer(descriptor);
                PdfRenderer.Page pdfPage = renderer.openPage(0)) {
            Matrix matrix = new Matrix();
            matrix.setScale((float) (WIDTH / 595.2756), (float) (WIDTH / 595.2756));
            pdfPage.render(pdfBitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        }
        png(directory, "pdf-on-device.png", pdfBitmap);

        JSONObject result = new JSONObject();
        result.put("syntheticOnly", true);
        result.put("userNotebookFilesOpened", false);
        result.put("strokeCount", page.strokes.size());
        result.put("nativeVsCanvas", compare(nativeReplay, shared));
        result.put("nativeVsPdf", compare(nativeReplay, pdfBitmap));
        result.put("nativeLightWidth", band(nativeReplay, 600, 120));
        result.put("nativeHeavyWidth", band(nativeReplay, 600, 210));
        result.put("canvasLightWidth", band(shared, 600, 120));
        result.put("canvasHeavyWidth", band(shared, 600, 210));
        result.put("pdfLightWidth", band(pdfBitmap, 600, 120));
        result.put("pdfHeavyWidth", band(pdfBitmap, 600, 210));
        result.put("outlineLifetimeAndCancellation", reuseAndCancellation(page));
        result.put("parallelReplay", parallelReplay(page));
        result.put("captureScale", scaledReplay(directory, page, nativeReplay));
        result.put("transparentNativePlaceholders", transparentTracks(page));
        boolean passed = result.getJSONObject("nativeVsCanvas").getInt("outsideOnePixel") == 0
            && result.getJSONObject("nativeVsPdf").getInt("outsideOnePixel") == 0
            && Math.abs(result.getInt("nativeLightWidth") - result.getInt("pdfLightWidth")) <= 1
            && Math.abs(result.getInt("nativeHeavyWidth") - result.getInt("pdfHeavyWidth")) <= 1
            && result.getInt("pdfHeavyWidth") > result.getInt("pdfLightWidth")
            && result.getBoolean("parallelReplay")
            && result.getJSONObject("captureScale").getBoolean("passed")
            && result.getBoolean("transparentNativePlaceholders");
        result.put("passed", passed);
        try (FileOutputStream output = new FileOutputStream(new File(directory, "result.json"))) {
            output.write(result.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        System.out.println(result.toString(2));
        if (!passed) throw new IllegalStateException("Native outline comparison failed");
        nativeReplay.recycle(); shared.recycle(); pdfBitmap.recycle();
    }

    private static NotebookPage fixture(NotebookPage.Template template) {
        ArrayList<NotebookPage.Stroke> strokes = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            ArrayList<NotebookPage.Point> points = new ArrayList<>();
            for (int i = 0; i <= 220; i++) {
                float pressure = row == 0 ? .1f : row == 1 ? 1f : .05f + .95f * (1 - Math.abs(i - 110) / 110f);
                float y = row < 3 ? 120 + 90 * row : 540 + (float) Math.sin(i * .08) * 55;
                points.add(new NotebookPage.Point(80 + 5 * i, y, 1000 + row * 10000 + i * 8, pressure));
            }
            strokes.add(new NotebookPage.Stroke(2, 10, Color.BLACK, points));
        }
        strokes.add(stroke(10, point(150,700,50000,.1f)));
        strokes.add(stroke(10, point(200,700,51000,1)));
        strokes.add(stroke(6, point(260,700,52000,.4f), point(260,700,52008,.4f), point(260,700,52016,.4f)));
        for (int row = 0; row < 5; row++) {
            float width = new float[]{1,3,4,6,10}[row];
            ArrayList<NotebookPage.Point> points = new ArrayList<>();
            for (int i = 0; i < 160; i++)
                points.add(point(80+7*i,850+row*70,60000+row*10000+i*8,.1f+.9f*i/159));
            strokes.add(new NotebookPage.Stroke(2,width,Color.BLACK,points));
        }
        strokes.add(stroke(10,point(100,1300,120000,.2f), point(300,1400,120200,1),
            point(500,1300,120400,.4f), point(700,1400,120600,.8f), point(900,1300,120800,.2f)));
        ArrayList<NotebookPage.Point> loop = new ArrayList<>();
        for (int i=0;i<=240;i++) {
            double angle=i*Math.PI/60;
            loop.add(point((float)(700+220*Math.sin(angle)),(float)(1570+60*Math.sin(2*angle)),
                130000+i*8,.15f+.8f*(float)(.5+.5*Math.cos(angle))));
        }
        strokes.add(new NotebookPage.Stroke(2,10,Color.BLACK,loop));
        return NotebookPage.empty().withSize(WIDTH,HEIGHT).withTemplate(template).withStrokes(strokes);
    }

    private static boolean reuseAndCancellation(NotebookPage page) {
        Path original = NotebookStrokeRenderer.path(page.strokes.get(0), () -> false);
        float[] before = original.approximate(.05f);
        NotebookStrokeRenderer.path(page.strokes.get(1), () -> false);
        if (!Arrays.equals(before,original.approximate(.05f))) throw new IllegalStateException("Reused pen changed old path");
        final int[] checks={0};
        boolean cancelled=false;
        try { NotebookStrokeRenderer.path(page.strokes.get(0), () -> ++checks[0]>=2); }
        catch (CancellationException expected) { cancelled=true; }
        if (!cancelled) throw new IllegalStateException("Native replay ignored cancellation");
        Path after = NotebookStrokeRenderer.path(page.strokes.get(0), () -> false);
        if (!Arrays.equals(before,after.approximate(.05f))) throw new IllegalStateException("Cancelled replay contaminated next stroke");
        return true;
    }

    private static boolean parallelReplay(NotebookPage page) throws Exception {
        List<float[]> expected = new ArrayList<>();
        for (NotebookPage.Stroke stroke : page.strokes)
            expected.add(NotebookStrokeRenderer.path(stroke, () -> false).approximate(.05f));
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch gate = new java.util.concurrent.CountDownLatch(1);
        try {
            List<java.util.concurrent.Future<Boolean>> jobs = new ArrayList<>();
            for (int worker = 0; worker < 2; worker++) jobs.add(pool.submit(() -> {
                gate.await();
                for (int pass = 0; pass < 10; pass++) for (int i = 0; i < page.strokes.size(); i++)
                    if (!Arrays.equals(expected.get(i), NotebookStrokeRenderer.path(page.strokes.get(i), () -> false).approximate(.05f)))
                        return false;
                return true;
            }));
            gate.countDown();
            for (java.util.concurrent.Future<Boolean> job : jobs) if (!job.get(30, java.util.concurrent.TimeUnit.SECONDS)) return false;
            return true;
        } finally { pool.shutdownNow(); }
    }

    /** Same physical native input, stored at several logical page scales and exported again. */
    private static JSONObject scaledReplay(File directory, NotebookPage nativePage, Bitmap reference) throws Exception {
        JSONArray checks = new JSONArray(); boolean passed = true;
        for (float captureScale : new float[]{.5f, .75f, 1.125f, 1.5f, 2f}) {
            List<NotebookPage.Stroke> logical = new ArrayList<>();
            for (NotebookPage.Stroke stroke : nativePage.strokes) {
                List<NotebookPage.Point> points = new ArrayList<>();
                for (NotebookPage.Point p : stroke.points)
                    points.add(point(p.x / captureScale, p.y / captureScale, p.time, p.pressure));
                logical.add(new NotebookPage.Stroke(stroke.penType, stroke.width / captureScale,
                    stroke.color, points, captureScale, stroke.width));
            }
            NotebookPage page = NotebookPage.empty().withSize(Math.round(WIDTH / captureScale),
                (int) Math.ceil(HEIGHT / captureScale)).withStrokes(logical);
            Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.WHITE); canvas.scale(captureScale, captureScale);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            for (NotebookPage.Stroke stroke : page.strokes) NotebookStrokeRenderer.draw(canvas, stroke, paint);
            JSONObject check = new JSONObject(); check.put("captureScale", captureScale);
            check.put("canvas", compare(reference, bitmap));
            File pdf = new File(directory, "capture-scale-" + captureScale + ".pdf");
            try (FileOutputStream output = new FileOutputStream(pdf)) {
                NotebookPdf.write(output, "Capture-space pressure regression", 1, number -> page, null,
                    NotebookStrokeRenderer::outline);
            }
            bitmap.eraseColor(Color.WHITE);
            try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
                    PdfRenderer renderer = new PdfRenderer(descriptor); PdfRenderer.Page pdfPage = renderer.openPage(0)) {
                Matrix matrix = new Matrix(); matrix.setScale((float) (WIDTH / 595.2756), (float) (WIDTH / 595.2756));
                pdfPage.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            }
            check.put("pdf", compare(reference, bitmap));
            passed &= check.getJSONObject("canvas").getInt("outsideOnePixel") == 0
                && check.getJSONObject("pdf").getInt("outsideOnePixel") == 0;
            checks.put(check); bitmap.recycle();
        }
        JSONObject result = new JSONObject(); result.put("checks", checks); result.put("passed", passed); return result;
    }

    private static boolean transparentTracks(NotebookPage page) throws Exception {
        List<NotebookPage.Stroke> tracks = new ArrayList<>();
        for (NotebookPage.Stroke stroke : page.strokes)
            tracks.add(new NotebookPage.Stroke(stroke.penType, stroke.width, Color.TRANSPARENT, stroke.points));
        Bitmap bitmap = paper(page);
        reference(new Canvas(bitmap), page.withStrokes(tracks));
        for (int y = 0; y < HEIGHT; y++) for (int x = 0; x < WIDTH; x++)
            if (bitmap.getPixel(x, y) != Color.WHITE) throw new IllegalStateException("Native transparent track repainted visible ink");
        bitmap.recycle(); return true;
    }

    private static Bitmap paper(NotebookPage page) {
        Bitmap bitmap=Bitmap.createBitmap(WIDTH,HEIGHT,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);canvas.drawColor(Color.WHITE);
        if(page.template==NotebookPage.Template.LINED) {
            Paint paint=new Paint();paint.setColor(0xffd2d2d2);paint.setStrokeWidth(1);
            for(int y=72;y<HEIGHT;y+=72)canvas.drawLine(0,y,WIDTH,y,paint);
        }
        return bitmap;
    }

    private static void reference(Canvas canvas, NotebookPage page) throws Exception {
        Class<?> creatorType=Class.forName("android.eink.view.edit.HWDefaultTrackCreator");
        Object creator=creatorType.getConstructor().newInstance();
        Method makePoint=creatorType.getMethod("createHWPoint",float.class,float.class,long.class,float.class);
        Method makeTrack=creatorType.getMethod("createHWTrack",int.class,float.class,int.class,long.class,List.class,RectF.class);
        ArrayList<Object> tracks=new ArrayList<>();
        for(NotebookPage.Stroke stroke:page.strokes) {
            ArrayList<Object> points=new ArrayList<>();
            for(NotebookPage.Point point:stroke.points)
                points.add(makePoint.invoke(creator,point.x,point.y,point.time,point.pressure));
            tracks.add(makeTrack.invoke(creator,stroke.penType,stroke.width,stroke.color,0L,points,new RectF(0,0,WIDTH,HEIGHT)));
        }
        Class.forName("android.eink.view.edit.HWTrackHelper").getMethod("drawTrackList",Canvas.class,List.class)
            .invoke(null,canvas,tracks);
    }

    private static JSONObject compare(Bitmap reference, Bitmap actual) throws Exception {
        int[] a=new int[WIDTH*HEIGHT],b=new int[WIDTH*HEIGHT];
        reference.getPixels(a,0,WIDTH,0,0,WIDTH,HEIGHT);actual.getPixels(b,0,WIDTH,0,0,WIDTH,HEIGHT);
        int exact=0,mask=0,outside=0,referenceInk=0;
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++) {
            int i=y*WIDTH+x;
            if(a[i]!=b[i])exact++;
            boolean first=black(a[i]),second=black(b[i]);
            if(first)referenceInk++;
            if(first!=second) {
                mask++;
                int[] target=first?b:a;
                boolean nearby=false;
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                    int xx=x+dx,yy=y+dy;
                    if(xx>=0&&xx<WIDTH&&yy>=0&&yy<HEIGHT&&black(target[yy*WIDTH+xx]))nearby=true;
                }
                if(!nearby)outside++;
            }
        }
        JSONObject result=new JSONObject();result.put("differentArgbPixels",exact);
        result.put("differentThresholdPixels",mask);result.put("outsideOnePixel",outside);
        result.put("nativeInkPixels",referenceInk);return result;
    }

    private static boolean black(int color) { return (color&255)<128; }
    private static int band(Bitmap bitmap,int x,int y) {
        int count=0;for(int yy=y-20;yy<=y+20;yy++)if(black(bitmap.getPixel(x,yy)))count++;return count;
    }
    private static void png(File directory,String name,Bitmap bitmap) throws Exception {
        try(FileOutputStream output=new FileOutputStream(new File(directory,name))) {
            if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,output))throw new IllegalStateException("PNG write failed");
        }
    }
    private static NotebookPage.Point point(float x,float y,long time,float pressure) {
        return new NotebookPage.Point(x,y,time,pressure);
    }
    private static NotebookPage.Stroke stroke(float width,NotebookPage.Point... points) {
        return new NotebookPage.Stroke(2,width,Color.BLACK,Arrays.asList(points));
    }
}
