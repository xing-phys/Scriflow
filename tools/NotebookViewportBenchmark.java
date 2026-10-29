package org.eink.inkprobe.notebook;

import java.util.*;

/** Same synthetic 600-page pan/search workload on the release baseline and current geometry. */
public final class NotebookViewportBenchmark {
    private static volatile long consumed;
    private static final int ITERATIONS = 20000;
    private static final List<NotebookViewport.Size> sizes = new ArrayList<>();
    private static final List<ViewportBaseline.Size> oldSizes = new ArrayList<>();
    public static void main(String[] args) {
        for (int i = 0; i < 600; i++) {
            int w = 1184 + i % 7 * 100, h = 1680 + i % 5 * 83;
            sizes.add(new NotebookViewport.Size(w,h)); oldSizes.add(new ViewportBaseline.Size(w,h));
        }
        for (int i = 0; i < 3; i++) { baseline(); current(); }
        double[] before = new double[7], after = new double[7];
        long expected = baseline(), actual = current();
        if (expected != actual) throw new AssertionError("Geometry workload results differ: " + expected + " / " + actual);
        for (int i = 0; i < before.length; i++) {
            // Alternate order to reduce consistent warmup/thermal bias.
            if (i % 2 == 0) { before[i] = timed(true); after[i] = timed(false); }
            else { after[i] = timed(false); before[i] = timed(true); }
        }
        Arrays.sort(before); Arrays.sort(after);
        System.out.println("{\"pages\":600,\"iterations\":"+ITERATIONS+",\"samples\":7,\"baselineMedianMs\":"+before[3]
            +",\"currentMedianMs\":"+after[3]+",\"sameResult\":true,\"checksum\":"+actual+"}");
    }
    private static double timed(boolean old) {
        long start = System.nanoTime(); consumed = old ? baseline() : current(); return (System.nanoTime()-start)/1e6;
    }
    private static long current() {
        NotebookViewport v = new NotebookViewport(sizes,1404,1872,24,2,0); long sum = 0;
        for (int i=0;i<ITERATIONS;i++) {
            v=v.withView(2,(i*31)&1023);float scroll=(i*733L)%(long)v.maxScroll();
            sum+=v.visiblePages(scroll).size()+v.nearestPage(936,scroll)+v.pageAt(702,900,scroll)+v.anchorPage(scroll);
        }
        return sum;
    }
    private static long baseline() {
        ViewportBaseline v = new ViewportBaseline(oldSizes,1404,1872,24,2,0); long sum = 0;
        for (int i=0;i<ITERATIONS;i++) {
            v=v.withView(2,(i*31)&1023);float scroll=(i*733L)%(long)v.maxScroll();
            sum+=v.visiblePages(scroll).size()+v.nearestPage(936,scroll)+v.pageAt(702,900,scroll)+v.anchorPage(scroll);
        }
        return sum;
    }
}
