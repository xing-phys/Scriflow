package org.eink.inkprobe.notebook;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import java.lang.reflect.Method;
import org.json.JSONArray;
import org.json.JSONObject;

/** Synthetic native raster only: never opens a display Surface or notebook storage. */
public final class NotebookToolBrushCheck {
    public static void main(String[] args) throws Exception {
        Class<?> config = Class.forName("android.eink.view.HandPenConfig");
        Class<?> api = Class.forName("android.eink.view.HandPen");
        Method board = api.getMethod("setCanvasBoard", Canvas.class);
        Method widthMethod = api.getMethod("setStrokeWidth", float.class);
        Method color = api.getMethod("setStrokeColor", int.class);
        Method input = api.getMethod("onTouchEvent", int.class, float.class, float.class, long.class, float.class);
        Method draw = api.getMethod("draw");
        Method track = api.getMethod("getTrack");
        Method path = Class.forName("android.eink.view.IPenTrack").getMethod("getPath");
        JSONArray checks = new JSONArray();
        boolean simpleMatches = true;
        for (String style : new String[]{"PEN_TYPE_SIMPLE", "PEN_TYPE_YUANZHUBI"}) {
            int type = config.getField(style).getInt(null);
            for (float width : new float[]{4, 9, 36, 120, 240}) for (float pressure : new float[]{.05f, .25f, .5f, 1}) {
                Object pen = api.getConstructor(int.class, boolean.class).newInstance(type, true);
                Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap); canvas.drawColor(Color.WHITE);
                board.invoke(pen, canvas); widthMethod.invoke(pen, width); color.invoke(pen, Color.BLACK);
                input.invoke(pen, MotionEvent.ACTION_DOWN, 32f, 256f, 1000L, pressure);
                for (int i = 0; i <= 100; i++) input.invoke(pen, MotionEvent.ACTION_MOVE, 32f + 4.4f * i, 256f, 1000L + i * 8, pressure);
                input.invoke(pen, MotionEvent.ACTION_UP, 472f, 256f, 1808L, 0f);
                draw.invoke(pen);
                int band = 0;
                for (int y = 0; y < 512; y++) if ((bitmap.getPixel(256, y) & 0xff) < 224) band++;
                RectF bounds = new RectF();
                Object nativeTrack = track.invoke(pen);
                if (nativeTrack != null) ((Path) path.invoke(nativeTrack)).computeBounds(bounds, true);
                JSONObject row = new JSONObject(); row.put("style", style); row.put("width", width); row.put("pressure", pressure);
                row.put("rasterBand", band); row.put("pathHeight", bounds.height()); checks.put(row);
                if (style.equals("PEN_TYPE_SIMPLE") && Math.abs(band - width) > 1) simpleMatches = false;
                bitmap.recycle();
            }
        }
        JSONObject result = new JSONObject(); result.put("syntheticOnly", true); result.put("userNotebookFilesOpened", false);
        result.put("simpleWidthMatchesAcrossPressure", simpleMatches); result.put("checks", checks);
        System.out.println(result.toString(2));
        if (!simpleMatches) throw new IllegalStateException("Simple brush does not match constant eraser diameter");
    }
}
