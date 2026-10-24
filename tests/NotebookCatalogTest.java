package org.eink.inkprobe.notebook;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;

/** Real-file catalog tests with independent format fixtures and publication interruptions. */
public final class NotebookCatalogTest {
    private static int assertions;
    private static final List<File> directories = new ArrayList<File>();

    public static void main(String[] args) throws Exception {
        try {
            legacyAndMetadataOnlyPaths();
            createRenameSelectAndImmutableStates();
            unicodeNamesAndInvalidInput();
            invalidChangesNeverMutateFiles();
            corruptOrTruncatedPrimaryFailsClosed();
            checksummedInvalidPayloadsFailClosed();
            interruptedPublicationAndRetry();
            maximumCatalog();
            System.out.println("NotebookCatalog: " + assertions + " assertions passed (host JVM, real files).");
        } finally { for (File directory : directories) deleteTree(directory); }
    }

    private static void legacyAndMetadataOnlyPaths() throws Exception {
        File directory = directory();
        byte[] page = {10, 2, 30, 4}, index = {4, 5, 6};
        Files.write(new File(directory, "page.nbk").toPath(), page);
        Files.write(new File(directory, "book.nbi").toPath(), index);
        NotebookCatalog catalog = new NotebookCatalog(directory);
        state(catalog.load(), 1, "My Notebook");
        catalog.sync();
        check(directory.list().length == 2, "legacy load and sync never create files");
        catalog.save(Arrays.asList(entry(1, "../../outside")), 1);
        check(Arrays.equals(page, bytes(directory, "page.nbk")), "legacy page preserved byte for byte");
        check(Arrays.equals(index, bytes(directory, "book.nbi")), "legacy page index preserved byte for byte");
        check(directory.list().length == 3, "pathlike name creates only catalog");
        state(new NotebookCatalog(directory).load(), 1, "../../outside");
        File missing = new File(directory, "not-created");
        new NotebookCatalog(missing).load();
        check(!missing.exists(), "default library does not create directory");
    }

    private static void createRenameSelectAndImmutableStates() throws Exception {
        File directory = directory();
        NotebookCatalog catalog = new NotebookCatalog(directory);
        NotebookCatalog.State initial = catalog.load();
        List<NotebookCatalog.Entry> entries = entries("My Notebook", "Physics");
        NotebookCatalog.State added = catalog.save(entries, 2);
        entries.clear();
        state(initial, 1, "My Notebook");
        state(added, 2, "My Notebook", "Physics");
        try { added.entries.clear(); throw new AssertionError("mutable entries"); }
        catch (UnsupportedOperationException expected) { assertions++; }
        state(new NotebookCatalog(directory).load(), 2, "My Notebook", "Physics");
        NotebookCatalog.State renamed = catalog.save(entries("My Notebook", "Quantum dynamics"), 1);
        state(added, 2, "My Notebook", "Physics");
        state(renamed, 1, "My Notebook", "Quantum dynamics");
        NotebookCatalog.State reloaded = new NotebookCatalog(directory).load();
        check(renamed.equals(reloaded), "deep value equality");
        check(renamed.hashCode() == reloaded.hashCode(), "matching state hashes");
        check(!renamed.equals(added), "different state unequal");
        check(!renamed.equals("other"), "state rejects different type");
        check(!entry(1, "A").equals(entry(2, "A")), "entry equality includes ID");
        byte[] primary = bytes(directory, "library.nbl"), backup = bytes(directory, "library.nbl.bak");
        catalog.save(entries("My Notebook", "Quantum dynamics"), 1);
        catalog.sync();
        check(Arrays.equals(primary, bytes(directory, "library.nbl")), "same save keeps primary");
        check(Arrays.equals(backup, bytes(directory, "library.nbl.bak")), "same save keeps backup");
    }

