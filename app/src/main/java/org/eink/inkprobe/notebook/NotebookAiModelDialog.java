package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Optional per-model edits, initialized from fetched metadata with a reset to those defaults. */
final class NotebookAiModelDialog {
    private final Activity activity;
    private final NotebookAiModel model;
    private final EditText context,input,output,reply,levels;
    private final Spinner images,effort;
    private final TextView error;
    private List<String> effortChoices=new ArrayList<>();
    private boolean filling;
    private final LinearLayout body;
    static AlertDialog show(Activity activity,NotebookAiModel model,Consumer<NotebookAiModel> apply){return new NotebookAiModelDialog(activity,model).show(apply);}
    private NotebookAiModelDialog(Activity activity,NotebookAiModel model){
        this.activity=activity;this.model=model;body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(12),0,dp(12),dp(8));
        String source=model.source.isEmpty()?"Defaults not reported by this provider.":"Defaults: "+model.source+(model.fetched>0?" · "+java.text.DateFormat.getDateInstance().format(new Date(model.fetched)):"");
        body.addView(label(source+"\n"+(model.edited()?"Your manual overrides are shown below.":"Parameters are filled automatically. You can adjust them below."),12));
        context=number("Context window (tokens)");input=number("Input token limit");output=number("Maximum output tokens");reply=number("Reply token budget");
        body.addView(label("Blank limits use the provider default. A blank reply budget uses the app's automatic budget.",11));
        images=spinner("Image input");setChoices(images,Arrays.asList("Not reported","Supported","Unsupported"));
        levels=field("Supported thinking efforts",InputType.TYPE_CLASS_TEXT);levels.setHint("Comma separated; leave blank for provider defaults");effort=spinner("Thinking effort");
        error=label("",12);body.addView(error);
        levels.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int after){}public void onTextChanged(CharSequence s,int a,int before,int c){if(!filling)updateEfforts(selectedEffort());}public void afterTextChanged(Editable value){}});
        fill(model);
    }
    private AlertDialog show(Consumer<NotebookAiModel> apply){
        ScrollView scroll=new ScrollView(activity);scroll.setBackgroundColor(Color.WHITE);scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle(model.name).setView(scroll).setNegativeButton("Cancel",null)
            .setNeutralButton("Use fetched defaults",null).setPositiveButton("Apply",null).create();
        dialog.setOnShowListener(d->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{fill(model.automatic());error.setText("Fetched defaults restored. Tap Apply, then Save AI settings.");});
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                try{NotebookAiModel edited=model.edit(value(context),value(input),value(output),value(reply),images.getSelectedItemPosition()==0?-1:images.getSelectedItemPosition()==1?1:0,levels.getText().toString(),selectedEffort());apply.accept(edited);dialog.dismiss();}
                catch(IllegalArgumentException problem){error.setText(problem.getMessage());}
            });
        });NotebookUi.show(dialog);return dialog;
    }
    private void fill(NotebookAiModel model){
        filling=true;context.setText(text(model.context));input.setText(text(model.input));output.setText(text(model.output));reply.setText(text(model.reply));images.setSelection(model.vision==-1?0:model.vision==1?1:2);
        levels.setText(String.join(", ",model.efforts));filling=false;updateEfforts(model.effort);error.setText("");
    }
    private void updateEfforts(String selected){
        effortChoices=new ArrayList<>();effortChoices.add("");for(String value:levels.getText().toString().split(",")){value=value.trim();if(NotebookAiModel.EFFORTS.contains(value)&&!effortChoices.contains(value))effortChoices.add(value);}
        List<String> names=new ArrayList<>();for(String value:effortChoices)names.add(value.isEmpty()?"Automatic (provider default)":value);setChoices(effort,names);effort.setSelection(Math.max(0,effortChoices.indexOf(selected)));
    }
    private String selectedEffort(){int index=effort.getSelectedItemPosition();return index>=0&&index<effortChoices.size()?effortChoices.get(index):"";}
    private static int value(EditText field){String text=field.getText().toString().trim();if(text.isEmpty())return 0;try{int value=Integer.parseInt(text);if(value<0||value>100000000)throw new NumberFormatException();return value;}catch(NumberFormatException error){throw new IllegalArgumentException("Enter whole token limits between 0 and 100,000,000, or leave blank.");}}
    private static String text(int value){return value>0?Integer.toString(value):"";}
    private EditText number(String name){EditText field=field(name,InputType.TYPE_CLASS_NUMBER);field.setHint("Provider default");return field;}
    private EditText field(String name,int type){
        body.addView(label(name,12));EditText field=new EditText(activity);field.setTextColor(Color.BLACK);field.setTextSize(14);field.setSingleLine(true);field.setInputType(type|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN);field.setSaveEnabled(false);field.setContentDescription(name);body.addView(field,new LinearLayout.LayoutParams(-1,dp(40)));return field;
    }
    private Spinner spinner(String name){body.addView(label(name,12));Spinner spinner=new Spinner(activity);spinner.setContentDescription(name);spinner.setPopupBackgroundDrawable(NotebookUi.border(activity.getResources().getDisplayMetrics().density));body.addView(spinner,new LinearLayout.LayoutParams(-1,dp(40)));return spinner;}
    private void setChoices(Spinner spinner,List<String> values){ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,values);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);spinner.setAdapter(adapter);}
    private TextView label(String text,int size){TextView view=new TextView(activity);view.setText(text);view.setTextSize(size);view.setTextColor(Color.BLACK);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}
