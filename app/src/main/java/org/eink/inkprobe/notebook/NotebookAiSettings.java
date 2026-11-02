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
    private NotebookAiSettings() { }
    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("aiSettings",Context.MODE_PRIVATE);
    }
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
    static boolean configured(Context context) { return prefs(context).getBoolean("configured",false); }
    static NotebookAiConfig publicConfig(Context context) {
        NotebookAiConfig.Provider provider=provider(context);
        return configuration(context,provider,baseUrl(context,provider),model(context,provider),"");
    }
    static NotebookAiConfig configuration(Context context,NotebookAiConfig.Provider provider,String url,String model,String key) {
        NotebookAiModel specs=model(context,provider,url,model);
        return new NotebookAiConfig(provider,url,model,key,specs);
    }
    private static String libraryKey(NotebookAiConfig.Provider provider,String url) {
        return "models."+new NotebookAiConfig(provider,url,"","").profile();
    }
    static java.util.List<NotebookAiModel> models(Context context,NotebookAiConfig.Provider provider,String url) {
        return NotebookAiModelLibrary.decode(prefs(context).getString(libraryKey(provider,url),"[]"));
    }
    static NotebookAiModel model(Context context,NotebookAiConfig.Provider provider,String url,String id) {
        for(NotebookAiModel value:models(context,provider,url))if(value.id.equals(id))return value;
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
    static java.util.List<NotebookAiConfig> publicConfigs(Context context) {
        java.util.List<NotebookAiConfig> configs=new java.util.ArrayList<>();
        for(NotebookAiConfig.Provider provider:NotebookAiConfig.Provider.values()) {
            NotebookAiConfig config=configuration(context,provider,baseUrl(context,provider),model(context,provider),"");
            try {new NotebookAiConfig(provider,config.baseUrl,config.model,"local-validation").validate();configs.add(config);}
            catch(IllegalArgumentException ignored){/* An unused custom provider has no endpoint. */}
            for(NotebookAiModel model:models(context,provider,config.baseUrl))if(!model.id.equals(config.model)){
                NotebookAiConfig added=new NotebookAiConfig(provider,config.baseUrl,model.id,"",model);
                try{new NotebookAiConfig(provider,config.baseUrl,model.id,"local-validation").validate();configs.add(added);}catch(IllegalArgumentException ignored){}
            }
        }
        return configs;
    }
    static java.util.List<NotebookAiConfig> savedModels(Context context) {
        java.util.List<NotebookAiConfig> configs=new java.util.ArrayList<>();
        for(NotebookAiConfig.Provider provider:NotebookAiConfig.Provider.values()){
            String url=baseUrl(context,provider);
            for(NotebookAiModel model:models(context,provider,url))if(model.chat!=0)configs.add(new NotebookAiConfig(provider,url,model.id,"",model));
        }
        return configs;
    }
    static void selectModel(Context context,NotebookAiConfig config)throws IOException {
        // Model switching reuses the selected provider's existing key; it never copies a credential.
        new NotebookAiConfig(config.provider,config.baseUrl,config.model,"local-validation").validate();
        if(!prefs(context).edit().putString("provider",config.provider.name()).putString(config.provider.name()+".url",config.baseUrl)
                .putString(config.provider.name()+".model",config.model).putBoolean("configured",config.provider==NotebookAiConfig.Provider.CUSTOM||hasKey(context,config.provider)).commit())
            throw new IOException("Could not save the selected model. Check storage and retry.");
    }
    static String apiKey(Context context,NotebookAiConfig.Provider provider) throws GeneralSecurityException,IOException { return decrypt(context,provider); }
    static NotebookAiConfig read(Context context) throws GeneralSecurityException,IOException {
        NotebookAiConfig.Provider provider=provider(context);
        return configuration(context,provider,baseUrl(context,provider),model(context,provider),decrypt(context,provider));
    }
    static NotebookAiConfig connection(Context context,NotebookAiConfig.Provider provider,String url,String model,String replacementKey)
            throws GeneralSecurityException,IOException {
        String credential=replacementKey.trim();
        if (credential.isEmpty()&&hasKey(context,provider)) credential=decrypt(context,provider);
        NotebookAiConfig config=new NotebookAiConfig(provider,url,model,credential); config.validateConnection(); return config;
    }
    static void save(Context context,NotebookAiConfig.Provider provider,String baseUrl,String model,String replacementKey)
            throws GeneralSecurityException,IOException {
        save(context,provider,baseUrl,model,replacementKey,null,null);
    }
    static void save(Context context,NotebookAiConfig.Provider provider,String baseUrl,String model,String replacementKey,
            java.util.List<NotebookAiModel> selected,java.util.List<NotebookAiModel> catalog)throws GeneralSecurityException,IOException {
        String apiKey=replacementKey.trim();
        if (apiKey.isEmpty() && hasKey(context,provider)) apiKey=decrypt(context,provider);
        NotebookAiConfig config=new NotebookAiConfig(provider,baseUrl,model,apiKey);if(config.model.isEmpty())config.validateConnection();else config.validate();
        SharedPreferences.Editor editor=prefs(context).edit().putString("provider",provider.name())
            .putString(provider.name()+".url",config.baseUrl).putString(provider.name()+".model",config.model).putBoolean("configured",!config.model.isEmpty());
        if(selected!=null)editor.putString(libraryKey(provider,config.baseUrl),NotebookAiModelLibrary.encode(selected));
        if(catalog!=null)editor.putString("catalog."+libraryKey(provider,config.baseUrl),NotebookAiModelLibrary.encodeCatalog(catalog));
        if (!config.apiKey.isEmpty()) {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key());
            cipher.updateAAD(provider.name().getBytes(StandardCharsets.UTF_8));
            byte[] encrypted=cipher.doFinal(config.apiKey.getBytes(StandardCharsets.UTF_8));
            editor.putString(provider.name()+".key",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP));
        }
        if (!editor.commit()) throw new IOException("Could not save AI settings. Try again.");
    }
    static void removeKey(Context context,NotebookAiConfig.Provider provider) throws IOException {
        SharedPreferences.Editor editor=prefs(context).edit().remove(provider.name()+".key");
        if (provider==provider(context)) editor.putBoolean("configured",false);
        if (!editor.commit()) throw new IOException("Could not remove the saved API key");
    }
    private static String decrypt(Context context,NotebookAiConfig.Provider provider) throws GeneralSecurityException,IOException {
        String stored=prefs(context).getString(provider.name()+".key",""); if (stored.isEmpty()) return "";
        try {
            String[] parts=stored.split(":",-1); if (parts.length!=2) throw new GeneralSecurityException("Invalid credential");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
            cipher.updateAAD(provider.name().getBytes(StandardCharsets.UTF_8));
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
