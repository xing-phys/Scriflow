package org.eink.inkprobe.notebook;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Real files and failure injection, including migration, portable ink and restart recovery. */
public final class NotebookFoundationTest {
    private static int assertions;
    private static final List<File> roots = new ArrayList<>();
    interface Action { void run() throws Exception; }
    public static void main(String[] args) throws Exception {
        try {
            migrationAndIdentity(); backupRoundTrip(); rejectMalformed(); interruptedRestore(); workerIsolation(); transforms();
            System.out.println("NotebookFoundation: " + assertions + " assertions passed (host JVM, real files).");
        } finally { for (File root : roots) NotebookBackup.deleteTree(root); }
    }
    private static File root() throws IOException {
        File dir = Files.createTempDirectory("ink-foundation-").toFile(); roots.add(dir); return dir;
    }
    private static NotebookPage.Stroke stroke() {
        return new NotebookPage.Stroke(2, 4.2f, 0xff123456, Arrays.asList(
            new NotebookPage.Point(10, 20, 100, .2f), new NotebookPage.Point(35, 40, 110, 1.6f)));
    }
    private static NotebookLibrary ready(File root) throws IOException {
        NotebookLibrary library = new NotebookLibrary(root); library.load(); library.initializeSize(100, 120); return library;
    }
    private static byte[] archive(NotebookLibrary library) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NotebookBackup.write(bytes, library.notebookName(), library.pageCount(), library::previewPage, () -> false);
        return bytes.toByteArray();
    }
    private static NotebookBackup.Restored read(byte[] bytes, File parent) throws IOException {
        return NotebookBackup.read(new ByteArrayInputStream(bytes), new File(parent, "stage-" + UUID.randomUUID()), () -> false);
    }
    private static void migrationAndIdentity() throws Exception {
        File root = root();
        // Fixed v1 encoder independent of production storage, including pressure/event time.
        ByteArrayOutputStream payload = new ByteArrayOutputStream(); DataOutputStream p = new DataOutputStream(payload);
        p.writeByte(1); p.writeInt(100); p.writeInt(120); p.writeByte(1); p.writeInt(1);
        NotebookPage.Stroke original = stroke();
        p.writeInt(original.penType); p.writeFloat(original.width); p.writeInt(original.color); p.writeInt(2);
        for (NotebookPage.Point point : original.points) {
            p.writeFloat(point.x); p.writeFloat(point.y); p.writeLong(point.time); p.writeFloat(point.pressure);
        }
        byte[] record = payload.toByteArray(); CRC32 crc = new CRC32(); crc.update(record);
        ByteArrayOutputStream file = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(file);
        out.writeInt(0x4e424b31); out.writeInt(1); out.writeInt(record.length); out.writeInt(~record.length);
        out.write(record); out.writeInt((int)crc.getValue());
        File pageFile = new File(root, "page.nbk"); Files.write(pageFile.toPath(), file.toByteArray());
        NotebookStore store = new NotebookStore(root); NotebookPage before = store.load();
        NotebookPage again = new NotebookStore(root).load();
        check(before.id.equals(again.id), "legacy page identity stable between reads");
        check(before.strokes.get(0).id.equals(again.strokes.get(0).id), "legacy stroke identity stable");
        check(Arrays.equals(file.toByteArray(), Files.readAllBytes(pageFile.toPath())), "read never migrates legacy file");
        store.appendStroke(stroke()); NotebookPage migrated = new NotebookStore(root).load();
        check(before.id.equals(migrated.id), "first edit persists original page ID");
        check(before.strokes.get(0).id.equals(migrated.strokes.get(0).id), "migration retains stroke ID");
        check(Arrays.equals(file.toByteArray(), Files.readAllBytes(new File(root, "page.nbk.bak").toPath())), "migration retains v1 predecessor");
        NotebookDocument doc = new NotebookDocument(root); doc.load(); UUID id = doc.current().strokes.get(1).id;
        doc.undo(); // History starts empty after reopening.
        doc.erase(Collections.singletonList(new NotebookPage.Point(10,20,200,1)), 4); doc.undo();
        check(doc.current().strokes.get(1).id.equals(id), "erase undo keeps object identity");
        byte[] committed = Files.readAllBytes(pageFile.toPath());
        expect(() -> store.appendStroke(migrated.strokes.get(0)));
        check(Arrays.equals(committed, Files.readAllBytes(pageFile.toPath())), "duplicate ID cannot change disk");
        // Newer schema fails closed instead of silently producing a blank page.
        byte[] future = committed.clone(); future[7] = 99; Files.write(pageFile.toPath(), future);
        expect(() -> new NotebookStore(root).load());
        check(Arrays.equals(future, Files.readAllBytes(pageFile.toPath())), "future format preserved");
    }
    private static void backupRoundTrip() throws Exception {
        File root = root(); NotebookLibrary source = ready(root);
        source.renameNotebook(1, "研究 · notes"); source.append(stroke()); source.addPage(); source.setTemplate(NotebookPage.Template.LINED); source.append(stroke());
        NotebookPage first = source.previewPage(1), second = source.previewPage(2);
        byte[] before = Files.readAllBytes(new File(root, "page.nbk").toPath()); byte[] bytes = archive(source);
        check(Arrays.equals(before, Files.readAllBytes(new File(root, "page.nbk").toPath())), "backup is read only");
        NotebookBackup.Restored restored = read(bytes, root());
        check(restored.name.equals("研究 · notes") && restored.pageCount == 2, "Unicode name and page count portable");
        File target = root(); NotebookLibrary library = ready(target); library.append(stroke());
        UUID existing = library.current().id; byte[] existingBytes = Files.readAllBytes(new File(target, "page.nbk").toPath());
        library.importNotebook(restored); library.load();
        check(library.entries().size() == 2 && library.notebookId() == 2, "restore adds separate selected book");
        check(Arrays.equals(existingBytes, Files.readAllBytes(new File(target, "page.nbk").toPath())), "restore never rewrites original notebook");
        compare(first, library.previewPage(1)); compare(second, library.previewPage(2));
        library.openNotebook(1); check(existing.equals(library.current().id), "old book and identity retained");
        check(library.canUndo(), "restore retains existing notebook history");
        library.openNotebook(2); library.append(stroke()); library.undo(); library.redo();
        check(library.current().strokes.size() == 2, "restored notebook remains editable");
        NotebookLibrary cold = new NotebookLibrary(target); cold.load(); check(cold.current().strokes.size() == 2, "restored edits survive restart");
        File empty = root(); NotebookLibrary emptyBook = new NotebookLibrary(empty); emptyBook.load();
        NotebookBackup.Restored unsized = read(archive(emptyBook), root());
        check(!new NotebookStore(unsized.directory).load().isInitialized(), "unsized new notebook portable");
    }
    private static void compare(NotebookPage a, NotebookPage b) {
        check(a.id.equals(b.id) && a.width == b.width && a.height == b.height && a.template == b.template, "page metadata round trip");
        check(a.strokes.size() == b.strokes.size(), "stroke count round trip");
        for (int i = 0; i < a.strokes.size(); i++) {
            NotebookPage.Stroke x = a.strokes.get(i), y = b.strokes.get(i);
            check(x.id.equals(y.id) && x.penType == y.penType && x.color == y.color && x.width == y.width, "stroke identity/style round trip");
            check(x.points.size() == y.points.size(), "sample count round trip");
            for (int j = 0; j < x.points.size(); j++) {
                NotebookPage.Point p=x.points.get(j), q=y.points.get(j);
                check(p.x==q.x && p.y==q.y && p.time==q.time && p.pressure==q.pressure, "exact pressure/time round trip");
            }
        }
    }
    private static void rejectMalformed() throws Exception {
        NotebookLibrary source = ready(root()); source.append(stroke()); byte[] good = archive(source);
        for (int cut : new int[]{0, 8, 32, good.length/2}) {
            File stage = new File(root(), "stage");
            expect(() -> NotebookBackup.read(new ByteArrayInputStream(Arrays.copyOf(good, cut)), stage, () -> false));
            check(!stage.exists(), "truncated backup cleans staging");
        }
        File cancelled = new File(root(), "stage");
        expect(() -> NotebookBackup.read(new ByteArrayInputStream(good), cancelled, () -> true));
        check(!cancelled.exists(), "cancel cleans staging");
        ByteArrayOutputStream malicious = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(malicious)) {
            zip.putNextEntry(new ZipEntry("../escape")); zip.write(1); zip.closeEntry();
        }
        File parent = root(); expect(() -> read(malicious.toByteArray(), parent));
        check(parent.list().length == 0 && !new File(parent, "escape").exists(), "unexpected paths never extracted");
        // Inner checksum corruption is checked even when the outer ZIP CRC is valid.
        ByteArrayOutputStream corrupt = new ByteArrayOutputStream();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(good)); ZipOutputStream zip = new ZipOutputStream(corrupt)) {
            ZipEntry entry; byte[] buffer = new byte[1024];
            while ((entry = input.getNextEntry()) != null) {
                ByteArrayOutputStream contents = new ByteArrayOutputStream(); int n;
                while ((n = input.read(buffer)) != -1) contents.write(buffer,0,n);
                byte[] bytes=contents.toByteArray(); if (entry.getName().endsWith(".nbk")) bytes[bytes.length-1]^=1;
                zip.putNextEntry(new ZipEntry(entry.getName())); zip.write(bytes); zip.closeEntry();
            }
        }
        expect(() -> read(corrupt.toByteArray(), root()));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        expect(() -> NotebookBackup.write(output,"Notes",1,source::previewPage,()->true));
    }
    private static void interruptedRestore() throws Exception {
        NotebookLibrary source = ready(root()); source.append(stroke()); byte[] bytes = archive(source);
        File target = root(); NotebookLibrary library = ready(target); library.append(stroke());
        library.renameNotebook(1, "Original");
        byte[] original = Files.readAllBytes(new File(target,"page.nbk").toPath());
        File block = new File(target,"library.nbl.tmp"); check(block.mkdir(), "inject catalog failure");
        expect(() -> library.importNotebook(read(bytes, root())));
        check(new File(target,"restore.pending").isFile(), "restore intent survives failed catalog publish");
        check(new File(target,"notebooks/2/page.nbk").isFile(), "restored content retained for recovery");
        check(block.delete(), "remove failure");
        NotebookLibrary restarted = new NotebookLibrary(target); restarted.load();
        check(restarted.entries().size()==2 && restarted.notebookId()==2, "restart completes restore exactly once");
        check(!new File(target,"restore.pending").exists(), "completed intent removed");
        check(Arrays.equals(original, Files.readAllBytes(new File(target,"page.nbk").toPath())), "restart recovery preserves original ink");
        NotebookLibrary another = new NotebookLibrary(target); another.load();
        check(another.entries().size()==2, "second restart cannot duplicate restored notebook");
        File orphan = new File(target,"notebooks/3"); check(orphan.mkdirs(), "inject unrelated target");
        Files.write(new File(orphan,"keep").toPath(), new byte[]{42});
        expect(() -> another.importNotebook(read(bytes, root())));
        check(new File(orphan,"keep").length()==1, "unlisted files never overwritten by restore");
    }
    private static void workerIsolation() throws Exception {
        NotebookTasks tasks = new NotebookTasks(); CountDownLatch hold = new CountDownLatch(1), started = new CountDownLatch(1);
        try {
            tasks.render.execute(() -> { started.countDown(); try { hold.await(); } catch (InterruptedException ignored) { } });
            check(started.await(2,TimeUnit.SECONDS), "render started");
            tasks.exports.execute(() -> { try { hold.await(); } catch (InterruptedException ignored) { } });
            check(tasks.writer.submit(() -> 42).get(2,TimeUnit.SECONDS)==42, "blocked rendering/export cannot block writer");
            for(int i=0;i<8;i++) tasks.render.execute(()->{});
            try { tasks.render.execute(()->{}); throw new AssertionError("unbounded render queue"); }
            catch(RejectedExecutionException expected) { check(true,"render queue bounded"); }
        } finally { hold.countDown(); tasks.shutdown(); }
    }
    private static void transforms() {
        NotebookPage.Point source = new NotebookPage.Point(30,40,1234,.7f);
        NotebookTransform[] transforms = {NotebookTransform.fit(.5f,0,-80),
            new NotebookTransform(0,2,-2,0,800,12), new NotebookTransform(1,.2f,.1f,1,90,12)};
        for (NotebookTransform transform : transforms) {
            NotebookPage.Point restored = transform.inverse().map(transform.map(source));
            check(Math.abs(restored.x-source.x)<.0001 && Math.abs(restored.y-source.y)<.0001, "crop/rotation/scale inverse");
            check(restored.time==source.time && restored.pressure==source.pressure, "transform preserves pen samples");
        }
        try { new NotebookTransform(0,0,0,0,0,0).inverse(); throw new AssertionError("singular transform"); }
        catch (IllegalArgumentException expected) { assertions++; }
    }
    private static void expect(Action action) throws Exception {
        try { action.run(); throw new AssertionError("expected IOException"); } catch(IOException expected) { assertions++; }
    }
    private static void check(boolean condition,String message) { assertions++; if(!condition) throw new AssertionError(message); }
}
