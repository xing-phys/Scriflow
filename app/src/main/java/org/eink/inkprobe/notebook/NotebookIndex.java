package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.zip.CRC32;

/**
 * Single-writer notebook page order and selection. Page 1 remains in the legacy page directory;
 * the index never opens, moves or rewrites a page file. Call from the notebook's serial worker.
 */
public final class NotebookIndex {
    public static final int MAX_PAGES = 1000;
    private static final int MAGIC = 0x4e424931; // NBI1
    private static final int VERSION = 1;
    private static final int PAYLOAD_BYTES = 8;
    private static final int FILE_BYTES = 28;

    public static final class State {
        public final int pageCount;
        public final int selectedPage;

        private State(int pageCount, int selectedPage) {
            this.pageCount = pageCount;
            this.selectedPage = selectedPage;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof State)) return false;
            State state = (State) other;
            return pageCount == state.pageCount && selectedPage == state.selectedPage;
        }

        @Override public int hashCode() { return 31 * pageCount + selectedPage; }
    }

    public static final class CorruptIndexException extends IOException {
        public CorruptIndexException(String message) { super(message); }
    }

    private final File directory, document, backup, temporary;
    private State state;
    private boolean failed, needsSnapshot;

    public NotebookIndex(File directory) {
        if (directory == null) throw new IllegalArgumentException("Missing notebook directory");
        this.directory = directory;
        document = new File(directory, "book.nbi");
        backup = new File(directory, "book.nbi.bak");
        temporary = new File(directory, "book.nbi.tmp");
    }

    /** Only complete absence means a legacy one-page notebook. Corruption never resets page order. */
    public State load() throws IOException {
        state = null;
        failed = false;
        File source = document.exists() ? document : backup.exists() ? backup : temporary.exists() ? temporary : null;
        needsSnapshot = source == null || !source.equals(document);
        if (source == null) return state = new State(1, 1);
        try {
            state = read(source);
            return state;
        } catch (IOException error) {
            failed = true;
            throw error;
        }
    }

    public State current() {
        if (state == null) throw new IllegalStateException("Notebook index has not loaded successfully");
        return state;
    }

    /** Pages are ordered, consecutive and append-only; publication can add at most one page. */
    public State save(int pageCount, int selectedPage) throws IOException {
        requireReady();
        validate(pageCount, selectedPage);
        if (pageCount != state.pageCount && pageCount != state.pageCount + 1) {
            throw new IOException("Notebook pages must be added one at a time");
        }
        State updated = new State(pageCount, selectedPage);
        if (updated.equals(state) && !needsSnapshot) return state;
        byte[] payload = payload(updated);
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Cannot create notebook index directory");
            }
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                DataOutputStream data = new DataOutputStream(output);
                data.writeInt(MAGIC);
                data.writeInt(VERSION);
                data.writeInt(PAYLOAD_BYTES);
                data.writeInt(~PAYLOAD_BYTES);
                data.write(payload);
                data.writeInt(checksum(payload));
                data.flush();
                output.getFD().sync();
            }
            // A crash between the renames leaves the preceding index in backup, never an empty book.
            if (document.exists()) {
                if (backup.exists() && !backup.delete()) throw new IOException("Cannot rotate notebook index backup");
                if (!document.renameTo(backup)) throw new IOException("Cannot preserve notebook index backup");
            }
            if (!temporary.renameTo(document)) {
                if (!document.exists() && backup.exists()) backup.renameTo(document);
                throw new IOException("Cannot publish notebook index");
            }
            state = updated;
            needsSnapshot = false;
            return state;
        } catch (IOException error) {
            failed = true;
            throw error;
        }
    }

    /** Reconfirms a selected index after an uncertain save, including backup-only recovery. */
    public void sync() throws IOException {
        requireReady();
        File source = document.exists() ? document : backup.exists() ? backup : temporary.exists() ? temporary : null;
        if (source == null) return;
        try (RandomAccessFile file = new RandomAccessFile(source, "rw")) {
            file.getFD().sync();
        } catch (IOException error) {
            failed = true;
            throw error;
        }
    }

    private void requireReady() throws IOException {
        if (failed) throw new IOException("Notebook index writes stopped after an earlier storage error; reload before retrying");
        if (state == null) throw new IllegalStateException("Load the notebook index before writing");
    }

    private static State read(File source) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(source, "r")) {
            if (input.length() != FILE_BYTES) throw new CorruptIndexException("Incomplete or oversized notebook index");
            if (input.readInt() != MAGIC || input.readInt() != VERSION) {
                throw new CorruptIndexException("Unsupported notebook index header");
            }
            if (input.readInt() != PAYLOAD_BYTES || input.readInt() != ~PAYLOAD_BYTES) {
                throw new CorruptIndexException("Notebook index length check failed");
            }
            byte[] payload = new byte[PAYLOAD_BYTES];
            input.readFully(payload);
            if (input.readInt() != checksum(payload)) throw new CorruptIndexException("Notebook index checksum failed");
            int count = integer(payload, 0), selected = integer(payload, 4);
            try { validate(count, selected); }
            catch (IOException error) { throw new CorruptIndexException("Invalid notebook page count or selection"); }
            return new State(count, selected);
        }
    }

    private static void validate(int count, int selected) throws IOException {
        if (count < 1 || count > MAX_PAGES || selected < 1 || selected > count) {
            throw new IOException("Notebook page count or selection is out of range");
        }
    }

    private static byte[] payload(State state) {
        byte[] bytes = new byte[PAYLOAD_BYTES];
        putInteger(bytes, 0, state.pageCount);
        putInteger(bytes, 4, state.selectedPage);
        return bytes;
    }

    private static int integer(byte[] bytes, int offset) {
        return (bytes[offset] & 255) << 24 | (bytes[offset + 1] & 255) << 16
                | (bytes[offset + 2] & 255) << 8 | (bytes[offset + 3] & 255);
    }

    private static void putInteger(byte[] bytes, int offset, int value) {
        for (int i = 0; i < 4; i++) bytes[offset + i] = (byte) (value >>> (24 - i * 8));
    }

    private static int checksum(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return (int) crc.getValue();
    }
}
