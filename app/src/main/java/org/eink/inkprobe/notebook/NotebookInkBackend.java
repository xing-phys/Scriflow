package org.eink.inkprobe.notebook;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import java.util.List;

/** Editor/device boundary. Live input belongs to the backend; durable pages belong to the core. */
public interface NotebookInkBackend {
    public enum Tool { PEN, ERASER }

    /** Paints only the opaque paper/background of the current physical viewport. */
    public interface PaperRenderer {
        void draw(Canvas canvas, int width, int height);
    }

    public interface Listener {
        void onGeometryReady(int width, int height);
        void onStrokeComplete(NotebookPage.Stroke stroke);
        void onPenStateChanged(boolean down);
        void onReady();
        void onError(String message);
        default void onEraseGesture(List<NotebookPage.Point> path, float radius) { }
        /** Synchronous borrowed event; callers must not retain it after this callback. */
        default void onFingerEvent(MotionEvent event) { }
    }


    View view();
    void start();
    void stop();
    void release();
    void pauseForEdit();
    boolean isReady();
    boolean isPenDown();
    void setTool(Tool tool);
    void setPenWidth(float width);
    void setEraserWidth(float width);
    void setOverlayBounds(Rect bounds);
    void setPage(NotebookPage page);
    void setPaperRenderer(PaperRenderer renderer);
    boolean updateViewport(NotebookPage page, PaperRenderer renderer);
    boolean showScrollPreview(Bitmap preview);
    List<NotebookPage.Stroke> snapshotNativeStrokes() throws Exception;
}
