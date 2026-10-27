package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.file.Files;
import java.util.UUID;
import java.util.zip.CRC32;

/** Single-writer redo journal for an indivisible move of ink between two page slots. */
final class NotebookInkTransfer {
    static final String PENDING = "ink-transfer.pending";
    private static final String STAGING = "ink-transfer.staging-", COMPLETED = "ink-transfer.completed-";
    private static final int MAGIC = 0x494d5631, VERSION = 1;

    static final class Transaction {
        final int sourceSlot, destinationSlot;
        final NotebookPage sourceBefore, sourceAfter, destinationBefore, destinationAfter;
        Transaction(int sourceSlot, int destinationSlot, NotebookPage sourceBefore, NotebookPage sourceAfter,
                    NotebookPage destinationBefore, NotebookPage destinationAfter) {
            this.sourceSlot = sourceSlot; this.destinationSlot = destinationSlot;
            this.sourceBefore = sourceBefore; this.sourceAfter = sourceAfter;
            this.destinationBefore = destinationBefore; this.destinationAfter = destinationAfter;
        }
    }

    private NotebookInkTransfer() { }
    static boolean pending(File directory) { return new File(directory, PENDING).exists(); }

    /** No original page is changed until the complete checked intent has been published. */
    static Transaction prepare(File directory, int sourceSlot, int destinationSlot,
            NotebookPage sourceBefore, NotebookPage sourceAfter,
            NotebookPage destinationBefore, NotebookPage destinationAfter) throws IOException {
        if (pending(directory)) throw new IOException("Finish the previous cross-page move before moving more ink");
        if (sourceSlot < 1 || destinationSlot < 1 || sourceSlot == destinationSlot)
            throw new IOException("Invalid cross-page move slots");
        validateEnvelope(sourceBefore, sourceAfter); validateEnvelope(destinationBefore, destinationAfter);
        if (sourceBefore.id.equals(destinationBefore.id)) throw new IOException("Cross-page move has duplicate page identities");
        cleanup(directory);
        File staged = new File(directory, STAGING + UUID.randomUUID());
        if (!staged.mkdir()) throw new IOException("Cannot stage the cross-page move");
        boolean published = false;
        try {
            writePage(staged, "source-before", sourceBefore); writePage(staged, "source-after", sourceAfter);
            writePage(staged, "destination-before", destinationBefore); writePage(staged, "destination-after", destinationAfter);
            byte[] record = record(sourceSlot, destinationSlot);
            try (FileOutputStream output = new FileOutputStream(new File(staged, "intent.bin"))) {
                output.write(record); output.getFD().sync();
            }
            // The journal is never permission to overwrite an unrelated page.
            requireExact(loadOriginal(directory, sourceSlot).current, sourceBefore);
            requireExact(loadOriginal(directory, destinationSlot).current, destinationBefore);
            if (!staged.renameTo(new File(directory, PENDING))) throw new IOException("Cannot publish the cross-page move intent");
            published = true;
            return new Transaction(sourceSlot, destinationSlot, sourceBefore, sourceAfter, destinationBefore, destinationAfter);
        } finally { if (!published) deleteTree(staged); }
    }

    /** Validate both participants before writing either, then publish destination before source. */
    static Transaction recover(File directory) throws IOException {
        cleanup(directory);
        File pending = new File(directory, PENDING);
        if (!pending.exists()) return null;
        if (Files.isSymbolicLink(pending.toPath()) || !pending.isDirectory()) throw new IOException("Invalid cross-page move journal; files preserved");
        Transaction transaction = read(directory, pending);
        Original source = loadOriginal(directory, transaction.sourceSlot);
        Original destination = loadOriginal(directory, transaction.destinationSlot);
        requireBeforeOrAfter(source.current, transaction.sourceBefore, transaction.sourceAfter);
        requireBeforeOrAfter(destination.current, transaction.destinationBefore, transaction.destinationAfter);
        apply(destination, transaction.destinationAfter);
        apply(source, transaction.sourceAfter);
        File completed = new File(directory, COMPLETED + UUID.randomUUID());
        if (!pending.renameTo(completed)) throw new IOException("Cross-page move saved; retry to finish its transaction");
        // After this atomic rename, interrupted cleanup can never look like an active journal.
        deleteTree(completed);
        return transaction;
    }

    private static final class Original {
        final NotebookStore store;
        final NotebookPage current;
        final boolean primary;
        Original(NotebookStore store, NotebookPage current, boolean primary) {
            this.store = store; this.current = current; this.primary = primary;
        }
    }

    private static Original loadOriginal(File directory, int slot) throws IOException {
        File target = slot == 1 ? directory : new File(new File(directory, "pages"), Integer.toString(slot));
        if (!target.isDirectory() || Files.isSymbolicLink(target.toPath())) throw new IOException("Cross-page move refers to a missing page; files preserved");
        File expected = slot == 1 ? directory.toPath().toRealPath().toFile()
            : new File(new File(directory.toPath().toRealPath().toFile(), "pages"), Integer.toString(slot));
        if (!target.toPath().toRealPath().toFile().equals(expected)) throw new IOException("Unexpected page path; files preserved");
        if (!new File(target, "page.nbk").isFile() && !new File(target, "page.nbk.bak").isFile()
                && !new File(target, "page.nbk.tmp").isFile()) throw new IOException("Cross-page move page is missing; files preserved");
        NotebookStore store = new NotebookStore(target);
        return new Original(store, store.load(), new File(target, "page.nbk").isFile());
    }

