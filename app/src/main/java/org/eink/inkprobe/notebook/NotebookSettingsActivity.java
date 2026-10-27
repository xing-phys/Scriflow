package org.eink.inkprobe.notebook;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.EnumMap;
import java.util.Map;

/** Compact, immediately saved preferences shared by every notebook screen. */
public final class NotebookSettingsActivity extends NotebookDisplayActivity {
    private NotebookButton pinchChoice, screenChoice;
    private final Map<NotebookSettings.Orientation, NotebookButton> choices = new EnumMap<>(NotebookSettings.Orientation.class);
    private final Map<NotebookPenConfig.WidthMode, NotebookButton> widthChoices = new EnumMap<>(NotebookPenConfig.WidthMode.class);
    private final Map<NotebookSettings.BookshelfView, NotebookButton> shelfChoices = new EnumMap<>(NotebookSettings.BookshelfView.class);

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setWindowAnimations(0);
        LinearLayout root = column(); root.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout header = row();
        header.addView(label("Settings", 17), new LinearLayout.LayoutParams(0, dp(36), 1));
        header.addView(button("Done", this::onBackPressed), new LinearLayout.LayoutParams(dp(56), dp(36)));
        header.addView(deviceSettingsButton(), new LinearLayout.LayoutParams(-2, dp(32)));
        root.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(Color.WHITE);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout body = column();

        section(body, "Screen orientation");
        LinearLayout orientationRow = row();
        for (NotebookSettings.Orientation orientation : NotebookSettings.Orientation.values()) {
            NotebookButton choice = button("", () -> {
                NotebookSettings.setOrientation(this, orientation); refreshDisplayOrientation(); updateChoices();
            });
            choices.put(orientation, choice); orientationRow.addView(choice, new LinearLayout.LayoutParams(0, dp(36), 1));
        }
        body.addView(orientationRow);
        hint(body, canUseOrientationSensor() ? "Sensor mode keeps the current direction when flat and waits until writing ends."
            : "No orientation sensor detected; choose Portrait or Landscape.");

        section(body, "Bookshelf");
        LinearLayout viewRow = row();
        for (NotebookSettings.BookshelfView view : NotebookSettings.BookshelfView.values()) {
            NotebookButton choice = button("", () -> { NotebookSettings.setBookshelfView(this, view); updateChoices(); });
            shelfChoices.put(view, choice); viewRow.addView(choice, new LinearLayout.LayoutParams(0, dp(34), 1));
        }
        body.addView(viewRow);
        hint(body, "List: one column in portrait, two in landscape. Thumbnail grid: choose rows and columns per screen.");
        shelfGridRow(body, false); shelfGridRow(body, true);

        section(body, "Page overview");
        hint(body, "Number of page thumbnails in each row.");
        LinearLayout pageRow = row();
        pageRow.addView(label("Portrait", 13), new LinearLayout.LayoutParams(0, dp(36), 1));
        pageRow.addView(number("pageColumns", false, NotebookSettings.pageColumns(this, false)), new LinearLayout.LayoutParams(dp(104), dp(34)));
        TextView landscape = label("Landscape", 13); landscape.setPadding(dp(12), 0, 0, 0);
        pageRow.addView(landscape, new LinearLayout.LayoutParams(0, dp(36), 1));
        pageRow.addView(number("pageColumns", true, NotebookSettings.pageColumns(this, true)), new LinearLayout.LayoutParams(dp(104), dp(34)));
        body.addView(pageRow);

        section(body, "Screen");
        screenChoice = button("", () -> {
            NotebookSettings.setKeepScreenOn(this, !NotebookSettings.keepScreenOn(this)); refreshScreenOn(); updateChoices();
        });
        body.addView(screenChoice, new LinearLayout.LayoutParams(-1, dp(34)));
        hint(body, "Keep the screen on while Scriflow is open. The cover, Power button, and system sleep remain in control.");

        section(body, "Touch gestures");
        pinchChoice = button("", () -> {
            NotebookSettings.setPinchZoomEnabled(this, !NotebookSettings.pinchZoomEnabled(this)); updateChoices();
        });
        body.addView(pinchChoice, new LinearLayout.LayoutParams(-1, dp(34)));
        hint(body, "Lift the pen away before using two fingers. Zoom presets also work when pinch zoom is off.");

