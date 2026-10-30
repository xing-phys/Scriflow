package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.CRC32;

/** Private, checksummed typing recovery, independent of a not-yet-completed render or page edit. */
final class NotebookMarkdownDraft {
    private static final int MAGIC = 0x4d444631, MAX_FILE = 512 * 1024;
    final UUID token, pageId, figureId;
    final int book, page;
    final float x, y, width, height, angle;
    final boolean inserting;
    final String baseSource, source;

    NotebookMarkdownDraft(UUID token, int book, int page, UUID pageId, UUID figureId, boolean inserting,
            float x, float y, float width, float height, float angle, String baseSource, String source) {
        if (token == null || book < 1 || page < 1 || pageId == null || figureId == null || baseSource == null || source == null
                || !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)
                || !Float.isFinite(angle) || width <= 0 || height <= 0 || width > 32768 || height > 32768)
            throw new IllegalArgumentException("Invalid Markdown draft");
        text(baseSource); text(source);
        this.token=token;this.book=book;this.page=page;this.pageId=pageId;this.figureId=figureId;this.inserting=inserting;
        this.x=x;this.y=y;this.width=width;this.height=height;this.angle=angle;this.baseSource=baseSource;this.source=source;
    }
    NotebookMarkdownDraft withSource(String value) {
        return new NotebookMarkdownDraft(token,book,page,pageId,figureId,inserting,x,y,width,height,angle,baseSource,value);
    }
    private static byte[] text(CharSequence source) {
        try {
            ByteBuffer bytes=StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(source));
            if(bytes.remaining()>NotebookMarkdown.MAX_SOURCE_BYTES)throw new IllegalArgumentException("Markdown source exceeds 128 KiB");
            byte[] result=new byte[bytes.remaining()];bytes.get(result);return result;
        } catch(CharacterCodingException e){throw new IllegalArgumentException("Invalid Markdown text",e);}
    }
    private static void string(DataOutputStream out,String text)throws IOException{byte[] b=text((CharSequence)text);out.writeInt(b.length);out.write(b);}
    private static String string(DataInputStream in)throws IOException{
        int n=in.readInt();if(n<0||n>NotebookMarkdown.MAX_SOURCE_BYTES||n>in.available())throw new IOException("Invalid Markdown draft text");
        byte[] b=new byte[n];in.readFully(b);
        try{return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();}
        catch(CharacterCodingException e){throw new IOException("Invalid Markdown draft text",e);}
    }
    private static void id(DataOutputStream out,UUID id)throws IOException{out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());}
    private static UUID id(DataInputStream in)throws IOException{return new UUID(in.readLong(),in.readLong());}
    private static int crc(byte[] bytes){CRC32 crc=new CRC32();crc.update(bytes);return(int)crc.getValue();}
    private static File file(File root,String suffix){return new File(root,"markdown-draft.bin"+suffix);}

    byte[] snapshot() throws IOException {
        NotebookMarkdownDraft draft = this;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        id(out,draft.token);out.writeInt(draft.book);out.writeInt(draft.page);id(out,draft.pageId);id(out,draft.figureId);
        out.writeBoolean(draft.inserting);out.writeFloat(draft.x);out.writeFloat(draft.y);out.writeFloat(draft.width);out.writeFloat(draft.height);out.writeFloat(draft.angle);
        string(out,draft.baseSource);string(out,draft.source);out.flush();return bytes.toByteArray();
    }
    static void write(File root,NotebookMarkdownDraft draft)throws IOException{
        byte[] payload=draft.snapshot();
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Cannot create Markdown draft directory");
        File document=file(root,""),temporary=file(root,".tmp"),backup=file(root,".bak");
        // Keep drafts from other editing sessions until they too are recovered and saved.
        Set<UUID> retained=new HashSet<>();
        for(File previous:new File[]{document,backup}) if(previous.exists()) {
            NotebookMarkdownDraft older;
            try{older=readFile(previous);}catch(IOException damaged){continue;}
            if(!older.token.equals(draft.token)&&retained.add(older.token)) {
                File archive=new File(root,"markdown-drafts");
                if(!archive.isDirectory()&&!archive.mkdirs())throw new IOException("Cannot retain the earlier Markdown draft");
                File kept=new File(archive,older.token+".bin");
                File staging=new File(archive,older.token+".tmp");
                try(FileInputStream input=new FileInputStream(previous);FileOutputStream output=new FileOutputStream(staging)) {
                    byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)output.write(buffer,0,n);output.getFD().sync();
                }
                if(!staging.renameTo(kept))throw new IOException("Cannot publish the earlier Markdown draft");
            }
        }
        try(FileOutputStream stream=new FileOutputStream(temporary)){
            DataOutputStream data=new DataOutputStream(stream);data.writeInt(MAGIC);data.writeInt(payload.length);data.writeInt(~payload.length);
            data.write(payload);data.writeInt(crc(payload));data.flush();stream.getFD().sync();
        }
        if(document.exists()){
            if(backup.exists()&&!backup.delete())throw new IOException("Cannot retain Markdown draft predecessor");
            if(!document.renameTo(backup))throw new IOException("Cannot retain Markdown draft predecessor");
        }
        if(!temporary.renameTo(document)){
            if(!document.exists()&&backup.exists())backup.renameTo(document);
            throw new IOException("Cannot save Markdown source draft");
        }
    }
    static NotebookMarkdownDraft read(File root)throws IOException{return read(root,0);}
    static NotebookMarkdownDraft read(File root,int book)throws IOException{
        IOException problem=null;
        for(String suffix:new String[]{"",".tmp",".bak"}){
            File source=file(root,suffix);if(!source.exists())continue;
            try{NotebookMarkdownDraft draft=readFile(source);if(book==0||draft.book==book)return draft;}catch(IOException e){problem=e;}
        }
        File[] archived=new File(root,"markdown-drafts").listFiles((dir,name)->name.endsWith(".bin"));
        if(archived!=null){Arrays.sort(archived,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
            for(File source:archived)try{NotebookMarkdownDraft draft=readFile(source);if(book==0||draft.book==book)return draft;}catch(IOException e){problem=e;}}
        if(problem!=null)throw new IOException("Cannot read Markdown draft; recovery files retained",problem);
        return null;
    }
    private static NotebookMarkdownDraft readFile(File file)throws IOException{
        if(file.length()<16||file.length()>MAX_FILE)throw new IOException("Invalid Markdown draft size");
        try(DataInputStream in=new DataInputStream(new FileInputStream(file))){
            if(in.readInt()!=MAGIC)throw new IOException("Unknown Markdown draft");int n=in.readInt();
            if(in.readInt()!=~n||n<0||n!=file.length()-16)throw new IOException("Invalid Markdown draft framing");
            byte[] payload=new byte[n];in.readFully(payload);if(in.readInt()!=crc(payload)||in.read()!=-1)throw new IOException("Invalid Markdown draft checksum");
            return fromSnapshot(payload);
        }
    }
    static NotebookMarkdownDraft fromSnapshot(byte[] payload)throws IOException {
        if(payload==null||payload.length>MAX_FILE)throw new IOException("Invalid Markdown draft size");
        DataInputStream data=new DataInputStream(new ByteArrayInputStream(payload));
        try{
            NotebookMarkdownDraft draft=new NotebookMarkdownDraft(id(data),data.readInt(),data.readInt(),id(data),id(data),data.readBoolean(),
                data.readFloat(),data.readFloat(),data.readFloat(),data.readFloat(),data.readFloat(),string(data),string(data));
            if(data.available()!=0)throw new IOException("Unexpected Markdown draft data");return draft;
        }catch(IllegalArgumentException e){throw new IOException("Invalid Markdown draft",e);}
    }
    static void clear(File root,UUID token)throws IOException{
        for(String suffix:new String[]{"",".tmp",".bak"}){
            File f=file(root,suffix);if(!f.exists())continue;
            final NotebookMarkdownDraft draft;
            try{draft=readFile(f);}catch(IOException damaged){continue;}
            if(draft.token.equals(token)&&!f.delete())throw new IOException("Cannot retire the saved Markdown draft");
        }
        File archived=new File(new File(root,"markdown-drafts"),token+".bin");
        if(archived.exists()&&readFile(archived).token.equals(token)&&!archived.delete())throw new IOException("Cannot retire the saved Markdown draft");
    }
}
