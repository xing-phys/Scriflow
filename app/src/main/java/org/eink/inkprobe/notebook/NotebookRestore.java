package org.eink.inkprobe.notebook;

import java.io.*;
import java.util.*;

/** Small redo transaction: publish an already validated new book, then its catalog entry. */
final class NotebookRestore {
    private static final String PENDING = "restore.pending", OWNER = "restore.owner";
    private static final int MAGIC = 0x49525431;
    private NotebookRestore() { }

    static void publish(File library, NotebookBackup.Restored restored) throws IOException {
        recover(library);
        NotebookCatalog catalog = new NotebookCatalog(library);
        NotebookCatalog.State state = catalog.load();
        int id = state.availableId();
        if (id == 0) throw new IOException("Notebook library is full. Permanently delete a notebook from Trash to free space.");
        File parent = new File(library, "notebooks");
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create notebook directory");
        File target = new File(parent, Integer.toString(id));
        if (target.exists()) throw new IOException("An unlisted notebook exists; its files were preserved");
        String token = UUID.randomUUID().toString();
        File staged = new File(parent, ".restore-" + token);
        byte[] record = record(token, id, restored.name);
        write(new File(restored.directory, OWNER), record);
        if (!restored.directory.renameTo(staged)) throw new IOException("Cannot stage restored notebook");
        // No catalog or existing page changes before the durable intent record.
        File temporary = new File(library, PENDING + ".tmp");
        try {
            write(temporary, record);
            if (!temporary.renameTo(new File(library, PENDING))) throw new IOException("Cannot publish restore intent");
        } catch (IOException error) {
            NotebookBackup.deleteTree(staged); temporary.delete(); throw error;
        }
        recover(library);
    }

    /** Idempotent after process death at either directory or catalog publication. */
    static boolean recover(File library) throws IOException {
        File intent = new File(library, PENDING);
        if (!intent.exists()) return false;
        if (intent.length() < 20 || intent.length() > 1024) throw new IOException("Invalid pending restore; files preserved");
        byte[] bytes = new byte[(int) intent.length()];
        try (DataInputStream input = new DataInputStream(new FileInputStream(intent))) { input.readFully(bytes); }
        DataInputStream data = new DataInputStream(new ByteArrayInputStream(bytes));
        if (data.readInt() != MAGIC) throw new IOException("Invalid pending restore");
        String token = data.readUTF(); int id = data.readInt(); String name = data.readUTF();
        try {
            if (!UUID.fromString(token).toString().equals(token) || !NotebookCatalog.normalizedName(name).equals(name))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) { throw new IOException("Invalid restore metadata", error); }
        if (data.available() != 0 || id < 1 || id > NotebookCatalog.MAX_NOTEBOOKS) throw new IOException("Invalid restore target");
        NotebookCatalog catalog = new NotebookCatalog(library); NotebookCatalog.State state = catalog.load();
        boolean published = id <= state.entries.size() && state.entries.get(id - 1).name.equals(name)
            && state.entries.get(id - 1).status == NotebookCatalog.ACTIVE;
        boolean vacant = id == state.entries.size() + 1 || id <= state.entries.size()
            && state.entries.get(id - 1).status == NotebookCatalog.PURGED;
        if (!vacant && !published) throw new IOException("Catalog changed during restore; files preserved");
        File parent = new File(library, "notebooks");
        File staged = new File(parent, ".restore-" + token), target = new File(parent, Integer.toString(id));
        File source = target.exists() ? target : staged;
        File owner = new File(source, OWNER);
        byte[] actual = new byte[bytes.length];
        try (DataInputStream input = new DataInputStream(new FileInputStream(owner))) {
            input.readFully(actual);
            if (input.read() != -1 || !Arrays.equals(actual, bytes)) throw new IOException("Unexpected restored directory; files preserved");
        }
        // Verify the complete staged book before publishing or replaying its catalog change.
        NotebookBook check = new NotebookBook(source); check.load();
        for (int number = 1; number <= check.pageCount(); number++) check.previewPage(number);
        if (!target.exists() && !staged.renameTo(target)) throw new IOException("Cannot publish restored notebook");
        if (!published) {
            List<NotebookCatalog.Entry> entries = new ArrayList<>(state.entries);
            if (id <= entries.size()) entries.set(id - 1, new NotebookCatalog.Entry(id, name));
            else entries.add(new NotebookCatalog.Entry(id, name));
            catalog.save(entries, id);
        }
        catalog.sync();
        // Delete intent first: a crash leaves an inert owner marker, never a missing proof of ownership.
        if (!intent.delete()) throw new IOException("Restore completed but its transaction could not be finalized; retry");
        new File(target, OWNER).delete();
        return true;
    }

    private static byte[] record(String token, int id, String name) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream data = new DataOutputStream(bytes);
        data.writeInt(MAGIC); data.writeUTF(token); data.writeInt(id); data.writeUTF(name); data.flush();
        return bytes.toByteArray();
    }
    private static void write(File file, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); output.getFD().sync(); }
    }
}
