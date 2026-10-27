package org.eink.inkprobe.notebook;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.EnumMap;
import java.util.Map;

/** Static, immediately saved preferences shared by every notebook screen. */
public final class NotebookSettingsActivity extends NotebookDisplayActivity {
    private NotebookButton pinchChoice;
    private final Map<NotebookSettings.Orientation, NotebookButton> choices =
        new EnumMap<>(NotebookSettings.Orientation.class);
    private final Map<NotebookPenConfig.WidthMode, NotebookButton> widthChoices =
        new EnumMap<>(NotebookPenConfig.WidthMode.class);

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setWindowAnimations(0);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(24), dp(16), dp(24), dp(16));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(label("Settings", 26), new LinearLayout.LayoutParams(0, dp(60), 1));
        NotebookButton done = new NotebookButton(this);
        done.setText("Done"); done.setTextSize(18);
        done.setOnClickListener(view -> onBackPressed());
        header.addView(done, new LinearLayout.LayoutParams(dp(96), dp(60)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        TextView title = label("Screen orientation", 22);
        title.setPadding(0, dp(20), 0, dp(12));
        body.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView description = label("Choose how the app rotates. Your choice applies to the library, notes, backups, and PDF export.", 16);
        description.setPadding(0, 0, 0, dp(20));
        body.addView(description, new LinearLayout.LayoutParams(-1, -2));

        for (NotebookSettings.Orientation orientation : NotebookSettings.Orientation.values()) {
            NotebookButton button = new NotebookButton(this);
            button.setTextSize(20);
            button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            button.setPadding(dp(12), 0, dp(12), 0);
            button.setOnClickListener(view -> {
                NotebookSettings.setOrientation(this, orientation);
                refreshDisplayOrientation();
                updateChoices();
            });
            choices.put(orientation, button);
            body.addView(button, new LinearLayout.LayoutParams(-1, dp(68)));
            View divider = new View(this); divider.setBackgroundColor(0xffbbbbbb);
            body.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        TextView sensor = label(canUseOrientationSensor()
            ? "Sensor detected. Hold the tablet at an angle to rotate; when it lies flat, the current direction stays. Rotation waits until you finish writing. No extra permission is needed."
            : "Automatic rotation is unavailable: no orientation sensor was detected. Choose Portrait or Landscape instead.", 15);
        sensor.setPadding(0, dp(20), 0, dp(24));
        body.addView(sensor, new LinearLayout.LayoutParams(-1, -2));
        body.addView(label("Touch gestures", 22), new LinearLayout.LayoutParams(-1, -2));
        pinchChoice = new NotebookButton(this); pinchChoice.setTextSize(20);
        pinchChoice.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        pinchChoice.setOnClickListener(view -> {
            NotebookSettings.setPinchZoomEnabled(this, !NotebookSettings.pinchZoomEnabled(this));
            updateChoices();
        });
        body.addView(pinchChoice, new LinearLayout.LayoutParams(-1, dp(68)));
        TextView palms = label("Finger navigation pauses while the pen is nearby and briefly after writing. For zoom, lift the pen away, place two fingers together, then pinch. You can turn pinch zoom off and use the percentage menu instead.", 15);
        palms.setPadding(0, dp(12), 0, dp(24));
        body.addView(palms, new LinearLayout.LayoutParams(-1, -2));
        body.addView(label("Pen width when zooming", 22), new LinearLayout.LayoutParams(-1, -2));
        for (NotebookPenConfig.WidthMode mode : NotebookPenConfig.WidthMode.values()) {
            NotebookButton button = new NotebookButton(this);
            button.setTextSize(20);
            button.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            button.setPadding(dp(12), 0, dp(12), 0);
            button.setOnClickListener(view -> {
                NotebookSettings.setPenWidthMode(this, mode);
                updateChoices();
            });
            widthChoices.put(mode, button);
            body.addView(button, new LinearLayout.LayoutParams(-1, dp(68)));
            TextView explanation = label(mode == NotebookPenConfig.WidthMode.PAGE
                ? "New strokes keep the same width on the page at every zoom. At the same zoom, they match earlier strokes drawn with the same pen width."
                : "The pen keeps the same width on the screen while you write at any zoom. Completed strokes scale with the page.", 15);
            explanation.setPadding(dp(12), 0, dp(12), dp(20));
            body.addView(explanation, new LinearLayout.LayoutParams(-1, -2));
        }
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        updateChoices();
    }

    @Override protected void onResume() {
        super.onResume();
        updateChoices();
    }

    private void updateChoices() {
        if (pinchChoice != null) {
            boolean enabled = NotebookSettings.pinchZoomEnabled(this);
            pinchChoice.setText("Two-finger zoom: " + (enabled ? "On" : "Off"));
            pinchChoice.setSelected(enabled);
            pinchChoice.setContentDescription("Two-finger zoom " + (enabled ? "on" : "off") + "; tap to change");
        }
        NotebookSettings.Orientation selected = NotebookSettings.orientation(this);
        for (Map.Entry<NotebookSettings.Orientation, NotebookButton> entry : choices.entrySet()) {
            boolean checked = selected == entry.getKey();
            entry.getValue().setText((checked ? "●  " : "○  ") + entry.getKey().label);
            entry.getValue().setSelected(checked);
            entry.getValue().setContentDescription(entry.getKey().label + (checked ? ", selected" : ""));
        }
        NotebookPenConfig.WidthMode widthMode = NotebookSettings.penWidthMode(this);
        for (Map.Entry<NotebookPenConfig.WidthMode, NotebookButton> entry : widthChoices.entrySet()) {
            boolean checked = widthMode == entry.getKey();
            String text = entry.getKey() == NotebookPenConfig.WidthMode.PAGE
                ? "Fixed width on page (default)" : "Fixed width on screen";
            entry.getValue().setText((checked ? "●  " : "○  ") + text);
            entry.getValue().setSelected(checked);
            entry.getValue().setContentDescription(text + (checked ? ", selected" : ""));
        }
    }

    private TextView label(String text, int size) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(size); view.setTextColor(Color.BLACK);
        return view;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, 0);
    }
}
