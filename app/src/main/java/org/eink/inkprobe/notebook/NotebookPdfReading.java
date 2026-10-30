package org.eink.inkprobe.notebook;

import android.graphics.Bitmap;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded, optional reading analysis. Neither the ink writer nor pen callbacks parse/rasterize PDFs. */
final class NotebookPdfReading {
    interface CropCallback{void complete(NotebookCropBox box,IOException error);}
    interface PreviewCallback{void complete(Bitmap bitmap,IOException error);}
    interface TextCallback{void complete(NotebookPdfText text,IOException error);}
    static final class Request{volatile boolean cancelled;void cancel(){cancelled=true;}}
    private static final ExecutorService worker=new ThreadPoolExecutor(0,1,30,TimeUnit.SECONDS,new LinkedBlockingQueue<>(),r->new Thread(r,"pdf-reading-analysis"));
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final LinkedHashMap<Key,NotebookCropBox> cache=new LinkedHashMap<>(32,.75f,true);
    private static final Map<Key,List<Pending>> pending=new HashMap<>();
    private static final LinkedHashMap<Key,NotebookPdfText> textCache=new LinkedHashMap<>(4,.75f,true);
    private static final class Key{
        final File source;final int page;final NotebookCropBox area;
        Key(File source,int page,NotebookCropBox area){this.source=source;this.page=page;this.area=area;}
        public boolean equals(Object o){return o instanceof Key&&source.equals(((Key)o).source)&&page==((Key)o).page&&area.equals(((Key)o).area);}
        public int hashCode(){return Objects.hash(source,page,area);}
    }
    private static final class Pending{final Request request;final CropCallback callback;Pending(Request r,CropCallback c){request=r;callback=c;}}
    static NotebookCropBox cached(File source,int page,NotebookCropBox area){synchronized(cache){return cache.get(new Key(source,page,area));}}
    static List<NotebookViewport.Size> layout(List<NotebookViewport.Size> original,File source,NotebookReadingSettings settings){
        if(settings.crop==NotebookReadingSettings.Crop.NONE||source==null)return original;
        return settings.layout(original,(page,area)->cached(source,page,area));
    }
    static Request crop(File source,int page,int width,int height,NotebookCropBox area,CropCallback callback){
        Request request=new Request();Key key=new Key(source,page,area);Pending entry=new Pending(request,callback);
        synchronized(cache){
            NotebookCropBox ready=cache.get(key);
            if(ready!=null){main.post(()->{if(!request.cancelled)callback.complete(ready,null);});return request;}
            if(pending.containsKey(key)){pending.get(key).add(entry);return request;}
            pending.put(key,new ArrayList<>(Collections.singletonList(entry)));
        }
        worker.execute(()->{
            NotebookCropBox result=null;IOException error=null;
            try{
                boolean needed=false;synchronized(cache){for(Pending p:pending.get(key))needed|=!p.request.cancelled;}
                if(needed){
                    Bitmap bitmap=preview(source,page,width,height,1000);
                    try{int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
                        result=NotebookPdfCrop.detect(pixels,bitmap.getWidth(),bitmap.getHeight(),area);
                    }finally{bitmap.recycle();}
                }
            }catch(IOException|RuntimeException|OutOfMemoryError failure){error=new IOException("Cannot detect PDF margins",failure);}
            final NotebookCropBox found=result;final IOException problem=error;
            main.post(()->{
                List<Pending> callbacks;
                synchronized(cache){
                    callbacks=pending.remove(key);
                    if(found!=null){cache.put(key,found);while(cache.size()>512){Iterator<Key> oldest=cache.keySet().iterator();oldest.next();oldest.remove();}}
                }
                for(Pending p:callbacks)if(!p.request.cancelled){
                    if(found==null&&problem==null)crop(source,page,width,height,area,(box,failure)->{if(!p.request.cancelled)p.callback.complete(box,failure);});
                    else p.callback.complete(found,problem);
                }
            });
        });
        return request;
    }
    static Request preview(File source,int page,int width,int height,PreviewCallback callback){
        Request request=new Request();worker.execute(()->{
            Bitmap bitmap=null;IOException error=null;
            try{if(!request.cancelled)bitmap=preview(source,page,width,height,1400);}
            catch(IOException|RuntimeException|OutOfMemoryError failure){error=new IOException("Cannot prepare crop preview",failure);}
            final Bitmap result=bitmap;final IOException problem=error;
            main.post(()->{if(request.cancelled){if(result!=null)result.recycle();}else callback.complete(result,problem);});
        });return request;
    }
    private static Bitmap preview(File file,int page,int width,int height,int edge)throws IOException{
        double scale=Math.min(1,edge/(double)Math.max(width,height));
        return NotebookPdfEngine.render(file,page,Math.max(1,(int)Math.ceil(width*scale)),Math.max(1,(int)Math.ceil(height*scale)));
    }
    static Request text(File source,int page,TextCallback callback){
        Request request=new Request();Key key=new Key(source,page,NotebookCropBox.FULL);
        worker.execute(()->{
            if(request.cancelled)return;
            NotebookPdfText result;IOException error=null;
            synchronized(textCache){result=textCache.get(key);}
            if(result==null)try{
                result=NotebookPdfEngine.text(source,page);
                synchronized(textCache){textCache.put(key,result);while(textCache.size()>4){Iterator<Key> oldest=textCache.keySet().iterator();oldest.next();oldest.remove();}}
            }catch(IOException|RuntimeException|OutOfMemoryError failure){error=new IOException("Cannot read this PDF's text layer",failure);}
            final NotebookPdfText found=result;final IOException problem=error;
            main.post(()->{if(!request.cancelled)callback.complete(found,problem);});
        });return request;
    }
    static void trim(){synchronized(cache){cache.clear();}synchronized(textCache){textCache.clear();}}
    private NotebookPdfReading(){}
}
