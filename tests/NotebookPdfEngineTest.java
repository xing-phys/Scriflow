package org.eink.inkprobe.notebook;

import com.tom_roush.pdfbox.cos.*;
import com.tom_roush.pdfbox.pdmodel.*;
import com.tom_roush.pdfbox.pdmodel.common.*;
import com.tom_roush.pdfbox.pdmodel.common.filespecification.*;
import com.tom_roush.pdfbox.pdmodel.encryption.*;
import com.tom_roush.pdfbox.pdmodel.interactive.action.*;
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.*;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.*;
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Executes the shipped PDFBox Android library, with Android logging and the bitmap decoder replaced by host contract adapters. */
public final class NotebookPdfEngineTest {
    private static int assertions;
    private static final String FIRST = "q 0.95 0.95 0.95 rg 20 30 360 240 re f Q\n" +
        "BT /F1 16 Tf 60 220 Td (Original PDF - rotated crop) Tj ET\n";
    private static final String SECOND_STREAM = "q 0 0 0 RG 1 w 60 190 m 300 190 l S Q\n";
    private static final String SECOND = "BT /F1 16 Tf 20 350 Td (Chapter two: preserved text) Tj ET\n" +
        "BT /F1 12 Tf 20 310 Td (Internal links, URI, outline and attachment) Tj ET\n";

