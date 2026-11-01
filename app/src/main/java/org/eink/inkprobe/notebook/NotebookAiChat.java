package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;

/** A focusable floating chat above native ink, with explicit input exclusions and contact barriers. */
final class NotebookAiChat {
    interface Listener {
        boolean contactStart(); void contactEnd(); void boundsChanged(Rect bounds); void closed();
    }
    private final Activity activity;
    private final View anchor;
    private final Listener listener;
    private final PopupWindow popup;
    private final LinearLayout content;
    private final EditText composer;
    private final LinearLayout attachments;
    private final HorizontalScrollView attachmentStrip;
    private final TextView status,title;
    private final NotebookButton send;
    private final NotebookIconButton copy,fresh;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final android.content.SharedPreferences prefs;
    private NotebookAiMarkdownView markdown;
    private NotebookAiSession session;
    private final Rect bounds=new Rect(),area=new Rect();
    private Rect resizeFrame;
    private final java.util.List<Runnable> pendingSelections=new java.util.ArrayList<>();
    private float xFraction,yFraction,widthFraction,heightFraction;
    private boolean closed,contact,composerLoaded,settingText;
    private String displayProblem="";
    private AlertDialog clearDialog;
    private final Runnable saveDraft=this::saveDraft;
    private final View.OnLayoutChangeListener layout=(v,l,t,r,b,ol,ot,or,ob)->position();

