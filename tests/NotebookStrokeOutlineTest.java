package org.eink.inkprobe.notebook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Contour/winding transport and real production PDF fills, without native API stubs. */
public final class NotebookStrokeOutlineTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        contoursAndOwnership();
        invalidGeometryAndIo();
        pdfUsesSuppliedPressureOutline();
        System.out.println("NotebookStrokeOutline: " + assertions + " assertions passed (contours, pressure geometry and PDF fills).");
    }

    private static void contoursAndOwnership() throws Exception {
        float[] samples = {0,0,0, .2f,20,0, .4f,20,20, .6f,0,0,
            .6f,4,4, .8f,4,8, 1,8,4};
        NotebookStrokeOutline outline = new NotebookStrokeOutline(samples, true);
        samples[1] = 999;
        String commands = commands(outline);
        check(commands.equals("M0.0,0.0 L20.0,0.0 L20.0,20.0 L0.0,0.0 Z M4.0,4.0 L4.0,8.0 L8.0,4.0 Z "),
            "equal fractions start new contours without connecting outer boundary to hole");
        check(outline.evenOdd && outline.vertexCount() == 7, "fill rule and complete vertex count survive");
        check(!commands.contains("999"), "public outline owns its samples");
        NotebookStrokeOutline duplicate = new NotebookStrokeOutline(new float[]{0,1,2, 0,1,2, 1,3,4}, false);
        check(commands(duplicate).equals("M1.0,2.0 L3.0,4.0 Z "), "coincident zero-length sample creates no spurious contour");
        NotebookStrokeOutline negative = new NotebookStrokeOutline(new float[]{0,-4,-8,1,10,12}, false);
        check(commands(negative).startsWith("M-4.0,-8.0"), "off-page brush geometry survives for page clipping");
    }

    private static void invalidGeometryAndIo() throws Exception {
        for (float[] samples : new float[][]{null, {}, {0,1}, {0,Float.NaN,0},
                {0,0,Float.POSITIVE_INFINITY}, {-1,0,0}, {2,0,0}, {.5f,0,0,.4f,1,1}}) {
            boolean failed = false;
            try { new NotebookStrokeOutline(samples, false); } catch (IllegalArgumentException expected) { failed = true; }
            check(failed, "malformed outline rejected before writing PDF geometry");
        }
        boolean failed = false;
        try {
            new NotebookStrokeOutline(new float[]{0,0,0,1,1,1}, false).writeTo(new NotebookStrokeOutline.Sink() {
                public void moveTo(float x,float y) throws IOException { throw new IOException("cancelled/full output"); }
                public void lineTo(float x,float y) { }
                public void close() { }
            });
        } catch (IOException expected) { failed = true; }
        check(failed, "output failures/cancellation propagate during outline streaming");
    }

    private static void pdfUsesSuppliedPressureOutline() throws Exception {
        NotebookPage.Stroke stroke = new NotebookPage.Stroke(2, 10, 0x80000000, Arrays.asList(
            new NotebookPage.Point(20,20,10,.1f), new NotebookPage.Point(80,20,20,1)));
        NotebookPage page = NotebookPage.empty().withSize(100,100).withStroke(stroke);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        final int[] calls = {0};
        NotebookPdf.write(output,"Pressure",1,n -> page,null, actual -> {
            check(actual == stroke && actual.points.get(0).pressure == .1f,
                "renderer receives the original width, pressure and timing without conversion");
            calls[0]++;
            // Distinct widths and a separate contour: the PDF must transport supplied geometry,
            // not construct its own nominal-width centreline or connect separate contours.
            return new NotebookStrokeOutline(new float[]{0,20,19, .2f,80,15, .4f,80,25,
                .6f,20,21, .8f,20,19, .8f,40,19, .9f,42,19, 1,41,21}, true);
        });
        String pdf = new String(output.toByteArray(), StandardCharsets.ISO_8859_1);
        String content = NotebookPdfTest.content(pdf, 6);
        check(calls[0] == 1, "one native outline requested per stroke");
        check(content.contains("20 19 m\n80 15 l\n80 25 l\n20 21 l\n20 19 l\nh\n40 19 m"),
            "variable pressure edges and separate contour arrive unchanged");
        check(content.contains("h\nf*\n"), "entire outline is filled once with the original fill rule");
        check(!content.contains("10 w") && !content.contains(" l S"), "nominal width cannot overwrite native brush geometry");
        check(pdf.contains("/CA 0.501961 /ca 0.501961"), "one filled outline retains stroke opacity");
        check(!pdf.contains("/Subtype /Image"), "pressure-sensitive handwriting remains vector geometry");
        for (NotebookPdf.OutlineSource bad : new NotebookPdf.OutlineSource[]{null, s -> null,
                s -> {throw new IOException("native render failed");}, s -> {throw new IllegalStateException("native failure");}}) {
            boolean failed = false;
            try { NotebookPdf.write(new ByteArrayOutputStream(),"Fail",1,n -> page,null,bad); }
            catch (IOException expected) { failed = true; }
            check(failed, "missing/failed outline cannot silently fall back to thick unpressured ink");
        }
    }

    private static String commands(NotebookStrokeOutline outline) throws IOException {
        StringBuilder out = new StringBuilder();
        outline.writeTo(new NotebookStrokeOutline.Sink() {
            public void moveTo(float x,float y) { out.append("M").append(x).append(",").append(y).append(" "); }
            public void lineTo(float x,float y) { out.append("L").append(x).append(",").append(y).append(" "); }
            public void close() { out.append("Z "); }
        });
        return out.toString();
    }
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
