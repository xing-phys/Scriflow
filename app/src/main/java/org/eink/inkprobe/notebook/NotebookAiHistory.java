package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

/** One atomic file per saved session; its small checksummed header can be listed without decoding images. */
final class NotebookAiHistory {
    private static final int MAGIC=0x53414831,MAX_META=16384;
    static final class Entry {
        final String id,title,profile,error;
        final NotebookAiConfig config;
        final long created,updated;
        final int messages;
        final boolean draft;
        Entry(String id,String title,NotebookAiConfig config,String profile,long created,long updated,int messages,boolean draft,String error) {
            validateId(id); this.id=id; this.title=title; this.profile=profile; this.created=created; this.updated=updated;
            this.messages=messages; this.draft=draft; this.error=error;
            this.config=config==null?null:new NotebookAiConfig(config.provider,config.baseUrl,config.model,"");
        }
        Entry resolved(List<NotebookAiConfig> known) {
            if (config!=null) return this;
            for (NotebookAiConfig candidate:known) if (candidate.profile().equals(profile))
                return new Entry(id,title,candidate,profile,created,updated,messages,draft,error);
            return this;
        }
    }
    static final class Saved {
        final Entry entry; final NotebookAiConversation chat;
        Saved(Entry entry,NotebookAiConversation chat) { this.entry=entry; this.chat=chat; }
    }
    static String newId() { return UUID.randomUUID().toString(); }
    static String restoreId(String id,String previousSettings,String currentSettings) {
        if(previousSettings==null||!previousSettings.equals(currentSettings)||id==null)return null;
        try{validateId(id);return id;}catch(IllegalArgumentException error){return null;}
    }
    static void validateId(String id) {
        if (id==null||!UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException("Invalid saved chat ID");
    }
    private static File root(File directory) throws IOException {
        File root=new File(directory,"ai-chat-sessions");
        if (!root.isDirectory()&&!root.mkdirs()) throw new IOException("Cannot open AI history storage");
        return root;
    }
    private static File file(File directory,String id,String suffix) throws IOException {
        validateId(id); return new File(root(directory),id+suffix);
    }
    static boolean deleted(File directory,String id) throws IOException { return file(directory,id,".gone").exists(); }
    static String title(NotebookAiConversation chat) {
        String text=chat.messages.isEmpty()?chat.draft:chat.messages.get(0).content;
        text=text.replaceAll("\\s+"," ").trim();
        if (text.isEmpty()) text=chat.draftImages.isEmpty()?"New chat":chat.draftImages.get(0).name;
        int count=text.codePointCount(0,text.length());
        return count>72?text.substring(0,text.offsetByCodePoints(0,72))+"…":text;
    }
    static void save(File directory,String id,NotebookAiConfig config,long created,NotebookAiConversation chat) throws IOException {
        if (deleted(directory,id)) throw new IOException("This chat was deleted. Start a new chat.");
        if (chat.isEmpty()) { Files.deleteIfExists(file(directory,id,".chat").toPath()); return; }
        Entry entry=new Entry(id,title(chat),config,config.profile(),created,Math.max(created,System.currentTimeMillis()),
            chat.messages.size(),!chat.draft.isEmpty()||!chat.draftImages.isEmpty(),"");
        publish(directory,entry,NotebookAiConversation.snapshot(chat));
    }
    private static void publish(File directory,Entry entry,byte[] conversation) throws IOException {
        ByteArrayOutputStream metadata=new ByteArrayOutputStream();
        try (DataOutputStream out=new DataOutputStream(metadata)) {
            out.writeUTF(entry.id);out.writeUTF(entry.title);out.writeUTF(entry.profile);
            out.writeUTF(entry.config==null?"":entry.config.provider.name());
            out.writeUTF(entry.config==null?"":entry.config.baseUrl);out.writeUTF(entry.config==null?"":entry.config.model);
            out.writeLong(entry.created);out.writeLong(entry.updated);out.writeInt(entry.messages);out.writeBoolean(entry.draft);
        }
        byte[] data=metadata.toByteArray(); if (data.length>MAX_META) throw new IOException("AI history metadata exceeds its size limit");
        CRC32 crc=new CRC32();crc.update(data);ByteArrayOutputStream record=new ByteArrayOutputStream();
        try (DataOutputStream out=new DataOutputStream(record)) {
            out.writeInt(MAGIC);out.writeInt(data.length);out.writeLong(crc.getValue());out.write(data);out.write(conversation);
        }
        NotebookAiConversation.publish(file(directory,entry.id,".chat"),record.toByteArray());
    }
    private static Entry metadata(DataInputStream in,String id) throws IOException {
        if (in.readInt()!=MAGIC) throw new IOException("Unrecognized saved chat");
        int size=in.readInt();long expected=in.readLong();
        if (size<1||size>MAX_META) throw new IOException("Invalid saved chat header");
        byte[] data=new byte[size];in.readFully(data);CRC32 crc=new CRC32();crc.update(data);
        if (crc.getValue()!=expected) throw new IOException("Saved chat header is damaged");
        try (DataInputStream meta=new DataInputStream(new ByteArrayInputStream(data))) {
            String storedId=meta.readUTF(),title=meta.readUTF(),profile=meta.readUTF(),provider=meta.readUTF(),url=meta.readUTF(),model=meta.readUTF();
            long created=meta.readLong(),updated=meta.readLong();int count=meta.readInt();boolean draft=meta.readBoolean();
            if (!id.equals(storedId)||title.length()>300||!profile.matches("[a-f0-9]{64}")||created<1||updated<created
                    ||count<0||count>NotebookAiConversation.MAX_MESSAGES||meta.read()!=-1) throw new IOException("Invalid saved chat metadata");
            NotebookAiConfig config=null;
            if (!provider.isEmpty()) {
                config=new NotebookAiConfig(NotebookAiConfig.Provider.valueOf(provider),url,model,"");
                new NotebookAiConfig(config.provider,url,model,"local-validation").validate();
                if (!config.profile().equals(profile)) throw new IOException("Saved chat destination does not match its metadata");
            }
            return new Entry(id,title,config,profile,created,updated,count,draft,"");
        } catch (IllegalArgumentException error) { throw new IOException("Invalid saved chat metadata",error); }
    }
    static Entry entry(File directory,String id) throws IOException {
        if(deleted(directory,id))throw new IOException("This saved chat has been deleted");
        File file=file(directory,id,".chat");if(!file.exists())return null;
        try(DataInputStream in=new DataInputStream(new FileInputStream(file))){return metadata(in,id);}
    }
    static Saved read(File directory,String id) throws IOException {
        if (deleted(directory,id)) throw new IOException("This saved chat has been deleted");
        File file=file(directory,id,".chat");if (!file.exists()) return null;
        if (file.length()>NotebookAiConversation.MAX_SNAPSHOT_BYTES+MAX_META+16) throw new IOException("Saved chat exceeds its size limit");
        byte[] data=Files.readAllBytes(file.toPath());
        try (ByteArrayInputStream bytes=new ByteArrayInputStream(data);DataInputStream in=new DataInputStream(bytes)) {
            Entry entry=metadata(in,id);byte[] conversation=new byte[bytes.available()];in.readFully(conversation);
            return new Saved(entry,NotebookAiConversation.fromSnapshot(conversation));
        }
    }
    static List<Entry> list(File directory,List<NotebookAiConfig> known) throws IOException {
        migrate(directory,known);List<Entry> entries=new ArrayList<>();File[] files=root(directory).listFiles();
        if (files==null) throw new IOException("Cannot list saved chats");
        for (File file:files) {
            String name=file.getName();if (!name.endsWith(".chat")) continue;
            String id=name.substring(0,name.length()-5);try {validateId(id);}catch(IllegalArgumentException error){continue;}
            if (deleted(directory,id)) continue;
            try (DataInputStream in=new DataInputStream(new FileInputStream(file))) { entries.add(metadata(in,id).resolved(known)); }
            catch (IOException error) { entries.add(problem(id,file,"Unreadable saved chat")); }
        }
        for (File legacy:legacyFiles(directory)) {
            String profile=legacyProfile(legacy),id=legacyId(legacy);
            if (!new File(root(directory),"legacy-"+id+".done").exists()&&!deleted(directory,id))
                entries.add(problem(id,legacy,"Unreadable earlier chat"));
        }
        for (File marker:files) if (marker.getName().endsWith(".gone")) {
            String id=marker.getName().substring(0,marker.getName().length()-5);
            try {validateId(id);cleanupDeleted(directory,id);}catch(IllegalArgumentException ignored){}
            catch (IOException error) {entries.add(problem(id,marker,"Deletion pending; tap Delete to retry"));}
        }
        entries.sort(Comparator.comparingLong((Entry entry)->entry.updated).reversed().thenComparing(entry->entry.id));
        return entries;
    }
    private static Entry problem(String id,File file,String message) {
        return new Entry(id,message,null,"",Math.max(1,file.lastModified()),Math.max(1,file.lastModified()),0,false,message);
    }
    static void delete(File directory,String id) throws IOException {
        NotebookAiConversation.publish(file(directory,id,".gone"),new byte[]{1});
        cleanupDeleted(directory,id);
    }
    private static void cleanupDeleted(File directory,String id) throws IOException {
        Files.deleteIfExists(file(directory,id,".chat").toPath());
        Files.deleteIfExists(file(directory,id,".chat.tmp").toPath());
        for (File legacy:legacyFiles(directory)) if (legacyId(legacy).equals(id)) Files.deleteIfExists(legacy.toPath());
    }
    private static File[] legacyFiles(File directory) {
        File[] files=directory.listFiles((parent,name)->name.matches("ai-chat-[a-f0-9]{64}\\.bin"));
        return files==null?new File[0]:files;
    }
    private static String legacyProfile(File file) { return file.getName().substring(8,72); }
    private static String legacyId(File source) {
        try {
            java.security.MessageDigest hash=java.security.MessageDigest.getInstance("SHA-256");
            hash.update(legacyProfile(source).getBytes(StandardCharsets.UTF_8));
            if(source.length()<=NotebookAiConversation.MAX_SNAPSHOT_BYTES) {
                try {hash.update(Files.readAllBytes(source.toPath()));}
                catch(IOException error){hash.update(("unreadable:"+source.length()+":"+source.lastModified()).getBytes(StandardCharsets.UTF_8));}
            } else hash.update(("oversized:"+source.length()+":"+source.lastModified()).getBytes(StandardCharsets.UTF_8));
            return UUID.nameUUIDFromBytes(hash.digest()).toString();
        } catch(java.security.NoSuchAlgorithmException error){throw new AssertionError(error);}
    }
    static void migrate(File directory,List<NotebookAiConfig> known) throws IOException {
        for (File source:legacyFiles(directory)) {
            String profile=legacyProfile(source),id=legacyId(source);File done=new File(root(directory),"legacy-"+id+".done");
            if (deleted(directory,id)||done.exists()) {try {Files.deleteIfExists(source.toPath());}catch(IOException ignored){}continue;}
            if (!file(directory,id,".chat").exists()) {
                NotebookAiConversation chat;
                try {chat=NotebookAiConversation.read(directory,profile);}catch(IOException error){continue;}
                if (!chat.isEmpty()) {
                    long date=Math.max(1,source.lastModified());
                    Entry entry=new Entry(id,title(chat),null,profile,date,date,chat.messages.size(),!chat.draft.isEmpty()||!chat.draftImages.isEmpty(),"").resolved(known);
                    publish(directory,entry,NotebookAiConversation.snapshot(chat));
                }
            }
            NotebookAiConversation.publish(done,new byte[]{1});
            try {Files.deleteIfExists(source.toPath());}catch(IOException ignored){/* Done marker prevents duplicate import. */}
        }
    }
}
