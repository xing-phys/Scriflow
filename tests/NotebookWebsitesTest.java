package org.eink.inkprobe.notebook;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Real-file snapshot publication, restart, failure, path isolation and URL policy regressions. */
public final class NotebookWebsitesTest {
    private static int assertions;
    private static final List<File> roots = new ArrayList<>();
    private interface Task { void run() throws Exception; }
    private static final byte[] ARCHIVE = ("From: <Saved by Chromium>\r\nMIME-Version: 1.0\r\n"
        + "Content-Type: multipart/related; boundary=\"test-page\"\r\n\r\n--test-page\r\n"
        + "Content-Type: text/html; charset=utf-8\r\nContent-Location: https://example.org/article\r\n\r\n"
        + "<html><body>Offline physics notes</body></html>\r\n--test-page--\r\n").getBytes(StandardCharsets.UTF_8);

    public static void main(String[] args) throws Exception {
        try {
            addresses(); publicationAndRestart(); failedSaves(); corruptionIsIsolated(); deletionAndPaths(); abandonedSaves();
            System.out.println("NotebookWebsites: " + assertions + " assertions passed (real files, atomic snapshots, restart, path isolation and HTTPS addresses).");
        } finally { for (File root : roots) remove(root); }
    }

    private static void addresses() {
        equal("https://example.org/", NotebookBrowserAddress.normalize(" example.org "), "bare host defaults to HTTPS");
        equal("https://example.org/", NotebookBrowserAddress.normalize("HTTPS://EXAMPLE.ORG"), "scheme and host normalized");
        equal("https://example.org/a%2Fb?q=a%20b#section", NotebookBrowserAddress.normalize("//example.org/a%2Fb?q=a b#section"), "escapes and fragments retained");
        equal("https://xn--fsqu00a.xn--0zwm56d/", NotebookBrowserAddress.normalize("例子.测试"), "international domain converted with IDN");
        equal("https://[::1]:8443/a", NotebookBrowserAddress.normalize("https://[::1]:8443/a"), "IPv6 and custom ports supported");
        equal("https://example.org/%E7%89%A9%E7%90%86", NotebookBrowserAddress.normalize("example.org/物理"), "Unicode paths escaped");
        for (String value : Arrays.asList("", " ", "http://example.org", "file:///etc/passwd", "content://private/item",
                "javascript:alert(1)", "data:text/html,<script>alert(1)</script>", "intent://example.org", "ftp://example.org",
                "https://user:pass@example.org", "https://example.org:0", "https://example.org:65536", "https://example.org:",
                "https://", "https://bad..host", "https://a%2fb.example.org", "https://example.org\n/path", "https://example.org\\@private")) {
            try { NotebookBrowserAddress.normalize(value); throw new AssertionError("Accepted " + value); }
            catch (IllegalArgumentException expected) { assertions++; }
        }
        for (String value : Arrays.asList("example.org", "//example.org", "http://example.org", "file:///tmp/page.mht", null))
            check(!NotebookBrowserAddress.isWebUrl(value), "navigation never adds a scheme or permits local/cleartext URLs");
        check(NotebookBrowserAddress.isWebUrl("https://example.org/article"), "absolute HTTPS navigation allowed");
        for (String scheme : Arrays.asList("data", "blob", "cid", "about"))
            check(NotebookBrowserAddress.isEmbeddedScheme(scheme), "inline archive resources can render without network or private-file access");
        for (String scheme : Arrays.asList("file", "content", "https", "http", "intent", null))
            check(!NotebookBrowserAddress.isEmbeddedScheme(scheme), "files, content providers and network URLs are never classified as embedded resources");
        equal("https://example.org/?q=%23x", NotebookBrowserAddress.withoutFragment("https://example.org/?q=%23x#heading"), "fragment removal retains escaped hash");
    }

