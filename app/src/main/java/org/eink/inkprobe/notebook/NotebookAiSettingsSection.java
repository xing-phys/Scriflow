package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

/** A provider's credential is never copied into a visible or saved-state text field. */
final class NotebookAiSettingsSection {
    private final Activity activity;
    private final Spinner provider;
    private final EditText url,model,key;
    private final TextView status;
    private final NotebookButton save,remove;
    NotebookAiSettingsSection(Activity activity,LinearLayout body) {
        this.activity=activity;
        TextView title=label("AI chat",15); title.setTypeface(null,android.graphics.Typeface.BOLD); title.setPadding(0,dp(12),0,dp(4)); body.addView(title);
        body.addView(label("Choose a provider and model, then save your API settings.",12));
        provider=new Spinner(activity); provider.setContentDescription("AI provider");
        ArrayAdapter<NotebookAiConfig.Provider> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,NotebookAiConfig.Provider.values());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); provider.setAdapter(adapter);
        provider.setPopupBackgroundDrawable(NotebookUi.border(activity.getResources().getDisplayMetrics().density));
        body.addView(provider,new LinearLayout.LayoutParams(-1,dp(42)));
        url=field(body,"API base URL","https://…",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        model=field(body,"Model ID","Your provider's model ID",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        key=field(body,"API key","Enter API key",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSaveEnabled(false); key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        LinearLayout actions=new LinearLayout(activity);
        save=button("Save AI settings",this::save); remove=button("Remove saved key",this::remove);
        actions.addView(save,new LinearLayout.LayoutParams(0,dp(38),1)); actions.addView(remove,new LinearLayout.LayoutParams(0,dp(38),1)); body.addView(actions);
        status=label("",12); status.setPadding(0,dp(4),0,dp(6)); body.addView(status);
        body.addView(label("Send shares your message and this chat's history with the configured provider. API usage may incur charges. "
            +"Keys are encrypted on this device. Chat history stays in app storage; notebook content is included only if you paste it into chat. "
            +"Custom endpoints use the OpenAI chat API over HTTPS; their API key can be blank.",12));
        provider.setSelection(NotebookAiSettings.provider(activity).ordinal());
        provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id) { load(); }
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        load();
    }
    private NotebookAiConfig.Provider selected() { return (NotebookAiConfig.Provider)provider.getSelectedItem(); }
    private void load() {
        NotebookAiConfig.Provider value=selected(); if (value==null) return;
        url.setText(NotebookAiSettings.baseUrl(activity,value)); model.setText(NotebookAiSettings.model(activity,value)); key.setText("");
        boolean stored=NotebookAiSettings.hasKey(activity,value); key.setHint(stored?"Saved key · leave blank to keep":"Enter API key");
        remove.setAvailable(stored);
        status.setText(value==NotebookAiSettings.provider(activity)&&NotebookAiSettings.configured(activity)?"Saved · "+value.label:"Tap Save AI settings to use this provider.");
    }
    private void save() {
        NotebookAiConfig.Provider value=selected(); String base=url.getText().toString(),id=model.getText().toString(),credential=key.getText().toString();
        busy(true); status.setText("Saving AI settings…");
        new Thread(()->{
            String problem=null;
            try { NotebookAiSettings.save(activity,value,base,id,credential); }
            catch (IllegalArgumentException error) { problem=error.getMessage(); }
            catch (java.security.GeneralSecurityException error) { problem="Cannot unlock the API key. Enter it again, or remove the saved key and retry."; }
            catch (java.io.IOException error) { problem=error.getMessage(); }
            final String failure=problem;
            activity.runOnUiThread(()->{ if (activity.isDestroyed()) return; busy(false); if (failure==null) load(); else status.setText(failure); });
        },"ai-settings").start();
    }
    private void remove() {
        NotebookAiConfig.Provider value=selected(); busy(true);
        new Thread(()->{
            String problem=null;
            try { NotebookAiSettings.removeKey(activity,value); } catch (java.io.IOException error) { problem=error.getMessage(); }
            final String failure=problem;
            activity.runOnUiThread(()->{ if (activity.isDestroyed()) return; busy(false); load(); if (failure!=null) status.setText(failure); });
        },"ai-settings").start();
    }
    private void busy(boolean value) {
        save.setAvailable(!value); remove.setAvailable(!value&&NotebookAiSettings.hasKey(activity,selected()));
        provider.setEnabled(!value); url.setEnabled(!value); model.setEnabled(!value); key.setEnabled(!value);
    }
    private EditText field(LinearLayout body,String name,String hint,int type) {
        body.addView(label(name,12)); EditText field=new EditText(activity); field.setTextColor(Color.BLACK); field.setTextSize(14);
        field.setSingleLine(true); field.setInputType(type|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN);
        field.setHint(hint); field.setContentDescription(name); body.addView(field,new LinearLayout.LayoutParams(-1,dp(42))); return field;
    }
    private NotebookButton button(String text,Runnable action) {
        NotebookButton view=new NotebookButton(activity); view.setText(text); view.setTextSize(13); view.setOnClickListener(v->action.run()); return view;
    }
    private TextView label(String text,int size) {
        TextView view=new TextView(activity); view.setText(text); view.setTextSize(size); view.setTextColor(Color.BLACK); view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    private int dp(int n) { return Math.round(n*activity.getResources().getDisplayMetrics().density); }
}
