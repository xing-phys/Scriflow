package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.Arrays;

/** Editable source and two self-contained vector appearances. Placement belongs to its page object. */
public final class NotebookMarkdown {
    public static final int MAX_SOURCE_BYTES = 128 * 1024, MAX_PDF_BYTES = 4 * 1024 * 1024;
    public final String source;
    public final boolean sourceMode;
    public final float renderedWidth, renderedHeight, sourceWidth, sourceHeight;
    private final byte[] text, rendered, code;

    public NotebookMarkdown(String source, boolean sourceMode, float renderedWidth, float renderedHeight,
            byte[] rendered, float sourceWidth, float sourceHeight, byte[] code) {
        this(source, sourceMode, renderedWidth, renderedHeight, rendered, sourceWidth, sourceHeight, code, true);
    }

    private NotebookMarkdown(String source, boolean sourceMode, float rw, float rh, byte[] rendered,
            float sw, float sh, byte[] code, boolean copy) {
        if (source == null || !dimension(rw) || !dimension(rh) || !dimension(sw) || !dimension(sh)
                || !pdf(rendered) || !pdf(code)) throw new IllegalArgumentException("Invalid Markdown block");
        try {
            ByteBuffer encoded = StandardCharsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).encode(java.nio.CharBuffer.wrap(source));
            if (encoded.remaining() > MAX_SOURCE_BYTES) throw new IllegalArgumentException("Markdown source exceeds 128 KiB");
            text = new byte[encoded.remaining()]; encoded.get(text);
        } catch (CharacterCodingException error) { throw new IllegalArgumentException("Invalid Markdown text", error); }
        this.source = source; this.sourceMode = sourceMode;
        renderedWidth = rw; renderedHeight = rh; sourceWidth = sw; sourceHeight = sh;
        this.rendered = copy ? rendered.clone() : rendered; this.code = copy ? code.clone() : code;
    }

    private static boolean dimension(float value) { return Float.isFinite(value) && value >= 1 && value <= 32768; }
    private static boolean pdf(byte[] data) {
        return data != null && data.length >= 16 && data.length <= MAX_PDF_BYTES
            && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F' && data[4] == '-';
    }
    public float width() { return sourceMode ? sourceWidth : renderedWidth; }
    public float height() { return sourceMode ? sourceHeight : renderedHeight; }
    public NotebookMarkdown asSource(boolean value) {
        return value == sourceMode ? this : new NotebookMarkdown(source, value, renderedWidth, renderedHeight,
            rendered, sourceWidth, sourceHeight, code, false);
    }
    public int byteCount() { return 29 + text.length + rendered.length + code.length; }
    byte[] appearance() { return sourceMode ? code : rendered; } // Package readers never mutate these bytes.
    public byte[] pdf() { return appearance().clone(); }

    void writeTo(DataOutputStream out) throws IOException {
        out.writeBoolean(sourceMode); out.writeInt(text.length); out.write(text);
        out.writeFloat(renderedWidth); out.writeFloat(renderedHeight); out.writeInt(rendered.length); out.write(rendered);
        out.writeFloat(sourceWidth); out.writeFloat(sourceHeight); out.writeInt(code.length); out.write(code);
    }
    static NotebookMarkdown readFrom(DataInputStream in) throws IOException {
        boolean mode = in.readBoolean(); byte[] text = bytes(in, MAX_SOURCE_BYTES);
        final String source;
        try { source = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(text)).toString(); }
        catch (CharacterCodingException error) { throw new IOException("Invalid Markdown text", error); }
        float rw = in.readFloat(), rh = in.readFloat(); byte[] rendered = bytes(in, MAX_PDF_BYTES);
        float sw = in.readFloat(), sh = in.readFloat(); byte[] code = bytes(in, MAX_PDF_BYTES);
        try { return new NotebookMarkdown(source, mode, rw, rh, rendered, sw, sh, code, false); }
        catch (IllegalArgumentException error) { throw new IOException("Invalid Markdown block", error); }
    }
    private static byte[] bytes(DataInputStream in, int maximum) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > maximum || count > in.available()) throw new IOException("Invalid Markdown data size");
        byte[] result = new byte[count]; in.readFully(result); return result;
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof NotebookMarkdown)) return false;
        NotebookMarkdown m = (NotebookMarkdown) other;
        return sourceMode == m.sourceMode && source.equals(m.source) && renderedWidth == m.renderedWidth
            && renderedHeight == m.renderedHeight && sourceWidth == m.sourceWidth && sourceHeight == m.sourceHeight
            && Arrays.equals(rendered, m.rendered) && Arrays.equals(code, m.code);
    }
    @Override public int hashCode() { return source.hashCode(); }
}
