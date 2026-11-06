package org.eink.inkprobe.notebook;

import android.content.Context;
import android.view.*;
import android.widget.FrameLayout;

/** Shared note/chat corner controls, gesture policy and resize-outline presentation. */
final class NotebookWindowControls {
    static final String[] POSITIONS={"Left","Right","Top","Bottom","Floating window"};
    static String[] options(String noun){return new String[]{"Position…","Minimize","Close "+noun,"Smaller pane","Equal split / Reset size","Larger pane"};}
    interface Listener {
        void menu();void minimize();boolean beginMove(boolean resize);void endMove();void save();void position();boolean ready();
    }
    final NotebookIconButton menu,minimize;
    private final Context context;
    private final FrameLayout root,content;
    private final NotebookWorkspace workspace;
    private final NotebookWorkspace.Pane pane;
    private final Listener listener;
    private final NotebookResizeOutline outline;
    private final Grip moveGrip,resizeGrip;
    NotebookWindowControls(Context context,FrameLayout root,FrameLayout content,NotebookWorkspace workspace,NotebookWorkspace.Pane pane,Listener listener,String noun){
        this(context,root,content,workspace,pane,listener,noun,false);
    }
    NotebookWindowControls(Context context,FrameLayout root,FrameLayout content,NotebookWorkspace workspace,NotebookWorkspace.Pane pane,Listener listener,String noun,boolean embedded){
        this.context=context;this.root=root;this.content=content;this.workspace=workspace;this.pane=pane;this.listener=listener;
        outline=new NotebookResizeOutline(context);
        menu=new NotebookIconButton(context,NotebookIconDrawable.Icon.MORE,noun+" options; drag to move floating window");
        minimize=new NotebookIconButton(context,NotebookIconDrawable.Icon.MINIMIZE,"Minimize "+noun+"; drag to resize floating window");
        menu.setOnClickListener(v->listener.menu());minimize.setOnClickListener(v->listener.minimize());
        if(!embedded){
            FrameLayout.LayoutParams left=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.BOTTOM|Gravity.LEFT);left.setMargins(dp(5),0,0,dp(5));content.addView(menu,left);
            FrameLayout.LayoutParams right=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.BOTTOM|Gravity.RIGHT);right.setMargins(0,0,dp(5),dp(5));content.addView(minimize,right);
        }
        moveGrip=new Grip(false);resizeGrip=new Grip(true);menu.setOnTouchListener(moveGrip);minimize.setOnTouchListener(resizeGrip);
    }
    boolean resizing(){return resizeGrip.moving;}
    void cancel(){moveGrip.cancel();resizeGrip.cancel();outline.hide();}
    void presented(){
        if(resizeGrip.moving||!outline.showing()||!workspace.panes().contains(pane)||!listener.ready())return;
        NotebookWorkspace.Box box=workspace.box(pane,root.getWidth(),root.getHeight());
        if(content.getWidth()!=box.width||content.getHeight()!=box.height)return;
        content.postOnAnimation(()->{
            if(!outline.showing()||!workspace.panes().contains(pane)||!listener.ready())return;
            NotebookWorkspace.Box current=workspace.box(pane,root.getWidth(),root.getHeight());
            if(!resizeGrip.moving&&content.getWidth()==current.width&&content.getHeight()==current.height)outline.hide();
        });
    }
    private final class Grip implements View.OnTouchListener {
        final boolean resize;
        final NotebookGripGesture gesture=new NotebookGripGesture(ViewConfiguration.get(context).getScaledTouchSlop());
        float downX,downY,startX,startY,startW,startH;
        boolean moving,rejected;
        Grip(boolean resize){this.resize=resize;}
        @Override public boolean onTouch(View view,MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){downX=event.getRawX();downY=event.getRawY();startX=pane.x;startY=pane.y;startW=pane.width;startH=pane.height;moving=rejected=false;gesture.begin(downX,downY);return true;}
            if(action==MotionEvent.ACTION_MOVE){begin(event.getRawX(),event.getRawY());if(moving)update(event.getRawX(),event.getRawY());return true;}
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
                if(action==MotionEvent.ACTION_UP)begin(event.getRawX(),event.getRawY());
                boolean click=gesture.finish(action==MotionEvent.ACTION_CANCEL),wasMoving=moving;
                if(wasMoving){if(action==MotionEvent.ACTION_CANCEL){restore();outline.hide();}else update(event.getRawX(),event.getRawY());moving=false;listener.save();listener.endMove();}
                else if(click&&!rejected&&action==MotionEvent.ACTION_UP)view.performClick();return true;
            }return true;
        }
        private void begin(float x,float y){
            if(gesture.move(x,y)&&!moving&&!rejected&&workspace.floating(pane)){moving=listener.beginMove(resize);rejected=!moving;if(moving&&!resize)outline.hide();}
        }
        private void update(float x,float y){
            float dx=x-downX,dy=y-downY;
            if(resize){
                float left=startX*(1-startW),top=startY*(1-startH);
                pane.width=NotebookWorkspace.clamp(startW+dx/Math.max(1,root.getWidth()),.3f,Math.min(.95f,1-left));
                pane.height=NotebookWorkspace.clamp(startH+dy/Math.max(1,root.getHeight()),.3f,Math.min(.95f,1-top));
                pane.x=NotebookWorkspace.clamp(left/(1-pane.width),0,1);pane.y=NotebookWorkspace.clamp(top/(1-pane.height),0,1);
                outline.show(root,workspace.box(pane,root.getWidth(),root.getHeight()));
            }else{pane.x=NotebookWorkspace.clamp(startX+dx/Math.max(1,root.getWidth()*(1-pane.width)),0,1);pane.y=NotebookWorkspace.clamp(startY+dy/Math.max(1,root.getHeight()*(1-pane.height)),0,1);listener.position();}
        }
        private void restore(){pane.x=startX;pane.y=startY;pane.width=startW;pane.height=startH;}
        void cancel(){if(moving)restore();moving=false;gesture.finish(true);outline.hide();}
    }
    private int dp(int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
}
