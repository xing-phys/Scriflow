package org.eink.inkprobe.notebook;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Explicit debug-intent check; never runs during ordinary notebook use. */
final class NotebookVerification {
    static void write(Context context, NotebookPage expected, List<NotebookPage.Stroke> actual) {
        final File directory = new File(context.getFilesDir(), "notebook-verification");
        final long started = System.currentTimeMillis();
        new Thread(() -> {
            try {
                JSONObject result = new JSONObject();
                result.put("startedAtMillis", started);
                result.put("template", expected.template.name());
                result.put("width", expected.width); result.put("height", expected.height);
                result.put("savedStrokeCount", expected.strokes.size());
                result.put("nativeStrokeCount", actual.size());
                result.put("savedPointCount", expected.pointCount);
                boolean match = actual.size() == expected.strokes.size();
                int actualPoints = 0;
                for (int i = 0; i < actual.size(); i++) {
                    NotebookPage.Stroke restored = actual.get(i);
                    actualPoints += restored.points.size();
                    if (i >= expected.strokes.size()) continue;
                    NotebookPage.Stroke saved = expected.strokes.get(i);
                    match &= saved.penType == restored.penType && saved.color == restored.color
                        && near(saved.width, restored.width) && saved.points.size() == restored.points.size();
                    for (int j = 0; j < Math.min(saved.points.size(), restored.points.size()); j++) {
                        NotebookPage.Point p = saved.points.get(j), q = restored.points.get(j);
                        match &= near(p.x, q.x) && near(p.y, q.y) && p.time == q.time
                            && near(p.pressure, q.pressure);
                    }
                }
                result.put("nativePointCount", actualPoints);
                result.put("coordinatesStylePressureTimeMatch", match);
                result.put("passed", match);
                if (!directory.isDirectory() && !directory.mkdirs()) return;
                try (FileOutputStream out = new FileOutputStream(new File(directory, "result.json"))) {
                    out.write(result.toString().getBytes(StandardCharsets.UTF_8)); out.getFD().sync();
                }
            } catch (Exception ignored) { /* This optional check never changes the document or ink state. */ }
        }, "notebook-verify").start();
    }
    private static boolean near(float a, float b) { return Math.abs(a - b) <= .001f; }
}
