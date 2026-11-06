package org.eink.inkprobe.notebook;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Credentials stay encrypted with an app-owned Android Keystore key, outside note backups. */
final class NotebookAiSettings {
    private static final String ALIAS="scriflow-ai-credentials-v1";
    static final class Profile {
        final String id, name;
        final NotebookAiConfig.Provider provider;
        Profile(String id,String name,NotebookAiConfig.Provider provider){this.id=id;this.name=name;this.provider=provider;}
        @Override public String toString(){return name;}
    }
    private NotebookAiSettings() { }
    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("aiSettings",Context.MODE_PRIVATE);
    }
    private static String prefix(Profile profile){return profile.id.equals(profile.provider.name())?profile.id:"profile."+profile.id;}
    private static String checkedName(String value){
        String name=value==null?"":value.trim();
        if(name.isEmpty()||name.length()>80)throw new IllegalArgumentException("Enter a provider name of at most 80 characters");
        for(int i=0;i<name.length();i++)if(Character.isISOControl(name.charAt(i)))throw new IllegalArgumentException("Enter a valid provider name");
        return name;
    }
    private static Profile legacy(Context context,NotebookAiConfig.Provider provider){
        return new Profile(provider.name(),prefs(context).getString("profile."+provider.name()+".name",provider.label),provider);
    }
    static Profile profile(Context context,String id){
        if(id==null)return null;
        try{return legacy(context,NotebookAiConfig.Provider.valueOf(id));}catch(IllegalArgumentException ignored){}
        String raw=prefs(context).getString("profile."+id+".type","");
        try{
            NotebookAiConfig.Provider provider=NotebookAiConfig.Provider.valueOf(raw);
            if(!NotebookAiConfig.validProviderId(provider,id))return null;
            return new Profile(id,checkedName(prefs(context).getString("profile."+id+".name",provider.label)),provider);
        }catch(IllegalArgumentException ignored){return null;}
    }
    static java.util.List<Profile> profiles(Context context){
        SharedPreferences p=prefs(context);java.util.List<Profile> result=new java.util.ArrayList<>();
        String active=p.getString("activeProfile",p.getString("provider","OPENAI"));
        for(NotebookAiConfig.Provider provider:NotebookAiConfig.Provider.values())if(provider.name().equals(active)
                ||p.contains(provider.name()+".url")||p.contains(provider.name()+".model")||p.contains(provider.name()+".key"))
            result.add(legacy(context,provider));
        for(String id:p.getString("profiles.ids","").split("\n")){Profile profile=profile(context,id);if(profile!=null&&!id.equals(profile.provider.name()))result.add(profile);}
        if(result.isEmpty())result.add(legacy(context,NotebookAiConfig.Provider.OPENAI));
        return result;
    }
    static Profile activeProfile(Context context){
        SharedPreferences p=prefs(context);String id=p.getString("activeProfile",p.getString("provider","OPENAI"));
        Profile profile=profile(context,id);return profile==null?legacy(context,provider(context)):profile;
    }
    static Profile addProfile(Context context,NotebookAiConfig.Provider provider,String name)throws IOException{
        if(provider==null)throw new IllegalArgumentException("Choose a provider type");
        if(profiles(context).size()>=64)throw new IOException("This device supports up to 64 AI providers");
        String label=checkedName(name);String id="p-"+java.util.UUID.randomUUID();SharedPreferences p=prefs(context);
        String ids=p.getString("profiles.ids","");
        if(!p.edit().putString("profiles.ids",ids.isEmpty()?id:ids+"\n"+id)
                .putString("profile."+id+".type",provider.name()).putString("profile."+id+".name",label)
                .putString("profile."+id+".url",provider.baseUrl).putString("profile."+id+".model","").commit())
            throw new IOException("Could not add the provider. Try again.");
        return new Profile(id,label,provider);
    }
    static String baseUrl(Context context,Profile profile){return prefs(context).getString(prefix(profile)+".url",profile.provider.baseUrl);}
    static String model(Context context,Profile profile){return prefs(context).getString(prefix(profile)+".model",profile.provider.model);}
    static boolean hasKey(Context context,Profile profile){return prefs(context).contains(prefix(profile)+".key");}
    static boolean configured(Context context,Profile profile){
        SharedPreferences p=prefs(context);String flag=prefix(profile)+".configured";
        if(p.contains(flag))return p.getBoolean(flag,false);
        return !model(context,profile).isEmpty()&&(hasKey(context,profile)||profile.provider==NotebookAiConfig.Provider.CUSTOM&&p.contains(prefix(profile)+".url"));
    }
    static String name(Context context,String id,NotebookAiConfig.Provider provider){Profile profile=profile(context,id);return profile==null||profile.provider!=provider?provider.label:profile.name;}
    static NotebookAiConfig.Provider provider(Context context) {
        try { return NotebookAiConfig.Provider.valueOf(prefs(context).getString("provider","OPENAI")); }
        catch (IllegalArgumentException error) { return NotebookAiConfig.Provider.OPENAI; }
    }
    static String baseUrl(Context context,NotebookAiConfig.Provider provider) {
        return prefs(context).getString(provider.name()+".url",provider.baseUrl);
    }
    static String model(Context context,NotebookAiConfig.Provider provider) {
        return prefs(context).getString(provider.name()+".model",provider.model);
    }
    static boolean hasKey(Context context,NotebookAiConfig.Provider provider) {
        return prefs(context).contains(provider.name()+".key");
    }
    static boolean configured(Context context) { return configured(context,activeProfile(context)); }
    static NotebookAiConfig publicConfig(Context context) {
        Profile profile=activeProfile(context);
        return configuration(context,profile,baseUrl(context,profile),model(context,profile),"");
    }
    static NotebookAiConfig configuration(Context context,NotebookAiConfig.Provider provider,String url,String model,String key) {
        NotebookAiModel specs=model(context,provider,url,model);
        return new NotebookAiConfig(provider,url,model,key,specs);
    }
    static NotebookAiConfig configuration(Context context,Profile profile,String url,String model,String key) {
        return new NotebookAiConfig(profile.provider,profile.id,url,model,key,model(context,profile,url,model));
    }
    private static String libraryKey(NotebookAiConfig.Provider provider,String url) {
        return "models."+new NotebookAiConfig(provider,url,"","").profile();
    }
    private static String libraryKey(Profile profile,String url){
        String key=libraryKey(profile.provider,url);return profile.id.equals(profile.provider.name())?key:key+"."+profile.id;
    }
    static java.util.List<NotebookAiModel> models(Context context,NotebookAiConfig.Provider provider,String url) {
        return NotebookAiModelLibrary.decode(prefs(context).getString(libraryKey(provider,url),"[]"));
    }
    static NotebookAiModel model(Context context,NotebookAiConfig.Provider provider,String url,String id) {
        for(NotebookAiModel value:catalog(context,provider,url))if(value.id.equals(id))return value;
        return id.isEmpty()?null:new NotebookAiModel(id);
    }
    static void saveModels(Context context,NotebookAiConfig.Provider provider,String url,java.util.List<NotebookAiModel> models)throws IOException {
        String encoded=NotebookAiModelLibrary.encode(models);
        if(!prefs(context).edit().putString(libraryKey(provider,url),encoded).commit())throw new IOException("Could not save selected models. Check storage and retry.");
    }
    static java.util.List<NotebookAiModel> catalog(Context context,NotebookAiConfig.Provider provider,String url){
        java.util.List<NotebookAiModel> cached=NotebookAiModelLibrary.catalog(prefs(context).getString("catalog."+libraryKey(provider,url),"[]"));
        java.util.LinkedHashMap<String,NotebookAiModel> values=new java.util.LinkedHashMap<>();for(NotebookAiModel model:cached)values.put(model.id,model);
        for(NotebookAiModel model:models(context,provider,url)){
            NotebookAiModel latest=values.get(model.id);values.put(model.id,latest==null||!latest.known()?model:latest.refreshed(model));
        }
        return new java.util.ArrayList<>(values.values());
    }
    static void saveCatalog(Context context,NotebookAiConfig.Provider provider,String url,java.util.List<NotebookAiModel> models)throws IOException{
        if(!prefs(context).edit().putString("catalog."+libraryKey(provider,url),NotebookAiModelLibrary.encodeCatalog(models)).commit())throw new IOException("Could not save the model list. Check storage and retry.");
    }
    static java.util.List<NotebookAiModel> models(Context context,Profile profile,String url){
        return NotebookAiModelLibrary.decode(prefs(context).getString(libraryKey(profile,url),"[]"));
    }
    static java.util.List<NotebookAiModel> catalog(Context context,Profile profile,String url){
        java.util.List<NotebookAiModel> cached=NotebookAiModelLibrary.catalog(prefs(context).getString("catalog."+libraryKey(profile,url),"[]"));
        java.util.LinkedHashMap<String,NotebookAiModel> values=new java.util.LinkedHashMap<>();for(NotebookAiModel model:cached)values.put(model.id,model);
        for(NotebookAiModel model:models(context,profile,url)){
            NotebookAiModel latest=values.get(model.id);values.put(model.id,latest==null||!latest.known()?model:latest.refreshed(model));
        }
        return new java.util.ArrayList<>(values.values());
    }
    static NotebookAiModel model(Context context,Profile profile,String url,String id){
        for(NotebookAiModel value:catalog(context,profile,url))if(value.id.equals(id))return value;
        return id.isEmpty()?null:new NotebookAiModel(id);
    }
    static void saveCatalog(Context context,Profile profile,String url,java.util.List<NotebookAiModel> models)throws IOException{
        if(!prefs(context).edit().putString("catalog."+libraryKey(profile,url),NotebookAiModelLibrary.encodeCatalog(models)).commit())
            throw new IOException("Could not save the model list. Check storage and retry.");
    }
    static java.util.List<NotebookAiConfig> publicConfigs(Context context) {
        java.util.List<NotebookAiConfig> configs=new java.util.ArrayList<>();
        for(Profile profile:profiles(context)) {
            NotebookAiConfig config=configuration(context,profile,baseUrl(context,profile),model(context,profile),"");
            try {new NotebookAiConfig(profile.provider,config.baseUrl,config.model,"local-validation").validate();configs.add(config);}
            catch(IllegalArgumentException ignored){/* An unused custom provider has no endpoint. */}
            for(NotebookAiModel model:models(context,profile,config.baseUrl))if(!model.id.equals(config.model)){
                NotebookAiConfig added=new NotebookAiConfig(profile.provider,profile.id,config.baseUrl,model.id,"",model);
                try{new NotebookAiConfig(profile.provider,config.baseUrl,model.id,"local-validation").validate();configs.add(added);}catch(IllegalArgumentException ignored){}
            }
        }
        return configs;
    }
    static java.util.List<NotebookAiConfig> savedModels(Context context) {
        java.util.List<NotebookAiConfig> configs=new java.util.ArrayList<>();
        for(Profile profile:profiles(context)){
            String url=baseUrl(context,profile);
            for(NotebookAiModel model:models(context,profile,url))if(model.chat!=0)configs.add(new NotebookAiConfig(profile.provider,profile.id,url,model.id,"",model));
        }
        return configs;
    }
    static void selectModel(Context context,NotebookAiConfig config)throws IOException {
        // Model switching reuses the selected provider's existing key; it never copies a credential.
        new NotebookAiConfig(config.provider,config.baseUrl,config.model,"local-validation").validate();
        Profile profile=profile(context,config.providerId);
        if(profile==null||profile.provider!=config.provider||!new NotebookAiConfig(config.provider,baseUrl(context,profile),config.model,"").sameEndpoint(config))
            throw new IOException("This provider or endpoint is no longer configured. Open AI settings.");
        boolean ready=config.provider==NotebookAiConfig.Provider.CUSTOM||hasKey(context,profile);
        if(!prefs(context).edit().putString("provider",config.provider.name()).putString("activeProfile",profile.id)
                .putString(prefix(profile)+".model",config.model).putBoolean(prefix(profile)+".configured",ready).putBoolean("configured",ready).commit())
            throw new IOException("Could not save the selected model. Check storage and retry.");
    }
    static String apiKey(Context context,NotebookAiConfig.Provider provider) throws GeneralSecurityException,IOException { return decrypt(context,legacy(context,provider)); }
    static String apiKey(Context context,Profile profile) throws GeneralSecurityException,IOException { return decrypt(context,profile); }
    static NotebookAiConfig read(Context context) throws GeneralSecurityException,IOException {
        Profile profile=activeProfile(context);
        return configuration(context,profile,baseUrl(context,profile),model(context,profile),decrypt(context,profile));
    }
    static NotebookAiConfig connection(Context context,NotebookAiConfig.Provider provider,String url,String model,String replacementKey)
            throws GeneralSecurityException,IOException {
        return connection(context,legacy(context,provider),url,model,replacementKey);
    }
    static NotebookAiConfig connection(Context context,Profile profile,String url,String model,String replacementKey)
            throws GeneralSecurityException,IOException {
        String credential=replacementKey.trim();
        if(credential.isEmpty()&&hasKey(context,profile)){
            NotebookAiConfig saved=new NotebookAiConfig(profile.provider,baseUrl(context,profile),"","");
            NotebookAiConfig requested=new NotebookAiConfig(profile.provider,url,"","");
            if(!saved.sameEndpoint(requested))throw new IllegalArgumentException("Enter the API key again when changing a saved endpoint");
            credential=decrypt(context,profile);
        }
        NotebookAiConfig config=new NotebookAiConfig(profile.provider,profile.id,url,model,credential,null);config.validateConnection();return config;
    }
    static void save(Context context,NotebookAiConfig.Provider provider,String baseUrl,String model,String replacementKey)
            throws GeneralSecurityException,IOException {
        save(context,provider,baseUrl,model,replacementKey,null,null);
    }
    static void save(Context context,NotebookAiConfig.Provider provider,String baseUrl,String model,String replacementKey,
            java.util.List<NotebookAiModel> selected,java.util.List<NotebookAiModel> catalog)throws GeneralSecurityException,IOException {
        Profile profile=legacy(context,provider);
        save(context,profile,profile.name,baseUrl,model,replacementKey,selected,catalog);
    }
    static void save(Context context,Profile profile,String name,String baseUrl,String model,String replacementKey,
            java.util.List<NotebookAiModel> selected,java.util.List<NotebookAiModel> catalog)throws GeneralSecurityException,IOException {
        String label=checkedName(name);
        NotebookAiConfig config=connection(context,profile,baseUrl,model,replacementKey);
        if(!config.model.isEmpty())config.validate();
        SharedPreferences.Editor editor=prefs(context).edit().putString("provider",profile.provider.name()).putString("activeProfile",profile.id)
            .putString("profile."+profile.id+".name",label)
            .putString(prefix(profile)+".url",config.baseUrl).putString(prefix(profile)+".model",config.model)
            .putBoolean(prefix(profile)+".configured",!config.model.isEmpty()).putBoolean("configured",!config.model.isEmpty());
        if(selected!=null)editor.putString(libraryKey(profile,config.baseUrl),NotebookAiModelLibrary.encode(selected));
        if(catalog!=null)editor.putString("catalog."+libraryKey(profile,config.baseUrl),NotebookAiModelLibrary.encodeCatalog(catalog));
        if (!config.apiKey.isEmpty()) {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
            cipher.updateAAD(profile.id.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=cipher.doFinal(config.apiKey.getBytes(StandardCharsets.UTF_8));
            editor.putString(prefix(profile)+".key",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP));
        }
        if (!editor.commit()) throw new IOException("Could not save AI settings. Try again.");
    }
    static void removeKey(Context context,NotebookAiConfig.Provider provider) throws IOException {
        removeKey(context,legacy(context,provider));
    }
    static void removeKey(Context context,Profile profile) throws IOException {
        SharedPreferences.Editor editor=prefs(context).edit().remove(prefix(profile)+".key").putBoolean(prefix(profile)+".configured",false);
        if (profile.id.equals(activeProfile(context).id)) editor.putBoolean("configured",false);
        if (!editor.commit()) throw new IOException("Could not remove the saved API key");
    }
    private static String decrypt(Context context,Profile profile) throws GeneralSecurityException,IOException {
        String stored=prefs(context).getString(prefix(profile)+".key",""); if (stored.isEmpty()) return "";
        try {
            String[] parts=stored.split(":",-1); if (parts.length!=2) throw new GeneralSecurityException("Invalid credential");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
            cipher.updateAAD(profile.id.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) { throw new GeneralSecurityException("Cannot unlock the saved API key",error); }
    }
    private static synchronized javax.crypto.SecretKey key() throws GeneralSecurityException,IOException {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
            return generator.generateKey();
        }
        return (javax.crypto.SecretKey)store.getKey(ALIAS,null);
    }
}
