package org.eink.inkprobe.notebook;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** App-owned links and recent destinations, independent of engine cookies and offline snapshots. */
final class NotebookBrowserLibrary {
    private static final int MAGIC = 0x4e425231, MAX_LINKS = 4000, MAX_FOLDERS = 200, MAX_VISITS = 1000, MAX_PINS = 32;
    private static final Object LOCK = new Object();
    private final File file;
    static final class Link {
        final String id, title, url, folder;
        final boolean pinned;
        Link(String id, String title, String url, String folder, boolean pinned) {
            this.id=id; this.title=title; this.url=url; this.folder=folder; this.pinned=pinned;
        }
    }
    static final class Folder {
        final String id, title;
        Folder(String id, String title) { this.id=id; this.title=title; }
    }
    static final class Visit {
        final String title, url;
        final long time;
        Visit(String title, String url, long time) { this.title=title; this.url=url; this.time=time; }
    }
    static final class Snapshot {
        final List<Link> links = new ArrayList<>();
        final List<Folder> folders = new ArrayList<>();
        final List<Visit> visits = new ArrayList<>();
        boolean initialized;
        Link find(String url) { for (Link link:links) if (link.url.equals(url)) return link; return null; }
        String folderTitle(String id) { for (Folder folder:folders) if (folder.id.equals(id)) return folder.title; return "Unsorted"; }
    }
    NotebookBrowserLibrary(File files) throws IOException { file=new File(files.getCanonicalFile(), "browser-library.nbr"); }
    Snapshot read() throws IOException { synchronized(LOCK) { return load(); } }
    Snapshot initialize() throws IOException {
        synchronized(LOCK) {
            Snapshot state=load();
            if (!state.initialized) {
                state.initialized=true;
                state.links.add(new Link(UUID.randomUUID().toString(), "Wikipedia", "https://en.wikipedia.org/", "", true));
                state.links.add(new Link(UUID.randomUUID().toString(), "GitHub", "https://github.com/", "", true));
                write(state);
            }
            return state;
        }
    }
    void put(String id, String title, String url, String folder, boolean pinned) throws IOException {
        synchronized(LOCK) {
            Snapshot state=load(); String normalized=NotebookBrowserAddress.normalize(url); title=name(title, normalized);
            String target=folder==null?"":folder;
            if (!target.isEmpty() && state.folders.stream().noneMatch(value->value.id.equals(target))) throw new IOException("Choose an existing collection.");
            Link existing=id==null?state.find(normalized):state.links.stream().filter(value->value.id.equals(id)).findFirst().orElse(null);
            if (id!=null && existing==null) throw new IOException("This bookmark was removed. Please reopen Bookmarks.");
            for (Link link:state.links) if (link!=existing && link.url.equals(normalized)) throw new IOException("That website is already bookmarked.");
            if (existing==null && state.links.size()>=MAX_LINKS) throw new IOException("The bookmark limit has been reached.");
            if(pinned&&(existing==null||!existing.pinned)&&state.links.stream().filter(link->link.pinned).count()>=MAX_PINS)throw new IOException("Up to 32 shortcuts can be pinned on the start page.");
            Link replacement=new Link(existing==null?UUID.randomUUID().toString():existing.id,title,normalized,target,pinned);
            if (existing==null) state.links.add(replacement); else state.links.set(state.links.indexOf(existing), replacement);
            state.initialized=true; write(state);
        }
    }
    void remove(String id) throws IOException { synchronized(LOCK) { Snapshot state=load(); state.links.removeIf(link->link.id.equals(id)); write(state); } }
    void pin(String id, boolean pinned) throws IOException {
        synchronized(LOCK) {
            Snapshot state=load();
            for (int i=0;i<state.links.size();i++) { Link link=state.links.get(i); if (link.id.equals(id)) { if(pinned&&!link.pinned&&state.links.stream().filter(value->value.pinned).count()>=MAX_PINS)throw new IOException("Up to 32 shortcuts can be pinned on the start page."); state.links.set(i,new Link(link.id,link.title,link.url,link.folder,pinned)); write(state); return; } }
            throw new IOException("This bookmark was removed.");
        }
    }
    void movePin(String id, int direction) throws IOException {
        synchronized(LOCK) {
            Snapshot state=load(); int at=-1;
            for (int i=0;i<state.links.size();i++) if (state.links.get(i).id.equals(id) && state.links.get(i).pinned) at=i;
            if (at<0) throw new IOException("This shortcut was removed.");
            int next=at+(direction<0?-1:1);
            while (next>=0 && next<state.links.size() && !state.links.get(next).pinned) next+=direction<0?-1:1;
            if (next>=0 && next<state.links.size()) { Collections.swap(state.links,at,next); write(state); }
        }
    }
    void folder(String id, String title) throws IOException {
        synchronized(LOCK) {
            Snapshot state=load(); title=name(title, ""); if (title.isEmpty()) throw new IOException("Enter a collection name.");
            for (Folder value:state.folders) if (!value.id.equals(id) && value.title.equalsIgnoreCase(title)) throw new IOException("A collection already has this name.");
            if (id==null) { if(state.folders.size()>=MAX_FOLDERS) throw new IOException("The collection limit has been reached."); state.folders.add(new Folder(UUID.randomUUID().toString(),title)); }
            else { int at=-1; for(int i=0;i<state.folders.size();i++) if(state.folders.get(i).id.equals(id)) at=i; if(at<0)throw new IOException("Collection was removed."); state.folders.set(at,new Folder(id,title)); }
            write(state);
        }
    }
    void removeFolder(String id) throws IOException {
        synchronized(LOCK) {
            Snapshot state=load(); state.folders.removeIf(folder->folder.id.equals(id));
            for(int i=0;i<state.links.size();i++) { Link link=state.links.get(i); if(link.folder.equals(id))state.links.set(i,new Link(link.id,link.title,link.url,"",link.pinned)); }
            write(state);
        }
    }
    void visit(String title, String url) throws IOException {
        if(!NotebookBrowserAddress.isWebUrl(url))return;
        synchronized(LOCK) {
            Snapshot state=load(); String normalized=NotebookBrowserAddress.normalize(url);
            state.visits.removeIf(visit->visit.url.equals(normalized));
            state.visits.add(0,new Visit(name(title==null?null:title.replaceAll("\\s+"," "),normalized),normalized,System.currentTimeMillis()));
            while(state.visits.size()>MAX_VISITS)state.visits.remove(state.visits.size()-1);
            write(state);
        }
    }
    void removeVisit(String url) throws IOException { synchronized(LOCK) { Snapshot state=load(); state.visits.removeIf(value->value.url.equals(url)); write(state); scrubRecovery(); } }
    void clearHistory() throws IOException { synchronized(LOCK) { Snapshot state=load(); state.visits.clear(); write(state); scrubRecovery(); } }
    private void scrubRecovery() throws IOException {
        File recovery=new File(file+".bak");checked(recovery);Files.copy(file.toPath(),recovery.toPath(),StandardCopyOption.REPLACE_EXISTING);try(FileOutputStream sync=new FileOutputStream(recovery,true)){sync.getFD().sync();}
    }
    static String host(String url) { try { return URI.create(NotebookBrowserAddress.normalize(url)).getHost(); } catch(Exception error) { return ""; } }
    private Snapshot load() throws IOException {
        checked(file); File backup=new File(file+".bak"); checked(backup);
        if(!file.exists() && !backup.exists())return new Snapshot();
        try { return parse(file); } catch(IOException error) { if(!backup.exists())throw error; return parse(backup); }
    }
    private Snapshot parse(File source) throws IOException {
        if(!source.isFile() || source.length()>4*1024*1024)throw new IOException("Browser bookmarks could not be read.");
        try(DataInputStream input=new DataInputStream(new FileInputStream(source))) {
            if(input.readInt()!=MAGIC)throw new IOException("Unknown browser bookmark format.");
            Snapshot state=new Snapshot(); state.initialized=input.readBoolean(); Set<String> ids=new HashSet<>(), urls=new HashSet<>();
            for(int n=count(input,MAX_FOLDERS);n>0;n--) { String id=input.readUTF(),title=input.readUTF(); validId(id,ids); if(!name(title,"").equals(title)||title.isEmpty())throw new IOException("Invalid collection."); state.folders.add(new Folder(id,title)); }
            for(int n=count(input,MAX_LINKS);n>0;n--) {
                String id=input.readUTF(),title=input.readUTF(),url=input.readUTF(),folder=input.readUTF(); boolean pinned=input.readBoolean(); validId(id,ids);
                if(!NotebookBrowserAddress.isWebUrl(url)||!urls.add(url)||!name(title,url).equals(title)||!folder.isEmpty()&&state.folders.stream().noneMatch(value->value.id.equals(folder)))throw new IOException("Invalid bookmark.");
                state.links.add(new Link(id,title,url,folder,pinned));
            }
            urls.clear();
            for(int n=count(input,MAX_VISITS);n>0;n--) { String title=input.readUTF(),url=input.readUTF(); long time=input.readLong(); if(!NotebookBrowserAddress.isWebUrl(url)||!urls.add(url)||time<=0||!name(title,url).equals(title))throw new IOException("Invalid browser history."); state.visits.add(new Visit(title,url,time)); }
            if(input.read()!=-1)throw new IOException("Browser bookmark data is damaged."); return state;
        } catch(IllegalArgumentException error) { throw new IOException("Browser bookmark data is damaged.",error); }
    }
    private void write(Snapshot state) throws IOException {
        checked(file); File temporary=new File(file+".tmp"),backup=new File(file+".bak"); checked(temporary); checked(backup);
        try(FileOutputStream bytes=new FileOutputStream(temporary); DataOutputStream output=new DataOutputStream(bytes)) {
            output.writeInt(MAGIC); output.writeBoolean(state.initialized); output.writeInt(state.folders.size());
            for(Folder folder:state.folders) { output.writeUTF(folder.id); output.writeUTF(folder.title); }
            output.writeInt(state.links.size());
            for(Link link:state.links) { output.writeUTF(link.id); output.writeUTF(link.title); output.writeUTF(link.url); output.writeUTF(link.folder); output.writeBoolean(link.pinned); }
            output.writeInt(state.visits.size()); for(Visit visit:state.visits) { output.writeUTF(visit.title); output.writeUTF(visit.url); output.writeLong(visit.time); }
            output.flush(); bytes.getFD().sync();
        }
        if(temporary.length()>4*1024*1024)throw new IOException("Browser bookmarks are too large. Shorten website addresses or remove unused items.");
        // A damaged primary is never copied over a good recovery generation.
        if(file.exists()) { try { parse(file); Files.copy(file.toPath(),backup.toPath(),StandardCopyOption.REPLACE_EXISTING); try(FileOutputStream sync=new FileOutputStream(backup,true)){sync.getFD().sync();} } catch(IOException error) { if(!backup.isFile())throw error; } }
        Files.move(temporary.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    private static void checked(File file) throws IOException { if(Files.isSymbolicLink(file.toPath())||!file.equals(file.getCanonicalFile()))throw new IOException("Invalid browser data path."); }
    private static int count(DataInputStream input,int maximum) throws IOException { int count=input.readInt(); if(count<0||count>maximum)throw new IOException("Invalid browser data count."); return count; }
    private static void validId(String id,Set<String> ids) throws IOException { if(!id.matches("[a-f0-9-]{36}")||!ids.add(id))throw new IOException("Invalid browser item."); }
    private static String name(String value,String fallback) throws IOException {
        String result=value==null?"":value.trim(); if(result.isEmpty())result=fallback;
        if(result.length()>1024)result=result.substring(0,1024);
        for(int i=0;i<result.length();i++)if(Character.isISOControl(result.charAt(i)))throw new IOException("Use a single-line name.");
        return result;
    }
}
