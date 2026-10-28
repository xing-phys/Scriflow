package android.graphics;

/** Host-only bitmap contract using ImageIO pixels; this is not Android/device decoder verification. */
public final class Bitmap {
    public enum Config { ALPHA_8, ARGB_8888, RGB_565 }
    public static final int DENSITY_NONE = 0;
    private final java.awt.image.BufferedImage image;
    Bitmap(java.awt.image.BufferedImage image) { this.image = image; }
    public static Bitmap createBitmap(int width, int height, Config config) {
        return new Bitmap(new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB));
    }
    public int getWidth() { return image.getWidth(); }
    public int getHeight() { return image.getHeight(); }
    public Config getConfig() { return Config.ARGB_8888; }
    public boolean hasAlpha() { return image.getColorModel().hasAlpha(); }
    public void getPixels(int[] pixels, int offset, int stride, int x, int y, int width, int height) {
        image.getRGB(x, y, width, height, pixels, offset, stride);
    }
    public void setDensity(int density) { }
    public void recycle() { image.flush(); }
    public void eraseColor(int color) { throw new UnsupportedOperationException("Native PDF rasterization requires a device"); }
}
