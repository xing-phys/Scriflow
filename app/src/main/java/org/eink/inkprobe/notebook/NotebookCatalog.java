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
    private static final int VERSION = 2;
    private static final int MAX_NAME_BYTES = MAX_NAME_CODE_POINTS * 4;
    private static final int MAX_PAYLOAD_BYTES = 16 + MAX_NOTEBOOKS * (28 + 2 * MAX_NAME_BYTES);
    public static final int ACTIVE = 0, TRASH = 1, PURGING = 2, PURGED = 3;

    public static final class Folder {
        public final int id;
        public final String name;
        public Folder(int id, String name) {
            if (id < 1) throw new IllegalArgumentException("Invalid folder ID");
            this.id = id; this.name = normalizedName(name);
        }
        @Override public boolean equals(Object other) {
            return other instanceof Folder && id == ((Folder) other).id && name.equals(((Folder) other).name);
        }
        @Override public int hashCode() { return 31 * id + name.hashCode(); }
    }

    public static final class Entry {
        public final int id;
        public final String name;
        public final int folderId, status;

        public Entry(int id, String name) {
            this(id, name, 0, ACTIVE);
        }

        public Entry(int id, String name, int folderId, int status) {
            if (id < 1 || id > MAX_NOTEBOOKS) throw new IllegalArgumentException("Invalid notebook ID");
            if (folderId < 0 || status < ACTIVE || status > PURGED)
                throw new IllegalArgumentException("Invalid notebook location");
            this.id = id;
            this.name = normalizedName(name);
            this.folderId = folderId; this.status = status;
        }

        public Entry renamed(String value) { return new Entry(id, value, folderId, status); }
        public Entry moved(int folder) { return new Entry(id, name, folder, status); }
        public Entry withStatus(int value) { return new Entry(id, name, value == PURGED ? 0 : folderId, value); }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Entry)) return false;
            Entry entry = (Entry) other;
            return id == entry.id && name.equals(entry.name) && folderId == entry.folderId && status == entry.status;
        }

        @Override public int hashCode() { return 31 * (31 * (31 * id + name.hashCode()) + folderId) + status; }
    }

    public static final class State {
        public final List<Entry> entries;
        public final int selectedId;
        public final List<Folder> folders;

        private State(List<Entry> entries, int selectedId, List<Folder> folders) {
            this.entries = Collections.unmodifiableList(new ArrayList<Entry>(entries));
            this.selectedId = selectedId;
            this.folders = Collections.unmodifiableList(new ArrayList<>(folders));
        }

        /** Reuse only fully purged slots. Trash still counts toward the library limit. */
        public int availableId() {
            for (Entry entry : entries) if (entry.status == PURGED) return entry.id;
            return entries.size() < MAX_NOTEBOOKS ? entries.size() + 1 : 0;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof State)) return false;
            State state = (State) other;
            return selectedId == state.selectedId && entries.equals(state.entries) && folders.equals(state.folders);
        }

        @Override public int hashCode() { return 31 * (31 * entries.hashCode() + selectedId) + folders.hashCode(); }
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
            return state = new State(Collections.singletonList(new Entry(1, "My Notebook")), 1, Collections.emptyList());
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
        return save(entries, selectedId, current().folders);
    }

    public State save(List<Entry> entries, int selectedId, List<Folder> folders) throws IOException {
        requireReady();
        State updated = checkedState(entries, selectedId, folders);
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

    private static State checkedState(List<Entry> entries, int selectedId, List<Folder> folders) throws IOException {
        if (entries == null || entries.isEmpty() || entries.size() > MAX_NOTEBOOKS
                || selectedId < 1 || selectedId > entries.size()) throw new IOException("Invalid notebook library count or selection");
        List<Entry> copy = new ArrayList<Entry>(entries);
        if (folders == null || folders.size() > MAX_NOTEBOOKS) throw new IOException("Invalid folder count");
        java.util.Set<Integer> folderIds = new java.util.HashSet<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        for (Folder folder : folders) {
            if (folder == null || !folderIds.add(folder.id) || !names.add(folder.name.toLowerCase(java.util.Locale.ROOT)))
                throw new IOException("Folder names and IDs must be unique");
        }
        for (int i = 0; i < copy.size(); i++) {
            Entry entry = copy.get(i);
            if (entry == null || entry.id != i + 1) throw new IOException("Notebook IDs must remain consecutive and ordered");
            if (entry.folderId != 0 && !folderIds.contains(entry.folderId)) throw new IOException("Notebook folder is missing");
        }
        return new State(copy, selectedId, folders);
    }

    private static State read(File source) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(source, "r")) {
            if (file.length() < 28 || file.length() > MAX_PAYLOAD_BYTES + 20L) throw new CorruptCatalogException("Incomplete or oversized notebook library");
            if (file.readInt() != MAGIC) throw new CorruptCatalogException("Unsupported notebook library header");
            int version = file.readInt();
            if (version != 1 && version != VERSION) throw new CorruptCatalogException("Unsupported notebook library version");
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
            List<Folder> folders = new ArrayList<>();
            if (version >= 2) {
                int folderCount = input.readInt();
                if (folderCount < 0 || folderCount > MAX_NOTEBOOKS) throw new CorruptCatalogException("Invalid folder count");
                for (int i = 0; i < folderCount; i++) {
                    int id = input.readInt(); String name = readName(input);
                    try { folders.add(new Folder(id, name)); }
                    catch (IllegalArgumentException error) { throw new CorruptCatalogException("Invalid folder"); }
                }
            }
            List<Entry> entries = new ArrayList<Entry>();
            for (int i = 0; i < count; i++) {
                int id = input.readInt(); String name = readName(input);
                int folder = version >= 2 ? input.readInt() : 0;
                int status = version >= 2 ? input.readInt() : ACTIVE;
                try {
                    Entry entry = new Entry(id, name, folder, status);
                    if (!entry.name.equals(name)) throw new IllegalArgumentException();
                    entries.add(entry);
                } catch (IllegalArgumentException error) { throw new CorruptCatalogException("Invalid notebook name or ID"); }
            }
            if (input.available() != 0) throw new CorruptCatalogException("Trailing notebook library data");
            try { return checkedState(entries, selected, folders); }
            catch (IOException error) { throw new CorruptCatalogException("Invalid notebook library order or selection"); }
        }
    }

    private static byte[] payload(State state) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(bytes);
        data.writeInt(state.entries.size());
        data.writeInt(state.selectedId);
        data.writeInt(state.folders.size());
        for (Folder folder : state.folders) {
            data.writeInt(folder.id); writeName(data, folder.name);
        }
        for (Entry entry : state.entries) {
            data.writeInt(entry.id);
            writeName(data, entry.name);
            data.writeInt(entry.folderId); data.writeInt(entry.status);
        }
        data.flush();
        return bytes.toByteArray();
    }

    private static void writeName(DataOutputStream data, String value) throws IOException {
        byte[] name = value.getBytes(StandardCharsets.UTF_8);
        data.writeInt(name.length); data.write(name);
    }

    private static String readName(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size < 1 || size > MAX_NAME_BYTES || size > input.available()) throw new CorruptCatalogException("Invalid name length");
        byte[] encoded = new byte[size]; input.readFully(encoded);
        try {
            String name = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(encoded)).toString();
            if (!normalizedName(name).equals(name)) throw new IllegalArgumentException();
            return name;
        } catch (CharacterCodingException | IllegalArgumentException error) { throw new CorruptCatalogException("Invalid name encoding or text"); }
    }

    private static int checksum(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return (int) crc.getValue();
    }
}
