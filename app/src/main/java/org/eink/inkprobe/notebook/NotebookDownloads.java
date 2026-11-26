package org.eink.inkprobe.notebook;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONException;
import org.json.JSONObject;

/** Streams Gecko's original response into app-owned entries in the tablet Downloads folder. */
final class NotebookDownloads {
    enum State { WAITING, DOWNLOADING, CANCELING, RECOVERING, COMPLETE, FAILED, CANCELED, INTERRUPTED }
    static final class Item {
        final String id, name, mime, uri, error;
        final State state; final long created, bytes, total;
        Item(Job job) {
            id=job.id; name=job.name; mime=job.mime; uri=job.uri; error=job.error;
            state=job.state; created=job.created; bytes=job.bytes; total=job.total;
        }
        boolean active() { return state==State.WAITING || state==State.DOWNLOADING || state==State.CANCELING; }
    }
    private static final class Job {
        final String id; final long created; final String mime; final long total;
        volatile String name, uri, error=""; volatile State state; volatile long bytes;
        volatile boolean cancel, removing; volatile InputStream input; volatile long lastNotice;
        Job(String id,String name,String mime,long created,long total,State state) {
            this.id=id;this.name=name;this.mime=mime;this.created=created;this.total=total;this.state=state;
        }
    }
    private static final int MAX_RECORDS=1000, MAX_ACTIVE=8;
    private static NotebookDownloads instance;
    static synchronized NotebookDownloads get(Context context) {
        if(instance==null)instance=new NotebookDownloads(context.getApplicationContext());
        return instance;
    }

    private final Context context; private final ContentResolver resolver; private final SharedPreferences preferences;
    private final Object lock=new Object(); private final Map<String,Job> jobs=new LinkedHashMap<>();
    private final CopyOnWriteArraySet<Runnable> listeners=new CopyOnWriteArraySet<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService transfers=Executors.newFixedThreadPool(4,r->new Thread(r,"browser-download"));
    private final ExecutorService maintenance=Executors.newSingleThreadExecutor(r->new Thread(r,"browser-download-history"));

    private NotebookDownloads(Context application) {
        context=application;resolver=context.getContentResolver();preferences=context.getSharedPreferences("browser-downloads",Context.MODE_PRIVATE);
        for(Map.Entry<String,?> saved:preferences.getAll().entrySet()) {
            if(!saved.getKey().startsWith("item-")||!(saved.getValue() instanceof String)||jobs.size()>=MAX_RECORDS)continue;
            try {
                JSONObject json=new JSONObject((String)saved.getValue());String id=saved.getKey().substring(5);
                if(!UUID.fromString(id).toString().equals(id))continue;
                State state=State.valueOf(json.getString("state"));
                String name=NotebookDownloadName.clean(json.getString("name"));
                String mime=NotebookDownloadName.mime(json.optString("mime"));
                long created=json.getLong("created"),total=json.optLong("total",-1);
                if(created<=0||total < -1)continue;
                Job job=new Job(id,name,mime,created,total,state);job.bytes=Math.max(0,json.optLong("bytes",0));
                job.uri=json.optString("uri","");job.error=json.optString("error","");
                if(!job.uri.isEmpty()&&!validUri(job.uri))continue;
                if(active(state)||state==State.RECOVERING)job.state=State.RECOVERING;
                jobs.put(id,job);
                if(job.state==State.RECOVERING)maintenance.execute(()->recover(job));
            } catch(JSONException|IllegalArgumentException ignored) { /* Keep unrelated downloads available. */ }
        }
    }

