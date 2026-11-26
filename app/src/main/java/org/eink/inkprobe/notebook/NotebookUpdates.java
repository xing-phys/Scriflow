package org.eink.inkprobe.notebook;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Public GitHub release checks and individually verified, private installer downloads. */
final class NotebookUpdates {
    enum Phase { IDLE, CHECKING, AVAILABLE, DOWNLOADING, VERIFYING, ERROR }
    static final class Snapshot {
        final Phase phase; final String message,available,ready; final long bytes,total,readyCode;
        Snapshot(Phase p,String m,String a,String r,long b,long t,long code){phase=p;message=m;available=a;ready=r;bytes=b;total=t;readyCode=code;}
    }
    private static final class Release {
        final NotebookReleaseVersion version; final String apk,checksum,digest; final long size;
        Release(NotebookReleaseVersion version,String apk,String checksum,String digest,long size){this.version=version;this.apk=apk;this.checksum=checksum;this.digest=digest;this.size=size;}
    }
    private static final long MAX_APK=1024L*1024*1024;
    private static final String API="https://api.github.com/repos/xing-phys/Scriflow/releases";
    private static NotebookUpdates instance;
    static synchronized NotebookUpdates get(Context context){if(instance==null)instance=new NotebookUpdates(context.getApplicationContext());return instance;}
    private final Context context; private final SharedPreferences preferences; private final File directory;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->new Thread(r,"scriflow-update"));
    private final Handler main=new Handler(Looper.getMainLooper()); private final CopyOnWriteArraySet<Runnable> listeners=new CopyOnWriteArraySet<>();
    private final Object lock=new Object();
    private Phase phase=Phase.IDLE; private String message="",ready=""; private Release available;
    private volatile long bytes; private long total,readyCode; private volatile boolean cancel;

    private NotebookUpdates(Context application){
        context=application;preferences=context.getSharedPreferences("app-updates",Context.MODE_PRIVATE);directory=new File(context.getFilesDir(),"updates");
        ready=preferences.getString("readyVersion","");readyCode=preferences.getLong("readyCode",0);
        worker.execute(this::recoverAndCleanup);
    }
    String currentVersion(){try{return context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName;}catch(PackageManager.NameNotFoundException error){return "unknown";}}
    long currentCode(){try{return context.getPackageManager().getPackageInfo(context.getPackageName(),0).getLongVersionCode();}catch(PackageManager.NameNotFoundException error){return 0;}}
    boolean includePreviews(){return preferences.getBoolean("includePreviews",currentVersion().contains("-"));}
    void includePreviews(boolean value){
        preferences.edit().putBoolean("includePreviews",value).apply();
        synchronized(lock){if(phase==Phase.AVAILABLE){available=null;phase=Phase.IDLE;message="Update preference changed. Check again.";}}
        changed();
    }
    boolean autoDelete(){return preferences.getBoolean("autoDelete",true);}
    void autoDelete(boolean value){preferences.edit().putBoolean("autoDelete",value).apply();worker.execute(this::cleanInstalled);}
    Snapshot snapshot(){synchronized(lock){return new Snapshot(phase,message,available==null?"":available.version.value,ready,bytes,total,readyCode);}}
    boolean transferring(){synchronized(lock){return phase==Phase.DOWNLOADING||phase==Phase.VERIFYING;}}
    void observe(Runnable listener){listeners.add(listener);}
    void removeObserver(Runnable listener){listeners.remove(listener);}
    private void changed(){main.post(()->{for(Runnable listener:listeners)listener.run();});}

    void check(){
        synchronized(lock){if(phase==Phase.CHECKING||phase==Phase.DOWNLOADING||phase==Phase.VERIFYING)return;phase=Phase.CHECKING;message="Checking for updates…";available=null;}
        changed();boolean previews=includePreviews();
        worker.execute(()->{
            try {
                NotebookReleaseVersion current=NotebookReleaseVersion.parse(currentVersion());
                if(current==null)throw new IOException("The installed version cannot be compared.");
                Release best=null;
                try {best=release(new JSONObject(readText(API+"/latest",1024*1024)),false);} catch(HttpStatus error){if(error.code!=404)throw error;}
                if(previews){
                    JSONArray list=new JSONArray(readText(API+"?per_page=100",8*1024*1024));
                    for(int i=0;i<list.length();i++){
                        Release candidate=release(list.optJSONObject(i),true);
                        if(candidate!=null&&(best==null||candidate.version.compareTo(best.version)>0))best=candidate;
                    }
                }
                synchronized(lock){
                    if(best!=null&&best.version.compareTo(current)>0){available=best;phase=Phase.AVAILABLE;message="Update "+best.version.value+" is available.";}
                    else{phase=Phase.IDLE;message="Scriflow is up to date"+(previews?" (including previews).":".");}
                }
            } catch(IOException|JSONException|RuntimeException failure){synchronized(lock){phase=Phase.ERROR;message=networkError(failure);}}
            changed();
        });
    }
    private Release release(JSONObject value,boolean previews){
        if(value==null||value.optBoolean("draft",false))return null;
        String tag=value.optString("tag_name","");if(!tag.startsWith("v"))return null;
        NotebookReleaseVersion version=NotebookReleaseVersion.parse(tag.substring(1));if(version==null)return null;
        if(!previews&&(value.optBoolean("prerelease",false)||version.preview))return null;
        JSONArray assets=value.optJSONArray("assets");if(assets==null)return null;
        String apk="",checksum="",digest="";long size=0;
        for(int i=0;i<assets.length();i++){
            JSONObject asset=assets.optJSONObject(i);if(asset==null||!"uploaded".equals(asset.optString("state")))continue;
            String name=asset.optString("name"),url=asset.optString("browser_download_url");
            if(name.equals(NotebookUpdateSource.apkName(version.value))&&NotebookUpdateSource.releaseAsset(url,version.value,false)){
                apk=url;size=asset.optLong("size",0);String valueDigest=asset.optString("digest","");
                if(valueDigest.matches("(?i)sha256:[0-9a-f]{64}"))digest=valueDigest.substring(7).toLowerCase(java.util.Locale.ROOT);
            } else if(name.equals(NotebookUpdateSource.apkName(version.value)+".sha256")&&NotebookUpdateSource.releaseAsset(url,version.value,true))checksum=url;
        }
        return apk.isEmpty()||checksum.isEmpty()||size<=0||size>MAX_APK?null:new Release(version,apk,checksum,digest,size);
    }

    void download(){
        final Release release;
        synchronized(lock){if(phase!=Phase.AVAILABLE||available==null)return;release=available;phase=Phase.DOWNLOADING;message="Downloading "+release.version.value+"…";bytes=0;total=release.size;cancel=false;}
        changed();
        try{context.startForegroundService(new Intent(context,NotebookUpdateService.class));}catch(RuntimeException ignored) { }
        worker.execute(()->download(release));
    }
    void cancel(){cancel=true;}
    private void download(Release release){
        File partial=null;
        try {
            if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create the update folder.");
            // PackageManager inspects only paths ending in .apk, even before publication.
            partial=new File(directory,"partial-"+release.version.value+".apk");
            if(!preferences.edit().putString("partial",partial.getName()).commit())throw new IOException("Cannot record the update download.");
            String expected=NotebookUpdateSource.checksum(readText(release.checksum,1024),release.version.value);
            if(expected==null||!release.digest.isEmpty()&&!release.digest.equals(expected))throw new IOException("The release checksum is invalid.");
            MessageDigest digest=sha256();long count=0,last=0;
            HttpURLConnection connection=open(release.apk);
            try(InputStream in=connection.getInputStream();FileOutputStream out=new FileOutputStream(partial)){
                byte[] buffer=new byte[65536];int read;
                while((read=in.read(buffer))!=-1){
                    if(cancel)throw new InterruptedIOException("Canceled.");
                    if(read==0)continue;if(count>release.size-read)throw new IOException("Update is larger than its published size.");
                    out.write(buffer,0,read);digest.update(buffer,0,read);count+=read;bytes=count;
                    long now=SystemClock.uptimeMillis();if(now-last>=2000){last=now;changed();}
                }
                if(cancel)throw new InterruptedIOException("Canceled.");
                out.getFD().sync();
            } finally {connection.disconnect();}
            if(count!=release.size||!hex(digest.digest()).equals(expected))throw new IOException("The downloaded update did not match its checksum.");
            synchronized(lock){phase=Phase.VERIFYING;message="Verifying the installer…";}changed();
            long code=verifyArchive(partial,release.version.value,true);
            if(cancel)throw new InterruptedIOException("Canceled.");
            File destination=new File(directory,NotebookUpdateSource.apkName(release.version.value));
            Files.move(partial.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);partial=null;
            if(!preferences.edit().putString("readyVersion",release.version.value).putLong("readyCode",code).putString("readySha256",expected).remove("partial").commit())
                throw new IOException("Could not save the installer record.");
            synchronized(lock){ready=release.version.value;readyCode=code;phase=Phase.IDLE;message="Update "+ready+" is ready to install.";available=null;}
        } catch(IOException|RuntimeException failure){
            android.util.Log.w("ScriflowUpdate", "Update transfer failed: " + failure.getClass().getSimpleName());
            if(partial!=null&&partial.isFile())partial.delete();preferences.edit().remove("partial").apply();
            synchronized(lock){phase=Phase.ERROR;message=cancel?"Update download canceled.":networkError(failure);}
        }
        changed();
    }

    void prepareInstall(Consumer<String> callback){
        final File file=readyFile();final String version;final String expected;
        synchronized(lock){if(file==null||phase==Phase.DOWNLOADING||phase==Phase.CHECKING||phase==Phase.VERIFYING){callback.accept("No installer is ready.");return;}version=ready;expected=preferences.getString("readySha256","");phase=Phase.VERIFYING;message="Verifying the installer…";}
        changed();worker.execute(()->{
            String error=null;
            try {
                if(!expected.matches("[0-9a-f]{64}")||!fileHash(file).equals(expected))throw new IOException("The installer no longer matches its checksum.");
                verifyArchive(file,version,true);
            } catch(IOException|RuntimeException failure){error=networkError(failure);}
            synchronized(lock){phase=error==null?Phase.IDLE:Phase.ERROR;message=error==null?"Installer verified.":error;}
            final String result=error;changed();main.post(()->callback.accept(result));
        });
    }
    Uri readyUri(){File file=readyFile();return file==null?null:new Uri.Builder().scheme("content").authority(context.getPackageName()+".updates").appendPath(file.getName()).build();}
    File readyFile(){
        synchronized(lock){
            if(NotebookReleaseVersion.parse(ready)==null||readyCode<=0)return null;
            File file=new File(directory,NotebookUpdateSource.apkName(ready));return file.isFile()?file:null;
        }
    }
    void removeInstallers(Consumer<String> callback){
        synchronized(lock){if(phase==Phase.DOWNLOADING||phase==Phase.VERIFYING){callback.accept("Wait for the update to finish.");return;}}
        worker.execute(()->{
            String error=null;File[] files=directory.listFiles();
            if(files!=null)for(File file:files){String name=file.getName();if(name.startsWith("scriflow-")&&name.endsWith(".apk")
                    &&NotebookReleaseVersion.parse(name.substring(9,name.length()-4))!=null&&!file.delete())error="Could not remove every saved installer.";}
            if(error==null){preferences.edit().remove("readyVersion").remove("readyCode").remove("readySha256").apply();synchronized(lock){ready="";readyCode=0;}}
            final String result=error;changed();main.post(()->callback.accept(result));
        });
    }

    private void recoverAndCleanup(){
        String part=preferences.getString("partial","");
        if(part.startsWith("partial-")&&part.endsWith(".apk")&&NotebookReleaseVersion.parse(part.substring(8,part.length()-4))!=null){
            File file=new File(directory,part);if(!file.exists()||file.delete())preferences.edit().remove("partial").apply();
            synchronized(lock){message="The previous update download was interrupted. Check for updates to try again.";}
        }
        cleanInstalled();
    }
    void afterPackageReplaced(Runnable finished){worker.execute(()->{try{cleanInstalled();}finally{finished.run();}});}
    private void cleanInstalled(){
        File file=readyFile();long code;
        synchronized(lock){code=readyCode;}
        if(file!=null&&code>0&&currentCode()>=code&&autoDelete()&&file.delete()){
            preferences.edit().remove("readyVersion").remove("readyCode").remove("readySha256").apply();
            synchronized(lock){ready="";readyCode=0;message="The installed update's installer was removed.";}
        }
        changed();
    }
    private long verifyArchive(File file,String version,boolean newer)throws IOException {
        PackageManager pm=context.getPackageManager();
        // Some Android releases collect archive certificates only when the legacy flag is also set.
        PackageInfo apk=pm.getPackageArchiveInfo(file.getAbsolutePath(),PackageManager.GET_SIGNING_CERTIFICATES | PackageManager.GET_SIGNATURES);
        PackageInfo installed;
        try{installed=pm.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);}catch(PackageManager.NameNotFoundException error){throw new IOException("Cannot inspect the installed app.");}
        if(apk==null||!context.getPackageName().equals(apk.packageName)||!version.equals(apk.versionName)
                ||newer&&apk.getLongVersionCode()<=installed.getLongVersionCode())throw new IOException("The installer is not a newer Scriflow release.");
        if(apk.signingInfo==null||installed.signingInfo==null)throw new IOException("The installer has no signing certificate.");
        Set<String> expected=signers(installed.signingInfo.getApkContentsSigners()),actual=signers(apk.signingInfo.getApkContentsSigners());
        if(expected.isEmpty()||!expected.equals(actual))throw new IOException("The installer is signed by a different key.");
        return apk.getLongVersionCode();
    }
    private static Set<String> signers(Signature[] signatures)throws IOException {
        Set<String> result=new HashSet<>();if(signatures!=null)for(Signature signature:signatures)result.add(hex(sha256().digest(signature.toByteArray())));return result;
    }
    private static String fileHash(File file)throws IOException {
        MessageDigest digest=sha256();try(InputStream input=new FileInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1)if(n>0)digest.update(buffer,0,n);}return hex(digest.digest());
    }
    private static MessageDigest sha256()throws IOException {try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException error){throw new IOException("SHA-256 is unavailable.",error);}}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder(bytes.length*2);for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    private static String readText(String url,int limit)throws IOException {
        HttpURLConnection connection=open(url);try(InputStream input=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1){if(n==0)continue;if(out.size()>limit-n)throw new IOException("Update response is too large.");out.write(buffer,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }finally{connection.disconnect();}
    }
    private static final class HttpStatus extends IOException {final int code;HttpStatus(int code){super("Update server returned HTTP "+code+".");this.code=code;}}
    private static HttpURLConnection open(String value)throws IOException {
        for(int redirects=0;redirects<=5;redirects++){
            if(!NotebookUpdateSource.trustedHttps(value))throw new IOException("The update server redirected to an untrusted address.");
            HttpURLConnection connection=(HttpsURLConnection)new URL(value).openConnection();connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);connection.setReadTimeout(45000);connection.setRequestProperty("User-Agent","Scriflow-update-check");
            connection.setRequestProperty("Accept","application/vnd.github+json, application/octet-stream");connection.setRequestProperty("Accept-Encoding","identity");
            int status;
            try { status=connection.getResponseCode(); }
            catch(IOException failure){
                android.util.Log.w("ScriflowUpdate", "Connection to " + new URL(value).getHost() + " failed: " + failure.getClass().getSimpleName());
                connection.disconnect();throw failure;
            }
            if(status>=300&&status<400){String location=connection.getHeaderField("Location");connection.disconnect();if(location==null)throw new IOException("The update redirect is incomplete.");value=new URL(new URL(value),location).toString();continue;}
            if(status!=200){connection.disconnect();throw new HttpStatus(status);}return connection;
        }
        throw new IOException("Too many update redirects.");
    }
    private static String networkError(Throwable error){
        if(error instanceof HttpStatus){int code=((HttpStatus)error).code;return code==403||code==429?"GitHub's update request limit was reached. Try again later.":"The update server returned HTTP "+code+".";}
        if(error instanceof java.net.SocketTimeoutException)return "The update connection timed out. Try again.";
        if(error instanceof java.net.UnknownHostException)return "Could not find the update server. Check your connection.";
        if(error instanceof javax.net.ssl.SSLException)return "The update server's secure connection could not be verified.";
        if(error instanceof java.net.ConnectException)return "Could not connect to the update server. Try again.";
        if(error instanceof IOException){String message=error.getMessage();if(message!=null&&(message.startsWith("The installer")||message.startsWith("The downloaded update")||message.startsWith("The release checksum")||message.startsWith("Update is larger")||message.startsWith("Cannot ")||message.startsWith("Could not ")))return message;}
        return "Could not check or download the update. Check your connection and try again.";
    }
}
