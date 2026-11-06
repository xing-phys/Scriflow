package org.eink.inkprobe.notebook;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.*;
import java.security.cert.Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.util.*;
import javax.crypto.KeyGeneratorSpi;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/** Real settings and AES/GCM with an in-memory Android-Keystore substitute; no network. */
public final class NotebookAiSettingsTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Security.addProvider(new FakeAndroidProvider());
        FakeContext context=new FakeContext();
        NotebookAiSettings.Profile legacy=NotebookAiSettings.profile(context,"OPENAI");
        NotebookAiModel oldModel=new NotebookAiModel("legacy-model");
        NotebookAiSettings.save(context,legacy,"OpenAI",legacy.provider.baseUrl,oldModel.id,"legacy-secret",Collections.singletonList(oldModel),null);
        check(NotebookAiSettings.apiKey(context,legacy).equals("legacy-secret"),"legacy provider credentials remain readable");
        check(!context.preferences.values.toString().contains("legacy-secret"),"preferences do not contain the plaintext credential");
        NotebookAiSettings.Profile one=NotebookAiSettings.addProfile(context,NotebookAiConfig.Provider.CUSTOM,"Institute A");
        NotebookAiSettings.Profile two=NotebookAiSettings.addProfile(context,NotebookAiConfig.Provider.CUSTOM,"Institute B");
        String url="https://ai.example/v1";NotebookAiModel modelA=new NotebookAiModel("model-a"),modelB=new NotebookAiModel("model-b");
        NotebookAiSettings.save(context,one,"Institute A",url,modelA.id,"key-for-a",Collections.singletonList(modelA),null);
        NotebookAiSettings.save(context,two,"Institute B",url,modelB.id,"key-for-b",Collections.singletonList(modelB),null);
        check(!one.id.equals(two.id)&&NotebookAiSettings.profiles(context).size()==3,"multiple custom providers keep distinct stable identities");
        check(NotebookAiSettings.apiKey(context,one).equals("key-for-a")&&NotebookAiSettings.apiKey(context,two).equals("key-for-b"),"same-endpoint providers retain independent encrypted keys");
        check(NotebookAiSettings.models(context,one,url).get(0).id.equals("model-a")&&NotebookAiSettings.models(context,two,url).get(0).id.equals("model-b"),"same-endpoint model selections stay isolated");
        NotebookAiConfig first=NotebookAiSettings.savedModels(context).stream().filter(value->value.providerId.equals(one.id)).findFirst().get();
        NotebookAiSettings.selectModel(context,first);
        check(NotebookAiSettings.publicConfig(context).providerId.equals(one.id)&&NotebookAiSettings.read(context).apiKey.equals("key-for-a"),"switching models selects the correct provider and credential");
        NotebookAiSettings.save(context,one,"Renamed institute",url,modelA.id,"",Collections.singletonList(modelA),null);
        check(NotebookAiSettings.name(context,one.id,one.provider).equals("Renamed institute")&&NotebookAiSettings.apiKey(context,one).equals("key-for-a"),"renaming preserves the profile and its credential");
        try { NotebookAiSettings.connection(context,one,"https://other.example/v1","model-a",""); throw new AssertionError("old key reused on changed endpoint"); }
        catch(IllegalArgumentException expected){checks++;}
        check(NotebookAiSettings.apiKey(context,one).equals("key-for-a"),"rejecting a changed endpoint leaves the original key intact");
        check(NotebookAiSettings.connection(context,one,"https://other.example/v1","model-a","explicit-new-key").apiKey.equals("explicit-new-key"),"a replacement key can explicitly configure a new endpoint");
        String aKey="profile."+one.id+".key",bKey="profile."+two.id+".key";
        Object original=context.preferences.values.get(bKey);context.preferences.values.put(bKey,context.preferences.values.get(aKey));
        try {NotebookAiSettings.apiKey(context,two);throw new AssertionError("credential moved between profiles");}
        catch(GeneralSecurityException expected){checks++;}
        context.preferences.values.put(bKey,original);
        check(NotebookAiSettings.apiKey(context,two).equals("key-for-b"),"authenticated provider identity rejects key substitution without corrupting the original");
        System.out.println("NotebookAiSettings: "+checks+" assertions passed (legacy migration, provider names, encrypted key and model isolation). No live API calls.");
    }
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}

    private static final class FakeContext extends Context {
        final FakePreferences preferences=new FakePreferences();
        @Override public SharedPreferences getSharedPreferences(String name,int mode){return preferences;}
    }
    private static final class FakePreferences implements SharedPreferences {
        final Map<String,Object> values=new HashMap<>();
        public String getString(String key,String fallback){Object v=values.get(key);return v instanceof String?(String)v:fallback;}
        public boolean getBoolean(String key,boolean fallback){Object v=values.get(key);return v instanceof Boolean?(Boolean)v:fallback;}
        public boolean contains(String key){return values.containsKey(key);}
        public Editor edit(){return new Editor(){final Map<String,Object> pending=new HashMap<>();final Set<String> removed=new HashSet<>();
            public Editor putString(String k,String v){pending.put(k,v);return this;}public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
            public Editor remove(String k){removed.add(k);return this;}public boolean commit(){for(String k:removed)values.remove(k);values.putAll(pending);return true;}};}
    }
    public static final class FakeAndroidProvider extends Provider {
        public FakeAndroidProvider(){super("AndroidKeyStore",1.0,"Test-only in-memory key store");put("KeyStore.AndroidKeyStore",MemoryKeyStore.class.getName());put("KeyGenerator.AES",MemoryGenerator.class.getName());}
    }
    public static final class MemoryGenerator extends KeyGeneratorSpi {
        private String alias;
        protected void engineInit(SecureRandom random){}
        protected void engineInit(int size,SecureRandom random){}
        protected void engineInit(AlgorithmParameterSpec spec,SecureRandom random){alias=((KeyGenParameterSpec)spec).alias;}
        protected SecretKey engineGenerateKey(){byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);SecretKey key=new SecretKeySpec(bytes,"AES");MemoryKeyStore.keys.put(alias,key);return key;}
    }
    public static final class MemoryKeyStore extends KeyStoreSpi {
        static final Map<String,Key> keys=new HashMap<>();
        public Key engineGetKey(String alias,char[] password){return keys.get(alias);}
        public Certificate[] engineGetCertificateChain(String alias){return null;}
        public Certificate engineGetCertificate(String alias){return null;}
        public Date engineGetCreationDate(String alias){return new Date(1);}
        public void engineSetKeyEntry(String alias,Key key,char[] password,Certificate[] chain){keys.put(alias,key);}
        public void engineSetKeyEntry(String alias,byte[] key,Certificate[] chain){}
        public void engineSetCertificateEntry(String alias,Certificate certificate){}
        public void engineDeleteEntry(String alias){keys.remove(alias);}
        public Enumeration<String> engineAliases(){return Collections.enumeration(keys.keySet());}
        public boolean engineContainsAlias(String alias){return keys.containsKey(alias);}
        public int engineSize(){return keys.size();}
        public boolean engineIsKeyEntry(String alias){return keys.containsKey(alias);}
        public boolean engineIsCertificateEntry(String alias){return false;}
        public String engineGetCertificateAlias(Certificate certificate){return null;}
        public void engineStore(OutputStream stream,char[] password){}
        public void engineLoad(InputStream stream,char[] password){}
    }
}