    public static void main(String[] args) throws Exception {
        File directory = new File(args[0]), source = new File(directory, "source.pdf");
        fixture(source);
        byte[] pristine = Files.readAllBytes(source.toPath());
        NotebookPdfAsset asset = NotebookPdfEngine.inspect(source);
        check(asset.pages.size() == 2, "Two PDF source pages");
        check(asset.title.equals("Original PDF title"), "Title metadata imported");
        check(asset.pages.get(0).width == 480 && asset.pages.get(0).height == 720, "Rotated crop logical dimensions");
        List<NotebookPdfAsset.Link> links = NotebookPdfEngine.links(source, 1);
        check(links.size() == 4, "Named, numeric, URI and Named-action links imported lazily; malformed link ignored");
        check(links.get(3).targetPage == 2, "Named NextPage action resolves relative to source page");
        check(links == NotebookPdfEngine.links(source, 1), "Repeated visible-page link lookup reuses parsed annotations");
        NotebookPdfAsset.Link internal = links.get(0);
        check(internal.targetPage == 2 && Math.abs(internal.fraction - .5) < .000001, "Named FitH destination resolved");
        check(Math.abs(internal.left) < .000001 && Math.abs(internal.top) < .000001
            && Math.abs(internal.right - 20f / 240) < .000001
            && Math.abs(internal.bottom - 40f / 360) < .000001, "Link hit bounds honor crop and rotation");
        check(asset.outline.size() == 2 && asset.outline.get(1).depth == 1, "Nested outline imported; empty NUL titles ignored");

        NotebookPage a = page(asset.pages.get(0), 1).withStroke(stroke(80, 120, 0xffb00000));
        NotebookPage b = page(asset.pages.get(1), 2).withStroke(stroke(100, 220, 0x802040c0));
        List<NotebookPage> pages = Arrays.asList(a, b);
        List<NotebookOutline.Entry> outline = imported(asset, pages);
        File annotated = new File(directory, "annotated.pdf");
        export(annotated, source, pages, outline);
        check(Arrays.equals(pristine, Files.readAllBytes(source.toPath())), "Immutable source byte-for-byte unchanged");
        try (PDDocument document = PDDocument.load(annotated)) {
            check(document.getNumberOfPages() == 2, "Export keeps source page count");
            check(document.getDocumentInformation().getTitle().equals("Original PDF title"), "Original PDF metadata preserved");
            check(document.getDocumentCatalog().getCOSObject().getString("FixtureCatalog")
                .equals("Preserve this catalog entry"), "Additional catalog entries preserved");
            check(document.getPage(0).getRotation() == 90, "Original page rotation preserved");
            check(document.getPage(0).getCropBox().getLowerLeftX() == 20, "Original crop box preserved");
            List<String> content = streams(document.getPage(0));
            check(content.contains(FIRST) && content.contains(SECOND_STREAM), "Original content streams preserved verbatim");
            String ink = content.get(content.size() - 1);
            check(ink.contains("0 0.5 0.5 0 20 30 cm"), "Vector ink maps logical coordinates onto rotated crop");
            check(ink.contains("80 120 m") && ink.contains("f\n"), "Handwriting appended as filled vector path");
            check(document.getPage(0).getResources().getXObjectNames().iterator().hasNext() == false, "Export does not rasterize PDF pages");
            List<PDAnnotation> annotations = document.getPage(0).getAnnotations();
            check(annotations.size() == 5, "All original link annotations, including malformed data, retained");
            PDAnnotationLink uri = (PDAnnotationLink) annotations.get(1);
            check(((PDActionURI) uri.getAction()).getURI().equals("https://example.org/paper#section-2"), "URI hyperlink preserved");
            PDPageDestination named = document.getDocumentCatalog().findNamedDestinationPage(new PDNamedDestination("chapter-two"));
            check(document.getPages().indexOf(named.getPage()) == 1, "Named destination still targets source page");
            PDOutlineItem chapter = document.getDocumentCatalog().getDocumentOutline().getFirstChild();
            check(chapter.isBold() && chapter.isItalic(), "Unchanged original outline style retained");
            check(chapter.getFirstChild().getTitle().equals("Chapter two"), "Original outline hierarchy retained");
            check(chapter.getNextSibling().getTitle().equals("\0\0"), "Unsupported original outline item retained in PDF");
            PDComplexFileSpecification attachment = document.getDocumentCatalog().getNames().getEmbeddedFiles().getNames().get("support.txt");
            check(new String(attachment.getEmbeddedFile().toByteArray(), StandardCharsets.UTF_8).equals("Original attached research data"), "Embedded attachment preserved");
        }

        NotebookPage duplicate = page(asset.pages.get(0), 1).withStroke(stroke(200, 300, 0xff0000b0));
        NotebookPage blank = NotebookPage.empty().withSize(600, 800).withTemplate(NotebookPage.Template.LINED);
        List<NotebookPage> reordered = Arrays.asList(b, a, duplicate, blank);
        List<NotebookOutline.Entry> changed = Arrays.asList(new NotebookOutline.Entry("My section", duplicate.id, .25, 0));
        File edited = new File(directory, "reordered-duplicate.pdf");
        export(edited, source, reordered, changed);
        try (PDDocument document = PDDocument.load(edited)) {
            check(document.getNumberOfPages() == 4, "Reorder, duplicate and inserted note page exported");
            check(document.getDocumentCatalog().getCOSObject().getCOSArray(COSName.getPDFName("FixtureArray")).getObject(0)
                instanceof COSInteger, "Custom catalog arrays are never interpreted as PDF destinations");
            String originalInk = last(streams(document.getPage(1))), duplicateInk = last(streams(document.getPage(2)));
            check(originalInk.contains("80 120 m") && !originalInk.contains("200 300 m"), "Duplicate ink never leaks into original");
            check(duplicateInk.contains("200 300 m") && !duplicateInk.contains("80 120 m"), "Original ink never leaks into duplicate");
            check(streams(document.getPage(2)).size() == streams(document.getPage(1)).size(), "Duplicated multi-stream page has independent append array");
            PDPageDestination named = document.getDocumentCatalog().findNamedDestinationPage(new PDNamedDestination("chapter-two"));
            check(document.getPages().indexOf(named.getPage()) == 0, "Named links follow original page through reorder");
            PDAnnotationLink numeric = (PDAnnotationLink) document.getPage(1).getAnnotations().get(2);
            PDPageDestination numericTarget = (PDPageDestination) numeric.getDestination();
            check(document.getPages().indexOf(numericTarget.getPage()) == 0, "Numeric links follow original page through reorder");
            check(((PDAnnotationLink) document.getPage(2).getAnnotations().get(1)).getPage().getCOSObject()
                == document.getPage(2).getCOSObject(), "Duplicate annotation owns the duplicate page");
            PDOutlineItem item = document.getDocumentCatalog().getDocumentOutline().getFirstChild();
            check(item.getTitle().equals("My section") && document.getPages().indexOf(item.findDestinationPage(document)) == 2,
                "Edited outline follows stable notebook page identity");
            check(last(streams(document.getPage(3))).contains("72 m"), "Added note page retains lined paper");
        }

        File duplicateFirst = new File(directory, "duplicate-before-original.pdf");
        export(duplicateFirst, source, Arrays.asList(duplicate, a, b), outline);
        try (PDDocument document = PDDocument.load(duplicateFirst)) {
            PDOutlineItem item = document.getDocumentCatalog().getDocumentOutline().getFirstChild();
            check(document.getPages().indexOf(item.findDestinationPage(document)) == 1,
                "Untouched note outline targets original page identity when duplicate is moved before it");
        }

        File plain = new File(directory, "note-outline.pdf");
        export(plain, null, Collections.singletonList(blank), Collections.singletonList(new NotebookOutline.Entry("A note heading", blank.id, .2, 0)));
        try (PDDocument document = PDDocument.load(plain)) {
            check(document.getDocumentCatalog().getDocumentOutline().getFirstChild().getTitle().equals("A note heading"),
                "Ordinary note outline exports as PDF bookmarks");
        }
        try { export(new File(directory, "missing-source.pdf"), null, pages, outline);
            throw new AssertionError("PDF pages must not export with a missing source"); }
        catch (IOException expected) { check(expected.getMessage().contains("original PDF source"), "Missing source fails closed rather than exporting only ink"); }
        rotations(directory);
        metadataCache(directory, source, asset);
        largePdf(directory);
        malformedAnnotations(directory);
        NotebookPdfEngine.closeNavigation();
        File encrypted = new File(directory, "encrypted.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy("owner-password", "", new AccessPermission()));
            document.save(encrypted);
        }
        try { NotebookPdfEngine.inspect(encrypted); throw new AssertionError("Encrypted import should reject"); }
        catch (IOException expected) { check(expected.getMessage().contains("unlocked copy"), "Encrypted PDF rejection is actionable"); }
        check(Arrays.equals(pristine, Files.readAllBytes(source.toPath())), "Source remains unchanged after all exports");
        figures(directory, source, a);
        rotatedFigures(directory);
        System.out.println("NotebookPdfEngineTest: " + assertions + " assertions passed; fixtures in " + directory);
    }