    NotebookAiChat(Activity activity,View anchor,Listener listener) {
        this.activity=activity; this.anchor=anchor; this.listener=listener;
        prefs=activity.getSharedPreferences("aiWindow",Activity.MODE_PRIVATE);
        xFraction=prefs.getFloat("x",.95f); yFraction=prefs.getFloat("y",.12f);
        widthFraction=prefs.getFloat("width",.68f); heightFraction=prefs.getFloat("height",.68f);
        content=new LinearLayout(activity) {
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if (event.getActionMasked()==MotionEvent.ACTION_DOWN) {
                    if (!listener.contactStart()) return true;
                    contact=true;
                }
                try { return super.dispatchTouchEvent(event); }
                finally {
                    if (event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL) finishContact();
                }
            }
        };
        content.setOrientation(LinearLayout.VERTICAL); content.setBackgroundColor(Color.WHITE);
        android.graphics.drawable.GradientDrawable frame=new android.graphics.drawable.GradientDrawable();
        frame.setColor(Color.TRANSPARENT); frame.setStroke(dp(2),Color.BLACK); content.setForeground(frame);
        content.setPadding(dp(6),dp(4),dp(6),dp(4));
        LinearLayout header=new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL);
        title=label("AI chat · drag to move",14); title.setMaxLines(2); title.setTypeface(null,android.graphics.Typeface.BOLD);
        title.setContentDescription("AI chat; drag to move window"); title.setOnTouchListener(new Grip(false));
        header.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));
        fresh=icon(NotebookIconDrawable.Icon.PLUS,"New chat",this::newChat);
        copy=icon(NotebookIconDrawable.Icon.COPY,"Copy last AI reply",this::copy);
        header.addView(fresh,new LinearLayout.LayoutParams(dp(32),dp(32))); header.addView(copy,new LinearLayout.LayoutParams(dp(32),dp(32)));
        NotebookButton settings=button("Settings",this::settings); header.addView(settings,new LinearLayout.LayoutParams(dp(62),dp(36)));
        header.addView(icon(NotebookIconDrawable.Icon.CLOSE,"Close AI chat",this::close),new LinearLayout.LayoutParams(dp(32),dp(32)));
        content.addView(header);
        markdown=new NotebookAiMarkdownView(activity,problem->{ displayProblem=problem; refresh(); });
        content.addView(markdown.view,new LinearLayout.LayoutParams(-1,0,1));
        status=label("Loading AI settings…",12); status.setMaxLines(3); content.addView(status,new LinearLayout.LayoutParams(-1,-2));
        attachments=new LinearLayout(activity); attachmentStrip=new HorizontalScrollView(activity);
        attachmentStrip.setOverScrollMode(View.OVER_SCROLL_NEVER); attachmentStrip.setHorizontalScrollBarEnabled(false);
        attachmentStrip.addView(attachments); content.addView(attachmentStrip,new LinearLayout.LayoutParams(-1,-2));
        composer=new EditText(activity); composer.setTextColor(Color.BLACK); composer.setTextSize(14); composer.setGravity(Gravity.TOP|Gravity.START);
        composer.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        composer.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN);
        composer.setMinLines(2); composer.setMaxLines(4); composer.setHint("Message AI…"); composer.setContentDescription("Message AI");
        composer.setFilters(new InputFilter[]{new InputFilter.LengthFilter(NotebookAiConversation.MAX_MESSAGE_BYTES)});
        composer.setSaveEnabled(false); content.addView(composer,new LinearLayout.LayoutParams(-1,-2));
        composer.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                if (settingText||closed) return; main.removeCallbacks(saveDraft); main.postDelayed(saveDraft,400); updateSend();
            }
            public void afterTextChanged(Editable value) { }
        });
        composer.setOnKeyListener((v,key,event)->{
            if (key==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_DOWN&&event.isCtrlPressed()) { send(); return true; }
            return false;
        });
        LinearLayout footer=new LinearLayout(activity); footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView hint=label("Markdown replies · Ctrl+Enter to send",11); footer.addView(hint,new LinearLayout.LayoutParams(0,-2,1));
        send=button("Send",this::send); footer.addView(send,new LinearLayout.LayoutParams(dp(72),dp(36)));
        NotebookIconButton resize=icon(NotebookIconDrawable.Icon.RESIZE,"Drag to resize AI chat",()->{}); resize.setOnTouchListener(new Grip(true));
        footer.addView(resize,new LinearLayout.LayoutParams(dp(32),dp(32))); content.addView(footer);
        popup=new PopupWindow(content,1,1,true); popup.setOutsideTouchable(false); popup.setTouchModal(false);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setClippingEnabled(false); popup.setIsLaidOutInScreen(true); popup.setAnimationStyle(0); popup.setElevation(0);
        popup.setEnterTransition(null); popup.setExitTransition(null);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popup.setOnDismissListener(this::close);
        content.getViewTreeObserver().addOnPreDrawListener(()->{
            if (!closed&&popup.isShowing()) {
                int[] origin=new int[2]; content.getLocationOnScreen(origin);
                publish(new Rect(origin[0],origin[1],origin[0]+content.getWidth(),origin[1]+content.getHeight()));
            }
            return true;
        });
        refresh();
    }
    void show() {
        if (closed) return; anchor.addOnLayoutChangeListener(layout); position();
        if (!NotebookPopupHost.show(popup,anchor,Gravity.TOP|Gravity.LEFT,bounds.left,bounds.top)) { close(); return; }
        // Decryption and disk I/O never block the native editor thread.
        new Thread(()->{
            NotebookAiConfig config=null; String problem=null;
            try {
                if (!NotebookAiSettings.configured(activity)) problem="Set your AI provider and API key in Settings to start chatting.";
                else { config=NotebookAiSettings.read(activity); config.validate(); }
            } catch (IllegalArgumentException error) { problem=error.getMessage(); }
            catch (java.security.GeneralSecurityException|java.io.IOException error) { problem="Cannot unlock AI settings. Enter your API key again in Settings."; }
            final NotebookAiConfig connection=config; final String failure=problem;
            main.post(()->{
                if (closed) return;
                if (failure!=null) { displayProblem=failure; refresh(); return; }
                title.setText("AI · "+connection.provider.label+"\n"+connection.model);
                session=new NotebookAiSession(activity.getFilesDir(),connection,main::post,this::refresh); session.load(); refresh();
            });
        },"ai-chat-open").start();
    }
    private void refresh() {
        if (closed||status==null) return;
        if (session!=null&&session.loaded&&!composerLoaded) {
            composerLoaded=true; setText(session.chat.draft);
            java.util.List<Runnable> pending=new java.util.ArrayList<>(pendingSelections); pendingSelections.clear();
            for (Runnable selection:pending) selection.run();
        }
        if (session!=null) {
            attachments.removeAllViews();
            for (int i=0;i<session.chat.draftImages.size();i++) {
                final int index=i; NotebookAiAttachment image=session.chat.draftImages.get(i);
                NotebookButton chip=button((image.name.startsWith("Handwriting")?"Ink ":"Image ")+(i+1)+"  ×",()->session.removeImage(index));
                chip.setContentDescription("Remove attached "+image.name); chip.setTooltipText(image.name);
                attachments.addView(chip,new LinearLayout.LayoutParams(dp(84),dp(30)));
            }
            attachmentStrip.setVisibility(session.chat.draftImages.isEmpty()?View.GONE:View.VISIBLE);
        }
        if (session!=null) markdown.messages(session.chat.messages);
        String text=!displayProblem.isEmpty()?displayProblem:session==null?"Loading AI settings…":session.status;
        status.setText(text); status.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE);
        composer.setEnabled(session!=null&&session.loaded&&!session.loadFailed&&!session.busy&&!session.chat.awaitingReply());
        copy.setAvailable(session!=null&&!session.chat.lastAnswer().isEmpty()); fresh.setAvailable(session!=null&&session.loaded);
        updateSend();
    }
    private void updateSend() {
        if (send==null) return;
        send.setText(session!=null&&session.busy?"Stop":session!=null&&session.chat.awaitingReply()?"Retry":session!=null&&session.saveFailed?"Save":"Send");
        send.setAvailable(session!=null&&session.loaded&&!session.loadFailed
            &&(session.busy||session.chat.awaitingReply()||session.saveFailed||!session.chat.draftImages.isEmpty()||composer.getText().toString().trim().length()>0));
    }
    void append(String source,NotebookAiAttachment image) {
        if (closed) return;
        if (session==null||!session.loaded) {
            if (pendingSelections.size()>=4) { Toast.makeText(activity,"Wait for AI chat to load before appending more selections",Toast.LENGTH_LONG).show(); return; }
            pendingSelections.add(()->append(source,image)); return;
        }
        if (session.loadFailed) { Toast.makeText(activity,"Start a new AI chat to recover before appending selections",Toast.LENGTH_LONG).show(); return; }
        if (!source.isEmpty()) {
            String text=composer.getText().toString(); String next=text+(text.isEmpty()?"":"\n\n")+source;
            try { session.chat.draft(next); }
            catch (IllegalArgumentException error) { Toast.makeText(activity,error.getMessage(),Toast.LENGTH_LONG).show(); return; }
            setText(next); saveDraft();
        }
        if (image!=null) session.attach(image);
        else { session.status="Markdown selection appended. Tap Send when ready."; refresh(); }
    }
    private void send() {
        if (session==null) return;
        if (session.busy) { session.stop(); return; }
        if (session.saveFailed&&!session.chat.awaitingReply()) { session.retrySave(); return; }
        main.removeCallbacks(saveDraft); displayProblem="";
        session.send(composer.getText().toString()); if (session.busy) { setText(""); hideKeyboard(); }
    }
    private void saveDraft() {
        if (session!=null&&composerLoaded) session.draft(composer.getText().toString());
    }
    private void setText(String text) { settingText=true; composer.setText(text); composer.setSelection(composer.length()); settingText=false; }
    private void copy() {
        if (session==null||session.chat.lastAnswer().isEmpty()) return;
        ((android.content.ClipboardManager)activity.getSystemService(Activity.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("AI reply",session.chat.lastAnswer()));
        Toast.makeText(activity,"AI reply copied as Markdown",Toast.LENGTH_SHORT).show();
    }
    private void newChat() {
        if (session==null||!session.loaded||clearDialog!=null) return;
        if (!contact) { if (!listener.contactStart()) return; contact=true; }
        clearDialog=new AlertDialog.Builder(activity).setTitle("Start a new chat?")
            .setMessage("This clears the current conversation and draft for this provider, endpoint, and model.")
            .setNegativeButton("Cancel",null).setPositiveButton("New chat",(d,w)->{
                main.removeCallbacks(saveDraft); displayProblem=""; setText(""); session.clear();
            }).create();
        clearDialog.setOnDismissListener(d->{ clearDialog=null; finishContact(); }); NotebookUi.show(clearDialog);
    }
    private void settings() { saveDraft(); activity.startActivity(new Intent(activity,NotebookSettingsActivity.class)); activity.overridePendingTransition(0,0); }
    private void position() {
        if (closed) return;
        anchor.getWindowVisibleDisplayFrame(area);
        if (area.isEmpty()) { int[] origin=new int[2]; anchor.getLocationOnScreen(origin); area.set(origin[0],origin[1],origin[0]+anchor.getWidth(),origin[1]+anchor.getHeight()); }
        if (area.isEmpty()) return;
        int margin=dp(8),width=Math.min(area.width()-2*margin,Math.max(dp(320),Math.round(area.width()*widthFraction)));
        int height=Math.min(area.height()-2*margin,Math.max(dp(260),Math.round(area.height()*heightFraction)));
        width=Math.max(1,width); height=Math.max(1,height);
        int x=area.left+margin+Math.round(Math.max(0,area.width()-2*margin-width)*clamp(xFraction));
        int y=area.top+margin+Math.round(Math.max(0,area.height()-2*margin-height)*clamp(yFraction));
        if (resizeFrame!=null) { x=resizeFrame.left; y=resizeFrame.top; width=resizeFrame.width(); height=resizeFrame.height(); }
        publish(new Rect(x,y,x+width,y+height));
        if (popup.isShowing()) popup.update(x,y,width,height); else { popup.setWidth(width); popup.setHeight(height); }
    }
    private void publish(Rect next) { if (!bounds.equals(next)) { bounds.set(next); listener.boundsChanged(new Rect(bounds)); } }
    private void finishContact() { if (clearDialog!=null&&clearDialog.isShowing()) return; if (contact) { contact=false; listener.contactEnd(); } }
    void close() {
        if (closed) return;
        main.removeCallbacks(saveDraft); saveDraft(); closed=true; anchor.removeOnLayoutChangeListener(layout);
        if (session!=null) session.close(); if (clearDialog!=null) clearDialog.dismiss();
        pendingSelections.clear();
        hideKeyboard(); popup.dismiss(); content.removeView(markdown.view); markdown.close(); publish(new Rect()); finishContact(); listener.closed();
    }
    private final class Grip implements View.OnTouchListener {
        final boolean resize; float startX,startY; final Rect start=new Rect(); boolean moved,cancelled;
        Grip(boolean resize) { this.resize=resize; }
        public boolean onTouch(View view,MotionEvent event) {
            if (event.getActionMasked()==MotionEvent.ACTION_DOWN) { startX=event.getRawX(); startY=event.getRawY(); start.set(bounds); moved=cancelled=false; return true; }
            if (event.getPointerCount()>1) { cancelled=true; return true; }
            if (event.getActionMasked()==MotionEvent.ACTION_MOVE) {
                if (cancelled) return true;
                float dx=event.getRawX()-startX,dy=event.getRawY()-startY;
                if (!moved&&Math.hypot(dx,dy)<ViewConfiguration.get(activity).getScaledTouchSlop()) return true; moved=true;
                if (resize) {
                    int[] next=NotebookAiWindow.resize(start.left,start.top,start.width(),start.height(),dx,dy,
                        area.right-dp(8),area.bottom-dp(8),dp(320),dp(260));
                    widthFraction=next[2]/(float)Math.max(1,area.width()); heightFraction=next[3]/(float)Math.max(1,area.height());
                    xFraction=(start.left-area.left-dp(8))/(float)Math.max(1,area.width()-2*dp(8)-next[2]);
                    yFraction=(start.top-area.top-dp(8))/(float)Math.max(1,area.height()-2*dp(8)-next[3]);
                    resizeFrame=new Rect(next[0],next[1],next[0]+next[2],next[1]+next[3]);
                }
                else { xFraction=(start.left-area.left-dp(8)+dx)/Math.max(1,area.width()-2*dp(8)-start.width()); yFraction=(start.top-area.top-dp(8)+dy)/Math.max(1,area.height()-2*dp(8)-start.height()); }
                xFraction=clamp(xFraction); yFraction=clamp(yFraction); widthFraction=clamp(widthFraction); heightFraction=clamp(heightFraction); position();
                content.invalidate(); return true;
            }
            if (event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL) {
                resizeFrame=null;
                if (!moved&&!cancelled&&event.getActionMasked()==MotionEvent.ACTION_UP) view.performClick();
                prefs.edit().putFloat("x",xFraction).putFloat("y",yFraction).putFloat("width",widthFraction).putFloat("height",heightFraction).apply(); return true;
            }
            return true;
        }
    }
    private void hideKeyboard() { ((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(composer.getWindowToken(),0); }
    private NotebookIconButton icon(NotebookIconDrawable.Icon icon,String name,Runnable action) {
        NotebookIconButton button=new NotebookIconButton(activity,icon,name); button.setTooltipText(name); button.setOnClickListener(v->action.run()); return button;
    }
    private NotebookButton button(String text,Runnable action) { NotebookButton button=new NotebookButton(activity); button.setText(text); button.setTextSize(12); button.setOnClickListener(v->action.run()); return button; }
    private TextView label(String text,int size) { TextView view=new TextView(activity); view.setText(text); view.setTextSize(size); view.setTextColor(Color.BLACK); return view; }
    private static float clamp(float value) { return Float.isNaN(value)?0:Math.max(0,Math.min(1,value)); }
    private int dp(int n) { return Math.round(n*activity.getResources().getDisplayMetrics().density); }
}
