package org.eink.inkprobe.notebook;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Explicit emulator fixture entry point. Never included in a release APK. */
public final class NotebookEbookSelfTestActivity extends NotebookEbookActivity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String identity,mode,anchor;
    private boolean waitingNotebook,finished;
    private int paints;
    private boolean uiActive;
    private final Runnable uiTick=new Runnable(){public void run(){
        if(!uiActive||isDestroyed())return;
        try(FileOutputStream output=new FileOutputStream(new File(getCacheDir(),"ebook-chrome-check.json"))){output.write(readerUi().toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
        handler.postDelayed(this,400);
    }};
    @Override protected void onResume(){super.onResume();uiActive=true;handler.post(uiTick);}
    @Override public void onCreate(Bundle saved){
        mode=getIntent().getStringExtra("mode");if(mode==null)mode="read";
        try{
            String name=getIntent().getStringExtra("fixture");
            if(name==null||!name.matches("[A-Za-z0-9_.-]{1,100}")||name.contains(".."))throw new IOException("Invalid fixture name.");
            File file=new File(new File(getCacheDir(),"ebook-check-input"),name);
            NotebookEbookStore store=new NotebookEbookStore(getFilesDir());
            try(InputStream input=new FileInputStream(file)){identity=store.importBook(input,name,n->{});}
            if(!mode.equals("restore")&&!mode.equals("reopen-title")){store.state(identity,"{}");store.metadata(identity,NotebookEbookStore.title(name),"");}
            String label=getIntent().getStringExtra("label");if(label!=null)store.rename(identity,label);
            getIntent().putExtra("book",identity);
        }catch(Exception error){writeError(error);}
        super.onCreate(saved);
        handler.postDelayed(()->{if(!finished)try{probe("diagnostic");}catch(Exception e){writeError(e);}},8000);
    }
    private void probe(String id)throws JSONException{readerCommand(new JSONObject().put("type","probe").put("id",id));}
    private void action(String id,String action)throws JSONException{readerCommand(new JSONObject().put("type","probe").put("id",id).put("action",action));}
    @Override protected void onReaderMessage(JSONObject message){
        if(finished)return;
        try{
            String type=message.optString("type");
            if(type.equals("error")){
                String error=message.optString("message");
                if(mode.equals("reject")&&(error.contains("DRM")||error.contains("encryption"))){writeResult(message);return;}
                writeError(new IOException(error+" "+message.optString("stack")));return;
            }
            if(type.equals("ready")){handler.postDelayed(()->{try{probe("initial");}catch(Exception e){writeError(e);}},1200);return;}
            if(!type.equals("probe"))return;
            String id=message.optString("id");
            if(id.equals("diagnostic")){
                try(FileOutputStream output=new FileOutputStream(new File(getCacheDir(),"ebook-diagnostic.json"))){output.write(message.toString().getBytes(StandardCharsets.UTF_8));}return;
            }
            if(id.equals("initial")){
                JSONArray documents=message.optJSONArray("documents");
                if(documents==null||documents.length()==0)throw new IOException("No rendered book document.");
                boolean canvas=false,painted=false,content=false;
                for(int d=0;d<documents.length();d++){
                    JSONObject doc=documents.getJSONObject(d);content|=!doc.optString("text").trim().isEmpty();
                    JSONArray images=doc.optJSONArray("images");if(images!=null)for(int n=0;n<images.length();n++)content|=images.getJSONObject(n).optInt("width")>0;
                    JSONArray canvases=doc.optJSONArray("canvases");if(canvases!=null)for(int c=0;c<canvases.length();c++){canvas=true;painted|=canvases.getJSONObject(c).optInt("ink")>0;}
                }
                if(canvas&&!painted){if(++paints>30)throw new IOException("DjVu did not paint pixels.");handler.postDelayed(()->{try{probe("initial");}catch(Exception e){writeError(e);}},1000);return;}
                if(!content&&!painted)throw new IOException("The book opened a blank frame.");
                if(mode.equals("reject"))throw new IOException("An encrypted book was accepted.");
                if(mode.equals("read")||mode.equals("reopen-title")){
                    if(getIntent().getStringExtra("fixture").endsWith(".cbz")&&!message.optString("title").equals("comic"))throw new IOException("The CBZ shelf title changed on reopening.");
                    writeResult(message);return;
                }
                if(mode.equals("restore")){
                    if(message.getJSONObject("settings").optInt("size")!=34||message.getJSONArray("annotations").length()!=1)throw new IOException("Saved reflow settings or annotations were lost.");
                    writeResult(message);return;
                }
                if(mode.equals("font")){
                    NotebookEbookStore store=new NotebookEbookStore(getFilesDir());
                    try(InputStream input=new FileInputStream(new File(getCacheDir(),"ebook-check-input/custom.ttf"))){store.font(identity,input);}
                    readerCommand(new JSONObject().put("type","font-imported").put("size",store.font(identity).length()));
                    handler.postDelayed(()->{try{probe("font");}catch(Exception e){writeError(e);}},1200);return;
                }
                if(mode.equals("snapshot")){waitingNotebook=true;action("capture","snapshot");return;}
                readerCommand(new JSONObject().put("type","probe").put("id","selected").put("action","select").put("text","The uncertainty principle"));
            }else if(id.equals("selected")){
                if(mode.equals("ink")){waitingNotebook=true;action("ink","handwrite");}
                else action("highlighted","highlight");
            }else if(id.equals("highlighted")){
                anchor=message.getJSONArray("annotations").getJSONObject(0).getString("value");
                readerCommand(new JSONObject().put("type","probe").put("id","styled").put("action","settings").put("settings",new JSONObject().put("size",34).put("font","serif").put("margin",40)));
            }else if(id.equals("styled")){
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                handler.postDelayed(()->{try{readerCommand(new JSONObject().put("type","probe").put("id","returned").put("action","goto").put("value",anchor));}catch(Exception e){writeError(e);}},800);
            }else if(id.equals("returned")){
                boolean resolved=false;JSONArray values=message.getJSONArray("resolved");for(int n=0;n<values.length();n++)resolved|=values.getJSONObject(n).optString("text").equals("The uncertainty principle");
                if(!resolved)throw new IOException("The text anchor moved after reflow.");
                if(!message.getJSONArray("documents").getJSONObject(0).getJSONObject("typography").getString("size").equals("34px"))throw new IOException("The requested font size was not applied.");
                readerCommand(new JSONObject().put("type","flush"));handler.postDelayed(()->writeResult(message),900);
            }else if(id.equals("font")){
                if(!message.getJSONObject("settings").optString("font").equals("custom")||!message.getJSONArray("documents").getJSONObject(0).getJSONObject("typography").getString("font").contains("ScriflowBookFont"))throw new IOException("The custom font did not load.");writeResult(message);
            }
        }catch(Exception error){writeError(error);}
    }
    @Override protected void onPause(){
        uiActive=false;handler.removeCallbacks(uiTick);
        super.onPause();
        if(waitingNotebook&&!finished)handler.postDelayed(()->{
            try{
                JSONObject state=new JSONObject(new NotebookEbookStore(getFilesDir()).state(identity));JSONArray notes=state.getJSONArray("annotations");JSONObject linked=notes.getJSONObject(notes.length()-1);
                if(linked.optInt("notebook")<1||mode.equals("snapshot")&&!linked.optBoolean("snapshot"))throw new IOException("Missing durable handwritten-note link.");
                NotebookSession session=NotebookSession.get(this);
                if(mode.equals("snapshot")&&(session.pdfSource()==null||session.pageCount()!=1))throw new IOException("The fixed reading view was not imported as a PDF notebook.");
                if(mode.equals("snapshot")){
                    android.graphics.Bitmap pixels=NotebookPdfEngine.render(session.pdfSource(),1,96,128);int ink=0;
                    for(int y=0;y<pixels.getHeight();y++)for(int x=0;x<pixels.getWidth();x++)if(pixels.getPixel(x,y)!=android.graphics.Color.WHITE)ink++;
                    pixels.recycle();if(ink<5)throw new IOException("The fixed reading copy is blank.");
                }
                writeResult(new JSONObject().put("linked",linked).put("notebook",session.notebookId()).put("pageCount",session.pageCount()).put("pdf",session.pdfSource()==null?JSONObject.NULL:session.pdfSource().getName()));
            }catch(Exception error){writeError(error);}
        },900);
    }
    private void writeResult(JSONObject value){
        if(finished)return;finished=true;
        try{value.put("passed",true).put("mode",mode).put("book",identity);write(value);}catch(Exception error){writeError(error);}
    }
    private void writeError(Exception error){
        finished=true;try{write(new JSONObject().put("passed",false).put("error",String.valueOf(error)).put("mode",mode));}catch(Exception ignored){ }
    }
    private void write(JSONObject value)throws IOException{
        try(FileOutputStream output=new FileOutputStream(new File(getCacheDir(),"ebook-reader-check.json"))){output.write(value.toString().getBytes(StandardCharsets.UTF_8));output.getFD().sync();}
    }
}
