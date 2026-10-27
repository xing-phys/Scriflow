package android.eink.view;

/** Host-only subset of the firmware surface methods used by page-coordinate writing. */
public interface ISurface {
    void save();
    void scale(float x, float y);
    void restore();
    void postFrameBuffer(int left, int top, int right, int bottom, int mode);
}
