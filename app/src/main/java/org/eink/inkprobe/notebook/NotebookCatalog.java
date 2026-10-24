package org.eink.inkprobe.notebook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.CRC32;

/** Single-writer notebook names and selection. Names are metadata, never directory names. */
public final class NotebookCatalog {
    public static final int MAX_NOTEBOOKS = 200;
    public static final int MAX_NAME_CODE_POINTS = 80;
    private static final int MAGIC = 0x4e424c31; // NBL1
    private static final int VERSION = 1;
    private static final int MAX_NAME_BYTES = MAX_NAME_CODE_POINTS * 4;
    private static final int MAX_PAYLOAD_BYTES = 8 + MAX_NOTEBOOKS * (8 + MAX_NAME_BYTES);

    public static final class Entry {
        public final int id;
        public final String name;

        public Entry(int id, String name) {
            if (id < 1 || id > MAX_NOTEBOOKS) throw new IllegalArgumentException("Invalid notebook ID");
            this.id = id;
            this.name = normalizedName(name);
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Entry)) return false;
            Entry entry = (Entry) other;
            return id == entry.id && name.equals(entry.name);
        }

        @Override public int hashCode() { return 31 * id + name.hashCode(); }
    }

    public static final class State {
        public final List<Entry> entries;
        public final int selectedId;

        private State(List<Entry> entries, int selectedId) {
            this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
            this.selectedId = selectedId;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof State)) return false;
            State state = (State) other;
            return selectedId == state.selectedId && entries.equals(state.entries);
        }

        @Override public int hashCode() { return 31 * entries.hashCode() + selectedId; }
    }

    public static final class CorruptCatalogException extends IOException {
        public CorruptCatalogException(String message) { super(message); }
    }

    private final File directory, document, backup, temporary;
    private State state;
    private boolean failed, needsSnapshot;

    public NotebookCatalog(File directory) {
        if (directory == null) throw new IllegalArgumentException("Missing notebook library directory");
        this.directory = directory;
        document = new File(directory, "library.nbl");
        backup = new File(directory, "library.nbl.bak");
        temporary = new File(directory, "library.nbl.tmp");
    }

    /** Complete absence imports the legacy notebook by reference without touching any files. */
    public State load() throws IOException {
        state = null;
        failed = false;
        File source = source();
        needsSnapshot = source == null || !source.equals(document);
        if (source == null) {
            return state = new State(Collections.singletonList(new Entry(1, "My Notebook")), 1);
        }
        try {
            return state = read(source);
        } catch (IOException error) {
            failed = true;
            throw error;
        }
    }

    public State current() {
        if (state == null) throw new IllegalStateException("Notebook library has not loaded successfully");
        return state;
    }

    /** Stable consecutive IDs permit renames, selection and appending one notebook at a time. */
    public State save(List<Entry> entries, int selectedId) throws IOException {
        requireReady();
        State updated = checkedState(entries, selectedId);
        int count = updated.entries.size();
        if (count != state.entries.size() && count != state.entries.size() + 1) {
            throw new IOException("Notebooks must be added one at a time");
        }
        if (updated.equals(state) && !needsSnapshot) return state;
        byte[] payload = payload(updated);
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Cannot create notebook library directory");
            }
            try (FileOutputStream output = new FileOutputStream(temporary)) {
                DataOutputStream data = new DataOutputStream(output);
                data.writeInt(MAGIC);
                data.writeInt(VERSION);
                data.writeInt(payload.length);
                data.writeInt(~payload.length);
                data.write(payload);
                data.writeInt(checksum(payload));
                data.flush();
                output.getFD().sync();
            }
            if (document.exists()) {
                if (backup.exists() && !backup.delete()) throw new IOException("Cannot rotate notebook library backup");
                if (!document.renameTo(backup)) throw new IOException("Cannot preserve notebook library backup");
            }
            if (!temporary.renameTo(document)) {
                if (!document.exists() && backup.exists()) backup.renameTo(document);
                throw new IOException("Cannot publish notebook library");
            }
            state = updated;
            needsSnapshot = false;
            return state;
        } catch (IOException error) {
            failed = true;
            throw error;
        }
    }

    public void sync() throws IOException {
        requireReady();
        File source = source();
        if (source == null) return;
        try (RandomAccessFile file = new RandomAccessFile(source, "rw")) {
            file.getFD().sync();
        } catch (IOException error) {
            failed = true;
            throw error;
        }
    }

    /** Trim Unicode whitespace; reject multiline/control text and invalid UTF-16. */
    public static String normalizedName(String name) {
        if (name == null) throw new IllegalArgumentException("Enter a notebook name");
        for (int i = 0; i < name.length();) {
            char value = name.charAt(i);
            if (Character.isSurrogate(value)
                    && (!Character.isHighSurrogate(value) || i + 1 >= name.length()
                    || !Character.isLowSurrogate(name.charAt(i + 1)))) {
                throw new IllegalArgumentException("Invalid character in notebook name");
            }
            int cp = name.codePointAt(i);
            if (Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029) {
                throw new IllegalArgumentException("Notebook names must be on one line");
            }
            i += Character.charCount(cp);
        }
        int start = 0, end = name.length();
        while (start < end && space(name.codePointAt(start))) start += Character.charCount(name.codePointAt(start));
        while (end > start && space(name.codePointBefore(end))) end -= Character.charCount(name.codePointBefore(end));
        String result = name.substring(start, end);
        int count = result.codePointCount(0, result.length());
        if (count == 0) throw new IllegalArgumentException("Enter a notebook name");
        if (count > MAX_NAME_CODE_POINTS) throw new IllegalArgumentException("Notebook names can have at most 80 characters");
        return result;
    }

    private static boolean space(int cp) { return Character.isWhitespace(cp) || Character.isSpaceChar(cp); }

    private File source() {
        return document.exists() ? document : backup.exists() ? backup : temporary.exists() ? temporary : null;
    }

    private void requireReady() throws IOException {
        if (failed) throw new IOException("Notebook library writes stopped after a storage error; reload before retrying");
        if (state == null) throw new IllegalStateException("Load the notebook library before writing");
    }

    private static State checkedState(List<Entry> entries, int selectedId) throws IOException {
        if (entries == null || entries.isEmpty() || entries.size() > MAX_NOTEBOOKS
                || selectedId < 1 || selectedId > entries.size()) throw new IOException("Invalid notebook library count or selection");
        List<Entry> copy = new ArrayList<Entry>(entries);
        for (int i = 0; i < copy.size(); i++) {
            Entry entry = copy.get(i);
            if (entry == null || entry.id != i + 1) throw new IOException("Notebook IDs must remain consecutive and ordered");
        }
        return new State(copy, selectedId);
    }

    private static State read(File source) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(source, "r")) {
            if (file.length() < 28 || file.length() > MAX_PAYLOAD_BYTES + 20L) throw new CorruptCatalogException("Incomplete or oversized notebook library");
            if (file.readInt() != MAGIC || file.readInt() != VERSION) throw new CorruptCatalogException("Unsupported notebook library header");
            int length = file.readInt();
            if (length < 8 || length > MAX_PAYLOAD_BYTES || file.readInt() != ~length || file.length() != length + 20L) {
                throw new CorruptCatalogException("Notebook library length check failed");
            }
            byte[] bytes = new byte[length];
            file.readFully(bytes);
            if (file.readInt() != checksum(bytes)) throw new CorruptCatalogException("Notebook library checksum failed");
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
            int count = input.readInt(), selected = input.readInt();
            if (count < 1 || count > MAX_NOTEBOOKS) throw new CorruptCatalogException("Invalid notebook count");
            List<Entry> entries = new ArrayList<Entry>();
            for (int i = 0; i < count; i++) {
                int id = input.readInt(), size = input.readInt();
                if (size < 1 || size > MAX_NAME_BYTES || size > input.available()) throw new CorruptCatalogException("Invalid notebook name length");
                byte[] encoded = new byte[size];
                input.readFully(encoded);
                String name;
                try {
                    name = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(encoded)).toString();
                } catch (CharacterCodingException error) { throw new CorruptCatalogException("Invalid notebook name encoding"); }
                try {
                    Entry entry = new Entry(id, name);
                    if (!entry.name.equals(name)) throw new IllegalArgumentException();
                    entries.add(entry);
                } catch (IllegalArgumentException error) { throw new CorruptCatalogException("Invalid notebook name or ID"); }
            }
            if (input.available() != 0) throw new CorruptCatalogException("Trailing notebook library data");
            try { return checkedState(entries, selected); }
            catch (IOException error) { throw new CorruptCatalogException("Invalid notebook library order or selection"); }
        }
    }

    private static byte[] payload(State state) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeInt(state.entries.size());
        data.writeInt(state.selectedId);
        for (Entry entry : state.entries) {
            byte[] name = entry.name.getBytes(StandardCharsets.UTF_8);
            data.writeInt(entry.id);
            data.writeInt(name.length);
            data.write(name);
        }
        data.flush();
        return bytes.toByteArray();
    }

    private static int checksum(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return (int) crc.getValue();
    }
}
