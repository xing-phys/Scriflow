package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.app.AlertDialog;
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
    private final TextView status,specifications;
    private final Spinner effort;
    private final NotebookButton save,remove,fetch,manage;
    private java.util.List<NotebookAiModel> savedModels=new java.util.ArrayList<>();
    private java.util.List<String> effortChoices=new java.util.ArrayList<>();
    private AlertDialog modelDialog;
    private NotebookAiClient.Cancellation modelRequest;
    private int modelGeneration;
    NotebookAiSettingsSection(Activity activity,LinearLayout body) {
        this.activity=activity;
        TextView title=label("AI chat",15); title.setTypeface(null,android.graphics.Typeface.BOLD); title.setPadding(0,dp(12),0,dp(4)); body.addView(title);
        body.addView(label("Fetch models, select the ones you want, then save. Model limits and thinking defaults are configured automatically.",12));
        provider=new Spinner(activity); provider.setContentDescription("AI provider");
        ArrayAdapter<NotebookAiConfig.Provider> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,NotebookAiConfig.Provider.values());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); provider.setAdapter(adapter);
        provider.setPopupBackgroundDrawable(NotebookUi.border(activity.getResources().getDisplayMetrics().density));
        body.addView(provider,new LinearLayout.LayoutParams(-1,dp(42)));
        url=field(body,"API base URL","https://…",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        model=field(body,"Model ID","Your provider's model ID",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        key=field(body,"API key","Enter API key",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSaveEnabled(false); key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        fetch=button("Fetch available models",this::fetchModels);
        body.addView(fetch,new LinearLayout.LayoutParams(-1,dp(38)));
        manage=button("Manage saved models",this::manageModels);body.addView(manage,new LinearLayout.LayoutParams(-1,dp(38)));
        specifications=label("",12);body.addView(specifications,new LinearLayout.LayoutParams(-1,-2));
        body.addView(label("Thinking effort",12));effort=new Spinner(activity);effort.setContentDescription("Thinking effort; Automatic uses the provider default");
        effort.setPopupBackgroundDrawable(NotebookUi.border(activity.getResources().getDisplayMetrics().density));body.addView(effort,new LinearLayout.LayoutParams(-1,dp(42)));
        LinearLayout actions=new LinearLayout(activity);
        save=button("Save AI settings",this::save); remove=button("Remove saved key",this::remove);
        actions.addView(save,new LinearLayout.LayoutParams(0,dp(38),1)); actions.addView(remove,new LinearLayout.LayoutParams(0,dp(38),1)); body.addView(actions);
        status=label("",12); status.setPadding(0,dp(4),0,dp(6)); body.addView(status);
        body.addView(label("Send shares your message and this chat's history with the configured provider. API usage may incur charges. "
            +"Keys are encrypted on this device. Notebook content is included only when you paste it or append a selection. Fetch models contacts the provider for available models and specifications. "
            +"For the official OpenAI endpoint, adding a model also reads its public OpenAI documentation without your key. Missing specs use provider defaults. "
            +"Custom endpoints use the OpenAI chat API over HTTPS; their API key can be blank.",12));
        android.text.TextWatcher changes=new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int after){}public void onTextChanged(CharSequence s,int a,int before,int c){updateSpecifications();}public void afterTextChanged(android.text.Editable value){}};
        model.addTextChangedListener(changes);
        url.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int after){}public void onTextChanged(CharSequence s,int a,int before,int c){loadModels();}public void afterTextChanged(android.text.Editable value){}});
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
        ((AutoCompleteTextView)model).setDropDownBackgroundDrawable(NotebookUi.border(activity.getResources().getDisplayMetrics().density));
        url.setText(NotebookAiSettings.baseUrl(activity,value)); model.setText(NotebookAiSettings.model(activity,value)); key.setText("");
        loadModels();
        boolean stored=NotebookAiSettings.hasKey(activity,value); key.setHint(stored?"Saved key · leave blank to keep":"Enter API key");
        remove.setAvailable(stored);
        status.setText(value==NotebookAiSettings.provider(activity)&&NotebookAiSettings.configured(activity)?"Saved · "+value.label:"Tap Save AI settings to use this provider.");
    }
    private void save() {
        NotebookAiConfig.Provider value=selected(); String base=url.getText().toString(),id=model.getText().toString(),credential=key.getText().toString();
        final NotebookAiModel profile;
        try{NotebookAiModel found=NotebookAiModelLibrary.find(savedModels,id.trim());profile=(found==null?new NotebookAiModel(id.trim()):found).withEffort(effortChoices.isEmpty()?"":effortChoices.get(effort.getSelectedItemPosition()));}
        catch(IllegalArgumentException error){status.setText(error.getMessage());return;}
        busy(true); status.setText("Saving AI settings…");
        new Thread(()->{
            String problem=null;
            try { NotebookAiSettings.connection(activity,value,base,id,credential).validate();
                NotebookAiSettings.saveModels(activity,value,base,NotebookAiModelLibrary.merge(NotebookAiSettings.models(activity,value,base),java.util.Collections.singletonList(profile)));
                NotebookAiSettings.save(activity,value,base,id,credential); }
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
        fetch.setAvailable(!value);
        manage.setAvailable(!value);effort.setEnabled(!value&&effortChoices.size()>1);
        save.setAvailable(!value); remove.setAvailable(!value&&NotebookAiSettings.hasKey(activity,selected()));
        provider.setEnabled(!value); url.setEnabled(!value); model.setEnabled(!value); key.setEnabled(!value);
    }
    private void fetchModels() {
        final NotebookAiConfig.Provider value=selected();
        final String base=url.getText().toString(),id=model.getText().toString(),credential=key.getText().toString();
        final int request=++modelGeneration; modelRequest=new NotebookAiClient.Cancellation();
        final NotebookAiClient.Cancellation cancel=modelRequest;
        busy(true); status.setText("Fetching available models…");
        new Thread(()->{
            java.util.List<NotebookAiModel> models=null; String problem=null;
            try { NotebookAiConfig config=NotebookAiSettings.connection(activity,value,base,id,credential);NotebookAiClient client=new NotebookAiClient();
                models=client.catalog(config,cancel);
                for(int i=0;i<models.size();i++)if(models.get(i).id.equals(id.trim()))models.set(i,enrich(client,config,models.get(i),cancel)); }
            catch (IllegalArgumentException error) { problem=error.getMessage(); }
            catch (java.security.GeneralSecurityException error) { problem="Cannot unlock the saved key. Enter your API key again."; }
            catch (java.io.IOException error) { problem=error.getMessage(); }
            final java.util.List<NotebookAiModel> result=models; final String failure=problem;
            activity.runOnUiThread(()->{
                if (activity.isDestroyed()||activity.isFinishing()||request!=modelGeneration) return;
                modelRequest=null; busy(false);
                if (failure!=null) { status.setText(failure); return; }
                status.setText(result.size()+" models fetched. Select models to add with automatic configuration.");
                chooseModels(result,false);
            });
        },"ai-models").start();
    }
    void cancelModels() {
        if(modelDialog!=null){modelDialog.dismiss();modelDialog=null;}
        if (modelRequest==null) return;
        ++modelGeneration; modelRequest.cancel(); modelRequest=null; busy(false); status.setText("Model fetch stopped. Tap Fetch available models to retry.");
    }
    private void loadModels(){
        if(selected()==null)return;savedModels=NotebookAiSettings.models(activity,selected(),url.getText().toString());
        java.util.List<String> ids=new java.util.ArrayList<>();for(NotebookAiModel value:savedModels)if(value.chat!=0)ids.add(value.id);
        ((AutoCompleteTextView)model).setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_dropdown_item_1line,ids));((AutoCompleteTextView)model).setThreshold(0);updateSpecifications();
    }
    private void updateSpecifications(){
        if(specifications==null||effort==null||selected()==null)return;
        String id=model.getText().toString().trim();NotebookAiModel value=NotebookAiModelLibrary.find(savedModels,id);
        if(value==null)try{value=new NotebookAiModel(id);}catch(IllegalArgumentException error){specifications.setText("Select a saved model or fetch available models.");effortChoices=new java.util.ArrayList<>(java.util.Collections.singletonList(""));setEfforts("");return;}
        specifications.setText(value.summary(selected())+(value.fetched>0?"\nChecked: "+java.text.DateFormat.getDateInstance().format(new java.util.Date(value.fetched)):""));
        effortChoices=new java.util.ArrayList<>();effortChoices.add("");effortChoices.addAll(value.efforts);setEfforts(value.effort);
    }
    private void setEfforts(String selected){
        java.util.List<String> labels=new java.util.ArrayList<>();for(String value:effortChoices)labels.add(value.isEmpty()?"Automatic (provider default)":value);
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,labels);adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);effort.setAdapter(adapter);
        effort.setSelection(Math.max(0,effortChoices.indexOf(selected)));effort.setEnabled(effortChoices.size()>1);
    }
    private void manageModels(){
        loadModels();if(savedModels.isEmpty()){status.setText("No saved models for this endpoint. Fetch models to add some.");return;}chooseModels(savedModels,true);
    }
    private void chooseModels(java.util.List<NotebookAiModel> models,boolean managing){
        java.util.List<NotebookAiModel> choices=new java.util.ArrayList<>();for(NotebookAiModel value:models)if(managing||value.chat!=0)choices.add(value);
        if(choices.isEmpty()){status.setText("This endpoint returned no models advertising text generation.");return;}
        String[] names=new String[choices.size()];boolean[] checked=new boolean[choices.size()];
        for(int i=0;i<choices.size();i++){NotebookAiModel value=choices.get(i);names[i]=value.name+(value.name.equals(value.id)?"":" · "+value.id)+"\n"+value.summary(selected());checked[i]=NotebookAiModelLibrary.find(savedModels,value.id)!=null;}
        modelDialog=new AlertDialog.Builder(activity).setTitle(managing?"Saved models":"Add models")
            .setMultiChoiceItems(names,checked,(dialog,which,on)->checked[which]=on).setNegativeButton("Cancel",null)
            .setPositiveButton(managing?"Keep selected":"Add selected",null).create();
        modelDialog.setOnShowListener(d->modelDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            java.util.List<NotebookAiModel> chosen=new java.util.ArrayList<>();for(int i=0;i<choices.size();i++)if(checked[i])chosen.add(choices.get(i));
            if(chosen.size()>NotebookAiModelLibrary.MAX_MODELS){Toast.makeText(activity,"Keep up to 64 saved models per endpoint",Toast.LENGTH_LONG).show();return;}
            if(chosen.isEmpty()&&!managing){Toast.makeText(activity,"Select at least one model",Toast.LENGTH_SHORT).show();return;}
            modelDialog.dismiss();addModels(chosen,managing);
        }));
        modelDialog.setOnDismissListener(d->modelDialog=null);NotebookUi.show(modelDialog);
    }
    private NotebookAiModel enrich(NotebookAiClient client,NotebookAiConfig config,NotebookAiModel value,NotebookAiClient.Cancellation cancel)throws java.io.IOException {
        try{return client.specifications(config,value,cancel);}
        catch(java.io.IOException error){cancel.check();NotebookAiModel cached=NotebookAiModelLibrary.find(NotebookAiSettings.models(activity,config.provider,config.baseUrl),value.id);return value.known()||cached==null?value:cached;}
    }
    private void addModels(java.util.List<NotebookAiModel> chosen,boolean managing){
        final NotebookAiConfig.Provider value=selected();final String base=url.getText().toString(),id=model.getText().toString(),credential=key.getText().toString();
        final int request=++modelGeneration;modelRequest=new NotebookAiClient.Cancellation();final NotebookAiClient.Cancellation cancel=modelRequest;
        busy(true);status.setText(managing?"Saving selected models…":"Configuring selected models…");
        new Thread(()->{
            String problem=null;java.util.List<NotebookAiModel> result=new java.util.ArrayList<>();
            try{
                NotebookAiConfig config=managing?new NotebookAiConfig(value,base,id,""):NotebookAiSettings.connection(activity,value,base,id,credential);
                NotebookAiClient client=new NotebookAiClient();for(NotebookAiModel item:chosen){cancel.check();NotebookAiModel updated=managing?item:enrich(client,config,item,cancel);
                    NotebookAiModel previous=NotebookAiModelLibrary.find(NotebookAiSettings.models(activity,value,base),item.id);
                    if(previous!=null&&updated.efforts.contains(previous.effort))updated=updated.withEffort(previous.effort);if(managing||updated.chat!=0)result.add(updated);}
                if(!managing&&result.isEmpty())throw new java.io.IOException("These models do not support text chat. Select models with a supported chat endpoint.");
                cancel.check();if(!managing)result=NotebookAiModelLibrary.merge(NotebookAiSettings.models(activity,value,base),result);
                NotebookAiSettings.saveModels(activity,value,base,result);
            }catch(java.security.GeneralSecurityException error){problem="Cannot unlock the saved key. Enter your API key again.";}
            catch(java.io.IOException|IllegalArgumentException error){problem=error.getMessage();}
            final String failure=problem;final java.util.List<NotebookAiModel> added=result;
            activity.runOnUiThread(()->{
                if(activity.isDestroyed()||activity.isFinishing()||request!=modelGeneration)return;modelRequest=null;busy(false);
                if(failure!=null){status.setText(failure);return;}loadModels();
                if(!added.isEmpty()&&NotebookAiModelLibrary.find(added,model.getText().toString().trim())==null)model.setText(added.get(0).id);
                int unknown=0;for(NotebookAiModel item:added)if(!item.known())unknown++;
                status.setText(added.size()+" models saved. "+(unknown>0?unknown+" use provider defaults because specs were not published or could not be fetched. ":"")+"Choose a model, then Save AI settings to use it.");
            });
        },"ai-model-specs").start();
    }
    private EditText field(LinearLayout body,String name,String hint,int type) {
        body.addView(label(name,12)); EditText field=name.equals("Model ID")?new AutoCompleteTextView(activity):new EditText(activity); field.setTextColor(Color.BLACK); field.setTextSize(14);
        if (field instanceof AutoCompleteTextView) field.setOnClickListener(v->{ AutoCompleteTextView picker=(AutoCompleteTextView)field; if (picker.getAdapter()!=null) picker.showDropDown(); });
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
