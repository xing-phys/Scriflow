package org.eink.inkprobe.notebook;

import java.util.List;

/** Width presets in stored page units, plus immutable style captured at pen-DOWN. */
final class NotebookPenConfig {
    static final class Preset {
        final String name;
        final float width;
        private Preset(String name, float width) { this.name = name; this.width = width; }
    }

    private static final Preset[] PRESETS = {
        new Preset("Extra fine", 1f), new Preset("Fine", 2f), new Preset("Medium", 4f),
        new Preset("Broad", 6f), new Preset("Extra broad", 10f)
    };

    private NotebookPenConfig() { }
    static int count() { return PRESETS.length; }
    static Preset preset(int index) { return PRESETS[index]; }
    static Preset defaultPreset() { return PRESETS[2]; }
    static Preset restore(float width) {
        for (Preset preset : PRESETS) if (preset.width == width) return preset;
        return defaultPreset();
    }

    /** One style object belongs to a stroke even if the next stroke uses another width. */
    static final class Style {
        final int penType, color;
        final float width;
        Style(int penType, float width, int color) {
            if (penType < 0 || penType > 255 || !NotebookPage.finite(width)
                    || width <= 0 || width > 1024) throw new IllegalArgumentException("Invalid pen style");
            this.penType = penType; this.width = width; this.color = color;
        }
        NotebookPage.Stroke stroke(List<NotebookPage.Point> points) {
            return new NotebookPage.Stroke(penType, width, color, points);
        }
    }
}
