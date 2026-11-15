package org.eink.inkprobe.notebook;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

/** Debug APK only: exercises the production browser and reports its rendered DOM. */
public final class NotebookBrowserSelfTestActivity extends NotebookBrowserActivity {
    private static Bundle captured, forced;
    private String lifecycle = "created";
    @Override protected void onResume() { super.onResume(); lifecycle = "resumed"; }
    @Override protected void onPause() { super.onPause(); lifecycle = "paused"; getWindow().getDecorView().postDelayed(this::report, 500); }
    @Override protected void onStop() { super.onStop(); lifecycle = "stopped"; getWindow().getDecorView().postDelayed(this::report, 500); }
    @Override public void onCreate(Bundle state) {
        if (forced != null) { state = forced; forced = null; }
        super.onCreate(state);
        getWindow().getDecorView().postDelayed(this::report, 5000);
        getWindow().getDecorView().postDelayed(this::report, 20000);
    }

    @Override protected void onBrowserDocumentReady() {
        getWindow().getDecorView().postDelayed(this::report, 3000);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("captureState", false)) { captured = new Bundle(); onSaveInstanceState(captured); }
        if (intent.getBooleanExtra("restoreStale", false) && captured != null) { forced = captured; recreate(); return; }
        String click = intent.getStringExtra("click");
        if (click != null) click(browserDebugRoot(), click);
        String tabText = intent.getStringExtra("tabText"); if (tabText != null) { click(getWindow().getDecorView(), "Browser tabs"); textClick(browserDebugRoot(), tabText); }
        String text = intent.getStringExtra("textClick"); if (text != null) textClick(browserDebugRoot(), text);
        String url = intent.getStringExtra("navigate"); if (url != null) { fill(getWindow().getDecorView(), "Website address", url); click(getWindow().getDecorView(), "Browse website address"); }
        String fragment = intent.getStringExtra("fragment");
        if (fragment != null && fragment.matches("[A-Za-z0-9_-]+")) jump(getWindow().getDecorView(), fragment);
        if (intent.getBooleanExtra("recreate", false)) { recreate(); return; }
        getWindow().getDecorView().postDelayed(this::report, 1500);
    }

    private boolean textClick(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText()) && view.isClickable()) return view.performClick();
        if (view instanceof ViewGroup) { ViewGroup group=(ViewGroup)view; for(int i=0;i<group.getChildCount();i++)if(textClick(group.getChildAt(i),text))return true; } return false;
    }
    private void fill(View view, String description, String text) {
        if (view instanceof android.widget.EditText && description.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())) ((android.widget.EditText)view).setText(text);
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)fill(group.getChildAt(i),description,text);}
    }
    private void jump(View view, String fragment) {
        if (view instanceof NotebookBrowserEngine) {
            NotebookBrowserEngine engine = (NotebookBrowserEngine) view;
            if (engine.getUrl() != null) engine.loadUrl(NotebookBrowserAddress.withoutFragment(engine.getUrl()) + "#" + fragment);
        } else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) jump(group.getChildAt(i), fragment);
        }
    }

    private boolean click(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription()))
            return view.performClick();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) if (click(group.getChildAt(i), description)) return true;
        }
        return false;
    }

    private void report() {
        if (isDestroyed() || isFinishing()) return;
        probeBrowser(stats -> {
            try {
                JSONObject result = stats == null ? new JSONObject().put("probe", "unavailable") : stats;
                JSONArray labels = new JSONArray(); labels(browserDebugRoot(), labels);
                result.put("nativeLabels", labels);
                result.put("lifecycle", lifecycle);
                result.put("time", System.currentTimeMillis());
                try (FileOutputStream output = new FileOutputStream(new File(getFilesDir(), "browser-test.json"))) {
                    output.write(result.toString(2).getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception error) { android.util.Log.e("BrowserSelfTest", "Could not write report", error); }
        });
    }

    private void labels(View view, JSONArray labels) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof TextView) labels.put(((TextView) view).getText().toString());
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) labels(group.getChildAt(i), labels);
        }
    }
}
