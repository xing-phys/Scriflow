package android.graphics;

/** Host-only renderer contract fixture. */
public final class Paint {
    public static final int ANTI_ALIAS_FLAG = 1;
    public enum Style { FILL }
    int color = 0xff000000;
    public Paint() { }
    public Paint(int flags) { }
    public void setStyle(Style style) { }
    public void setColor(int color) { this.color = color; }
}
