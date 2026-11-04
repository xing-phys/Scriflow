package org.eink.inkprobe.notebook;

import java.util.Base64;

/** A bounded local copy of an explicitly selected image or whole-notebook PDF. */
final class NotebookAiAttachment {
    static final int MAX_BYTES=1024*1024,MAX_PDF_BYTES=8*1024*1024,MAX_PER_MESSAGE=4,MAX_TOTAL_BYTES=16*1024*1024;
    final String name;
    private final byte[] data;
    private final boolean pdf;
    NotebookAiAttachment(String name,byte[] data) {
        boolean image=data!=null&&data.length>=8&&data.length<=MAX_BYTES
            &&data[0]==(byte)137&&data[1]==80&&data[2]==78&&data[3]==71&&data[4]==13&&data[5]==10&&data[6]==26&&data[7]==10;
        boolean document=data!=null&&data.length>=16&&data.length<=MAX_PDF_BYTES
            &&data[0]=='%'&&data[1]=='P'&&data[2]=='D'&&data[3]=='F'&&data[4]=='-';
        if (name==null||name.isEmpty()||name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>120||name.matches("(?s).*\\p{Cntrl}.*")||!image&&!document)
            throw new IllegalArgumentException("Invalid or oversized AI attachment");
        this.name=name; this.data=data.clone();this.pdf=document;
    }
    boolean isPdf(){return pdf;}
    int byteCount() { return data.length; }
    byte[] bytes() { return data.clone(); }
    String base64() { return Base64.getEncoder().encodeToString(data); }
    String dataUrl() { return "data:"+(pdf?"application/pdf":"image/png")+";base64,"+base64(); }
}
