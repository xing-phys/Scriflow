package org.eink.inkprobe.notebook;

import android.content.Context;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

/** One compact row until the message needs more room, then a full-width editor above the actions. */
final class NotebookAiComposer extends ViewGroup {
    private final EditText editor;
    private final View menu, add, model, send, minimize;
    private final int actionSize, actionGap, modelLimit, editorMinimum;
    private boolean expanded;

    NotebookAiComposer(Context context, EditText editor, View menu, View add, View model, View send, View minimize) {
        super(context);
        this.editor=editor;this.menu=menu;this.add=add;this.model=model;this.send=send;this.minimize=minimize;
        float density=context.getResources().getDisplayMetrics().density;
        actionSize=Math.round(22*density);actionGap=Math.round(4*density);modelLimit=Math.round(104*density);editorMinimum=Math.round(48*density);
        for(View child:new View[]{menu,add,editor,model,send,minimize})addView(child);
    }

    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=MeasureSpec.getSize(widthSpec);
        int inner=Math.max(1,width-getPaddingLeft()-getPaddingRight());
        int icon=Math.min(actionSize,Math.max(1,(inner-2*actionGap)/4));
        int gap=Math.min(actionGap,Math.max(0,(inner-4*icon)/2));
        int fixed=MeasureSpec.makeMeasureSpec(icon,MeasureSpec.EXACTLY);
        for(View child:new View[]{menu,add,send,minimize})child.measure(fixed,fixed);
        int maximumModel=Math.max(0,Math.min(modelLimit,inner-4*icon-2*gap-editorMinimum));
        model.measure(MeasureSpec.makeMeasureSpec(maximumModel,MeasureSpec.AT_MOST),fixed);
        int compact=Math.max(1,inner-4*icon-2*gap-model.getMeasuredWidth());
        CharSequence value=editor.getText();
        int textSpace=Math.max(1,compact-editor.getCompoundPaddingLeft()-editor.getCompoundPaddingRight());
        expanded=value.toString().indexOf('\n')>=0 || Layout.getDesiredWidth(value,editor.getPaint())>textSpace;
        int editorWidth=expanded?inner:compact;
        editor.measure(MeasureSpec.makeMeasureSpec(editorWidth,MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        int height=getPaddingTop()+getPaddingBottom()+(expanded?editor.getMeasuredHeight()+icon:Math.max(icon,editor.getMeasuredHeight()));
        setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(height,heightSpec));
    }

    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        int x=getPaddingLeft(), rowBottom=getHeight()-getPaddingBottom();
        int icon=menu.getMeasuredWidth(), rowTop=rowBottom-icon;
        int inner=Math.max(1,getWidth()-getPaddingLeft()-getPaddingRight());
        int gap=Math.min(actionGap,Math.max(0,(inner-4*icon)/2));
        if(expanded) editor.layout(x,getPaddingTop(),x+editor.getMeasuredWidth(),getPaddingTop()+editor.getMeasuredHeight());
        menu.layout(x,rowTop,x+icon,rowBottom);x+=icon+gap;
        add.layout(x,rowTop,x+icon,rowBottom);x+=icon;
        if(!expanded) {
            int y=rowBottom-editor.getMeasuredHeight();
            editor.layout(x,y,x+editor.getMeasuredWidth(),rowBottom);x+=editor.getMeasuredWidth();
        } else x=getWidth()-getPaddingRight()-2*icon-gap-model.getMeasuredWidth();
        model.layout(x,rowTop,x+model.getMeasuredWidth(),rowBottom);x+=model.getMeasuredWidth();
        send.layout(x,rowTop,x+icon,rowBottom);x+=icon+gap;
        minimize.layout(x,rowTop,x+icon,rowBottom);
    }
}
