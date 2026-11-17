package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.media.projection.MediaProjectionConfig;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.security.SecureRandom;

/** Local casting controls. This screen and Settings are never sent to viewers. */
public final class NotebookCastActivity extends NotebookDisplayActivity {
    private static final int CAPTURE = 4301;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView status, address;
    private EditText password;
    private LinearLayout passwordControls;
    private NotebookButton start, stop, copy;
    private boolean requesting;
    private String pendingPassword;
    private NotebookCastNetwork pendingWifi;
    private final Runnable update = new Runnable() {
        @Override public void run() { refresh(); ui.postDelayed(this, 500); }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setWindowAnimations(0);
        LinearLayout root = column(); root.setPadding(dp(18), dp(12), dp(18), dp(12));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = label("Screen cast", 21); title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        header.addView(button("Done", this::onBackPressed), new LinearLayout.LayoutParams(dp(76), dp(38)));
        root.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout body = column();
        body.addView(label("See Scriflow in a browser on your Mac, phone or tablet. Connect both devices to the same Wi-Fi.", 15));
        status = label("", 16); status.setTypeface(null, Typeface.BOLD); body.addView(status);
        address = label("", 21); address.setTextIsSelectable(true); body.addView(address);
        copy = button("Copy address", () -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Scriflow screen cast", NotebookCastController.address()));
            Toast.makeText(this, "Cast address copied", Toast.LENGTH_SHORT).show();
        }); body.addView(copy, new LinearLayout.LayoutParams(-1, dp(42)));
        passwordControls = column(); passwordControls.addView(label("Cast password", 15));
        password = new EditText(this); password.setTextSize(17); password.setTextColor(Color.BLACK);
        password.setSingleLine(true); password.setHint("8–128 characters");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setTransformationMethod(PasswordTransformationMethod.getInstance());
        password.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128)});
        password.setSaveEnabled(false); password.setContentDescription("Cast password");
        password.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        passwordControls.addView(password, new LinearLayout.LayoutParams(-1, dp(52)));
        CheckBox show = new CheckBox(this); show.setText("Show password"); show.setTextColor(Color.BLACK);
        show.setOnCheckedChangeListener((button, checked) -> {
            password.setTransformationMethod(checked ? null : PasswordTransformationMethod.getInstance()); password.setSelection(password.length());
        }); passwordControls.addView(show);
        passwordControls.addView(button("Generate password", () -> {
            String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
            SecureRandom random = new SecureRandom(); StringBuilder value = new StringBuilder();
            for (int i = 0; i < 12; i++) value.append(alphabet.charAt(random.nextInt(alphabet.length())));
            password.setText(value); show.setChecked(true);
        }), new LinearLayout.LayoutParams(-1, dp(42)));
        passwordControls.addView(label("Remember the password before starting. It is kept only for this cast session. Stop casting to change it.", 13));
        body.addView(passwordControls);
        start = button("Start casting", this::requestCapture); body.addView(start, new LinearLayout.LayoutParams(-1, dp(48)));
        stop = button("Stop casting", () -> { NotebookCastController.stopCasting(this); refresh(); });
        body.addView(stop, new LinearLayout.LayoutParams(-1, dp(48)));
        body.addView(label("Open the complete http:// address shown above in the other device's browser, then enter the password. Viewers can watch; writing and controls stay on the tablet.", 14));
        body.addView(label("Return to a note, book or browser page to share. Settings and these controls stay private. Leaving Scriflow pauses the feed; turning the screen off or losing Wi-Fi stops casting.", 14));
        body.addView(label("Use trusted Wi-Fi: this local HTTP connection is not encrypted. Guest networks may block devices from reaching each other. No Internet connection is required.", 13));
        scroll.addView(body); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root); refresh();
    }

    private void requestCapture() {
        if (requesting || NotebookCastController.running() || NotebookCastController.starting()) return;
        String value = password.getText().toString();
        if (value.codePointCount(0, value.length()) < 8 || value.trim().isEmpty()) {
            password.setError("Enter at least 8 characters."); password.requestFocus(); return;
        }
        NotebookCastNetwork wifi = NotebookCastNetwork.find(this);
        if (wifi == null) { Toast.makeText(this, "Connect the tablet to Wi-Fi first", Toast.LENGTH_LONG).show(); return; }
        pendingPassword = value; pendingWifi = wifi; requesting = true; refresh();
        try {
            MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
            // Keep capture owned by this app's visibility gate on newer Android too.
            Intent request = Build.VERSION.SDK_INT >= 34
                ? manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay()) : manager.createScreenCaptureIntent();
            startActivityForResult(request, CAPTURE);
        }
        catch (RuntimeException error) {
            requesting = false; pendingPassword = null; pendingWifi = null; refresh();
            Toast.makeText(this, "Android screen capture is unavailable", Toast.LENGTH_LONG).show();
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != CAPTURE) return;
        requesting = false;
        if (result == Activity.RESULT_OK && data != null && pendingPassword != null && pendingWifi != null) {
            NotebookCastController.start(this, data, pendingPassword, pendingWifi); password.setText("");
            getSystemService(android.view.inputmethod.InputMethodManager.class).hideSoftInputFromWindow(password.getWindowToken(), 0);
        } else Toast.makeText(this, "Screen capture was not started", Toast.LENGTH_SHORT).show();
        pendingPassword = null; pendingWifi = null; refresh();
    }
    private void refresh() {
        if (status == null) return;
        boolean active = NotebookCastController.running(), busy = requesting || NotebookCastController.starting();
        text(status, requesting ? "Allow screen capture in Android's prompt." : NotebookCastController.status());
        text(address, active ? NotebookCastController.address() : ""); address.setVisibility(active ? View.VISIBLE : View.GONE);
        copy.setVisibility(active ? View.VISIBLE : View.GONE); stop.setVisibility(active ? View.VISIBLE : View.GONE);
        start.setVisibility(active ? View.GONE : View.VISIBLE); start.setAvailable(!busy);
        passwordControls.setVisibility(active ? View.GONE : View.VISIBLE); password.setEnabled(!busy);
    }
    private void text(TextView view, String text) { if (!text.contentEquals(view.getText())) view.setText(text); }
    @Override protected boolean allowScreenCast() { return false; }
    @Override protected void onResume() { super.onResume(); ui.removeCallbacks(update); ui.post(update); }
    @Override protected void onPause() { ui.removeCallbacks(update); super.onPause(); }
    @Override protected void onDestroy() { ui.removeCallbacksAndMessages(null); pendingPassword = null; pendingWifi = null; super.onDestroy(); }
    @Override public void onBackPressed() { super.onBackPressed(); overridePendingTransition(0, 0); }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); view.setBackgroundColor(Color.WHITE); return view;
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size); view.setTextColor(Color.BLACK);
        view.setPadding(0, dp(10), 0, dp(10)); return view;
    }
    private NotebookButton button(String text, Runnable action) {
        NotebookButton button = new NotebookButton(this); button.setText(text); button.setTextSize(15);
        NotebookUi.action(button, null); button.setOnClickListener(view -> action.run()); return button;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
