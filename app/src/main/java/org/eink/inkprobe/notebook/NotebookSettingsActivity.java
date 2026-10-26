package org.eink.inkprobe.notebook;

import android.app.Activity;
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

/** Static, immediately saved display choices shared by every notebook screen. */
public final class NotebookSettingsActivity extends Activity {
    private final Map<NotebookSettings.Orientation, NotebookButton> choices =
        new EnumMap<>(NotebookSettings.Orientation.class);

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        NotebookSettings.applyOrientation(this);
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
                updateChoices();
            });
            choices.put(orientation, button);
            body.addView(button, new LinearLayout.LayoutParams(-1, dp(68)));
            View divider = new View(this); divider.setBackgroundColor(0xffbbbbbb);
            body.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        }
        TextView sensor = label("Follow device sensor rotates with the tablet, including when the system rotation lock is on. Rotation waits until you lift the pen.", 15);
        sensor.setPadding(0, dp(20), 0, dp(24));
        body.addView(sensor, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        updateChoices();
    }

    @Override protected void onResume() {
        super.onResume();
        NotebookSettings.applyOrientation(this);
        updateChoices();
    }

    private void updateChoices() {
        NotebookSettings.Orientation selected = NotebookSettings.orientation(this);
        for (Map.Entry<NotebookSettings.Orientation, NotebookButton> entry : choices.entrySet()) {
            boolean checked = selected == entry.getKey();
            entry.getValue().setText((checked ? "●  " : "○  ") + entry.getKey().label);
            entry.getValue().setSelected(checked);
            entry.getValue().setContentDescription(entry.getKey().label + (checked ? ", selected" : ""));
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
