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
    static NotebookBrowserIconButton icon(Context context,NotebookIconDrawable.Icon symbol,String name,Runnable action) { return new NotebookBrowserIconButton(context,symbol,name,action); }
    static LinearLayout.LayoutParams beside(Context context,int width,int height) { LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(width<0?width:dp(context,width),height<0?height:dp(context,height));size.setMarginStart(dp(context,8));return size; }
    static boolean compact(Context context) { return context.getResources().getConfiguration().screenWidthDp<480; }
    static void header(Context context,LinearLayout root,String name,Runnable back,android.view.View status) {
        LinearLayout header=row(context);header.addView(icon(context,NotebookIconDrawable.Icon.PREVIOUS,"Back to browser",back),new LinearLayout.LayoutParams(dp(context,48),dp(context,48)));
        TextView title=label(context,name,21);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams titleSize=new LinearLayout.LayoutParams(0,dp(context,48),1);titleSize.setMargins(dp(context,12),0,dp(context,12),0);header.addView(title,titleSize);
        header.addView(status);root.addView(header);space(root,dp(context,12));
    }
    static void list(ListView view) { view.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);view.setDivider(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));view.setDividerHeight(dp(view.getContext(),10));view.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)); }
    static void field(EditText view) { Context context=view.getContext();view.setBackground(NotebookUi.rounded(context.getResources().getDisplayMetrics().density,7));view.setPadding(dp(context,12),0,dp(context,12),0);view.setHintTextColor(0xff555555);view.setMinimumHeight(dp(context,48)); }
    static EditText input(Context context,String hint,String text) { EditText view=new EditText(context);view.setTextColor(Color.BLACK);view.setTextSize(15);view.setSingleLine(true);view.setHint(hint);view.setText(text);view.setContentDescription(hint);return view; }
    static LinearLayout row(Context context) { LinearLayout view=new LinearLayout(context);view.setGravity(Gravity.CENTER_VERTICAL);return view; }
    static void space(LinearLayout root,int pixels) { android.view.View view=new android.view.View(root.getContext());root.addView(view,new LinearLayout.LayoutParams(1,pixels)); }
    private NotebookBrowserUi() { }
}
