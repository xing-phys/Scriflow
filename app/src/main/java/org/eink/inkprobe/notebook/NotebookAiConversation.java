package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

/** Bounded, atomic local transcript. A final user turn is retained for explicit Retry. */
final class NotebookAiConversation {
    static final int MAX_MESSAGES=80, MAX_MESSAGE_BYTES=128*1024, MAX_TOTAL_BYTES=1024*1024;
    static final int MAX_SNAPSHOT_BYTES=MAX_TOTAL_BYTES+NotebookAiAttachment.MAX_TOTAL_BYTES+65536;
    static final class Message {
        final String role, content;
        final List<NotebookAiAttachment> images;
        Message(String role,String content) { this(role,content,Collections.emptyList()); }
        Message(String role, String content,List<NotebookAiAttachment> images) {
            if (!("user".equals(role)||"assistant".equals(role)) || content==null || content.trim().isEmpty()
                    || bytes(content).length>MAX_MESSAGE_BYTES) throw new IllegalArgumentException("Chat message is empty or exceeds 128 KiB");
            this.role=role; this.content=content;
            if (images.size()>NotebookAiAttachment.MAX_PER_MESSAGE||!images.isEmpty()&&!role.equals("user")) throw new IllegalArgumentException("Attach up to four images to a user message");
            this.images=Collections.unmodifiableList(new ArrayList<>(images));
        }
    }
    final List<Message> messages;
    final String draft;
    final List<NotebookAiAttachment> draftImages;