    private static void publicationAndRestart() throws Exception {
        File root = root(); NotebookWebsites store = new NotebookWebsites(root);
        Files.write(new File(root, "page.nbk").toPath(), new byte[]{4, 7, 2});
        check(store.list().isEmpty() && !new File(root, "websites").exists(), "empty folder lookup makes no files");
        NotebookWebsites.Pending pending = store.begin();
        Files.write(pending.archive.toPath(), ARCHIVE);
        check(store.list().isEmpty(), "archive bytes remain invisible until metadata publication");
        NotebookWebsites.Saved first = store.finish(pending, "物理 · Quantum notes", "https://example.org/article");
        check(!pending.directory.exists() && store.list().size() == 1, "archive and metadata are published together");
        check(first.bytes == ARCHIVE.length && first.savedAt > 0, "snapshot records byte size and timestamp");
        NotebookWebsites cold = new NotebookWebsites(root);
        NotebookWebsites.Saved restored = cold.get(first.id);
        equal(first.title, restored.title, "Unicode title survives restart");
        equal(first.url, restored.url, "source URL survives restart");
        check(Arrays.equals(ARCHIVE, Files.readAllBytes(cold.archive(restored).toPath())), "archive bytes survive restart unchanged");
        NotebookWebsites.Saved second = save(store, "Second copy", first.url);
        check(!first.id.equals(second.id) && store.list().size() == 2, "saving again preserves independent snapshots");
        metadata(new File(root, "websites/" + first.id + "/website.nws"), "Earlier", first.url, 10, ARCHIVE.length);
        equal(second.id, store.list().get(0).id, "newest snapshot listed first");
        check(Arrays.equals(new byte[]{4, 7, 2}, Files.readAllBytes(new File(root, "page.nbk").toPath())), "website operations preserve notebook bytes");
    }

    private static void failedSaves() throws Exception {
        File root = root(); NotebookWebsites store = new NotebookWebsites(root);
        NotebookWebsites.Pending pending = store.begin();
        expectIo(() -> store.finish(pending, "Empty", "https://example.org"));
        check(store.list().isEmpty(), "missing native archive cannot publish an entry");
        Files.write(pending.archive.toPath(), new byte[0]);
        expectIo(() -> store.finish(pending, "Empty", "https://example.org"));
        Files.write(pending.archive.toPath(), "not a web archive".getBytes(StandardCharsets.UTF_8));
        expectIo(() -> store.finish(pending, "Invalid", "https://example.org"));
        Files.write(pending.archive.toPath(), ARCHIVE);
        expectIo(() -> store.finish(pending, "Invalid source", "file:///private"));
        File blocked = new File(pending.directory, "website.nws"); check(blocked.mkdir(), "simulate a metadata write failure");
        expectIo(() -> store.finish(pending, "Cannot publish", "https://example.org"));
        check(new NotebookWebsites(root).list().isEmpty(), "failed metadata save remains invisible after restart");
        store.abandon(pending); check(!pending.directory.exists(), "failed saves can be removed");
        NotebookWebsites.Saved fallback = save(store, "  ", "https://example.org/article");
        equal("https://example.org/article", fallback.title, "untitled pages use their address");
        File invalidRoot = root(); Files.write(new File(invalidRoot, "websites").toPath(), new byte[]{1});
        expectIo(() -> new NotebookWebsites(invalidRoot).begin());
    }

    private static void corruptionIsIsolated() throws Exception {
        File root = root(); NotebookWebsites store = new NotebookWebsites(root);
        NotebookWebsites.Saved good = save(store, "Keep", "https://example.org/keep");
        NotebookWebsites.Saved bad = save(store, "Damaged", "https://example.org/bad");
        File directory = new File(root, "websites/" + bad.id), metadata = new File(directory, "website.nws");
        Files.write(new File(directory, "page.mht").toPath(), new byte[]{1});
        expectIo(() -> store.get(bad.id));
        check(store.list().size() == 1 && store.list().get(0).id.equals(good.id), "truncated archive cannot hide healthy snapshots");
        Files.write(new File(directory, "page.mht").toPath(), ARCHIVE);
        metadata(metadata, "Bad source", "javascript:alert(1)", 10, ARCHIVE.length); expectIo(() -> store.get(bad.id));
        metadata(metadata, "Trailing garbage", "https://example.org", 10, ARCHIVE.length);
        try (FileOutputStream extra = new FileOutputStream(metadata, true)) { extra.write(7); }
        expectIo(() -> store.get(bad.id));
        Files.write(metadata.toPath(), new byte[]{0, 0, 0, 1}); expectIo(() -> store.get(bad.id));
        check(store.list().size() == 1 && metadata.exists(), "unknown metadata stays intact for recovery");
    }

