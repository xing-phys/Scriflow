package org.eink.inkprobe.notebook;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only navigation and display metadata; the original PDF remains authoritative. */
public final class NotebookPdfAsset {
    public final String title;
    public final List<Page> pages;
    public final List<Heading> outline;

    NotebookPdfAsset(String title, List<Page> pages, List<Heading> outline) {
        this.title = title;
        this.pages = immutable(pages);
        this.outline = immutable(outline);
    }

    public static final class Page {
        public final int width, height;
        public final List<Link> links;

        Page(int width, int height, List<Link> links) {
            this.width = width; this.height = height; this.links = immutable(links);
        }
    }

    public static final class Link {
        /** Hit rectangle in the displayed, rotated crop box, normalized to [0, 1]. */
        public final float left, top, right, bottom;
        /** One-based original PDF page, or zero for a URI. */
        public final int targetPage;
        public final double fraction;
        public final String uri;

        Link(float left, float top, float right, float bottom, int targetPage,
             double fraction, String uri) {
            this.left = left; this.top = top; this.right = right; this.bottom = bottom;
            this.targetPage = targetPage; this.fraction = fraction; this.uri = uri;
        }
    }

    public static final class Heading {
        public final String title;
        public final int pdfPage, depth;
        public final double fraction;

        Heading(String title, int pdfPage, int depth, double fraction) {
            this.title = title; this.pdfPage = pdfPage; this.depth = depth;
            this.fraction = fraction;
        }
    }

    private static <T> List<T> immutable(List<T> entries) {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }
}
