package org.eink.inkprobe.notebook;

import android.content.Context;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

/** The message bubble expands between two pairs of independent window actions. */
final class NotebookAiComposer extends ViewGroup {
    private final EditText editor;
    private final View menu, add, model, send, minimize, bubble;
    private final int actionSize, actionGap, modelLimit, editorMinimum, inset;
    private boolean expanded;
    private int bubbleWidth, bubbleHeight;

    NotebookAiComposer(Context context, EditText editor, View menu, View add, View model, View send, View minimize, View bubble) {
        super(context);
        this.editor=editor;this.menu=menu;this.add=add;this.model=model;this.send=send;this.minimize=minimize;this.bubble=bubble;
        float density=context.getResources().getDisplayMetrics().density;
        actionSize=Math.round(22*density);actionGap=Math.round(2*density);modelLimit=Math.round(104*density);
        editorMinimum=Math.round(48*density);inset=Math.round(3*density);
        for(View child:new View[]{bubble,menu,add,editor,model,send,minimize})addView(child);
    }

    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=MeasureSpec.getSize(widthSpec);
        int inner=Math.max(1,width-getPaddingLeft()-getPaddingRight());
        int icon=Math.min(actionSize,Math.max(1,(inner-4*actionGap-1)/4));
        int gap=Math.min(actionGap,Math.max(0,(inner-4*icon-1)/4));
        int fixed=MeasureSpec.makeMeasureSpec(icon,MeasureSpec.EXACTLY);
        for(View child:new View[]{menu,add,send,minimize})child.measure(fixed,fixed);
        bubbleWidth=Math.max(1,inner-4*icon-4*gap);
        int available=Math.max(1,bubbleWidth-2*inset);
        int maximumModel=Math.max(0,Math.min(modelLimit,available-editorMinimum));
        model.measure(MeasureSpec.makeMeasureSpec(maximumModel,MeasureSpec.AT_MOST),fixed);
        int compact=Math.max(1,available-model.getMeasuredWidth());
        CharSequence value=editor.getText();
        int textSpace=Math.max(1,compact-editor.getCompoundPaddingLeft()-editor.getCompoundPaddingRight());
        expanded=value.toString().indexOf('\n')>=0 || Layout.getDesiredWidth(value,editor.getPaint())>textSpace;
        editor.measure(MeasureSpec.makeMeasureSpec(expanded?available:compact,MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));
        bubbleHeight=2*inset+(expanded?editor.getMeasuredHeight()+icon:Math.max(icon,editor.getMeasuredHeight()));
        bubble.measure(MeasureSpec.makeMeasureSpec(bubbleWidth,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(bubbleHeight,MeasureSpec.EXACTLY));
        setMeasuredDimension(resolveSize(width,widthSpec),resolveSize(getPaddingTop()+getPaddingBottom()+Math.max(icon,bubbleHeight),heightSpec));
    }

    @Override protected void onLayout(boolean changed,int left,int top,int right,int bottom) {
        int x=getPaddingLeft(), icon=menu.getMeasuredWidth();
        int inner=Math.max(1,getWidth()-getPaddingLeft()-getPaddingRight());
        int gap=Math.min(actionGap,Math.max(0,(inner-4*icon-1)/4));
        int bubbleTop=getPaddingTop(), actionTop=bubbleTop+bubbleHeight-inset-icon;
        menu.layout(x,actionTop,x+icon,actionTop+icon);x+=icon+gap;
        add.layout(x,actionTop,x+icon,actionTop+icon);x+=icon+gap;
        int bubbleLeft=x, bubbleRight=bubbleLeft+bubbleWidth;
        bubble.layout(bubbleLeft,bubbleTop,bubbleRight,bubbleTop+bubbleHeight);
        int editorTop=expanded?bubbleTop+inset:bubbleTop+(bubbleHeight-editor.getMeasuredHeight())/2;
        editor.layout(bubbleLeft+inset,editorTop,bubbleLeft+inset+editor.getMeasuredWidth(),editorTop+editor.getMeasuredHeight());
        int modelTop=expanded?bubbleTop+bubbleHeight-inset-icon:bubbleTop+(bubbleHeight-icon)/2;
        model.layout(bubbleRight-inset-model.getMeasuredWidth(),modelTop,bubbleRight-inset,modelTop+icon);
        x=bubbleRight+gap;send.layout(x,actionTop,x+icon,actionTop+icon);x+=icon+gap;
        minimize.layout(x,actionTop,x+icon,actionTop+icon);
    }
}