    private static void unicodeNamesAndInvalidInput() throws Exception {
        check(NotebookCatalog.normalizedName(" \u3000\u00a0量子动力学 📚\u3000 ").equals("量子动力学 📚"), "trim Unicode whitespace");
        String eighty = repeat("📓", 80);
        check(NotebookCatalog.normalizedName(eighty).equals(eighty), "80 supplementary codepoints accepted");
        File directory = directory();
        NotebookCatalog catalog = new NotebookCatalog(directory);
        catalog.load();
        catalog.save(entries(" e\u0301 / 量子 📓 "), 1);
        state(new NotebookCatalog(directory).load(), 1, "e\u0301 / 量子 📓");
        String[] invalid = {null, "", "  \u3000\u00a0", "a\nb", "a\rb", "a\tb", "\u0000x", "\u007fx", "x\u0085", "x\u2028", "x\u2029", "x\ud800", "\udc00", "\ud800a", repeat("a", 81), repeat("📓", 81)};
        for (String name : invalid) expectArgument(() -> NotebookCatalog.normalizedName(name));
        expectArgument(() -> entry(0, "A"));
        expectArgument(() -> entry(201, "A"));
    }

    private static void invalidChangesNeverMutateFiles() throws Exception {
        File directory = directory();
        NotebookCatalog catalog = new NotebookCatalog(directory);
        catalog.load();
        catalog.save(entries("A", "B"), 2);
        catalog.save(entries("A", "B", "C"), 3);
        Files.write(new File(directory, "library.nbl.tmp").toPath(), new byte[] {42});
        byte[] primary = bytes(directory, "library.nbl"), backup = bytes(directory, "library.nbl.bak"), temp = bytes(directory, "library.nbl.tmp");
        List<List<NotebookCatalog.Entry>> invalid = new ArrayList<List<NotebookCatalog.Entry>>();
        invalid.add(null);
        invalid.add(entries());
        invalid.add(entries("A", "B"));
        invalid.add(entries("A", "B", "C", "D", "E"));
        invalid.add(Arrays.asList(entry(1, "A"), entry(3, "C"), entry(2, "B")));
        invalid.add(Arrays.asList(entry(1, "A"), entry(2, "B"), entry(2, "C")));
        invalid.add(Arrays.asList(entry(1, "A"), null, entry(3, "C")));
        for (List<NotebookCatalog.Entry> value : invalid) {
            expectIo(() -> catalog.save(value, 1));
            unchanged(directory, primary, backup, temp);
        }
        expectIo(() -> catalog.save(entries("A", "B", "C"), 0));
        expectIo(() -> catalog.save(entries("A", "B", "C"), 4));
        unchanged(directory, primary, backup, temp);
        state(catalog.current(), 3, "A", "B", "C");
        state(catalog.save(entries("A", "B", "C", "D"), 4), 4, "A", "B", "C", "D");
    }

    private static void corruptOrTruncatedPrimaryFailsClosed() throws Exception {
        File directory = directory();
        byte[] valid = fixture(new int[] {1, 2}, new byte[][] {utf("A"), utf("量子 📓")}, 2);
        Files.write(new File(directory, "library.nbl.bak").toPath(), fixture(new int[] {1}, new byte[][] {utf("Older")}, 1));
        for (int i = 0; i < valid.length; i++) {
            byte[] bad = valid.clone();
            bad[i] ^= 1;
            Files.write(new File(directory, "library.nbl").toPath(), bad);
            NotebookCatalog catalog = new NotebookCatalog(directory);
            expectIo(() -> catalog.load());
            expectIo(() -> catalog.save(entries("A"), 1));
            expectIo(() -> catalog.sync());
            check(Arrays.equals(bad, bytes(directory, "library.nbl")), "corrupt bytes preserved at " + i);
        }
        for (int size = 0; size < valid.length; size++) {
            byte[] partial = Arrays.copyOf(valid, size);
            Files.write(new File(directory, "library.nbl").toPath(), partial);
            expectIo(() -> new NotebookCatalog(directory).load());
            check(Arrays.equals(partial, bytes(directory, "library.nbl")), "partial primary never silently uses older backup");
        }
        Files.write(new File(directory, "library.nbl").toPath(), Arrays.copyOf(valid, valid.length + 1));
        expectIo(() -> new NotebookCatalog(directory).load());
    }

