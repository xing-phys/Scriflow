package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Debug-only PROCESS_TEXT target models a dictionary Activity floating over a visible page. */
public final class NotebookBrowserOverlaySelfTestActivity extends Activity {
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.WHITE));
        getWindow().setGravity(Gravity.TOP | Gravity.END);
        LinearLayout body = NotebookBrowserUi.column(this); body.setPadding(24, 24, 24, 24);
        TextView word = NotebookBrowserUi.label(this, "Dictionary overlay fixture\n" + getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT), 16); body.addView(word);
        body.addView(NotebookBrowserUi.button(this, "Close overlay", this::finish)); setContentView(body);
        getWindow().setLayout(NotebookBrowserUi.dp(this, 240), NotebookBrowserUi.dp(this, 150));
    }
}
