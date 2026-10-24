package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.zip.CRC32;

/** Small single-writer viewport anchor, saved only after a completed scrolling gesture. */
public final class NotebookScrollStore {
    private static final int MAGIC = 0x4e425331, VERSION = 1, PAYLOAD_BYTES = 12, FILE_BYTES = 32;
    public static final class State {
        public final int page;
        public final double fraction;
        public State(int page, double fraction) {
            if (!valid(page, fraction)) throw new IllegalArgumentException("Invalid notebook scroll position");
            this.page = page; this.fraction = fraction == 0 ? 0 : fraction;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof State)) return false;
            State state = (State) other;
            return page == state.page && Double.doubleToLongBits(fraction) == Double.doubleToLongBits(state.fraction);
        }
        @Override public int hashCode() {
            long bits = Double.doubleToLongBits(fraction); return 31 * page + (int) (bits ^ bits >>> 32);
        }
    }

    public static final class CorruptScrollException extends IOException {
        public CorruptScrollException(String message) { super(message); }
    }
    private final File directory, document, backup, temporary;
    private State state;
    private boolean failed, needsSnapshot;
    public NotebookScrollStore(File directory) {
        if (directory == null) throw new IllegalArgumentException("Missing notebook directory");
        this.directory = directory;
        document = new File(directory, "scroll.nbs"); backup = new File(directory, "scroll.nbs.bak");
        temporary = new File(directory, "scroll.nbs.tmp");
    }

    public State load(int defaultPage) throws IOException {
        State fallback = new State(defaultPage, 0);
        state = null; failed = false;
        File source = source(); needsSnapshot = source == null || !source.equals(document);
        if (source == null) return state = fallback;
        try { return state = read(source); }
        catch (IOException error) { failed = true; throw error; }
    }

    public State current() {
        if (state == null) throw new IllegalStateException("Load the scroll position before reading");
        return state;
    }

    public State save(int page, double fraction) throws IOException {
        requireReady();
        if (!valid(page, fraction)) throw new IOException("Invalid notebook scroll position");
        State updated = new State(page, fraction);
        if (updated.equals(state) && !needsSnapshot) return state;
        byte[] payload = payload(updated);
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create notebook scroll directory");
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                DataOutputStream data = new DataOutputStream(output);
                data.writeInt(MAGIC); data.writeInt(VERSION); data.writeInt(PAYLOAD_BYTES); data.writeInt(~PAYLOAD_BYTES);
                data.write(payload); data.writeInt(checksum(payload)); data.flush(); output.getFD().sync();
            }
            if (document.exists()) {
                if (backup.exists() && !backup.delete()) throw new IOException("Cannot rotate notebook scroll backup");
                if (!document.renameTo(backup)) throw new IOException("Cannot preserve notebook scroll backup");
            }
            if (!temporary.renameTo(document)) {
                if (!document.exists() && backup.exists()) backup.renameTo(document);
                throw new IOException("Cannot publish notebook scroll position");
            }
            needsSnapshot = false; return state = updated;
        } catch (IOException error) { failed = true; throw error; }
    }

    public void sync() throws IOException {
        requireReady(); File source = source(); if (source == null) return;
        try (RandomAccessFile file = new RandomAccessFile(source, "rw")) { file.getFD().sync(); }
        catch (IOException error) { failed = true; throw error; }
    }
    private File source() { return document.exists() ? document : backup.exists() ? backup : temporary.exists() ? temporary : null; }
    private void requireReady() throws IOException {
        if (failed) throw new IOException("Scroll position writes stopped after an earlier storage error; reload before retrying");
        if (state == null) throw new IllegalStateException("Load the scroll position before writing");
    }
    private static boolean valid(int page, double fraction) {
        return page >= 1 && page <= NotebookIndex.MAX_PAGES && !Double.isNaN(fraction)
            && !Double.isInfinite(fraction) && fraction >= 0 && fraction < 1;
    }
    private static byte[] payload(State state) {
        byte[] bytes = new byte[PAYLOAD_BYTES];
        for (int i = 0; i < 4; i++) bytes[i] = (byte) (state.page >>> (24 - 8 * i));
        long fraction = Double.doubleToLongBits(state.fraction);
        for (int i = 0; i < 8; i++) bytes[4 + i] = (byte) (fraction >>> (56 - 8 * i));
        return bytes;
    }
    private static State read(File source) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(source, "r")) {
            if (input.length() != FILE_BYTES) throw new CorruptScrollException("Incomplete or oversized notebook scroll position");
            if (input.readInt() != MAGIC || input.readInt() != VERSION) throw new CorruptScrollException("Unsupported scroll position header");
            if (input.readInt() != PAYLOAD_BYTES || input.readInt() != ~PAYLOAD_BYTES) throw new CorruptScrollException("Scroll position length check failed");
            byte[] payload = new byte[PAYLOAD_BYTES]; input.readFully(payload);
            if (input.readInt() != checksum(payload)) throw new CorruptScrollException("Scroll position checksum failed");
            int page = 0; long fraction = 0;
            for (int i = 0; i < 4; i++) page = page << 8 | payload[i] & 255;
            for (int i = 4; i < 12; i++) fraction = fraction << 8 | payload[i] & 255L;
            double value = Double.longBitsToDouble(fraction);
            if (!valid(page, value)) throw new CorruptScrollException("Invalid notebook scroll position");
            return new State(page, value);
        }
    }
    private static int checksum(byte[] bytes) { CRC32 crc = new CRC32(); crc.update(bytes); return (int) crc.getValue(); }
}