    private static void deletionAndPaths() throws Exception {
        File root = root(), outside = root(); NotebookWebsites store = new NotebookWebsites(root);
        File keep = new File(outside, "keep"); Files.write(keep.toPath(), new byte[]{8});
        NotebookWebsites.Saved first = save(store, "Remove", "https://example.org/a");
        NotebookWebsites.Saved second = save(store, "Keep", "https://example.org/b");
        store.delete(first.id); store.delete(first.id);
        check(store.list().size() == 1 && store.get(second.id).title.equals("Keep"), "deletion is isolated and repeatable");
        NotebookWebsites.Saved interrupted = save(store, "Interrupted deletion", "https://example.org/old");
        File tombstone = new File(root, "websites/.deleting-" + interrupted.id);
        check(new File(root, "websites/" + interrupted.id).renameTo(tombstone), "simulate process death after deletion publication");
        new NotebookWebsites(root).delete(interrupted.id);
        check(!tombstone.exists() && store.get(second.id).title.equals("Keep"), "deletion retry completes cleanup after process restart");
        for (String id : Arrays.asList("..", "../../outside", "", null, second.id + "/..")) {
            expectIo(() -> store.get(id)); expectIo(() -> store.delete(id));
        }
        String linkId = UUID.randomUUID().toString();
        Files.createSymbolicLink(new File(root, "websites/" + linkId).toPath(), outside.toPath());
        expectIo(() -> store.delete(linkId)); expectIo(() -> store.get(linkId));
        check(keep.exists() && store.list().size() == 1, "directory symlink cannot expose or delete outside data");
        NotebookWebsites.Pending pending = store.begin();
        Files.createSymbolicLink(pending.archive.toPath(), keep.toPath());
        expectIo(() -> store.finish(pending, "Unsafe", "https://example.org"));
        check(Arrays.equals(new byte[]{8}, Files.readAllBytes(keep.toPath())), "saving refuses an archive symlink without touching its target");
        File linkedRoot = root(); Files.createSymbolicLink(new File(linkedRoot, "websites").toPath(), outside.toPath());
        expectIo(() -> new NotebookWebsites(linkedRoot).list());
    }

    private static void abandonedSaves() throws Exception {
        File root = root(), outside = root(); NotebookWebsites store = new NotebookWebsites(root);
        NotebookWebsites.Pending old = store.begin(); Files.write(old.archive.toPath(), ARCHIVE);
        File keep = new File(outside, "keep"); Files.write(keep.toPath(), new byte[]{9});
        Files.createSymbolicLink(new File(old.directory, "link").toPath(), outside.toPath());
        check(old.directory.setLastModified(1), "simulate an old interrupted save");
        NotebookWebsites.Pending fresh = store.begin();
        check(!old.directory.exists() && keep.exists(), "old partial save cleanup does not follow nested links");
        NotebookWebsites.Pending other = store.begin();
        check(fresh.directory.exists(), "new cleanup preserves an active save");
        File unrelated = new File(root, "websites/.saving-unrelated"); check(unrelated.mkdir(), "create unrelated directory"); unrelated.setLastModified(1);
        store.abandon(other); store.begin(); check(unrelated.exists(), "cleanup only recognizes owned UUID paths");
    }

    private static NotebookWebsites.Saved save(NotebookWebsites store, String title, String url) throws Exception {
        NotebookWebsites.Pending pending = store.begin(); Files.write(pending.archive.toPath(), ARCHIVE); return store.finish(pending, title, url);
    }
    private static void metadata(File file, String title, String url, long timestamp, long bytes) throws Exception {
        try (DataOutputStream output = new DataOutputStream(new FileOutputStream(file))) {
            output.writeInt(0x4e575331); output.writeUTF(title); output.writeUTF(url); output.writeLong(timestamp); output.writeLong(bytes);
        }
    }
    private static File root() throws Exception { File root = Files.createTempDirectory("scriflow-websites-").toFile(); roots.add(root); return root; }
    private static void expectIo(Task task) throws Exception { try { task.run(); throw new AssertionError("Expected IO failure"); } catch (IOException expected) { assertions++; } }
    private static void equal(String expected, String value, String detail) { check(expected.equals(value), detail + ": " + value); }
    private static void check(boolean condition, String detail) { assertions++; if (!condition) throw new AssertionError(detail); }
    private static void remove(File file) throws Exception {
        if (!Files.isSymbolicLink(file.toPath()) && file.isDirectory()) {
            File[] children = file.listFiles(); if (children != null) for (File child : children) remove(child);
        }
        Files.deleteIfExists(file.toPath());
    }
}
