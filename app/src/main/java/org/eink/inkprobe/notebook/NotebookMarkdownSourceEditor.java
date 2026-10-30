package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.*;
import android.text.method.KeyListener;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

/** Source editing stays beside its block. Outside finishes as code; the eye finishes rendered. */
final class NotebookMarkdownSourceEditor {
    interface Listener {
        void save(NotebookMarkdownSourceEditor owner, NotebookMarkdownDraft draft, NotebookMarkdown value, boolean selectAfter);
        void dismissed(NotebookMarkdownSourceEditor owner, boolean selectAfter);
    }
    interface DraftCallback { void complete(NotebookMarkdownDraft draft, IOException error); }
    private static final ExecutorService io = new ThreadPoolExecutor(0,1,30,TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(),r->new Thread(r,"markdown-draft"));
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Activity activity;
    private final FrameLayout root;
    private final View anchor;
    private final File directory;
    private final NotebookFigure before;
    private final NotebookMarkdownDraft initial;
    private final Listener listener;
    private final EditText editor;
    private final KeyListener keyListener;
    private final PopupWindow popup;
    private final RectF bounds;
    private final Runnable saveDraft = () -> persist(null);
    private NotebookMarkdownDraft current;
    private NotebookMarkdownRenderer renderer;
    private boolean closed, busy, failed;

