package org.eink.inkprobe.notebook;

import android.graphics.*;
import java.io.*;
import java.util.List;

/** Worker-only selection snapshots; no notebook mutation or automatic provider request. */
final class NotebookAiSelection {
    static NotebookAiAttachment ink(List<NotebookPage.Stroke> strokes) throws IOException {
        NotebookSelectionExport geometry=new NotebookSelectionExport(strokes,NotebookStrokeRenderer::outline);
        float scale=Math.min(2,2048/Math.max(geometry.width,geometry.height));
        Bitmap bitmap=Bitmap.createBitmap(Math.max(1,(int)Math.ceil(geometry.width*scale)),
            Math.max(1,(int)Math.ceil(geometry.height*scale)),Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas=new Canvas(bitmap); canvas.drawColor(Color.WHITE); canvas.scale(scale,scale); canvas.translate(-geometry.left,-geometry.top);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            for (NotebookPage.Stroke stroke:strokes) NotebookStrokeRenderer.draw(canvas,stroke,paint);
            return pack("Handwriting selection",bitmap);
        } finally { bitmap.recycle(); }
    }
    static NotebookAiAttachment figure(NotebookFigure figure) throws IOException {
        if (figure.byteCount()<=NotebookAiAttachment.MAX_BYTES) return new NotebookAiAttachment("Selected image",figure.png());
        return pack("Selected image",NotebookImages.bitmap(figure));
    }
    private static NotebookAiAttachment pack(String name,Bitmap original) throws IOException {
        Bitmap current=original;
        try {
            while (true) {
                ByteArrayOutputStream output=new ByteArrayOutputStream();
                if (!current.compress(Bitmap.CompressFormat.PNG,100,output)) throw new IOException("Could not prepare AI image");
                if (output.size()<=NotebookAiAttachment.MAX_BYTES) return new NotebookAiAttachment(name,output.toByteArray());
                if (current.getWidth()==1&&current.getHeight()==1) throw new IOException("AI image exceeds its size limit");
                Bitmap smaller=Bitmap.createScaledBitmap(current,Math.max(1,current.getWidth()/2),Math.max(1,current.getHeight()/2),true);
                if (current!=original) current.recycle(); current=smaller;
            }
        } finally { if (current!=original) current.recycle(); }
    }
    private NotebookAiSelection() { }
}
