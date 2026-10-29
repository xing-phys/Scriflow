package org.eink.inkprobe.notebook;

public final class NotebookCacheBudgetTest {
    public static void main(String[] args) {
        int checks = 0;
        for (int notes = 1; notes <= 1000; notes++) {
            NotebookCacheBudget budget = new NotebookCacheBudget(notes);
            if (budget.pdfPixelsPerNote * notes > NotebookCacheBudget.PDF_PIXELS) throw new AssertionError("PDF optional allowance grows with pane count"); checks++;
            if (budget.activePoints + (long)budget.readerPoints * (notes-1) > 131072) throw new AssertionError("Outline allowance grows with pane count"); checks++;
            for (long required : new long[]{0,262144,1048576,3000000,8000000,Long.MAX_VALUE}) {
                long retained = NotebookCacheBudget.retainedPdfPixels(required,budget.pdfPixelsPerNote);
                if (retained < required) throw new AssertionError("Required sharp pixels must survive budget pressure");checks++;
                if (required==0 && retained!=0) throw new AssertionError("Empty pane retains fallback pixels");checks++;
            }
        }
        boolean rejected=false;try{new NotebookCacheBudget(0);}catch(IllegalArgumentException e){rejected=true;}
        if(!rejected)throw new AssertionError("Missing workspace rejected");checks++;
        System.out.println("NotebookCacheBudget: "+checks+" assertions passed (shared allowances and required sharp pixels).");
    }
}
