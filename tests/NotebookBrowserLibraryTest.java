package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Durable user links and deletion boundaries, plus tab/search behavior without Android stubs. */
public final class NotebookBrowserLibraryTest {
    private static int checks;
    private static final List<File> roots=new ArrayList<>();
    interface Task {void run() throws Exception;}
    public static void main(String[] args)throws Exception{
        try{bookmarks();history();recovery();paths();tabs();search();concurrent();System.out.println("NotebookBrowserLibrary: "+checks+" assertions passed (durable collections/pins/history, deletion isolation, recovery, tab persistence and search).");}
        finally{for(File root:roots)remove(root);}
    }
    private static void bookmarks()throws Exception{
        File root=root();NotebookBrowserLibrary library=new NotebookBrowserLibrary(root);check(library.read().links.isEmpty(),"read makes no default links");
        NotebookBrowserLibrary.Snapshot first=library.initialize();check(first.links.size()==2&&first.links.stream().allMatch(link->link.pinned),"a new home page has useful offline-listed shortcuts");
        check(library.initialize().links.size()==2,"initialization is idempotent");library.folder(null,"Quantum information");NotebookBrowserLibrary.Folder folder=library.read().folders.get(0);
        library.put(null,"物理 · Math","https://example.org/math",folder.id,true);NotebookBrowserLibrary.Link link=library.read().find("https://example.org/math");
        check(link!=null&&link.pinned&&link.folder.equals(folder.id),"bookmark carries its folder and pin state");
        NotebookBrowserLibrary cold=new NotebookBrowserLibrary(root);check(cold.read().find(link.url).title.equals(link.title),"Unicode bookmark survives a cold read");
        library.put(link.id,"Updated",link.url,"",false);check(!cold.read().find(link.url).pinned&&cold.read().find(link.url).id.equals(link.id),"editing preserves identity and updates pin state");
        library.pin(link.id,true);library.movePin(link.id,-1);check(library.read().links.get(1).id.equals(link.id),"pinned order changes independently of folder names");
        library.folder(folder.id,"QEC");check(library.read().folderTitle(folder.id).equals("QEC"),"collection rename retains identity");
        io(()->library.folder(null,"qec"));library.put(null,"Other","https://example.org/math","",false);check(library.read().find("https://example.org/math").id.equals(link.id)&&library.read().links.size()==3,"saving an existing URL updates its bookmark without duplication");
        library.put(link.id,"Moved",link.url,folder.id,true);library.removeFolder(folder.id);link=library.read().find(link.url);check(link.folder.isEmpty()&&link.pinned,"removing a collection keeps its bookmarks and pins");
        final String id=link.id;io(()->library.put(id,"Unsafe","file:///private","",true));io(()->library.put(null,"Unsafe","http://example.org","",true));io(()->library.put(null,"Bad folder","https://example.org/other","missing",false));
        for(NotebookBrowserLibrary.Link value:new ArrayList<>(library.read().links))library.remove(value.id);
        check(library.initialize().links.isEmpty(),"deleted starter pins never reappear");
    }
    private static void history()throws Exception{
        File root=root();NotebookBrowserLibrary library=new NotebookBrowserLibrary(root);library.initialize();
        byte[] note={4,2,9};Files.write(new File(root,"keep.nbk").toPath(),note);File offline=new File(root,"websites/keep");check(offline.mkdirs(),"create independent offline copy");Files.write(new File(offline,"page.html").toPath(),note);
        library.visit(" First\n page ","https://one.example/path");library.visit("Second","https://two.example/path");library.visit("Updated","https://one.example/path");
        check(library.read().visits.size()==2&&library.read().visits.get(0).title.equals("Updated"),"history stores the latest visit per URL");
        library.visit("Forbidden","file:///private");check(library.read().visits.size()==2,"local files never enter online history");
        library.removeVisit("https://one.example/path");check(library.read().visits.size()==1,"single-entry deletion leaves other visits");
        library.clearHistory();check(library.read().visits.isEmpty()&&library.read().links.size()==2,"history deletion preserves bookmarks");
        check(Arrays.equals(Files.readAllBytes(new File(root,"keep.nbk").toPath()),note)&&Arrays.equals(Files.readAllBytes(new File(offline,"page.html").toPath()),note),"browser history operations preserve notebooks and offline files exactly");
        Files.write(new File(root,"browser-library.nbr").toPath(),new byte[]{1});check(library.read().visits.isEmpty(),"recovery cannot resurrect successfully cleared history");
    }
    private static void recovery()throws Exception{
        File root=root();NotebookBrowserLibrary library=new NotebookBrowserLibrary(root);library.initialize();library.put(null,"Physics","https://example.org/physics","",false);
        Files.write(new File(root,"browser-library.nbr").toPath(),new byte[]{9});check(library.read().links.size()==2,"damaged primary recovers an earlier complete generation");
        library.put(null,"Recovered","https://example.org/recovered","",true);check(library.read().find("https://example.org/recovered")!=null,"recovered state can be updated atomically");
        Files.write(new File(root,"browser-library.nbr").toPath(),new byte[]{8});Files.write(new File(root,"browser-library.nbr.bak").toPath(),new byte[]{7});io(()->library.initialize());check(Arrays.equals(Files.readAllBytes(new File(root,"browser-library.nbr").toPath()),new byte[]{8}),"unreadable generations are not silently overwritten");
    }
    private static void paths()throws Exception{
        for(String name:new String[]{"browser-library.nbr","browser-library.nbr.bak","browser-library.nbr.tmp"}){
            File root=root(),outside=root();File keep=new File(outside,"keep");Files.write(keep.toPath(),new byte[]{6});Files.createSymbolicLink(new File(root,name).toPath(),keep.toPath());NotebookBrowserLibrary library=new NotebookBrowserLibrary(root);io(()->library.initialize());check(Arrays.equals(Files.readAllBytes(keep.toPath()),new byte[]{6}),"browser catalog never follows a storage symlink");
        }
    }
    private static void tabs(){
        NotebookBrowserTabs tabs=new NotebookBrowserTabs();NotebookBrowserTabs.Tab first=tabs.current();first.url="https://example.org/one";first.title="One";first.home=false;
        NotebookBrowserTabs.Tab second=tabs.add();second.url="https://example.org/two";second.title="Two";second.home=false;tabs.select(first.id);tabs.close(second.id);check(tabs.current()==first,"closing a background tab preserves selection");
        for(int i=1;i<NotebookBrowserTabs.MAX;i++)tabs.add();try{tabs.add();throw new AssertionError("unbounded tabs");}catch(IllegalStateException expected){checks++;}
        String stored=tabs.encode();NotebookBrowserTabs restored=new NotebookBrowserTabs();check(restored.restore(stored)&&restored.items.size()==12&&restored.current().id.equals(tabs.current().id),"tab destinations and selection survive a cold restore");
        check(!restored.restore("garbage")&&!restored.restore(stored+"x")&&restored.items.size()==12,"damaged tab data cannot replace current tabs");
        restored.clear();check(restored.items.stream().allMatch(tab->tab.home&&tab.url.isEmpty()),"data clearing removes restorable tab addresses");
        for(NotebookBrowserTabs.Tab tab:new ArrayList<>(restored.items))restored.close(tab.id);check(restored.items.size()==1&&restored.current().home,"closing the last tab yields a usable start page");
    }
    private static void search(){
        check(NotebookBrowserAddress.destination("example.org/path",0).equals("https://example.org/path"),"address still opens directly");
        check(NotebookBrowserAddress.destination("quantum error correction",0).equals("https://duckduckgo.com/?q=quantum+error+correction"),"plain text uses selected search provider");
        check(NotebookBrowserAddress.destination("物理",1).startsWith("https://www.google.com/search?q=%"),"Unicode search is escaped");
        check(NotebookBrowserAddress.destination("cat & dog",2).endsWith("cat+%26+dog"),"search punctuation cannot become another parameter");
        for(String value:new String[]{"http://example.org","file:///private","javascript:alert(1)"," ","test\nsearch"})try{NotebookBrowserAddress.destination(value,0);throw new AssertionError("unsafe destination "+value);}catch(IllegalArgumentException expected){checks++;}
    }
    private static void concurrent()throws Exception{
        File root=root();NotebookBrowserLibrary library=new NotebookBrowserLibrary(root);library.initialize();List<Throwable> errors=Collections.synchronizedList(new ArrayList<>());Thread[] workers=new Thread[3];
        for(int i=0;i<workers.length;i++){final int worker=i;workers[i]=new Thread(()->{try{for(int n=0;n<12;n++)new NotebookBrowserLibrary(root).visit("Page "+worker+"/"+n,"https://example.org/"+worker+"/"+n);}catch(Throwable error){errors.add(error);}});workers[i].start();}
        for(Thread worker:workers)worker.join();check(errors.isEmpty()&&library.read().visits.size()==36&&library.read().links.size()==2,"independent instances serialize updates without losing bookmarks or visits");
    }
    private static File root()throws Exception{File root=Files.createTempDirectory("browser-library-").toFile();roots.add(root);return root;}
    private static void io(Task task)throws Exception{try{task.run();throw new AssertionError("Expected failure");}catch(IOException|IllegalArgumentException expected){checks++;}}
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void remove(File file)throws Exception{if(!Files.isSymbolicLink(file.toPath())&&file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)remove(child);}Files.deleteIfExists(file.toPath());}
}