    NotebookAiConversation() { this(Collections.emptyList(),"",Collections.emptyList()); }
    private NotebookAiConversation(List<Message> messages, String draft) {
        this(messages,draft,Collections.emptyList());
    }
    private NotebookAiConversation(List<Message> messages, String draft,List<NotebookAiAttachment> images) {
        if (messages.size()>MAX_MESSAGES || bytes(draft).length>MAX_MESSAGE_BYTES)
            throw new IllegalArgumentException("Chat is full. Start a new chat or shorten your message.");
        int total=bytes(draft).length;
        if (images.size()>NotebookAiAttachment.MAX_PER_MESSAGE) throw new IllegalArgumentException("Attach up to four images to a message");
        int imageBytes=0;
        for (NotebookAiAttachment image:images) imageBytes+=image.byteCount();
        for (int i=0;i<messages.size();i++) {
            Message message=messages.get(i);
            if (!message.role.equals(i%2==0?"user":"assistant")) throw new IllegalArgumentException("Invalid chat turn order");
            total+=bytes(message.content).length;
            for (NotebookAiAttachment image:message.images) imageBytes+=image.byteCount();
        }
        if (total>MAX_TOTAL_BYTES) throw new IllegalArgumentException("Chat is full. Start a new chat.");
        if (imageBytes>NotebookAiAttachment.MAX_TOTAL_BYTES) throw new IllegalArgumentException("Chat attachments exceed 16 MiB. Start a new chat.");
        this.messages=Collections.unmodifiableList(new ArrayList<>(messages)); this.draft=draft;
        this.draftImages=Collections.unmodifiableList(new ArrayList<>(images));
    }
    boolean awaitingReply() { return messages.size()%2==1; }
    boolean isEmpty() { return messages.isEmpty()&&draft.isEmpty()&&draftImages.isEmpty(); }
    NotebookAiConversation draft(String text) { return new NotebookAiConversation(messages,text,draftImages); }
    NotebookAiConversation attach(NotebookAiAttachment image) {
        List<NotebookAiAttachment> images=new ArrayList<>(draftImages); images.add(image);
        return new NotebookAiConversation(messages,draft,images);
    }
    NotebookAiConversation removeImage(int index) {
        List<NotebookAiAttachment> images=new ArrayList<>(draftImages); images.remove(index);
        return new NotebookAiConversation(messages,draft,images);
    }
    NotebookAiConversation question(String text) {
        if (awaitingReply()) throw new IllegalArgumentException("Retry the last message or start a new chat");
        if (messages.size()>MAX_MESSAGES-2) throw new IllegalArgumentException("Chat is full. Start a new chat.");
        String content=text.trim();
        if (content.isEmpty()&&!draftImages.isEmpty()) content="Please help me with the attached material.";
        List<Message> next=new ArrayList<>(messages); next.add(new Message("user",content,draftImages));
        return new NotebookAiConversation(next,"",Collections.emptyList());
    }
    NotebookAiConversation answer(String text) {
        if (!awaitingReply()) throw new IllegalArgumentException("No chat message is awaiting a reply");
        return append(new Message("assistant",text));
    }
    private NotebookAiConversation append(Message message) {
        List<Message> next=new ArrayList<>(messages); next.add(message); return new NotebookAiConversation(next,draft,draftImages);
    }
    String lastAnswer() {
        for (int i=messages.size()-1;i>=0;i--) if (messages.get(i).role.equals("assistant")) return messages.get(i).content;
        return "";
    }
    static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
    private static File file(File directory, String profile) {
        if (!profile.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid AI profile");
        return new File(directory,"ai-chat-"+profile+".bin");
    }
    static NotebookAiConversation read(File directory,String profile) throws IOException {
        File file=file(directory,profile);
        if (!file.exists()) return new NotebookAiConversation();
        if (file.length()>MAX_SNAPSHOT_BYTES) throw new IOException("Saved AI chat exceeds its size limit");
        return fromSnapshot(Files.readAllBytes(file.toPath()));
    }
    static NotebookAiConversation fromSnapshot(byte[] data) throws IOException {
        if (data.length>MAX_SNAPSHOT_BYTES) throw new IOException("Saved AI chat exceeds its size limit");
        try (DataInputStream in=new DataInputStream(new ByteArrayInputStream(data))) {
            int version=in.readInt();
            if (version!=0x53414931&&version!=0x53414932&&version!=0x53414933) throw new IOException("Unrecognized saved AI chat");
            long expected=in.readLong(); CRC32 crc=new CRC32(); crc.update(data,12,data.length-12);
            if (expected!=crc.getValue()) throw new IOException("Saved AI chat is damaged");
            int count=in.readInt(); if (count<0||count>MAX_MESSAGES) throw new IOException("Invalid AI chat length");
            List<Message> messages=new ArrayList<>();
            for (int i=0;i<count;i++) {
                String role=in.readBoolean()?"user":"assistant",content=readText(in);
                messages.add(new Message(role,content,version!=0x53414931?readImages(in,version):Collections.emptyList()));
            }
            String draft=readText(in);
            NotebookAiConversation value=new NotebookAiConversation(messages,draft,version!=0x53414931?readImages(in,version):Collections.emptyList());
            if (in.read()!=-1) throw new IOException("Unexpected saved AI chat data");
            return value;
        } catch (EOFException error) { throw new IOException("Saved AI chat is incomplete",error); }
        catch (IllegalArgumentException error) { throw new IOException("Saved AI chat is damaged",error); }
    }
    static void write(File directory,String profile,NotebookAiConversation chat) throws IOException {
        publish(file(directory,profile),snapshot(chat));
    }
    static byte[] snapshot(NotebookAiConversation chat) throws IOException {
        boolean newFormat=false;int attachmentBytes=0;
        for(NotebookAiAttachment value:chat.draftImages){newFormat|=value.isPdf();attachmentBytes+=value.byteCount();}
        for(Message message:chat.messages)for(NotebookAiAttachment value:message.images){newFormat|=value.isPdf();attachmentBytes+=value.byteCount();}
        newFormat|=attachmentBytes>8*1024*1024;
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        try (DataOutputStream out=new DataOutputStream(body)) {
            out.writeInt(chat.messages.size());
            for (Message message:chat.messages) { out.writeBoolean(message.role.equals("user")); writeText(out,message.content); writeImages(out,message.images); }
            writeText(out,chat.draft); writeImages(out,chat.draftImages);
        }
        byte[] data=body.toByteArray(); CRC32 crc=new CRC32(); crc.update(data);
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        try (DataOutputStream out=new DataOutputStream(result)) {
            out.writeInt(newFormat?0x53414933:0x53414932); out.writeLong(crc.getValue()); out.write(data);
        }
        return result.toByteArray();
    }
    static void publish(File target,byte[] data) throws IOException {
        File temporary=new File(target.getParentFile(),target.getName()+".tmp");
        try {
            try (FileOutputStream stream=new FileOutputStream(temporary); DataOutputStream out=new DataOutputStream(stream)) {
                out.write(data); out.flush(); stream.getFD().sync();
            }
            try { Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }
    private static void writeText(DataOutputStream out,String text) throws IOException {
        byte[] data=bytes(text); out.writeInt(data.length); out.write(data);
    }
    private static void writeImages(DataOutputStream out,List<NotebookAiAttachment> images) throws IOException {
        out.writeInt(images.size());
        for (NotebookAiAttachment image:images) { writeText(out,image.name); byte[] data=image.bytes(); out.writeInt(data.length); out.write(data); }
    }
    private static List<NotebookAiAttachment> readImages(DataInputStream in,int version) throws IOException {
        int count=in.readInt();
        if (count<0||count>NotebookAiAttachment.MAX_PER_MESSAGE) throw new IOException("Invalid AI image count");
        List<NotebookAiAttachment> images=new ArrayList<>();
        for (int i=0;i<count;i++) {
            String name=readText(in); int length=in.readInt();
            if (length<8||length>(version==0x53414932?NotebookAiAttachment.MAX_BYTES:NotebookAiAttachment.MAX_PDF_BYTES)) throw new IOException("Invalid AI attachment size");
            byte[] data=new byte[length]; in.readFully(data); NotebookAiAttachment value=new NotebookAiAttachment(name,data);
            if(version==0x53414932&&value.isPdf())throw new IOException("Invalid legacy AI image");images.add(value);
        }
        return images;
    }
    private static String readText(DataInputStream in) throws IOException {
        int length=in.readInt(); if (length<0||length>MAX_MESSAGE_BYTES) throw new IOException("Invalid AI chat message length");
        byte[] data=new byte[length]; in.readFully(data);
        String text=new String(data,StandardCharsets.UTF_8);
        if (!Arrays.equals(data,bytes(text))) throw new IOException("Invalid AI chat text encoding");
        return text;
    }
}