    private static void apply(Original original, NotebookPage after) throws IOException {
        // A recovered .tmp/.bak target must become a primary before discarding our final copy.
        if (!original.primary || !NotebookDocument.samePage(original.current, after)) original.store.replaceStrokes(after.strokes);
        original.store.sync();
    }

    private static Transaction read(File directory, File journal) throws IOException {
        File record = new File(journal, "intent.bin");
        if (record.length() != 20 || !record.isFile()) throw new IOException("Incomplete cross-page move intent; files preserved");
        byte[] bytes = new byte[20];
        try (DataInputStream input = new DataInputStream(new FileInputStream(record))) { input.readFully(bytes); }
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
        if (input.readInt() != MAGIC || input.readInt() != VERSION) throw new IOException("Unsupported cross-page move intent");
        int source = input.readInt(), destination = input.readInt();
        CRC32 crc = new CRC32(); crc.update(bytes, 0, 16);
        if (input.readInt() != (int) crc.getValue() || source < 1 || destination < 1 || source == destination)
            throw new IOException("Invalid cross-page move intent; files preserved");
        NotebookIndex.State index = new NotebookIndex(directory).load();
        if (!index.slots.contains(source) || !index.slots.contains(destination))
            throw new IOException("Page order changed during the cross-page move; files preserved");
        NotebookPage sourceBefore = readPage(journal, "source-before"), sourceAfter = readPage(journal, "source-after");
        NotebookPage destinationBefore = readPage(journal, "destination-before"), destinationAfter = readPage(journal, "destination-after");
        validateEnvelope(sourceBefore, sourceAfter); validateEnvelope(destinationBefore, destinationAfter);
        if (sourceBefore.id.equals(destinationBefore.id)) throw new IOException("Cross-page move has duplicate page identities");
        return new Transaction(source, destination, sourceBefore, sourceAfter, destinationBefore, destinationAfter);
    }

    private static void validateEnvelope(NotebookPage before, NotebookPage after) throws IOException {
        if (before == null || after == null || !before.isInitialized()
                || !NotebookDocument.samePage(before.withStrokes(after.strokes), after))
            throw new IOException("Cross-page move changes page metadata; files preserved");
    }

    private static void requireExact(NotebookPage actual, NotebookPage expected) throws IOException {
        if (!NotebookDocument.samePage(actual, expected)) throw new IOException("The saved page changed before moving ink; files preserved");
    }
    private static void requireBeforeOrAfter(NotebookPage actual, NotebookPage before, NotebookPage after) throws IOException {
        if (!NotebookDocument.samePage(actual, before) && !NotebookDocument.samePage(actual, after))
            throw new IOException("The saved page differs from the cross-page move; files preserved");
    }

    private static void writePage(File journal, String name, NotebookPage page) throws IOException {
        File target = new File(journal, name);
        if (!target.mkdir()) throw new IOException("Cannot stage cross-page move snapshots");
        try (FileOutputStream output = new FileOutputStream(new File(target, "page.nbk"))) {
            NotebookStore.writeSnapshot(output, page); output.getFD().sync();
        }
    }
    private static NotebookPage readPage(File journal, String name) throws IOException {
        File target = new File(journal, name);
        if (Files.isSymbolicLink(target.toPath()) || !new File(target, "page.nbk").isFile())
            throw new IOException("Incomplete cross-page move snapshot; files preserved");
        return new NotebookStore(target).load();
    }
    private static byte[] record(int source, int destination) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream data = new DataOutputStream(bytes);
        data.writeInt(MAGIC); data.writeInt(VERSION); data.writeInt(source); data.writeInt(destination); data.flush();
        CRC32 crc = new CRC32(); crc.update(bytes.toByteArray()); data.writeInt((int) crc.getValue()); data.flush();
        return bytes.toByteArray();
    }

    /** Abandoned staging and completed journals are inert; cleanup never changes page files. */
    private static void cleanup(File directory) {
        File[] children = directory.listFiles(); if (children == null) return;
        for (File child : children) {
            String name = child.getName();
            String suffix = name.startsWith(STAGING) ? name.substring(STAGING.length())
                : name.startsWith(COMPLETED) ? name.substring(COMPLETED.length()) : "";
            try { if (UUID.fromString(suffix).toString().equals(suffix)) deleteTree(child); }
            catch (IllegalArgumentException ignored) { /* Unrelated files are not ours. */ }
        }
    }
    private static void deleteTree(File target) {
        if (Files.isSymbolicLink(target.toPath())) return;
        File[] children = target.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        target.delete();
    }
}