        section(body, "Pen width when zooming");
        for (NotebookPenConfig.WidthMode mode : NotebookPenConfig.WidthMode.values()) {
            NotebookButton choice = button("", () -> { NotebookSettings.setPenWidthMode(this, mode); updateChoices(); });
            widthChoices.put(mode, choice); body.addView(choice, new LinearLayout.LayoutParams(-1, dp(32)));
            hint(body, mode == NotebookPenConfig.WidthMode.PAGE
                ? "Fits the nib to the page. Native pressure response may vary with zoom."
                : "Keeps the nib's screen width while writing. Saved strokes scale with the page.");
        }
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root); updateChoices();
    }

    private void shelfGridRow(LinearLayout body, boolean landscape) {
        LinearLayout line = row();
        line.addView(label(landscape ? "Landscape" : "Portrait", 13), new LinearLayout.LayoutParams(0, dp(38), 1));
        line.addView(label("Rows ", 12));
        line.addView(number("shelfRows", landscape, NotebookSettings.bookshelfRows(this, landscape)), new LinearLayout.LayoutParams(dp(104), dp(34)));
        line.addView(label("  Columns ", 12));
        line.addView(number("shelfColumns", landscape, NotebookSettings.bookshelfColumns(this, landscape)), new LinearLayout.LayoutParams(dp(104), dp(34)));
        body.addView(line);
    }

    private LinearLayout number(String kind, boolean landscape, int value) {
        final int minimum = kind.equals("pageColumns") ? 2 : 1;
        final int[] current = {value};
        String name = (landscape ? "Landscape " : "Portrait ")
            + (kind.equals("shelfRows") ? "bookshelf rows" : kind.equals("shelfColumns") ? "bookshelf columns" : "page overview columns");
        LinearLayout control = row(); control.setPadding(dp(1), dp(1), dp(1), dp(1));
        control.setBackground(NotebookUi.border(getResources().getDisplayMetrics().density));
        TextView number = label(String.valueOf(value), 13); number.setGravity(Gravity.CENTER);
        NotebookButton decrease = button("−", () -> { });
        NotebookButton increase = button("+", () -> { });
        decrease.setTextSize(18); increase.setTextSize(18);
        decrease.setGravity(Gravity.CENTER); increase.setGravity(Gravity.CENTER);
        decrease.setContentDescription("Decrease " + name); increase.setContentDescription("Increase " + name);
        Runnable update = () -> {
            number.setText(String.valueOf(current[0])); number.setContentDescription(name + ": " + current[0]);
            decrease.setAvailable(current[0] > minimum); increase.setAvailable(current[0] < 12);
        };
        decrease.setOnClickListener(view -> {
            if (current[0] <= minimum) return;
            NotebookSettings.setGridPreference(this, kind, landscape, --current[0]); update.run();
        });
        increase.setOnClickListener(view -> {
            if (current[0] >= 12) return;
            NotebookSettings.setGridPreference(this, kind, landscape, ++current[0]); update.run();
        });
        control.addView(decrease, new LinearLayout.LayoutParams(dp(32), -1));
        control.addView(number, new LinearLayout.LayoutParams(0, -1, 1));
        control.addView(increase, new LinearLayout.LayoutParams(dp(32), -1));
        update.run(); return control;
    }

    @Override protected void onResume() { super.onResume(); updateChoices(); }

    private void updateChoices() {
        if (pinchChoice != null) pinchChoice.setText("Two-finger zoom: " + (NotebookSettings.pinchZoomEnabled(this) ? "On" : "Off"));
        if (screenChoice != null) screenChoice.setText("Keep screen on: " + (NotebookSettings.keepScreenOn(this) ? "On" : "Off"));
        for (Map.Entry<NotebookSettings.Orientation, NotebookButton> entry : choices.entrySet())
            choose(entry.getValue(), entry.getKey() == NotebookSettings.orientation(this),
                entry.getKey() == NotebookSettings.Orientation.SENSOR ? "Sensor" : entry.getKey().label);
        for (Map.Entry<NotebookSettings.BookshelfView, NotebookButton> entry : shelfChoices.entrySet())
            choose(entry.getValue(), entry.getKey() == NotebookSettings.bookshelfView(this),
                entry.getKey() == NotebookSettings.BookshelfView.LIST ? "List" : "Thumbnails");
        for (Map.Entry<NotebookPenConfig.WidthMode, NotebookButton> entry : widthChoices.entrySet())
            choose(entry.getValue(), entry.getKey() == NotebookSettings.penWidthMode(this),
                entry.getKey() == NotebookPenConfig.WidthMode.PAGE ? "Fixed width on page (default)" : "Fixed width on screen");
    }

    private void choose(NotebookButton button, boolean selected, String text) {
        button.setText((selected ? "● " : "○ ") + text); button.setSelected(selected);
        button.setContentDescription(text + (selected ? ", selected" : ""));
    }
    private void section(LinearLayout body, String text) {
        TextView heading = label(text, 14); heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, dp(12), 0, dp(3)); body.addView(heading);
    }
    private void hint(LinearLayout body, String text) {
        TextView hint = label(text, 12); hint.setPadding(0, dp(2), 0, dp(5)); body.addView(hint);
    }
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(Color.WHITE); return layout;
    }
    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this); layout.setGravity(Gravity.CENTER_VERTICAL); return layout;
    }
    private NotebookButton button(String text, Runnable action) {
        NotebookButton button = new NotebookButton(this); button.setText(text); button.setTextSize(13);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL); button.setOnClickListener(view -> action.run()); return button;
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(Color.BLACK);
        view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onBackPressed() { super.onBackPressed(); overridePendingTransition(0, 0); }
}
