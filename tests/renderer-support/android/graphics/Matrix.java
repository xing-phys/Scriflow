package android.graphics;

/** Host-only test double; does not verify Android or Huawei rasterization. */
public final class Matrix {
    final java.awt.geom.AffineTransform transform = new java.awt.geom.AffineTransform();
    public void setScale(float x, float y) { transform.setToScale(x, y); }
}
