package android.eink.view;

/** Host-only reflection interface matching the firmware method used by the renderer. */
public interface IPenTrack {
    android.graphics.Path getPath();
    android.graphics.RectF getBounds();
}