    private static void checksummedInvalidPayloadsFailClosed() throws Exception {
        File directory = directory();
        List<byte[]> invalid = Arrays.asList(
                fixture(new int[] {}, new byte[][] {}, 1),
                fixture(new int[] {2}, new byte[][] {utf("A")}, 1),
                fixture(new int[] {1, 1}, new byte[][] {utf("A"), utf("B")}, 1),
                fixture(new int[] {1}, new byte[][] {utf("A")}, 2),
                fixture(new int[] {1}, new byte[][] {utf("A")}, 0),
                fixture(new int[] {1}, new byte[][] {utf(" A ")}, 1),
                fixture(new int[] {1}, new byte[][] {utf("A\nB")}, 1),
                fixture(new int[] {1}, new byte[][] {utf(repeat("A", 81))}, 1),
                fixture(new int[] {1}, new byte[][] {new byte[] {(byte)0xc0, (byte)0xaf}}, 1),
                fixture(new int[] {1}, new byte[][] {new byte[] {(byte)0xed, (byte)0xa0, (byte)0x80}}, 1),
                fixture(new int[] {1}, new byte[][] {new byte[] {}}, 1));
        for (byte[] bad : invalid) {
            Files.write(new File(directory, "library.nbl").toPath(), bad);
            expectIo(() -> new NotebookCatalog(directory).load());
            check(Arrays.equals(bad, bytes(directory, "library.nbl")), "invalid checksummed payload preserved");
        }
    }

    private static void interruptedPublicationAndRetry() throws Exception {
        byte[] old = fixture(new int[] {1}, new byte[][] {utf("Old")}, 1);
        byte[] next = fixture(new int[] {1, 2}, new byte[][] {utf("Old"), utf("New")}, 2);
        File directory = directory();
        Files.write(new File(directory, "library.nbl").toPath(), old);
        Files.write(new File(directory, "library.nbl.tmp").toPath(), next);
        state(new NotebookCatalog(directory).load(), 1, "Old");
        check(new File(directory, "library.nbl").renameTo(new File(directory, "library.nbl.bak")), "simulate interrupted rename");
        NotebookCatalog recovered = new NotebookCatalog(directory);
        state(recovered.load(), 1, "Old");
        recovered.sync();
        check(!new File(directory, "library.nbl").exists(), "backup recovery load and sync do not publish");
        recovered.save(entries("Old", "New"), 2);
        state(new NotebookCatalog(directory).load(), 2, "Old", "New");
        check(Arrays.equals(old, bytes(directory, "library.nbl.bak")), "recovery preserves old backup");

        File temporaryOnly = directory();
        Files.write(new File(temporaryOnly, "library.nbl.tmp").toPath(), next);
        NotebookCatalog first = new NotebookCatalog(temporaryOnly);
        state(first.load(), 2, "Old", "New");
        first.save(entries("Old", "New"), 2);
        state(new NotebookCatalog(temporaryOnly).load(), 2, "Old", "New");
        check(!new File(temporaryOnly, "library.nbl.tmp").exists(), "temporary promoted after explicit save");

        File corruptBackup = directory();
        Files.write(new File(corruptBackup, "library.nbl.bak").toPath(), new byte[] {1});
        Files.write(new File(corruptBackup, "library.nbl.tmp").toPath(), next);
        expectIo(() -> new NotebookCatalog(corruptBackup).load());
        check(Arrays.equals(next, bytes(corruptBackup, "library.nbl.tmp")), "corrupt backup does not promote pending next state");

        NotebookCatalog blocked = new NotebookCatalog(directory);
        blocked.load();
        File obstruction = new File(directory, "library.nbl.tmp");
        check(obstruction.mkdir(), "temporary path blocked");
        expectIo(() -> blocked.save(entries("Renamed", "New"), 1));
        state(blocked.current(), 2, "Old", "New");
        check(obstruction.delete(), "clear temporary obstruction");
        expectIo(() -> blocked.save(entries("Renamed", "New"), 1));
        blocked.load();
        state(blocked.save(entries("Renamed", "New"), 1), 1, "Renamed", "New");

        // A nonempty backup directory makes rotation fail after the new snapshot has synced.
        check(new File(directory, "library.nbl.bak").delete(), "clear old backup");
        check(new File(directory, "library.nbl.bak").mkdir(), "block backup rotation");
        Files.write(new File(directory, "library.nbl.bak/obstruction").toPath(), new byte[] {1});
        byte[] committed = bytes(directory, "library.nbl");
        expectIo(() -> blocked.save(entries("Renamed", "New", "Third"), 3));
        check(Arrays.equals(committed, bytes(directory, "library.nbl")), "rotation failure keeps current catalog");
        state(new NotebookCatalog(directory).load(), 1, "Renamed", "New");
        deleteTree(new File(directory, "library.nbl.bak"));
        blocked.load();
        state(blocked.save(entries("Renamed", "New", "Third"), 3), 3, "Renamed", "New", "Third");
        check(new File(directory, "library.nbl").delete(), "remove primary for sync failure");
        check(new File(directory, "library.nbl").mkdir(), "replace primary by directory");
        expectIo(() -> blocked.sync());
        expectIo(() -> blocked.save(entries("Renamed", "New", "Third"), 1));
    }

