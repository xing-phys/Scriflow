package org.eink.inkprobe.notebook;

import java.util.Base64;

/** Local, bounded PNG copy of an explicitly selected object or handwriting region. */
final class NotebookAiAttachment {
    static final int MAX_BYTES=1024*1024,MAX_PER_MESSAGE=4,MAX_TOTAL_BYTES=8*1024*1024;
    final String name;
    private final byte[] png;
    NotebookAiAttachment(String name,byte[] png) {
        if (name==null||name.isEmpty()||name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>120||name.matches("(?s).*\\p{Cntrl}.*")||png==null||png.length<8||png.length>MAX_BYTES
                ||png[0]!=(byte)137||png[1]!=80||png[2]!=78||png[3]!=71||png[4]!=13||png[5]!=10||png[6]!=26||png[7]!=10)
            throw new IllegalArgumentException("Invalid or oversized AI image attachment");
        this.name=name; this.png=png.clone();
    }
    int byteCount() { return png.length; }
    byte[] bytes() { return png.clone(); }
    String base64() { return Base64.getEncoder().encodeToString(png); }
    String dataUrl() { return "data:image/png;base64,"+base64(); }
}
