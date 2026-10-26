package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.zip.CRC32;

/** Small single-writer viewport anchor, saved only after a completed scrolling gesture. */
public final class NotebookScrollStore {
    private static final int MAGIC = 0x4e425331, PAYLOAD_BYTES = 12;
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
    private final long generation;
    private State state;
    private boolean failed, needsSnapshot;
    public NotebookScrollStore(File directory) {
        this(directory, 0);
    }
    /** A scroll position from another page order defaults to the newly selected page. */
    NotebookScrollStore(File directory, long generation) {
        if (directory == null) throw new IllegalArgumentException("Missing notebook directory");
        if (generation < 0) throw new IllegalArgumentException("Invalid page-order generation");
        this.generation = generation;
        this.directory = directory;
        document = new File(directory, "scroll.nbs"); backup = new File(directory, "scroll.nbs.bak");
        temporary = new File(directory, "scroll.nbs.tmp");
    }

    public State load(int defaultPage) throws IOException {
        State fallback = new State(defaultPage, 0);
        state = null; failed = false;
        File source = source(); needsSnapshot = source == null || !source.equals(document);
        if (source == null) return state = fallback;
        try { State read = read(source); return state = read == null ? fallback : read; }
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
        byte[] payload = payload(updated, generation);
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Cannot create notebook scroll directory");
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                DataOutputStream data = new DataOutputStream(output);
                data.writeInt(MAGIC); data.writeInt(generation == 0 ? 1 : 2);
                data.writeInt(payload.length); data.writeInt(~payload.length);
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
    private static byte[] payload(State state, long generation) {
        byte[] bytes = new byte[generation == 0 ? PAYLOAD_BYTES : PAYLOAD_BYTES + 8];
        for (int i = 0; i < 4; i++) bytes[i] = (byte) (state.page >>> (24 - 8 * i));
        long fraction = Double.doubleToLongBits(state.fraction);
        for (int i = 0; i < 8; i++) bytes[4 + i] = (byte) (fraction >>> (56 - 8 * i));
        if (generation != 0) for (int i = 0; i < 8; i++) bytes[12 + i] = (byte) (generation >>> (56 - 8 * i));
        return bytes;
    }
    private State read(File source) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(source, "r")) {
            if (input.length() != 32 && input.length() != 40) throw new CorruptScrollException("Incomplete or oversized notebook scroll position");
            if (input.readInt() != MAGIC) throw new CorruptScrollException("Unsupported scroll position header");
            int version = input.readInt();
            if (version != 1 && version != 2) throw new CorruptScrollException("Unsupported scroll position version");
            int length = version == 1 ? 12 : 20;
            if (input.readInt() != length || input.readInt() != ~length || input.length() != length + 20)
                throw new CorruptScrollException("Scroll position length check failed");
            byte[] payload = new byte[length]; input.readFully(payload);
            if (input.readInt() != checksum(payload)) throw new CorruptScrollException("Scroll position checksum failed");
            int page = 0; long fraction = 0;
            for (int i = 0; i < 4; i++) page = page << 8 | payload[i] & 255;
            for (int i = 4; i < 12; i++) fraction = fraction << 8 | payload[i] & 255L;
            double value = Double.longBitsToDouble(fraction);
            if (!valid(page, value)) throw new CorruptScrollException("Invalid notebook scroll position");
            long savedGeneration = 0;
            if (version == 2) for (int i = 12; i < 20; i++) savedGeneration = savedGeneration << 8 | payload[i] & 255L;
            if (savedGeneration < 0 || version == 2 && savedGeneration == 0)
                throw new CorruptScrollException("Invalid scroll page-order generation");
            if (savedGeneration != generation) { needsSnapshot = true; return null; }
            return new State(page, value);
        }
    }
    private static int checksum(byte[] bytes) { CRC32 crc = new CRC32(); crc.update(bytes); return (int) crc.getValue(); }
}
