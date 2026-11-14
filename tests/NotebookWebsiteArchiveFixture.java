package org.eink.inkprobe.notebook;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Bridge a real Chromium-generated archive through production storage and a cold reopen. */
public final class NotebookWebsiteArchiveFixture {
    public static void main(String[] args) throws Exception {
        File root = new File(args[0]), source = new File(args[1]);
        NotebookWebsites store = new NotebookWebsites(root);
        NotebookWebsites.Pending pending = store.begin();
        Files.copy(source.toPath(), pending.archive.toPath(), StandardCopyOption.REPLACE_EXISTING);
        NotebookWebsites.Saved entry = store.finish(pending, "Offline quantum notes", "https://example.org/article");
        NotebookWebsites cold = new NotebookWebsites(root);
        if (cold.list().size() != 1) throw new AssertionError("Snapshot missing after cold reopen");
        System.out.println(cold.archive(cold.get(entry.id)).getAbsolutePath());
    }
}