    private static void figures(File directory, File source, NotebookPage pdfPage) throws Exception {
        for (boolean imported : new boolean[]{false, true}) for (boolean alpha : new boolean[]{false, true}) {
            NotebookPage page = (imported ? pdfPage : NotebookPage.empty().withSize(480, 720))
                .withStrokes(Collections.emptyList()).withFigures(Collections.singletonList(
                    new NotebookFigure(UUID.randomUUID(), 20, 30, 100, 80, NotebookFigureFixture.png(alpha))));
            File output = new File(directory, imported ? "figure-on-pdf.pdf" : "figure-on-paper.pdf");
            export(output, imported ? source : null, Collections.singletonList(page), Collections.emptyList());
            try (PDDocument document = PDDocument.load(output)) {
                PDPage first = document.getPage(0);
                check(first.getResources().getXObjectNames().iterator().hasNext(), "Figure survives PDF export without ink on " + (imported ? "PDF" : "paper"));
                String content = String.join("\n", streams(first));
                check(content.contains("100 0 0 -80 20 110 cm"), "Figure transform preserves logical placement and image orientation");
                if (imported) check(content.contains(FIRST), "Figure composition preserves original PDF content");
                com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject image =
                    (com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject) first.getResources().getXObject(first.getResources().getXObjectNames().iterator().next());
                check(image.getWidth() == 2 && image.getHeight() == 2, "Export retains source figure pixels");
                check((image.getSoftMask() != null) == alpha, "PDF preserves figure transparency");
            }
        }
    }

