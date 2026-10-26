package org.eink.inkprobe.notebook;

import java.io.File;
import java.io.FileOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Single-writer page order. Version 1 implies consecutive slots; version 2 maps display
 * positions to stable storage slots. Publishing order never moves or rewrites page files.
 */
public final class NotebookIndex {
    public static final int MAX_PAGES = 1000;
    private static final int MAGIC = 0x4e424931; // NBI1
    private static final int PAYLOAD_BYTES = 8;
    private static final int MAX_PAYLOAD_BYTES = 20 + MAX_PAGES * 4;

    public static final class State {
        public final int pageCount;
        public final int selectedPage;
        public final List<Integer> slots;
        public final int nextSlot;
        /** Changes only when page order changes; binds scroll positions to that order. */
        public final long generation;

        private State(int pageCount, int selectedPage) {
            this(consecutive(pageCount), selectedPage, pageCount + 1, 0);
        }

        private State(List<Integer> slots, int selectedPage, int nextSlot, long generation) {
            this.pageCount = slots.size(); this.selectedPage = selectedPage;
            this.slots = Collections.unmodifiableList(new ArrayList<>(slots));
            this.nextSlot = nextSlot; this.generation = generation;
        }

        public int slot(int page) { return slots.get(page - 1); }
        public boolean managed() { return generation != 0; }

        State reordered(List<Integer> order, int selected, int next) throws IOException {
            if (generation == Long.MAX_VALUE) throw new IOException("Notebook page-order limit reached");
            State target = new State(order, selected, Math.max(nextSlot, next), generation + 1);
            validate(target); return target;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof State)) return false;
            State state = (State) other;
            return selectedPage == state.selectedPage && slots.equals(state.slots)
                && nextSlot == state.nextSlot && generation == state.generation;
        }

        @Override public int hashCode() { return java.util.Objects.hash(slots, selectedPage, nextSlot, generation); }
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

    /** Legacy append/navigation entry point; existing books retain v1 until a management edit. */
    public State save(int pageCount, int selectedPage) throws IOException {
        requireReady();
        validate(pageCount, selectedPage);
        if (pageCount != state.pageCount && pageCount != state.pageCount + 1) {
            throw new IOException("Notebook pages must be added one at a time");
        }
        State updated;
        if (!state.managed()) updated = new State(pageCount, selectedPage);
        else if (pageCount == state.pageCount) updated = new State(state.slots, selectedPage, state.nextSlot, state.generation);
        else {
            if (state.nextSlot == Integer.MAX_VALUE) throw new IOException("Notebook page storage limit reached");
            List<Integer> order = new ArrayList<>(state.slots); order.add(state.nextSlot);
            updated = state.reordered(order, selectedPage, state.nextSlot + 1);
        }
        return publish(updated);
    }

    /** Publish one exact structural change, including a session undo, in a single snapshot. */
    State saveOrder(State updated) throws IOException {
        requireReady(); validate(updated);
        if (updated.equals(state)) return publish(updated);
        if (updated.generation != state.generation + 1 || updated.nextSlot < state.nextSlot)
            throw new IOException("Invalid page-order transition");
        return publish(updated);
    }

    private State publish(State updated) throws IOException {
        if (updated.equals(state) && !needsSnapshot) return state;
        byte[] payload = payload(updated);
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Cannot create notebook index directory");
            }
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                DataOutputStream data = new DataOutputStream(output);
                data.writeInt(MAGIC);
                data.writeInt(updated.managed() ? 2 : 1);
                data.writeInt(payload.length);
                data.writeInt(~payload.length);
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
            if (input.length() < 28 || input.length() > MAX_PAYLOAD_BYTES + 20)
                throw new CorruptIndexException("Incomplete or oversized notebook index");
            if (input.readInt() != MAGIC) {
                throw new CorruptIndexException("Unsupported notebook index header");
            }
            int version = input.readInt();
            if (version != 1 && version != 2) throw new CorruptIndexException("Unsupported notebook index version");
            int length = input.readInt();
            if (length < 8 || length > MAX_PAYLOAD_BYTES || input.readInt() != ~length
                    || input.length() != length + 20L || version == 1 && length != PAYLOAD_BYTES) {
                throw new CorruptIndexException("Notebook index length check failed");
            }
            byte[] payload = new byte[length];
            input.readFully(payload);
            if (input.readInt() != checksum(payload)) throw new CorruptIndexException("Notebook index checksum failed");
            int count = integer(payload, 0), selected = integer(payload, 4);
            try {
                validate(count, selected);
                if (version == 1) return new State(count, selected);
                if (length != 20 + count * 4) throw new IOException("Invalid page-order length");
                int next = integer(payload, 8);
                long generation = ((long) integer(payload, 12) << 32) | (integer(payload, 16) & 0xffffffffL);
                List<Integer> slots = new ArrayList<>();
                for (int i = 0; i < count; i++) slots.add(integer(payload, 20 + i * 4));
                State result = new State(slots, selected, next, generation);
                validate(result);
                if (!result.managed()) throw new IOException("Missing page-order generation");
                return result;
            }
            catch (IOException error) { throw new CorruptIndexException("Invalid notebook page count or selection"); }
        }
    }

    private static void validate(int count, int selected) throws IOException {
        if (count < 1 || count > MAX_PAGES || selected < 1 || selected > count) {
            throw new IOException("Notebook page count or selection is out of range");
        }
    }

    private static void validate(State state) throws IOException {
        validate(state.pageCount, state.selectedPage);
        if (state.generation < 0 || state.nextSlot < 2) throw new IOException("Invalid page-order metadata");
        HashSet<Integer> seen = new HashSet<>();
        for (int slot : state.slots) {
            if (slot < 1 || slot >= state.nextSlot || !seen.add(slot)) throw new IOException("Invalid or duplicate page slot");
        }
    }

    private static List<Integer> consecutive(int count) {
        List<Integer> slots = new ArrayList<>(); for (int i = 1; i <= count; i++) slots.add(i); return slots;
    }

    private static byte[] payload(State state) {
        byte[] bytes = new byte[state.managed() ? 20 + state.pageCount * 4 : PAYLOAD_BYTES];
        putInteger(bytes, 0, state.pageCount);
        putInteger(bytes, 4, state.selectedPage);
        if (state.managed()) {
            putInteger(bytes, 8, state.nextSlot);
            putInteger(bytes, 12, (int) (state.generation >>> 32));
            putInteger(bytes, 16, (int) state.generation);
            for (int i = 0; i < state.slots.size(); i++) putInteger(bytes, 20 + i * 4, state.slots.get(i));
        }
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
