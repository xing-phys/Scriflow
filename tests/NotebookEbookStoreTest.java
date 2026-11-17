package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Real files: original independence, deduplication, state recovery and failed-import cleanup. */
public final class NotebookEbookStoreTest {
    private static int assertions;
    public static void main(String[] args)throws Exception{
        File directory=Files.createTempDirectory("scriflow-ebooks-").toFile();
        try{
            NotebookEbookStore store=new NotebookEbookStore(directory);
            byte[] source="An immutable original ebook, with 中文 and quantum information.".getBytes(StandardCharsets.UTF_8);
            String id=store.importBook(new ByteArrayInputStream(source),"Physics 中文.epub",bytes->{});
            check(id.matches("[a-f0-9]{64}"),"Content and format define a stable private identity");
            check(Arrays.equals(source,Files.readAllBytes(store.source(id).toPath())),"A complete independent original is published");
            check(store.book(id).title.equals("Physics 中文")&&store.book(id).format.equals("epub"),"Unicode display names and format are retained");
            check(store.importBook(new ByteArrayInputStream(source),"A renamed file.epub",bytes->{}).equals(id)&&store.list().size()==1,"Reimporting identical bytes under another filename deduplicates the book");
            String html=store.importBook(new ByteArrayInputStream(source),"Same bytes.html",bytes->{});
            check(!html.equals(id),"Intentional text-versus-HTML interpretations have separate state");
            check(store.state(id).equals("{}"),"A new book has clean reading state");
            String first="{\"version\":1,\"location\":\"epubcfi(/6/2!/4/2:12)\",\"note\":\"中文\"}";
            String second="{\"version\":1,\"settings\":{\"size\":34},\"notebook\":19}";
            store.state(id,first);store.state(id,second);
            check(store.state(id).equals(second),"Typography and text anchors round-trip without a page format migration");
            File saved=new File(directory,"ebooks/"+id+"/state"),backup=new File(saved+".bak");
            Files.write(saved.toPath(),new byte[]{1,2,3});
            check(store.state(id).equals(first),"A damaged active state recovers the last valid predecessor");
            store.state(id,second);
            check(store.state(id).equals(second)&&backup.isFile(),"Writing after recovery keeps the valid predecessor");
            NotebookEbookStore.writeFrame(new File(saved+".next"),first.getBytes(StandardCharsets.UTF_8));
            Files.write(saved.toPath(),new byte[]{3,4});
            check(store.state(id).equals(first),"A complete interrupted publication can recover from its next file");
            Files.write(new File(saved+".next").toPath(),new byte[]{0});Files.write(backup.toPath(),new byte[]{0});
            fails(()->store.state(id),"All damaged state copies report an error instead of resetting annotations");
            check(saved.exists()&&backup.exists()&&new File(saved+".next").exists(),"Damaged recovery data is retained for inspection");
            store.state(html,first);
            fails(()->store.state(html,new String(new char[NotebookEbookStore.MAX_STATE_BYTES+1])),"Oversized state is refused before replacing the current state");
            check(store.state(html).equals(first),"An oversized write leaves saved annotations intact");
            for(String invalid:new String[]{"../outside","a",id+"0",id.substring(1),"A"+id.substring(1)})fails(()->store.source(invalid),"Book IDs cannot traverse outside the book store");
            fails(()->store.importBook(new ByteArrayInputStream(source),"unrecognized.zip",n->{}),"Generic ZIP files are not advertised as readable books");
            fails(()->store.importBook(new ByteArrayInputStream(new byte[0]),"empty.epub",n->{}),"Empty imports do not become books");
            InputStream broken=new InputStream(){int count;@Override public int read()throws IOException{if(++count>5)throw new IOException("injected failure");return 1;}};
            fails(()->store.importBook(broken,"broken.mobi",n->{}),"Provider failures abort private staging");
            check(Arrays.stream(new File(directory,"ebooks").list()).noneMatch(name->name.startsWith(".import-")),"Failed imports leave no partial library entries");
            byte[] font={0,1,0,0,2,3,4,5};store.font(html,new ByteArrayInputStream(font));
            fails(()->store.font(html,new ByteArrayInputStream(new byte[]{1,2,3,4,5})),"Nonfont files are rejected");
            check(Arrays.equals(font,Files.readAllBytes(store.font(html).toPath())),"Rejected font imports keep the previous font");
            store.metadata(html,"Updated title","Test author");check(store.book(html).title.equals("Updated title")&&store.book(html).author.equals("Test author"),"Parsed metadata updates the shelf independently of the original bytes");
            store.rename(html,"Personal reading name");store.move(html,7);store.trash(html,true);
            NotebookEbookStore reopened=new NotebookEbookStore(directory);
            check(reopened.book(html).title.equals("Personal reading name")&&reopened.book(html).folderId==7&&reopened.book(html).trashed,"Shared-shelf name, folder and Trash survive restart");
            reopened.metadata(html,"Publisher metadata","Author");
            check(reopened.book(html).title.equals("Personal reading name")&&reopened.book(html).documentTitle.equals("Publisher metadata"),"Reopening a book cannot replace the owner's shelf name with publisher metadata");
            check(reopened.state(html).equals(first)&&Arrays.equals(source,Files.readAllBytes(reopened.source(html).toPath())),"Shelf changes preserve the original bytes and reading annotations");
            check(reopened.importBook(new ByteArrayInputStream(source),"Book.html",n->{}).equals(html)&&!reopened.book(html).trashed,"Importing the same original from Trash restores the existing book without duplicating its state");
            reopened.move(html,0);check(reopened.book(html).folderId==0&&reopened.book(html).title.equals("Personal reading name"),"Moving to the homepage preserves the book identity and custom name");
            check(NotebookEbookStore.format("Book.AZW3").equals("azw3")&&NotebookEbookStore.format("Book.FB2.ZIP").equals("fbz")&&NotebookEbookStore.format("scan.DJV").equals("djvu"),"Named ebook variants route to their correct format readers");
            File notebook=new File(directory,"notebook-19");Files.createDirectory(notebook.toPath());
            fails(()->store.purge(html),"A stale Trash action cannot permanently delete an active ebook");
            check(store.state(html).equals(first)&&store.source(html).isFile(),"Refusing active-book deletion preserves reading state and the original");
            store.trash(html,true);store.purge(html);check(notebook.isDirectory()&&store.source(id).isFile(),"Removing one book cannot delete linked notebooks or other originals");
            System.out.println("NotebookEbookStore: "+assertions+" assertions passed.");
        }finally{delete(directory);}
    }
    private interface Action{void run()throws IOException;}
    private static void fails(Action action,String message)throws IOException{try{action.run();}catch(IOException expected){check(true,message);return;}throw new AssertionError(message);}
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    private static void delete(File file)throws IOException{File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);Files.deleteIfExists(file.toPath());}
}
