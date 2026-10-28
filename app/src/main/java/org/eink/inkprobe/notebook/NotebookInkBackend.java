package org.eink.inkprobe.notebook;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import java.util.List;

/** Editor/device boundary. Live input belongs to the backend; durable pages belong to the core. */
public interface NotebookInkBackend {
    public enum Tool { PEN, ERASER, LASSO }

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
        default void onLassoGesture(List<NotebookPage.Point> path) { }
        /** Borrowed pointer stream while a closed lasso is being manipulated; AHW stays off. */
        default void onSelectionEvent(MotionEvent event) { }
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
    /** True only after the retained native surface has adopted these physical dimensions. */
    boolean hasGeometry(int width, int height);
    void setTool(Tool tool);
    void setSelectionMode(boolean selected);
    /** Fitted width in the current widget's page coordinates. Live drawing belongs to native AHW. */
    void setPenWidth(float width);
    void setEraserWidth(float width);
    void setOverlayBounds(Rect... bounds);
    /** Paused: repost existing paper/tracks exposed by an overlay, without repainting or clearing them. */
    void redrawAfterOverlayChange();
    void setPage(NotebookPage page);
    void setPaperRenderer(PaperRenderer renderer);
    boolean updateViewport(NotebookPage page, PaperRenderer renderer);
    boolean showScrollPreview(Bitmap preview);
    List<NotebookPage.Stroke> snapshotNativeStrokes() throws Exception;
}
