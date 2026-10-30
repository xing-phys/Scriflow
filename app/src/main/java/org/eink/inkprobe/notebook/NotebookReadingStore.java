package org.eink.inkprobe.notebook;

import java.io.*;
import java.util.*;
import java.util.zip.CRC32;

/** Small atomic view-preference snapshots, separate from notebook/backup page formats. */
final class NotebookReadingStore {
    private static final int MAGIC=0x4e525631,VERSION=1,MAX_BYTES=1024*1024;
    private final File directory,document,temporary,backup;
    NotebookReadingStore(File root,int book) {
        if(book<1)throw new IllegalArgumentException("Invalid notebook");
        directory=new File(root,"reading");document=new File(directory,book+".view");temporary=new File(directory,book+".view.tmp");backup=new File(directory,book+".view.bak");
    }
    NotebookReadingSettings read()throws IOException {
        IOException error=null;
        for(File file:new File[]{document,temporary,backup})if(file.exists())try{return read(file);}catch(IOException e){error=e;}
        if(error!=null)throw new IOException("Cannot read notebook view settings; recovery files retained",error);
        return NotebookReadingSettings.DEFAULT;
    }
    void write(NotebookReadingSettings settings)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeByte(settings.crop.ordinal());out.writeBoolean(settings.singlePage);out.writeFloat(settings.contrast);
        box(out,settings.odd);box(out,settings.even);out.writeInt(settings.pages.size());
        for(Map.Entry<UUID,NotebookCropBox> entry:settings.pages.entrySet()){out.writeLong(entry.getKey().getMostSignificantBits());out.writeLong(entry.getKey().getLeastSignificantBits());box(out,entry.getValue());}
        out.flush();byte[] data=bytes.toByteArray();
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Cannot create notebook view settings");
        try(FileOutputStream stream=new FileOutputStream(temporary)){
            DataOutputStream frame=new DataOutputStream(stream);frame.writeInt(MAGIC);frame.writeInt(VERSION);frame.writeInt(data.length);frame.writeInt(~data.length);frame.write(data);frame.writeInt(crc(data));frame.flush();stream.getFD().sync();
        }
        if(document.exists()){
            if(backup.exists()&&!backup.delete())throw new IOException("Cannot retain view settings predecessor");
            if(!document.renameTo(backup))throw new IOException("Cannot retain view settings predecessor");
        }
        if(!temporary.renameTo(document)){
            if(!document.exists()&&backup.exists())backup.renameTo(document);
            throw new IOException("Cannot publish notebook view settings");
        }
    }
    private static NotebookReadingSettings read(File file)throws IOException {
        if(file.length()<20||file.length()>MAX_BYTES)throw new IOException("Invalid view settings size");
        try(DataInputStream in=new DataInputStream(new FileInputStream(file))){
            if(in.readInt()!=MAGIC||in.readInt()!=VERSION)throw new IOException("Unknown view settings version");
            int length=in.readInt();if(length<0||in.readInt()!=~length||length!=file.length()-20)throw new IOException("Invalid view settings framing");
            byte[] data=new byte[length];in.readFully(data);if(in.readInt()!=crc(data)||in.read()!=-1)throw new IOException("View settings checksum failed");
            DataInputStream fields=new DataInputStream(new ByteArrayInputStream(data));int mode=fields.readUnsignedByte(),single=fields.readUnsignedByte();
            if(mode>=NotebookReadingSettings.Crop.values().length||single>1)throw new IOException("Invalid view settings flags");
            float contrast=fields.readFloat();
            try{
                NotebookCropBox odd=box(fields),even=box(fields);int count=fields.readInt();
                if(count<0||count>NotebookIndex.MAX_PAGES||count*32!=fields.available())throw new IOException("Invalid page crop count");
                Map<UUID,NotebookCropBox> pages=new LinkedHashMap<>();
                for(int n=0;n<count;n++){UUID id=new UUID(fields.readLong(),fields.readLong());if(pages.put(id,box(fields))!=null)throw new IOException("Duplicate crop page");}
                return new NotebookReadingSettings(NotebookReadingSettings.Crop.values()[mode],single==1,contrast,odd,even,pages);
            }catch(IllegalArgumentException e){throw new IOException("Invalid notebook view settings",e);}
        }
    }
    private static void box(DataOutputStream out,NotebookCropBox b)throws IOException{out.writeFloat(b.left);out.writeFloat(b.top);out.writeFloat(b.right);out.writeFloat(b.bottom);}
    private static NotebookCropBox box(DataInputStream in)throws IOException{return new NotebookCropBox(in.readFloat(),in.readFloat(),in.readFloat(),in.readFloat());}
    private static int crc(byte[] bytes){CRC32 crc=new CRC32();crc.update(bytes);return(int)crc.getValue();}
}
