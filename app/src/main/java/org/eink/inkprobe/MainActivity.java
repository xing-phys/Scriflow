package org.eink.inkprobe;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String TAG = "InkProbe";
    private static final int NOTES_UI_FLAGS = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
    private final Trace trace = new Trace();
    private final List<String> events = new ArrayList<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private FrameLayout paper;
    private TextView status;
    private Button zButton;
    private Button fullscreenButton;
    private Button notesUiButton;
    private Button awakeButton;
    private HuaweiInk ink;
    private String mode = "Android";
    private boolean onTop;
    private boolean fullscreen;
    private boolean notesUi;
    private boolean keepAwake = true;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) {
            fullscreen = saved.getBoolean("fullscreen", false);
            notesUi = saved.getBoolean("notesUi", false);
            keepAwake = saved.getBoolean("keepAwake", true);
            onTop = saved.getBoolean("onTop", false);
            mode = saved.getString("mode", "Android");
        }
        if (fullscreen) getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (keepAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(notesUi ? NOTES_UI_FLAGS : 0);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.WHITE);
        TextView title = new TextView(this);
        title.setText("Ink Probe 0.6 · MatePad Paper"); title.setTextSize(22); title.setTextColor(Color.BLACK);
        title.setPadding(16, 12, 12, 6); root.addView(title);
        LinearLayout modes = row(root);
        button(modes, "Android", () -> select("Android"));
        button(modes, "HW live", () -> select("Huawei"));
        button(modes, "Native edit", () -> select("Native"));
        button(modes, "Replay", () -> {
            if ("Native".equals(mode)) report("Native edit owns its tracks. Replay applies to HW live / Android points.");
            else select("Replay");
        });
        LinearLayout actions = row(root);
        button(actions, "Clear", this::clear);
        button(actions, "Inspect", this::inspect);
        button(actions, "Save log", this::saveLog);
        button(actions, onTop ? "Z: top" : "Z: default", () -> {
            onTop = !onTop;
            ((Button) actions.getChildAt(3)).setText(onTop ? "Z: top" : "Z: default");
            report("Z order changed to " + onTop); select(mode);
        });
        zButton = (Button) actions.getChildAt(3);
        LinearLayout windowActions = row(root);
        button(windowActions, fullscreen ? "Fullscreen: on" : "Fullscreen: off", this::toggleFullscreen);
        fullscreenButton = (Button) windowActions.getChildAt(0);
        button(windowActions, notesUi ? "Notes UI: on" : "Notes UI: off", this::toggleNotesUi);
        notesUiButton = (Button) windowActions.getChildAt(1);
        button(windowActions, keepAwake ? "Awake: on" : "Awake: off", this::toggleAwake);
        awakeButton = (Button) windowActions.getChildAt(2);
        status = new TextView(this); status.setTextSize(14); status.setTextColor(Color.BLACK);
        status.setPadding(16, 4, 16, 8); status.setMinLines(3); status.setMaxLines(4);
        root.addView(status, new LinearLayout.LayoutParams(-1, (int) (88 * getResources().getDisplayMetrics().density)));
        paper = new FrameLayout(this); paper.setBackgroundColor(Color.WHITE);
        root.addView(paper, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        report("Device " + Build.MODEL + "; " + Build.DISPLAY + "; API " + Build.VERSION.SDK_INT);
        try { report("Declared display policy: " + DisplayPolicy.json(this)); }
        catch (Exception e) { report("Display policy unavailable: " + e); }
        select(mode);
    }
    private LinearLayout row(LinearLayout root) {
        LinearLayout r = new LinearLayout(this); root.addView(r); return r;
    }
    private void button(LinearLayout row, String label, Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14);
        b.setOnClickListener(v -> action.run());
        row.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
    }
    private void report(String s) {
        Log.i(TAG, s);
        synchronized (events) { events.add(System.currentTimeMillis() + " " + s); }
        // Status changes happen on explicit actions and setup, never on pen-point callbacks.
        runOnUiThread(() -> {
            if (status != null) status.setText(mode + " · "
                + ("Native".equals(mode) ? "native editor owns tracks" : trace.summary()) + "\n" + s);
        });
    }
    private void select(String next) {
        if (ink != null) { ink.release(); ink = null; }
        trace.cancel(); paper.removeAllViews(); mode = next;
        zButton.setEnabled("Huawei".equals(mode));
        if ("Huawei".equals(mode) || "Native".equals(mode)) {
            try {
                ink = new HuaweiInk(this, trace, this::report, onTop, "Native".equals(mode));
                paper.addView(ink.view, new FrameLayout.LayoutParams(-1, -1));
                ink.start();
                report("Native".equals(mode) ? "Native editor owns rendering and tracks; Replay is unavailable."
                    : "HW live: app does not paint strokes. Use Inspect and Replay after writing.");
            } catch (Exception e) { report("ERROR creating Huawei surface: " + e); }
        } else {
            paper.addView(new PaperView("Android".equals(mode)), new FrameLayout.LayoutParams(-1, -1));
            report("Replay".equals(mode) ? "Captured points only; no live handwriting active."
                : "Ordinary Android rendering. Pen or finger; finger ignored briefly after pen use.");
        }
    }
    private void inspect() {
        if (ink != null) ink.inspect();
        if ("Native".equals(mode)) return;
        report("Inspect: " + trace.summary() + "; canvas " + paper.getWidth() + "x" + paper.getHeight());
    }
    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        if (fullscreen) getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        fullscreenButton.setText(fullscreen ? "Fullscreen: on" : "Fullscreen: off");
        report("Fullscreen=" + fullscreen + "; only the fullscreen window flag changed. Save log before switching.");
    }
    private void toggleNotesUi() {
        notesUi = !notesUi;
        getWindow().getDecorView().setSystemUiVisibility(notesUi ? NOTES_UI_FLAGS : 0);
        notesUiButton.setText(notesUi ? "Notes UI: on" : "Notes UI: off");
        report("Notes UI=" + notesUi + "; system UI flags only. Save log before switching.");
    }
    private void toggleAwake() {
        keepAwake = !keepAwake;
        if (keepAwake) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        awakeButton.setText(keepAwake ? "Awake: on" : "Awake: off");
        report("Keep screen on=" + keepAwake + "; only the awake window flag changed.");
    }
    private void clear() {
        trace.clear();
        if (ink != null) {
            try { ink.stop(); ink.clearSurface(); ink.start(); }
            catch (Exception e) { report("ERROR clearing: " + e); return; }
        } else paper.invalidate();
        for (int i = 0; i < paper.getChildCount(); i++) paper.getChildAt(i).invalidate();
        report("Cleared captured points and writing surface");
    }
    private void saveLog() {
        // Explicit snapshot only; callbacks never trigger disk I/O.
        try {
            JSONObject snapshot = trace.json();
            snapshot.put("appVersion", "0.6");
            snapshot.put("model", Build.MODEL); snapshot.put("firmware", Build.DISPLAY);
            snapshot.put("mode", mode); snapshot.put("zOnTop", onTop);
            snapshot.put("fullscreen", fullscreen);
            int windowFlags = getWindow().getAttributes().flags;
            snapshot.put("windowFlags", windowFlags);
            snapshot.put("notesUiRequested", notesUi);
            snapshot.put("systemUiVisibility", getWindow().getDecorView().getSystemUiVisibility());
            snapshot.put("keepScreenOnRequested", keepAwake);
            snapshot.put("keepScreenOn", (windowFlags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0);
            snapshot.put("canvasWidth", paper.getWidth()); snapshot.put("canvasHeight", paper.getHeight());
            try { snapshot.put("displayPolicy", DisplayPolicy.json(this)); }
            catch (Exception e) { snapshot.put("displayPolicyError", e.toString()); }
            if (ink != null) snapshot.put("firmwareSurface", ink.nativeSnapshot());
            JSONArray log = new JSONArray();
            synchronized (events) { for (String e : events) log.put(e); }
            snapshot.put("events", log);
            final byte[] data = snapshot.toString(2).getBytes(StandardCharsets.UTF_8);
            writer.execute(() -> {
                File file = new File(getFilesDir(), "probe.json");
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write(data); out.getFD().sync();
                    report("Saved probe.json (app-private diagnostic export)");
                } catch (Exception e) { report("ERROR saving log: " + e); }
            });
        } catch (Exception e) { report("ERROR taking snapshot: " + e); }
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("fullscreen", fullscreen);
        out.putBoolean("notesUi", notesUi);
        out.putBoolean("keepAwake", keepAwake);
        out.putBoolean("onTop", onTop);
        out.putString("mode", mode);
        super.onSaveInstanceState(out);
    }
    @Override protected void onPause() { if (ink != null) ink.stop(); trace.cancel(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (ink != null) ink.start(); }
    @Override protected void onDestroy() {
        if (ink != null) ink.release(); writer.shutdown(); super.onDestroy();
    }
    private final class PaperView extends View {
        final boolean writable;
        final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        long lastPen;
        PaperView(boolean writable) {
            super(MainActivity.this); this.writable = writable;
            pen.setColor(Color.BLACK); pen.setStrokeWidth(4); pen.setStyle(Paint.Style.STROKE);
            pen.setStrokeJoin(Paint.Join.ROUND); pen.setStrokeCap(Paint.Cap.ROUND);
        }
        @Override protected void onDraw(Canvas c) { c.drawColor(Color.WHITE); trace.draw(c, pen); }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!writable) return false;
            int tool = e.getToolType(0);
            boolean stylus = tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER;
            if (stylus) lastPen = e.getEventTime();
            else if (e.getEventTime() - lastPen < 1000) return true;
            if (e.getActionMasked() > MotionEvent.ACTION_CANCEL) return true;
            String source = stylus ? "android-stylus" : "android-finger";
            for (int i = 0; i < e.getHistorySize(); i++)
                trace.point(source, MotionEvent.ACTION_MOVE, e.getHistoricalX(0, i), e.getHistoricalY(0, i),
                    e.getHistoricalEventTime(i), e.getHistoricalPressure(0, i));
            trace.point(source, e.getActionMasked(), e.getX(), e.getY(), e.getEventTime(), e.getPressure());
            invalidate(); return true;
        }
    }
}
