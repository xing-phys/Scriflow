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
        boolean contactStart();void contactEnd();void boundsChanged(Rect bounds);void closed();
        boolean activated();boolean mayRaise();void minimized();void options();void changed();
        void attachNotebook(NotebookAiChat chat);
        void open(NotebookAiConfig config,String sessionId,boolean rebind);
    }
    private final Activity activity;
    private final FrameLayout anchor;
    private final NotebookAiWorkspace workspace;
    private final NotebookAiWorkspace.Window window;
    private final NotebookWindowControls controls;
    private final Listener listener;
    private final NotebookWritingGate writingGate;
    private final Runnable refreshAfterWriting = this::refresh;
    private final PopupWindow popup;
    private final NotebookAiFrame frame;
    private final LinearLayout content;
    private final EditText composer;
    private final LinearLayout attachments;
    private final HorizontalScrollView attachmentStrip;
    private final TextView status;
    private final NotebookButton model;
    private final NotebookIconButton send,add;
    private final Handler main=new Handler(Looper.getMainLooper());
    private NotebookAiMarkdownView markdown;
    private NotebookAiSession session;
    private final Rect bounds=new Rect();
    private final java.util.List<Runnable> pendingSelections=new java.util.ArrayList<>();
    private boolean closed,hidden,contact,composerLoaded,settingText,optionsOpen,raisePending,raiseKeyboard;
    private String displayProblem="";
    private AlertDialog modelDialog;
    private String visibleSessionId;
    private int openingGeneration,rendererGeneration;
    private final Runnable saveDraft=this::saveDraft;
    private final View.OnLayoutChangeListener layout=(v,l,t,r,b,ol,ot,or,ob)->position();

    NotebookAiChat(Activity activity,FrameLayout anchor,NotebookAiWorkspace workspace,NotebookAiWorkspace.Window window,Listener listener) {
        this.activity=activity;this.anchor=anchor;this.workspace=workspace;this.window=window;this.listener=listener;
        writingGate=NotebookWritingGate.from(activity);
        content=new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL); content.setBackgroundColor(Color.WHITE);
        content.setPadding(dp(6),dp(4),dp(6),dp(4));
        frame=new NotebookAiFrame(activity,dp(2)){
            @Override public boolean dispatchTouchEvent(MotionEvent event){
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
                    if(!listener.contactStart())return true;contact=true;
                    Rect editorBounds=new Rect();
                    raiseKeyboard=composer.getGlobalVisibleRect(editorBounds)&&editorBounds.contains((int)event.getRawX(),(int)event.getRawY());
                    raisePending|=listener.activated();
                }
                try{return super.dispatchTouchEvent(event);}finally{
                    if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)finishContact();
                }
            }
        };
        frame.addView(content,new FrameLayout.LayoutParams(-1,-1));
        ensureRenderer();
        status=label("Loading AI settings…",12); status.setMaxLines(3); content.addView(status,new LinearLayout.LayoutParams(-1,-2));
        attachments=new LinearLayout(activity); attachmentStrip=new HorizontalScrollView(activity);
        attachmentStrip.setOverScrollMode(View.OVER_SCROLL_NEVER); attachmentStrip.setHorizontalScrollBarEnabled(false);
        attachmentStrip.addView(attachments); content.addView(attachmentStrip,new LinearLayout.LayoutParams(-1,-2));
        composer=new EditText(activity); composer.setTextColor(Color.BLACK); composer.setTextSize(12); composer.setGravity(Gravity.TOP|Gravity.START);
        composer.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        composer.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN);
        composer.setMinLines(1);composer.setMinimumHeight(dp(24));composer.setMaxLines(6); composer.setHint("Message AI…"); composer.setContentDescription("Message AI");
        composer.setFilters(new InputFilter[]{new InputFilter.LengthFilter(NotebookAiConversation.MAX_MESSAGE_BYTES)});
        composer.setSaveEnabled(false);
        composer.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) { }
            public void onTextChanged(CharSequence s,int start,int before,int count) {
                if(composer.getParent() instanceof View) ((View)composer.getParent()).requestLayout();
                if (settingText||closed) return; main.removeCallbacks(saveDraft); main.postDelayed(saveDraft,400); updateSend();
            }
            public void afterTextChanged(Editable value) { }
        });
        composer.setOnKeyListener((v,key,event)->{
            if (key==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_DOWN&&event.isCtrlPressed()) { send(); return true; }
            return false;
        });
        send=icon(NotebookIconDrawable.Icon.SEND,"Send message (Ctrl+Enter)",this::send);
        add=icon(NotebookIconDrawable.Icon.PLUS,"Attach a notebook",this::chooseNotebook);
        model=button("Choose model ▾",this::chooseModel);model.setTextSize(10);model.setSingleLine(true);model.setEllipsize(android.text.TextUtils.TruncateAt.END);
        model.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);model.setOnTouchListener(new ModelTap());
        controls=new NotebookWindowControls(activity,anchor,frame,workspace.geometry,window.pane,new NotebookWindowControls.Listener(){
            public void menu(){if(!contact){if(!listener.contactStart())return;contact=true;}optionsOpen=true;listener.options();}
            public void minimize(){NotebookAiChat.this.minimize();}
            public boolean beginMove(boolean resize){hideKeyboard();return contact;}
            public void endMove(){position();listener.changed();}
            public void save(){listener.changed();}public void position(){NotebookAiChat.this.position();}
            public boolean ready(){return !hidden&&!closed;}
        },"chat",true);
        layoutInput();
        frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->controls.presented());
        popup=new PopupWindow(frame,1,1,true); popup.setOutsideTouchable(false); popup.setTouchModal(false);
        NotebookAiFrame.configure(popup);popup.setAnimationStyle(0); popup.setElevation(0);
        popup.setEnterTransition(null); popup.setExitTransition(null);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED); popup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popup.setOnDismissListener(()->{if(!hidden&&!closed)close();});
        frame.getViewTreeObserver().addOnPreDrawListener(()->{
            if (!closed&&popup.isShowing()) {
                int[] origin=new int[2]; frame.getLocationOnScreen(origin);
                publish(new Rect(origin[0],origin[1],origin[0]+frame.getWidth(),origin[1]+frame.getHeight()));
            }
            return true;
        });
        refresh();
    }
    private void layoutInput(){
        NotebookAiComposer row=new NotebookAiComposer(activity,composer,controls.menu,add,model,send,controls.minimize);
        android.graphics.drawable.GradientDrawable outline=new android.graphics.drawable.GradientDrawable();outline.setColor(Color.WHITE);outline.setCornerRadius(dp(14));outline.setStroke(dp(1),Color.BLACK);row.setBackground(outline);row.setPadding(dp(3),dp(2),dp(3),dp(2));
        composer.setBackground(null);composer.setPadding(dp(4),dp(3),dp(4),dp(3));
        model.setPadding(dp(3),0,dp(3),0);
        content.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    private final class ModelTap implements View.OnTouchListener {
        final NotebookGripGesture gesture=new NotebookGripGesture(ViewConfiguration.get(activity).getScaledTouchSlop());
        public boolean onTouch(View view,MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN)gesture.begin(event.getRawX(),event.getRawY());
            else if(action==MotionEvent.ACTION_MOVE)gesture.move(event.getRawX(),event.getRawY());
            else if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_POINTER_UP)gesture.cancelTap();
            else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                gesture.move(event.getRawX(),event.getRawY());
                if(gesture.finish(action==MotionEvent.ACTION_CANCEL)&&event.getX()>=0&&event.getY()>=0&&event.getX()<view.getWidth()&&event.getY()<view.getHeight())view.performClick();
            }
            // Open the model dialog before the outer frame releases its native input barrier.
            return true;
        }
    }
    void show() {
        if(closed)return;hidden=false;ensureRenderer();anchor.removeOnLayoutChangeListener(layout);anchor.addOnLayoutChangeListener(layout);position();
        if(!NotebookPopupHost.show(popup,anchor,Gravity.TOP|Gravity.LEFT,bounds.left,bounds.top)){close();return;}
        if(session==null){
            NotebookAiConfig config=new NotebookAiConfig(NotebookAiConfig.Provider.valueOf(window.provider),window.url,window.model,"");
            openSession(config,window.sessionId,window.rebind);
        }else refresh();
    }
    String sessionId(){return session==null?window.sessionId:session.id;}
    String name(){return session==null||!session.loaded?window.title:NotebookAiHistory.title(session.chat);}
    NotebookAiConfig configuration(){return session==null?new NotebookAiConfig(NotebookAiConfig.Provider.valueOf(window.provider),window.url,window.model,""):session.config;}
    void raise(){raise(false);}
    private void raise(boolean focusComposer){
        if(closed)return;if(hidden){show();return;}
        hidden=true;popup.dismiss();hidden=false;position();
        if(!NotebookPopupHost.show(popup,anchor,Gravity.TOP|Gravity.LEFT,bounds.left,bounds.top)){close();return;}
        if(focusComposer)composer.post(()->{if(!closed&&!hidden){composer.requestFocus();((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).showSoftInput(composer,InputMethodManager.SHOW_IMPLICIT);}});
    }
    private void ensureRenderer(){
        if(markdown!=null)return;final int request=++rendererGeneration;
        markdown=new NotebookAiMarkdownView(activity,problem->{if(!closed&&request==rendererGeneration){displayProblem=problem;refresh();}});
        content.addView(markdown.view,0,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void releaseRenderer(){
        if(writingGate!=null)writingGate.cancel(refreshAfterWriting);
        ++rendererGeneration;if(markdown!=null){content.removeView(markdown.view);markdown.close();markdown=null;}
    }
    private void openSession(NotebookAiConfig publicConfig,String id,boolean rebind) {
        final int request=++openingGeneration;
        main.removeCallbacks(saveDraft);saveDraft();
        if(session!=null)session.close();session=null;composerLoaded=false;visibleSessionId=null;
        setText("");displayProblem="";refresh();
        // Saved sessions remain readable even if their credential was removed or cannot be unlocked.
        new Thread(()->{
            String credential="",problem="";
            NotebookAiConfig destination=publicConfig;boolean allowRebind=rebind,canSend=true;
            if(id!=null) {
                try {
                    NotebookAiHistory.Entry entry=NotebookAiHistory.entry(activity.getFilesDir(),id);
                    if(entry!=null){entry=entry.resolved(NotebookAiSettings.publicConfigs(activity));
                        if(entry.config!=null)destination=entry.config;else allowRebind=true;}
                } catch(java.io.IOException error){problem="Cannot restore this saved chat. Use History to reopen or delete it.";}
            }
            NotebookAiConfig configured=new NotebookAiConfig(destination.provider,NotebookAiSettings.baseUrl(activity,destination.provider),destination.model,"");
            if(!destination.sameEndpoint(configured)) {
                canSend=false;problem="Saved endpoint: "+destination.baseUrl+". Configure this endpoint and its key in Settings to send. History is readable.";
            } else try {credential=NotebookAiSettings.apiKey(activity,destination.provider);}
            catch(java.security.GeneralSecurityException|java.io.IOException error){canSend=false;problem="Cannot unlock this provider's key. You can read history; enter the key again in Settings to send.";}
            if(credential.isEmpty()&&destination.provider!=NotebookAiConfig.Provider.CUSTOM&&problem.isEmpty())
                problem="Set this provider's API key in Settings to send. Saved chats remain available.";
            final NotebookAiConfig connection=NotebookAiSettings.configuration(activity,destination.provider,destination.baseUrl,destination.model,credential);
            final boolean acceptedRebind=allowRebind;
            final String blockedSend=canSend?"":problem;
            final String notice=problem;
            main.post(()->{
                if(closed||request!=openingGeneration)return;
                displayProblem=notice;
                session=new NotebookAiSession(activity.getFilesDir(),connection,id,main::post,this::refresh);
                session.rebindAllowed=acceptedRebind;session.sendProblem=blockedSend;session.load();refresh();
            });
        },"ai-chat-open").start();
    }
    private void refresh() {
        if (closed||hidden||status==null) return;
        // Requests and persistence continue independently; only their presentation waits.
        // An explicit chat contact already owns the native writer barrier.
        if (!contact && writingGate!=null && !writingGate.isQuiet()) {
            writingGate.defer(refreshAfterWriting); return;
        }
        if (session!=null&&session.loaded&&(!composerLoaded||!session.id.equals(visibleSessionId))) {
            composerLoaded=true;visibleSessionId=session.id;setText(session.chat.draft);
            java.util.List<Runnable> pending=new java.util.ArrayList<>(pendingSelections); pendingSelections.clear();
            for (Runnable selection:pending) selection.run();
        }
        updateDestination();
        if (session!=null) {
            attachments.removeAllViews();
            for (int i=0;i<session.chat.draftImages.size();i++) {
                final int index=i; NotebookAiAttachment image=session.chat.draftImages.get(i);
                NotebookButton chip=attachmentChip(image,index);
                LinearLayout.LayoutParams item=new LinearLayout.LayoutParams(-2,dp(26));item.rightMargin=dp(6);item.bottomMargin=dp(6);
                attachments.addView(chip,item);
            }
            attachmentStrip.setVisibility(session.chat.draftImages.isEmpty()?View.GONE:View.VISIBLE);
        } else {
            attachments.removeAllViews();attachmentStrip.setVisibility(View.GONE);
        }
        if(markdown!=null)markdown.messages(session==null?java.util.Collections.emptyList():session.chat.messages);
        String text=session!=null&&session.loadFailed?session.status:!displayProblem.isEmpty()?displayProblem:session==null?"Loading AI settings…":session.status;
        status.setText(text); status.setVisibility(text.isEmpty()?View.GONE:View.VISIBLE);
        composer.setEnabled(session!=null&&session.loaded&&!session.loadFailed&&!session.busy&&!session.switching&&!session.chat.awaitingReply());
        model.setAvailable(session!=null&&session.loaded&&!session.switching);
        add.setAvailable(session!=null&&session.loaded&&!session.loadFailed&&!session.busy&&!session.switching&&!session.chat.awaitingReply());
        updateSend();listener.changed();
    }
    private void updateDestination(){
        NotebookAiConfig config=configuration();
        String name=config.specs==null?config.model:config.specs.name;model.setText(name+" ▾");
        model.setContentDescription(name+"; "+config.provider.label+"; choose a model for a new chat");
        model.setTooltipText(config.provider.label+" · "+config.model+"; choose a model for a new chat");
    }
    private void updateSend() {
        if (send==null) return;
        NotebookIconDrawable.Icon symbol=session!=null&&session.busy?NotebookIconDrawable.Icon.STOP:session!=null&&session.chat.awaitingReply()?NotebookIconDrawable.Icon.RETRY:session!=null&&session.saveFailed?NotebookIconDrawable.Icon.SAVE:NotebookIconDrawable.Icon.SEND;
        String name=symbol==NotebookIconDrawable.Icon.STOP?"Stop generating":symbol==NotebookIconDrawable.Icon.RETRY?"Retry last message":symbol==NotebookIconDrawable.Icon.SAVE?"Retry saving chat":"Send message (Ctrl+Enter)";
        send.setIcon(symbol);send.setContentDescription(name);send.setTooltipText(name);
        send.setAvailable(session!=null&&session.loaded&&!session.loadFailed&&!session.switching
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
        else { session.status=""; refresh(); }
    }
    private void send() {
        if (session==null) return;
        if (session.busy) { session.stop(); return; }
        if (session.saveFailed&&!session.chat.awaitingReply()) { session.retrySave(); return; }
        main.removeCallbacks(saveDraft); displayProblem="";
        boolean retry=session.chat.awaitingReply();
        session.send(composer.getText().toString()); if (session.busy) { if (!retry) setText(""); hideKeyboard(); }
    }
    void saveDraft() {
        if (session!=null&&composerLoaded) session.draft(composer.getText().toString());
    }
    private void setText(String text) { settingText=true; composer.setText(text); composer.setSelection(composer.length()); settingText=false; }
    private NotebookButton attachmentChip(NotebookAiAttachment image,int index) {
        String label=image.name.startsWith("Handwriting")?"Ink "+(index+1):image.name.startsWith("Selected image")?"Image "+(index+1):image.name;
        NotebookButton chip=button(label+"  ×",()->session.removeImage(index));
        chip.setTextSize(10);chip.setPadding(dp(7),0,dp(7),0);
        android.graphics.drawable.GradientDrawable border=new android.graphics.drawable.GradientDrawable();
        border.setColor(Color.WHITE);border.setCornerRadius(dp(1000));border.setStroke(dp(1),0xff999999);chip.setBackground(border);
        chip.setContentDescription("Remove attached "+image.name);chip.setTooltipText(image.name);return chip;
    }
    void copyLastReply() {
        if(session==null||session.chat.lastAnswer().isEmpty()) {
            Toast.makeText(activity,"No AI reply to copy",Toast.LENGTH_SHORT).show();return;
        }
        ((android.content.ClipboardManager)activity.getSystemService(Activity.CLIPBOARD_SERVICE))
            .setPrimaryClip(ClipData.newPlainText("AI reply",session.chat.lastAnswer()));
        Toast.makeText(activity,"AI reply copied as Markdown",Toast.LENGTH_SHORT).show();
    }
    private void chooseNotebook() {
        if(session==null||!session.loaded||session.loadFailed||session.busy||session.switching||session.chat.awaitingReply())return;
        hideKeyboard();optionsOpen=true;listener.attachNotebook(this);
    }
    private void chooseModel(){
        if(session==null||!session.loaded||session.switching||modelDialog!=null)return;
        if(!contact){if(!listener.contactStart())return;contact=true;}
        main.removeCallbacks(saveDraft);saveDraft();hideKeyboard();
        java.util.List<NotebookAiConfig> choices=NotebookAiSettings.savedModels(activity);
        if(choices.isEmpty()){Toast.makeText(activity,"Add models in Settings → AI chat → Fetch available models",Toast.LENGTH_LONG).show();finishContact();return;}
        String[] labels=new String[choices.size()];for(int i=0;i<choices.size();i++){NotebookAiConfig config=choices.get(i);labels[i]=config.provider.label+" · "+config.specs.name;}
        modelDialog=new AlertDialog.Builder(activity).setTitle("Model for a new chat")
            .setItems(labels,(dialog,index)->selectModel(choices.get(index)))
            .setNegativeButton("Cancel",null).create();
        modelDialog.setOnDismissListener(d->{modelDialog=null;finishContact();});NotebookUi.show(modelDialog);
    }
    private void selectModel(NotebookAiConfig config){
        main.removeCallbacks(saveDraft);saveDraft();
        new Thread(()->{
            String problem=null;try{NotebookAiSettings.selectModel(activity,config);}catch(java.io.IOException|IllegalArgumentException error){problem=error.getMessage();}
            final String failure=problem;main.post(()->{if(closed)return;if(failure!=null){Toast.makeText(activity,failure,Toast.LENGTH_LONG).show();return;}listener.open(config,null,false);});
        },"ai-model-select").start();
    }
    void position() {
        if(closed||hidden||window.pane.collapsed||anchor.getWidth()<1||anchor.getHeight()<1)return;
        NotebookWorkspace.Box box=workspace.geometry.box(window.pane,anchor.getWidth(),anchor.getHeight());
        int[] origin=new int[2];anchor.getLocationOnScreen(origin);int x=origin[0]+box.x,y=origin[1]+box.y;
        publish(new Rect(x,y,x+box.width,y+box.height));
        if(popup.isShowing())popup.update(x,y,box.width,box.height);else{popup.setWidth(box.width);popup.setHeight(box.height);}
        controls.presented();
    }
    void optionsFinished(){optionsOpen=false;finishContact();}
    void minimize(){
        if(closed||hidden)return;main.removeCallbacks(saveDraft);saveDraft();hidden=true;controls.cancel();
        if(modelDialog!=null)modelDialog.dismiss();
        hideKeyboard();popup.dismiss();anchor.removeOnLayoutChangeListener(layout);releaseRenderer();publish(new Rect());finishContact();listener.minimized();
    }
    private void publish(Rect next) { if (!bounds.equals(next)) { bounds.set(next); listener.boundsChanged(new Rect(bounds)); } }
    private void finishContact() {
        if (optionsOpen||modelDialog!=null&&modelDialog.isShowing()) return;
        if (contact) { contact=false; listener.contactEnd(); }
        queueRaise();
    }
    private void queueRaise() {
        if (raisePending) {
            raisePending=false;final boolean focusComposer=raiseKeyboard;raiseKeyboard=false;
            frame.post(()->{if(!closed&&!hidden&&workspace.active()==window&&listener.mayRaise())raise(focusComposer);});
        }
    }
    void close() {
        if(closed)return;main.removeCallbacks(saveDraft);saveDraft();closed=true;controls.cancel();anchor.removeOnLayoutChangeListener(layout);
        if(session!=null)session.close();if(modelDialog!=null)modelDialog.dismiss();
        pendingSelections.clear();hideKeyboard();popup.dismiss();releaseRenderer();publish(new Rect());optionsOpen=false;finishContact();listener.closed();
    }
    private void hideKeyboard() { ((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(composer.getWindowToken(),0); }
    private NotebookIconButton icon(NotebookIconDrawable.Icon icon,String name,Runnable action) {
        NotebookIconButton button=new NotebookIconButton(activity,icon,name); button.setTooltipText(name); button.setOnClickListener(v->action.run()); return button;
    }
    private NotebookButton button(String text,Runnable action) { NotebookButton button=new NotebookButton(activity); button.setText(text); button.setTextSize(12); button.setOnClickListener(v->action.run()); return button; }
    private TextView label(String text,int size) { TextView view=new TextView(activity); view.setText(text); view.setTextSize(size); view.setTextColor(Color.BLACK); return view; }
    private int dp(int n) { return Math.round(n*activity.getResources().getDisplayMetrics().density); }
}
