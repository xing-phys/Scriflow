package org.eink.inkprobe.notebook;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native shell for offline books. Linked ink uses the established native notebook editor. */
public class NotebookEbookActivity extends NotebookDisplayActivity {
    private static final int EXPORT = 2472, FONT = 2473;
    private NotebookEbookStore store;
    private NotebookEbookPage page;
    private String book, title = "Book", pendingExport;
    private NotebookEbookChrome chrome;
    private TextView status;
    private boolean resumed, busy;
    private AlertDialog dialog;
    private NotebookPdfImport snapshotImport;
    private JSONObject snapshotLink;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); book = getIntent().getStringExtra("book"); store = new NotebookEbookStore(getFilesDir());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN); getWindow().setWindowAnimations(0);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.WHITE);
        status=new TextView(this);status.setTextColor(Color.BLACK);status.setTextSize(13);status.setPadding(dp(12),dp(5),dp(12),dp(5));status.setVisibility(View.GONE);
        try {
            // Validate identities before creating any web/native bridge.
            if (book == null || !book.matches("[a-f0-9]{64}")) throw new IOException("Choose a book from the bookshelf.");
            page = new NotebookEbookPage(this, store, book, this::message); root.addView(page,new FrameLayout.LayoutParams(-1,-1));
            NotebookEbookTasks.run(() -> store.book(book), (entry,error) -> { if (!isDestroyed() && error == null) { title=entry.title;if(chrome!=null)chrome.title(title); } });
        } catch (Exception error) { problem(error.getMessage()); }
        chrome=new NotebookEbookChrome(this,root,()->resumed&&!busy,this::action);
        FrameLayout.LayoutParams notice=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);notice.setMargins(dp(12),0,dp(12),dp(10));
        status.setBackground(NotebookUi.statusIsland(this).getBackground());root.addView(status,notice);
        setContentView(root);
    }
    @Override protected void onResume() { super.onResume(); resumed=true; if(page!=null)page.active(true);if(chrome!=null)chrome.active(true); }
    @Override protected void onPause() { resumed=false;if(chrome!=null)chrome.active(false);if(page!=null)page.active(false);if(dialog!=null){dialog.dismiss();dialog=null;}super.onPause(); }
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration) { super.onConfigurationChanged(configuration); }
    @Override protected void onDestroy() { if(snapshotImport!=null)snapshotImport.detach();if(chrome!=null)chrome.close();if(page!=null)page.close();super.onDestroy(); }
    @Override public void onBackPressed() { if(busy)return;if(page!=null)page.command("back");else super.onBackPressed(); }
    private void message(JSONObject message) {
        try {
            switch(message.optString("type")) {
                case "ready":
                    title=message.optString("title",title);chrome.title(title);status.setVisibility(View.GONE);
                    String author=message.optString("author","");NotebookEbookTasks.run(()->{store.metadata(book,message.optString("documentTitle",title),author);return null;},(value,error)->{if(error!=null)problem(error.getMessage());});
                    onReaderMessage(message);break;
                case "error":problem(message.optString("message","Could not read this book."));onReaderMessage(message);break;
                case "copy":
                    String selected=message.optString("text","");getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Book text",selected));break;
                case "lookup":lookup(message.optString("text",""));break;
                case "handwrite":if(!busy)handwrite(message);break;
                case "open-note":if(!busy)openNote(message.optInt("notebook"));break;
                case "snapshot":if(!busy)snapshot(message);break;
                case "export":if(!busy)export(message);break;
                case "import-font":if(!busy)pickFont();break;
                case "zen":chrome.zen(message.optBoolean("enabled"));break;
                case "selection":chrome.selection(message.optBoolean("enabled"));break;
                case "panel":chrome.panel(message.optBoolean("open"));break;
                case "reading-state":chrome.progress(message.optInt("percentage"));chrome.bookmark(message.optBoolean("bookmarked"));chrome.history(message.optBoolean("back"),message.optBoolean("forward"));break;
                case "external-link":externalLink(message.optString("url"));break;
                case "exit":leave();break;
                default:onReaderMessage(message);
            }
        } catch(Exception error){problem(error.getMessage());}
    }
    /** Debug-only fixture activities can observe real engine replies without exposing reader state. */
    protected void onReaderMessage(JSONObject message) { }
    protected final void readerCommand(JSONObject command) { if(page!=null)page.send(command); }
    protected final JSONObject readerUi()throws org.json.JSONException{return chrome==null?new JSONObject():chrome.debug();}
    private void lookup(String text) {
        if(text.isEmpty())return;String term=text.substring(0,Math.min(200,text.length()));
        try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("eudic://peek/"+Uri.encode(term)))); }
        catch(ActivityNotFoundException missing){problem("Install Eudic and enable its floating dictionary to look up selected text.");}
    }
    private void externalLink(String url) {
        Uri uri=Uri.parse(url);if(!"https".equals(uri.getScheme())){problem("Only HTTPS links can open in Browser.");return;}
        dialog=new AlertDialog.Builder(this).setTitle("Open link in Browser?").setMessage(url).setNegativeButton("Cancel",null)
            .setPositiveButton("Open",(d,n)->startActivity(new Intent(this,NotebookBrowserActivity.class).putExtra(NotebookBrowserActivity.INITIAL_URL,url))).create();NotebookUi.show(dialog);
    }
    private void handwrite(JSONObject link) throws Exception {
        JSONObject state=link.getJSONObject("state");String value=link.optString("value");
        if(value.isEmpty()||value.length()>16384)throw new IOException("Select a passage or wait for the book to finish opening.");
        JSONArray annotations=state.optJSONArray("annotations");
        if(annotations!=null)for(int i=0;i<annotations.length();i++){
            JSONObject item=annotations.optJSONObject(i);if(item!=null&&value.equals(item.optString("value"))&&item.optInt("notebook")>0){openNote(item.optInt("notebook"));return;}
        }
        busy=true;showStatus("Creating linked handwritten notebook…");
        working(true);
        NotebookSession session=NotebookSession.get(this);String noteTitle="Notes — "+title.substring(0,Math.min(70,title.length()));
        session.list((ignored,error)->{
            if(error!=null){busy=false;working(false);problem(error.getMessage());return;}
            session.createNotebook(noteTitle,NotebookPaperSize.A5,(loaded,failure)->{
                if(failure!=null){busy=false;working(false);problem(failure.getMessage());return;}
                try { linkNotebook(link,session.notebookId(),false,()->launchNote()); }
                catch(Exception linkError){busy=false;working(false);problem(linkError.getMessage());}
            });
        });
    }
    private void linkNotebook(JSONObject link,int notebook,boolean snapshot,Runnable ready) throws Exception {
        JSONObject state=link.getJSONObject("state");JSONArray annotations=state.optJSONArray("annotations");
        if(annotations==null){annotations=new JSONArray();state.put("annotations",annotations);}
        String value=link.optString("value");JSONObject item=null;
        if(!snapshot)for(int i=0;i<annotations.length();i++){JSONObject candidate=annotations.optJSONObject(i);if(candidate!=null&&value.equals(candidate.optString("value"))){item=candidate;break;}}
        if(item==null){item=new JSONObject().put("id",UUID.randomUUID().toString()).put("value",value).put("text",link.optString("text",title)).put("created",System.currentTimeMillis());annotations.put(item);}
        item.put("notebook",notebook);if(snapshot)item.put("snapshot",true);else if(link.optBoolean("mark"))item.put("mark",true);
        page.replaceState(state,error->{if(!isDestroyed()){busy=false;working(false);page.command("snapshot-done");if(error!=null)problem(error.getMessage());else{status.setVisibility(View.GONE);if(resumed)ready.run();}}});
    }
    private void openNote(int id) {
        if(id<1)return;busy=true;showStatus("Opening handwritten notebook…");
        NotebookSession session=NotebookSession.get(this);session.openNotebook(id,(loaded,error)->{
            busy=false;if(error!=null){problem("Couldn’t open that notebook. It may be in the bookshelf Trash. "+error.getMessage());return;}
            if(resumed&&!isDestroyed())launchNote();
        });
    }
    private void launchNote() { if(!resumed||isDestroyed())return;status.setVisibility(View.GONE);startActivity(new Intent(this,NotebookActivity.class));overridePendingTransition(0,0); }
    private void snapshot(JSONObject link) {
        busy=true;showStatus("Creating a fixed copy of this reading view…");
        page.snapshot(bitmap->{
            if(bitmap==null){busy=false;page.command("snapshot-done");problem("Could not capture the reading view. Wait for the page to finish drawing and retry.");return;}
            snapshotLink=link;
            NotebookEbookTasks.run(()->{
                Bitmap image=bitmap;
                try {
                    JSONObject box=link.getJSONObject("box");double ratio=bitmap.getWidth()/box.getDouble("viewportWidth");
                    int x=Math.max(0,(int)Math.round(box.getDouble("x")*ratio)),y=Math.max(0,(int)Math.round(box.getDouble("y")*ratio));
                    int w=Math.min(bitmap.getWidth()-x,(int)Math.round(box.getDouble("width")*ratio)),h=Math.min(bitmap.getHeight()-y,(int)Math.round(box.getDouble("height")*ratio));
                    if(w<1||h<1)throw new IOException("Invalid reading-view bounds.");
                    image=Bitmap.createBitmap(bitmap,x,y,w,h);
                    File file=new File(getCacheDir(),"ebook-view-"+UUID.randomUUID()+".pdf");
                    PdfDocument pdf=new PdfDocument();try{
                        PdfDocument.Page output=pdf.startPage(new PdfDocument.PageInfo.Builder(w,h,1).create());output.getCanvas().drawBitmap(image,0,0,null);pdf.finishPage(output);
                        try(FileOutputStream stream=new FileOutputStream(file)){pdf.writeTo(stream);stream.getFD().sync();}
                    }finally{pdf.close();}return file;
                }finally{if(image!=bitmap)image.recycle();bitmap.recycle();}
            },(file,error)->{
                if(isDestroyed())return;
                if(error!=null){busy=false;page.command("snapshot-done");problem(error.getMessage());return;}
                Uri uri=new Uri.Builder().scheme("content").authority(getPackageName()+".ebookviews").appendPath(file.getName()).build();
                snapshotImport=new NotebookPdfImport(this);snapshotImport.flow.beginPicker();
                snapshotImport.message="Preparing fixed reading copy…";snapshotImport.observe(()->{
                    showStatus(snapshotImport.message);
                    if(snapshotImport.flow.phase()==NotebookPdfImportFlow.Phase.ERROR){busy=false;page.command("snapshot-done");problem(snapshotImport.message);snapshotImport.detach();file.delete();}
                    else if(snapshotImport.flow.phase()==NotebookPdfImportFlow.Phase.READY&&snapshotImport.flow.beginOpen()){
                        snapshotImport.detach();file.delete();try{linkNotebook(snapshotLink,snapshotImport.flow.notebookId(),true,this::launchNote);}catch(Exception failure){busy=false;page.command("snapshot-done");problem(failure.getMessage());}
                    }
                });snapshotImport.importPdf(uri);
            });
        });
    }
    private void pickFont() {
        try{startActivityForResult(NotebookFilePicker.open(this, "font"),FONT);}
        catch(ActivityNotFoundException error){problem("Could not open Files.");}
    }
    private void export(JSONObject message) {
        String contents=message.optString("text");if(contents.getBytes(StandardCharsets.UTF_8).length>NotebookEbookStore.MAX_STATE_BYTES){problem("This notes export exceeds 2 MiB.");return;}
        pendingExport=contents;
        Intent intent=NotebookFilePicker.save(this, title.replaceAll("[/\\\\\\p{Cntrl}]","_")+"-"+new File(message.optString("name","notes.json")).getName());
        try{startActivityForResult(intent,EXPORT);}catch(ActivityNotFoundException error){pendingExport=null;problem("Could not open Files.");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null){if(request==EXPORT)pendingExport=null;return;}
        Uri uri=data.getData();
        if(request==EXPORT){
            String contents=pendingExport;pendingExport=null;if(contents==null){problem("Export was interrupted. Choose Export reading notes again.");return;}
            NotebookEbookTasks.run(()->{try(OutputStream output=getContentResolver().openOutputStream(uri,"wt")){if(output==null)throw new IOException("Could not open the destination.");output.write(contents.getBytes(StandardCharsets.UTF_8));}return null;},(value,error)->{if(error!=null)problem(error.getMessage());else showStatus("Reading notes exported.");});
        }else if(request==FONT){
            NotebookEbookTasks.run(()->{try(InputStream input=getContentResolver().openInputStream(uri)){store.font(book,input);}return store.font(book).length();},(size,error)->{
                if(error!=null)problem(error.getMessage());else try{page.send(new JSONObject().put("type","font-imported").put("size",size));}catch(Exception failure){problem(failure.getMessage());}
            });
        }
    }
    private void action(String action){
        if(busy)return;try{if(page!=null)page.send(new JSONObject().put("type","command").put("action",action));else if(action.equals("close"))leave();}
        catch(Exception error){problem(error.getMessage());}
    }
    private void leave() {
        if(busy)return;startActivity(new Intent(this,NotebookLibraryActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();overridePendingTransition(0,0);
    }
    private void showStatus(String message) { if(isDestroyed())return;status.setText(message);status.setVisibility(View.VISIBLE); }
    private void working(boolean value){try{if(page!=null)page.send(new JSONObject().put("type","busy").put("enabled",value));}catch(Exception impossible){throw new AssertionError(impossible);}}
    private void problem(String message) { if(isDestroyed())return;showStatus(message==null?"Could not complete the reading action.":message); }
    private int dp(int size) { return Math.round(size*getResources().getDisplayMetrics().density); }
}