    String start(InputStream body,String url,Map<String,String> headers,int status) throws IOException {
        if(body==null)throw new IOException("This website did not provide a download stream.");
        if(status>=400){close(body);throw new IOException("The website returned HTTP "+status+".");}
        String mime=NotebookDownloadName.mime(headers.get("content-type"));
        String name=NotebookDownloadName.choose(url,headers.get("content-disposition"));
        if(NotebookFiles.extension(name).isEmpty()) {
            String extension=MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if(extension!=null&&!extension.isEmpty())name=NotebookDownloadName.clean(name+"."+extension);
        }
        long size=NotebookDownloadName.length(headers.get("content-length"),headers.get("content-encoding"));
        Job job=new Job(UUID.randomUUID().toString(),name,mime,System.currentTimeMillis(),size,State.WAITING);job.input=body;
        synchronized(lock) {
            if(jobs.size()>=MAX_RECORDS){close(body);throw new IOException("Remove some download history before starting another download.");}
            if(activeCountLocked()>=MAX_ACTIVE){close(body);throw new IOException("Wait for a download to finish before starting another.");}
            jobs.put(job.id,job);persist(job,false);
        }
        try { context.startForegroundService(new Intent(context,NotebookDownloadService.class)); }
        catch(RuntimeException ignored) { /* The transfer still runs while the app process remains alive. */ }
        transfers.execute(()->transfer(job));changed();return job.name;
    }

