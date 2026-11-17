package org.eink.inkprobe.notebook;

import com.tom_roush.pdfbox.cos.*;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.io.RandomAccessBufferedFileInputStream;
import com.tom_roush.pdfbox.io.ScratchFile;
import com.tom_roush.pdfbox.pdfparser.PDFParser;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.*;

/** Read-only PDFBox session. Page geometry/navigation never load images, fonts or attachments.
 * Export deliberately uses the complete PDDocument loader instead of this partial document. */
final class NotebookPdfReader extends PDFParser implements Closeable {
    private static final int MAX_METADATA_OBJECTS = 200_000;
    private static final Set<COSName> PAGE_KEYS = names("Type", "Pages", "Kids", "Parent", "Count",
        "MediaBox", "CropBox", "Rotate");
    private static final Set<COSName> DESTINATION_KEYS = names("Dests", "Names", "Kids", "Limits", "D");
    private static final Set<COSName> OUTLINE_KEYS = names("First", "Next", "Title", "Dest", "A", "D", "S");
    private static final Set<COSName> LINK_KEYS = names("Subtype", "Rect", "Dest", "A", "S", "D", "URI", "N");
    private PDDocument pdf;

    private NotebookPdfReader(RandomAccessBufferedFileInputStream input, ScratchFile scratch) throws IOException {
        super(input, scratch);
    }

    static NotebookPdfReader open(File source, File temporaryDirectory) throws IOException {
        MemoryUsageSetting memory = MemoryUsageSetting.setupMixed(2L * 1024 * 1024);
        if (temporaryDirectory != null) memory.setTempDir(temporaryDirectory);
        RandomAccessBufferedFileInputStream input = new RandomAccessBufferedFileInputStream(source);
        ScratchFile scratch = null;
        try {
            scratch = new ScratchFile(memory);
            NotebookPdfReader reader = new NotebookPdfReader(input, scratch);
            reader.parse();
            reader.pdf = reader.getPDDocument();
            return reader;
        } catch (IOException | RuntimeException | Error failure) {
            try { if (scratch != null) scratch.close(); } catch (IOException ignored) { }
            try { input.close(); } catch (IOException ignored) { }
            throw failure;
        }
    }

    PDDocument pdf() { return pdf; }

    /** Keep the upstream trailer/xref, repair, decryption and page-tree validation code.
     * Its normal initial parse recursively reads the entire catalog; select only what we use. */
    @Override protected void parseDictObjects(COSDictionary dictionary, COSName... excluded) throws IOException {
        if (dictionary == document.getTrailer().getDictionaryObject(COSName.ROOT)) {
            graph(dictionary.getItem(COSName.PAGES), PAGE_KEYS);
            graph(dictionary.getItem(COSName.NAMES), DESTINATION_KEYS);
            COSBase legacy = resolve(dictionary.getItem(COSName.DESTS));
            if (legacy instanceof COSDictionary)
                for (COSBase value : ((COSDictionary) legacy).getValues()) graph(value, DESTINATION_KEYS);
            graph(dictionary.getItem(COSName.OUTLINES), OUTLINE_KEYS);
            graph(dictionary.getItem(COSName.URI), names("Base"));
        } else graph(dictionary, names("Title"));
    }

    void pageLinks(PDPage page) throws IOException {
        graph(page.getCOSObject().getItem(COSName.ANNOTS), LINK_KEYS);
    }

    /** Decode content and inherited resources only for the page explicitly being selected. */
    void pageText(PDPage page) throws IOException {
        COSDictionary selected = new COSDictionary();
        selected.setItem(COSName.CONTENTS, page.getCOSObject().getItem(COSName.CONTENTS));
        Set<COSBase> seen = Collections.newSetFromMap(new IdentityHashMap<COSBase, Boolean>());
        COSDictionary owner = page.getCOSObject();
        while (owner != null && seen.add(owner)) {
            COSBase resources = owner.getItem(COSName.RESOURCES);
            if (resources != null) { selected.setItem(COSName.RESOURCES, resources); break; }
            COSBase parent = resolve(owner.getItem(COSName.PARENT));
            owner = parent instanceof COSDictionary ? (COSDictionary) parent : null;
        }
        super.parseDictObjects(selected);
    }

    private COSBase resolve(COSBase value) throws IOException {
        int references = 0;
        while (value instanceof COSObject) {
            if (++references > 32) throw new IOException("The PDF contains a circular object reference");
            value = parseObjectDynamically((COSObject) value, false);
        }
        return value;
    }

    private void graph(COSBase start, Set<COSName> keys) throws IOException {
        if (start == null) return;
        Deque<COSBase> queue = new ArrayDeque<>(); queue.add(start);
        Set<COSBase> seen = Collections.newSetFromMap(new IdentityHashMap<COSBase, Boolean>());
        while (!queue.isEmpty()) {
            COSBase value = queue.removeFirst();
            if (!seen.add(value)) continue;
            if (seen.size() > MAX_METADATA_OBJECTS) throw new IOException("This PDF has too much navigation metadata");
            if (value instanceof COSObject) {
                COSBase resolved = resolve(value);
                if (resolved != null) queue.addLast(resolved);
            } else if (value instanceof COSDictionary) {
                COSDictionary dictionary = (COSDictionary) value;
                for (COSName key : keys) {
                    COSBase child = dictionary.getItem(key);
                    if (child != null) queue.addLast(child);
                }
            } else if (value instanceof COSArray) {
                for (COSBase child : (COSArray) value) if (child != null) queue.addLast(child);
            }
        }
    }

    private static Set<COSName> names(String... values) {
        Set<COSName> result = new HashSet<>();
        for (String value : values) result.add(COSName.getPDFName(value));
        return result;
    }

    @Override public void close() throws IOException { pdf.close(); }
}
