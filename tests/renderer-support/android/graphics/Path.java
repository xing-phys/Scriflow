package android.graphics;

import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.util.ArrayList;

/** Host-only shape fixture: deliberately injects the reported failed boolean operation. */
public final class Path {
    public enum FillType { WINDING, EVEN_ODD, INVERSE_WINDING, INVERSE_EVEN_ODD }
    public enum Direction { CW }
    public enum Op { INTERSECT }
    public static int booleanOperations;
    final Path2D.Float shape;
    private boolean empty = true;
    public Path() { shape = new Path2D.Float(); }
    public Path(Path other) { shape = (Path2D.Float) other.shape.clone(); empty = other.empty; }
    public void moveTo(float x, float y) { shape.moveTo(x, y); empty = false; }
    public void lineTo(float x, float y) { shape.lineTo(x, y); }
    public void close() { shape.closePath(); }
    public void addRect(float left, float top, float right, float bottom, Direction direction) {
        moveTo(left, top); lineTo(right, top); lineTo(right, bottom); lineTo(left, bottom); close();
    }
    public boolean isEmpty() { return empty; }
    public FillType getFillType() { return FillType.WINDING; }
    public void transform(Matrix matrix) { shape.transform(matrix.transform); }
    public boolean op(Path other, Op operation) { booleanOperations++; return false; }
    public float[] approximate(float error) {
        ArrayList<float[]> points = new ArrayList<>(); float[] coordinates = new float[6];
        float startX = 0, startY = 0;
        for (PathIterator iterator = shape.getPathIterator(null, error); !iterator.isDone(); iterator.next()) {
            int type = iterator.currentSegment(coordinates);
            if (type == PathIterator.SEG_MOVETO) { startX = coordinates[0]; startY = coordinates[1]; }
            points.add(type == PathIterator.SEG_CLOSE ? new float[]{startX, startY}
                : new float[]{coordinates[0], coordinates[1]});
        }
        float[] samples = new float[points.size() * 3];
        for (int i = 0; i < points.size(); i++) {
            samples[i * 3] = points.size() == 1 ? 0 : (float) i / (points.size() - 1);
            samples[i * 3 + 1] = points.get(i)[0]; samples[i * 3 + 2] = points.get(i)[1];
        }
        return samples;
    }
}