    private void transfer(Job job) {
        Uri uri=null; boolean published=false;
        try(InputStream input=job.input) {
            if(job.cancel)throw new InterruptedIOException("Download canceled.");
            ContentValues values=new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME,job.name);values.put(MediaStore.Downloads.MIME_TYPE,job.mime);
            values.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/");
            values.put(MediaStore.Downloads.IS_PENDING,1);
            uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
            if(uri==null)throw new IOException("Could not create a file in Downloads.");
            synchronized(lock) {
                job.uri=uri.toString();job.name=displayName(uri,job.name);job.state=job.cancel?State.CANCELING:State.DOWNLOADING;
                if(!persist(job,true))throw new IOException("Could not record the download.");
            }
            changed();
            ParcelFileDescriptor descriptor=resolver.openFileDescriptor(uri,"w");
            if(descriptor==null)throw new IOException("Could not open the download destination.");
            try(OutputStream output=new ParcelFileDescriptor.AutoCloseOutputStream(descriptor)) {
                job.bytes=NotebookDownloadStream.copy(input,output,job.total,new NotebookDownloadStream.Control(){
                    public boolean cancelled(){return job.cancel;}
                    public void progress(long bytes){
                        job.bytes=bytes;long now=SystemClock.uptimeMillis();
                        if(now-job.lastNotice>=2000){job.lastNotice=now;changed();}
                    }
                });
            }
            synchronized(lock) {
                if(job.cancel)throw new InterruptedIOException("Download canceled.");
                ContentValues valuesToPublish=new ContentValues();valuesToPublish.put(MediaStore.Downloads.IS_PENDING,0);
                if(resolver.update(uri,valuesToPublish,null,null)!=1)throw new IOException("Could not finish publishing the download.");
                published=true;
                job.name=displayName(uri,job.name);job.state=State.COMPLETE;job.error="";persist(job,true);
            }
        } catch(IOException|RuntimeException failure) {
            boolean removed=uri==null;
            if(uri!=null&&!published)try{removed=resolver.delete(uri,null,null)>0;}catch(RuntimeException ignored) { }
            synchronized(lock) {
                if(removed)job.uri="";
                job.state=published?State.COMPLETE:job.cancel?State.CANCELED:State.FAILED;
                job.error=published?"":!removed?"Could not remove the incomplete file. Delete it from the download manager.":job.cancel?"":safeError(failure);persist(job,true);
            }
        } finally { job.input=null;changed(); }
    }

    private void recover(Job job) {
        boolean published=false, missing=job.uri.isEmpty(), removed=false, unknown=false;
        try {
            if(!job.uri.isEmpty()) {
                Uri uri=Uri.parse(job.uri);
                try(Cursor cursor=resolver.query(uri,new String[]{MediaStore.Downloads.IS_PENDING,MediaStore.Downloads.DISPLAY_NAME,MediaStore.Downloads.SIZE},null,null,null)) {
                    if(cursor!=null&&cursor.moveToFirst()) {
                        published=cursor.getInt(0)==0;
                        if(published){job.name=NotebookDownloadName.clean(cursor.getString(1));job.bytes=Math.max(0,cursor.getLong(2));}
                    } else if(cursor!=null)missing=true;
                    else unknown=true;
                }
                if(!published&&!missing&&!unknown)removed=resolver.delete(uri,null,null)>0;
            }
        } catch(RuntimeException ignored) { unknown=true; }
        synchronized(lock) {
            if(published){job.state=State.COMPLETE;job.error="";}
            else{job.state=State.INTERRUPTED;if(missing||removed)job.uri="";job.error=unknown?"Could not inspect the previous download. Check Downloads in Files.":"The app stopped before the download finished. Download it again from the website.";}
            persist(job,true);
        }
        changed();
    }

    void cancel(String id) {
        Job job;
        synchronized(lock) {
            job=jobs.get(id);if(job==null||!active(job.state)||job.cancel)return;
            job.cancel=true;job.state=State.CANCELING;persist(job,false);
        }
        InputStream input=job.input;
        if(input!=null)new Thread(()->close(input),"browser-download-cancel").start();
        changed();
    }

    void remove(String id,boolean deleteFile,java.util.function.Consumer<String> callback) {
        Job job;
        synchronized(lock) {
            job=jobs.get(id);if(job==null||active(job.state)||job.state==State.RECOVERING||job.removing)return;
            job.removing=true;
        }
        maintenance.execute(()->{
            String error=null;
            try {
                if(deleteFile&&!job.uri.isEmpty())resolver.delete(Uri.parse(job.uri),null,null);
                synchronized(lock) {
                    if(!preferences.edit().remove("item-"+id).commit())throw new IOException("Could not update download history.");
                    jobs.remove(id);
                }
            } catch(IOException|RuntimeException failure){error=safeError(failure);job.removing=false;}
            final String result=error;main.post(()->{changed();callback.accept(result);});
        });
    }

    List<Item> items() {
        List<Item> result=new ArrayList<>();synchronized(lock){for(Job job:jobs.values())result.add(new Item(job));}
        result.sort(Comparator.comparingLong((Item item)->item.created).reversed());return Collections.unmodifiableList(result);
    }
    int activeCount(){synchronized(lock){return activeCountLocked();}}
    private int activeCountLocked(){int count=0;for(Job job:jobs.values())if(active(job.state))count++;return count;}
    private static boolean active(State state){return state==State.WAITING||state==State.DOWNLOADING||state==State.CANCELING;}
    void observe(Runnable listener){listeners.add(listener);}
    void removeObserver(Runnable listener){listeners.remove(listener);}
    private void changed(){main.post(()->{for(Runnable listener:listeners)listener.run();});}

    private boolean persist(Job job,boolean commit) {
        try {
            JSONObject value=new JSONObject().put("name",job.name).put("mime",job.mime).put("created",job.created)
                .put("total",job.total).put("bytes",job.bytes).put("state",job.state.name())
                .put("uri",job.uri==null?"":job.uri).put("error",job.error);
            SharedPreferences.Editor edit=preferences.edit().putString("item-"+job.id,value.toString());
            if(commit)return edit.commit();edit.apply();return true;
        } catch(JSONException error){return false;}
    }
    private String displayName(Uri uri,String fallback) {
        try(Cursor cursor=resolver.query(uri,new String[]{MediaStore.Downloads.DISPLAY_NAME},null,null,null)) {
            if(cursor!=null&&cursor.moveToFirst())return NotebookDownloadName.clean(cursor.getString(0));
        } catch(RuntimeException ignored) { }
        return fallback;
    }
    private static boolean validUri(String value) {
        Uri uri=Uri.parse(value);
        return "content".equals(uri.getScheme())&&"media".equals(uri.getAuthority())
            &&uri.getQuery()==null&&uri.getFragment()==null&&uri.getPathSegments().size()==3
            &&(uri.getPathSegments().get(0).equals("external")||uri.getPathSegments().get(0).equals("external_primary"))
            &&uri.getPathSegments().get(1).equals("downloads")&&uri.getPathSegments().get(2).matches("[0-9]+");
    }
    private static String safeError(Throwable failure) {
        if(failure instanceof SecurityException)return "Storage access was denied.";
        return "The download could not be completed. Check the connection and try again.";
    }
    private static void close(InputStream input){try{input.close();}catch(IOException ignored) { }}
}
