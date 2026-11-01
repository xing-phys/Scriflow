package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;

/** Bounded, atomic local transcript. A final user turn is retained for explicit Retry. */
final class NotebookAiConversation {
    static final int MAX_MESSAGES=80, MAX_MESSAGE_BYTES=128*1024, MAX_TOTAL_BYTES=1024*1024;
    static final class Message {
        final String role, content;
        Message(String role, String content) {
            if (!("user".equals(role)||"assistant".equals(role)) || content==null || content.trim().isEmpty()
                    || bytes(content).length>MAX_MESSAGE_BYTES) throw new IllegalArgumentException("Chat message is empty or exceeds 128 KiB");
            this.role=role; this.content=content;
        }
    }
    final List<Message> messages;
    final String draft;

    NotebookAiConversation() { this(Collections.emptyList(),""); }
    private NotebookAiConversation(List<Message> messages, String draft) {
        if (messages.size()>MAX_MESSAGES || bytes(draft).length>MAX_MESSAGE_BYTES)
            throw new IllegalArgumentException("Chat is full. Start a new chat or shorten your message.");
        int total=bytes(draft).length;
        for (int i=0;i<messages.size();i++) {
            Message message=messages.get(i);
            if (!message.role.equals(i%2==0?"user":"assistant")) throw new IllegalArgumentException("Invalid chat turn order");
            total+=bytes(message.content).length;
        }
        if (total>MAX_TOTAL_BYTES) throw new IllegalArgumentException("Chat is full. Start a new chat.");
        this.messages=Collections.unmodifiableList(new ArrayList<>(messages)); this.draft=draft;
    }
    boolean awaitingReply() { return messages.size()%2==1; }
    NotebookAiConversation draft(String text) { return new NotebookAiConversation(messages,text); }
    NotebookAiConversation question(String text) {
        if (awaitingReply()) throw new IllegalArgumentException("Retry the last message or start a new chat");
        if (messages.size()>MAX_MESSAGES-2) throw new IllegalArgumentException("Chat is full. Start a new chat.");
        return draft("").append(new Message("user",text.trim()));
    }
    NotebookAiConversation answer(String text) {
        if (!awaitingReply()) throw new IllegalArgumentException("No chat message is awaiting a reply");
        return append(new Message("assistant",text));
    }
    private NotebookAiConversation append(Message message) {
        List<Message> next=new ArrayList<>(messages); next.add(message); return new NotebookAiConversation(next,draft);
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
        if (file.length()>MAX_TOTAL_BYTES+4096) throw new IOException("Saved AI chat exceeds its size limit");
        byte[] data=Files.readAllBytes(file.toPath());
        try (DataInputStream in=new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readInt()!=0x53414931) throw new IOException("Unrecognized saved AI chat");
            long expected=in.readLong(); CRC32 crc=new CRC32(); crc.update(data,12,data.length-12);
            if (expected!=crc.getValue()) throw new IOException("Saved AI chat is damaged");
            int count=in.readInt(); if (count<0||count>MAX_MESSAGES) throw new IOException("Invalid AI chat length");
            List<Message> messages=new ArrayList<>();
            for (int i=0;i<count;i++) messages.add(new Message(in.readBoolean()?"user":"assistant",readText(in)));
            NotebookAiConversation value=new NotebookAiConversation(messages,readText(in));
            if (in.read()!=-1) throw new IOException("Unexpected saved AI chat data");
            return value;
        } catch (EOFException error) { throw new IOException("Saved AI chat is incomplete",error); }
        catch (IllegalArgumentException error) { throw new IOException("Saved AI chat is damaged",error); }
    }
    static void write(File directory,String profile,NotebookAiConversation chat) throws IOException {
        ByteArrayOutputStream body=new ByteArrayOutputStream();
        try (DataOutputStream out=new DataOutputStream(body)) {
            out.writeInt(chat.messages.size());
            for (Message message:chat.messages) { out.writeBoolean(message.role.equals("user")); writeText(out,message.content); }
            writeText(out,chat.draft);
        }
        byte[] data=body.toByteArray(); CRC32 crc=new CRC32(); crc.update(data);
        File target=file(directory,profile), temporary=new File(directory,target.getName()+".tmp");
        try {
            try (FileOutputStream stream=new FileOutputStream(temporary); DataOutputStream out=new DataOutputStream(stream)) {
                out.writeInt(0x53414931); out.writeLong(crc.getValue()); out.write(data); out.flush(); stream.getFD().sync();
            }
            try { Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException error) { Files.move(temporary.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }
    private static void writeText(DataOutputStream out,String text) throws IOException {
        byte[] data=bytes(text); out.writeInt(data.length); out.write(data);
    }
    private static String readText(DataInputStream in) throws IOException {
        int length=in.readInt(); if (length<0||length>MAX_MESSAGE_BYTES) throw new IOException("Invalid AI chat message length");
        byte[] data=new byte[length]; in.readFully(data);
        String text=new String(data,StandardCharsets.UTF_8);
        if (!Arrays.equals(data,bytes(text))) throw new IOException("Invalid AI chat text encoding");
        return text;
    }
}
