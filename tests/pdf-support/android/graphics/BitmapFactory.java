package android.graphics;

/** Host ImageIO adapter for the Android decode boundary exercised by the shipped PDFBox encoder. */
public final class BitmapFactory {
    public static Bitmap decodeStream(java.io.InputStream input) {
        try {
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(input);
            return image == null ? null : new Bitmap(image);
        } catch (java.io.IOException error) { return null; }
    }
}
