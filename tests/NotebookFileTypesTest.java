package org.eink.inkprobe.notebook;

/** File-type badges must distinguish ordinary formats without trusting filenames. */
public final class NotebookFileTypesTest {
    public static void main(String[] args) {
        check("paper.PDF", "PDF"); check("book.epub", "EPUB"); check("book.AZW3", "AZW3");
        check("book.fb2.zip", "FB2Z"); check("archive.zip", "ZIP");
        check("picture.JPEG", "JPG"); check("note.markdown", "MD"); check("paper.djvu", "DJVU");
        check("README", "FILE"); check(".hidden", "FILE"); check(".hidden.txt", "TXT");
        check("document.", "FILE"); check("document.verylongextension", "FILE");
        check("document.✎", "FILE"); check(null, "FILE");
        System.out.println("NotebookFileTypes: 15 assertions passed (book formats, case, compound extensions and fallback).");
    }
    private static void check(String name, String expected) {
        String actual = NotebookFileTypes.label(name);
        if (!expected.equals(actual)) throw new AssertionError(name + ": expected " + expected + ", got " + actual);
    }
}
