package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;

final class NotebookBrowserUi {
    static int dp(Context context,int value) { return Math.round(value*context.getResources().getDisplayMetrics().density); }
    static LinearLayout column(Context context) { LinearLayout view=new LinearLayout(context); view.setOrientation(LinearLayout.VERTICAL); view.setBackgroundColor(Color.WHITE); return view; }
    static TextView label(Context context,String text,int size) { TextView view=new TextView(context);view.setText(text);view.setTextSize(size);view.setTextColor(Color.BLACK);view.setGravity(Gravity.CENTER_VERTICAL);return view; }
    static NotebookButton button(Context context,String text,Runnable action) { NotebookButton view=new NotebookButton(context);view.setText(text);view.setTextSize(13);NotebookUi.action(view,null);view.setOnClickListener(ignored->action.run());return view; }
    static EditText input(Context context,String hint,String text) { EditText view=new EditText(context);view.setTextColor(Color.BLACK);view.setTextSize(15);view.setSingleLine(true);view.setHint(hint);view.setText(text);view.setContentDescription(hint);return view; }
    static LinearLayout row(Context context) { LinearLayout view=new LinearLayout(context);view.setGravity(Gravity.CENTER_VERTICAL);return view; }
    static void space(LinearLayout root,int pixels) { android.view.View view=new android.view.View(root.getContext());root.addView(view,new LinearLayout.LayoutParams(1,pixels)); }
    private NotebookBrowserUi() { }
}
