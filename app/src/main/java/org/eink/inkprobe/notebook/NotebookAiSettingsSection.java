package org.eink.inkprobe.notebook;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.util.*;

/** Connection fields followed by name-only model rows and per-model settings. */
final class NotebookAiSettingsSection {
    private final Activity activity;
    private final Spinner provider;
    private final EditText providerName,url,key;
    private final TextView status,empty,providerType;
    private final ListView models;
    private final NotebookButton save,remove,fetch,addProvider;
    private final Set<String> checked=new LinkedHashSet<>();
    private List<NotebookAiModel> available=new ArrayList<>();
    private final BaseAdapter rows;
    private AlertDialog modelDialog,providerDialog;
    private NotebookAiClient.Cancellation modelRequest;
    private int modelGeneration;
    private boolean loading,working;

    NotebookAiSettingsSection(Activity activity,LinearLayout body) {
        this.activity=activity;
        TextView title=label("AI chat",14);title.setTypeface(null,android.graphics.Typeface.BOLD);title.setPadding(dp(10),dp(3),dp(10),dp(3));
        title.setBackground(NotebookUi.rounded(activity.getResources().getDisplayMetrics().density,5));
        LinearLayout.LayoutParams titleSpace=new LinearLayout.LayoutParams(-1,dp(34));titleSpace.setMargins(0,dp(14),0,dp(6));body.addView(title,titleSpace);
        body.addView(label("Enter your connection, fetch models, and check the ones you want. Use a model's settings icon to adjust its parameters.",12));
        provider=new Spinner(activity);provider.setContentDescription("Saved AI provider");
        provider.setPopupBackgroundDrawable(NotebookUi.border(activity.getResources().getDisplayMetrics().density));body.addView(provider,new LinearLayout.LayoutParams(-1,dp(42)));
        providerType=label("",12);body.addView(providerType,new LinearLayout.LayoutParams(-1,dp(26)));
        addProvider=button("Add provider…",this::addProvider);body.addView(addProvider,new LinearLayout.LayoutParams(-1,dp(38)));
        providerName=field(body,"Provider name","Name for this provider",InputType.TYPE_CLASS_TEXT);
        url=field(body,"API base URL","https://…",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        key=field(body,"API key","Enter API key",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        key.setSaveEnabled(false);key.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        fetch=button("Fetch available models",this::fetchModels);body.addView(fetch,new LinearLayout.LayoutParams(-1,dp(38)));
        empty=label("Fetch models to show them here.",12);empty.setPadding(0,dp(6),0,dp(6));body.addView(empty);
        models=new ListView(activity);models.setBackgroundColor(Color.WHITE);models.setCacheColorHint(Color.WHITE);
        models.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));models.setOverScrollMode(View.OVER_SCROLL_NEVER);
        models.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&(v.canScrollVertically(1)||v.canScrollVertically(-1)))v.getParent().requestDisallowInterceptTouchEvent(true);
            if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL)v.getParent().requestDisallowInterceptTouchEvent(false);return false;});
        rows=new BaseAdapter(){
            public int getCount(){return available.size();}public Object getItem(int position){return available.get(position);}public long getItemId(int position){return position;}
            public View getView(int position,View recycled,ViewGroup parent){
                NotebookAiModel model=available.get(position);LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);
                CheckBox choice=new CheckBox(activity);choice.setText(model.name);choice.setTextSize(14);choice.setTextColor(Color.BLACK);
                choice.setSingleLine(true);choice.setEllipsize(TextUtils.TruncateAt.END);choice.setChecked(checked.contains(model.id));choice.setEnabled(!working);
                choice.setButtonTintList(android.content.res.ColorStateList.valueOf(Color.BLACK));choice.setContentDescription("Use model: "+model.name);
                choice.setOnCheckedChangeListener((button,on)->{
                    if(working){button.setChecked(checked.contains(model.id));return;}
                    if(on&&checked.size()>=NotebookAiModelLibrary.MAX_MODELS){button.setChecked(false);Toast.makeText(activity,"Select up to 64 models per endpoint",Toast.LENGTH_SHORT).show();return;}
                    if(on)checked.add(model.id);else checked.remove(model.id);status.setText("Save AI settings to apply your selection.");
                });
                row.addView(choice,new LinearLayout.LayoutParams(0,dp(48),1));
                NotebookIconButton settings=new NotebookIconButton(activity,NotebookIconDrawable.Icon.SETTINGS,"Settings for "+model.name);
                settings.setTooltipText("Settings for "+model.name);settings.setAvailable(!working);settings.setOnClickListener(v->editModel(model));
                row.addView(settings,new LinearLayout.LayoutParams(dp(40),dp(40)));return row;
            }
        };
        models.setAdapter(rows);body.addView(models,new LinearLayout.LayoutParams(-1,0));
        LinearLayout actions=new LinearLayout(activity);save=button("Save AI settings",this::save);remove=button("Remove saved key",this::remove);
        actions.addView(save,new LinearLayout.LayoutParams(0,dp(38),1));LinearLayout.LayoutParams removeSpace=new LinearLayout.LayoutParams(0,dp(38),1);removeSpace.leftMargin=dp(6);actions.addView(remove,removeSpace);body.addView(actions);
        status=label("",12);status.setPadding(0,dp(4),0,dp(6));body.addView(status);
        body.addView(label("Send shares your chat and explicitly attached selections with the configured provider. API usage may incur charges. Keys are encrypted on this device. "
            +"Fetching models retrieves their available defaults. The official OpenAI endpoint also uses public OpenAI model documentation, without your key. "
            +"Model settings are filled automatically; manual edits are optional. Custom HTTPS endpoints can use a blank key.",12));
        url.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int after){}public void onTextChanged(CharSequence s,int a,int before,int c){if(!loading)loadModels();}public void afterTextChanged(Editable value){}});
        provider.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> parent,View view,int position,long id){load();}public void onNothingSelected(AdapterView<?> parent){}
        });reloadProfiles(NotebookAiSettings.activeProfile(activity).id);
    }
    private NotebookAiSettings.Profile selected(){return (NotebookAiSettings.Profile)provider.getSelectedItem();}
    private void reloadProfiles(String id){
        java.util.List<NotebookAiSettings.Profile> profiles=NotebookAiSettings.profiles(activity);
        ArrayAdapter<NotebookAiSettings.Profile> choices=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,profiles);
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);provider.setAdapter(choices);
        int index=0;for(int i=0;i<profiles.size();i++)if(profiles.get(i).id.equals(id)){index=i;break;}
        provider.setSelection(index);load();
    }
    private void addProvider(){
        if(working||providerDialog!=null)return;
        LinearLayout fields=new LinearLayout(activity);fields.setOrientation(LinearLayout.VERTICAL);fields.setPadding(dp(20),dp(8),dp(20),dp(8));
        fields.addView(label("Provider type",13));Spinner type=new Spinner(activity);
        ArrayAdapter<NotebookAiConfig.Provider> types=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,NotebookAiConfig.Provider.values());
        types.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);type.setAdapter(types);type.setSelection(NotebookAiConfig.Provider.CUSTOM.ordinal());
        fields.addView(type,new LinearLayout.LayoutParams(-1,dp(42)));
        EditText name=new EditText(activity);name.setSingleLine(true);name.setHint("Provider name");name.setContentDescription("New provider name");
        fields.addView(name,new LinearLayout.LayoutParams(-1,dp(48)));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Add AI provider").setView(fields)
            .setNegativeButton("Cancel",null).setPositiveButton("Add",null).create();providerDialog=dialog;
        dialog.setOnDismissListener(d->{if(providerDialog==dialog)providerDialog=null;});NotebookUi.show(dialog);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            NotebookAiConfig.Provider chosen=(NotebookAiConfig.Provider)type.getSelectedItem();
            String label=name.getText().toString().trim();if(label.isEmpty())label=chosen.label;
            try{NotebookAiSettings.Profile created=NotebookAiSettings.addProfile(activity,chosen,label);dialog.dismiss();reloadProfiles(created.id);}
            catch(java.io.IOException|IllegalArgumentException error){name.setError(error.getMessage());}
        });
    }
    private void load(){
        NotebookAiSettings.Profile value=selected();if(value==null)return;loading=true;
        providerType.setText("Provider type · "+value.provider.label);
        providerName.setText(value.name);
        url.setText(NotebookAiSettings.baseUrl(activity,value));key.setText("");loading=false;loadModels();
        boolean stored=NotebookAiSettings.hasKey(activity,value);key.setHint(stored?"Saved key · leave blank to keep":"Enter API key");remove.setAvailable(stored);
        status.setText(NotebookAiSettings.configured(activity,value)?"Saved · "+value.name:"Fetch models, check your selection, then Save AI settings.");
    }
    private void loadModels(){
        if(selected()==null)return;String base=url.getText().toString();available=NotebookAiSettings.catalog(activity,selected(),base);checked.clear();
        for(NotebookAiModel model:NotebookAiSettings.models(activity,selected(),base))checked.add(model.id);
        String active=NotebookAiSettings.model(activity,selected());
        if(available.isEmpty()&&!active.isEmpty()&&(NotebookAiSettings.hasKey(activity,selected())||NotebookAiSettings.configured(activity,selected()))&&new NotebookAiConfig(selected().provider,base,"","").sameEndpoint(new NotebookAiConfig(selected().provider,NotebookAiSettings.baseUrl(activity,selected()),"",""))){
            available.add(new NotebookAiModel(active));checked.add(active);
        }
        available.removeIf(model->model.chat==0);renderModels();
    }
    private void renderModels(){
        rows.notifyDataSetChanged();models.setVisibility(available.isEmpty()?View.GONE:View.VISIBLE);empty.setVisibility(available.isEmpty()?View.VISIBLE:View.GONE);
        models.setLayoutParams(new LinearLayout.LayoutParams(-1,dp(Math.min(8,available.size())*49)));models.requestLayout();
    }
    private void busy(boolean value){
        working=value;fetch.setAvailable(!value);save.setAvailable(!value);remove.setAvailable(!value&&NotebookAiSettings.hasKey(activity,selected()));addProvider.setAvailable(!value);
        provider.setEnabled(!value);providerName.setEnabled(!value);url.setEnabled(!value);key.setEnabled(!value);rows.notifyDataSetChanged();
    }
    private NotebookAiModel enrich(NotebookAiClient client,NotebookAiConfig config,NotebookAiModel model,NotebookAiClient.Cancellation cancel)throws java.io.IOException{
        try{return client.specifications(config,model,cancel).refreshed(model);}catch(java.io.IOException error){cancel.check();return model;}
    }
    private void fetchModels(){
        final NotebookAiSettings.Profile value=selected();final String base=url.getText().toString(),credential=key.getText().toString();
        final List<NotebookAiModel> before=new ArrayList<>(available);final Set<String> enabled=new LinkedHashSet<>(checked);
        final String active=NotebookAiSettings.model(activity,value);final int request=++modelGeneration;
        modelRequest=new NotebookAiClient.Cancellation();final NotebookAiClient.Cancellation cancel=modelRequest;busy(true);status.setText("Fetching available models…");
        new Thread(()->{
            List<NotebookAiModel> result=null;String problem=null;
            try{
                NotebookAiConfig config=NotebookAiSettings.connection(activity,value,base,"",credential);NotebookAiClient client=new NotebookAiClient();
                result=NotebookAiModelLibrary.refreshed(client.catalog(config,cancel),before);
                for(int i=0;i<result.size();i++)if(enabled.contains(result.get(i).id)||result.get(i).id.equals(active))result.set(i,enrich(client,config,result.get(i),cancel));
                result.removeIf(model->model.chat==0);
                List<NotebookAiModel> persisted=NotebookAiSettings.catalog(activity,value,base),cached=new ArrayList<>();
                for(NotebookAiModel model:result)cached.add(model.automatic().refreshed(NotebookAiModelLibrary.find(persisted,model.id)));
                cancel.check();NotebookAiSettings.saveCatalog(activity,value,base,cached);
            }catch(java.security.GeneralSecurityException error){problem="Cannot unlock the saved key. Enter your API key again.";}
            catch(java.io.IOException|IllegalArgumentException error){problem=error.getMessage();}
            final List<NotebookAiModel> found=result;final String failure=problem;
            activity.runOnUiThread(()->{
                if(stale(request))return;modelRequest=null;busy(false);if(failure!=null){status.setText(failure);return;}
                available=found;checked.retainAll(ids(found));renderModels();status.setText(found.isEmpty()?"No text-chat models were returned.":"Check the models you want, then Save AI settings.");
            });
        },"ai-models").start();
    }
    private static Set<String> ids(List<NotebookAiModel> models){Set<String> ids=new HashSet<>();for(NotebookAiModel model:models)ids.add(model.id);return ids;}
    private void editModel(NotebookAiModel model){
        if(working||modelDialog!=null)return;
        final NotebookAiSettings.Profile value=selected();final String base=url.getText().toString();final int request=++modelGeneration;
        modelRequest=new NotebookAiClient.Cancellation();final NotebookAiClient.Cancellation cancel=modelRequest;busy(true);status.setText("Loading model settings…");
        new Thread(()->{
            NotebookAiModel result=model;try{if(!model.known())result=enrich(new NotebookAiClient(),new NotebookAiConfig(value.provider,value.id,base,model.id,"",null),model,cancel);}
            catch(java.io.IOException ignored){}
            final NotebookAiModel loaded=result;
            activity.runOnUiThread(()->{
                if(stale(request))return;modelRequest=null;busy(false);replace(loaded);
                if(loaded.chat==0){checked.remove(loaded.id);available.removeIf(item->item.id.equals(loaded.id));renderModels();status.setText("This model does not support the chat interface.");return;}
                modelDialog=NotebookAiModelDialog.show(activity,loaded,updated->{replace(updated);status.setText("Model settings updated. Save AI settings to apply.");});
                modelDialog.setOnDismissListener(dialog->modelDialog=null);status.setText("Model defaults are filled automatically. Manual edits are optional.");
            });
        },"ai-model-details").start();
    }
    private void replace(NotebookAiModel model){for(int i=0;i<available.size();i++)if(available.get(i).id.equals(model.id)){available.set(i,model);break;}rows.notifyDataSetChanged();}
    private void save(){
        if(working)return;final NotebookAiSettings.Profile value=selected();final String name=providerName.getText().toString(),base=url.getText().toString(),credential=key.getText().toString();
        final List<NotebookAiModel> catalog=new ArrayList<>(available);final Set<String> enabled=new LinkedHashSet<>(checked);
        final String current=NotebookAiSettings.model(activity,value);final int request=++modelGeneration;
        modelRequest=new NotebookAiClient.Cancellation();final NotebookAiClient.Cancellation cancel=modelRequest;busy(true);status.setText("Saving AI settings…");
        new Thread(()->{
            String problem=null;List<NotebookAiModel> selected=new ArrayList<>();
            try{
                NotebookAiConfig config=NotebookAiSettings.connection(activity,value,base,"",credential);NotebookAiClient client=new NotebookAiClient();
                for(int i=0;i<catalog.size();i++)if(enabled.contains(catalog.get(i).id)){
                    NotebookAiModel model=catalog.get(i);if(!model.known())model=enrich(client,config,model,cancel);catalog.set(i,model);
                    if(model.chat==0)throw new java.io.IOException(model.name+" does not support text chat. Uncheck it and save again.");selected.add(model);
                }
                String active=NotebookAiModelLibrary.find(selected,current)!=null?current:selected.isEmpty()?"":selected.get(0).id;
                cancel.check();NotebookAiSettings.save(activity,value,name,base,active,credential,selected,catalog);
            }catch(java.security.GeneralSecurityException error){problem="Cannot unlock the API key. Enter it again, or remove the saved key and retry.";}
            catch(java.io.IOException|IllegalArgumentException error){problem=error.getMessage();}
            final String failure=problem;
            activity.runOnUiThread(()->{
                if(stale(request))return;modelRequest=null;busy(false);if(failure!=null){status.setText(failure);return;}
                reloadProfiles(value.id);status.setText(enabled.isEmpty()?"Connection saved. Fetch models and check the ones you want to use.":"AI settings saved.");
            });
        },"ai-settings").start();
    }
    private void remove(){
        NotebookAiSettings.Profile value=selected();busy(true);new Thread(()->{
            String problem=null;try{NotebookAiSettings.removeKey(activity,value);}catch(java.io.IOException error){problem=error.getMessage();}final String failure=problem;
            activity.runOnUiThread(()->{if(activity.isDestroyed())return;busy(false);load();if(failure!=null)status.setText(failure);});
        },"ai-key-remove").start();
    }
    private boolean stale(int request){return activity.isDestroyed()||activity.isFinishing()||request!=modelGeneration;}
    void cancelModels(){
        if(providerDialog!=null){providerDialog.dismiss();providerDialog=null;}
        if(modelDialog!=null){modelDialog.dismiss();modelDialog=null;}
        if(modelRequest==null)return;++modelGeneration;modelRequest.cancel();modelRequest=null;busy(false);status.setText("Model loading stopped. Tap Fetch available models to retry.");
    }
    private EditText field(LinearLayout body,String name,String hint,int type){
        body.addView(label(name,12));EditText field=new EditText(activity);field.setTextColor(Color.BLACK);field.setTextSize(14);field.setSingleLine(true);field.setInputType(type|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI|EditorInfo.IME_FLAG_NO_FULLSCREEN);field.setHint(hint);field.setContentDescription(name);
        field.setBackground(NotebookUi.rounded(activity.getResources().getDisplayMetrics().density,5));field.setPadding(dp(10),0,dp(10),0);
        body.addView(field,new LinearLayout.LayoutParams(-1,dp(42)));return field;
    }
    private NotebookButton button(String text,Runnable action){NotebookButton view=new NotebookButton(activity);view.setText(text);view.setTextSize(13);NotebookUi.action(view,null);view.setOnClickListener(v->action.run());return view;}
    private TextView label(String text,int size){TextView view=new TextView(activity);view.setText(text);view.setTextSize(size);view.setTextColor(Color.BLACK);view.setGravity(Gravity.CENTER_VERTICAL);return view;}
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
}
