package org.eink.inkprobe.notebook;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.cos.COSArray;
import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.cos.COSBase;
import com.tom_roush.pdfbox.cos.COSInteger;
import com.tom_roush.pdfbox.io.MemoryUsageSetting;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.PDPage;
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException;
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDAction;
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI;
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionNamed;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.*;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.*;
import com.tom_roush.pdfbox.util.Matrix;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/** PDF backgrounds and navigation, plus lossless source-content / vector-ink composition. */
public final class NotebookPdfEngine {
    private static final int METADATA_CACHE_SIZE = 3;
    private static final Map<String, NotebookPdfAsset> metadata =
        new LinkedHashMap<String, NotebookPdfAsset>(4, .75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, NotebookPdfAsset> entry) {
                return size() > METADATA_CACHE_SIZE;
            }
        };

    private NotebookPdfEngine() { }

    public static File sourceFile(File directory) { return new File(directory, "source.pdf"); }

    public static void initialize(Context context) {
        PDFBoxResourceLoader.init(context.getApplicationContext());
    }

    public static NotebookPdfAsset inspect(Context context, File source) throws IOException {
        initialize(context);
        return inspect(source);
    }

    /** The caller initializes the resource loader first when running on Android. */
    static NotebookPdfAsset inspect(File source) throws IOException {
        String key = source.getCanonicalPath() + ':' + source.length() + ':' + source.lastModified();
        synchronized (metadata) {
            NotebookPdfAsset cached = metadata.get(key);
            if (cached != null) return cached;
        }
        try (PDDocument document = open(source)) {
            NotebookPdfAsset result = inspect(document);
            synchronized (metadata) { metadata.put(key, result); }
            return result;
        }
    }

    public static List<NotebookPdfAsset.Link> links(Context context, File source, int pdfPage)
            throws IOException {
        NotebookPdfAsset asset = inspect(context, source);
        if (pdfPage < 1 || pdfPage > asset.pages.size()) throw new IOException("The PDF page is unavailable");
        return asset.pages.get(pdfPage - 1).links;
    }

    /** Bounded bitmap for display only. The bitmap never enters an exported PDF. */
    public static Bitmap render(File source, int pdfPage, int width, int height) throws IOException {
        if (width < 1 || height < 1 || (long) width * height > 16_777_216L)
            throw new IOException("The PDF preview is too large");
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(descriptor)) {
            if (pdfPage < 1 || pdfPage > renderer.getPageCount()) throw new IOException("The PDF page is unavailable");
            try (PdfRenderer.Page page = renderer.openPage(pdfPage - 1)) {
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                try {
                    bitmap.eraseColor(Color.WHITE);
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    return bitmap;
                } catch (RuntimeException | Error failure) { bitmap.recycle(); throw failure; }
            }
        } catch (SecurityException | IllegalArgumentException failure) {
            throw new IOException("Could not render the PDF page", failure);
        }
    }

    private static PDDocument open(File file) throws IOException {
        try {
            PDDocument document = PDDocument.load(file, MemoryUsageSetting.setupMixed(8 * 1024 * 1024));
            if (document.isEncrypted()) {
                document.close();
                throw new IOException("Password-protected PDFs are not supported. Import an unlocked copy.");
            }
            return document;
        } catch (InvalidPasswordException failure) {
            throw new IOException("Password-protected PDFs are not supported. Import an unlocked copy.", failure);
        }
    }

    private static NotebookPdfAsset inspect(PDDocument document) throws IOException {
        int count = document.getNumberOfPages();
        if (count < 1 || count > NotebookIndex.MAX_PAGES) throw new IOException("The PDF has an unsupported page count");
        List<NotebookPdfAsset.Page> pages = new ArrayList<>(count);
        int pageNumber = 0;
        for (PDPage page : document.getPages()) {
            pageNumber++;
            PDRectangle crop = page.getCropBox();
            if (!(crop.getWidth() > 0) || !(crop.getHeight() > 0)
                    || !Float.isFinite(crop.getWidth()) || !Float.isFinite(crop.getHeight())
                    || !Float.isFinite(crop.getLowerLeftX()) || !Float.isFinite(crop.getLowerLeftY())
                    || !Float.isFinite(crop.getUpperRightX()) || !Float.isFinite(crop.getUpperRightY()))
                throw new IOException("The PDF contains an invalid page size");
            boolean sideways = rotation(page) % 180 != 0;
            int width = dimension((sideways ? crop.getHeight() : crop.getWidth()) * 2),
                height = dimension((sideways ? crop.getWidth() : crop.getHeight()) * 2);
            List<NotebookPdfAsset.Link> links = new ArrayList<>();
            for (PDAnnotation annotation : page.getAnnotations()) {
                try {
                if (!(annotation instanceof PDAnnotationLink)) continue;
                PDAnnotationLink link = (PDAnnotationLink) annotation;
                PDRectangle box = link.getRectangle();
                if (box == null) continue;
                PDAction action = link.getAction();
                String uri = action instanceof PDActionURI ? ((PDActionURI) action).getURI() : null;
                if (uri != null && document.getDocumentCatalog().getURI() != null) {
                    String base = document.getDocumentCatalog().getURI().getBase();
                    if (base != null) try { uri = new java.net.URI(base).resolve(uri).toString(); }
                        catch (java.net.URISyntaxException | IllegalArgumentException ignored) { }
                }
                Destination destination = action instanceof PDActionNamed
                    ? namedDestination(document, (PDActionNamed) action, pageNumber)
                    : destination(document, action instanceof PDActionGoTo
                        ? ((PDActionGoTo) action).getDestination() : link.getDestination());
                if ((uri == null || uri.trim().isEmpty()) && destination == null) continue;
                float[] a = displayPoint(page, box.getLowerLeftX(), box.getLowerLeftY()),
                    b = displayPoint(page, box.getUpperRightX(), box.getUpperRightY());
                if (!Float.isFinite(a[0]) || !Float.isFinite(a[1]) || !Float.isFinite(b[0]) || !Float.isFinite(b[1])) continue;
                float left = unit(Math.min(a[0], b[0])), top = unit(Math.min(a[1], b[1])),
                    right = unit(Math.max(a[0], b[0])), bottom = unit(Math.max(a[1], b[1]));
                if (!(left < right) || !(top < bottom)) continue;
                links.add(new NotebookPdfAsset.Link(left, top, right, bottom,
                    destination == null ? 0 : destination.page, destination == null ? 0 : destination.fraction, uri));
                } catch (IOException | IllegalArgumentException malformedLink) {
                    // A broken source annotation is retained for export; other links remain usable.
                }
            }
            pages.add(new NotebookPdfAsset.Page(width, height, links));
        }
        List<NotebookPdfAsset.Heading> headings = new ArrayList<>();
        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        if (outline != null) readOutline(document, outline.getFirstChild(), 0, headings,
            Collections.newSetFromMap(new IdentityHashMap<COSDictionary, Boolean>()));
        String title = document.getDocumentInformation().getTitle();
        return new NotebookPdfAsset(title == null ? "" : title.trim(), pages, headings);
    }

    private static int dimension(float size) throws IOException {
        if (!Float.isFinite(size) || size < 1 || size > NotebookStore.MAX_DIMENSION)
            throw new IOException("The PDF contains an unsupported page size");
        return Math.max(1, Math.round(size));
    }

    private static void readOutline(PDDocument document, PDOutlineItem item, int depth,
            List<NotebookPdfAsset.Heading> entries, Set<COSDictionary> visited) throws IOException {
        while (item != null && entries.size() < NotebookOutline.MAX_ENTRIES && visited.size() < 50000) {
            if (!visited.add(item.getCOSObject())) return;
            PDAction action = item.getAction();
            Destination target = null;
            try { target = destination(document, action instanceof PDActionGoTo
                ? ((PDActionGoTo) action).getDestination() : item.getDestination()); }
            catch (IOException | IllegalArgumentException malformedDestination) { }
            String title = item.getTitle();
            if (target != null && title != null && !title.trim().isEmpty()) {
                title = title.trim().replace("\0", "");
                if (title.length() > NotebookOutline.MAX_TITLE_LENGTH)
                    title = title.substring(0, NotebookOutline.MAX_TITLE_LENGTH);
                if (!title.trim().isEmpty())
                    entries.add(new NotebookPdfAsset.Heading(title, target.page, depth, target.fraction));
            }
            if (depth < NotebookOutline.MAX_DEPTH)
                readOutline(document, item.getFirstChild(), depth + 1, entries, visited);
            item = item.getNextSibling();
        }
    }

    private static final class Destination {
        final int page; final double fraction;
        Destination(int page, double fraction) { this.page = page; this.fraction = fraction; }
    }

    private static Destination namedDestination(PDDocument document, PDActionNamed action, int current) {
        switch (action.getN() == null ? "" : action.getN()) {
            case "FirstPage": return new Destination(1, 0);
            case "LastPage": return new Destination(document.getNumberOfPages(), 0);
            case "NextPage": return new Destination(Math.min(document.getNumberOfPages(), current + 1), 0);
            case "PrevPage": return new Destination(Math.max(1, current - 1), 0);
            default: return null;
        }
    }

    private static Destination destination(PDDocument document, PDDestination destination) throws IOException {
        if (destination instanceof PDNamedDestination)
            destination = document.getDocumentCatalog().findNamedDestinationPage((PDNamedDestination) destination);
        if (!(destination instanceof PDPageDestination)) return null;
        PDPageDestination target = (PDPageDestination) destination;
        int number = target.getPage() == null ? target.getPageNumber()
            : document.getPages().indexOf(target.getPage());
        if (number < 0 || number >= document.getNumberOfPages()) return null;
        PDPage page = document.getPage(number);
        float left = page.getCropBox().getLowerLeftX(), top = page.getCropBox().getUpperRightY();
        boolean explicit = false;
        if (target instanceof PDPageXYZDestination) {
            PDPageXYZDestination xyz = (PDPageXYZDestination) target;
            if (xyz.getLeft() != -1) left = xyz.getLeft();
            if (xyz.getTop() != -1) top = xyz.getTop();
            explicit = xyz.getLeft() != -1 || xyz.getTop() != -1;
        } else if (target instanceof PDPageFitWidthDestination) {
            int value = ((PDPageFitWidthDestination) target).getTop();
            if (value != -1) { top = value; explicit = true; }
        } else if (target instanceof PDPageFitRectangleDestination) {
            PDPageFitRectangleDestination rectangle = (PDPageFitRectangleDestination) target;
            left = rectangle.getLeft(); top = rectangle.getTop(); explicit = true;
        } else if (target instanceof PDPageFitHeightDestination) {
            int value = ((PDPageFitHeightDestination) target).getLeft();
            if (value != -1) { left = value; explicit = true; }
        }
        double fraction = explicit ? displayPoint(page, left, top)[1] : 0;
        return new Destination(number + 1, Math.max(0, Math.min(Math.nextDown(1.0), fraction)));
    }

    private static int rotation(PDPage page) { return ((page.getRotation() % 360) + 360) % 360; }

    private static float[] displayPoint(PDPage page, float x, float y) {
        PDRectangle crop = page.getCropBox();
        float u = (x - crop.getLowerLeftX()) / crop.getWidth(),
            v = (y - crop.getLowerLeftY()) / crop.getHeight();
        switch (rotation(page)) {
            case 90: return new float[] { v, u };
            case 180: return new float[] { 1 - u, v };
            case 270: return new float[] { 1 - v, 1 - u };
            default: return new float[] { u, 1 - v };
        }
    }

    private static float unit(float value) { return Math.max(0, Math.min(1, value)); }

    /** Preserve the complete original object graph and add vector content streams on its pages. */
    static void write(OutputStream output, File original, String name, int count,
            NotebookPdf.PageSource source, NotebookPdf.Progress progress, NotebookPdf.OutlineSource ink,
            List<NotebookOutline.Entry> outline) throws IOException {
        if (output == null || source == null || ink == null || name == null
                || count < 1 || count > NotebookIndex.MAX_PAGES) throw new IOException("Invalid PDF export request");
        if (original == null) {
            writeNew(output, name, count, number -> {
                NotebookPage page = source.page(number);
                if (page != null && page.pdfPage > 0) throw new IOException("The original PDF source is unavailable");
                return page;
            }, progress, ink, outline);
            return;
        }
        try (PDDocument document = open(original)) {
            NotebookPdfAsset originalMetadata = inspect(document);
            List<PDPage> originalPages = new ArrayList<>();
            for (PDPage page : document.getPages()) {
                // Freeze inherited values before replacing the page tree or detaching a page.
                page.setMediaBox(page.getMediaBox()); page.setCropBox(page.getCropBox());
                page.setRotation(page.getRotation());
                if (page.getResources() != null) page.setResources(page.getResources());
                originalPages.add(page);
            }
            List<NotebookPage> pages = new ArrayList<>(count);
            Set<Integer> used = new HashSet<>();
            List<PDPage> outputPages = new ArrayList<>(count);
            for (int i = 1; i <= count; i++) {
                NotebookPage page = source.page(i);
                if (page == null || !page.isInitialized()) throw new IOException("The notebook page is unavailable");
                pages.add(page.withStrokes(Collections.emptyList()));
                if (page.pdfPage > 0) {
                    if (page.pdfPage > originalPages.size()) throw new IOException("The original PDF page is unavailable");
                    PDPage originalPage = originalPages.get(page.pdfPage - 1);
                    outputPages.add(used.add(page.pdfPage) ? originalPage : duplicate(originalPage));
                } else outputPages.add(blankPage(page));
            }
            boolean sameOrder = outputPages.size() == originalPages.size();
            if (sameOrder) for (int i = 0; i < outputPages.size(); i++)
                if (outputPages.get(i) != originalPages.get(i)) { sameOrder = false; break; }
            if (!sameOrder) {
                normalizeDestinations(document, originalPages);
                while (document.getNumberOfPages() > 0) document.removePage(0);
                for (PDPage page : outputPages) document.addPage(page);
            }
            for (int i = 0; i < pages.size(); i++) {
                NotebookPage page = source.page(i + 1);
                appendInk(document, outputPages.get(i), page, page.pdfPage == 0, ink);
                if (progress != null) progress.pageExported(i + 1, count);
            }
            if (!unchangedOutline(originalMetadata.outline, originalPages, pages, outputPages, outline))
                setOutline(document, pages, outputPages, outline);
            document.save(output);
        }
    }

    private static PDPage duplicate(PDPage original) throws IOException {
        PDPage result = new PDPage(new COSDictionary(original.getCOSObject()));
        // Independent annotation dictionaries keep each duplicate's /P reference correct.
        COSArray annotations = new COSArray();
        for (PDAnnotation annotation : original.getAnnotations()) {
            COSDictionary copy = new COSDictionary(annotation.getCOSObject());
            copy.setItem(COSName.P, result);
            annotations.add(copy);
        }
        result.getCOSObject().setItem(COSName.ANNOTS, annotations);
        return result;
    }

    /** Numeric local destinations must keep targeting their original page after reordering. */
    private static void normalizeDestinations(PDDocument document, List<PDPage> pages) {
        COSDictionary catalog = document.getDocumentCatalog().getCOSObject();
        normalizeDestinationValue(catalog.getDictionaryObject(COSName.OPEN_ACTION), pages);
        normalizeActionGraph(catalog, pages, identitySet(), 0);
        COSDictionary names = catalog.getCOSDictionary(COSName.NAMES);
        if (names != null) normalizeDestinationTree(names.getCOSDictionary(COSName.DESTS), pages, identitySet(), 0);
        COSDictionary legacy = catalog.getCOSDictionary(COSName.DESTS);
        if (legacy != null) for (COSName key : new ArrayList<>(legacy.keySet()))
            normalizeDestinationValue(legacy.getDictionaryObject(key), pages);
        normalizeOutlineTree(catalog.getCOSDictionary(COSName.OUTLINES), pages, identitySet(), 0);
    }

    private static Set<COSBase> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<COSBase, Boolean>());
    }

    /** Only known action and link dictionaries are interpreted; arbitrary PDF metadata is untouched. */
    private static void normalizeActionGraph(COSBase object, List<PDPage> pages, Set<COSBase> seen, int depth) {
        if (object == null || depth > 256 || !seen.add(object)) return;
        if (object instanceof COSDictionary) {
            COSDictionary dictionary = (COSDictionary) object;
            if ("GoTo".equals(dictionary.getNameAsString(COSName.S)))
                normalizeDestinationValue(dictionary.getDictionaryObject(COSName.D), pages);
            if ("Link".equals(dictionary.getNameAsString(COSName.SUBTYPE)))
                normalizeDestinationValue(dictionary.getDictionaryObject(COSName.DEST), pages);
            for (COSName key : new ArrayList<>(dictionary.keySet()))
                normalizeActionGraph(dictionary.getDictionaryObject(key), pages, seen, depth + 1);
        } else if (object instanceof COSArray) {
            COSArray array = (COSArray) object;
            for (int i = 0; i < array.size(); i++) normalizeActionGraph(array.getObject(i), pages, seen, depth + 1);
        }
    }

    private static void normalizeOutlineTree(COSDictionary node, List<PDPage> pages, Set<COSBase> seen, int depth) {
        while (node != null && depth <= 256 && seen.add(node)) {
            normalizeDestinationValue(node.getDictionaryObject(COSName.DEST), pages);
            normalizeOutlineTree(node.getCOSDictionary(COSName.FIRST), pages, seen, depth + 1);
            node = node.getCOSDictionary(COSName.NEXT);
        }
    }

    private static void normalizeDestinationTree(COSDictionary node, List<PDPage> pages, Set<COSBase> seen, int depth) {
        if (node == null || depth > 256 || !seen.add(node)) return;
        COSArray names = node.getCOSArray(COSName.NAMES), children = node.getCOSArray(COSName.KIDS);
        if (names != null) for (int i = 1; i < names.size(); i += 2) normalizeDestinationValue(names.getObject(i), pages);
        if (children != null) for (int i = 0; i < children.size(); i++) {
            COSBase child = children.getObject(i);
            if (child instanceof COSDictionary) normalizeDestinationTree((COSDictionary) child, pages, seen, depth + 1);
        }
    }

    private static void normalizeDestinationValue(COSBase value, List<PDPage> pages) {
        if (value instanceof COSDictionary) value = ((COSDictionary) value).getDictionaryObject(COSName.D);
        if (!(value instanceof COSArray)) return;
        COSArray array = (COSArray) value;
        if (array.size() < 2 || !(array.getObject(0) instanceof COSInteger) || !(array.getObject(1) instanceof COSName)) return;
        String type = ((COSName) array.getObject(1)).getName();
        if (!Arrays.asList("XYZ", "Fit", "FitH", "FitV", "FitR", "FitB", "FitBH", "FitBV").contains(type)) return;
        int page = ((COSInteger) array.getObject(0)).intValue();
        if (page >= 0 && page < pages.size()) array.set(0, pages.get(page).getCOSObject());
    }

    private static PDPage blankPage(NotebookPage page) {
        float width = page.paperSize != null ? (float) page.paperSize.pdfWidth() : 595.2756f;
        float height = page.paperSize != null ? (float) page.paperSize.pdfHeight()
            : Math.min(14400, width / page.width * page.height);
        if (page.paperSize == null && height == 14400) width = height / page.height * page.width;
        return new PDPage(new PDRectangle(width, height));
    }

    private static Matrix pageTransform(PDPage pdf, NotebookPage page) {
        PDRectangle box = pdf.getCropBox();
        float w = box.getWidth(), h = box.getHeight();
        switch (rotation(pdf)) {
            case 90: return new Matrix(0, h / page.width, w / page.height, 0, box.getLowerLeftX(), box.getLowerLeftY());
            case 180: return new Matrix(-w / page.width, 0, 0, h / page.height, box.getUpperRightX(), box.getLowerLeftY());
            case 270: return new Matrix(0, -h / page.width, -w / page.height, 0, box.getUpperRightX(), box.getUpperRightY());
            default: return new Matrix(w / page.width, 0, 0, -h / page.height, box.getLowerLeftX(), box.getUpperRightY());
        }
    }

    private static void appendInk(PDDocument document, PDPage target, NotebookPage page,
            boolean paper, NotebookPdf.OutlineSource outlines) throws IOException {
        if (!paper && page.strokes.isEmpty()) return;
        COSArray contents = target.getCOSObject().getCOSArray(COSName.CONTENTS);
        if (contents != null) {
            COSArray copy = new COSArray(); copy.addAll(contents);
            target.getCOSObject().setItem(COSName.CONTENTS, copy);
        }
        // Adding an ExtGState must not mutate a resource dictionary shared by source pages.
        PDResources resources = target.getResources();
        COSDictionary independent = resources == null ? new COSDictionary() : new COSDictionary(resources.getCOSObject());
        for (COSName key : new ArrayList<>(independent.keySet())) {
            COSBase value = independent.getDictionaryObject(key);
            if (value instanceof COSDictionary) independent.setItem(key, new COSDictionary((COSDictionary) value));
        }
        target.setResources(new PDResources(independent));
        try (PDPageContentStream stream = new PDPageContentStream(document, target,
                PDPageContentStream.AppendMode.APPEND, true, true)) {
            stream.saveGraphicsState(); stream.transform(pageTransform(target, page));
            stream.addRect(0, 0, page.width, page.height); stream.clip();
            if (paper) {
                stream.setNonStrokingColor(255, 255, 255);
                stream.addRect(0, 0, page.width, page.height); stream.fill();
                if (page.template == NotebookPage.Template.LINED) {
                    stream.setStrokingColor(210, 210, 210); stream.setLineWidth(1);
                    for (int y = 72; y < page.height; y += 72) {
                        stream.moveTo(0, y); stream.lineTo(page.width, y);
                    }
                    stream.stroke();
                }
            }
            Map<Integer, PDExtendedGraphicsState> alphas = new HashMap<>();
            for (NotebookPage.Stroke stroke : page.strokes) {
                NotebookStrokeOutline shape = outlines.outline(stroke);
                if (shape == null) throw new IOException("Missing vector handwriting outline");
                int alpha = stroke.color >>> 24;
                PDExtendedGraphicsState state = alphas.get(alpha);
                if (state == null) {
                    state = new PDExtendedGraphicsState(); state.setNonStrokingAlphaConstant(alpha / 255f);
                    alphas.put(alpha, state);
                }
                stream.setGraphicsStateParameters(state);
                stream.setNonStrokingColor((stroke.color >>> 16) & 255, (stroke.color >>> 8) & 255, stroke.color & 255);
                shape.writeTo(new NotebookStrokeOutline.Sink() {
                    @Override public void moveTo(float x, float y) throws IOException { stream.moveTo(x, y); }
                    @Override public void lineTo(float x, float y) throws IOException { stream.lineTo(x, y); }
                    @Override public void close() throws IOException { stream.closePath(); }
                });
                if (shape.evenOdd) stream.fillEvenOdd(); else stream.fill();
            }
            stream.restoreGraphicsState();
        }
    }

    private static boolean unchangedOutline(List<NotebookPdfAsset.Heading> imported, List<PDPage> originals,
            List<NotebookPage> pages, List<PDPage> pdfPages, List<NotebookOutline.Entry> entries) {
        if (entries == null || imported.size() != entries.size()) return false;
        Map<UUID, Integer> sources = new HashMap<>();
        Map<UUID, PDPage> targets = new HashMap<>();
        for (int i = 0; i < pages.size(); i++) {
            NotebookPage page = pages.get(i); sources.put(page.id, page.pdfPage); targets.put(page.id, pdfPages.get(i));
        }
        for (int i = 0; i < imported.size(); i++) {
            NotebookPdfAsset.Heading a = imported.get(i); NotebookOutline.Entry b = entries.get(i);
            if (!a.title.equals(b.title) || a.depth != b.depth || !Objects.equals(sources.get(b.pageId), a.pdfPage)
                    || targets.get(b.pageId) != originals.get(a.pdfPage - 1)
                    || Math.abs(a.fraction - b.fraction) > .000001) return false;
        }
        return true;
    }

    private static void setOutline(PDDocument document, List<NotebookPage> pages,
            List<PDPage> pdfPages, List<NotebookOutline.Entry> entries) {
        if (entries == null) return;
        PDDocumentOutline outline = new PDDocumentOutline();
        Map<UUID, PDPage> targets = new HashMap<>();
        for (int i = 0; i < pages.size(); i++) targets.put(pages.get(i).id, pdfPages.get(i));
        List<PDOutlineNode> ancestors = new ArrayList<>(); ancestors.add(outline);
        for (NotebookOutline.Entry entry : entries) {
            PDPage page = targets.get(entry.pageId);
            if (page == null) continue;
            int depth = Math.min(entry.depth, ancestors.size() - 1);
            while (ancestors.size() > depth + 1) ancestors.remove(ancestors.size() - 1);
            PDOutlineItem item = new PDOutlineItem(); item.setTitle(entry.title);
            PDPageXYZDestination destination = new PDPageXYZDestination(); destination.setPage(page);
            PDRectangle crop = page.getCropBox();
            // Convert the top-left logical view point back to the rotated PDF's user space.
            double f = entry.fraction;
            switch (rotation(page)) {
                case 90:
                    destination.setLeft(Math.round((float) (crop.getLowerLeftX() + f * crop.getWidth())));
                    destination.setTop(Math.round(crop.getLowerLeftY())); break;
                case 180:
                    destination.setLeft(Math.round(crop.getUpperRightX()));
                    destination.setTop(Math.round((float) (crop.getLowerLeftY() + f * crop.getHeight()))); break;
                case 270:
                    destination.setLeft(Math.round((float) (crop.getUpperRightX() - f * crop.getWidth())));
                    destination.setTop(Math.round(crop.getUpperRightY())); break;
                default:
                    destination.setLeft(Math.round(crop.getLowerLeftX()));
                    destination.setTop(Math.round((float) (crop.getUpperRightY() - f * crop.getHeight())));
            }
            destination.setZoom(0); item.setDestination(destination);
            ancestors.get(depth).addLast(item); ancestors.add(item);
        }
        document.getDocumentCatalog().setDocumentOutline(outline);
    }

    /** Notes retain the small streaming writer; PDFBox adds the optional bookmark tree. */
    private static void writeNew(OutputStream output, String name, int count, NotebookPdf.PageSource source,
            NotebookPdf.Progress progress, NotebookPdf.OutlineSource ink, List<NotebookOutline.Entry> outline)
            throws IOException {
        if (outline == null || outline.isEmpty()) { NotebookPdf.write(output, name, count, source, progress, ink); return; }
        // Use a bounded disk staging file rather than retaining all vector content in heap.
        File staging = File.createTempFile("scriflow-outline-", ".pdf");
        List<NotebookPage> pages = new ArrayList<>(count);
        try {
            try (java.io.FileOutputStream file = new java.io.FileOutputStream(staging)) {
                NotebookPdf.write(file, name, count, number -> {
                    NotebookPage page = source.page(number);
                    pages.add(page.withStrokes(Collections.emptyList())); return page;
                }, progress, ink);
            }
            try (PDDocument document = open(staging)) {
                List<PDPage> pdfPages = new ArrayList<>();
                for (PDPage page : document.getPages()) pdfPages.add(page);
                setOutline(document, pages, pdfPages, outline); document.save(output);
            }
        } finally { staging.delete(); }
    }
}