    private static void rotatedFigures(File directory) throws Exception {
        for (float angle : new float[]{37, 90, -53}) {
            NotebookFigure figure = new NotebookFigure(UUID.randomUUID(), 140, 200, 100, 80, angle, NotebookFigureFixture.png(true));
            NotebookPage page = NotebookPage.empty().withSize(480, 720).withFigures(Collections.singletonList(figure));
            File output = new File(directory, "figure-angle-" + Math.round(angle) + ".pdf");
            export(output, null, Collections.singletonList(page), Collections.emptyList());
            try (PDDocument document = PDDocument.load(output)) {
                String content = String.join("\n", streams(document.getPage(0)));
                java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "([-+0-9.eE]+)\\s+([-+0-9.eE]+)\\s+([-+0-9.eE]+)\\s+([-+0-9.eE]+)\\s+([-+0-9.eE]+)\\s+([-+0-9.eE]+)\\s+cm").matcher(content);
                float[] actual = null;
                while (matcher.find()) { actual = new float[6]; for (int i=0;i<6;i++) actual[i]=Float.parseFloat(matcher.group(i+1)); }
                float[] expected = {figure.cornerX(2)-figure.cornerX(3),figure.cornerY(2)-figure.cornerY(3),
                    figure.cornerX(0)-figure.cornerX(3),figure.cornerY(0)-figure.cornerY(3),figure.cornerX(3),figure.cornerY(3)};
                boolean matches = actual != null;
                if (matches) for (int i=0;i<6;i++) matches &= Math.abs(actual[i]-expected[i])<.001;
                check(matches, "PDF image transform follows the visible rotated corners at " + angle + " degrees");
                com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject image = (com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject)
                    document.getPage(0).getResources().getXObject(document.getPage(0).getResources().getXObjectNames().iterator().next());
                check(image.getWidth()==2 && image.getHeight()==2 && image.getSoftMask()!=null, "Rotation preserves original pixels/transparency in PDF export");
            }
        }
    }

    private static NotebookPage page(NotebookPdfAsset.Page metadata, int number) {
        return NotebookPage.empty().withSize(metadata.width, metadata.height).withPdfPage(number);
    }

    private static NotebookPage.Stroke stroke(float x, float y, int color) {
        return new NotebookPage.Stroke(1, 3, color, Collections.singletonList(new NotebookPage.Point(x, y, 0, 1)));
    }

    private static List<NotebookOutline.Entry> imported(NotebookPdfAsset asset, List<NotebookPage> pages) {
        List<NotebookOutline.Entry> result = new ArrayList<>();
        for (NotebookPdfAsset.Heading heading : asset.outline)
            result.add(new NotebookOutline.Entry(heading.title, pages.get(heading.pdfPage - 1).id, heading.fraction, heading.depth));
        return result;
    }

    private static void export(File output, File source, List<NotebookPage> pages, List<NotebookOutline.Entry> outline) throws IOException {
        try (FileOutputStream raw = new FileOutputStream(output);
                BufferedOutputStream stream = new BufferedOutputStream(raw, 65536)) {
            NotebookPdfEngine.write(stream, source, "Notebook title", pages.size(), number -> pages.get(number - 1), null,
                stroke -> {
                    float x = stroke.points.get(0).x, y = stroke.points.get(0).y;
                    return new NotebookStrokeOutline(new float[] {0, x, y, .25f, x + 70, y,
                        .5f, x + 70, y + 8, .75f, x, y + 8, 1, x, y}, false);
                }, outline);
            // This is the Android export transaction: PDFBox must leave our stream/FD open.
            stream.flush(); raw.getFD().sync();
            check(raw.getFD().valid(), "PDF export preserves caller stream ownership for flush and fsync");
        }
    }

    private static void fixture(File file) throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.getDocumentInformation().setTitle("Original PDF title");
            document.getDocumentCatalog().getCOSObject().setString("FixtureCatalog", "Preserve this catalog entry");
            COSArray customArray = new COSArray(); customArray.add(COSInteger.get(1)); customArray.add(COSName.getPDFName("Fit"));
            document.getDocumentCatalog().getCOSObject().setItem("FixtureArray", customArray);
            PDPage first = new PDPage(new PDRectangle(400, 300));
            first.setCropBox(new PDRectangle(20, 30, 360, 240)); first.setRotation(90);
            PDPage second = new PDPage(new PDRectangle(300, 400));
            document.addPage(first); document.addPage(second);
            COSDictionary font = new COSDictionary(); font.setItem(COSName.TYPE, COSName.FONT);
            font.setItem(COSName.SUBTYPE, COSName.TYPE1); font.setName(COSName.BASE_FONT, "Helvetica");
            COSDictionary fonts = new COSDictionary(); fonts.setItem(COSName.getPDFName("F1"), font);
            PDResources resources = new PDResources(); resources.getCOSObject().setItem(COSName.FONT, fonts);
            first.setResources(resources); second.setResources(resources);
            first.setContents(Arrays.asList(stream(document, FIRST), stream(document, SECOND_STREAM)));
            second.setContents(stream(document, SECOND));
            PDPageFitWidthDestination chapterTwo = new PDPageFitWidthDestination(); chapterTwo.setPage(second); chapterTwo.setTop(200);
            PDDocumentNameDictionary names = new PDDocumentNameDictionary(document.getDocumentCatalog());
            PDDestinationNameTreeNode destinations = new PDDestinationNameTreeNode();
            destinations.setNames(Collections.singletonMap("chapter-two", chapterTwo)); names.setDests(destinations);
            PDEmbeddedFile attachment = new PDEmbeddedFile(document,
                new ByteArrayInputStream("Original attached research data".getBytes(StandardCharsets.UTF_8)));
            PDComplexFileSpecification specification = new PDComplexFileSpecification();
            specification.setFile("support.txt"); specification.setEmbeddedFile(attachment);
            PDEmbeddedFilesNameTreeNode attachments = new PDEmbeddedFilesNameTreeNode();
            attachments.setNames(Collections.singletonMap("support.txt", specification)); names.setEmbeddedFiles(attachments);
            document.getDocumentCatalog().setNames(names);
            PDAnnotationLink internal = new PDAnnotationLink(); internal.setRectangle(new PDRectangle(20, 30, 40, 20));
            internal.setDestination(new PDNamedDestination("chapter-two"));
            PDAnnotationLink uri = new PDAnnotationLink(); uri.setRectangle(new PDRectangle(80, 60, 100, 20));
            PDActionURI action = new PDActionURI(); action.setURI("https://example.org/paper#section-2"); uri.setAction(action);
            PDAnnotationLink numeric = new PDAnnotationLink(); numeric.setRectangle(new PDRectangle(80, 100, 100, 20));
            PDPageFitDestination numericDestination = new PDPageFitDestination(); numericDestination.setPageNumber(1);
            numeric.setDestination(numericDestination);
            PDAnnotationLink next = new PDAnnotationLink(); next.setRectangle(new PDRectangle(80, 140, 100, 20));
            PDActionNamed nextAction = new PDActionNamed(); nextAction.setN("NextPage"); next.setAction(nextAction);
            PDAnnotationLink malformed = new PDAnnotationLink(); malformed.setRectangle(new PDRectangle(80, 160, 100, 20));
            malformed.getCOSObject().setItem(COSName.DEST, COSInteger.get(3));
            first.setAnnotations(Arrays.asList(internal, uri, numeric, next, malformed));
            PDDocumentOutline outline = new PDDocumentOutline();
            PDOutlineItem chapter = new PDOutlineItem(); chapter.setTitle("Chapter one"); chapter.setBold(true); chapter.setItalic(true);
            PDPageXYZDestination start = new PDPageXYZDestination(); start.setPage(first); start.setLeft(40); start.setTop(220);
            chapter.setDestination(start); outline.addLast(chapter);
            PDOutlineItem child = new PDOutlineItem(); child.setTitle("Chapter two"); child.setDestination(chapterTwo); chapter.addLast(child);
            PDOutlineItem empty = new PDOutlineItem(); empty.setTitle("\0\0"); empty.setDestination(second); outline.addLast(empty);
            document.getDocumentCatalog().setDocumentOutline(outline);
            document.save(file);
        }
    }

    private static PDStream stream(PDDocument document, String text) throws IOException {
        return new PDStream(document, new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII)), COSName.FLATE_DECODE);
    }

    private static void rotations(File directory) throws IOException {
        File original = new File(directory, "rotations-source.pdf"), output = new File(directory, "rotations-annotated.pdf");
        try (PDDocument document = new PDDocument()) {
            for (int rotation : new int[] {0, 90, 180, 270}) {
                PDPage page = new PDPage(new PDRectangle(400, 300));
                page.setCropBox(new PDRectangle(20, 30, 360, 240)); page.setRotation(rotation);
                document.addPage(page);
                PDAnnotationLink link = new PDAnnotationLink(); link.setRectangle(new PDRectangle(20, 30, 40, 20));
                PDActionURI uri = new PDActionURI(); uri.setURI("https://example.org/rotation"); link.setAction(uri);
                page.setAnnotations(Collections.singletonList(link));
            }
            document.save(original);
        }
        NotebookPdfAsset metadata = NotebookPdfEngine.inspect(original);
        List<NotebookPage> pages = new ArrayList<>();
        for (int i = 0; i < 4; i++) pages.add(page(metadata.pages.get(i), i + 1).withStroke(stroke(80, 120, 0xff008000)));
        export(output, original, pages, Collections.emptyList());
        String[] transforms = {"0.5 0 0 -0.5 20 270 cm", "0 0.5 0.5 0 20 30 cm",
            "-0.5 0 0 0.5 380 30 cm", "0 -0.5 -0.5 0 380 270 cm"};
        try (PDDocument document = PDDocument.load(output)) {
            for (int i = 0; i < 4; i++) check(last(streams(document.getPage(i))).contains(transforms[i]),
                "Crop and rotation " + (i * 90) + " vector transform");
        }
        NotebookPdfAsset.Link upside = NotebookPdfEngine.links(original, 3).get(0), left = NotebookPdfEngine.links(original, 4).get(0);
        check(Math.abs(upside.left - 8f / 9) < .000001 && upside.top == 0, "Rotation180 link geometry");
        check(Math.abs(left.left - 11f / 12) < .000001 && Math.abs(left.top - 8f / 9) < .000001,
            "Rotation270 link geometry");
    }

    private static void metadataCache(File directory, File source, NotebookPdfAsset asset) throws IOException {
        NotebookPdfAsset cached = NotebookPdfMetadata.read(source);
        check(cached != null && cached.pages.size() == asset.pages.size() && cached.outline.size() == asset.outline.size(),
            "Geometry and outline persisted independently of Android/process lifetime");
        check(cached.pages.get(0).width == 480 && cached.outline.get(1).title.equals("Chapter two"),
            "Metadata cache retains rotated geometry and nested outline");
        File cache = NotebookPdfMetadata.file(source);
        try (RandomAccessFile file = new RandomAccessFile(cache, "rw")) {
            file.seek(file.length() - 1); int b = file.read(); file.seek(file.length() - 1); file.write(b ^ 1);
        }
        check(NotebookPdfMetadata.read(source) == null, "Corrupt disposable metadata rejected");
        NotebookPdfMetadata.write(source, asset);
        long originalTime = source.lastModified();
        check(source.setLastModified(originalTime + 2000), "Test source timestamp changed");
        check(NotebookPdfMetadata.read(source) == null, "Source changes invalidate derived metadata");
        check(source.setLastModified(originalTime), "Test source timestamp restored");
        check(NotebookPdfMetadata.read(source) != null, "Unchanged immutable source can reuse cached metadata");
    }

    private static void largePdf(File directory) throws IOException {
        File source = new File(directory, "large-source.pdf");
        try (PDDocument document = new PDDocument()) {
            List<PDPage> pages = new ArrayList<>();
            StringBuilder vector = new StringBuilder("q\n");
            for (int i = 0; i < 1100; i++) vector.append("0 0 m 1 1 l S\n");
            byte[] drawing = vector.append("Q\n").toString().getBytes(StandardCharsets.US_ASCII);
            for (int i = 0; i < 600; i++) {
                PDPage page = new PDPage(); document.addPage(page); pages.add(page);
                page.setContents(new PDStream(document, new ByteArrayInputStream(drawing)));
            }
            // A bad annotation collection on an offscreen page must not make import/open decode it.
            COSArray brokenAnnotations = new COSArray(); brokenAnnotations.add(COSInteger.get(99));
            pages.get(599).getCOSObject().setItem(COSName.ANNOTS, brokenAnnotations);
            PDAnnotationLink link = new PDAnnotationLink(); link.setRectangle(new PDRectangle(20, 30, 40, 20));
            PDPageFitDestination destination = new PDPageFitDestination(); destination.setPage(pages.get(598));
            link.setDestination(destination); pages.get(0).setAnnotations(Collections.singletonList(link));
            PDDocumentOutline outline = new PDDocumentOutline();
            PDOutlineItem heading = new PDOutlineItem(); heading.setTitle("Near the end"); heading.setDestination(pages.get(598));
            outline.addLast(heading); document.getDocumentCatalog().setDocumentOutline(outline); document.save(source);
        }
        check(source.length() > 8L * 1024 * 1024, "Large fixture includes over 8 MiB of original vector content");
        long start = System.nanoTime(); NotebookPdfAsset asset = NotebookPdfEngine.inspect(source);
        long inspected = System.nanoTime();
        check(asset.pages.size() == 600 && asset.outline.get(0).pdfPage == 599,
            "600-page import reads geometry and outline without decoding offscreen annotation collections");
        check(NotebookPdfEngine.links(source, 1).get(0).targetPage == 599,
            "Lazy links resolve distant page objects through preindexed page identities");
        List<NotebookPdfAsset.Link> first = NotebookPdfEngine.links(source, 1);
        for (int page = 2; page <= 32; page++) NotebookPdfEngine.links(source, page);
        check(first != NotebookPdfEngine.links(source, 1), "Visited page link cache remains bounded");
        List<NotebookPdfAsset.Link> beforeClose = NotebookPdfEngine.links(source, 1);
        NotebookPdfEngine.closeNavigation();
        check(beforeClose != NotebookPdfEngine.links(source, 1), "Closing navigation drops reader and page link cache");
        check(NotebookPdfEngine.links(source, 600).isEmpty(), "Malformed offscreen annotation array cannot disable navigation");
        NotebookPdfAsset cached = NotebookPdfMetadata.read(source);
        check(cached != null && cached.pages.size() == 600, "600-page reopen metadata available without reparsing source");
        System.out.println("600-page, " + source.length() / (1024 * 1024) + " MiB geometry/outline index: "
            + (inspected - start) / 1000000 + " ms");
    }

    private static void malformedAnnotations(File directory) throws IOException {
        File source = new File(directory, "malformed-annotations.pdf"), output = new File(directory, "malformed-annotations-export.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage first = new PDPage(), second = new PDPage(); document.addPage(first); document.addPage(second);
            PDAnnotationLink uri = new PDAnnotationLink(); uri.setRectangle(new PDRectangle(20, 30, 40, 20));
            PDActionURI action = new PDActionURI(); action.setURI("https://example.org/valid"); uri.setAction(action);
            PDAnnotationLink broken = new PDAnnotationLink(); broken.getCOSObject().setItem(COSName.RECT, COSInteger.get(1));
            PDAnnotationLink internal = new PDAnnotationLink(); internal.setRectangle(new PDRectangle(80, 30, 40, 20));
            PDPageFitDestination destination = new PDPageFitDestination(); destination.setPage(second); internal.setDestination(destination);
            COSArray annotations = new COSArray();
            annotations.add(uri); annotations.add(COSInteger.get(99)); annotations.add(broken); annotations.add(internal);
            first.getCOSObject().setItem(COSName.ANNOTS, annotations);
            document.save(source);
        }
        byte[] original = Files.readAllBytes(source.toPath());
        NotebookPdfAsset asset = NotebookPdfEngine.inspect(source);
        List<NotebookPdfAsset.Link> links = NotebookPdfEngine.links(source, 1);
        check(links.size() == 2 && links.get(0).uri.equals("https://example.org/valid") && links.get(1).targetPage == 2,
            "Malformed annotation entries skipped independently while valid URI and internal links remain active");
        NotebookPage first = page(asset.pages.get(0), 1).withStroke(stroke(30, 40, 0xff000000));
        NotebookPage duplicate = page(asset.pages.get(0), 1);
        export(output, source, Arrays.asList(first, duplicate, page(asset.pages.get(1), 2)), Collections.emptyList());
        try (PDDocument document = PDDocument.load(output)) {
            for (int page = 0; page < 2; page++) {
                COSArray retained = document.getPage(page).getCOSObject().getCOSArray(COSName.ANNOTS);
                check(retained.size() == 4 && ((COSInteger) retained.getObject(1)).intValue() == 99,
                    "Original and duplicated PDF pages retain unsupported original annotation entries in export");
            }
        }
        check(Arrays.equals(original, Files.readAllBytes(source.toPath())), "Malformed source annotations remain byte-for-byte untouched");
    }

    private static List<String> streams(PDPage page) throws IOException {
        List<String> result = new ArrayList<>(); Iterator<PDStream> streams = page.getContentStreams();
        while (streams.hasNext()) result.add(new String(streams.next().toByteArray(), StandardCharsets.US_ASCII));
        return result;
    }
    private static String last(List<String> strings) { return strings.get(strings.size() - 1); }
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
}
