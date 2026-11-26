package org.eink.inkprobe.notebook;

public final class NotebookDownloadNameTest {
    private static int checks;
    private static void equal(Object expected, Object actual, String message) {
        checks++; if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
    }
    public static void main(String[] args) {
        equal("paper.pdf", NotebookDownloadName.choose("https://host/file", "attachment; filename=paper.pdf"), "ordinary filename");
        equal("a; b.pdf", NotebookDownloadName.choose("https://host/file", "attachment; filename=\"a; b.pdf\""), "quoted semicolon");
        equal("量子+笔记.pdf", NotebookDownloadName.choose("https://host/file", "attachment; filename=old.pdf; filename*=UTF-8''%E9%87%8F%E5%AD%90%2B%E7%AC%94%E8%AE%B0.pdf"), "UTF-8 filename takes precedence");
        equal("a+b.pdf", NotebookDownloadName.choose("https://host/a%2Bb.pdf?q=secret", null), "URL decoding retains plus");
        equal(".._outside.pdf", NotebookDownloadName.choose("https://host/file", "attachment; filename=\"../outside.pdf\""), "path separators cannot escape Downloads");
        equal("download", NotebookDownloadName.clean(".."), "invalid dot-only name");
        equal("ab.pdf", NotebookDownloadName.clean("a\u202eb.pdf"), "remove direction controls");
        StringBuilder longSource = new StringBuilder(); for (int i=0;i<150;i++) longSource.append("量");
        String longName = NotebookDownloadName.clean(longSource + ".pdf");
        if (longName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 200 || !longName.endsWith(".pdf")) throw new AssertionError("bounded Unicode filename"); checks++;
        equal("application/pdf", NotebookDownloadName.mime("Application/PDF; charset=UTF-8"), "normalize MIME");
        equal("application/octet-stream", NotebookDownloadName.mime("bad\nvalue"), "reject malformed MIME");
        equal(42L, NotebookDownloadName.length("42", null), "valid length");
        equal(-1L, NotebookDownloadName.length("42", "gzip"), "encoded length is not the decoded size");
        equal(-1L, NotebookDownloadName.length("-1", null), "negative length is unknown");
        System.out.println("NotebookDownloadName: " + checks + " assertions passed.");
    }
}