    private static void maximumCatalog() throws Exception {
        int[] ids = new int[200];
        byte[][] names = new byte[200][];
        for (int i = 0; i < ids.length; i++) { ids[i] = i + 1; names[i] = utf(repeat("📓", 80)); }
        File directory = directory();
        Files.write(new File(directory, "library.nbl").toPath(), fixture(ids, names, 200));
        NotebookCatalog catalog = new NotebookCatalog(directory);
        NotebookCatalog.State state = catalog.load();
        check(state.entries.size() == 200 && state.selectedId == 200, "largest catalog loads bounded names");
        catalog.save(state.entries, 1);
        check(new NotebookCatalog(directory).load().selectedId == 1, "largest catalog selection persists");
    }

    private static NotebookCatalog.Entry entry(int id, String name) { return new NotebookCatalog.Entry(id, name); }
    private static List<NotebookCatalog.Entry> entries(String... names) {
        List<NotebookCatalog.Entry> entries = new ArrayList<NotebookCatalog.Entry>();
        for (int i = 0; i < names.length; i++) entries.add(entry(i + 1, names[i]));
        return entries;
    }

    private static void state(NotebookCatalog.State state, int selected, String... names) {
        check(state.selectedId == selected, "selected notebook");
        check(state.entries.equals(entries(names)), "notebook IDs and names");
    }

    private static byte[] fixture(int[] ids, byte[][] names, int selected) throws IOException {
        ByteArrayOutputStream payloadBytes = new ByteArrayOutputStream();
        DataOutputStream payload = new DataOutputStream(payloadBytes);
        payload.writeInt(ids.length);
        payload.writeInt(selected);
        for (int i = 0; i < ids.length; i++) {
            payload.writeInt(ids[i]); payload.writeInt(names[i].length); payload.write(names[i]);
        }
        payload.flush();
        byte[] raw = payloadBytes.toByteArray();
        CRC32 crc = new CRC32(); crc.update(raw);
        ByteArrayOutputStream fileBytes = new ByteArrayOutputStream();
        DataOutputStream file = new DataOutputStream(fileBytes);
        file.writeInt(0x4e424c31); file.writeInt(1); file.writeInt(raw.length); file.writeInt(~raw.length);
        file.write(raw); file.writeInt((int) crc.getValue()); file.flush();
        return fileBytes.toByteArray();
    }

    private static void unchanged(File directory, byte[] primary, byte[] backup, byte[] temp) throws IOException {
        check(Arrays.equals(primary, bytes(directory, "library.nbl")), "invalid save retains primary");
        check(Arrays.equals(backup, bytes(directory, "library.nbl.bak")), "invalid save retains backup");
        check(Arrays.equals(temp, bytes(directory, "library.nbl.tmp")), "invalid save retains temporary");
    }

    private static byte[] utf(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static String repeat(String value, int count) { StringBuilder text = new StringBuilder(); for (int i = 0; i < count; i++) text.append(value); return text.toString(); }
    private static File directory() throws IOException { File file = Files.createTempDirectory("notebook-catalog-").toFile(); directories.add(file); return file; }
    private static byte[] bytes(File directory, String name) throws IOException { return Files.readAllBytes(new File(directory, name).toPath()); }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private interface Action { void run() throws Exception; }
    private static void expectIo(Action action) throws Exception { try { action.run(); } catch (IOException expected) { assertions++; return; } throw new AssertionError("Expected IOException"); }
    private static void expectArgument(Action action) throws Exception { try { action.run(); } catch (IllegalArgumentException expected) { assertions++; return; } throw new AssertionError("Expected IllegalArgumentException"); }
    private static void deleteTree(File file) { File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child); file.delete(); }
}
