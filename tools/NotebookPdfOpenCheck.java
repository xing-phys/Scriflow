package org.eink.inkprobe.notebook;

import android.graphics.Bitmap;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Android-native smoke check using disposable host PDF fixtures; no handwriting API required. */
public final class NotebookPdfOpenCheck {
    public static void main(String[] args) throws Exception {
        File directory = new File(args[0]);
        JSONObject result = new JSONObject(); JSONArray files = new JSONArray();
        for (String name : new String[]{"source.pdf", "encrypted.pdf", "encrypted-aes-128.pdf", "encrypted-aes-256.pdf", "scan-384-mib.pdf"}) {
            File file = new File(directory, name); long started = System.nanoTime();
            NotebookPdfAsset asset = NotebookPdfEngine.inspect(file);
            if (asset.pages.isEmpty()) throw new AssertionError("Missing PDF pages: " + name);
            int rendered = 0, nonwhite = 0;
            for (int page : new int[]{1, asset.pages.size()}) {
                Bitmap bitmap = NotebookPdfEngine.render(file, page, 256, 256);
                if (bitmap.getWidth() != 256 || bitmap.getHeight() != 256) throw new AssertionError("Wrong preview size");
                int[] pixels = new int[256 * 256]; bitmap.getPixels(pixels, 0, 256, 0, 0, 256, 256);
                int painted = 0; for (int pixel : pixels) if (pixel != -1) painted++;
                if (painted == 0) throw new AssertionError("Missing rendered PDF content: " + name);
                nonwhite += painted;
                bitmap.recycle(); rendered++;
                NotebookPdfEngine.links(file, page);
            }
            files.put(new JSONObject().put("file", name).put("bytes", file.length()).put("pages", asset.pages.size())
                .put("nativePreviews", rendered).put("nonwhitePixels", nonwhite)
                .put("elapsedMs", (System.nanoTime() - started) / 1_000_000));
            NotebookPdfEngine.closeNavigation();
        }
        result.put("passed", true).put("files", files).put("maxHeapBytes", Runtime.getRuntime().maxMemory());
        try (FileOutputStream output = new FileOutputStream(new File(directory, "native-pdf-result.json"))) {
            output.write(result.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        System.out.println(result.toString(2));
    }
}
