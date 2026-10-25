package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Physical preview damage, excluding the floating controls from direct framebuffer updates. */
final class NotebookEraserDamage {
    static final int COLOR = 0xffaaaaaa;
    static final class Box {
        final int left, top, right, bottom;
        Box(int left, int top, int right, int bottom) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
        }
    }
    private NotebookEraserDamage() { }

    static Box sweep(float ax, float ay, float bx, float by, float diameter, int width, int height) {
        if (!NotebookPage.finite(ax) || !NotebookPage.finite(ay) || !NotebookPage.finite(bx)
                || !NotebookPage.finite(by) || !NotebookPage.finite(diameter) || diameter <= 0
                || width <= 0 || height <= 0) throw new IllegalArgumentException("Invalid eraser damage");
        double radius = diameter / 2.0 + 2; // Round caps and antialias coverage.
        int left = (int) Math.max(0, Math.floor(Math.min(ax, bx) - radius));
        int top = (int) Math.max(0, Math.floor(Math.min(ay, by) - radius));
        int right = (int) Math.min(width, Math.ceil(Math.max(ax, bx) + radius));
        int bottom = (int) Math.min(height, Math.ceil(Math.max(ay, by) + radius));
        return left < right && top < bottom ? new Box(left, top, right, bottom) : null;
    }

    static List<Box> excluding(Box dirty, Box hole) {
        if (dirty == null) return Collections.emptyList();
        if (hole == null || hole.right <= dirty.left || hole.left >= dirty.right
                || hole.bottom <= dirty.top || hole.top >= dirty.bottom)
            return Collections.singletonList(dirty);
        int left = Math.max(dirty.left, hole.left), right = Math.min(dirty.right, hole.right);
        int top = Math.max(dirty.top, hole.top), bottom = Math.min(dirty.bottom, hole.bottom);
        List<Box> result = new ArrayList<>(4);
        add(result, dirty.left, dirty.top, dirty.right, top);
        add(result, dirty.left, bottom, dirty.right, dirty.bottom);
        add(result, dirty.left, top, left, bottom);
        add(result, right, top, dirty.right, bottom);
        return result;
    }
    private static void add(List<Box> result, int left, int top, int right, int bottom) {
        if (left < right && top < bottom) result.add(new Box(left, top, right, bottom));
    }
}
