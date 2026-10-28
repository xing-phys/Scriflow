package org.eink.inkprobe.notebook;

import java.io.*;
import java.util.zip.*;

/** Tiny real RGB/RGBA PNGs, without Android decoding or a host graphics toolkit. */
final class NotebookFigureFixture {
    static byte[] png(boolean alpha) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeLong(0x89504e470d0a1a0aL);
        ByteArrayOutputStream header = new ByteArrayOutputStream(); DataOutputStream fields = new DataOutputStream(header);
        fields.writeInt(2); fields.writeInt(2); fields.writeByte(8); fields.writeByte(alpha ? 6 : 2);
        fields.write(new byte[3]); chunk(out, "IHDR", header.toByteArray());
        ByteArrayOutputStream pixels = new ByteArrayOutputStream();
        try (DeflaterOutputStream zipped = new DeflaterOutputStream(pixels)) {
            for (int y = 0; y < 2; y++) { zipped.write(0); for (int x = 0; x < 2; x++) {
                zipped.write(x * 255); zipped.write(y * 255); zipped.write(80); if (alpha) zipped.write(x == y ? 128 : 255);
            } }
        }
        chunk(out, "IDAT", pixels.toByteArray()); chunk(out, "IEND", new byte[0]); return bytes.toByteArray();
    }
    private static void chunk(DataOutputStream out, String type, byte[] bytes) throws IOException {
        byte[] name = type.getBytes("US-ASCII"); CRC32 crc = new CRC32(); crc.update(name); crc.update(bytes);
        out.writeInt(bytes.length); out.write(name); out.write(bytes); out.writeInt((int) crc.getValue());
    }
}