    NotebookMarkdownSourceEditor(Activity activity, FrameLayout root, View anchor, RectF bounds,
            NotebookMarkdownDraft initial, NotebookFigure before, Listener listener) {
        this.activity=activity;this.root=root;this.anchor=anchor;this.bounds=new RectF(bounds);this.initial=initial;
        this.before=before;this.current=initial;this.listener=listener;directory=activity.getFilesDir();
        FrameLayout content=new FrameLayout(activity);
        GradientDrawable background=new GradientDrawable();background.setColor(Color.WHITE);background.setStroke(dp(1),Color.BLACK);
        background.setCornerRadius(dp(3));content.setBackground(background);content.setPadding(dp(6),dp(6),dp(6),dp(6));
        editor=new EditText(activity){
            @Override public boolean onKeyPreIme(int keyCode,KeyEvent event){
                if(keyCode==KeyEvent.KEYCODE_BACK&&event.getAction()==KeyEvent.ACTION_UP&&failed){
                    pause();listener.dismissed(NotebookMarkdownSourceEditor.this,false);return true;
                }
                return super.onKeyPreIme(keyCode,event);
            }
        };
        editor.setBackground(null);editor.setTextColor(Color.BLACK);editor.setTextSize(14);editor.setTypeface(Typeface.MONOSPACE);
        editor.setGravity(Gravity.TOP|Gravity.LEFT);editor.setPadding(dp(5),dp(4),dp(34),dp(4));
        editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        editor.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN);
        editor.setHorizontallyScrolling(false);editor.setHint("Markdown · $…$ inline math · $$…$$ display math");
        editor.setText(initial.source);editor.setSelection(editor.length());keyListener=editor.getKeyListener();
        editor.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){
                if(closed||busy)return;
                try{current=initial.withSource(s.toString());failed=false;main.removeCallbacks(saveDraft);main.postDelayed(saveDraft,300);}
                catch(IllegalArgumentException error){editor.setError(error.getMessage());}
            }
            public void afterTextChanged(Editable e){}
        });
        content.addView(editor,new FrameLayout.LayoutParams(-1,-1));
        NotebookIconButton render=new NotebookIconButton(activity,NotebookIconDrawable.Icon.RENDER_MARKDOWN,"Show rendered Markdown");
        render.setTooltipText("Show rendered Markdown");render.setOnClickListener(v->commit(false,true));
        FrameLayout.LayoutParams button=new FrameLayout.LayoutParams(dp(28),dp(28),Gravity.TOP|Gravity.RIGHT);content.addView(render,button);
        popup=new PopupWindow(content,1,1,true);popup.setOutsideTouchable(true);popup.setClippingEnabled(false);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setIsLaidOutInScreen(true);popup.setAnimationStyle(0);popup.setElevation(0);
        popup.setEnterTransition(null);popup.setExitTransition(null);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popup.setOnDismissListener(()->{if(!closed&&!busy)commit(true,false);});
    }

    void show() {
        if(closed)return;
        int[] origin=new int[2];root.getLocationOnScreen(origin);
        int width=Math.min(Math.max(1,root.getWidth()-dp(16)),Math.max(dp(260),Math.round(bounds.width())));
        int height=Math.min(Math.max(1,root.getHeight()/2),Math.max(dp(140),Math.round(bounds.height())));
        int x=Math.max(origin[0]+dp(8),Math.min(origin[0]+root.getWidth()-width-dp(8),Math.round(bounds.left)));
        int y=Math.max(origin[1]+dp(48),Math.min(origin[1]+root.getHeight()-height-dp(8),Math.round(bounds.top)));
        popup.setWidth(width);popup.setHeight(height);
        if(!NotebookPopupHost.show(popup,anchor,Gravity.TOP|Gravity.LEFT,x,y)){pause();listener.dismissed(this,false);return;}
        editor.requestFocus();editor.post(()->{if(!closed&&!busy)((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE))
            .showSoftInput(editor,InputMethodManager.SHOW_IMPLICIT);});
        if(!current.source.isEmpty())persist(null);
    }
    private void persist(Runnable complete) {
        final NotebookMarkdownDraft snapshot=current;
        io.execute(()->{
            IOException error=null;
            try{NotebookMarkdownDraft.write(directory,snapshot);}catch(IOException e){error=e;}
            final IOException problem=error;
            main.post(()->{
                if(closed)return;
                if(problem!=null){failed(problem);return;}
                if(complete!=null)complete.run();
            });
        });
    }
    private void commit(boolean sourceMode,boolean selectAfter) {
        if(closed||busy)return;
        try{current=initial.withSource(editor.getText().toString());}
        catch(IllegalArgumentException error){failed(new IOException(error.getMessage()));return;}
        busy=true;failed=false;main.removeCallbacks(saveDraft);editor.setKeyListener(null);hideKeyboard();popup.dismiss();
        if(before==null&&current.source.trim().isEmpty()){saved();listener.dismissed(this,false);return;}
        persist(()->{
            if(!current.source.equals(editor.getText().toString())){busy=false;commit(sourceMode,selectAfter);return;}
            if(before!=null&&before.markdown.source.equals(current.source)){
                if(before.markdown.sourceMode==sourceMode){saved();listener.dismissed(this,selectAfter);}
                else listener.save(this,current,before.markdown.asSource(sourceMode),selectAfter);
                return;
            }
            try{
                Toast.makeText(activity,"Rendering Markdown…",Toast.LENGTH_SHORT).show();
                renderer=new NotebookMarkdownRenderer(activity,root,current.source,sourceMode,640,(value,error)->{
                    NotebookMarkdownRenderer completed=renderer;renderer=null;
                    if(closed)return;
                    if(error!=null){failed(error);return;}
                    if(!value.source.equals(editor.getText().toString())){busy=false;commit(sourceMode,selectAfter);return;}
                    if(completed!=null&&!completed.warning().isEmpty())Toast.makeText(activity,completed.warning(),Toast.LENGTH_LONG).show();
                    listener.save(this,current,value,selectAfter);
                });
            }catch(RuntimeException error){failed(new IOException("Cannot start Markdown rendering",error));}
        });
    }
    void failed(IOException error) {
        if(closed)return;busy=false;failed=true;editor.setKeyListener(keyListener);
        Toast.makeText(activity,error.getMessage()+" · Back keeps the source draft",Toast.LENGTH_LONG).show();
        if(!popup.isShowing())show();
    }
    void saved() {
        if(closed)return;closed=true;main.removeCallbacks(saveDraft);hideKeyboard();popup.dismiss();
        if(renderer!=null){renderer.close();renderer=null;}
        io.execute(()->{try{NotebookMarkdownDraft.clear(directory,current.token);}catch(IOException ignored){/* Reopening compares the durable source before editing. */}});
    }
    void pause() {
        if(closed)return;
        main.removeCallbacks(saveDraft);
        try{current=initial.withSource(editor.getText().toString());}catch(IllegalArgumentException ignored){}
        final NotebookMarkdownDraft snapshot=current;
        io.execute(()->{try{NotebookMarkdownDraft.write(directory,snapshot);}catch(IOException ignored){/* The source is also retained by Activity state during recreation. */}});
        closed=true;hideKeyboard();popup.dismiss();if(renderer!=null){renderer.close();renderer=null;}
    }
    NotebookMarkdownDraft draft() {
        try{current=initial.withSource(editor.getText().toString());}catch(IllegalArgumentException ignored){}
        return current;
    }
    static void recover(File directory,int book,DraftCallback callback) {
        Handler main=new Handler(Looper.getMainLooper());
        io.execute(()->{
            NotebookMarkdownDraft draft=null;IOException error=null;
            try{draft=NotebookMarkdownDraft.read(directory,book);}catch(IOException e){error=e;}
            final NotebookMarkdownDraft result=draft;final IOException problem=error;
            main.post(()->callback.complete(result,problem));
        });
    }
    static void retire(File directory,NotebookMarkdownDraft draft) {
        io.execute(()->{try{NotebookMarkdownDraft.clear(directory,draft.token);}catch(IOException ignored){}});
    }
    private void hideKeyboard(){((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(editor.getWindowToken(),0);}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
